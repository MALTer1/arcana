package dev.arcana.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class GrimoireScreen extends Screen {
    private TextFieldWidget spellName;

    public GrimoireScreen() {
        super(Text.literal("Grimoire"));
    }

    @Override
    protected void init() {
        int panelWidth = 310;
        int left = (width - panelWidth) / 2;
        int top = (height - 220) / 2;

        spellName = new TextFieldWidget(textRenderer, left + 18, top + 76, 274, 20, Text.literal("Spell name"));
        spellName.setMaxLength(32);
        spellName.setPlaceholder(Text.literal("Spell page name"));
        addDrawableChild(spellName);

        addDrawableChild(ButtonWidget.builder(Text.literal("View pages"), b -> send("arcana grimoire list"))
            .dimensions(left + 18, top + 108, 130, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Insert page"), b -> withName("arcana grimoire insert "))
            .dimensions(left + 162, top + 108, 130, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Remove page"), b -> withName("arcana grimoire remove "))
            .dimensions(left + 18, top + 138, 130, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cast spell"), b -> withName("arcana grimoire cast "))
            .dimensions(left + 162, top + 138, 130, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
            .dimensions(left + 105, top + 178, 100, 20).build());
    }

    private void withName(String prefix) {
        String name = spellName.getText().trim();
        if (!name.isEmpty()) send(prefix + name);
    }

    private void send(String command) {
        if (client != null && client.player != null) client.player.networkHandler.sendChatCommand(command);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (width - 310) / 2;
        int top = (height - 220) / 2;
        context.fill(left, top, left + 310, top + 220, 0xEE241B32);
        context.fill(left + 4, top + 4, left + 306, top + 216, 0xFFDDC69A);
        context.fill(left + 8, top + 8, left + 302, top + 212, 0xFF392A42);
        context.fill(left + 12, top + 12, left + 298, top + 208, 0xFFF0DDB5);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 20, 0x38233F);
        context.drawCenteredTextWithShadow(textRenderer, "A book of engraved spell pages", width / 2, top + 43, 0x60452F);
        context.drawTextWithShadow(textRenderer, "Hold this grimoire while using its pages.", left + 18, top + 62, 0x60452F);
        context.drawCenteredTextWithShadow(textRenderer, "Page changes are saved to your world.", width / 2, top + 202, 0x60452F);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
