package dev.arcana.client;

import dev.arcana.block.ArcanaBlocks;
import dev.arcana.item.GrimoireTier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class ArcanaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient || GrimoireTier.fromItem(player.getStackInHand(hand).getItem()) == null) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            MinecraftClient.getInstance().setScreen(new GrimoireScreen());
            return TypedActionResult.success(player.getStackInHand(hand));
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient || !world.getBlockState(hit.getBlockPos()).isOf(ArcanaBlocks.MAGIC_WRITER)) {
                return ActionResult.PASS;
            }
            MinecraftClient.getInstance().setScreen(new MagicWriterScreen());
            return ActionResult.SUCCESS;
        });
    }
}
