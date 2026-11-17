package dev.arcana.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MagicWriterScreen extends Screen {
    private static final String[][] COMPONENTS = {
        {"projectile", "Projectile"}, {"beam", "Beam"}, {"wave", "Wave"},
        {"barrier", "Barrier"}, {"field", "Field"}, {"fire", "Fire"},
        {"water", "Water"}, {"wind", "Wind"}, {"earth", "Earth"},
        {"lightning", "Lightning"}, {"ice", "Ice"}, {"single_target", "Single Target"},
        {"multiple_targets", "Multiple Targets"}, {"self", "Self"}, {"area", "Area"},
        {"explosion", "Explosion"}, {"healing", "Healing"}, {"summoning", "Summoning"},
        {"enhancement", "Enhancement"}, {"teleportation", "Teleportation"}, {"binding", "Binding"}
    };

    private TextFieldWidget spellName;

    public MagicWriterScreen() {
        super(Text.literal("Magic Writer"));
    }

    @Override
    protected void init() {
        int left = (width - 342) / 2;
        int top = (height - 286) / 2;
        int buttonWidth = 110;
        int buttonHeight = 18;
        for (int i = 0; i < COMPONENTS.length; i++) {
            final String id = COMPONENTS[i][0];
            String label = COMPONENTS[i][1];
            int col = i % 3;
            int row = i / 3;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button ->
                sendCommand("arcana select " + id))
                .dimensions(left + col * 116, top + 48 + row * 20, buttonWidth, buttonHeight).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear draft"), button -> sendCommand("arcana clear"))
            .dimensions(left, top + 198, 108, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Preview"), button -> sendCommand("arcana preview"))
            .dimensions(left + 117, top + 198, 108, 20).build());

        spellName = new TextFieldWidget(textRenderer, left, top + 230, 225, 20, Text.literal("Spell name"));
        spellName.setMaxLength(32);
        spellName.setPlaceholder(Text.literal("Name your spell"));
        addDrawableChild(spellName);

        addDrawableChild(ButtonWidget.builder(Text.literal("Engrave"), button -> {
            String name = spellName.getText().trim();
            if (!name.isEmpty()) sendCommand("arcana engrave " + name);
        }).dimensions(left + 234, top + 230, 108, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(left + 234, top + 198, 108, 20).build());
    }

    private void sendCommand(String command) {
        if (client != null && client.player != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = (width - 342) / 2;
        int top = (height - 286) / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Select spell components (maximum 5):", left, top + 34, 0xD6C8FF);
        context.drawTextWithShadow(textRenderer, "Your draft and validation feedback appear in chat.", left, top + 260, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
