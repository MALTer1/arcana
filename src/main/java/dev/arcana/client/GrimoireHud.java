package dev.arcana.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.arcana.network.GrimoireNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class GrimoireHud {
    private static JsonObject state = new JsonObject();
    private static int tab, selectedSpell, selectedLoosePage;
    private static boolean removeMode;
    private static String notice = "";
    private static long noticeUntil;

    private GrimoireHud() {}

    public static void receiveState(String json) {
        try {
            state = JsonParser.parseString(json).getAsJsonObject();
            if (state.has("message")) {
                notice = state.get("message").getAsString();
                noticeUntil = System.currentTimeMillis() + 3500;
            }
            selectedSpell = Math.max(0, Math.min(selectedSpell, Math.max(0, spells().size() - 1)));
            selectedLoosePage = Math.max(0, Math.min(selectedLoosePage, Math.max(0, pages().size() - 1)));
            GrimoireScreen.receiveState(json);
        } catch (RuntimeException ignored) {
            notice = "Could not read grimoire pages.";
            noticeUntil = System.currentTimeMillis() + 3500;
        }
    }

    public static void refresh() { send("refresh", ""); }
    public static void previousPage() {
        if (tab == 0 && !spells().isEmpty()) selectedSpell = Math.floorMod(selectedSpell - 1, spells().size());
        else if (tab == 1 && !pages().isEmpty()) selectedLoosePage = Math.floorMod(selectedLoosePage - 1, pages().size());
    }
    public static void nextPage() {
        if (tab == 0 && !spells().isEmpty()) selectedSpell = (selectedSpell + 1) % spells().size();
        else if (tab == 1 && !pages().isEmpty()) selectedLoosePage = (selectedLoosePage + 1) % pages().size();
    }
    public static void nextTab() { tab = (tab + 1) % 4; }
    public static void previousTab() { tab = (tab + 3) % 4; }
    public static void toggleRemove() {
        removeMode = !removeMode;
        notice = removeMode ? "Removal mode: select a spell and press Use." : "Removal mode off.";
        noticeUntil = System.currentTimeMillis() + 3000;
    }
    public static void usePage() {
        if (tab == 0 && !spells().isEmpty()) {
            JsonObject spell = spells().get(selectedSpell).getAsJsonObject();
            send(removeMode ? "remove" : "cast", spell.get("name").getAsString());
        } else if (tab == 1 && !pages().isEmpty()) {
            send("insert", pages().get(selectedLoosePage).getAsString());
        }
    }

    public static void castSelected() {
        if (tab != 0 || spells().isEmpty() || removeMode) return;
        JsonObject spell = spells().get(selectedSpell).getAsJsonObject();
        send("cast", spell.get("name").getAsString());
    }

    private static void send(String action, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new GrimoireNetwork.ActionPayload(action, name));
        }
    }
    private static JsonArray spells() {
        return state.has("spells") && state.get("spells").isJsonArray()
            ? state.getAsJsonArray("spells") : new JsonArray();
    }
    private static JsonArray pages() {
        return state.has("loosePages") && state.get("loosePages").isJsonArray()
            ? state.getAsJsonArray("loosePages") : new JsonArray();
    }
    private static void line(DrawContext draw, MinecraftClient client, String value, int x, int y, int color) {
        // Vanilla's shadow pass softens the small HUD glyphs. Draw the base font
        // directly for sharper edges and more consistent readability.
        draw.drawText(client.textRenderer, value, x, y, color, false);
    }
    private static void fitLine(DrawContext draw, MinecraftClient client, String value,
                                int x, int y, int maxWidth, int color) {
        if (value == null || value.isEmpty() || maxWidth < 1) return;
        int measured = client.textRenderer.getWidth(value);
        float scale = measured <= maxWidth ? 1.0f : (float) maxWidth / measured;
        var matrices = draw.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1.0f);
        draw.drawText(client.textRenderer, value, 0, 0, color, false);
        matrices.pop();
    }

    private static void wrapped(DrawContext draw, MinecraftClient client, String value,
                                int x, int y, int width, int color, int maxLines) {
        if (value == null || value.isBlank() || width < 12 || maxLines < 1) return;
        StringBuilder current = new StringBuilder();
        int row = 0;
        for (String word : value.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (client.textRenderer.getWidth(candidate) > width && current.length() > 0) {
                line(draw, client, current.toString(), x, y + row * 11, color);
                if (++row >= maxLines) return;
                current.setLength(0);
                current.append(client.textRenderer.trimToWidth(word, width));
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
        }
        if (current.length() > 0 && row < maxLines) {
            line(draw, client, current.toString(), x, y + row * 11, color);
        }
    }

    private static void wrappedFit(DrawContext draw, MinecraftClient client, String value,
                                   int x, int y, int width, int maxHeight, int color) {
        if (value == null || value.isBlank() || width < 12 || maxHeight < 9) return;
        java.util.List<String> rows = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (client.textRenderer.getWidth(candidate) > width && current.length() > 0) {
                rows.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
        }
        if (current.length() > 0) rows.add(current.toString());
        if (rows.isEmpty()) return;
        float scale = Math.min(1.0f, (float) maxHeight / (rows.size() * 11.0f));
        var matrices = draw.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1.0f);
        for (int i = 0; i < rows.size(); i++) {
            line(draw, client, rows.get(i), 0, i * 11, color);
        }
        matrices.pop();
    }

    private static void panel(DrawContext draw, int x, int y, int width, int height, boolean right) {
        // 0xE6 alpha leaves the parchment and frame approximately 10% transparent.
        draw.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x55170D20);
        draw.fill(x, y, x + width, y + height, 0x754B245F);
        draw.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x55FFF1D5);
        draw.fill(x + 7, y + 7, x + width - 7, y + height - 7,
            right ? 0x55F7EBD4 : 0x55F4E4C3);
        draw.fill(x + 7, y + 7, x + width - 7, y + 9, 0x66B18A5C);
        draw.fill(x + 7, y + height - 9, x + width - 7, y + height - 7, 0x77B18A5C);
    }

    public static void render(DrawContext draw, MinecraftClient client) {
        if (client.player == null) return;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Independent side panels: never draw a single connected book across the centre.
        int panelWidth = Math.max(144, Math.min(176, (screenWidth - 36) / 2));
        panelWidth = Math.min(panelWidth, Math.max(120, (screenWidth - 20) / 2));
        int panelHeight = Math.min(196, Math.max(164, screenHeight - 28));
        int y = Math.max(8, (screenHeight - panelHeight) / 2);
        int leftX = 8;
        int rightX = screenWidth - panelWidth - 8;
        int innerX = 12;
        int ink = 0xFF302015;
        int accent = 0xFF572B70;
        int muted = 0xFF76583A;
        int contentWidth = panelWidth - 24;
        int top = y + 43;

        panel(draw, leftX, y, panelWidth, panelHeight, false);
        panel(draw, rightX, y, panelWidth, panelHeight, true);

        String tier = state.has("tier") ? state.get("tier").getAsString() : "Grimoire";
        fitLine(draw, client, tier.toUpperCase(), leftX + innerX, y + 8, contentWidth, accent);
        String[] labels = {"SPELL", "PAGES", "ARC", "BOOK"};
        int tabWidth = contentWidth / 4;
        for (int i = 0; i < labels.length; i++) {
            int tx = leftX + innerX + i * tabWidth;
            if (i == tab) draw.fill(tx - 1, y + 22, tx + tabWidth - 2, y + 35, 0x884B245F);
            fitLine(draw, client, labels[i], tx, y + 25, tabWidth - 4, i == tab ? 0xFFFFEAC8 : muted);
        }

        String rightTitle = switch (tab) {
            case 0 -> "SPELL DETAILS";
            case 1 -> "PAGE PREVIEW";
            case 2 -> "ARCANA";
            default -> "BOOK DETAILS";
        };
        fitLine(draw, client, rightTitle, rightX + innerX, y + 12, contentWidth, accent);
        draw.fill(rightX + innerX, y + 25, rightX + panelWidth - innerX, y + 27, 0x66B89A70);

        if (tab == 0) {
            line(draw, client, "SPELL INDEX", leftX + innerX, top, muted);
            if (spells().isEmpty()) {
                wrapped(draw, client, "No spells stored. Create a spell and engrave a page at the Magic Writer.",
                    leftX + innerX, top + 20, contentWidth, ink, Math.max(1, (panelHeight - 100) / 11));
                wrapped(draw, client, "Select a spell to see its components, behavior and Arcana cost here.",
                    rightX + innerX, top + 24, contentWidth, ink, Math.max(1, (panelHeight - 104) / 11));
            } else {
                int visible = Math.max(1, Math.min(8, (panelHeight - 92) / 13));
                int rows = Math.min(visible, spells().size());
                int firstIndex = Math.floorMod(selectedSpell - rows / 2, spells().size());
                for (int row = 0; row < rows; row++) {
                    int index = (firstIndex + row) % spells().size();
                    String name = spells().get(index).getAsJsonObject().get("name").getAsString();
                    String label = (index == selectedSpell ? "› " : "  ") + name;
                    fitLine(draw, client, label, leftX + innerX, top + 18 + row * 13,
                        contentWidth, index == selectedSpell ? accent : ink);
                }
                JsonObject selected = spells().get(selectedSpell).getAsJsonObject();
                String name = selected.get("name").getAsString();
                fitLine(draw, client, name, rightX + innerX, top + 8, contentWidth, accent);
                JsonArray components = selected.has("components") ? selected.getAsJsonArray("components") : new JsonArray();
                line(draw, client, "Components (" + components.size() + ")", rightX + innerX, top + 27, muted);
                // Reserve the bottom of the page for controls. Show only as many
                // component rows as fit, then allocate the remaining space to behavior.
                int componentY = top + 39;
                int visibleComponents = Math.min(components.size(), Math.max(2, Math.min(4, (panelHeight - 130) / 15)));
                for (int i = 0; i < visibleComponents; i++) {
                    fitLine(draw, client, "• " + components.get(i).getAsString(),
                        rightX + innerX, componentY + i * 10, contentWidth, ink);
                }
                if (components.size() > visibleComponents) {
                    line(draw, client, "+" + (components.size() - visibleComponents) + " more components",
                        rightX + innerX, componentY + visibleComponents * 10, muted);
                }
                int shownRows = visibleComponents + (components.size() > visibleComponents ? 1 : 0);
                int costY = componentY + shownRows * 10 + 4;
                int cost = selected.has("cost") ? selected.get("cost").getAsInt() : 0;
                line(draw, client, "Cost: " + cost + " Arcana", rightX + innerX, costY, accent);
                String behavior = selected.has("behavior") ? selected.get("behavior").getAsString() : "No description available.";
                int behaviorY = costY + 14;
                int lastSafeY = y + panelHeight - 43;
                int behaviorLines = Math.max(1, (lastSafeY - behaviorY) / 11 + 1);
                wrappedFit(draw, client, behavior, rightX + innerX, behaviorY,
                    contentWidth, Math.max(9, lastSafeY - behaviorY + 9), ink);
            }
        } else if (tab == 1) {
            line(draw, client, "LOOSE SPELL PAGES", leftX + innerX, top, muted);
            if (pages().isEmpty()) {
                wrapped(draw, client, "No named Spell Pages in your inventory.", leftX + innerX, top + 20, contentWidth, ink, 6);
                wrapped(draw, client, "Engrave a spell first, then select its loose page and press Enter to insert it.",
                    rightX + innerX, top + 22, contentWidth, ink, 7);
            } else {
                int visible = Math.max(1, Math.min(10, (panelHeight - 92) / 13));
                int rows = Math.min(visible, pages().size());
                int firstIndex = Math.floorMod(selectedLoosePage - rows / 2, pages().size());
                for (int row = 0; row < rows; row++) {
                    int index = (firstIndex + row) % pages().size();
                    String name = pages().get(index).getAsString();
                    fitLine(draw, client, (index == selectedLoosePage ? "› " : "  ") + name,
                        leftX + innerX, top + 18 + row * 13, contentWidth, index == selectedLoosePage ? accent : ink);
                }
                line(draw, client, trimToWidth(client, pages().get(selectedLoosePage).getAsString(), contentWidth),
                    rightX + innerX, top + 12, accent);
                wrapped(draw, client, "Press Enter to insert this page into the held grimoire. The book must have an open slot.",
                    rightX + innerX, top + 34, contentWidth, ink, Math.max(1, (panelHeight - 114) / 11));
            }
        } else if (tab == 2) {
            int value = state.has("arcana") ? state.get("arcana").getAsInt() : 0;
            int maximum = state.has("maxArcana") ? state.get("maxArcana").getAsInt() : 100;
            line(draw, client, "CURRENT RESERVE", leftX + innerX, top, muted);
            draw.fill(leftX + innerX, top + 24, leftX + panelWidth - innerX, top + 38, 0x66B9A88C);
            int barWidth = Math.max(0, (contentWidth - 4) * value / Math.max(1, maximum));
            draw.fill(leftX + innerX + 2, top + 26, leftX + innerX + 2 + barWidth, top + 36, 0x6675409A);
            line(draw, client, value + " / " + maximum, leftX + innerX, top + 47, ink);
            wrapped(draw, client, "Arcana regenerates over time. Casting spends Arcana based on spell complexity.",
                rightX + innerX, top + 8, contentWidth, ink, 7);
        } else {
            int slots = state.has("slots") ? state.get("slots").getAsInt() : 0;
            int maxComponents = state.has("maxComponents") ? state.get("maxComponents").getAsInt() : 0;
            line(draw, client, "STORED SPELLS", leftX + innerX, top, muted);
            line(draw, client, spells().size() + " / " + slots + " pages", leftX + innerX, top + 20, ink);
            line(draw, client, "MAX COMPONENTS", leftX + innerX, top + 43, muted);
            line(draw, client, String.valueOf(maxComponents), leftX + innerX, top + 62, ink);
            wrapped(draw, client, "The grimoire tier sets its page capacity and the maximum complexity of each spell.",
                rightX + innerX, top + 8, contentWidth, ink, 7);
        }

        int controlY = y + panelHeight - 19;
        line(draw, client, "← / →  PAGE", leftX + innerX, controlY, muted);
        line(draw, client, "↑ / ↓  TAB", rightX + innerX, controlY, muted);
        if (tab == 0) {
            String action = removeMode ? "ENTER: REMOVE" : "ENTER: CAST";
            line(draw, client, action, rightX + innerX, controlY - 13, accent);
            if (removeMode) line(draw, client, "X: EXIT REMOVE", leftX + innerX, controlY - 13, accent);
        } else if (tab == 1) {
            line(draw, client, "ENTER: INSERT PAGE", rightX + innerX, controlY - 13, accent);
        }
        renderSpellWheel(draw, client, screenWidth, screenHeight);

        if (System.currentTimeMillis() < noticeUntil && !notice.isBlank()) {
            draw.drawCenteredTextWithShadow(client.textRenderer, notice, screenWidth / 2, Math.max(4, y - 13), 0xFFFFFFFF);
        }
    }

    /**
     * Compact radial spell selector around the crosshair. The selected spell
     * remains the same index used by the open-page panel and Enter / shift-use.
     */
    private static void renderSpellWheel(DrawContext draw, MinecraftClient client, int width, int height) {
        if (tab != 0 || spells().isEmpty()) return;
        int centerX = width / 2;
        int centerY = height / 2;
        int size = 18;
        int innerRadius = 32;
        int cornerRadius = 42;
        int[][] directions = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
        };
        for (int i = 0; i < directions.length; i++) {
            int spellIndex = Math.floorMod(selectedSpell + i, spells().size());
            JsonObject spell = spells().get(spellIndex).getAsJsonObject();
            int radius = directions[i][0] != 0 && directions[i][1] != 0 ? cornerRadius : innerRadius;
            int x = centerX + directions[i][0] * radius - size / 2;
            int y = centerY + directions[i][1] * radius - size / 2;
            boolean selected = i == 0;
            draw.fill(x - 2, y - 2, x + size + 2, y + size + 2, selected ? 0xE6E0B45D : 0xB84B245F);
            draw.fill(x, y, x + size, y + size, selected ? 0x66FFF0C9 : 0xBFD9C9A8);
            draw.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xBFAE9877);
            String glyph = spellGlyph(spell);
            int glyphWidth = client.textRenderer.getWidth(glyph);
            draw.drawText(client.textRenderer, glyph, x + (size - glyphWidth) / 2, y + 7,
                selected ? 0xFF43205A : 0xFF302015, false);
        }
        JsonObject selected = spells().get(selectedSpell).getAsJsonObject();
        String name = selected.get("name").getAsString();
        int maxWidth = Math.min(150, width - 24);
        String label = client.textRenderer.trimToWidth(name, maxWidth);
        int labelWidth = client.textRenderer.getWidth(label);
        int labelX = centerX - labelWidth / 2;
        int labelY = centerY + cornerRadius + size / 2 + 8;
        draw.fill(labelX - 5, labelY - 3, labelX + labelWidth + 5, labelY + 12, 0xB8201427);
        draw.drawText(client.textRenderer, label, labelX, labelY, 0xFFFFEAC8, false);
    }

    private static String spellGlyph(JsonObject spell) {
        if (!spell.has("components") || !spell.get("components").isJsonArray()
            || spell.getAsJsonArray("components").isEmpty()) return "SP";
        String first = spell.getAsJsonArray("components").get(0).getAsString();
        return switch (first) {
            case "fire" -> "FR";
            case "water" -> "WT";
            case "wind" -> "WN";
            case "earth" -> "ER";
            case "lightning" -> "LT";
            case "ice" -> "IC";
            case "explosion" -> "EX";
            case "healing" -> "HL";
            case "summoning" -> "SM";
            case "teleportation" -> "TP";
            case "binding" -> "BD";
            case "projectile" -> "PJ";
            case "beam" -> "BM";
            case "wave" -> "WV";
            case "barrier" -> "BR";
            case "field" -> "FD";
            default -> first.substring(0, 1).toUpperCase();
        };
    }

    private static String trimToWidth(MinecraftClient client, String text, int width) {
        return client.textRenderer.trimToWidth(text, Math.max(1, width));
    }
}
