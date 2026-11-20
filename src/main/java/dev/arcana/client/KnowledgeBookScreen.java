package dev.arcana.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class KnowledgeBookScreen extends Screen {
    private static KnowledgeBookScreen active;
    private JsonObject state = new JsonObject();
    private int tab;
    private int category;
    private int page;
    private int selected;

    public KnowledgeBookScreen() {
        super(Text.literal("Book of Knowledge"));
        active = this;
    }

    public static void receiveState(String json) {
        if (active == null) return;
        try {
            active.state = JsonParser.parseString(json).getAsJsonObject();
            active.rebuild();
        } catch (RuntimeException ignored) {
            // Keep the empty-state page visible if the server sends malformed data.
        }
    }

    @Override
    protected void init() {
        int left = (width - 440) / 2;
        int top = (height - 300) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("COMPONENTS"), b -> {
            tab = 0; page = 0; selected = 0; rebuild();
        }).dimensions(left + 16, top + 12, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SPELL ARCHIVE"), b -> {
            tab = 1; page = 0; selected = 0; rebuild();
        }).dimensions(left + 122, top + 12, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE"), b -> close())
            .dimensions(left + 350, top + 12, 74, 20).build());

        if (tab == 0) {
            String[] categories = {"Form and Delivery", "Effect", "Augment", "Element", "Target"};
            for (int i = 0; i < categories.length; i++) {
                final int next = i;
                addDrawableChild(ButtonWidget.builder(Text.literal(categories[i]), b -> {
                    category = next; page = 0; selected = 0; rebuild();
                }).dimensions(left + 16, top + 48 + i * 27, 112, 22).build());
            }
            List<JsonObject> entries = componentsForCategory();
            int start = page * 7;
            for (int i = start; i < Math.min(start + 7, entries.size()); i++) {
                final int index = i;
                JsonObject entry = entries.get(i);
                boolean known = entry.has("known") && entry.get("known").getAsBoolean();
                String name = known ? entry.get("name").getAsString() : "???";
                addDrawableChild(ButtonWidget.builder(Text.literal((selected == i ? "◆ " : "• ") + name), b -> {
                    selected = index; rebuild();
                }).dimensions(left + 144, top + 52 + (i - start) * 25, 155, 21).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { page = Math.max(0, page - 1); rebuild(); })
                .dimensions(left + 144, top + 232, 34, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> {
                if ((page + 1) * 7 < entries.size()) page++;
                rebuild();
            }).dimensions(left + 264, top + 232, 34, 20).build());
        } else {
            JsonArray spells = array("spells");
            int start = page * 6;
            for (int i = start; i < Math.min(start + 6, spells.size()); i++) {
                final int index = i;
                JsonObject spell = spells.get(i).getAsJsonObject();
                addDrawableChild(ButtonWidget.builder(Text.literal((selected == i ? "◆ " : "• ") + spell.get("name").getAsString()), b -> {
                    selected = index; rebuild();
                }).dimensions(left + 20, top + 52 + (i - start) * 27, 165, 22).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { page = Math.max(0, page - 1); rebuild(); })
                .dimensions(left + 20, top + 232, 34, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> {
                if ((page + 1) * 6 < spells.size()) page++;
                rebuild();
            }).dimensions(left + 151, top + 232, 34, 20).build());
        }
    }

    private List<JsonObject> componentsForCategory() {
        List<JsonObject> result = new ArrayList<>();
        JsonArray all = array("components");
        String[] categories = {"Form and Delivery", "Effect", "Augment", "Element", "Target"};
        for (int i = 0; i < all.size(); i++) {
            JsonObject item = all.get(i).getAsJsonObject();
            if (categories[category].equals(item.get("category").getAsString())) result.add(item);
        }
        return result;
    }

    private JsonArray array(String key) {
        return state.has(key) && state.get(key).isJsonArray() ? state.getAsJsonArray(key) : new JsonArray();
    }

    private void rebuild() {
        if (client != null) { clearChildren(); init(); }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (width - 440) / 2;
        int top = (height - 300) / 2;
        context.fill(0, 0, width, height, 0x880B0710);
        context.fill(left, top, left + 440, top + 300, 0xFF30223B);
        context.fill(left + 5, top + 5, left + 435, top + 295, 0xFFF0E2C4);
        context.drawCenteredTextWithShadow(textRenderer, "BOOK OF KNOWLEDGE", left + 220, top + 37, 0xFF4A3020);

        if (tab == 0) {
            List<JsonObject> entries = componentsForCategory();
            context.drawText(textRenderer, "KNOWN COMPONENTS: " + (state.has("knownCount") ? state.get("knownCount").getAsInt() : 0)
                + " / " + (state.has("componentCount") ? state.get("componentCount").getAsInt() : 0), left + 144, top + 42, 0xFF684D36, true);
            if (entries.isEmpty()) context.drawText(textRenderer, "No components in this category.", left + 150, top + 75, 0xFF684D36, true);
            else if (selected < entries.size()) {
                JsonObject item = entries.get(selected);
                boolean known = item.has("known") && item.get("known").getAsBoolean();
                if (known) {
                    context.drawCenteredTextWithShadow(textRenderer, item.get("name").getAsString(), left + 365, top + 83, 0xFF4A3020);
                    int detailY = top + 106;
                    for (var line : textRenderer.wrapLines(Text.literal(item.get("description").getAsString()), 112)) {
                        context.drawText(textRenderer, line, left + 310, detailY, 0xFF684D36, true);
                        detailY += 10;
                    }
                } else {
                    context.drawCenteredTextWithShadow(textRenderer, "???", left + 365, top + 83, 0xFF4A3020);
                    context.drawText(textRenderer, "Not yet recorded in your spell knowledge.", left + 310, top + 106, 0xFF684D36, true);
                }
            }
        } else {
            JsonArray spells = array("spells");
            context.drawText(textRenderer, "SPELLS CREATED: " + spells.size(), left + 20, top + 42, 0xFF684D36, true);
            if (spells.isEmpty()) {
                context.drawText(textRenderer, "No spells have been recorded yet.", left + 205, top + 90, 0xFF684D36, true);
                context.drawText(textRenderer, "Engrave a spell to add it to this archive.", left + 205, top + 108, 0xFF684D36, true);
            } else if (selected < spells.size()) {
                JsonObject spell = spells.get(selected).getAsJsonObject();
                int x = left + 205;
                context.drawText(textRenderer, spell.get("name").getAsString(), x, top + 58, 0xFF4A3020, true);
                context.drawText(textRenderer, "Glyph sequence:", x, top + 78, 0xFF684D36, true);
                JsonArray ids = spell.getAsJsonArray("components");
                StringBuilder sequence = new StringBuilder();
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) sequence.append(" → ");
                    sequence.append(ids.get(i).getAsString());
                }
                int y = top + 92;
                for (var line : textRenderer.wrapLines(Text.literal(sequence.toString()), 215)) {
                    context.drawText(textRenderer, line, x, y, 0xFF4A3020, true); y += 10;
                }
                context.drawText(textRenderer, "Cost: " + spell.get("cost").getAsInt() + " Arcana", x, y + 3, 0xFF684D36, true);
                String description = spell.has("description") && !spell.get("description").getAsString().isBlank()
                    ? spell.get("description").getAsString() : spell.get("behavior").getAsString();
                int descY = y + 19;
                for (var line : textRenderer.wrapLines(Text.literal(description), 215)) {
                    if (descY > top + 238) break;
                    context.drawText(textRenderer, line, x, descY, 0xFF4A3020, true); descY += 10;
                }
                if (spell.has("legendary") && spell.get("legendary").getAsBoolean())
                    context.drawText(textRenderer, "LEGENDARY: " + spell.get("legendaryEffect").getAsString(), x, top + 258, 0xFF986000, true);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (active == this) active = null; super.close(); }
}
