package dev.arcana.block;

import com.mojang.serialization.MapCodec;
import dev.arcana.block.entity.MagicWriterBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

public final class MagicWriterBlock extends BlockWithEntity {
    public static final MapCodec<MagicWriterBlock> CODEC = createCodec(MagicWriterBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF =
        EnumProperty.of("half", DoubleBlockHalf.class);

    public MagicWriterBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override public MapCodec<MagicWriterBlock> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        World world = context.getWorld();
        if (!world.getBlockState(pos.up()).canReplace(context)) return null;
        return getDefaultState().with(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
                                                 BlockState neighborState, WorldAccess world,
                                                 BlockPos pos, BlockPos neighborPos) {
        if (direction == (state.get(HALF) == DoubleBlockHalf.LOWER
                ? Direction.UP : Direction.DOWN)
            && !neighborState.isOf(this)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER
            ? new MagicWriterBlockEntity(pos, state) : null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && state.get(HALF) == DoubleBlockHalf.LOWER) {
            world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos lowerPos = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
        BlockEntity entity = world.getBlockEntity(lowerPos);
        if (entity instanceof MagicWriterBlockEntity writer) player.openHandledScreen(writer);
        return ActionResult.CONSUME;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos,
                                   BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockPos otherPos = state.get(HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
            if (world.getBlockState(otherPos).isOf(this)) {
                world.setBlockState(otherPos, Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
            if (state.get(HALF) == DoubleBlockHalf.LOWER) {
                BlockEntity entity = world.getBlockEntity(pos);
                if (entity instanceof MagicWriterBlockEntity writer) {
                    ItemScatterer.spawn(world, pos, writer);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
}
