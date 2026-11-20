package dev.arcana.client;

import dev.arcana.network.MagicWriterNetwork;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.spell.*;
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
import java.util.*;

public final class MagicWriterScreen extends HandledScreen<MagicWriterScreenHandler> {
    private record Choice(String id, String label, String description) {}
    private static volatile Set<String> knownIds = Set.of();
    private static MagicWriterScreen active;

    public static void receiveKnownComponents(String ids) {
        Set<String> parsed = new LinkedHashSet<>();
        if (ids != null && !ids.isBlank()) for (String id : ids.split(",")) {
            if (ComponentRegistry.get(id) != null) parsed.add(id);
        }
        knownIds = Set.copyOf(parsed);
        if (active != null) active.clearAndInit();
    }

    private static List<Choice> choices() {
        return ComponentRegistry.all().stream()
            .filter(component -> knownIds.contains(component.id()))
            .map(component -> new Choice(component.id(), component.displayName(), component.description()))
            .toList();
    }

    private static final int BASE_COLUMNS = 3, BASE_ROWS = 4;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<ButtonWidget> componentButtons = new ArrayList<>();
    private TextFieldWidget spellName;
    private TextFieldWidget search;
    private ButtonWidget browseButton;
    private ButtonWidget engraveButton;
    private ButtonWidget clearButton;
    private int scrollRow;
    private boolean legendaryDraft;
    private boolean browseMode;

    public MagicWriterScreen(MagicWriterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        active = this;
        ClientPlayNetworking.send(new MagicWriterNetwork.RequestKnownPayload());
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
        for (Choice choice : choices) {
            final int buttonIndex = componentButtons.size();
            ButtonWidget button = ButtonWidget.builder(buttonText(choice), b -> {
                List<Choice> current = filteredChoices();
                if (buttonIndex < current.size()) toggle(current.get(buttonIndex));
            }).dimensions(x + 330, y + 68, 70, 18).build();
            componentButtons.add(button);
            addDrawableChild(button);
        }
        search = new TextFieldWidget(textRenderer, x + 330, y + 43, 212, 19, Text.literal("Search known components"));
        search.setMaxLength(40);
        search.setPlaceholder(Text.literal("Search known components..."));
        search.setChangedListener(value -> positionComponentButtons());
        addDrawableChild(search);

        browseButton = ButtonWidget.builder(Text.literal("▦ BROWSE"), b -> {
            browseMode = !browseMode;
            positionComponentButtons();
        }).dimensions(x + 470, y + 7, 72, 18).build();
        addDrawableChild(browseButton);

        spellName = new TextFieldWidget(textRenderer, x + 164, y + 59, 146, 20, Text.literal("Spell name"));
        spellName.setMaxLength(32);
        spellName.setPlaceholder(Text.literal("Spell name"));
        ItemStack paper = handler.getWriterInventory().getStack(1);
        SpellBookStore.StoredSpell existing = paper.isOf(ArcanaItems.MAGIC_PAPER) ? SpellPageStore.get(paper) : null;
        legendaryDraft = existing != null && existing.legendary;
        if (existing != null) {
            spellName.setText(existing.name);
            selected.addAll(existing.components);
        }
        addDrawableChild(spellName);
        engraveButton = ButtonWidget.builder(Text.literal("✦ ENGRAVE"), b -> engrave())
            .dimensions(x + 164, y + 91, 72, 20).build();
        addDrawableChild(engraveButton);
        clearButton = ButtonWidget.builder(Text.literal("CLEAR"), b -> {
            selected.clear(); refreshButtons();
        }).dimensions(x + 242, y + 91, 68, 20).build();
        addDrawableChild(clearButton);
        positionComponentButtons();
    }

    private List<Choice> filteredChoices() {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        return choices().stream().filter(choice -> query.isEmpty()
            || choice.label().toLowerCase(Locale.ROOT).contains(query)
            || choice.id().toLowerCase(Locale.ROOT).contains(query)
            || choice.description().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private void positionComponentButtons() {
        List<Choice> filtered = filteredChoices();
        int columns = browseMode ? 5 : BASE_COLUMNS;
        int rows = browseMode ? 4 : BASE_ROWS;
        int totalRows = (filtered.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - rows);
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
        int startX = browseMode ? 44 : x + 330;
        int startY = browseMode ? 76 : y + 67;
        int gapX = browseMode ? Math.max(112, Math.min(145, (width - 100) / 5)) : 73;
        int gapY = browseMode ? 34 : 20;
        int buttonW = browseMode ? gapX - 7 : 70;
        for (int i = 0; i < componentButtons.size(); i++) {
            ButtonWidget button = componentButtons.get(i);
            if (i >= filtered.size()) {
                button.visible = false;
                button.active = false;
                continue;
            }
            int row = i / columns - scrollRow;
            int col = i % columns;
            button.setPosition(startX + col * gapX, startY + row * gapY);
            button.setWidth(buttonW);
            button.visible = row >= 0 && row < rows;
            button.active = button.visible;
            button.setMessage(buttonText(filtered.get(i)));
        }
        if (search != null) {
            search.setPosition(browseMode ? (width - 340) / 2 : x + 330, browseMode ? 42 : y + 43);
            search.setWidth(browseMode ? 340 : 212);
        }
        if (browseButton != null) {
            browseButton.setPosition(browseMode ? width - 96 : x + 470, browseMode ? 24 : y + 7);
            browseButton.setMessage(Text.literal(browseMode ? "CLOSE" : "▦ BROWSE"));
        }
    }

    private int componentLimit() {
        if (legendaryDraft) return 8;
        GrimoireTier tier = GrimoireTier.fromItem(handler.getWriterInventory().getStack(0).getItem());
        return tier == null ? 5 : tier.maxComponents();
    }
    private Text buttonText(Choice choice) {
        String label = choice.label();
        return Text.literal((selected.contains(choice.id()) ? "✦ " : "+ ") + label);
    }
    private void toggle(Choice choice) {
        if (selected.contains(choice.id())) selected.remove(choice.id());
        else if (selected.size() < componentLimit()) selected.add(choice.id());
        refreshButtons();
    }
    private void refreshButtons() {
        for (ButtonWidget button : componentButtons) {
            // Labels are refreshed from the currently filtered list in positionComponentButtons.
            button.setMessage(button.getMessage());
        }
        positionComponentButtons();
    }
    private void scrollComponents(int amount) {
        scrollRow += amount;
        positionComponentButtons();
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean overGrid = browseMode
            ? mouseX >= 25 && mouseX <= width - 25 && mouseY >= 70 && mouseY <= height - 40
            : mouseX >= x + 324 && mouseX <= x + 552 && mouseY >= y + 60 && mouseY <= y + 155;
        if (overGrid && verticalAmount != 0) {
            scrollComponents(verticalAmount < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    private void engrave() {
        ClientPlayNetworking.send(new MagicWriterNetwork.EngravePayload(handler.syncId, spellName.getText(), String.join(",", selected)));
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + 560, y + 286, 0xFF100D18);
        context.fill(x + 3, y + 3, x + 557, y + 283, 0xFF59402E);
        context.fill(x + 6, y + 6, x + 554, y + 280, 0xFF211A2A);
        context.fill(x + 10, y + 24, x + 148, y + 158, 0xFF302438);
        context.fill(x + 154, y + 24, x + 318, y + 158, 0xFF302438);
        context.fill(x + 324, y + 24, x + 552, y + 158, 0xFF35283D);
        context.fill(x + 10, y + 166, x + 552, y + 278, 0xFF2B2133);
        context.fill(x + 3, y + 3, x + 557, y + 5, 0xFFE1B76C);
        context.fill(x + 3, y + 281, x + 557, y + 283, 0xFFE1B76C);
        context.fill(x + 3, y + 3, x + 5, y + 283, 0xFFE1B76C);
        context.fill(x + 555, y + 3, x + 557, y + 283, 0xFFE1B76C);
        if (browseMode) {
            context.fill(0, 0, width, height, 0xFF100D18);
            int panelX = 20, panelY = 18, panelW = width - 40, panelH = height - 36;
            context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF59402E);
            context.fill(panelX + 3, panelY + 3, panelX + panelW - 3, panelY + panelH - 3, 0xFF211A2A);
            context.fill(panelX + 12, panelY + 65, panelX + panelW - 12, panelY + panelH - 42, 0xFF302438);
            context.drawCenteredTextWithShadow(textRenderer, "KNOWN COMPONENTS", width / 2, 23, 0xFFFFD98D);
            context.drawCenteredTextWithShadow(textRenderer, "5 × 4 BROWSER  •  SCROLL TO EXPLORE", width / 2, height - 28, 0xFFB9DDE7);
        }
    }

    @Override protected void drawSlot(DrawContext context, Slot slot) {
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

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        if (browseMode) return;
        context.drawTextWithShadow(textRenderer, "✧ MAGIC WRITER ✧", 12, 8, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "GRIMOIRE", 16, 31, 0xFFF1DCA9);
        context.drawTextWithShadow(textRenderer, "MAGIC PAPER", 16, 76, 0xFFB9DDE7);
        context.drawTextWithShadow(textRenderer, "OUTPUT", 88, 58, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "SPELL DETAILS", 164, 34, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "SPELL NAME", 164, 49, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "KNOWN " + choices().size() + "  •  SELECTED " + selected.size() + "/" + componentLimit(),
            330, 28, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "ESTIMATED ARCANA: " + SpellCostCalculator.calculate(new ArrayList<>(selected), legendaryDraft).total(),
            164, 119, 0xFFB9DDE7);
        context.drawTextWithShadow(textRenderer, "INVENTORY", 199, 171, 0xFFD8C4A4);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!browseMode) return super.mouseClicked(mouseX, mouseY, button);
        if (search != null && search.mouseClicked(mouseX, mouseY, button)) return true;
        if (browseButton != null && browseButton.mouseClicked(mouseX, mouseY, button)) return true;
        for (ButtonWidget componentButton : componentButtons) {
            if (componentButton.visible && componentButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        // Consume clicks on the overlay so hidden writer slots and inventory
        // cannot receive input through the browser.
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!browseMode) return super.mouseReleased(mouseX, mouseY, button);
        if (search != null) search.mouseReleased(mouseX, mouseY, button);
        if (browseButton != null) browseButton.mouseReleased(mouseX, mouseY, button);
        for (ButtonWidget componentButton : componentButtons) {
            if (componentButton.visible) componentButton.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (spellName != null && spellName.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { spellName.setFocused(false); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && browseMode) { browseMode = false; positionComponentButtons(); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { scrollComponents(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) { scrollComponents(-1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (browseMode) {
            // Draw the browser over the entire handled screen, including the
            // underlying writer slots and inventory, then redraw its controls.
            context.fill(0, 0, width, height, 0xFF100D18);
            int panelX = 20, panelY = 18, panelW = width - 40, panelH = height - 36;
            context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF59402E);
            context.fill(panelX + 3, panelY + 3, panelX + panelW - 3, panelY + panelH - 3, 0xFF211A2A);
            context.fill(panelX + 12, panelY + 65, panelX + panelW - 12, panelY + panelH - 42, 0xFF302438);
            context.drawCenteredTextWithShadow(textRenderer, "KNOWN COMPONENTS", width / 2, 23, 0xFFFFD98D);
            context.drawCenteredTextWithShadow(textRenderer, "5 × 4 BROWSER  •  SCROLL TO EXPLORE", width / 2, height - 28, 0xFFB9DDE7);
            if (search != null) search.render(context, mouseX, mouseY, delta);
            if (browseButton != null) browseButton.render(context, mouseX, mouseY, delta);
            for (ButtonWidget button : componentButtons) if (button.visible) button.render(context, mouseX, mouseY, delta);
            List<Choice> filtered = filteredChoices();
            int cols = 5;
            int first = scrollRow * cols;
            for (int i = first; i < Math.min(first + 20, filtered.size()); i++) {
                Choice choice = filtered.get(i);
                int col = (i - first) % cols;
                int row = (i - first) / cols;
                int cellX = 44 + col * Math.max(112, Math.min(145, (width - 100) / 5));
                int cellY = 76 + row * 34;
                if (mouseX >= cellX && mouseX <= cellX + 100 && mouseY >= cellY && mouseY <= cellY + 28) {
                    context.drawTooltip(textRenderer, Text.literal(choice.label() + "\n" + choice.description()), mouseX, mouseY);
                    break;
                }
            }
        }
        if (!browseMode) drawMouseoverTooltip(context, mouseX, mouseY);
    }
    @Override public void close() { if (active == this) active = null; super.close(); }
    @Override public boolean shouldPause() { return false; }
}