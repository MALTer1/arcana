package dev.arcana.client;

import dev.arcana.block.ArcanaBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;

public final class ArcanaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient || !world.getBlockState(hit.getBlockPos()).isOf(ArcanaBlocks.MAGIC_WRITER)) {
                return ActionResult.PASS;
            }
            MinecraftClient.getInstance().setScreen(new MagicWriterScreen());
            return ActionResult.SUCCESS;
        });
    }
}
