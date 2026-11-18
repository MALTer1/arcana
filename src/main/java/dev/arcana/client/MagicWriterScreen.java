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
        new Choice("summoning","Summoning"), new Choice("enhancement","Enhancement"),
        new Choice("teleportation","Teleport"), new Choice("binding","Binding")
    );

    private final Set<String> selected = new LinkedHashSet<>();
    private final List<ButtonWidget> componentButtons = new ArrayList<>();
    private TextFieldWidget spellName;

    public MagicWriterScreen(MagicWriterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 430;
        backgroundHeight = 276;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 160;
        titleX = 14;
        titleY = 10;
    }

    @Override
    protected void init() {
        super.init();
        componentButtons.clear();
        for (int i = 0; i < CHOICES.size(); i++) {
            final Choice choice = CHOICES.get(i);
            int col = i % 3;
            int row = i / 3;
            ButtonWidget button = ButtonWidget.builder(buttonText(choice), b -> toggle(choice))
                .dimensions(x + 132 + col * 88, y + 48 + row * 18, 84, 17).build();
            componentButtons.add(button);
            addDrawableChild(button);
        }

        spellName = new TextFieldWidget(textRenderer, x + 132, y + 199, 174, 20, Text.literal("Spell name"));
        spellName.setMaxLength(32);
        spellName.setPlaceholder(Text.literal("Name this spell"));
        addDrawableChild(spellName);

        addDrawableChild(ButtonWidget.builder(Text.literal("Engrave"), b -> engrave())
            .dimensions(x + 312, y + 199, 84, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), b -> {
            selected.clear();
            refreshButtons();
        }).dimensions(x + 312, y + 225, 84, 18).build());
    }

    private Text buttonText(Choice choice) {
        return Text.literal((selected.contains(choice.id()) ? "✓ " : "+ ") + choice.label());
    }

    private void toggle(Choice choice) {
        if (selected.contains(choice.id())) selected.remove(choice.id());
        else if (selected.size() < 5) selected.add(choice.id());
        refreshButtons();
    }

    private void refreshButtons() {
        for (int i = 0; i < componentButtons.size(); i++) {
            componentButtons.get(i).setMessage(buttonText(CHOICES.get(i)));
        }
    }

    private void engrave() {
        String ids = String.join(",", selected);
        ClientPlayNetworking.send(new MagicWriterNetwork.EngravePayload(
            handler.syncId, spellName.getText(), ids));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // A bespoke dark-wood and enchanted-parchment interface, drawn at runtime
        // so it stays crisp at every GUI scale and needs no external texture pack.
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xEE171522);
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFF30263A);
        context.fill(x + 7, y + 7, x + backgroundWidth - 7, y + 34, 0xFF211B2C);
        context.fill(x + 8, y + 38, x + 122, y + 153, 0xFF211B2B);
        context.fill(x + 126, y + 38, x + 422, y + 190, 0xFF292238);
        context.fill(x + 126, y + 194, x + 422, y + 250, 0xFF211B2B);
        // Fine gold edging.
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 5, 0xFFB58A55);
        context.fill(x + 3, y + backgroundHeight - 5, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFFB58A55);
        context.fill(x + 3, y + 3, x + 5, y + backgroundHeight - 3, 0xFFB58A55);
        context.fill(x + backgroundWidth - 5, y + 3, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFFB58A55);
        context.fill(x + 126, y + 36, x + 422, y + 37, 0xFF80618C);
        context.fill(x + 126, y + 192, x + 422, y + 193, 0xFF80618C);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, "MAGIC WRITER", 14, 12, 0xF5D9A6);
        context.drawTextWithShadow(textRenderer, "WORKING SLOTS", 16, 43, 0xD8B98A);
        context.drawTextWithShadow(textRenderer, "GRIMOIRE", 16, 58, 0xE8DCCB);
        context.drawTextWithShadow(textRenderer, "MAGIC PAPER", 16, 103, 0xE8DCCB);
        context.drawTextWithShadow(textRenderer, "SPELL COMPONENTS  •  " + selected.size() + "/5", 134, 40, 0xD8B98A);
        String preview = selected.isEmpty() ? "Select at least two components to begin." :
            "Selected: " + String.join(" + ", selected);
        context.drawTextWithShadow(textRenderer, preview, 134, 178, 0xE6D8C4);
        context.drawTextWithShadow(textRenderer, "SPELL NAME", 134, 189, 0xD8B98A);
        context.drawTextWithShadow(textRenderer, "Place a Grimoire and Magic Paper in the two slots.", 16, 143, 0xBCAFC7);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean shouldPause() { return false; }
}
