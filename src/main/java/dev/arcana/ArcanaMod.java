package dev.arcana;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.arcana.ai.ArcanaAiService;
import dev.arcana.block.ArcanaBlocks;
import dev.arcana.entity.ArcanaEntities;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.network.GrimoireNetwork;
import dev.arcana.network.KnowledgeBookNetwork;
import dev.arcana.network.MagicWriterNetwork;
import dev.arcana.screen.MagicWriterScreenHandlers;
import dev.arcana.spell.ArcanaResource;
import dev.arcana.spell.ComponentRegistry;
import dev.arcana.spell.ComponentKnowledgeStore;
import dev.arcana.spell.GrimoireStore;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellCaster;
import dev.arcana.spell.SpellPageStore;
import dev.arcana.spell.SpellDraft;
import dev.arcana.spell.TemporarySummonStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArcanaMod implements ModInitializer {
    private static final Map<UUID, SpellDraft> DRAFTS = new ConcurrentHashMap<>();
    private static int hudSyncTicks;

    @Override
    public void onInitialize() {
        ComponentRegistry.bootstrap();
        ArcanaAiService.initialize();
        ArcanaEntities.register();
        GrimoireNetwork.initialize();
        KnowledgeBookNetwork.initialize();
        GrimoireCommands.register();
        ArcanaItems.register();
        ArcanaBlocks.register();
        MagicWriterScreenHandlers.register();
        MagicWriterNetwork.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ArcanaResource.tick(server);
            TemporarySummonStore.tick();
            if (++hudSyncTicks >= 5) {
                hudSyncTicks = 0;
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    GrimoireNetwork.syncHeldGrimoire(player);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SpellBookStore.load(server);
            ComponentKnowledgeStore.load(server);
            ArcanaAiService.load(server);
            GrimoireStore.load(server);
            ArcanaResource.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SpellBookStore.save(server);
            ComponentKnowledgeStore.save(server);
            ArcanaAiService.save(server);
            GrimoireStore.save(server);
            ArcanaResource.save(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ArcanaResource.save(server));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("arcana")
                .then(CommandManager.literal("components").executes(context -> {
                    var source = context.getSource();
                    source.sendFeedback(() -> Text.literal("Spell components:"), false);
                    ComponentRegistry.all().forEach(component -> source.sendFeedback(
                        () -> Text.literal(component.id() + " [" + component.category().displayName() + "] — " + component.description()), false));
                    return ComponentRegistry.all().size();
                }))
                .then(CommandManager.literal("select")
                    .then(CommandManager.argument("component", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            ComponentRegistry.all().forEach(c -> builder.suggest(c.id()));
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            String id = StringArgumentType.getString(context, "component");
                            SpellDraft currentDraft = draft(player);
                            if (currentDraft.selectedIds().size() >= SpellDraft.MAX_NORMAL_COMPONENTS
                                && !currentDraft.selectedIds().contains(id)) {
                                player.sendMessage(Text.literal("Normal spell drafts support five glyphs. Upgrade a page with a Legendary Scroll before adding more."), false);
                                return 0;
                            }
                            SpellDraft.AddResult result = currentDraft.add(id);
                            player.sendMessage(Text.literal(result.message()), false);
                            return result.success() ? 1 : 0;
                        })))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("component", StringArgumentType.word()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        String id = StringArgumentType.getString(context, "component");
                        boolean removed = draft(player).remove(id);
                        player.sendMessage(Text.literal(removed ? "Removed " + id + "." : "That component was not in your draft."), false);
                        return removed ? 1 : 0;
                    })))
                .then(CommandManager.literal("clear").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    draft(player).clear();
                    player.sendMessage(Text.literal("Spell draft cleared."), false);
                    return 1;
                }))
                .then(CommandManager.literal("preview").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    draft(player).preview().forEach(line -> player.sendMessage(Text.literal(line), false));
                    return 1;
                }))
                .then(CommandManager.literal("engrave")
                    .then(CommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        String name = normalizeName(StringArgumentType.getString(context, "name"));
                        if (name.isEmpty() || name.length() > 32) {
                            player.sendMessage(Text.literal("Spell names must contain 1–32 characters."), false);
                            return 0;
                        }
                        SpellDraft spellDraft = draft(player);
                        if (spellDraft.selectedIds().size() > SpellDraft.MAX_NORMAL_COMPONENTS) {
                            player.sendMessage(Text.literal("Normal spell pages support up to five glyphs. A Legendary Scroll raises that limit to eight."), false);
                            return 0;
                        }
                        if (!spellDraft.isValid()) {
                            player.sendMessage(Text.literal("This draft cannot be engraved. Use /arcana preview to see what needs fixing."), false);
                            return 0;
                        }
                        if (SpellBookStore.find(player.getUuid(), name) != null) {
                            player.sendMessage(Text.literal("You already have a spell named '" + name + "'. Choose another name."), false);
                            return 0;
                        }
                        if (!consumeMagicPaper(player)) {
                            player.sendMessage(Text.literal("You need one Magic Paper to engrave a spell."), false);
                            return 0;
                        }
                        SpellBookStore.StoredSpell spell = new SpellBookStore.StoredSpell(
                            name, spellDraft.selectedIds(), spellDraft.behavior());
                        SpellBookStore.add(player.getUuid(), spell);
                        net.minecraft.item.ItemStack page = new net.minecraft.item.ItemStack(ArcanaItems.MAGIC_PAPER);
                        SpellPageStore.set(page, spell);
                        if (!player.getInventory().insertStack(page)) player.dropItem(page, false);
                        spellDraft.clear();
                        SpellBookStore.save(player.getServer());
                        player.sendMessage(Text.literal("Engraved '" + name + "' onto Magic Paper. Hold your Grimoire and use its Pages tab to insert it."), false);
                        return 1;
                    })))
                .then(CommandManager.literal("spells").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    var spells = SpellBookStore.get(player.getUuid());
                    if (spells.isEmpty()) {
                        player.sendMessage(Text.literal("Your spellbook is empty. Create a draft and use /arcana engrave <name>."), false);
                        return 0;
                    }
                    player.sendMessage(Text.literal("=== YOUR SPELLBOOK (" + spells.size() + ") ==="), false);
                    spells.forEach(spell -> player.sendMessage(
                        Text.literal("• " + spell.name + " — " + String.join(", ", spell.components)
                            + " — " + spell.arcanaCost() + " Arcana" + (spell.legendary ? " — LEGENDARY" : "")), false));
                    return spells.size();
                }))
                .then(CommandManager.literal("inspect")
                    .then(CommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        String name = normalizeName(StringArgumentType.getString(context, "name"));
                        SpellBookStore.StoredSpell spell = SpellBookStore.find(player.getUuid(), name);
                        if (spell == null) {
                            player.sendMessage(Text.literal("No spell named '" + name + "' exists in your spellbook."), false);
                            return 0;
                        }
                        player.sendMessage(Text.literal("=== " + spell.name + " ==="), false);
                        player.sendMessage(Text.literal("Components: " + String.join(" + ", spell.components)), false);
                        player.sendMessage(Text.literal("Behavior: " + spell.behavior), false);
                        player.sendMessage(Text.literal("Arcana cost: " + spell.arcanaCost()), false);
                        if (spell.legendary) player.sendMessage(Text.literal("LEGENDARY EFFECT: " + spell.legendaryEffect), false);
                        player.sendMessage(Text.literal("This spell is stored in your spellbook. Use /arcana cast " + spell.name + " to cast it."), false);
                        return 1;
                    })))
                .then(CommandManager.literal("forget")
                    .then(CommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        String name = normalizeName(StringArgumentType.getString(context, "name"));
                        boolean removed = SpellBookStore.remove(player.getUuid(), name);
                        if (removed) SpellBookStore.save(player.getServer());
                        player.sendMessage(Text.literal(removed
                            ? "Removed '" + name + "' from your spellbook."
                            : "No spell named '" + name + "' exists in your spellbook."), false);
                        return removed ? 1 : 0;
                    })))
                .then(CommandManager.literal("cast")
                    .then(CommandManager.argument("name", StringArgumentType.greedyString()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        String name = normalizeName(StringArgumentType.getString(context, "name"));
                        SpellBookStore.StoredSpell spell = SpellBookStore.find(player.getUuid(), name);
                        if (spell == null) {
                            player.sendMessage(Text.literal("No spell named '" + name + "' exists in your spellbook."), false);
                            return 0;
                        }
                        SpellCaster.CastResult result = SpellCaster.cast(player, spell);
                        player.sendMessage(Text.literal(result.message()), false);
                        return result.success() ? 1 : 0;
                    })))
                .then(CommandManager.literal("ai")
                    .then(CommandManager.literal("status").executes(context -> {
                        context.getSource().sendFeedback(() -> Text.literal("[Arcana AI] " + ArcanaAiService.status()), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("ask")
                        .then(CommandManager.argument("question", StringArgumentType.greedyString()).executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            String question = StringArgumentType.getString(context, "question");
                            player.sendMessage(Text.literal("[Arcana AI] Thinking..."), false);
                            ArcanaAiService.ask(player, question).whenComplete((answer, error) -> {
                                var server = player.getServer();
                                if (server == null) return;
                                server.execute(() -> {
                                    String result = error == null ? answer
                                        : "Request failed — " + ArcanaAiService.failureMessage(error);
                                    sendAiResponse(player, result);
                                });
                            });
                            return 1;
                        })))
                    .then(CommandManager.literal("clear-memory").executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        ArcanaAiService.clearMemory(player.getUuid(), player.getServer());
                        player.sendMessage(Text.literal("[Arcana AI] Your saved memory for this world has been cleared."), false);
                        return 1;
                    })))
                .then(CommandManager.literal("status").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(Text.literal("Arcana: " + ArcanaResource.current(player) + "/" +
                        ArcanaResource.MAX_ARCANA + " (regenerates 2 Arcana per second)"), false);
                    return 1;
                }))
            )
        );
    }

    /**
     * Sends long AI replies in ordered chat-sized pieces so the client does not
     * display one enormous message as a single clipped block.
     */
    private static void sendAiResponse(ServerPlayerEntity player, String response) {
        if (response == null || response.isBlank()) {
            player.sendMessage(Text.literal("[Arcana AI] The model returned an empty response."), false);
            return;
        }
        String remaining = response.trim();
        boolean first = true;
        while (!remaining.isEmpty()) {
            int end = Math.min(240, remaining.length());
            if (end < remaining.length()) {
                int boundary = Math.max(remaining.lastIndexOf(' ', end), remaining.lastIndexOf('\n', end));
                if (boundary > 100) end = boundary;
            }
            String part = remaining.substring(0, end).trim();
            if (!part.isEmpty()) {
                player.sendMessage(Text.literal((first ? "[Arcana AI] " : "    ") + part), false);
                first = false;
            }
            remaining = remaining.substring(end).trim();
        }
    }

    private static SpellDraft draft(ServerPlayerEntity player) {
        return DRAFTS.computeIfAbsent(player.getUuid(), ignored -> new SpellDraft());
    }

    private static boolean consumeMagicPaper(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            net.minecraft.item.ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(ArcanaItems.MAGIC_PAPER)) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
    }

    private static boolean hasSpellPage(ServerPlayerEntity player, String name) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            net.minecraft.item.ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(ArcanaItems.SPELL_PAGE) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static void consumeSpellPage(ServerPlayerEntity player, String name) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            net.minecraft.item.ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(ArcanaItems.SPELL_PAGE) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equalsIgnoreCase(name)) {
                stack.decrement(1);
                return;
            }
        }
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
