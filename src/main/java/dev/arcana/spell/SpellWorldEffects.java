package dev.arcana.spell;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * World- and inventory-facing spell actions.
 *
 * This class intentionally keeps block mutation bounded to a small radius
 * around the spell impact and uses the normal world break/drop pipeline.
 * It is server-only: clients never decide which blocks or items are changed.
 */
public final class SpellWorldEffects {
    private static final int BLOCK_RADIUS = 4;

    private SpellWorldEffects() {}

    public static int apply(ServerPlayerEntity caster, java.util.List<String> components) {
        ServerWorld world = caster.getServerWorld();
        BlockPos center = raycastBlock(caster, 12.0);
        int changed = 0;

        if (components.contains("growth")) {
            changed += growCrops(world, center);
        }
        if (components.contains("harvest")) {
            changed += harvestCrops(world, center, caster);
        }
        if (components.contains("repair")) {
            changed += repairHeldItem(caster);
        }
        if (components.contains("magnetism")) {
            changed += attractItems(world, caster);
        }
        if (components.contains("repulsion")) {
            changed += repelItems(world, caster);
        }
        return changed;
    }

    private static BlockPos raycastBlock(ServerPlayerEntity caster, double range) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).normalize().multiply(range));
        var hit = caster.getWorld().raycast(new net.minecraft.world.RaycastContext(
            start, end, net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
            net.minecraft.world.RaycastContext.FluidHandling.NONE, caster));
        if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            return BlockPos.ofFloored(hit.getPos());
        }
        return BlockPos.ofFloored(end);
    }

    private static int growCrops(ServerWorld world, BlockPos center) {
        int changed = 0;
        for (BlockPos pos : nearby(center)) {
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock)) continue;
            IntProperty age = findAgeProperty(state);
            if (age == null) continue;
            int current = state.get(age);
            int maximum = age.getValues().stream().mapToInt(Integer::intValue).max().orElse(current);
            if (current >= maximum) continue;
            world.setBlockState(pos, state.with(age, Math.min(maximum, current + 1)), Block.NOTIFY_ALL);
            changed++;
        }
        return changed;
    }

    private static int harvestCrops(ServerWorld world, BlockPos center, ServerPlayerEntity caster) {
        int harvested = 0;
        for (BlockPos pos : nearby(center)) {
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock)) continue;
            IntProperty age = findAgeProperty(state);
            if (age == null) continue;
            int current = state.get(age);
            int maximum = age.getValues().stream().mapToInt(Integer::intValue).max().orElse(current);
            if (current < maximum) continue;
            if (world.breakBlock(pos, true, caster)) harvested++;
        }
        return harvested;
    }

    private static IntProperty findAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntProperty intProperty && property.getName().equals("age")) {
                return intProperty;
            }
        }
        return null;
    }

    private static Iterable<BlockPos> nearby(BlockPos center) {
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int x = -BLOCK_RADIUS; x <= BLOCK_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -BLOCK_RADIUS; z <= BLOCK_RADIUS; z++) {
                    positions.add(center.add(x, y, z));
                }
            }
        }
        return positions;
    }

    private static int repairHeldItem(ServerPlayerEntity caster) {
        ItemStack stack = caster.getMainHandStack();
        if (stack.isEmpty() || !stack.isDamageable() || stack.getDamage() <= 0) return 0;
        int oldDamage = stack.getDamage();
        stack.setDamage(Math.max(0, oldDamage - Math.max(1, stack.getMaxDamage() / 4)));
        return stack.getDamage() < oldDamage ? 1 : 0;
    }

    private static int attractItems(ServerWorld world, ServerPlayerEntity caster) {
        Box area = caster.getBoundingBox().expand(8.0);
        var items = world.getEntitiesByClass(ItemEntity.class, area, entity -> entity.isAlive());
        int moved = 0;
        for (ItemEntity item : items) {
            Vec3d direction = caster.getPos().add(0.0, 0.8, 0.0).subtract(item.getPos());
            double distance = direction.length();
            if (distance < 0.25) continue;
            Vec3d velocity = direction.normalize().multiply(Math.min(0.35, 1.4 / distance));
            item.addVelocity(velocity);
            item.velocityModified = true;
            moved++;
        }
        return moved;
    }

    private static int repelItems(ServerWorld world, ServerPlayerEntity caster) {
        Box area = caster.getBoundingBox().expand(5.0);
        var items = world.getEntitiesByClass(ItemEntity.class, area, entity -> entity.isAlive());
        int moved = 0;
        for (ItemEntity item : items) {
            Vec3d direction = item.getPos().subtract(caster.getPos());
            if (direction.lengthSquared() < 0.01) continue;
            Vec3d velocity = direction.normalize().multiply(0.45).add(0.0, 0.12, 0.0);
            item.addVelocity(velocity);
            item.velocityModified = true;
            moved++;
        }
        return moved;
    }
}
