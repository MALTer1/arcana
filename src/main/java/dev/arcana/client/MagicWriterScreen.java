package dev.arcana.client;

import dev.arcana.network.MagicWriterNetwork;
import dev.arcana.item.ArcanaItems;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellPageStore;
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
    private static final List<Choice> CHOICES = List.of(
        new Choice("projectile","Projectile"), new Choice("beam","Beam"), new Choice("wave","Wave"),
        new Choice("barrier","Barrier"), new Choice("field","Field"),
        new Choice("fire","Fire"), new Choice("water","Water"), new Choice("wind","Wind"),
        new Choice("earth","Earth"), new Choice("lightning","Lightning"), new Choice("ice","Ice"),
        new Choice("single_target","Single Target"), new Choice("multiple_targets","Multi Target"),
        new Choice("self","Self"), new Choice("area","Area"),
        new Choice("explosion","Explosion"), new Choice("healing","Healing"),
        new Choice("summoning","Summoning"), new Choice("enhancement","Enhance"),
        new Choice("teleportation","Teleport"), new Choice("binding","Binding"),
        new Choice("night_vision","Night Vision"), new Choice("water_breathing","Water Breathing"),
        new Choice("slow_falling","Slow Falling")
    );

    private static final int COLUMNS = 3;
    private static final int VISIBLE_ROWS = 7;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<ButtonWidget> componentButtons = new ArrayList<>();
    private TextFieldWidget spellName;
    private int scrollRow;

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
        int rows = (CHOICES.size() + COLUMNS - 1) / COLUMNS;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows - VISIBLE_ROWS)));
        for (int i = 0; i < CHOICES.size(); i++) {
            Choice choice = CHOICES.get(i);
            ButtonWidget button = ButtonWidget.builder(buttonText(choice), b -> toggle(choice))
                .dimensions(x + 130, y + 34, 80, 14).build();
            componentButtons.add(button);
            addDrawableChild(button);
        }
        positionComponentButtons();
        spellName = new TextFieldWidget(textRenderer, x + 274, y + 155, 104, 18, Text.literal("Spell name"));
        spellName.setMaxLength(32);
        spellName.setPlaceholder(Text.literal("Spell name"));
        ItemStack insertedPage = handler.getWriterInventory().getStack(1);
        SpellBookStore.StoredSpell existingPage = insertedPage.isOf(ArcanaItems.SPELL_PAGE)
            ? SpellPageStore.get(insertedPage) : null;
        if (existingPage != null) {
            spellName.setText(existingPage.name);
            selected.addAll(existingPage.components);
            refreshButtons();
        }
        addDrawableChild(spellName);
        addDrawableChild(ButtonWidget.builder(Text.literal("✦ ENGRAVE"), b -> engrave())
            .dimensions(x + 274, y + 180, 104, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CLEAR"), b -> {
            selected.clear();
            refreshButtons();
        }).dimensions(x + 274, y + 203, 104, 16).build());
    }

    private void positionComponentButtons() {
        for (int i = 0; i < componentButtons.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS - scrollRow;
            ButtonWidget button = componentButtons.get(i);
            button.setPosition(x + 130 + col * 86, y + 34 + row * 15);
            button.visible = row >= 0 && row < VISIBLE_ROWS;
            button.active = button.visible;
        }
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
        for (int i = 0; i < componentButtons.size(); i++) {
            componentButtons.get(i).setMessage(buttonText(CHOICES.get(i)));
        }
    }

    private void scrollComponents(int amount) {
        int rows = (CHOICES.size() + COLUMNS - 1) / COLUMNS;
        int max = Math.max(0, rows - VISIBLE_ROWS);
        scrollRow = Math.max(0, Math.min(max, scrollRow + amount));
        positionComponentButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x + 124 && mouseX <= x + 382 && mouseY >= y + 18 && mouseY <= y + 143) {
            if (verticalAmount != 0) scrollComponents(verticalAmount < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void engrave() {
        ClientPlayNetworking.send(new MagicWriterNetwork.EngravePayload(
            handler.syncId, spellName.getText(), String.join(",", selected)));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF100D18);
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFF59402E);
        context.fill(x + 6, y + 6, x + backgroundWidth - 6, y + backgroundHeight - 6, 0xFF211A2A);
        context.fill(x + 10, y + 18, x + 120, y + 139, 0xFF302438);
        context.fill(x + 124, y + 18, x + 382, y + 143, 0xFF35283D);
        context.fill(x + 98, y + 142, x + 270, y + 232, 0xFF2B2133);
        context.fill(x + 274, y + 148, x + 380, y + 228, 0xFF241B30);

        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 5, 0xFFE1B76C);
        context.fill(x + 3, y + backgroundHeight - 5, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFFE1B76C);
        context.fill(x + 3, y + 3, x + 5, y + backgroundHeight - 3, 0xFFE1B76C);
        context.fill(x + backgroundWidth - 5, y + 3, x + backgroundWidth - 3, y + backgroundHeight - 3, 0xFFE1B76C);

        context.fill(x + 128, y + 30, x + 378, y + 32, 0xFFB58B58);
        context.drawText(textRenderer, "OUTPUT", x + 86, y + 74, 0xFFFFD98D, false);
        context.fill(x + 102, y + 152, x + 264, y + 154, 0xFFB58B58);

        int totalRows = (CHOICES.size() + COLUMNS - 1) / COLUMNS;
        int maxRows = Math.max(0, totalRows - VISIBLE_ROWS);
        context.fill(x + 379, y + 35, x + 382, y + 139, 0xFF211727);
        int thumbHeight = Math.max(12, 104 * VISIBLE_ROWS / Math.max(VISIBLE_ROWS, totalRows));
        int thumbY = y + 35 + (maxRows == 0 ? 0 : (104 - thumbHeight) * scrollRow / maxRows);
        context.fill(x + 379, thumbY, x + 382, thumbY + thumbHeight, 0xFFE1B76C);
    }

    @Override
    protected void drawSlot(DrawContext context, Slot slot) {
        int sx = x + slot.x;
        int sy = y + slot.y;
        boolean writerSlot = slot.x < 90;
        int outer = writerSlot ? (slot.y < 90 ? 0xFFE6C477 : 0xFF9BC7D2) : 0xFF9A805C;
        int inner = writerSlot ? (slot.y < 90 ? 0xFF49304F : 0xFF263C4A) : 0xFF3B3040;
        context.fill(sx - 2, sy - 2, sx + 18, sy + 18, 0xFF100B17);
        context.fill(sx - 1, sy - 1, sx + 17, sy + 17, outer);
        context.fill(sx, sy, sx + 16, sy + 16, inner);
        context.fill(sx + 1, sy + 1, sx + 15, sy + 15, writerSlot ? 0xFF211A2A : 0xFF29212F);
        super.drawSlot(context, slot);
        context.fill(sx - 2, sy - 2, sx + 18, sy - 1, outer);
        context.fill(sx - 2, sy + 17, sx + 18, sy + 18, outer);
        context.fill(sx - 2, sy - 2, sx - 1, sy + 18, outer);
        context.fill(sx + 17, sy - 2, sx + 18, sy + 18, outer);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(textRenderer, "✧ MAGIC WRITER ✧", 14, 8, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "GRIMOIRE", 16, 52, 0xFFF1DCA9);
        context.drawTextWithShadow(textRenderer, "MAGIC PAPER / PAGE", 16, 96, 0xFFB9DDE7);
        context.drawTextWithShadow(textRenderer, "COMPONENTS  " + selected.size() + "/5", 130, 20, 0xFFFFD98D);
        context.drawTextWithShadow(textRenderer, "SCROLL TO BROWSE", 270, 132, 0xFFD7C19F);
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
            spellName.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollComponents(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollComponents(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean shouldPause() { return false; }
}
