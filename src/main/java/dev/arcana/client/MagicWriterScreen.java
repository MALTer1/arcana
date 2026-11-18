package dev.arcana.client;

import dev.arcana.network.MagicWriterNetwork;
import dev.arcana.screen.MagicWriterScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MagicWriterScreen extends HandledScreen<MagicWriterScreenHandler> {
    private record Choice(String id, String label) {}
    private static final List<Choice> CHOICES = List.of(
        new Choice("projectile","Projectile"), new Choice("beam","Beam"), new Choice("wave","Wave"),
        new Choice("barrier","Barrier"), new Choice("field","Field"),
        new Choice("fire","Fire"), new Choice("water","Water"), new Choice("wind","Wind"),
        new Choice("earth","Earth"), new Choice("lightning","Lightning"), new Choice("ice","Ice"),
        new Choice("single_target","Single Target"), new Choice("multiple_targets","Multi Target"),
        new Choice("self","Self"), new Choice("area","Area"),
        new Choice("explosion","Explosion"), new Choice("healing","Healing"),
        new Choice("summoning","Summoning"), new Choice("enhancement","Enhance"),
        new Choice("teleportation","Teleport"), new Choice("binding","Binding")
    );

    private final Set<String> selected = new LinkedHashSet<>();
    private final List<ButtonWidget> componentButtons = new ArrayList<>();
    private TextFieldWidget spellName;

    public MagicWriterScreen(MagicWriterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 390;
        backgroundHeight = 236;
        playerInventoryTitleX = 104;
        playerInventoryTitleY = 145;
        titleX = 14;
        titleY = 10;
    }

    @Override
    protected void init() {
        super.init();
        componentButtons.clear();
        for (int i = 0; i < CHOICES.size(); i++) {
            Choice choice = CHOICES.get(i);
            int col = i % 3;
            int row = i / 3;
            ButtonWidget button = ButtonWidget.builder(buttonText(choice), b -> toggle(choice))
                .dimensions(x + 130 + col * 86, y + 34 + row * 15, 80, 14).build();
            componentButtons.add(button);
            addDrawableChild(button);
        }
        spellName = new TextFieldWidget(textRenderer, x + 274, y + 155, 104, 18, Text.literal("Spell name"));
        spellName.setMaxLength(32);
        spellName.setPlaceholder(Text.literal("Spell name"));
        addDrawableChild(spellName);
        addDrawableChild(ButtonWidget.builder(Text.literal("✦ ENGRAVE"), b -> engrave())
            .dimensions(x + 274, y + 180, 104, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CLEAR"), b -> {
            selected.clear();
            refreshButtons();
        }).dimensions(x + 274, y + 203, 104, 16).build());
    }

    private Text buttonText(Choice choice) {
        return Text.literal((selected.contains(choice.id()) ? "✦ " : "＋ ") + choice.label());
    }
    private void toggle(Choice choice) {
        if (selected.contains(choice.id())) selected.remove(choice.id());
        else if (selected.size() < 5) selected.add(choice.id());
        refreshButtons();
    }
    private void refreshButtons() {
        for (int i = 0; i < componentButtons.size(); i++) componentButtons.get(i).setMessage(buttonText(CHOICES.get(i)));
    }
    private void engrave() {
        ClientPlayNetworking.send(new MagicWriterNetwork.EngravePayload(
            handler.syncId, spellName.getText(), String.join(",", selected)));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Dark walnut frame and separate parchment panels.
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF100D18);
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFF59402E);
        context.fill(x + 6, y + 6, x + backgroundWidth - 6, y + backgroundHeight - 6, 0xFF211A2A);
        context.fill(x + 10, y + 18, x + 120, y + 139, 0xFF302438);
        context.fill(x + 124, y + 18, x + 382, y + 143, 0xFF35283D);
        context.fill(x + 98, y + 142, x + 270, y + 232, 0xFF2B2133);
        context.fill(x + 274, y + 148, x + 380, y + 228, 0xFF241B30);

        // Brass outer trim.
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 5, 0xFFE1B76C);
        context.fill(x + 3, y + backgroundHeight - 5, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFFE1B76C);
        context.fill(x + 3, y + 3, x + 5, y + backgroundHeight - 3, 0xFFE1B76C);
        context.fill(x + backgroundWidth - 5, y + 3, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFFE1B76C);

        // Custom slot wells are drawn behind the real ItemStack slots.
        ornateSlot(context, x + 44, y + 62, true);
        ornateSlot(context, x + 44, y + 106, false);
        context.fill(x + 16, y + 94, x + 108, y + 96, 0xFF9C7447);
        context.fill(x + 16, y + 138, x + 108, y + 140, 0xFF9C7447);
        context.fill(x + 128, y + 30, x + 378, y + 32, 0xFFB58B58);
        context.fill(x + 102, y + 152, x + 264, y + 154, 0xFFB58B58);
    }

    private void ornateSlot(DrawContext context, int sx, int sy, boolean grimoire) {
        int outer = grimoire ? 0xFFE6C477 : 0xFF9BC7D2;
        int inner = grimoire ? 0xFF49304F : 0xFF263C4A;
        context.fill(sx - 5, sy - 5, sx + 27, sy + 27, 0xFF100B17);
        context.fill(sx - 4, sy - 4, sx + 26, sy + 26, outer);
        context.fill(sx - 2, sy - 2, sx + 24, sy + 24, inner);
        context.fill(sx, sy, sx + 22, sy + 22, grimoire ? 0xFF30213D : 0xFF1C3441);
        context.fill(sx + 2, sy + 2, sx + 20, sy + 4, grimoire ? 0xFFB99754 : 0xFF6EABB8);
        context.fill(sx + 2, sy + 18, sx + 20, sy + 20, grimoire ? 0xFFB99754 : 0xFF6EABB8);
        context.fill(sx + 2, sy + 2, sx + 4, sy + 20, grimoire ? 0xFFB99754 : 0xFF6EABB8);
        context.fill(sx + 18, sy + 2, sx + 20, sy + 20, grimoire ? 0xFFB99754 : 0xFF6EABB8);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, "✧ MAGIC WRITER ✧", 14, 8, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "RELIC SLOT", 16, 38, 0xFFE7D5B8);
        context.drawTextWithShadow(textRenderer, "GRIMOIRE", 16, 52, 0xFFF1DCA9);
        context.drawTextWithShadow(textRenderer, "PAPER SLOT", 16, 101, 0xFFB9DDE7);
        context.drawTextWithShadow(textRenderer, "MAGIC PAPER", 16, 145 - 12, 0xFFE4D7C7);
        context.drawTextWithShadow(textRenderer, "COMPONENTS  " + selected.size() + "/5", 130, 20, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "SPELL NAME", 274, 145, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "Your inventory", 104, 145, 0xFFD8C4A4);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (spellName != null && spellName.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                spellName.setFocused(false);
                return true;
            }
            // While entering a name, consume keys here so inventory/other game
            // bindings cannot close the workstation or trigger gameplay actions.
            return spellName.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    @Override public boolean shouldPause() { return false; }
}
