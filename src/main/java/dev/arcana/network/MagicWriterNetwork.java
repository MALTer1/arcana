package dev.arcana.network;

import dev.arcana.block.entity.MagicWriterBlockEntity;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.screen.MagicWriterScreenHandler;
import dev.arcana.spell.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

public final class MagicWriterNetwork {
    public record EngravePayload(int syncId, String name, String components) implements CustomPayload {
        public static final Id<EngravePayload> ID = new Id<>(Identifier.of("arcana", "writer_engrave"));
        public static final PacketCodec<RegistryByteBuf, EngravePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EngravePayload::syncId,
            PacketCodecs.STRING, EngravePayload::name,
            PacketCodecs.STRING, EngravePayload::components,
            EngravePayload::new
        );
        public Id<? extends CustomPayload> getId() { return ID; }
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
            player.sendMessage(Text.literal("Spell names must contain 1–32 characters."), false);
            return;
        }

        List<String> ids = new ArrayList<>();
        if (!payload.components().isBlank()) {
            for (String id : payload.components().split(",")) {
                if (id.isBlank() || ids.contains(id)) continue;
                if (ComponentRegistry.get(id) == null) {
                    player.sendMessage(Text.literal("Unknown component: " + id), false);
                    return;
                }
                ids.add(id);
            }
        }

        SpellDraft draft = new SpellDraft();
        for (String id : ids) {
            var result = draft.add(id);
            if (!result.success()) {
                player.sendMessage(Text.literal(result.message()), false);
                return;
            }
        }
        if (!draft.isValid()) {
            draft.validationIssues().forEach(issue -> player.sendMessage(Text.literal("• " + issue), false));
            return;
        }

        ItemStack book = writer.getStack(0);
        ItemStack input = writer.getStack(1);
        if (input.isEmpty()) {
            player.sendMessage(Text.literal("Insert Magic Paper or an editable Spell Page."), false);
            return;
        }

        boolean editingPage = input.isOf(ArcanaItems.SPELL_PAGE);
        if (editingPage && SpellPageStore.get(input) == null) {
            player.sendMessage(Text.literal("That Spell Page has no editable spell data."), false);
            return;
        }

        GrimoireTier tier = writer.getInsertedTier();
        SpellBookStore.StoredSpell spell =
            new SpellBookStore.StoredSpell(name, draft.selectedIds(), draft.behavior());

        if (tier == null) {
            ItemStack output = editingPage ? input.copy() : new ItemStack(ArcanaItems.SPELL_PAGE);
            output.setCount(1);
            SpellPageStore.set(output, spell);

            // Reserve a safe destination before consuming the input. A page goes into
            // the player's inventory when possible, otherwise into the writer output.
            boolean fitsInventory = canFitInMainInventory(output, player.getInventory());
            boolean useOutput = !fitsInventory;
            if (useOutput && !writer.getStack(2).isEmpty()) {
                player.sendMessage(Text.literal("No space! Free an inventory slot or collect the Magic Writer output."), false);
                return;
            }

            if (fitsInventory) {
                ItemStack toInsert = output.copy();
                player.getInventory().insertStack(toInsert);
                if (!toInsert.isEmpty()) {
                    // Defensive fallback in case inventory capacity changed unexpectedly.
                    if (!writer.getStack(2).isEmpty()) {
                        player.sendMessage(Text.literal("No space! Free an inventory slot or collect the Magic Writer output."), false);
                        return;
                    }
                    writer.setOutput(toInsert);
                }
            } else {
                writer.setOutput(output);
            }

            writer.consumeInput();
            writer.markDirty();
            player.sendMessage(Text.literal("Finished '" + name + "' as a Spell Page."), false);
            return;
        }

        if (draft.selectedIds().size() > tier.maxComponents()) {
            player.sendMessage(Text.literal(tier.displayName() + " supports up to " + tier.maxComponents() + " components."), false);
            return;
        }

        SpellBookStore.StoredSpell old = GrimoireStore.find(book, tier, name);
        if (old != null) GrimoireStore.remove(book, tier, name);
        if (!GrimoireStore.insert(book, tier, spell)) {
            if (old != null) GrimoireStore.insert(book, tier, old);
            player.sendMessage(Text.literal("The Grimoire is full or cannot hold this spell."), false);
            return;
        }

        writer.consumeInput();
        writer.markDirty();
        player.getInventory().markDirty();
        player.sendMessage(Text.literal((editingPage ? "Edited and engraved '" : "Engraved '")
            + name + "' into this " + tier.displayName() + "."), false);
    }

    private static boolean canFitInMainInventory(ItemStack stack, PlayerInventory inventory) {
        int capacity = 0;
        for (ItemStack existing : inventory.main) {
            if (existing.isEmpty()) {
                capacity += stack.getMaxCount();
            } else if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                capacity += Math.max(0, existing.getMaxCount() - existing.getCount());
            }
            if (capacity >= stack.getCount()) return true;
        }
        return false;
    }
}
