package dev.arcana.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.spell.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GrimoireNetwork {
    private static final Gson GSON = new Gson();
    public record ActionPayload(String action, String name) implements CustomPayload {
        public static final Id<ActionPayload> ID = new Id<>(Identifier.of("arcana", "grimoire_action"));
        public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ActionPayload::action, PacketCodecs.STRING, ActionPayload::name, ActionPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record StatePayload(String json) implements CustomPayload {
        public static final Id<StatePayload> ID = new Id<>(Identifier.of("arcana", "grimoire_state"));
        public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, StatePayload::json, StatePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    private GrimoireNetwork() {}

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID,
            (payload, context) -> context.server().execute(() -> handle(context.player(), payload.action(), payload.name())));
    }

    private static ItemStack activeStack(ServerPlayerEntity player) {
        if (GrimoireTier.fromItem(player.getOffHandStack().getItem()) != null) return player.getOffHandStack();
        return GrimoireTier.fromItem(player.getMainHandStack().getItem()) != null ? player.getMainHandStack() : ItemStack.EMPTY;
    }

    private static void handle(ServerPlayerEntity player, String action, String value) {
        ItemStack book = activeStack(player);
        GrimoireTier tier = book.isEmpty() ? null : GrimoireTier.fromItem(book.getItem());
        if (tier == null) { send(player, new JsonObject(), "Hold a Grimoire in either hand."); return; }
        String message = "";
        if ("cast".equals(action)) {
            SpellBookStore.StoredSpell spell = GrimoireStore.find(book, tier, value);
            if (spell == null) message = "That spell is not in this Grimoire.";
            else message = SpellCaster.cast(player, spell).message();
        } else if ("remove".equals(action)) {
            SpellBookStore.StoredSpell spell = GrimoireStore.remove(book, tier, value);
            if (spell == null) message = "That page is not in this Grimoire.";
            else {
                ItemStack page = new ItemStack(ArcanaItems.MAGIC_PAPER);
                SpellPageStore.set(page, spell);
                if (!player.getInventory().insertStack(page)) player.dropItem(page, false);
                message = "Removed " + spell.name + " and returned its Magic Paper.";
            }
        } else if ("insert".equals(action)) {
            ItemStack pageStack = findMagicPaper(player, value);
            SpellBookStore.StoredSpell page = pageStack.isEmpty() ? null : SpellPageStore.get(pageStack);
            if (page == null) message = "No engraved Magic Paper named '" + value + "' was found.";
            else if (GrimoireStore.insert(book, tier, page)) {
                pageStack.decrement(1);
                player.getInventory().markDirty();
                message = "Inserted '" + page.name + "' into your Grimoire.";
            } else message = page.legendary && tier != GrimoireTier.MASTER
                ? "Legendary pages can only be stored in a Master Grimoire."
                : "The Grimoire is full, already contains that page, or has too few glyph slots.";
        } else if ("edit_add".equals(action) || "edit_remove".equals(action) || "edit_move_up".equals(action) || "edit_move_down".equals(action)) {
            int split = value.indexOf('|');
            String spellName = split < 0 ? "" : value.substring(0, split);
            String glyph = split < 0 ? "" : value.substring(split + 1);
            SpellBookStore.StoredSpell old = GrimoireStore.find(book, tier, spellName);
            if (old == null || glyph.isBlank() || ComponentRegistry.get(glyph) == null) {
                message = "The selected spell or glyph could not be found.";
            } else {
                java.util.List<String> ids = new java.util.ArrayList<>(old.components);
                if ("edit_add".equals(action)) {
                    if (ids.contains(glyph)) message = "That glyph is already on this page.";
                    else if (ids.size() >= (old.legendary ? 8 : tier.maxComponents()))
                        message = "This spell page has reached its glyph limit.";
                    else ids.add(glyph);
                } else if ("edit_remove".equals(action) && !ids.remove(glyph)) message = "That glyph is not on this page.";
                else if ("edit_move_up".equals(action) || "edit_move_down".equals(action)) {
                    int at = ids.indexOf(glyph);
                    int next = at + ("edit_move_up".equals(action) ? -1 : 1);
                    if (at < 0 || next < 0 || next >= ids.size()) message = "That glyph cannot move in that direction.";
                    else { java.util.Collections.swap(ids, at, next); }
                }
                if (message.isEmpty()) {
                    SpellIntelligence.Analysis analysis = SpellIntelligence.analyze(old.name, ids);
                    if (!analysis.isValid()) message = String.join(" ", analysis.issues());
                    else {
                        SpellBookStore.StoredSpell updated = new SpellBookStore.StoredSpell(
                            old.name, ids, analysis.summary(), old.legendary, old.legendaryEffect);
                        if (GrimoireStore.replace(book, tier, updated)) {
                            player.getInventory().markDirty();
                            message = "Updated " + old.name + ". New Arcana cost: " + updated.arcanaCost() + ".";
                        } else message = "The Grimoire rejected the edited page.";
                    }
                }
            }
        } else if ("legendary".equals(action)) {
            SpellBookStore.StoredSpell old = GrimoireStore.find(book, tier, value);
            if (tier != GrimoireTier.MASTER) message = "Only a Master Grimoire can hold Legendary spells.";
            else if (old == null) message = "That spell is not in this Grimoire.";
            else if (old.legendary) message = "This spell is already Legendary.";
            else if (!consumeScroll(player)) message = "You need a Legendary Scroll in your inventory.";
            else {
                SpellBookStore.StoredSpell upgraded = new SpellBookStore.StoredSpell(old.name, old.components,
                    old.behavior, true, LegendarySpellEngine.describe(old.components));
                if (GrimoireStore.replace(book, tier, upgraded)) {
                    player.getInventory().markDirty();
                    message = old.name + " has become LEGENDARY! " + upgraded.legendaryEffect;
                } else message = "The Master Grimoire could not store this Legendary page.";
            }
        } else if ("legendary_page".equals(action)) {
            ItemStack pageStack = findMagicPaper(player, value);
            SpellBookStore.StoredSpell old = pageStack.isEmpty() ? null : SpellPageStore.get(pageStack);
            if (old == null) message = "That engraved page is not in your inventory.";
            else if (old.legendary) message = "This page is already Legendary.";
            else if (!consumeScroll(player)) message = "You need a Legendary Scroll in your inventory.";
            else {
                SpellPageStore.set(pageStack, new SpellBookStore.StoredSpell(old.name, old.components,
                    old.behavior, true, LegendarySpellEngine.describe(old.components)));
                player.getInventory().markDirty();
                message = old.name + " page awakened as Legendary. It can now only be inserted into a Master Grimoire.";
            }
        }
        send(player, buildState(player, book, tier), message);
    }

    private static boolean consumeScroll(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ArcanaItems.LEGENDARY_SCROLL)) {
                stack.decrement(1);
                player.getInventory().markDirty();
                return true;
            }
        }
        return false;
    }

    private static ItemStack findMagicPaper(ServerPlayerEntity player, String name) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ArcanaItems.MAGIC_PAPER) || stack.isOf(ArcanaItems.SPELL_PAGE)) {
                SpellBookStore.StoredSpell page = SpellPageStore.get(stack);
                if (page != null && page.name.equalsIgnoreCase(name)) return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void syncHeldGrimoire(ServerPlayerEntity player) {
        ItemStack book = activeStack(player);
        GrimoireTier tier = book.isEmpty() ? null : GrimoireTier.fromItem(book.getItem());
        if (tier != null) send(player, buildState(player, book, tier), "");
    }

    private static JsonObject buildState(ServerPlayerEntity player, ItemStack book, GrimoireTier tier) {
        JsonObject root = new JsonObject();
        root.addProperty("tier", tier.displayName());
        root.addProperty("slots", tier.spellSlots());
        root.addProperty("maxComponents", tier.maxComponents());
        root.addProperty("arcana", ArcanaResource.current(player));
        root.addProperty("maxArcana", ArcanaResource.MAX_ARCANA);
        JsonArray spells = new JsonArray();
        for (SpellBookStore.StoredSpell spell : GrimoireStore.get(book, tier)) {
            JsonObject item = new JsonObject();
            item.addProperty("name", spell.name);
            item.addProperty("behavior", spell.behavior == null ? "" : spell.behavior);
            item.addProperty("legendary", spell.legendary);
            item.addProperty("legendaryEffect", spell.legendaryEffect == null ? "" : spell.legendaryEffect);
            SpellCostCalculator.Cost cost = SpellCostCalculator.calculate(spell.components, spell.legendary);
            item.addProperty("cost", cost.total());
            item.addProperty("costBreakdown", "F" + cost.formCost() + " E" + cost.effectCost()
                + " A" + cost.augmentCost() + " El" + cost.elementalCost()
                + " C" + cost.complexityCost() + " L" + cost.legendaryCost());
            JsonArray components = new JsonArray();
            if (spell.components != null) spell.components.forEach(components::add);
            item.add("components", components);
            JsonArray effects = new JsonArray();
            JsonArray effectDescriptions = new JsonArray();
            if (spell.components != null) for (String id : spell.components) {
                SpellComponent component = ComponentRegistry.get(id);
                if (component != null && component.category() == ComponentCategory.EFFECT) {
                    effects.add(component.displayName());
                    effectDescriptions.add(component.displayName() + ": " + component.description());
                }
            }
            item.add("effects", effects);
            item.add("effectDescriptions", effectDescriptions);
            SpellIntelligence.Analysis analysis = SpellIntelligence.analyze(spell.name, spell.components);
            JsonArray warnings = new JsonArray();
            analysis.warnings().forEach(warnings::add);
            item.add("warnings", warnings);
            spells.add(item);
        }
        root.add("spells", spells);
        JsonArray pages = new JsonArray();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ArcanaItems.MAGIC_PAPER) || stack.isOf(ArcanaItems.SPELL_PAGE)) {
                SpellBookStore.StoredSpell page = SpellPageStore.get(stack);
                if (page != null) pages.add(page.name);
            }
        }
        root.add("loosePages", pages);
        JsonArray glyphs = new JsonArray();
        for (SpellComponent component : ComponentRegistry.all()) {
            JsonObject glyph = new JsonObject();
            glyph.addProperty("id", component.id());
            glyph.addProperty("name", component.displayName());
            glyph.addProperty("category", component.category().displayName());
            glyph.addProperty("description", component.description());
            glyphs.add(glyph);
        }
        root.add("glyphs", glyphs);
        root.addProperty("hasLegendaryScroll", hasScroll(player));
        return root;
    }

    private static boolean hasScroll(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++)
            if (player.getInventory().getStack(i).isOf(ArcanaItems.LEGENDARY_SCROLL)) return true;
        return false;
    }

    private static void send(ServerPlayerEntity player, JsonObject state, String message) {
        state.addProperty("message", message);
        ServerPlayNetworking.send(player, new StatePayload(GSON.toJson(state)));
    }
}
