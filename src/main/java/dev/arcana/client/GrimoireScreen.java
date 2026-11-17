package dev.arcana.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.arcana.network.GrimoireNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
    private boolean removeMode = false;
    private String notice = "Opening your grimoire...";

    public GrimoireScreen() {
        super(Text.literal("Grimoire"));
        active = this;
    }

    public static void receiveState(String json) {
        if (active == null) return;
        try {
            active.state = JsonParser.parseString(json).getAsJsonObject();
            String message = active.state.has("message") ? active.state.get("message").getAsString() : "";
            if (!message.isBlank()) active.notice = message;
            active.rebuild();
        } catch (RuntimeException ignored) {
            active.notice = "The grimoire could not read its pages.";
        }
    }

    @Override
    protected void init() {
        int left = (width - 430) / 2;
        int top = (height - 250) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Spells"), b -> changeTab(0))
            .dimensions(left - 58, top + 28, 54, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pages"), b -> changeTab(1))
            .dimensions(left - 58, top + 54, 54, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Arcana"), b -> changeTab(2))
            .dimensions(left - 58, top + 80, 54, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Book"), b -> changeTab(3))
            .dimensions(left - 58, top + 106, 54, 22).build());

        if (tab == 0) {
            JsonArray spells = array("spells");
            int start = listPage * 5;
            for (int i = start; i < Math.min(start + 5, spells.size()); i++) {
                final int index = i;
                String name = spells.get(i).getAsJsonObject().get("name").getAsString();
                addDrawableChild(ButtonWidget.builder(Text.literal((selected == i ? "◆ " : "▤ ") + name),
                    b -> {
                        selected = index;
                        if (removeMode) send("remove", name);
                        else send("cast", name);
                    })
                    .dimensions(left + 20, top + 64 + (i - start) * 25, 174, 21).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> { listPage = Math.max(0, listPage - 1); rebuild(); })
                .dimensions(left + 20, top + 198, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> { if ((listPage + 1) * 5 < spells.size()) listPage++; rebuild(); })
                .dimensions(left + 166, top + 198, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(removeMode ? "REMOVE MODE: ON" : "Fold corner: remove"),
                b -> { removeMode = !removeMode; notice = removeMode ? "Choose a spell page to remove." : "Removal mode off."; rebuild(); })
                .dimensions(left + 250, top + 30, 158, 20).build());
        } else if (tab == 1) {
            JsonArray pages = array("loosePages");
            for (int i=0;i<Math.min(6,pages.size());i++) {
                String name=pages.get(i).getAsString();
                addDrawableChild(ButtonWidget.builder(Text.literal("Insert: " + name), b -> send("insert", name))
                    .dimensions(left + 30, top + 65 + i*25, 350, 21).build());
            }
        } else if (tab == 2) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Arcana"), b -> send("refresh", ""))
                .dimensions(left + 250, top + 198, 156, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Close book"), b -> close())
            .dimensions(left + 300, top + 222, 108, 20).build());
    }

    private void changeTab(int next) { tab = next; selected = 0; listPage = 0; rebuild(); }
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
            context.drawText(textRenderer, "Press a spell page to cast it", left + 22, top + 54, 0xFF5A3D28, false);
            if (spells.size() == 0) {
                context.drawText(textRenderer, "This book is empty.", left + 25, top + 88, 0xFF5A3D28, false);
                context.drawText(textRenderer, "Engrave pages at the Magic Writer.", left + 25, top + 101, 0xFF5A3D28, false);
            } else if (selected < spells.size()) {
                JsonObject spell = spells.get(selected).getAsJsonObject();
                String name = spell.get("name").getAsString();
                context.drawCenteredTextWithShadow(textRenderer, name, left + 320, top + 58, 0xFF4A3020);
                context.fill(left + 278, top + 83, left + 362, top + 166, 0xFFDFD0B0);
                context.drawCenteredTextWithShadow(textRenderer, "✦", left + 320, top + 105, 0xFF7545A0);
                context.drawCenteredTextWithShadow(textRenderer, "SPELL SIGIL", left + 320, top + 145, 0xFF684D36);
                context.drawText(textRenderer, spell.has("behavior") ? spell.get("behavior").getAsString() : "", left + 235, top + 172, 0xFF4A3020, false);
                JsonArray components = spell.has("components") ? spell.getAsJsonArray("components") : new JsonArray();
                context.drawText(textRenderer, "Components: " + components.size(), left + 235, top + 205, 0xFF684D36, false);
                context.drawText(textRenderer, "Arcana cost: " + (10 + components.size() * 5), left + 235, top + 217, 0xFF684D36, false);
            }
        } else if (tab == 1) {
            context.drawText(textRenderer, "Spell pages in your inventory", left + 24, top + 55, 0xFF5A3D28, false);
            if (array("loosePages").isEmpty()) context.drawText(textRenderer, "No loose Spell Pages.", left + 28, top + 88, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Select a page on the left", left + 235, top + 85, 0xFF5A3D28, false);
            context.drawText(textRenderer, "to insert it into this book.", left + 235, top + 98, 0xFF5A3D28, false);
        } else if (tab == 2) {
            int current = state.has("arcana") ? state.get("arcana").getAsInt() : 0;
            int max = state.has("maxArcana") ? state.get("maxArcana").getAsInt() : 100;
            context.drawText(textRenderer, "ARCANA RESERVE", left + 28, top + 68, 0xFF5A3D28, false);
            context.fill(left + 28, top + 92, left + 190, top + 105, 0xFF8B8175);
            context.fill(left + 28, top + 92, left + 28 + (int)(162.0 * current / Math.max(1,max)), top + 105, 0xFF7950A0);
            context.drawText(textRenderer, current + " / " + max, left + 28, top + 116, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Arcana regenerates over time.", left + 235, top + 80, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Casting spends Arcana.", left + 235, top + 94, 0xFF5A3D28, false);
        } else {
            context.drawText(textRenderer, "A grimoire holds engraved spell pages.", left + 28, top + 72, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Pages: " + array("spells").size() + " / " + (state.has("slots") ? state.get("slots").getAsInt() : 0), left + 28, top + 98, 0xFF5A3D28, false);
            context.drawText(textRenderer, "Components per spell: " + (state.has("maxComponents") ? state.get("maxComponents").getAsInt() : 0), left + 28, top + 116, 0xFF5A3D28, false);
        }
        context.drawText(textRenderer, notice, left + 14, top + 226, 0xFFEEE0C4, false);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (active == this) active = null; super.close(); }
}
