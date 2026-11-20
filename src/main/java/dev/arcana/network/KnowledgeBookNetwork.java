package dev.arcana.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.arcana.item.ArcanaItems;
import dev.arcana.spell.ComponentRegistry;
import dev.arcana.spell.ComponentCategory;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellComponent;
import dev.arcana.spell.SpellIntelligence;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

public final class KnowledgeBookNetwork {
    private static final Gson GSON = new Gson();
    public record RequestPayload(String request) implements CustomPayload {
        public static final Id<RequestPayload> ID = new Id<>(Identifier.of("arcana", "knowledge_book_request"));
        public static final PacketCodec<RegistryByteBuf, RequestPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, RequestPayload::request, RequestPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record StatePayload(String json) implements CustomPayload {
        public static final Id<StatePayload> ID = new Id<>(Identifier.of("arcana", "knowledge_book_state"));
        public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, StatePayload::json, StatePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private KnowledgeBookNetwork() {}

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(RequestPayload.ID, RequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RequestPayload.ID, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                boolean holding = player.getMainHandStack().isOf(ArcanaItems.BOOK_OF_KNOWLEDGE)
                    || player.getOffHandStack().isOf(ArcanaItems.BOOK_OF_KNOWLEDGE);
                if (holding) ServerPlayNetworking.send(player, new StatePayload(GSON.toJson(buildState(player))));
            }));
    }

    private static JsonObject buildState(ServerPlayerEntity player) {
        JsonObject root = new JsonObject();
        Set<String> known = new HashSet<>();
        var savedSpells = SpellBookStore.get(player.getUuid());
        for (SpellBookStore.StoredSpell spell : savedSpells) {
            if (spell.components != null) known.addAll(spell.components);
        }

        JsonArray components = new JsonArray();
        for (SpellComponent component : ComponentRegistry.all()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", component.id());
            item.addProperty("name", component.displayName());
            item.addProperty("category", component.category().displayName());
            item.addProperty("description", component.description());
            item.addProperty("known", known.contains(component.id()));
            components.add(item);
        }
        root.add("components", components);

        JsonArray spells = new JsonArray();
        for (SpellBookStore.StoredSpell spell : savedSpells) {
            JsonObject item = new JsonObject();
            item.addProperty("name", spell.name);
            item.addProperty("behavior", spell.behavior == null ? "" : spell.behavior);
            item.addProperty("description", spell.customDescription == null ? "" : spell.customDescription);
            item.addProperty("cost", spell.arcanaCost());
            item.addProperty("legendary", spell.legendary);
            item.addProperty("legendaryEffect", spell.legendaryEffect == null ? "" : spell.legendaryEffect);
            JsonArray ids = new JsonArray();
            if (spell.components != null) spell.components.forEach(ids::add);
            item.add("components", ids);
            SpellIntelligence.Analysis analysis = SpellIntelligence.analyze(
                spell.name, spell.components, spell.customDescription);
            JsonArray warnings = new JsonArray();
            analysis.warnings().forEach(warnings::add);
            item.add("analysis", warnings);
            spells.add(item);
        }
        root.add("spells", spells);
        root.addProperty("componentCount", components.size());
        root.addProperty("knownCount", known.size());
        root.addProperty("spellCount", spells.size());
        return root;
    }
}
