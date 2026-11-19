package dev.arcana.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.arcana.network.GrimoireNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class GrimoireScreen extends Screen {
    private static GrimoireScreen active;
    private JsonObject state = new JsonObject();
    private int tab = 0;
    private int selected = 0;
    private int listPage = 0;
    private int glyphPage = 0;
    private boolean removeMode = false;
    private boolean editMode = false;
    private String notice = "Opening your grimoire...";

    public GrimoireScreen() {
        super(Text.literal("Grimoire"));
        active = this;
    }

    public static void receiveState(String json) {
        if (active == null) return;
        try {
            boolean firstState = active.state.size() == 0;
            active.state = JsonParser.parseString(json).getAsJsonObject();
            String message = active.state.has("message") ? active.state.get("message").getAsString() : "";
            if (!message.isBlank()) active.notice = message;
            if (firstState || !message.isBlank()) active.rebuild();
        } catch (RuntimeException ignored) {
            active.notice = "The grimoire could not read its pages.";
        }
    }

    @Override
    protected void init() {
        int left = (width - 430) / 2;
        int top = (height - 250) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Spells"), b -> changeTab(0)).dimensions(left - 58, top + 28, 54, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pages"), b -> changeTab(1)).dimensions(left - 58, top + 54, 54, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Arcana"), b -> changeTab(2)).dimensions(left - 58, top + 80, 54, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Book"), b -> changeTab(3)).dimensions(left - 58, top + 106, 54, 22).build());

        if (tab == 0) {
            JsonArray spells = array("spells");
            int start = listPage * 5;
            for (int i = start; i < Math.min(start + 5, spells.size()); i++) {
                final int index = i;
                String name = spells.get(i).getAsJsonObject().get("name").getAsString();
                addDrawableChild(ButtonWidget.builder(Text.literal((selected == i ? "◆ " : "▤ ") + name), b -> {
                    selected = index;
                    editMode = false;
                    rebuild();
                }).dimensions(left + 18, top + 64 + (i - start) * 25, 180, 21).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { listPage = Math.max(0, listPage - 1); rebuild(); })
                .dimensions(left + 18, top + 194, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> { if ((listPage + 1) * 5 < spells.size()) listPage++; rebuild(); })
                .dimensions(left + 166, top + 194, 28, 20).build());
            if (!spells.isEmpty() && selected < spells.size()) {
                JsonObject spell = spells.get(selected).getAsJsonObject();
                String name = spell.get("name").getAsString();
                addDrawableChild(ButtonWidget.builder(Text.literal("CAST"), b -> send("cast", name))
                    .dimensions(left + 230, top + 30, 66, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal(editMode ? "DONE" : "ADJUST"), b -> {
                    editMode = !editMode; glyphPage = 0; rebuild();
                }).dimensions(left + 302, top + 30, 72, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal(removeMode ? "REMOVE: ON" : "REMOVE PAGE"), b -> {
                    removeMode = !removeMode; notice = removeMode ? "Select a spell page to remove." : "Removal mode off."; rebuild();
                }).dimensions(left + 230, top + 194, 144, 20).build());
                if (spell.has("legendary") && !spell.get("legendary").getAsBoolean()) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("USE LEGENDARY SCROLL"), b -> send("legendary", name))
                        .dimensions(left + 230, top + 218, 144, 18).build());
                }
                if (editMode) buildEditor(left, top, name, spell);
                if (removeMode) {
                    // In removal mode the selected page is returned as engraved Magic Paper.
                    addDrawableChild(ButtonWidget.builder(Text.literal("CONFIRM REMOVE"), b -> send("remove", name))
                        .dimensions(left + 18, top + 218, 180, 18).build());
                }
            }
        } else if (tab == 1) {
            JsonArray pages = array("loosePages");
            for (int i = 0; i < Math.min(6, pages.size()); i++) {
                String name = pages.get(i).getAsString();
                addDrawableChild(ButtonWidget.builder(Text.literal("Insert: " + name), b -> send("insert", name))
                    .dimensions(left + 30, top + 65 + i * 25, 350, 21).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Legendary"), b -> send("legendary_page", name))
                    .dimensions(left + 330, top + 65 + i * 25, 74, 21).build());
            }
        } else if (tab == 2) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Arcana"), b -> send("refresh", ""))
                .dimensions(left + 250, top + 198, 156, 20).build());
        }
        if (!editMode) addDrawableChild(ButtonWidget.builder(Text.literal("Close book"), b -> close())
            .dimensions(left + 300, top + 8, 108, 20).build());
    }

    private void buildEditor(int left, int top, String spellName, JsonObject spell) {
        JsonArray components = spell.has("components") ? spell.getAsJsonArray("components") : new JsonArray();
        for (int i = 0; i < Math.min(8, components.size()); i++) {
            String id = components.get(i).getAsString();
            final String name = spellName;
            int column = i / 4;
            int row = i % 4;
            int cellX = left + 224 + column * 90;
            int cellY = top + 91 + row * 18;
            addDrawableChild(ButtonWidget.builder(Text.literal("− " + shortName(id)), b -> send("edit_remove", name + "|" + id))
                .dimensions(cellX, cellY, 48, 17).build());
            final String moveId = id;
            addDrawableChild(ButtonWidget.builder(Text.literal("↑"), b -> send("edit_move_up", name + "|" + moveId))
                .dimensions(cellX + 50, cellY, 16, 17).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("↓"), b -> send("edit_move_down", name + "|" + moveId))
                .dimensions(cellX + 68, cellY, 16, 17).build());
        }
        JsonArray glyphs = array("glyphs");
        int start = glyphPage * 4;
        for (int i = start; i < Math.min(start + 4, glyphs.size()); i++) {
            JsonObject glyph = glyphs.get(i).getAsJsonObject();
            String id = glyph.get("id").getAsString();
            boolean already = false;
            for (int j = 0; j < components.size(); j++) if (components.get(j).getAsString().equals(id)) already = true;
            if (!already) {
                String name = spellName;
                addDrawableChild(ButtonWidget.builder(Text.literal("+ " + glyph.get("name").getAsString()), b -> send("edit_add", name + "|" + id))
                    .dimensions(left + 224, top + 169 + (i - start) * 13, 176, 13).build());
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("‹ glyphs"), b -> { glyphPage = Math.max(0, glyphPage - 1); rebuild(); })
            .dimensions(left + 224, top + 222, 66, 17).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("glyphs ›"), b -> {
            int max = Math.max(0, (glyphs.size() - 1) / 4);
            glyphPage = Math.min(max, glyphPage + 1); rebuild();
        }).dimensions(left + 294, top + 222, 72, 17).build());
    }

    private void drawPreviewAnimation(DrawContext context, int left, int top, JsonObject spell, boolean legendary) {
        int age = MinecraftClient.getInstance().player == null ? 0 : MinecraftClient.getInstance().player.age;
        int pulse = (int)(Math.sin(age * 0.16) * 5.0);
        int color = legendary ? 0xFFFFD35A : 0xFF7545A0;
        JsonArray components = spell.has("components") ? spell.getAsJsonArray("components") : new JsonArray();
        String form = "";
        String element = "";
        for (int i = 0; i < components.size(); i++) {
            String id = components.get(i).getAsString();
            if (java.util.Set.of("projectile", "beam", "wave", "barrier", "field", "arc", "chain",
                "cone", "ring", "pulse", "orbit", "tether", "rune", "wall", "column").contains(id)
                && form.isEmpty()) form = id;
            if (java.util.Set.of("fire", "water", "wind", "earth", "lightning", "ice", "light",
                "shadow", "nature", "metal", "crystal", "magma", "smoke", "sound", "gravity",
                "time", "space", "poison", "sand", "wood", "moon", "sun").contains(id)) element = id;
        }
        if (form.isEmpty()) form = "rune";
        color = switch (element) {
            case "fire", "magma", "sun" -> 0xFFE66A32;
            case "water", "ice" -> 0xFF4AA8D8;
            case "lightning" -> 0xFFE5C94B;
            case "nature", "wood" -> 0xFF55A65B;
            case "shadow", "poison" -> 0xFF8051A8;
            case "light", "moon" -> 0xFFE6D18A;
            case "wind" -> 0xFF9BC9C6;
            default -> color;
        };
        context.fill(left + 278, top + 83, left + 362, top + 166, 0xFFDFD0B0);
        context.fill(left + 281, top + 86, left + 359, top + 163, 0xFFEEE4CD);
        int centerX = left + 320;
        int centerY = top + 122;
        switch (form) {
            case "projectile", "arc", "chain" -> {
                int travel = Math.floorMod(age * 2, 52);
                context.fill(left + 291 + travel, centerY - 3, left + 297 + travel, centerY + 3, color);
                context.fill(left + 294 + travel, centerY - 1, left + 296 + travel, centerY + 1, 0xFFFFFFFF);
            }
            case "beam" -> {
                context.fill(left + 291, centerY - 2 - pulse / 3, left + 349, centerY + 2 + pulse / 3, color);
                context.fill(left + 291, centerY - 1, left + 349, centerY + 1, 0xFFFFFFFF);
            }
            case "field", "rune", "orbit", "tether" -> {
                int radius = 12 + Math.floorMod(age, 20);
                context.fill(centerX - radius, centerY - radius, centerX + radius, centerY - radius + 2, color);
                context.fill(centerX - radius, centerY + radius - 2, centerX + radius, centerY + radius, color);
                context.fill(centerX - radius, centerY - radius, centerX - radius + 2, centerY + radius, color);
                context.fill(centerX + radius - 2, centerY - radius, centerX + radius, centerY + radius, color);
            }
            case "barrier", "wall" -> {
                context.fill(centerX - 18, centerY - 22, centerX - 14, centerY + 22, color);
                context.fill(centerX + 14, centerY - 22, centerX + 18, centerY + 22, color);
                context.fill(centerX - 18, centerY - 22, centerX + 18, centerY - 18, color);
                context.fill(centerX - 18, centerY + 18, centerX + 18, centerY + 22, color);
            }
            case "wave", "ring", "pulse", "cone", "column" -> {
                int radius = 10 + Math.floorMod(age, 24);
                context.fill(centerX - radius, centerY - radius, centerX + radius, centerY - radius + 2, color);
                context.fill(centerX - radius, centerY + radius - 2, centerX + radius, centerY + radius, color);
                context.fill(centerX - radius, centerY - radius, centerX - radius + 2, centerY + radius, color);
                context.fill(centerX + radius - 2, centerY - radius, centerX + radius, centerY + radius, color);
            }
            default -> {
                context.fill(centerX - 3, centerY - 18, centerX + 3, centerY + 18, color);
                context.fill(centerX - 18, centerY - 3, centerX + 18, centerY + 3, color);
            }
        }
        if (legendary) {
            context.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFFFFE9A0);
        }
    }

    private String shortName(String id) {
        JsonArray glyphs = array("glyphs");
        for (int i = 0; i < glyphs.size(); i++) {
            JsonObject glyph = glyphs.get(i).getAsJsonObject();
            if (glyph.get("id").getAsString().equals(id)) return glyph.get("name").getAsString();
        }
        return id;
    }
    private void changeTab(int next) { tab = next; selected = 0; listPage = 0; editMode = false; rebuild(); }
    private void rebuild() { if (client != null) { clearChildren(); init(); } }
    private JsonArray array(String key) { return state.has(key) && state.get(key).isJsonArray() ? state.getAsJsonArray(key) : new JsonArray(); }
    private void send(String action, String name) {
        if (client != null && client.player != null) {
            notice = "Turning the page...";
            ClientPlayNetworking.send(new GrimoireNetwork.ActionPayload(action, name));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (width - 430) / 2;
        int top = (height - 250) / 2;
        context.fill(0, 0, width, height, 0x880B0710);
        context.fill(left - 5, top + 5, left + 435, top + 250, 0xFF211629);
        context.fill(left, top, left + 430, top + 244, 0xFF765333);
        context.fill(left + 5, top + 5, left + 425, top + 239, 0xFF2B1D2E);
        context.fill(left + 10, top + 10, left + 210, top + 234, 0xFFF0DDB7);
        context.fill(left + 220, top + 10, left + 420, top + 234, 0xFFF4E6C8);
        context.fill(left + 214, top + 10, left + 216, top + 234, 0xFF9B7950);
        context.drawCenteredTextWithShadow(textRenderer, state.has("tier") ? state.get("tier").getAsString() : "Grimoire", left + 215, top + 17, 0xFFF0DDB7);
        String[] tabs = {"SPELLS", "LOOSE PAGES", "ARCANA", "BOOK"};
        context.drawText(textRenderer, tabs[tab], left + 24, top + 39, 0xFF5A3D28, false);

        if (tab == 0) {
            JsonArray spells = array("spells");
            context.drawText(textRenderer, "Select a spell to inspect or adjust", left + 22, top + 54, 0xFF5A3D28, false);
            if (spells.isEmpty()) {
                context.drawText(textRenderer, "This book is empty.", left + 25, top + 88, 0xFF5A3D28, false);
                context.drawText(textRenderer, "Engrave pages at the Magic Writer.", left + 25, top + 101, 0xFF5A3D28, false);
            } else if (selected < spells.size()) {
                JsonObject spell = spells.get(selected).getAsJsonObject();
                String name = spell.get("name").getAsString();
                boolean legendary = spell.has("legendary") && spell.get("legendary").getAsBoolean();
                JsonArray orderedGlyphs = spell.has("components") ? spell.getAsJsonArray("components") : new JsonArray();
                java.util.List<String> glyphNames = new java.util.ArrayList<>();
                for (int i = 0; i < orderedGlyphs.size(); i++) glyphNames.add(shortName(orderedGlyphs.get(i).getAsString()));
                String glyphOrder = textRenderer.trimToWidth("Order: " + String.join(" → ", glyphNames), 184);
                context.drawText(textRenderer, glyphOrder, left + 230, top + 72, 0xFF684D36, false);
                drawPreviewAnimation(context, left, top, spell, legendary);
                context.drawCenteredTextWithShadow(textRenderer, "SPELL PREVIEW", left + 320, top + 145, 0xFF684D36);
                context.drawCenteredTextWithShadow(textRenderer, name, left + 320, top + 58, 0xFF4A3020);
                java.util.List<net.minecraft.text.OrderedText> behaviorLines = textRenderer.wrapLines(
                    Text.literal(spell.has("behavior") ? spell.get("behavior").getAsString() : ""), 184);
                for (int i = 0; i < Math.min(2, behaviorLines.size()); i++)
                    context.drawText(textRenderer, behaviorLines.get(i), left + 230, top + 170 + i * 10, 0xFF4A3020, false);
                context.drawText(textRenderer, "Arcana cost: " + (spell.has("cost") ? spell.get("cost").getAsInt() : 0), left + 230, top + 191, 0xFF684D36, false);
                JsonArray effects = spell.has("effects") ? spell.getAsJsonArray("effects") : new JsonArray();
                String effectSummary = effects.size() == 0 ? "None" : effects.get(0).getAsString();
                if (effects.size() > 1) effectSummary += " +" + (effects.size() - 1) + " more";
                context.drawText(textRenderer, "Effects: " + effectSummary, left + 230, top + 204, 0xFF684D36, false);
                JsonArray descriptions = spell.has("effectDescriptions") ? spell.getAsJsonArray("effectDescriptions") : new JsonArray();
                if (!descriptions.isEmpty()) {
                    String detail = textRenderer.trimToWidth(descriptions.get(0).getAsString(), 184);
                    context.drawText(textRenderer, detail, left + 230, top + 216, 0xFF684D36, false);
                }
                if (legendary) {
                    context.drawText(textRenderer, "LEGENDARY", left + 230, top + 228, 0xFF9A6510, false);
                    String signature = textRenderer.trimToWidth(spell.get("legendaryEffect").getAsString(), 184);
                    context.drawText(textRenderer, signature, left + 230, top + 239, 0xFF9A6510, false);
                } else {
                    JsonArray warnings = spell.has("warnings") ? spell.getAsJsonArray("warnings") : new JsonArray();
                    if (!warnings.isEmpty()) {
                        String synergy = textRenderer.trimToWidth("Synergy: " + warnings.get(0).getAsString(), 184);
                        context.drawText(textRenderer, synergy, left + 230, top + 228, 0xFF684D36, false);
                    }
                }
                if (editMode) context.drawText(textRenderer, "EDIT: add/remove/reorder glyphs", left + 230, top + 72, 0xFF684D36, false);
            }
        } else if (tab == 1) {
            context.drawText(textRenderer, "Engraved Magic Paper in your inventory", left + 24, top + 55, 0xFF5A3D28, false);
            if (array("loosePages").isEmpty()) context.drawText(textRenderer, "No engraved Magic Paper.", left + 28, top + 88, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Insert a page or awaken it with a Legendary Scroll.", left + 235, top + 85, 0xFF5A3D28, false);
        } else if (tab == 2) {
            int current = state.has("arcana") ? state.get("arcana").getAsInt() : 0;
            int max = state.has("maxArcana") ? state.get("maxArcana").getAsInt() : 100;
            context.drawText(textRenderer, "ARCANA RESERVE", left + 28, top + 68, 0xFF5A3D28, false);
            context.fill(left + 28, top + 92, left + 190, top + 105, 0xFF8B8175);
            context.fill(left + 28, top + 92, left + 28 + (int)(162.0 * current / Math.max(1,max)), top + 105, 0xFF7950A0);
            context.drawText(textRenderer, current + " / " + max, left + 28, top + 116, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Casting spends the cost shown on each spell page.", left + 235, top + 80, 0xFF5A3D28, false);
        } else {
            context.drawText(textRenderer, "A grimoire holds engraved spell pages.", left + 28, top + 72, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Pages: " + array("spells").size() + " / " + (state.has("slots") ? state.get("slots").getAsInt() : 0), left + 28, top + 98, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Normal glyph limit: " + (state.has("maxComponents") ? state.get("maxComponents").getAsInt() : 0), left + 28, top + 116, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Legendary pages: up to 8 glyphs; Master Grimoire only.", left + 28, top + 132, 0xFF5A3D28, false);
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(notice, 195), left + 14, top + 226, 0xFFEEE0C4, false);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (active == this) active = null; super.close(); }
}
