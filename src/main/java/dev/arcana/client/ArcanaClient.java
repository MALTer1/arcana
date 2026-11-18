package dev.arcana.client;

import dev.arcana.item.GrimoireTier;
import dev.arcana.block.ArcanaBlocks;
import dev.arcana.item.ArcanaItems;
import dev.arcana.spell.SpellPageStore;
import dev.arcana.network.GrimoireNetwork;
import dev.arcana.spell.GrimoireStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

public final class ArcanaClient implements ClientModInitializer {
    private static KeyBinding previousPage, nextPage, previousTab, nextTab, usePage, removeMode;
    private static boolean wasHoldingGrimoire;

    @Override
    public void onInitializeClient() {
        previousPage = key("key.arcana.previous_page", GLFW.GLFW_KEY_LEFT);
        nextPage = key("key.arcana.next_page", GLFW.GLFW_KEY_RIGHT);
        previousTab = key("key.arcana.previous_tab", GLFW.GLFW_KEY_UP);
        nextTab = key("key.arcana.next_tab", GLFW.GLFW_KEY_DOWN);
        usePage = key("key.arcana.use_page", GLFW.GLFW_KEY_ENTER);
        removeMode = key("key.arcana.remove_mode", GLFW.GLFW_KEY_X);

        HandledScreens.register(ArcanaBlocks.MAGIC_WRITER_SCREEN_HANDLER, MagicWriterScreen::new);
        BlockEntityRendererFactories.register(ArcanaBlocks.MAGIC_WRITER_ENTITY, MagicWriterBlockEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(GrimoireNetwork.StatePayload.ID,
            (payload, context) -> context.client().execute(() -> GrimoireHud.receiveState(payload.json())));

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isOf(ArcanaItems.SPELL_PAGE)) {
                var page = SpellPageStore.get(stack);
                if (page != null) {
                    lines.add(Text.literal("§6Editable Spell Page"));
                    lines.add(Text.literal("§7Components: " + String.join(", ", page.components)));
                    lines.add(Text.literal("§8Insert into a Magic Writer to edit."));
                }
                return;
            }
            GrimoireTier tier = GrimoireTier.fromItem(stack.getItem());
            if (tier == null) return;
            var spells = GrimoireStore.get(stack, tier);
            lines.add(Text.literal("§6" + spells.size() + " / " + tier.spellSlots() + " engraved pages"));
            for (var spell : spells) lines.add(Text.literal("§7✦ " + spell.name));
            if (spells.isEmpty()) lines.add(Text.literal("§8An untouched grimoire"));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean holding = client.player != null && activeTier(client) != null;
            if (holding && !wasHoldingGrimoire) GrimoireHud.refresh();
            if (holding && client.player.age % 10 == 0) GrimoireHud.refresh();
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
            if (client.player != null && activeTier(client) != null) GrimoireHud.render(drawContext, client);
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient || !player.isSneaking() || activeTier(MinecraftClient.getInstance()) == null)
                return TypedActionResult.pass(player.getStackInHand(hand));
            GrimoireHud.castSelected();
            return TypedActionResult.success(player.getStackInHand(hand), true);
        });
    }

    private static GrimoireTier activeTier(MinecraftClient client) {
        if (client.player == null) return null;
        GrimoireTier offhand = GrimoireTier.fromItem(client.player.getOffHandStack().getItem());
        return offhand != null ? offhand : GrimoireTier.fromItem(client.player.getMainHandStack().getItem());
    }

    private static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, code, "category.arcana.grimoire"));
    }
}
