package dev.arcana.client;

import dev.arcana.block.ArcanaBlocks;
import dev.arcana.item.GrimoireTier;
import dev.arcana.network.GrimoireNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public final class ArcanaClient implements ClientModInitializer {
    private static KeyBinding previousPage, nextPage, previousTab, nextTab, usePage, removeMode;
    private static boolean wasHoldingGrimoire;

    @Override
    public void onInitializeClient() {
        // Restore the original, intuitive arrow-key controls. These do not replace
        // Better MC's WASD, attack, use, inventory, drop, or hotbar controls.
        previousPage = key("key.arcana.previous_page", GLFW.GLFW_KEY_LEFT);
        nextPage = key("key.arcana.next_page", GLFW.GLFW_KEY_RIGHT);
        previousTab = key("key.arcana.previous_tab", GLFW.GLFW_KEY_UP);
        nextTab = key("key.arcana.next_tab", GLFW.GLFW_KEY_DOWN);
        usePage = key("key.arcana.use_page", GLFW.GLFW_KEY_ENTER);
        removeMode = key("key.arcana.remove_mode", GLFW.GLFW_KEY_X);

        ClientPlayNetworking.registerGlobalReceiver(GrimoireNetwork.StatePayload.ID, (payload, context) ->
            context.client().execute(() -> GrimoireHud.receiveState(payload.json())));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean holding = client.player != null && activeTier(client) != null;
            if (holding && !wasHoldingGrimoire) GrimoireHud.refresh();
            wasHoldingGrimoire = holding;
            if (!holding || client.currentScreen != null) return;
            while (previousPage.wasPressed()) GrimoireHud.previousPage();
            while (nextPage.wasPressed()) GrimoireHud.nextPage();
            while (previousTab.wasPressed()) GrimoireHud.previousTab();
            while (nextTab.wasPressed()) GrimoireHud.nextTab();
            while (usePage.wasPressed()) GrimoireHud.usePage();
            while (removeMode.wasPressed()) GrimoireHud.toggleRemove();
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && activeTier(client) != null) {
                GrimoireHud.render(drawContext, client);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient || !world.getBlockState(hit.getBlockPos()).isOf(ArcanaBlocks.MAGIC_WRITER)) return ActionResult.PASS;
            MinecraftClient.getInstance().setScreen(new MagicWriterScreen());
            return ActionResult.SUCCESS;
        });
    }

    private static GrimoireTier activeTier(MinecraftClient client) {
        GrimoireTier offhand = GrimoireTier.fromItem(client.player.getOffHandStack().getItem());
        return offhand != null ? offhand : GrimoireTier.fromItem(client.player.getMainHandStack().getItem());
    }

    private static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, code, "category.arcana.grimoire"));
    }
}
