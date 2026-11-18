package dev.arcana.network;

import dev.arcana.block.entity.MagicWriterBlockEntity;
import dev.arcana.item.GrimoireTier;
import dev.arcana.screen.MagicWriterScreenHandler;
import dev.arcana.spell.GrimoireStore;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellDraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class MagicWriterNetwork {
    public record EngravePayload(int syncId, String name, String components) implements CustomPayload {
        public static final Id<EngravePayload> ID = new Id<>(Identifier.of("arcana", "writer_engrave"));
        public static final PacketCodec<RegistryByteBuf, EngravePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EngravePayload::syncId,
            PacketCodecs.STRING, EngravePayload::name,
            PacketCodecs.STRING, EngravePayload::components,
            EngravePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private MagicWriterNetwork() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(EngravePayload.ID, EngravePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(EngravePayload.ID,
            (payload, context) -> context.server().execute(() -> engrave(context.player(), payload)));
    }

    private static void engrave(ServerPlayerEntity player, EngravePayload payload) {
        if (!(player.currentScreenHandler instanceof MagicWriterScreenHandler handler)
            || handler.syncId != payload.syncId() || !handler.canUse(player)) return;
        if (!(handler.getWriterInventory() instanceof MagicWriterBlockEntity writer)) return;

        String name = payload.name().trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 32) {
            player.sendMessage(Text.literal("Spell names must contain 1–32 characters."), false); return;
        }
        var book = writer.getStack(0);
        GrimoireTier tier = writer.getInsertedTier();
        if (tier == null) {
            player.sendMessage(Text.literal("Place a Grimoire in the highlighted book slot."), false); return;
        }
        List<String> ids = new ArrayList<>();
        if (!payload.components().isBlank()) for (String id : payload.components().split(",")) {
            if (!id.isBlank() && !ids.contains(id)) ids.add(id);
        }
        if (ids.size() > tier.maxComponents()) {
            player.sendMessage(Text.literal(tier.displayName() + " supports up to " + tier.maxComponents() + " components."), false); return;
        }
        SpellDraft draft = new SpellDraft();
        for (String id : ids) {
            SpellDraft.AddResult result = draft.add(id);
            if (!result.success()) { player.sendMessage(Text.literal(result.message()), false); return; }
        }
        if (!draft.isValid()) {
            draft.validationIssues().forEach(issue -> player.sendMessage(Text.literal("• " + issue), false)); return;
        }
        if (GrimoireStore.find(book, tier, name) != null) {
            player.sendMessage(Text.literal("This Grimoire already has a spell named '" + name + "'."), false); return;
        }
        if (writer.getStack(1).isEmpty()) {
            player.sendMessage(Text.literal("Place Magic Paper in the lower writer slot."), false); return;
        }
        SpellBookStore.StoredSpell spell = new SpellBookStore.StoredSpell(name, draft.selectedIds(), draft.behavior());
        if (!GrimoireStore.insert(book, tier, spell)) {
            player.sendMessage(Text.literal("The Grimoire is full or cannot hold this spell."), false); return;
        }
        writer.consumePaper();
        player.getInventory().markDirty();
        player.sendMessage(Text.literal("Engraved '" + name + "' into this " + tier.displayName() + "."), false);
    }
}
