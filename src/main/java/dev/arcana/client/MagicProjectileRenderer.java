package dev.arcana.client;

import dev.arcana.entity.ArcanaProjectileEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public final class MagicProjectileRenderer extends EntityRenderer<ArcanaProjectileEntity> {
    public MagicProjectileRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(ArcanaProjectileEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.scale(0.55F, 0.55F, 0.55F);
        MinecraftClient.getInstance().getItemRenderer().renderItem(
            new ItemStack(Items.AMETHYST_SHARD), ModelTransformationMode.GROUND,
            light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers,
            entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(ArcanaProjectileEntity entity) {
        return Identifier.ofVanilla("textures/item/amethyst_shard.png");
    }
}
