package dev.arcana.client;

import dev.arcana.block.entity.MagicWriterBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;

public final class MagicWriterBlockEntityRenderer implements BlockEntityRenderer<MagicWriterBlockEntity> {
    private final ItemRenderer itemRenderer;

    public MagicWriterBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MagicWriterBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (entity.getWorld() == null) return;

        ItemStack book = entity.getStack(0);
        ItemStack page = entity.getStack(1);

        // The lower block model's writing surface is at 9/16 of a block.
        // Ground-mode item transforms lie flat on that surface instead of hovering above it.
        if (!book.isEmpty()) {
            matrices.push();
            matrices.translate(0.5, 0.565, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            matrices.scale(0.78F, 0.78F, 0.78F);
            itemRenderer.renderItem(book, ModelTransformationMode.GROUND, light, overlay,
                matrices, vertexConsumers, entity.getWorld(), 0);
            matrices.pop();
        }

        if (!page.isEmpty()) {
            matrices.push();
            matrices.translate(0.5, book.isEmpty() ? 0.565 : 0.582, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            matrices.scale(book.isEmpty() ? 0.78F : 0.52F, book.isEmpty() ? 0.78F : 0.52F,
                book.isEmpty() ? 0.78F : 0.52F);
            itemRenderer.renderItem(page, ModelTransformationMode.GROUND, light, overlay,
                matrices, vertexConsumers, entity.getWorld(), 1);
            matrices.pop();
        }

        // The stylus is kept close to the writing surface; the upper model's
        // supported arm reaches over it.
        float time = entity.getWorld().getTime() + tickDelta;
        float sweep = (float) Math.sin(time * 0.16F) * 0.16F;
        float dip = (float) Math.abs(Math.sin(time * 0.16F)) * 0.055F;
        float swing = (float) Math.sin(time * 0.16F) * 22.0F;
        matrices.push();
        matrices.translate(0.5 + sweep, 1.12 - dip, 0.70);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swing));
        matrices.scale(0.18F, 0.18F, 0.18F);
        itemRenderer.renderItem(new ItemStack(Items.END_ROD), ModelTransformationMode.GROUND,
            light, overlay, matrices, vertexConsumers, entity.getWorld(), 2);
        matrices.pop();
    }
}
