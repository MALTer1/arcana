package dev.arcana.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.spell.ArcanaResource;
import dev.arcana.spell.GrimoireStore;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellCaster;
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

    private static void handle(ServerPlayerEntity player, String action, String name) {
        ItemStack book = activeStack(player);
        GrimoireTier tier = book.isEmpty() ? null : GrimoireTier.fromItem(book.getItem());
        if (tier == null) { send(player, new JsonObject(), "Hold a Grimoire in either hand."); return; }
        String message = "";
        if ("cast".equals(action)) {
            SpellBookStore.StoredSpell spell = GrimoireStore.find(book, tier, name);
            if (spell == null) message = "That spell is not in this Grimoire.";
            else message = SpellCaster.cast(player, spell).message();
        } else if ("remove".equals(action)) {
            SpellBookStore.StoredSpell spell = GrimoireStore.remove(book, tier, name);
            if (spell == null) message = "That page is not in this Grimoire.";
            else {
                ItemStack page = new ItemStack(ArcanaItems.SPELL_PAGE);
                page.set(DataComponentTypes.CUSTOM_NAME, Text.literal(spell.name));
                if (!player.getInventory().insertStack(page)) player.dropItem(page, false);
                message = "Removed " + spell.name + " and returned its page.";
            }
        } else if ("insert".equals(action)) {
            message = "New spells are engraved directly onto a Grimoire at the Magic Writer.";
        }
        send(player, buildState(player, book, tier), message);
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
            JsonArray components = new JsonArray();
            if (spell.components != null) spell.components.forEach(components::add);
            item.add("components", components);
            spells.add(item);
        }
        root.add("spells", spells);
        JsonArray pages = new JsonArray();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ArcanaItems.SPELL_PAGE) && stack.contains(DataComponentTypes.CUSTOM_NAME)) pages.add(stack.getName().getString());
        }
        root.add("loosePages", pages);
        return root;
    }

    private static void send(ServerPlayerEntity player, JsonObject state, String message) {
        state.addProperty("message", message);
        ServerPlayNetworking.send(player, new StatePayload(GSON.toJson(state)));
    }
}
