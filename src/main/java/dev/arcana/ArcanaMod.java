package dev.arcana;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.arcana.spell.ComponentRegistry;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellDraft;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArcanaMod implements ModInitializer {
    private static final Map<UUID, SpellDraft> DRAFTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ARCANA = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ComponentRegistry.bootstrap();

        ServerLifecycleEvents.SERVER_STARTED.register(SpellBookStore::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(SpellBookStore::save);

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
                            SpellDraft.AddResult result = draft(player).add(id);
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
                        if (!spellDraft.isValid()) {
                            player.sendMessage(Text.literal("This draft cannot be engraved. Use /arcana preview to see what needs fixing."), false);
                            return 0;
                        }

                        SpellBookStore.StoredSpell spell = new SpellBookStore.StoredSpell(
                            name, spellDraft.selectedIds(), spellDraft.behavior());
                        if (!SpellBookStore.add(player.getUuid(), spell)) {
                            player.sendMessage(Text.literal("You already have a spell named '" + name + "'. Choose another name."), false);
                            return 0;
                        }

                        spellDraft.clear();
                        SpellBookStore.save(player.getServer());
                        player.sendMessage(Text.literal("Engraved '" + name + "' and added it to your spellbook."), false);
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
                        Text.literal("• " + spell.name + " — " + String.join(", ", spell.components)), false));
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
                        player.sendMessage(Text.literal("Engraved spells are stored; casting is not implemented yet."), false);
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
                .then(CommandManager.literal("status").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    int current = ARCANA.computeIfAbsent(player.getUuid(), ignored -> 100);
                    player.sendMessage(Text.literal("Arcana: " + current + "/100 (casting and regeneration are not implemented yet)"), false);
                    return 1;
                }))
            )
        );
    }

    private static SpellDraft draft(ServerPlayerEntity player) {
        return DRAFTS.computeIfAbsent(player.getUuid(), ignored -> new SpellDraft());
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
