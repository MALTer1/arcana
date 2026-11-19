package dev.arcana.entity;

import dev.arcana.spell.SpellBookStore;
import dev.arcana.spell.SpellCaster;
import dev.arcana.spell.SpellCompositionEngine;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.List;

public final class ArcanaProjectileEntity extends Entity {
    private static final TrackedData<Integer> OWNER_ID =
        DataTracker.registerData(ArcanaProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> COMPONENTS =
        DataTracker.registerData(ArcanaProjectileEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> ARC_TRAJECTORY =
        DataTracker.registerData(ArcanaProjectileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> LEGENDARY =
        DataTracker.registerData(ArcanaProjectileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final java.util.Set<Integer> hitEntityIds = new java.util.HashSet<>();
    private int lifetime;

    public ArcanaProjectileEntity(EntityType<? extends ArcanaProjectileEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(OWNER_ID, -1);
        builder.add(COMPONENTS, "");
        builder.add(ARC_TRAJECTORY, false);
        builder.add(LEGENDARY, false);
    }

    public static boolean launch(ServerPlayerEntity owner, SpellBookStore.StoredSpell spell,
                                 SpellCompositionEngine.Plan composition) {
        ArcanaProjectileEntity projectile = new ArcanaProjectileEntity(ArcanaEntities.MAGIC_PROJECTILE, owner.getWorld());
        Vec3d look = owner.getRotationVec(1.0F).normalize();
        boolean arc = spell.components.contains("arc");
        Vec3d direction = arc ? look.add(0.0, 0.16, 0.0).normalize() : look;
        double speed = 1.55 * composition.travelSpeedMultiplier() * (spell.legendary ? 1.35 : 1.0);
        projectile.refreshPositionAndAngles(
            owner.getX() + look.x * 0.65,
            owner.getEyeY() - 0.15 + look.y * 0.65,
            owner.getZ() + look.z * 0.65,
            owner.getYaw(), owner.getPitch());
        projectile.setVelocity(direction.multiply(speed));
        projectile.dataTracker.set(OWNER_ID, owner.getId());
        projectile.dataTracker.set(COMPONENTS, String.join(",", spell.components));
        projectile.dataTracker.set(ARC_TRAJECTORY, arc);
        projectile.dataTracker.set(LEGENDARY, spell.legendary);
        return owner.getWorld().spawnEntity(projectile);
    }

    @Override
    public void tick() {
        super.tick();
        String encodedComponents = dataTracker.get(COMPONENTS);
        int maxLifetime = encodedComponents.contains("extend") || encodedComponents.contains("homing")
            || encodedComponents.contains("ricochet") ? 160 : 100;
        if (dataTracker.get(LEGENDARY)) maxLifetime = Math.min(240, maxLifetime * 2);
        if (++lifetime > maxLifetime) {
            discard();
            return;
        }

        Vec3d velocity = getVelocity();
        if (getWorld().isClient) {
            setPosition(getPos().add(velocity));
            setVelocity(dataTracker.get(ARC_TRAJECTORY)
                ? velocity.add(0.0, -0.012, 0.0).multiply(0.995)
                : velocity.multiply(0.995));
            return;
        }

        List<String> activeIds = Arrays.stream(dataTracker.get(COMPONENTS).split(","))
            .filter(id -> !id.isBlank()).toList();
        if (activeIds.contains("homing") || (activeIds.contains("ricochet") && !hitEntityIds.isEmpty())) {
            LivingEntity guidedTarget = getWorld().getEntitiesByClass(LivingEntity.class,
                getBoundingBox().expand(16.0), candidate -> candidate.isAlive()
                    && candidate.getId() != dataTracker.get(OWNER_ID)
                    && !hitEntityIds.contains(candidate.getId()))
                .stream().min(java.util.Comparator.comparingDouble(candidate ->
                    candidate.squaredDistanceTo(this))).orElse(null);
            if (guidedTarget != null) {
                Vec3d desired = guidedTarget.getBoundingBox().getCenter().subtract(getPos()).normalize();
                double speedNow = Math.max(0.2, velocity.length());
                velocity = velocity.normalize().multiply(0.78).add(desired.multiply(0.22)).normalize().multiply(speedNow);
                setVelocity(velocity);
            }
        }

        Vec3d start = getPos();
        Vec3d end = start.add(velocity);
        BlockHitResult blockHit = getWorld().raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE, this));

        Entity entityHit = getWorld().getOtherEntities(this,
                getBoundingBox().stretch(velocity).expand(0.25),
                candidate -> candidate instanceof LivingEntity && candidate.isAlive()
                    && candidate.getId() != dataTracker.get(OWNER_ID)
                    && !hitEntityIds.contains(candidate.getId()))
            .stream()
            .min(java.util.Comparator.comparingDouble(candidate ->
                candidate.getBoundingBox().getCenter().squaredDistanceTo(start)))
            .orElse(null);

        double blockDistance = blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
            ? start.squaredDistanceTo(blockHit.getPos()) : Double.MAX_VALUE;
        double entityDistance = entityHit == null ? Double.MAX_VALUE
            : start.squaredDistanceTo(entityHit.getBoundingBox().getCenter());

        if (entityHit != null && entityDistance <= blockDistance) {
            setPosition(entityHit.getBoundingBox().getCenter());
            impact(entityHit);
            return;
        }
        if (blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            setPosition(blockHit.getPos());
            impact(null);
            return;
        }

        setPosition(end);
        setVelocity(dataTracker.get(ARC_TRAJECTORY)
            ? velocity.add(0.0, -0.012, 0.0).multiply(0.995)
            : velocity.multiply(0.995));
    }

    private void impact(Entity hit) {
        if (getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(),
                10, 0.12, 0.12, 0.12, 0.025);
            serverWorld.playSound(null, getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL,
                SoundCategory.PLAYERS, 0.55F, 1.35F);
            Entity owner = serverWorld.getEntityById(dataTracker.get(OWNER_ID));
            if (owner instanceof ServerPlayerEntity player && hit instanceof LivingEntity living) {
                List<String> ids = Arrays.stream(dataTracker.get(COMPONENTS).split(","))
                    .filter(id -> !id.isBlank()).toList();
                SpellCaster.impactProjectile(player,
                    new SpellBookStore.StoredSpell("Arcane Projectile", ids, "",
                        dataTracker.get(LEGENDARY), dataTracker.get(LEGENDARY)
                            ? dev.arcana.spell.LegendarySpellEngine.describe(ids) : ""), living);
                hitEntityIds.add(living.getId());
                if (ids.contains("pierce") && hitEntityIds.size() <= 3) {
                    setPosition(getPos().add(getVelocity().normalize().multiply(0.35)));
                    return;
                }
            }
        }
        discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        lifetime = nbt.getInt("Lifetime");
        dataTracker.set(OWNER_ID, nbt.getInt("OwnerId"));
        dataTracker.set(COMPONENTS, nbt.getString("Components"));
        dataTracker.set(ARC_TRAJECTORY, nbt.getBoolean("ArcTrajectory"));
        dataTracker.set(LEGENDARY, nbt.getBoolean("Legendary"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Lifetime", lifetime);
        nbt.putInt("OwnerId", dataTracker.get(OWNER_ID));
        nbt.putString("Components", dataTracker.get(COMPONENTS));
        nbt.putBoolean("ArcTrajectory", dataTracker.get(ARC_TRAJECTORY));
        nbt.putBoolean("Legendary", dataTracker.get(LEGENDARY));
    }
}
