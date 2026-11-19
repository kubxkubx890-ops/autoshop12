package pl.autosklep;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class AutoSklepScreen extends Screen {

    private TextFieldWidget cmdField;
    private TextFieldWidget clicksField;
    private TextFieldWidget retriesField;
    private TextFieldWidget delayField;
    private TextFieldWidget dropDelayField;
    private TextFieldWidget sellIntField;
    private TextFieldWidget dropIntField;
    private ButtonWidget toggleButton;
    private ButtonWidget dropButton;

    public AutoSklepScreen() {
        super(Text.literal("AutoSklep"));
    }

    private TextFieldWidget num(int x, int y, int value, int maxLen) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 55, 20, Text.empty());
        f.setMaxLength(maxLen);
        f.setText(String.valueOf(value));
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int cx = this.width / 2;

        cmdField = new TextFieldWidget(textRenderer, cx - 100, 35, 200, 20, Text.literal("Komenda"));
        cmdField.setMaxLength(100);
        cmdField.setText(c.command);
        addDrawableChild(cmdField);

        int f1 = cx - 85;
        int f2 = cx + 125;
        clicksField = num(f1, 68, c.copperClicks, 2);
        retriesField = num(f2, 68, c.maxRetries, 2);
        delayField = num(f1, 92, c.clickDelay, 3);
        dropDelayField = num(f2, 92, c.dropDelayTicks, 3);
        sellIntField = num(f1, 116, c.sellIntervalSeconds, 4);
        dropIntField = num(f2, 116, c.dropIntervalSeconds, 4);

        dropButton = ButtonWidget.builder(dropText(), b -> {
            Config cc = Config.get();
            cc.dropEnabled = !cc.dropEnabled;
            b.setMessage(dropText());
        }).dimensions(cx - 100, 146, 98, 20).build();
        addDrawableChild(dropButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Chronione sloty"), b -> {
            applyFields();
            client.setScreen(new ProtectedSlotsScreen());
        }).dimensions(cx + 2, 146, 98, 20).build());

        toggleButton = ButtonWidget.builder(toggleText(), b -> {
            AutoSklepClient.setEnabled(!AutoSklepClient.enabled);
            b.setMessage(toggleText());
        }).dimensions(cx - 100, 170, 200, 20).build();
        addDrawableChild(toggleButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz i zamknij"), b -> close())
                .dimensions(cx - 100, 194, 200, 20).build());
    }

    private Text toggleText() {
        return Text.literal("Mod: " + (AutoSklepClient.enabled ? "WŁĄCZONY" : "WYŁĄCZONY"));
    }

    private Text dropText() {
        return Text.literal("Wyrzut: " + (Config.get().dropEnabled ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int x1 = cx - 205;
        int x2 = cx + 5;
        int gray = 0xAAAAAA;

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("AutoSklep"), cx, 8, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Komenda sklepu (bez /), np. sklep lub repair"), cx - 100, 24, gray);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Kliknięć w miedź"), x1, 74, gray);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Próby sprzedaży"), x2, 74, gray);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Opóźn. klik. (tick)"), x1, 98, gray);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Opóźn. wyrzutu (tick)"), x2, 98, gray);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Sprzedaż co (s)"), x1, 122, gray);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Wyrzut co (s)"), x2, 122, gray);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Wyrzut usuwa WSZYSTKO poza miedzią i chronionymi slotami!"),
                cx, 220, 0xFF5555);
    }

    private static int parse(String s, int def, int min, int max) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return def;
        }
    }

    private void applyFields() {
        Config c = Config.get();
        String cmd = cmdField.getText().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.isEmpty()) c.command = cmd;
        c.copperClicks = parse(clicksField.getText(), c.copperClicks, 1, 20);
        c.maxRetries = parse(retriesField.getText(), c.maxRetries, 1, 20);
        c.clickDelay = parse(delayField.getText(), c.clickDelay, 0, 100);
        c.dropDelayTicks = parse(dropDelayField.getText(), c.dropDelayTicks, 0, 40);
        c.sellIntervalSeconds = parse(sellIntField.getText(), c.sellIntervalSeconds, 0, 3600);
        c.dropIntervalSeconds = parse(dropIntField.getText(), c.dropIntervalSeconds, 0, 3600);
        Config.save();
    }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
