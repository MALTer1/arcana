package dev.arcana;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.spell.GrimoireStore;
import dev.arcana.spell.ComponentKnowledgeStore;
import dev.arcana.spell.ComponentRegistry;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellCaster;
import dev.arcana.spell.SpellPageStore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class GrimoireCommands {
    private GrimoireCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("arcana")
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("grimoire")
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("list").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ItemStack book = activeBook(player);
                        GrimoireTier tier = book.isEmpty() ? null : GrimoireTier.fromItem(book.getItem());
                        if (tier == null) { player.sendMessage(Text.literal("Hold a Grimoire first."), false); return 0; }
                        var spells = GrimoireStore.get(book, tier);
                        player.sendMessage(Text.literal(tier.displayName() + " — " + spells.size() + "/" + tier.spellSlots() + " pages"), false);
                        spells.forEach(spell -> player.sendMessage(Text.literal("• " + spell.name + " (" + spell.components.size() + " components)"), false));
                        return spells.size();
                    }))
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("insert")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.server.command.ServerCommandSource, String>argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            ctx.getSource().sendError(Text.literal("Use the Magic Writer to engrave spells directly onto a Grimoire."));
                            return 0;
                        })))
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("remove")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.server.command.ServerCommandSource, String>argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            ItemStack book = activeBook(player);
                            GrimoireTier tier = book.isEmpty() ? null : GrimoireTier.fromItem(book.getItem());
                            String name = ctx.getArgument("name", String.class).trim();
                            if (tier == null) { player.sendMessage(Text.literal("Hold a Grimoire first."), false); return 0; }
                            SpellBookStore.StoredSpell spell = GrimoireStore.remove(book, tier, name);
                            if (spell == null) { player.sendMessage(Text.literal("That Grimoire does not contain '" + name + "'."), false); return 0; }
                            ItemStack page = new ItemStack(ArcanaItems.SPELL_PAGE);
                            SpellPageStore.set(page, spell);
                            if (!player.getInventory().insertStack(page)) player.dropItem(page, false);
                            player.sendMessage(Text.literal("Removed '" + name + "' and returned its Spell Page."), false);
                            return 1;
                        })))
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("cast")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.server.command.ServerCommandSource, String>argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            ItemStack book = activeBook(player);
                            GrimoireTier tier = book.isEmpty() ? null : GrimoireTier.fromItem(book.getItem());
                            String name = ctx.getArgument("name", String.class).trim();
                            if (tier == null) { player.sendMessage(Text.literal("Hold a Grimoire first."), false); return 0; }
                            SpellBookStore.StoredSpell spell = GrimoireStore.find(book, tier, name);
                            if (spell == null) { player.sendMessage(Text.literal("That spell is not in this Grimoire."), false); return 0; }
                            SpellCaster.CastResult result = SpellCaster.cast(player, spell);
                            player.sendMessage(Text.literal(result.message()), false);
                            return result.success() ? 1 : 0;
                        })))
                )
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("learn")
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("all").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        int learned = ComponentKnowledgeStore.learnAll(player.getUuid());
                        ComponentKnowledgeStore.save(player.getServer());
                        player.sendMessage(Text.literal("Learned " + learned + " new components. You now know " + ComponentKnowledgeStore.get(player.getUuid()).size() + "/" + ComponentRegistry.all().size() + "."), false);
                        return learned;
                    }))
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.server.command.ServerCommandSource, String>argument("component", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            ComponentRegistry.all().forEach(component -> builder.suggest(component.id()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String id = ctx.getArgument("component", String.class);
                            if (ComponentRegistry.get(id) == null) {
                                player.sendMessage(Text.literal("Unknown component: " + id), false);
                                return 0;
                            }
                            boolean learned = ComponentKnowledgeStore.learn(player.getUuid(), id);
                            ComponentKnowledgeStore.save(player.getServer());
                            player.sendMessage(Text.literal(learned ? "You learned " + id + "." : "You already know " + id + "."), false);
                            return learned ? 1 : 0;
                        })))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("learned").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    var known = ComponentKnowledgeStore.get(player.getUuid());
                    player.sendMessage(Text.literal("Known components (" + known.size() + "/" + ComponentRegistry.all().size() + "): " + String.join(", ", known)), false);
                    return known.size();
                }))
                ));
    }

    private static ItemStack activeBook(ServerPlayerEntity player) {
        if (GrimoireTier.fromItem(player.getOffHandStack().getItem()) != null) return player.getOffHandStack();
        return GrimoireTier.fromItem(player.getMainHandStack().getItem()) != null ? player.getMainHandStack() : ItemStack.EMPTY;
    }
}
