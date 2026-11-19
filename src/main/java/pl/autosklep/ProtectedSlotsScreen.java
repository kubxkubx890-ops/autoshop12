package pl.autosklep;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Siatka ekwipunku - klikasz slot, zeby go chronic (zielony = nie wyrzucaj). */
public class ProtectedSlotsScreen extends Screen {

    private static final int CELL = 18;
    private int x0;
    private int yMain;
    private int yHot;

    public ProtectedSlotsScreen() {
        super(Text.literal("Chronione sloty"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        x0 = cx - (9 * CELL) / 2;
        yMain = 60;
        yHot = yMain + 3 * CELL + 6;
        int by = yHot + CELL + 12;

        addDrawableChild(ButtonWidget.builder(Text.literal("Zaznacz zajęte"), b -> {
            if (client.player == null) return;
            Config c = Config.get();
            for (int i = 0; i < 36; i++) {
                ItemStack s = client.player.getInventory().getStack(i);
                c.protectedSlots[i] = !s.isEmpty() && !AutoSklepClient.isCopper(s);
            }
        }).dimensions(cx - 100, by, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Wyczyść"), b -> {
            Config c = Config.get();
            for (int i = 0; i < 36; i++) c.protectedSlots[i] = false;
        }).dimensions(cx + 2, by, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> close())
                .dimensions(cx - 100, by + 24, 200, 20).build());
    }

    private int cellX(int idx) {
        return idx >= 9 ? x0 + ((idx - 9) % 9) * CELL : x0 + idx * CELL;
    }

    private int cellY(int idx) {
        return idx >= 9 ? yMain + ((idx - 9) / 9) * CELL : yHot;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Chronione sloty"), cx, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Kliknij slot: zielony = NIE wyrzucaj"), cx, 28, 0x55FF55);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Miedź nigdy nie jest wyrzucana"), cx, 40, 0xAAAAAA);

        Config c = Config.get();
        for (int i = 0; i < 36; i++) {
            int x = cellX(i);
            int y = cellY(i);
            boolean prot = c.protectedSlots[i];
            ctx.fill(x, y, x + CELL - 1, y + CELL - 1, prot ? 0xAA00AA00 : 0x88000000);
            if (client != null && client.player != null) {
                ItemStack s = client.player.getInventory().getStack(i);
                if (!s.isEmpty()) {
                    ctx.drawItem(s, x + 1, y + 1);
                    ctx.drawStackOverlay(textRenderer, s, x + 1, y + 1);
                }
            }
            ctx.drawBorder(x, y, CELL - 1, CELL - 1, prot ? 0xFF55FF55 : 0xFF555555);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Config c = Config.get();
            for (int i = 0; i < 36; i++) {
                int x = cellX(i);
                int y = cellY(i);
                if (mouseX >= x && mouseX < x + CELL - 1 && mouseY >= y && mouseY < y + CELL - 1) {
                    c.protectedSlots[i] = !c.protectedSlots[i];
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(new AutoSklepScreen());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
