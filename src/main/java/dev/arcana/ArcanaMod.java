package dev.arcana;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.arcana.spell.ComponentRegistry;
import dev.arcana.spell.SpellDraft;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("arcana")
                .then(CommandManager.literal("components").executes(context -> {
                    ServerCommandSource source = context.getSource();
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
                            SpellDraft.AddResult result = DRAFTS.computeIfAbsent(player.getUuid(), ignored -> new SpellDraft()).add(id);
                            player.sendMessage(Text.literal(result.message()), false);
                            return result.success() ? 1 : 0;
                        })))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("component", StringArgumentType.word()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        String id = StringArgumentType.getString(context, "component");
                        boolean removed = DRAFTS.computeIfAbsent(player.getUuid(), ignored -> new SpellDraft()).remove(id);
                        player.sendMessage(Text.literal(removed ? "Removed " + id + "." : "That component was not in your draft."), false);
                        return removed ? 1 : 0;
                    })))
                .then(CommandManager.literal("clear").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    DRAFTS.computeIfAbsent(player.getUuid(), ignored -> new SpellDraft()).clear();
                    player.sendMessage(Text.literal("Spell draft cleared."), false);
                    return 1;
                }))
                .then(CommandManager.literal("preview").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    DRAFTS.computeIfAbsent(player.getUuid(), ignored -> new SpellDraft()).preview()
                        .forEach(line -> player.sendMessage(Text.literal(line), false));
                    return 1;
                }))
                .then(CommandManager.literal("status").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    int current = ARCANA.computeIfAbsent(player.getUuid(), ignored -> 100);
                    player.sendMessage(Text.literal("Arcana: " + current + "/100 (prototype status only; casting/regeneration not connected)"), false);
                    return 1;
                }))
            )
        );
    }
}
