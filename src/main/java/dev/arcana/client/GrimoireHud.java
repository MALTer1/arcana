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
        } catch (RuntimeException ignored) {
            notice = "Could not read grimoire pages.";
            noticeUntil = System.currentTimeMillis() + 3500;
        }
    }

    public static void refresh() { send("refresh", ""); }
    public static void previousPage() {
        if (tab == 0) selectedSpell = Math.max(0, selectedSpell - 1);
        else if (tab == 1) selectedLoosePage = Math.max(0, selectedLoosePage - 1);
    }
    public static void nextPage() {
        if (tab == 0) selectedSpell = Math.min(Math.max(0, spells().size() - 1), selectedSpell + 1);
        else if (tab == 1) selectedLoosePage = Math.min(Math.max(0, pages().size() - 1), selectedLoosePage + 1);
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
        draw.drawTextWithShadow(client.textRenderer, value, x, y, color);
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
                current.append(word);
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
        }
        if (current.length() > 0 && row < maxLines) {
            line(draw, client, current.toString(), x, y + row * 11, color);
        }
    }

    private static void panel(DrawContext draw, int x, int y, int width, int height, boolean right) {
        // 0xE6 alpha leaves the parchment and frame approximately 10% transparent.
        draw.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xE6170D20);
        draw.fill(x, y, x + width, y + height, 0xE64B245F);
        draw.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0xE6FFF1D5);
        draw.fill(x + 7, y + 7, x + width - 7, y + height - 7,
            right ? 0xE6F7EBD4 : 0xE6F4E4C3);
        draw.fill(x + 7, y + 7, x + width - 7, y + 9, 0xE6B18A5C);
        draw.fill(x + 7, y + height - 9, x + width - 7, y + height - 7, 0xE6B18A5C);
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
        int top = y + 28;

        panel(draw, leftX, y, panelWidth, panelHeight, false);
        panel(draw, rightX, y, panelWidth, panelHeight, true);

        String tier = state.has("tier") ? state.get("tier").getAsString() : "Grimoire";
        line(draw, client, tier.toUpperCase(), leftX + innerX, y + 11, accent);
        String[] labels = {"SPELL", "PAGES", "ARC", "BOOK"};
        int tabWidth = Math.max(26, contentWidth / 4);
        for (int i = 0; i < labels.length; i++) {
            int tx = leftX + innerX + i * tabWidth;
            if (i == tab) draw.fill(tx - 1, y + 24, tx + tabWidth - 2, y + 35, 0xE64B245F);
            line(draw, client, trimToWidth(client, labels[i], tabWidth - 3), tx, y + 25, i == tab ? 0xFFFFEAC8 : muted);
        }

        String rightTitle = switch (tab) {
            case 0 -> "SPELL DETAILS";
            case 1 -> "PAGE PREVIEW";
            case 2 -> "ARCANA";
            default -> "BOOK DETAILS";
        };
        line(draw, client, rightTitle, rightX + innerX, y + 12, accent);
        draw.fill(rightX + innerX, y + 25, rightX + panelWidth - innerX, y + 27, 0xE6B89A70);

        if (tab == 0) {
            line(draw, client, "SPELL INDEX", leftX + innerX, top, muted);
            if (spells().isEmpty()) {
                wrapped(draw, client, "No spells stored. Create a spell and engrave a page at the Magic Writer.",
                    leftX + innerX, top + 20, contentWidth, ink, 7);
                wrapped(draw, client, "Select a spell to see its components, behavior and Arcana cost here.",
                    rightX + innerX, top + 24, contentWidth, ink, 7);
            } else {
                int visible = Math.max(1, Math.min(8, (panelHeight - 76) / 15));
                int start = Math.max(0, Math.min(selectedSpell - visible / 2, spells().size() - visible));
                for (int i = start; i < Math.min(start + visible, spells().size()); i++) {
                    String name = spells().get(i).getAsJsonObject().get("name").getAsString();
                    String label = (i == selectedSpell ? "› " : "  ") + name;
                    line(draw, client, trimToWidth(client, label, contentWidth), leftX + innerX,
                        top + 18 + (i - start) * 15, i == selectedSpell ? accent : ink);
                }
                JsonObject selected = spells().get(selectedSpell).getAsJsonObject();
                String name = selected.get("name").getAsString();
                line(draw, client, trimToWidth(client, name, contentWidth), rightX + innerX, top + 8, accent);
                JsonArray components = selected.has("components") ? selected.getAsJsonArray("components") : new JsonArray();
                line(draw, client, "Components (" + components.size() + ")", rightX + innerX, top + 27, muted);
                int componentY = top + 42;
                for (int i = 0; i < components.size() && i < 6; i++) {
                    line(draw, client, trimToWidth(client, "• " + components.get(i).getAsString(), contentWidth),
                        rightX + innerX, componentY + i * 12, ink);
                }
                int cost = 10 + components.size() * 5;
                line(draw, client, "Cost: " + cost + " Arcana", rightX + innerX, componentY + Math.min(components.size(), 6) * 12 + 5, accent);
                String behavior = selected.has("behavior") ? selected.get("behavior").getAsString() : "No description available.";
                wrapped(draw, client, behavior, rightX + innerX, componentY + Math.min(components.size(), 6) * 12 + 20,
                    contentWidth, ink, Math.max(2, (panelHeight - 118) / 11));
            }
        } else if (tab == 1) {
            line(draw, client, "LOOSE SPELL PAGES", leftX + innerX, top, muted);
            if (pages().isEmpty()) {
                wrapped(draw, client, "No named Spell Pages in your inventory.", leftX + innerX, top + 20, contentWidth, ink, 6);
                wrapped(draw, client, "Engrave a spell first, then select its loose page and press Enter to insert it.",
                    rightX + innerX, top + 22, contentWidth, ink, 7);
            } else {
                int visible = Math.max(1, Math.min(10, (panelHeight - 76) / 15));
                int start = Math.max(0, Math.min(selectedLoosePage - visible / 2, pages().size() - visible));
                for (int i = start; i < Math.min(start + visible, pages().size()); i++) {
                    String name = pages().get(i).getAsString();
                    line(draw, client, trimToWidth(client, (i == selectedLoosePage ? "› " : "  ") + name, contentWidth),
                        leftX + innerX, top + 18 + (i - start) * 15, i == selectedLoosePage ? accent : ink);
                }
                line(draw, client, trimToWidth(client, pages().get(selectedLoosePage).getAsString(), contentWidth),
                    rightX + innerX, top + 12, accent);
                wrapped(draw, client, "Press Enter to insert this page into the held grimoire. The book must have an open slot.",
                    rightX + innerX, top + 34, contentWidth, ink, 7);
            }
        } else if (tab == 2) {
            int value = state.has("arcana") ? state.get("arcana").getAsInt() : 0;
            int maximum = state.has("maxArcana") ? state.get("maxArcana").getAsInt() : 100;
            line(draw, client, "CURRENT RESERVE", leftX + innerX, top, muted);
            draw.fill(leftX + innerX, top + 24, leftX + panelWidth - innerX, top + 38, 0xE6B9A88C);
            int barWidth = Math.max(0, (contentWidth - 4) * value / Math.max(1, maximum));
            draw.fill(leftX + innerX + 2, top + 26, leftX + innerX + 2 + barWidth, top + 36, 0xE675409A);
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
        if (System.currentTimeMillis() < noticeUntil && !notice.isBlank()) {
            draw.drawCenteredTextWithShadow(client.textRenderer, notice, screenWidth / 2, Math.max(4, y - 13), 0xFFFFFFFF);
        }
    }

    private static String trimToWidth(MinecraftClient client, String text, int width) {
        return client.textRenderer.trimToWidth(text, Math.max(1, width));
    }
}
