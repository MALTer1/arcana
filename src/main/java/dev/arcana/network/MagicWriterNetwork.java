package dev.arcana.network;

import dev.arcana.block.entity.MagicWriterBlockEntity;
import dev.arcana.ai.ArcanaAiService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
    public record EngravePayload(int syncId, String name, String components, String description) implements CustomPayload {
        public static final Id<EngravePayload> ID = new Id<>(Identifier.of("arcana", "writer_engrave"));
        public static final PacketCodec<RegistryByteBuf, EngravePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EngravePayload::syncId,
            PacketCodecs.STRING, EngravePayload::name,
            PacketCodecs.STRING, EngravePayload::components,
            PacketCodecs.STRING, EngravePayload::description,
            EngravePayload::new
        );
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RequestKnownPayload() implements CustomPayload {
        public static final Id<RequestKnownPayload> ID = new Id<>(Identifier.of("arcana", "writer_known_request"));
        public static final PacketCodec<RegistryByteBuf, RequestKnownPayload> CODEC =
            PacketCodec.unit(new RequestKnownPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record KnownComponentsPayload(String ids) implements CustomPayload {
        public static final Id<KnownComponentsPayload> ID = new Id<>(Identifier.of("arcana", "writer_known_components"));
        public static final PacketCodec<RegistryByteBuf, KnownComponentsPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, KnownComponentsPayload::ids, KnownComponentsPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AiDesignPayload(int syncId, String name, String components, String idea) implements CustomPayload {
        public static final Id<AiDesignPayload> ID = new Id<>(Identifier.of("arcana", "writer_ai_design"));
        public static final PacketCodec<RegistryByteBuf, AiDesignPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AiDesignPayload::syncId,
            PacketCodecs.STRING, AiDesignPayload::name,
            PacketCodecs.STRING, AiDesignPayload::components,
            PacketCodecs.STRING, AiDesignPayload::idea,
            AiDesignPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AiDesignResultPayload(int syncId, String description, String components, String message) implements CustomPayload {
        public static final Id<AiDesignResultPayload> ID = new Id<>(Identifier.of("arcana", "writer_ai_result"));
        public static final PacketCodec<RegistryByteBuf, AiDesignResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AiDesignResultPayload::syncId,
            PacketCodecs.STRING, AiDesignResultPayload::description,
            PacketCodecs.STRING, AiDesignResultPayload::components,
            PacketCodecs.STRING, AiDesignResultPayload::message,
            AiDesignResultPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private MagicWriterNetwork() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(EngravePayload.ID, EngravePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestKnownPayload.ID, RequestKnownPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AiDesignPayload.ID, AiDesignPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KnownComponentsPayload.ID, KnownComponentsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiDesignResultPayload.ID, AiDesignResultPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RequestKnownPayload.ID, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (player.currentScreenHandler instanceof MagicWriterScreenHandler handler && handler.canUse(player)) {
                    String ids = String.join(",", ComponentKnowledgeStore.get(player.getUuid()));
                    ServerPlayNetworking.send(player, new KnownComponentsPayload(ids));
                }
            }));
        ServerPlayNetworking.registerGlobalReceiver(EngravePayload.ID,
            (payload, context) -> context.server().execute(() -> engrave(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AiDesignPayload.ID,
            (payload, context) -> context.server().execute(() -> design(context.player(), payload)));
    }

    private static void design(ServerPlayerEntity player, AiDesignPayload payload) {
        if (!(player.currentScreenHandler instanceof MagicWriterScreenHandler handler)
            || handler.syncId != payload.syncId() || !handler.canUse(player)) return;
        List<String> known = new ArrayList<>(ComponentKnowledgeStore.get(player.getUuid()));
        List<String> selected = new ArrayList<>();
        if (payload.components() != null && !payload.components().isBlank()) {
            for (String id : payload.components().split(",")) {
                if (known.contains(id) && ComponentRegistry.get(id) != null && !selected.contains(id)) selected.add(id);
            }
        }
        String name = payload.name() == null ? "" : payload.name().trim();
        String idea = payload.idea() == null ? "" : payload.idea().trim();
        if (idea.isBlank()) {
            ServerPlayNetworking.send(player, new AiDesignResultPayload(payload.syncId(), "",
                String.join(",", selected), "Describe the spell idea first."));
            return;
        }
        ServerPlayNetworking.send(player, new AiDesignResultPayload(payload.syncId(), "",
            String.join(",", selected), "Arcana AI is designing your spell..."));
        ArcanaAiService.designSpell(name, idea, selected, known).whenComplete((result, error) ->
            player.getServer().execute(() -> {
                if (!(player.currentScreenHandler instanceof MagicWriterScreenHandler current)
                    || current.syncId != payload.syncId() || !current.canUse(player)) return;
                if (error != null || result == null) {
                    String detail = error == null ? "Unknown AI error." : ArcanaAiService.failureMessage(error);
                    ServerPlayNetworking.send(player, new AiDesignResultPayload(payload.syncId(), "",
                        String.join(",", selected), "AI design failed: " + detail));
                    return;
                }
                String description = result.has("description") && !result.get("description").isJsonNull()
                    ? result.get("description").getAsString().trim() : "";
                if (description.length() > 180) description = description.substring(0, 180);
                List<String> generated = new ArrayList<>();
                if (result.has("components") && result.get("components").isJsonArray()) {
                    JsonArray array = result.getAsJsonArray("components");
                    for (int i = 0; i < array.size() && generated.size() < SpellDraft.MAX_NORMAL_COMPONENTS; i++) {
                        if (!array.get(i).isJsonPrimitive()) continue;
                        String id = array.get(i).getAsString();
                        if (known.contains(id) && ComponentRegistry.get(id) != null && !generated.contains(id)) generated.add(id);
                    }
                }
                if (generated.isEmpty()) generated.addAll(selected);
                if (generated.isEmpty()) generated.addAll(fallbackDesign(idea, known));
                // Keep a valid delivery Form at the front. AI output is never
                // trusted to bypass learned-component or spell-composition checks.
                generated.removeIf(id -> !known.contains(id) || ComponentRegistry.get(id) == null);
                SpellDraft draft = new SpellDraft();
                boolean valid = true;
                for (String id : generated) {
                    if (!draft.add(id).success()) { valid = false; break; }
                }
                if (!valid || !draft.isValid()) {
                    generated = new ArrayList<>(selected);
                    String message = "AI description generated; component suggestion was invalid, so your selected glyphs were kept.";
                    ServerPlayNetworking.send(player, new AiDesignResultPayload(payload.syncId(), description,
                        String.join(",", generated), message));
                    return;
                }
                ServerPlayNetworking.send(player, new AiDesignResultPayload(payload.syncId(), description,
                    String.join(",", generated), "AI designed the spell description and selected compatible learned glyphs. Review before engraving."));
            }));
    }

    private static List<String> fallbackDesign(String idea, List<String> known) {
        String text = idea == null ? "" : idea.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        String form = known.contains("projectile") ? "projectile"
            : known.contains("beam") ? "beam"
            : known.contains("wave") ? "wave"
            : known.stream().filter(id -> {
                SpellComponent component = ComponentRegistry.get(id);
                return component != null && component.category() == ComponentCategory.DELIVERY;
            }).findFirst().orElse("");
        if (!form.isBlank()) result.add(form);
        String[][] hints = {
            {"heal", "healing"}, {"restore", "healing"}, {"fire", "fire"},
            {"burn", "fire"}, {"flame", "fire"}, {"freeze", "ice"},
            {"frost", "ice"}, {"cold", "ice"}, {"lightning", "lightning"},
            {"electric", "lightning"}, {"shock", "lightning"}, {"ash", "smoke"},
            {"smoke", "smoke"}, {"earth", "earth"}, {"stone", "earth"},
            {"water", "water"}, {"wind", "wind"}, {"protect", "barrier"},
            {"shield", "barrier"}, {"summon", "summoning"},
            {"teleport", "teleportation"}, {"bind", "binding"},
            {"slow", "binding"}, {"invisible", "invisibility"},
            {"dark", "shadow"}, {"light", "light"}
        };
        for (String[] hint : hints) {
            if (text.contains(hint[0]) && known.contains(hint[1]) && !result.contains(hint[1])) {
                result.add(hint[1]);
            }
        }
        if (result.size() == 1) {
            String effect = known.stream().filter(id -> {
                SpellComponent component = ComponentRegistry.get(id);
                return component != null && component.category() == ComponentCategory.EFFECT;
            }).findFirst().orElse("");
            if (!effect.isBlank()) result.add(effect);
        }
        return result;
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

        String description = payload.description() == null ? "" : payload.description().trim();
        if (description.length() > 180) {
            player.sendMessage(Text.literal("Spell descriptions must be 180 characters or fewer."), false);
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
                if (!ComponentKnowledgeStore.knows(player.getUuid(), id)) {
                    player.sendMessage(Text.literal("You have not learned the component: " + id), false);
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
        SpellIntelligence.Analysis analysis = SpellIntelligence.analyze(name, draft.selectedIds());

        ItemStack book = writer.getStack(0);
        ItemStack input = writer.getStack(1);
        if (input.isEmpty() || !input.isOf(ArcanaItems.MAGIC_PAPER)) {
            player.sendMessage(Text.literal("Insert Magic Paper to engrave a spell."), false);
            return;
        }

        GrimoireTier tier = writer.getInsertedTier();
        SpellBookStore.StoredSpell existingPage = SpellPageStore.get(input);
        SpellBookStore.StoredSpell existingBookSpell = tier == null ? null : GrimoireStore.find(book, tier, name);
        SpellBookStore.StoredSpell previous = existingPage != null ? existingPage : existingBookSpell;
        boolean keepLegendary = previous != null && previous.legendary;
        if (keepLegendary && tier != null && tier != GrimoireTier.MASTER) {
            player.sendMessage(Text.literal("Legendary spells can only be stored in a Master Grimoire."), false);
            return;
        }
        int allowedComponents = keepLegendary ? 8 : (tier == null
            ? SpellDraft.MAX_NORMAL_COMPONENTS : tier.maxComponents());
        if (draft.selectedIds().size() > allowedComponents) {
            player.sendMessage(Text.literal((keepLegendary ? "Legendary pages support up to 8 glyphs."
                : "This page can hold up to " + allowedComponents + " glyphs.") ), false);
            return;
        }
        SpellBookStore.StoredSpell spell = new SpellBookStore.StoredSpell(
            name, draft.selectedIds(), draft.behavior(), keepLegendary,
            keepLegendary ? LegendarySpellEngine.describe(draft.selectedIds()) : "", description);

        if (tier == null) {
            // The spell is written onto the same item type the player inserted.
            // Copy the complete stack first so existing custom data/components survive.
            ItemStack engravedPaper = input.copy();
            engravedPaper.setCount(1);
            SpellPageStore.set(engravedPaper, spell);

            boolean fitsInventory = canFitInMainInventory(engravedPaper, player.getInventory());
            if (!fitsInventory && !writer.getStack(2).isEmpty()) {
                player.sendMessage(Text.literal("No space! Free an inventory slot or collect the Magic Writer output."), false);
                return;
            }

            if (fitsInventory) {
                ItemStack toInsert = engravedPaper.copy();
                player.getInventory().insertStack(toInsert);
                if (!toInsert.isEmpty()) {
                    if (!writer.getStack(2).isEmpty()) {
                        player.sendMessage(Text.literal("No space! Free an inventory slot or collect the Magic Writer output."), false);
                        return;
                    }
                    writer.setOutput(toInsert);
                }
            } else {
                writer.setOutput(engravedPaper);
            }

            writer.consumeInput();
            writer.markDirty();
            player.sendMessage(Text.literal("Engraved '" + name + "' onto Magic Paper."), false);
            player.sendMessage(Text.literal("Spell Intelligence: " + analysis.summary()), false);
            analysis.warnings().forEach(warning ->
                player.sendMessage(Text.literal("Spell Intelligence: " + warning), false));
            return;
        }

        if (draft.selectedIds().size() > (keepLegendary ? 8 : tier.maxComponents())) {
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
        player.sendMessage(Text.literal("Engraved '" + name + "' into this " + tier.displayName() + "."), false);
        player.sendMessage(Text.literal("Spell Intelligence: " + analysis.summary()), false);
        analysis.warnings().forEach(warning ->
            player.sendMessage(Text.literal("Spell Intelligence: " + warning), false));
    }

    private static boolean canFitInMainInventory(ItemStack stack, PlayerInventory inventory) {
        int capacity = 0;
        for (ItemStack existing : inventory.main) {
            if (existing.isEmpty()) capacity += stack.getMaxCount();
            else if (ItemStack.areItemsAndComponentsEqual(existing, stack))
                capacity += Math.max(0, existing.getMaxCount() - existing.getCount());
            if (capacity >= stack.getCount()) return true;
        }
        return false;
    }
}
