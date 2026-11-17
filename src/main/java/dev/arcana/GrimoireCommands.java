package dev.arcana;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.arcana.item.ArcanaItems;
import dev.arcana.item.GrimoireTier;
import dev.arcana.spell.GrimoireStore;
import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellCaster;
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
                        GrimoireTier tier = GrimoireTier.fromItem(player.getMainHandStack().getItem());
                        if (tier == null) { player.sendMessage(Text.literal("Hold a grimoire first."), false); return 0; }
                        var spells = GrimoireStore.get(player.getUuid(), tier);
                        player.sendMessage(Text.literal(tier.displayName() + " — " + spells.size() + "/" + tier.spellSlots() + " pages"), false);
                        spells.forEach(spell -> player.sendMessage(Text.literal("• " + spell.name + " (" + spell.components.size() + " components)"), false));
                        return spells.size();
                    }))
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("insert")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.server.command.ServerCommandSource, String>argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            GrimoireTier tier = GrimoireTier.fromItem(player.getMainHandStack().getItem());
                            String name = ctx.getArgument("name", String.class).trim().replaceAll("\\s+", " ");
                            if (tier == null) { player.sendMessage(Text.literal("Hold a grimoire first."), false); return 0; }
                            SpellBookStore.StoredSpell spell = SpellBookStore.find(player.getUuid(), name);
                            if (spell == null) { player.sendMessage(Text.literal("No engraved spell named '" + name + "' exists."), false); return 0; }
                            if (spell.components.size() > tier.maxComponents()) { player.sendMessage(Text.literal("This grimoire supports up to " + tier.maxComponents() + " components."), false); return 0; }
                            if (!hasPage(player, name)) { player.sendMessage(Text.literal("You need that named Spell Page in your inventory."), false); return 0; }
                            if (!GrimoireStore.insert(player.getUuid(), tier, spell)) { player.sendMessage(Text.literal("Grimoire full or spell already inserted."), false); return 0; }
                            consumePage(player, name);
                            GrimoireStore.save(player.getServer());
                            player.sendMessage(Text.literal("Inserted '" + name + "' into " + tier.displayName() + "."), false);
                            return 1;
                        })))
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("remove")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.server.command.ServerCommandSource, String>argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            GrimoireTier tier = GrimoireTier.fromItem(player.getMainHandStack().getItem());
                            String name = ctx.getArgument("name", String.class).trim().replaceAll("\\s+", " ");
                            if (tier == null) { player.sendMessage(Text.literal("Hold a grimoire first."), false); return 0; }
                            SpellBookStore.StoredSpell spell = GrimoireStore.remove(player.getUuid(), tier, name);
                            if (spell == null) { player.sendMessage(Text.literal("That grimoire does not contain '" + name + "'."), false); return 0; }
                            ItemStack page = new ItemStack(ArcanaItems.SPELL_PAGE);
                            page.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(spell.name));
                            if (!player.getInventory().insertStack(page)) player.dropItem(page, false);
                            GrimoireStore.save(player.getServer());
                            player.sendMessage(Text.literal("Removed '" + name + "' and returned its Spell Page."), false);
                            return 1;
                        })))
                    .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<net.minecraft.server.command.ServerCommandSource>literal("cast")
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.server.command.ServerCommandSource, String>argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            GrimoireTier tier = GrimoireTier.fromItem(player.getMainHandStack().getItem());
                            String name = ctx.getArgument("name", String.class).trim().replaceAll("\\s+", " ");
                            if (tier == null) { player.sendMessage(Text.literal("Hold a grimoire first."), false); return 0; }
                            SpellBookStore.StoredSpell spell = GrimoireStore.find(player.getUuid(), tier, name);
                            if (spell == null) { player.sendMessage(Text.literal("That spell is not in this grimoire."), false); return 0; }
                            SpellCaster.CastResult result = SpellCaster.cast(player, spell);
                            player.sendMessage(Text.literal(result.message()), false);
                            return result.success() ? 1 : 0;
                        })))
                ))
        );
    }

    private static boolean hasPage(ServerPlayerEntity player, String name) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ArcanaItems.SPELL_PAGE) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static void consumePage(ServerPlayerEntity player, String name) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ArcanaItems.SPELL_PAGE) && stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME) && stack.getName().getString().equalsIgnoreCase(name)) {
                stack.decrement(1);
                return;
            }
        }
    }
}
