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
import net.minecraft.client.gui.widget.TextFieldWidget;
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
    private boolean powerAdjustMode = false;
    private boolean descriptionEditorOpen = false;
    private final TextFieldWidget[] descriptionLines = new TextFieldWidget[3];
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
        int top = (height - 300) / 2;
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
                addDrawableChild(ButtonWidget.builder(Text.literal("WRITE DESCRIPTION"), b -> {
                    descriptionEditorOpen = true;
                    rebuild();
                }).dimensions(left + 230, top + 30, 176, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("GLYPHS"), b -> {
                    editMode = !editMode; powerAdjustMode = false; glyphPage = 0; rebuild();
                }).dimensions(left + 230, top + 53, 84, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("POWER %"), b -> {
                    powerAdjustMode = !powerAdjustMode; editMode = false; rebuild();
                }).dimensions(left + 320, top + 53, 86, 20).build());
                if (descriptionEditorOpen) {
                    String[] savedLines = (spell.has("description") ? spell.get("description").getAsString() : "").split("\\n", -1);
                    for (int line = 0; line < 3; line++) {
                        descriptionLines[line] = new TextFieldWidget(textRenderer, left + 35, top + 112 + line * 30,
                            360, 20, Text.literal("Description line " + (line + 1)));
                        descriptionLines[line].setMaxLength(100);
                        descriptionLines[line].setPlaceholder(Text.literal("Continue your description..."));
                        descriptionLines[line].setText(line < savedLines.length ? savedLines[line] : "");
                        addDrawableChild(descriptionLines[line]);
                    }
                    addDrawableChild(ButtonWidget.builder(Text.literal("SAVE DESCRIPTION"), b -> {
                        String value = String.join("\\n", java.util.Arrays.stream(descriptionLines)
                            .map(TextFieldWidget::getText).toList());
                        if (value.length() > 300) value = value.substring(0, 300);
                        send("description", name + "\\n" + value);
                        descriptionEditorOpen = false;
                        rebuild();
                    }).dimensions(left + 230, top + 220, 110, 20).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("CANCEL"), b -> {
                        descriptionEditorOpen = false; rebuild();
                    }).dimensions(left + 346, top + 220, 60, 20).build());
                }
                if (!editMode && !powerAdjustMode) addDrawableChild(ButtonWidget.builder(Text.literal(removeMode ? "REMOVE: ON" : "REMOVE PAGE"), b -> {
                    removeMode = !removeMode; notice = removeMode ? "Select a spell page to remove." : "Removal mode off."; rebuild();
                }).dimensions(left + 230, top + 250, 72, 18).build());
                if (!editMode && spell.has("legendary") && !spell.get("legendary").getAsBoolean()) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("LEGENDARY SCROLL"), b -> send("legendary", name))
                        .dimensions(left + 306, top + 250, 100, 18).build());
                }
                if (editMode || powerAdjustMode) buildEditor(left, top, name, spell);
                if (removeMode) {
                    // In removal mode the selected page is returned as engraved Magic Paper.
                    addDrawableChild(ButtonWidget.builder(Text.literal("CONFIRM REMOVE"), b -> send("remove", name))
                        .dimensions(left + 18, top + 250, 180, 18).build());
                }
            }
        } else if (tab == 1) {
            JsonArray pages = array("loosePages");
            for (int i = 0; i < Math.min(6, pages.size()); i++) {
                String name = pages.get(i).getAsString();
                addDrawableChild(ButtonWidget.builder(Text.literal("Insert: " + name), b -> send("insert", name))
                    .dimensions(left + 25, top + 65 + i * 25, 110, 21).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Legendary"), b -> send("legendary_page", name))
                    .dimensions(left + 138, top + 65 + i * 25, 72, 21).build());
            }
        } else if (tab == 2) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Arcana"), b -> send("refresh", ""))
                .dimensions(left + 250, top + 198, 156, 20).build());
        }
        if (!editMode && !powerAdjustMode) addDrawableChild(ButtonWidget.builder(Text.literal("Close book"), b -> close())
            .dimensions(left + 300, top + 8, 108, 20).build());
    }

    private void buildEditor(int left, int top, String spellName, JsonObject spell) {
        JsonArray components = spell.has("components") ? spell.getAsJsonArray("components") : new JsonArray();
        if (powerAdjustMode) {
            for (int i = 0; i < components.size(); i++) {
                String id = components.get(i).getAsString();
                int y = top + 91 + i * 17;
                addDrawableChild(ButtonWidget.builder(Text.literal("−25"), b -> send("power_adjust", spellName + "|" + id + "|-25"))
                    .dimensions(left + 352, y, 26, 15).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+25"), b -> send("power_adjust", spellName + "|" + id + "|25"))
                    .dimensions(left + 380, y, 26, 15).build());
            }
            return;
        }
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

    private static boolean containsComponent(JsonArray components, String id) {
        for (int i = 0; i < components.size(); i++) {
            if (id.equals(components.get(i).getAsString())) return true;
        }
        return false;
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
        String effect = "";
        JsonArray glyphs = array("glyphs");
        for (int i = 0; i < components.size() && effect.isEmpty(); i++) {
            String id = components.get(i).getAsString();
            for (int j = 0; j < glyphs.size(); j++) {
                JsonObject glyph = glyphs.get(j).getAsJsonObject();
                if (glyph.get("id").getAsString().equals(id)
                    && glyph.get("category").getAsString().equals("Power and Effect")) {
                    effect = id;
                    break;
                }
            }
        }
        int effectSize = 4 + Math.floorMod(age / 3, 5);
        int effectColor = switch (effect) {
            case "healing", "regeneration", "growth", "cleanse" -> 0xFF55A65B;
            case "explosion", "repulsion" -> 0xFFE66A32;
            case "teleportation", "portal", "recall" -> 0xFF6B73D8;
            case "binding", "anchor", "fatigue" -> 0xFF8051A8;
            case "summoning", "enhancement", "haste" -> 0xFFE6B84D;
            default -> color;
        };
        switch (effect) {
            case "healing", "regeneration", "growth", "cleanse" -> {
                context.fill(centerX - 2, centerY - effectSize, centerX + 2, centerY + effectSize, effectColor);
                context.fill(centerX - effectSize, centerY - 2, centerX + effectSize, centerY + 2, effectColor);
            }
            case "explosion", "repulsion" -> {
                context.fill(centerX - effectSize, centerY - 2, centerX + effectSize, centerY + 2, effectColor);
                context.fill(centerX - 2, centerY - effectSize, centerX + 2, centerY + effectSize, effectColor);
                context.fill(centerX - effectSize, centerY - effectSize, centerX - effectSize + 3, centerY - effectSize + 3, effectColor);
                context.fill(centerX + effectSize - 3, centerY + effectSize - 3, centerX + effectSize, centerY + effectSize, effectColor);
            }
            case "teleportation", "portal", "recall" -> {
                context.fill(centerX - effectSize, centerY - effectSize, centerX + effectSize, centerY - effectSize + 2, effectColor);
                context.fill(centerX - effectSize, centerY + effectSize - 2, centerX + effectSize, centerY + effectSize, effectColor);
                context.fill(centerX - effectSize, centerY - effectSize, centerX - effectSize + 2, centerY + effectSize, effectColor);
                context.fill(centerX + effectSize - 2, centerY - effectSize, centerX + effectSize, centerY + effectSize, effectColor);
            }
            case "binding", "anchor", "fatigue" -> {
                context.fill(centerX - effectSize, centerY - 5, centerX + effectSize, centerY - 2, effectColor);
                context.fill(centerX - effectSize, centerY + 2, centerX + effectSize, centerY + 5, effectColor);
            }
            case "summoning", "enhancement", "haste" -> {
                context.fill(centerX - 2, centerY - effectSize, centerX + 2, centerY + effectSize, effectColor);
                context.fill(centerX - effectSize, centerY - 2, centerX + effectSize, centerY + 2, effectColor);
            }
            default -> context.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, effectColor);
        }
        // Show every selected element as a moving orbiting mote, not just the last element.
        int mote = 0;
        for (int i = 0; i < components.size(); i++) {
            String id = components.get(i).getAsString();
            int moteColor = switch (id) {
                case "fire", "magma", "sun" -> 0xFFFF7138;
                case "water", "ice" -> 0xFF59C8F0;
                case "lightning" -> 0xFFFFE45C;
                case "nature", "wood" -> 0xFF65C96B;
                case "shadow", "poison" -> 0xFFAD72D8;
                case "light", "moon" -> 0xFFFFE8A0;
                case "wind" -> 0xFFB5E8DD;
                case "earth", "sand" -> 0xFFB88A52;
                case "metal", "crystal" -> 0xFFC7D2E0;
                default -> 0;
            };
            if (moteColor != 0) {
                double angle = age * 0.055 + mote * (Math.PI * 2.0 / 5.0);
                int px = centerX + (int)(Math.cos(angle) * 27.0);
                int py = centerY + (int)(Math.sin(angle) * 27.0);
                context.fill(px - 3, py - 3, px + 3, py + 3, moteColor);
                context.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);
                mote++;
            }
        }
        if (containsComponent(components, "amplify") || containsComponent(components, "expand")) {
            int halo = 22 + Math.floorMod(age / 2, 7);
            context.fill(centerX - halo, centerY - halo, centerX + halo, centerY - halo + 1, 0x88FFFFFF);
            context.fill(centerX - halo, centerY + halo - 1, centerX + halo, centerY + halo, 0x88FFFFFF);
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
    private void changeTab(int next) { tab = next; selected = 0; listPage = 0; editMode = false; powerAdjustMode = false; descriptionEditorOpen = false; rebuild(); }
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
        int top = (height - 300) / 2;
        context.fill(0, 0, width, height, 0x880B0710);
        context.fill(left - 5, top + 5, left + 435, top + 300, 0xFF211629);
        context.fill(left, top, left + 430, top + 294, 0xFF765333);
        context.fill(left + 5, top + 5, left + 425, top + 289, 0xFF2B1D2E);
        context.fill(left + 10, top + 10, left + 210, top + 284, 0xFFF0DDB7);
        context.fill(left + 220, top + 10, left + 420, top + 284, 0xFFF4E6C8);
        context.fill(left + 214, top + 10, left + 216, top + 284, 0xFF9B7950);
        context.drawCenteredTextWithShadow(textRenderer, state.has("tier") ? state.get("tier").getAsString() : "Grimoire", left + 215, top + 17, 0xFFF0DDB7);
        String[] tabs = {"SPELLS", "LOOSE PAGES", "ARCANA", "BOOK"};
        context.drawText(textRenderer, tabs[tab], left + 24, top + 39, 0xFF5A3D28, true);

        if (tab == 0) {
            JsonArray spells = array("spells");
            context.drawText(textRenderer, "Select a spell to inspect or adjust", left + 22, top + 54, 0xFF5A3D28, true);
            if (spells.isEmpty()) {
                context.drawText(textRenderer, "This book is empty.", left + 25, top + 88, 0xFF5A3D28, true);
                context.drawText(textRenderer, "Engrave pages at the Magic Writer.", left + 25, top + 101, 0xFF5A3D28, true);
            } else if (selected < spells.size()) {
                JsonObject spell = spells.get(selected).getAsJsonObject();
                String name = spell.get("name").getAsString();
                boolean legendary = spell.has("legendary") && spell.get("legendary").getAsBoolean();
                JsonArray orderedGlyphs = spell.has("components") ? spell.getAsJsonArray("components") : new JsonArray();
                java.util.List<String> glyphNames = new java.util.ArrayList<>();
                for (int i = 0; i < orderedGlyphs.size(); i++) glyphNames.add(shortName(orderedGlyphs.get(i).getAsString()));
                String glyphOrder = textRenderer.trimToWidth("Order: " + String.join(" → ", glyphNames), 184);
                context.drawText(textRenderer, glyphOrder, left + 230, top + 72, 0xFF684D36, true);
                drawPreviewAnimation(context, left, top, spell, legendary);
                context.drawCenteredTextWithShadow(textRenderer, "SPELL PREVIEW", left + 320, top + 145, 0xFF684D36);
                context.drawCenteredTextWithShadow(textRenderer, name, left + 320, top + 58, 0xFF4A3020);
                String visibleDescription = spell.has("description") && !spell.get("description").getAsString().isBlank()
                    ? spell.get("description").getAsString()
                    : (spell.has("behavior") ? spell.get("behavior").getAsString() : "");
                java.util.List<net.minecraft.text.OrderedText> behaviorLines = textRenderer.wrapLines(
                    Text.literal(visibleDescription), 184);
                for (int i = 0; i < Math.min(2, behaviorLines.size()); i++)
                    context.drawText(textRenderer, behaviorLines.get(i), left + 230, top + 170 + i * 10, 0xFF4A3020, true);
                String costLine = "Arcana cost: " + (spell.has("cost") ? spell.get("cost").getAsInt() : 0);
                context.drawText(textRenderer, textRenderer.trimToWidth(costLine, 184), left + 230, top + 191, 0xFF684D36, true);
                if (spell.has("costBreakdown")) {
                    String breakdown = textRenderer.trimToWidth(spell.get("costBreakdown").getAsString(), 184);
                    context.drawText(textRenderer, breakdown, left + 230, top + 202, 0xFF806B4F, true);
                }
                JsonArray effects = spell.has("effects") ? spell.getAsJsonArray("effects") : new JsonArray();
                String effectSummary = effects.size() == 0 ? "None" : effects.get(0).getAsString();
                if (effects.size() > 1) effectSummary += " +" + (effects.size() - 1) + " more";
                context.drawText(textRenderer, "Effects: " + effectSummary, left + 230, top + 214, 0xFF684D36, true);
                JsonArray descriptions = spell.has("effectDescriptions") ? spell.getAsJsonArray("effectDescriptions") : new JsonArray();
                if (legendary) {
                    String signature = "LEGENDARY EFFECT: " + (spell.has("legendaryEffect") ? spell.get("legendaryEffect").getAsString() : "Arcane Ascendance");
                    java.util.List<net.minecraft.text.OrderedText> signatureLines =
                        textRenderer.wrapLines(Text.literal(signature), 184);
                    for (int i = 0; i < Math.min(4, signatureLines.size()); i++)
                        context.drawText(textRenderer, signatureLines.get(i), left + 230, top + 226 + i * 10, 0xFF8A4D08, true);
                } else {
                    if (!descriptions.isEmpty()) {
                        String detail = textRenderer.trimToWidth(descriptions.get(0).getAsString(), 184);
                        context.drawText(textRenderer, detail, left + 230, top + 226, 0xFF684D36, true);
                    }
                    JsonArray warnings = spell.has("warnings") ? spell.getAsJsonArray("warnings") : new JsonArray();
                    if (!warnings.isEmpty()) {
                        context.drawText(textRenderer, "SPELL CHECK / SYNERGY", left + 230, top + 238, 0xFF80551F, true);
                        java.util.List<net.minecraft.text.OrderedText> checkLines = textRenderer.wrapLines(
                            Text.literal(warnings.get(0).getAsString()), 184);
                        for (int line = 0; line < Math.min(2, checkLines.size()); line++)
                            context.drawText(textRenderer, checkLines.get(line), left + 230, top + 249 + line * 9, 0xFF684D36, true);
                    }
                }
                if (editMode) context.drawText(textRenderer, "EDIT: add/remove/reorder glyphs", left + 230, top + 76, 0xFF684D36, true);
                if (powerAdjustMode) {
                    context.drawText(textRenderer, "POWER: 25%–300% (100% normal)", left + 230, top + 76, 0xFF684D36, true);
                    JsonObject powers = spell.has("powerPercentages") ? spell.getAsJsonObject("powerPercentages") : new JsonObject();
                    for (int i = 0; i < orderedGlyphs.size(); i++) {
                        String id = orderedGlyphs.get(i).getAsString();
                        int y = top + 94 + i * 17;
                        int percent = powers.has(id) ? powers.get(id).getAsInt() : 100;
                        context.drawText(textRenderer, textRenderer.trimToWidth(shortName(id), 82), left + 230, y, 0xFF4A3020, true);
                        context.drawText(textRenderer, percent + "%", left + 316, y, 0xFF684D36, true);
                    }
                }
            }
        } else if (tab == 1) {
            context.drawText(textRenderer, "Engraved Magic Paper in your inventory", left + 24, top + 55, 0xFF5A3D28, true);
            if (array("loosePages").isEmpty()) context.drawText(textRenderer, "No engraved Magic Paper.", left + 28, top + 88, 0xFF5A3D28, true);
            context.drawText(textRenderer, "Insert a page or awaken it with a Legendary Scroll.", left + 235, top + 85, 0xFF5A3D28, true);
        } else if (tab == 2) {
            int current = state.has("arcana") ? state.get("arcana").getAsInt() : 0;
            int max = state.has("maxArcana") ? state.get("maxArcana").getAsInt() : 100;
            context.drawText(textRenderer, "ARCANA RESERVE", left + 28, top + 68, 0xFF5A3D28, true);
            context.fill(left + 28, top + 92, left + 190, top + 105, 0xFF8B8175);
            context.fill(left + 28, top + 92, left + 28 + (int)(162.0 * current / Math.max(1,max)), top + 105, 0xFF7950A0);
            context.drawText(textRenderer, current + " / " + max, left + 28, top + 116, 0xFF5A3D28, true);
            context.drawText(textRenderer, "Casting spends the cost shown on each spell page.", left + 235, top + 80, 0xFF5A3D28, true);
        } else {
            context.drawText(textRenderer, "A grimoire holds engraved spell pages.", left + 28, top + 72, 0xFF5A3D28, true);
            context.drawText(textRenderer, "Pages: " + array("spells").size() + " / " + (state.has("slots") ? state.get("slots").getAsInt() : 0), left + 28, top + 98, 0xFF5A3D28, true);
            context.drawText(textRenderer, "Normal glyph limit: " + (state.has("maxComponents") ? state.get("maxComponents").getAsInt() : 0), left + 28, top + 116, 0xFF5A3D28, true);
            context.drawText(textRenderer, "Legendary pages: up to 8 glyphs; Master Grimoire only.", left + 28, top + 132, 0xFF5A3D28, true);
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(notice, 195), left + 14, top + 270, 0xFFEEE0C4, true);
        if (descriptionEditorOpen) {
            context.fill(left + 20, top + 82, left + 410, top + 252, 0xFF2B1D2E);
            context.fill(left + 24, top + 86, left + 406, top + 248, 0xFFF4E6C8);
            context.drawCenteredTextWithShadow(textRenderer, "WRITE YOUR SPELL DESCRIPTION", left + 215, top + 94, 0xFF4A3020);
            context.drawText(textRenderer, "Describe what the spell is meant to do (up to 300 characters).", left + 36, top + 101, 0xFF684D36, true);
        }
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (descriptionEditorOpen && keyCode == 256) { descriptionEditorOpen = false; rebuild(); return true; }
        if (powerAdjustMode && keyCode == 256) { powerAdjustMode = false; rebuild(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void close() { if (active == this) active = null; super.close(); }
}
