package dev.arcana.client;

import dev.arcana.network.MagicWriterNetwork;
import dev.arcana.item.ArcanaItems;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellPageStore;
import dev.arcana.spell.ComponentRegistry;
import dev.arcana.spell.SpellCostCalculator;
import dev.arcana.screen.MagicWriterScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MagicWriterScreen extends HandledScreen<MagicWriterScreenHandler> {
    private record Choice(String id, String label) {}
    private static List<Choice> choices() {
        return ComponentRegistry.all().stream()
            .map(component -> new Choice(component.id(), component.displayName()))
            .toList();
    }
    private static final int COLUMNS = 3, VISIBLE_ROWS = 4;
    private static final int COMPONENT_X = 330, COMPONENT_Y = 43, BUTTON_W = 70, COLUMN_GAP = 73, ROW_GAP = 20;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<ButtonWidget> componentButtons = new ArrayList<>();
    private TextFieldWidget spellName;
    private int scrollRow;
    private boolean legendaryDraft;

    public MagicWriterScreen(MagicWriterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 560;
        backgroundHeight = 286;
        playerInventoryTitleX = 199;
        playerInventoryTitleY = 171;
        titleX = 12;
        titleY = 8;
    }

    @Override protected void init() {
        super.init();
        componentButtons.clear();
        List<Choice> choices = choices();
        int rows = (choices.size() + COLUMNS - 1) / COLUMNS;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows - VISIBLE_ROWS)));
        for (Choice choice : choices) {
            ButtonWidget button = ButtonWidget.builder(buttonText(choice), b -> toggle(choice))
                .dimensions(x + COMPONENT_X, y + COMPONENT_Y, BUTTON_W, 18).build();
            componentButtons.add(button);
            addDrawableChild(button);
        }
        positionComponentButtons();

        spellName = new TextFieldWidget(textRenderer, x + 164, y + 59, 146, 20, Text.literal("Spell name"));
        spellName.setMaxLength(32);
        spellName.setPlaceholder(Text.literal("Spell name"));
        ItemStack paper = handler.getWriterInventory().getStack(1);
        SpellBookStore.StoredSpell existing = paper.isOf(ArcanaItems.MAGIC_PAPER) ? SpellPageStore.get(paper) : null;
        legendaryDraft = existing != null && existing.legendary;
        if (existing != null) {
            spellName.setText(existing.name);
            selected.addAll(existing.components);
            refreshButtons();
        }
        addDrawableChild(spellName);
        addDrawableChild(ButtonWidget.builder(Text.literal("✦ ENGRAVE"), b -> engrave())
            .dimensions(x + 164, y + 91, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CLEAR"), b -> {
            selected.clear(); refreshButtons();
        }).dimensions(x + 242, y + 91, 68, 20).build());
    }

    private void positionComponentButtons() {
        for (int i = 0; i < componentButtons.size(); i++) {
            int col = i % COLUMNS, row = i / COLUMNS - scrollRow;
            ButtonWidget b = componentButtons.get(i);
            b.setPosition(x + COMPONENT_X + col * COLUMN_GAP, y + COMPONENT_Y + row * ROW_GAP);
            b.visible = row >= 0 && row < VISIBLE_ROWS;
            b.active = b.visible;
        }
    }
    private Text buttonText(Choice c) { return Text.literal((selected.contains(c.id()) ? "✦ " : "+ ") + c.label()); }
    private void toggle(Choice c) {
        if (selected.contains(c.id())) selected.remove(c.id());
        else if (selected.size() < (legendaryDraft ? 8 : 5)) selected.add(c.id());
        refreshButtons();
    }
    private void refreshButtons() {
        for (int i = 0; i < componentButtons.size(); i++) componentButtons.get(i).setMessage(buttonText(choices().get(i)));
    }
    private void scrollComponents(int amount) {
        int rows = (choices().size() + COLUMNS - 1) / COLUMNS;
        scrollRow = Math.max(0, Math.min(Math.max(0, rows - VISIBLE_ROWS), scrollRow + amount));
        positionComponentButtons();
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x + 324 && mouseX <= x + 552 && mouseY >= y + 24 && mouseY <= y + 137) {
            if (verticalAmount != 0) scrollComponents(verticalAmount < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    private void engrave() {
        ClientPlayNetworking.send(new MagicWriterNetwork.EngravePayload(handler.syncId, spellName.getText(), String.join(",", selected)));
    }

    @Override protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        c.fill(x, y, x + 560, y + 286, 0xFF100D18);
        c.fill(x + 3, y + 3, x + 557, y + 283, 0xFF59402E);
        c.fill(x + 6, y + 6, x + 554, y + 280, 0xFF211A2A);
        c.fill(x + 10, y + 24, x + 148, y + 158, 0xFF302438);
        c.fill(x + 154, y + 24, x + 318, y + 158, 0xFF302438);
        c.fill(x + 324, y + 24, x + 552, y + 158, 0xFF35283D);
        c.fill(x + 10, y + 166, x + 552, y + 278, 0xFF2B2133);
        c.fill(x + 3, y + 3, x + 557, y + 5, 0xFFE1B76C);
        c.fill(x + 3, y + 281, x + 557, y + 283, 0xFFE1B76C);
        c.fill(x + 3, y + 3, x + 5, y + 283, 0xFFE1B76C);
        c.fill(x + 555, y + 3, x + 557, y + 283, 0xFFE1B76C);
        int totalRows = (choices().size() + COLUMNS - 1) / COLUMNS;
        int maxRows = Math.max(0, totalRows - VISIBLE_ROWS);
        c.fill(x + 545, y + 43, x + 548, y + 119, 0xFF211727);
        int thumb = Math.max(12, 76 * VISIBLE_ROWS / Math.max(VISIBLE_ROWS, totalRows));
        int thumbY = y + 43 + (maxRows == 0 ? 0 : (76 - thumb) * scrollRow / maxRows);
        c.fill(x + 545, thumbY, x + 548, thumbY + thumb, 0xFFE1B76C);
    }

    @Override protected void drawSlot(DrawContext context, Slot slot) {
        // HandledScreen already translated the context by the screen origin.
        // Slot coordinates here are local, so do not add x/y again.
        int sx = slot.x, sy = slot.y;
        boolean writer = slot.id < 3;
        int border = writer ? (slot.id == 1 ? 0xFF9BC7D2 : 0xFFE6C477) : 0xFF9A805C;
        int fill = writer ? (slot.id == 1 ? 0xFF263C4A : 0xFF49304F) : 0xFF3B3040;
        context.fill(sx - 2, sy - 2, sx + 18, sy + 18, 0xFF100B17);
        context.fill(sx - 1, sy - 1, sx + 17, sy + 17, border);
        context.fill(sx, sy, sx + 16, sy + 16, fill);
        context.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF211A2A);
        super.drawSlot(context, slot);
    }

    @Override protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawTextWithShadow(textRenderer, "✧ MAGIC WRITER ✧", 12, 8, 0xFFFFD98D);
        c.drawTextWithShadow(textRenderer, "GRIMOIRE", 16, 31, 0xFFF1DCA9);
        c.drawTextWithShadow(textRenderer, "MAGIC PAPER", 16, 76, 0xFFB9DDE7);
        c.drawTextWithShadow(textRenderer, "OUTPUT", 88, 58, 0xFFFFD98D);
        c.drawTextWithShadow(textRenderer, "SPELL DETAILS", 164, 34, 0xFFFFD98D);
        c.drawTextWithShadow(textRenderer, "SPELL NAME", 164, 49, 0xFFFFD98D);
        c.drawTextWithShadow(textRenderer, "COMPONENTS " + selected.size() + "/" + (legendaryDraft ? 8 : 5), 330, 28, 0xFFFFD98D);
        c.drawTextWithShadow(textRenderer, "ESTIMATED ARCANA: " + SpellCostCalculator.calculate(new ArrayList<>(selected), false).total(), 164, 119, 0xFFB9DDE7);
        c.drawTextWithShadow(textRenderer, "INVENTORY", 199, 171, 0xFFD8C4A4);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (spellName != null && spellName.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { spellName.setFocused(false); return true; }
            spellName.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { scrollComponents(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) { scrollComponents(-1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
    @Override public boolean shouldPause() { return false; }
}