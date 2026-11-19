package pl.autosklep;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.function.Predicate;

public class AutoSklepClient implements ClientModInitializer {

    enum State { IDLE, WAIT_COMPASS, WAIT_WOOL, WAIT_COPPER, COOLDOWN, DROPPING }

    static boolean enabled = false;
    private static State state = State.IDLE;
    private static int wait = 0;
    private static int checkTicks = 0;

    private static int sellTimer = 0;
    private static int dropTimer = 0;
    private static int sellBlock = 0;      // przerwa po nieudanych probach sprzedazy
    private static int attempts = 0;
    private static int copperBefore = 0;
    private static boolean sellCycle = false;
    private static boolean forceSell = false;
    private static boolean forceDrop = false;
    private static Boolean prevPause = null;
    private static final ArrayDeque<Integer> dropQueue = new ArrayDeque<>();

    private static KeyBinding toggleKey;
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosklep.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.autosklep"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosklep.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.autosklep"));

        // /autosklep [start|stop|gui|status|sprzedaj|wyrzuc|cmd <komenda>]
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("autosklep")
                .then(ClientCommandManager.literal("start").executes(ctx -> { setEnabled(true); return 1; }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> { setEnabled(false); return 1; }))
                .then(ClientCommandManager.literal("gui").executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new AutoSklepScreen()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("sprzedaj").executes(ctx -> {
                    if (!enabled) msg("Najpierw włącz moda (/autosklep start)");
                    else { forceSell = true; msg("Sprzedaż za chwilę"); }
                    return 1;
                }))
                .then(ClientCommandManager.literal("wyrzuc").executes(ctx -> {
                    if (!enabled) msg("Najpierw włącz moda (/autosklep start)");
                    else { forceDrop = true; msg("Wyrzucanie za chwilę"); }
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    Config c = Config.get();
                    msg("Stan: " + (enabled ? "WŁĄCZONY" : "wyłączony")
                            + " | komenda: /" + c.command
                            + " | sprzedaż co " + c.sellIntervalSeconds + " s"
                            + " | wyrzut: " + (c.dropEnabled ? "co " + c.dropIntervalSeconds + " s" : "OFF"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("cmd")
                    .then(ClientCommandManager.argument("komenda", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String c = StringArgumentType.getString(ctx, "komenda").trim();
                            if (c.startsWith("/")) c = c.substring(1);
                            Config.get().command = c;
                            Config.save();
                            msg("Ustawiono komendę: /" + c);
                            return 1;
                        })))
                .executes(ctx -> { setEnabled(!enabled); return 1; })
            ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
        while (toggleKey.wasPressed()) setEnabled(!enabled);
        while (guiKey.wasPressed()) mc.setScreen(new AutoSklepScreen());

        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null || mc.interactionManager == null) return;
        Config c = Config.get();

        sellTimer++;
        dropTimer++;
        if (sellBlock > 0) sellBlock--;

        switch (state) {
            case IDLE -> {
                if (mc.currentScreen != null) return;
                if (++checkTicks < 10) return;
                checkTicks = 0;

                boolean timedSell = c.sellIntervalSeconds > 0 && sellTimer >= c.sellIntervalSeconds * 20;
                if (forceSell || (sellBlock == 0 && (timedSell || inventoryFull(p, c)))) {
                    forceSell = false;
                    startSell(p, c);
                    return;
                }

                boolean timedDrop = c.dropEnabled && c.dropIntervalSeconds > 0
                        && dropTimer >= c.dropIntervalSeconds * 20;
                if (forceDrop || timedDrop) {
                    forceDrop = false;
                    startDrop(mc, p, c);
                }
            }
            case WAIT_COMPASS -> {
                wait++;
                GenericContainerScreenHandler h = container(mc);
                if (h != null && wait % (c.clickDelay + 1) == 0) {
                    int slot = findInShop(h, st -> st.isOf(Items.COMPASS));
                    if (slot >= 0) {
                        click(mc, p, h, slot); // lewy klik w kompas
                        state = State.WAIT_WOOL;
                        wait = 0;
                        return;
                    }
                }
                if (wait > c.timeoutTicks) abort(mc, p);
            }
            case WAIT_WOOL -> {
                wait++;
                GenericContainerScreenHandler h = container(mc);
                if (h != null && wait % (c.clickDelay + 1) == 0) {
                    int slot = findInShop(h, st -> st.isOf(Items.RED_WOOL));
                    if (slot >= 0) {
                        click(mc, p, h, slot); // lewy klik w czerwona welne
                        state = State.WAIT_COPPER;
                        wait = 0;
                        return;
                    }
                }
                if (wait > c.timeoutTicks) abort(mc, p);
            }
            case WAIT_COPPER -> {
                wait++;
                GenericContainerScreenHandler h = container(mc);
                if (h != null && wait % (c.clickDelay + 1) == 0) {
                    int slot = findInShop(h, AutoSklepClient::isCopper);
                    if (slot >= 0) {
                        for (int i = 0; i < c.copperClicks; i++) shiftRightClick(mc, p, h, slot);
                        p.closeHandledScreen(); // jak ESC
                        state = State.COOLDOWN;
                        wait = 0;
                        return;
                    }
                }
                if (wait > c.timeoutTicks) abort(mc, p);
            }
            case COOLDOWN -> {
                if (++wait >= c.cooldownTicks) {
                    state = State.IDLE;
                    checkTicks = 0;
                    if (sellCycle) {
                        sellCycle = false;
                        evaluateSell(p, c);
                    }
                }
            }
            case DROPPING -> {
                wait++;
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    finishDrop();
                    return;
                }
                if (wait < 4) return; // chwila na otwarcie ekwipunku
                if ((wait - 4) % (c.dropDelayTicks + 1) != 0) return;

                Integer slotId = dropQueue.poll();
                if (slotId == null) {
                    p.closeHandledScreen();
                    finishDrop();
                    return;
                }
                ItemStack st = p.playerScreenHandler.getSlot(slotId).getStack();
                if (st.isEmpty() || isCopper(st)) return;
                // THROW + przycisk 1 = wyrzuc caly stack (Ctrl+Q)
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slotId, 1,
                        SlotActionType.THROW, p);
            }
        }
    }

    // ---------------- sprzedaz ----------------

    private static void startSell(ClientPlayerEntity p, Config c) {
        copperBefore = countCopper(p);
        sellCycle = true;
        p.networkHandler.sendChatCommand(c.command);
        state = State.WAIT_COMPASS;
        wait = 0;
    }

    /** Po cooldownie sprawdza, czy miedzi ubylo. Jesli nie - ponawia. */
    private static void evaluateSell(ClientPlayerEntity p, Config c) {
        int after = countCopper(p);
        boolean sold = copperBefore == 0 || after < copperBefore;
        if (sold) {
            attempts = 0;
            sellTimer = 0;
        } else if (++attempts >= c.maxRetries) {
            attempts = 0;
            sellTimer = 0;
            sellBlock = 600; // 30 s przerwy
            msg("Nie udało się sprzedać po " + c.maxRetries + " próbach. Przerwa 30 s.");
        }
        // przy niepowodzeniu warunek sprzedazy dalej jest spelniony, wiec IDLE ponowi probe
    }

    private static void abort(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.currentScreen != null) p.closeHandledScreen(); // ESC
        state = State.COOLDOWN;
        wait = 0;
    }

    // ---------------- wyrzucanie ----------------

    private static void startDrop(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        dropTimer = 0;
        dropQueue.clear();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || c.protectedSlots[i] || isCopper(s)) continue;
            // PlayerInventory: 0-8 pasek, 9-35 ekwipunek -> slot w playerScreenHandler
            dropQueue.add(i < 9 ? 36 + i : i);
        }
        if (dropQueue.isEmpty()) return; // nic do wyrzucenia - nie otwieraj ekwipunku
        mc.setScreen(new InventoryScreen(p));
        state = State.DROPPING;
        wait = 0;
    }

    private static void finishDrop() {
        dropQueue.clear();
        state = State.IDLE;
        checkTicks = 0;
    }

    // ---------------- pomocnicze ----------------

    private static GenericContainerScreenHandler container(MinecraftClient mc) {
        if (mc.currentScreen instanceof GenericContainerScreen s) return s.getScreenHandler();
        return null;
    }

    private static int findInShop(GenericContainerScreenHandler h, Predicate<ItemStack> match) {
        int size = h.getRows() * 9;
        for (int i = 0; i < size && i < h.slots.size(); i++) {
            ItemStack st = h.slots.get(i).getStack();
            if (!st.isEmpty() && match.test(st)) return i;
        }
        return -1;
    }

    private static void click(MinecraftClient mc, ClientPlayerEntity p,
                              GenericContainerScreenHandler h, int slot) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, p);
    }

    /** Shift + prawy przycisk myszy (QUICK_MOVE z przyciskiem 1). */
    private static void shiftRightClick(MinecraftClient mc, ClientPlayerEntity p,
                                        GenericContainerScreenHandler h, int slot) {
        mc.interactionManager.clickSlot(h.syncId, slot, 1, SlotActionType.QUICK_MOVE, p);
    }

    static boolean isCopper(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().contains("copper");
    }

    private static int countCopper(ClientPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (isCopper(s)) n += s.getCount();
        }
        return n;
    }

    /** Pelny = kazdy NIEchroniony slot zawiera miedz (chronione sloty sa pomijane). */
    private static boolean inventoryFull(ClientPlayerEntity p, Config c) {
        PlayerInventory inv = p.getInventory();
        int unprotected = 0;
        for (int i = 0; i < 36; i++) {
            if (c.protectedSlots[i]) continue;
            unprotected++;
            if (!isCopper(inv.getStack(i))) return false;
        }
        return unprotected > 0;
    }

    static void setEnabled(boolean v) {
        enabled = v;
        state = State.IDLE;
        checkTicks = 10;
        sellTimer = 0;
        dropTimer = 0;
        sellBlock = 0;
        attempts = 0;
        sellCycle = false;
        forceSell = false;
        forceDrop = false;
        dropQueue.clear();

        // dzialanie w tle: nie pauzuj gry po utracie fokusu okna
        MinecraftClient mc = MinecraftClient.getInstance();
        if (v) {
            if (prevPause == null) prevPause = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
        } else if (prevPause != null) {
            mc.options.pauseOnLostFocus = prevPause;
            prevPause = null;
        }
        msg(v ? "WŁĄCZONY (/" + Config.get().command + ")" : "wyłączony");
    }

    static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[AutoSklep] " + s), false);
    }
}
