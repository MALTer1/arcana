package dev.arcana.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Server-authoritative, visible reaction cloud. The entity carries a small
 * validated reaction ID; visuals and behavior are selected from built-in
 * primitives rather than executable AI-generated code.
 */
public final class ArcanaEffectEntity extends Entity {
    private static final TrackedData<String> REACTION =
        DataTracker.registerData(ArcanaEffectEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> OWNER_ID =
        DataTracker.registerData(ArcanaEffectEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> RADIUS =
        DataTracker.registerData(ArcanaEffectEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private int lifetime;

    public ArcanaEffectEntity(EntityType<? extends ArcanaEffectEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(REACTION, "steam");
        builder.add(OWNER_ID, -1);
        builder.add(RADIUS, 2.5F);
    }

    public static boolean spawnReaction(ServerPlayerEntity owner, String reactionId, Vec3d position, double scale) {
        if (!(owner.getWorld() instanceof ServerWorld world)) return false;
        if (!isSupportedReaction(reactionId)) return false;
        ArcanaEffectEntity effect = new ArcanaEffectEntity(ArcanaEntities.MAGIC_EFFECT, world);
        effect.refreshPositionAndAngles(position.x, position.y, position.z, 0.0F, 0.0F);
        effect.dataTracker.set(REACTION, reactionId);
        effect.dataTracker.set(OWNER_ID, owner.getId());
        effect.dataTracker.set(RADIUS, (float) Math.max(1.0, Math.min(5.0, 2.5 * scale)));
        if (reactionId.equals("steam") || reactionId.equals("storm_steam") || reactionId.equals("mist")) {
            effect.setVelocity(owner.getRotationVec(1.0F).multiply(0.025).add(0.0, 0.012, 0.0));
        }
        return world.spawnEntity(effect);
    }

    public boolean isReaction(String id) {
        return id != null && id.equals(dataTracker.get(REACTION));
    }

    /**
     * Fire spells ignite nearby persistent ash clouds. The cloud owns its
     * lifetime and position, so this also works after the original spell ends.
     */
    public static int igniteNearbyAshClouds(ServerPlayerEntity caster, Vec3d position) {
        if (!(caster.getWorld() instanceof ServerWorld world)) return 0;
        Box search = new Box(position.x - 5, position.y - 4, position.z - 5,
            position.x + 5, position.y + 4, position.z + 5);
        var clouds = world.getEntitiesByClass(ArcanaEffectEntity.class, search,
            entity -> entity.isAlive() && entity.isReaction("ash_cloud"));
        int ignited = 0;
        for (ArcanaEffectEntity cloud : clouds) {
            Vec3d center = cloud.getPos();
            double radius = Math.max(2.5, cloud.dataTracker.get(RADIUS) + 1.0);
            world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 2, 0.25, 0.2, 0.25, 0.0);
            world.spawnParticles(ParticleTypes.FLAME, center.x, center.y + 0.4, center.z, 36,
                radius * 0.45, 0.45, radius * 0.45, 0.08);
            world.spawnParticles(ParticleTypes.SMOKE, center.x, center.y + 0.3, center.z, 22,
                radius * 0.4, 0.35, radius * 0.4, 0.035);
            Box blast = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, blast,
                entity -> entity.isAlive() && entity.getId() != cloud.dataTracker.get(OWNER_ID))) {
                if (target.getBoundingBox().getCenter().squaredDistanceTo(center) <= radius * radius) {
                    target.damage(world.getDamageSources().magic(), 6.0F);
                    target.setOnFireFor(3);
                }
            }
            cloud.discard();
            ignited++;
        }
        return ignited;
    }

    private static boolean isSupportedReaction(String id) {
        return switch (id) {
            case "storm_steam", "steam", "ash_cloud", "charged_water", "magma", "frozen_water",
                 "sandstorm", "wildfire", "lunar_tide", "solar_flare", "blizzard",
                 "mist", "mud", "thermal_shock", "eclipse", "resonance",
                 "verdant_surge", "magnetic_arc", "solar_eclipse" -> true;
            default -> false;
        };
    }

    @Override
    public void tick() {
        super.tick();
        String activeReaction = dataTracker.get(REACTION);
        int maxLifetime = activeReaction.equals("ash_cloud") ? 160 : 80;
        if (++lifetime > maxLifetime) {
            discard();
            return;
        }
        if (!(getWorld() instanceof ServerWorld world)) return;

        // Clouds drift after impact instead of leaving every particle at the original hit point.
        Vec3d drift = getVelocity();
        if (drift.lengthSquared() > 1.0E-5) {
            setPosition(getPos().add(drift));
            setVelocity(drift.multiply(0.96).add(0.0, 0.003, 0.0));
        }
        if (lifetime % 2 != 0) return;

        String reaction = dataTracker.get(REACTION);
        double radius = dataTracker.get(RADIUS);
        Vec3d pos = getPos();
        spawnVisuals(world, reaction, pos, radius);

        if (lifetime % 10 == 0 && (reaction.equals("steam") || reaction.equals("storm_steam")
            || reaction.equals("mist") || reaction.equals("sandstorm") || reaction.equals("ash_cloud"))) {
            int ownerId = dataTracker.get(OWNER_ID);
            Box area = new Box(pos.x - radius, pos.y - radius * 0.5, pos.z - radius,
                pos.x + radius, pos.y + radius * 0.5 + 1.0, pos.z + radius);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity.getId() != ownerId)) {
                if (!reaction.equals("ash_cloud")) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, true, false));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false));
                } else {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1, true, false));
                }
                if (reaction.equals("storm_steam")) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0, true, false));
                }
                // Steam remains an active hazard while an entity stays inside it.
                if ((reaction.equals("steam") || reaction.equals("storm_steam")) && lifetime % 20 == 0) {
                    target.damage(world.getDamageSources().magic(), 1.0F);
                }
            }
        }
    }

    private static void spawnVisuals(ServerWorld world, String reaction, Vec3d pos, double radius) {
        switch (reaction) {
            case "ash_cloud" -> {
                world.spawnParticles(ParticleTypes.WHITE_ASH, pos.x, pos.y + 0.4, pos.z,
                    12, radius * 0.55, 0.35, radius * 0.55, 0.008);
                world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.3, pos.z,
                    6, radius * 0.4, 0.25, radius * 0.4, 0.006);
            }
            case "steam", "storm_steam" -> {
                world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.4, pos.z,
                    7, radius * 0.45, 0.35, radius * 0.45, 0.012);
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y + 0.2, pos.z,
                    3, radius * 0.3, 0.15, radius * 0.3, 0.008);
                if (reaction.equals("storm_steam")) {
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.5, pos.z,
                        3, radius * 0.35, 0.4, radius * 0.35, 0.025);
                }
            }
            case "charged_water", "magnetic_arc" ->
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.5, pos.z,
                    8, radius * 0.45, 0.45, radius * 0.45, 0.04);
            case "magma", "wildfire", "solar_flare" -> {
                world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.2, pos.z,
                    8, radius * 0.4, 0.3, radius * 0.4, 0.025);
                world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.3, pos.z,
                    4, radius * 0.3, 0.2, radius * 0.3, 0.01);
            }
            case "frozen_water", "blizzard" ->
                world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.6, pos.z,
                    10, radius * 0.45, 0.45, radius * 0.45, 0.015);
            case "sandstorm" -> world.spawnParticles(ParticleTypes.WHITE_ASH, pos.x, pos.y + 0.4, pos.z,
                10, radius * 0.5, 0.25, radius * 0.5, 0.02);
            case "lunar_tide", "mist" -> {
                world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.3, pos.z,
                    8, radius * 0.45, 0.25, radius * 0.45, 0.008);
                if (reaction.equals("lunar_tide")) {
                    world.spawnParticles(ParticleTypes.BUBBLE, pos.x, pos.y + 0.1, pos.z,
                        5, radius * 0.3, 0.2, radius * 0.3, 0.01);
                }
            }
            case "mud" -> world.spawnParticles(ParticleTypes.WHITE_ASH, pos.x, pos.y + 0.3, pos.z,
                8, radius * 0.4, 0.3, radius * 0.4, 0.01);
            case "thermal_shock" -> {
                world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.3, pos.z,
                    4, radius * 0.3, 0.2, radius * 0.3, 0.015);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 0.5, pos.z,
                    4, radius * 0.35, 0.3, radius * 0.35, 0.01);
            }
            case "eclipse", "solar_eclipse" ->
                world.spawnParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.5, pos.z,
                    7, radius * 0.4, 0.35, radius * 0.4, 0.012);
            case "resonance" -> world.spawnParticles(ParticleTypes.NOTE, pos.x, pos.y + 0.6, pos.z,
                5, radius * 0.35, 0.35, radius * 0.35, 0.0);
            case "verdant_surge" ->
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.5, pos.z,
                    6, radius * 0.35, 0.35, radius * 0.35, 0.01);
            default -> world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.4, pos.z,
                5, radius * 0.3, 0.25, radius * 0.3, 0.01);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        lifetime = nbt.getInt("Lifetime");
        dataTracker.set(REACTION, nbt.getString("Reaction"));
        dataTracker.set(OWNER_ID, nbt.getInt("OwnerId"));
        dataTracker.set(RADIUS, nbt.getFloat("Radius"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Lifetime", lifetime);
        nbt.putString("Reaction", dataTracker.get(REACTION));
        nbt.putInt("OwnerId", dataTracker.get(OWNER_ID));
        nbt.putFloat("Radius", dataTracker.get(RADIUS));
    }
}
