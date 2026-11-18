package dev.arcana.block;

import com.mojang.serialization.MapCodec;
import dev.arcana.block.entity.MagicWriterBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MagicWriterBlock extends BlockWithEntity {
    public static final MapCodec<MagicWriterBlock> CODEC = createCodec(MagicWriterBlock::new);

    public MagicWriterBlock(Settings settings) { super(settings); }

    @Override
    public MapCodec<MagicWriterBlock> getCodec() { return CODEC; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MagicWriterBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity instanceof MagicWriterBlockEntity writer) {
            player.openHandledScreen(writer);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
