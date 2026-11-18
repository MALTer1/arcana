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

        if (!book.isEmpty()) {
            matrices.push();
            matrices.translate(0.5, 1.015, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            matrices.scale(0.58F, 0.58F, 0.58F);
            itemRenderer.renderItem(book, ModelTransformationMode.GROUND, light, overlay,
                matrices, vertexConsumers, entity.getWorld(), 0);
            matrices.pop();
        }

        if (!page.isEmpty()) {
            matrices.push();
            matrices.translate(0.5, book.isEmpty() ? 1.02 : 1.075, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            float scale = book.isEmpty() ? 0.62F : 0.42F;
            matrices.scale(scale, scale, scale);
            itemRenderer.renderItem(page, ModelTransformationMode.GROUND, light, overlay,
                matrices, vertexConsumers, entity.getWorld(), 1);
            matrices.pop();
        }

        // A glowing stylus rocks over the writing surface like a small engraving arm.
        float time = entity.getWorld().getTime() + tickDelta;
        float swing = (float) Math.sin(time * 0.12F) * 13.0F;
        matrices.push();
        matrices.translate(0.5, 1.48, 0.34);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swing));
        matrices.translate(0.0, -0.12, 0.0);
        matrices.scale(0.22F, 0.22F, 0.22F);
        itemRenderer.renderItem(new ItemStack(Items.END_ROD), ModelTransformationMode.GROUND,
            light, overlay, matrices, vertexConsumers, entity.getWorld(), 2);
        matrices.pop();
    }
}
