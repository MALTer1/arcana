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
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persistent, server-authoritative delivery zone for fields, runes and barriers. */
public final class ArcanaFieldEntity extends Entity {
    private static final TrackedData<Integer> OWNER_ID =
        DataTracker.registerData(ArcanaFieldEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> COMPONENTS =
        DataTracker.registerData(ArcanaFieldEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> SHAPE =
        DataTracker.registerData(ArcanaFieldEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Float> RADIUS =
        DataTracker.registerData(ArcanaFieldEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FACING_YAW =
        DataTracker.registerData(ArcanaFieldEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> LEGENDARY =
        DataTracker.registerData(ArcanaFieldEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> MAX_LIFETIME =
        DataTracker.registerData(ArcanaFieldEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final Set<UUID> affectedTargets = new HashSet<>();
    private final Set<UUID> deflectedProjectiles = new HashSet<>();
    private int lifetime;

    public ArcanaFieldEntity(EntityType<? extends ArcanaFieldEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(OWNER_ID, -1);
        builder.add(COMPONENTS, "");
        builder.add(SHAPE, "field");
        builder.add(RADIUS, 3.5F);
        builder.add(FACING_YAW, 0.0F);
        builder.add(LEGENDARY, false);
        builder.add(MAX_LIFETIME, 200);
    }

    public static boolean launch(ServerPlayerEntity owner, SpellBookStore.StoredSpell spell,
                                 SpellCompositionEngine.Plan plan) {
        Vec3d look = owner.getRotationVec(1.0F).normalize();
        Vec3d start = owner.getEyePos();
        Vec3d end = start.add(look.multiply(12.0));
        var hit = owner.getWorld().raycast(new RaycastContext(start, end,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, owner));
        Vec3d center = hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
            ? hit.getPos() : end;
        String shape = chooseShape(spell.components);
        if (shape.equals("barrier") || shape.equals("orbit")) center = owner.getPos().add(0.0, 0.8, 0.0);
        return launchAt(owner, spell, plan, center, shape);
    }

    public static boolean launchAt(ServerPlayerEntity owner, SpellBookStore.StoredSpell spell, Vec3d center) {
        return launchAt(owner, spell, SpellCompositionEngine.compose(spell.components), center,
            chooseShape(spell.components));
    }

    private static boolean launchAt(ServerPlayerEntity owner, SpellBookStore.StoredSpell spell,
                                    SpellCompositionEngine.Plan plan, Vec3d center, String shape) {
        ArcanaFieldEntity field = new ArcanaFieldEntity(ArcanaEntities.MAGIC_FIELD, owner.getWorld());
        float baseRadius = shape.equals("wall") ? 5.0F : shape.equals("barrier") ? 3.2F : 3.8F;
        field.refreshPositionAndAngles(center.x, center.y, center.z, owner.getYaw(), 0.0F);
        field.dataTracker.set(OWNER_ID, owner.getId());
        field.dataTracker.set(COMPONENTS, String.join(",", spell.components));
        field.dataTracker.set(SHAPE, shape);
        double legendaryArea = spell.legendary ? 1.5 : 1.0;
        field.dataTracker.set(RADIUS, (float) Math.max(1.5,
            Math.min(8.0, baseRadius * plan.areaMultiplier() * legendaryArea)));
        field.dataTracker.set(FACING_YAW, owner.getYaw());
        field.dataTracker.set(LEGENDARY, spell.legendary);
        int lifetimeTicks = (int)Math.round(200.0 * plan.durationMultiplier() * (spell.legendary ? 2.0 : 1.0));
        field.dataTracker.set(MAX_LIFETIME, Math.max(200, Math.min(600, lifetimeTicks)));
        return owner.getWorld().spawnEntity(field);
    }

    private static String chooseShape(java.util.List<String> parts) {
        if (parts.contains("barrier")) return "barrier";
        if (parts.contains("wall")) return "wall";
        if (parts.contains("tether")) return "tether";
        if (parts.contains("orbit")) return "orbit";
        if (parts.contains("rune")) return "rune";
        return "field";
    }

    @Override
    public void tick() {
        super.tick();
        if (++lifetime > dataTracker.get(MAX_LIFETIME)) {
            discard();
            return;
        }
        if (getWorld().isClient || !(getWorld() instanceof ServerWorld world)) return;

        Entity ownerEntity = world.getEntityById(dataTracker.get(OWNER_ID));
        ServerPlayerEntity owner = ownerEntity instanceof ServerPlayerEntity player ? player : null;
        String shape = dataTracker.get(SHAPE);
        if (shape.equals("barrier") && owner != null) {
            setPosition(owner.getX(), owner.getY() + 0.8, owner.getZ());
        } else if (shape.equals("orbit") && owner != null) {
            double angle = lifetime * 0.12;
            setPosition(owner.getX() + Math.cos(angle) * 2.4,
                owner.getY() + 1.0, owner.getZ() + Math.sin(angle) * 2.4);
        }

        if (lifetime % 5 != 0) return;
        float radius = dataTracker.get(RADIUS);
        var ids = Arrays.stream(dataTracker.get(COMPONENTS).split(","))
            .filter(id -> !id.isBlank()).toList();
        spawnShapeVisuals(world, owner, shape, radius, ids);

        if (owner != null) {
            Box search = searchBox(owner, shape, radius);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, search,
                entity -> entity.isAlive() && !affectedTargets.contains(entity.getUuid()))) {
                if (!containsTarget(owner, target, shape, radius)) continue;
                SpellCaster.impactDelivery(owner,
                    new SpellBookStore.StoredSpell("Persistent Arcana", ids, "",
                        dataTracker.get(LEGENDARY), dataTracker.get(LEGENDARY)
                            ? dev.arcana.spell.LegendarySpellEngine.describe(ids) : ""), target);
                affectedTargets.add(target.getUuid());
            }
        }

        if (shape.equals("barrier")) deflectProjectiles(world, radius);
    }

    private Box searchBox(ServerPlayerEntity owner, String shape, float radius) {
        if (shape.equals("tether")) {
            Vec3d start = owner.getEyePos();
            Vec3d end = getPos();
            return new Box(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)).expand(1.5);
        }
        if (shape.equals("wall")) return getBoundingBox().expand(radius, 3.0, radius);
        return getBoundingBox().expand(radius + 1.0);
    }

    private boolean containsTarget(ServerPlayerEntity owner, LivingEntity target, String shape, float radius) {
        Vec3d point = target.getBoundingBox().getCenter();
        if (shape.equals("wall")) {
            Vec3d offset = point.subtract(getPos());
            double yaw = Math.toRadians(dataTracker.get(FACING_YAW));
            Vec3d normal = new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
            double depth = Math.abs(offset.dotProduct(normal));
            Vec3d lateral = offset.subtract(normal.multiply(offset.dotProduct(normal)));
            return depth <= 0.9 && lateral.horizontalLength() <= radius
                && Math.abs(offset.y) <= 2.7;
        }
        if (shape.equals("tether")) {
            Vec3d start = owner.getEyePos();
            Vec3d segment = getPos().subtract(start);
            double lengthSquared = segment.lengthSquared();
            if (lengthSquared < 0.01) return false;
            double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dotProduct(segment) / lengthSquared));
            return point.squaredDistanceTo(start.add(segment.multiply(t))) <= 1.5 * 1.5;
        }
        if (shape.equals("orbit") && owner != null) {
            double horizontal = Math.sqrt(Math.pow(point.x - owner.getX(), 2) + Math.pow(point.z - owner.getZ(), 2));
            return Math.abs(horizontal - 2.4) <= 1.4 && Math.abs(point.y - owner.getY()) <= 2.5;
        }
        return point.squaredDistanceTo(getPos()) <= radius * radius;
    }

    private void spawnShapeVisuals(ServerWorld world, ServerPlayerEntity owner, String shape,
                                    float radius, java.util.List<String> ids) {
        if (shape.equals("tether") && owner != null) {
            Vec3d start = owner.getEyePos();
            Vec3d delta = getPos().subtract(start);
            for (int i = 0; i <= 20; i++) spawnElementParticle(world, ids, start.add(delta.multiply(i / 20.0)));
            return;
        }
        if (shape.equals("wall")) {
            double yaw = Math.toRadians(dataTracker.get(FACING_YAW));
            Vec3d across = new Vec3d(Math.cos(yaw), 0.0, Math.sin(yaw));
            for (int i = 0; i <= 20; i++) {
                double offset = (i / 10.0 - 1.0) * radius;
                for (int y = -2; y <= 2; y += 2)
                    spawnElementParticle(world, ids, getPos().add(across.multiply(offset)).add(0.0, y, 0.0));
            }
            return;
        }
        double ringRadius = shape.equals("orbit") ? 2.4 : Math.max(1.0, radius);
        Vec3d center = shape.equals("barrier") && owner != null
            ? owner.getPos().add(0.0, 0.8, 0.0) : getPos();
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0 * i / 32.0;
            Vec3d point = center.add(Math.cos(angle) * ringRadius, shape.equals("barrier") ? 0.0 : 0.08,
                Math.sin(angle) * ringRadius);
            spawnElementParticle(world, ids, point);
            if (shape.equals("barrier")) {
                spawnElementParticle(world, ids, point.add(0.0, 1.5, 0.0));
                spawnElementParticle(world, ids, point.add(0.0, -0.5, 0.0));
            }
        }
        if (shape.equals("field") || shape.equals("rune")) {
            world.spawnParticles(ParticleTypes.ENCHANT, center.x, center.y + 0.15, center.z,
                4, radius * 0.35, 0.08, radius * 0.35, 0.01);
        }
    }

    private static void spawnElementParticle(ServerWorld world, java.util.List<String> ids, Vec3d pos) {
        if (ids.contains("lightning") || ids.contains("metal")) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.01);
        } else if (ids.contains("fire") || ids.contains("magma") || ids.contains("sun")) {
            world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.005);
        } else if (ids.contains("ice")) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0.002);
        } else if (ids.contains("water")) {
            world.spawnParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0.002);
        } else if (ids.contains("smoke") || ids.contains("shadow")) {
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0.002);
        } else if (ids.contains("wind")) {
            world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0.002);
        } else {
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.005, 0.005, 0.005, 0.002);
        }
    }

    private void deflectProjectiles(ServerWorld world, float radius) {
        for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class,
            getBoundingBox().expand(radius + 0.5), entity -> entity.isAlive()
                && !deflectedProjectiles.contains(entity.getUuid()))) {
            if (projectile.getPos().squaredDistanceTo(getPos()) > radius * radius) continue;
            projectile.setVelocity(projectile.getVelocity().multiply(-0.9).add(0.0, 0.12, 0.0));
            projectile.velocityModified = true;
            deflectedProjectiles.add(projectile.getUuid());
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        lifetime = nbt.getInt("Lifetime");
        dataTracker.set(OWNER_ID, nbt.getInt("OwnerId"));
        dataTracker.set(COMPONENTS, nbt.getString("Components"));
        dataTracker.set(SHAPE, nbt.getString("Shape"));
        dataTracker.set(RADIUS, nbt.getFloat("Radius"));
        dataTracker.set(FACING_YAW, nbt.getFloat("FacingYaw"));
        dataTracker.set(LEGENDARY, nbt.getBoolean("Legendary"));
        dataTracker.set(MAX_LIFETIME, nbt.contains("MaxLifetime") ? nbt.getInt("MaxLifetime") : 200);
        readUuidSet(nbt.getString("AffectedTargets"), affectedTargets);
        readUuidSet(nbt.getString("DeflectedProjectiles"), deflectedProjectiles);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Lifetime", lifetime);
        nbt.putInt("OwnerId", dataTracker.get(OWNER_ID));
        nbt.putString("Components", dataTracker.get(COMPONENTS));
        nbt.putString("Shape", dataTracker.get(SHAPE));
        nbt.putFloat("Radius", dataTracker.get(RADIUS));
        nbt.putFloat("FacingYaw", dataTracker.get(FACING_YAW));
        nbt.putBoolean("Legendary", dataTracker.get(LEGENDARY));
        nbt.putInt("MaxLifetime", dataTracker.get(MAX_LIFETIME));
        nbt.putString("AffectedTargets", joinUuidSet(affectedTargets));
        nbt.putString("DeflectedProjectiles", joinUuidSet(deflectedProjectiles));
    }

    private static String joinUuidSet(Set<UUID> values) {
        return values.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
    }

    private static void readUuidSet(String encoded, Set<UUID> target) {
        target.clear();
        if (encoded == null || encoded.isBlank()) return;
        for (String value : encoded.split(",")) {
            try { target.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
    }
}
