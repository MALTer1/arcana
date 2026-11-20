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
        return world.spawnEntity(effect);
    }

    private static boolean isSupportedReaction(String id) {
        return switch (id) {
            case "storm_steam", "steam", "charged_water", "magma", "frozen_water",
                 "sandstorm", "wildfire", "lunar_tide", "solar_flare", "blizzard",
                 "mist", "mud", "thermal_shock", "eclipse", "resonance",
                 "verdant_surge", "magnetic_arc", "solar_eclipse" -> true;
            default -> false;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (++lifetime > 80) {
            discard();
            return;
        }
        if (!(getWorld() instanceof ServerWorld world) || lifetime % 2 != 0) return;

        String reaction = dataTracker.get(REACTION);
        double radius = dataTracker.get(RADIUS);
        Vec3d pos = getPos();
        spawnVisuals(world, reaction, pos, radius);

        if (lifetime % 10 == 0 && (reaction.equals("steam") || reaction.equals("storm_steam")
            || reaction.equals("mist") || reaction.equals("sandstorm"))) {
            int ownerId = dataTracker.get(OWNER_ID);
            Box area = new Box(pos.x - radius, pos.y - radius * 0.5, pos.z - radius,
                pos.x + radius, pos.y + radius * 0.5 + 1.0, pos.z + radius);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity.getId() != ownerId)) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, true, false));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, true, false));
                if (reaction.equals("storm_steam")) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0, true, false));
                }
            }
        }
    }

    private static void spawnVisuals(ServerWorld world, String reaction, Vec3d pos, double radius) {
        switch (reaction) {
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
