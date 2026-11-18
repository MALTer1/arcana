package dev.arcana.spell;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Resolves every currently registered Arcana component.
 *
 * Delivery components control how targets are selected; target components
 * choose the affected entities; elements modify the result; effect components
 * supply the spell's main action.
 */
public final class SpellCaster {
    private static final int BASE_COST = 10;
    private static final int COMPONENT_COST = 5;

    private SpellCaster() {}

    public record CastResult(boolean success, String message, int cost) {}

    public static CastResult cast(ServerPlayerEntity player, SpellBookStore.StoredSpell spell) {
        if (spell == null || spell.components == null || spell.components.isEmpty()) {
            return new CastResult(false, "This spell has no components.", 0);
        }

        List<String> parts = spell.components;
        boolean self = parts.contains("self");
        boolean area = parts.contains("area");
        boolean multiple = parts.contains("multiple_targets");
        boolean single = parts.contains("single_target");
        boolean teleport = parts.contains("teleportation");
        SpellCompositionEngine.Plan composition = SpellCompositionEngine.compose(parts);

        List<LivingEntity> targets = selectTargets(player, parts, self, area, multiple, single, composition);
        if (targets.isEmpty() && !teleport && !parts.contains("summoning")) {
            return new CastResult(false, "No valid target was found. Aim at a nearby creature or choose a different target type.", 0);
        }

        int cost = BASE_COST + parts.size() * COMPONENT_COST;
        if (!ArcanaResource.trySpend(player, cost)) {
            return new CastResult(false, "Not enough Arcana. Need " + cost + ", currently have " +
                ArcanaResource.current(player) + ".", cost);
        }

        int affected = 0;
        boolean didSummon = false;
        List<ElementalReactionEngine.Reaction> reactions = ElementalReactionEngine.resolve(parts);
        for (LivingEntity target : targets) {
            if (target.isRemoved()) continue;
            applyElements(player, target, parts, reactions);
            applyEffects(player, target, parts);
            applyCompositionInteractions(player, target, composition);
            affected++;
        }

        if (parts.contains("summoning")) didSummon = summon(player);
        if (teleport && (self || targets.contains(player))) {
            teleportForward(player, parts.contains("wind") ? 10.0 : 6.0);
            affected++;
        }

        if (affected == 0 && !didSummon) {
            ArcanaResource.restore(player, cost);
            return new CastResult(false, "The spell had no valid effect; Arcana was refunded.", cost);
        }

        SpellIntelligence.Analysis analysis = SpellIntelligence.analyze(spell.name, parts);
        String detail = didSummon ? " A magical helper was summoned." : "";
        String reactionText = analysis.reactions().isEmpty() ? "" :
            " Reactions: " + analysis.reactions().stream().map(ElementalReactionEngine.Reaction::name)
                .collect(java.util.stream.Collectors.joining(", ")) + ".";
        return new CastResult(true, "Cast '" + spell.name + "'. " + analysis.summary() +
            " Cost: " + cost + " Arcana. Affected " + affected + " target(s)." + detail + reactionText +
            " Remaining Arcana: " + ArcanaResource.current(player) + ".", cost);
    }

    private static List<LivingEntity> selectTargets(ServerPlayerEntity player, List<String> parts,
                                                     boolean self, boolean area, boolean multiple, boolean single,
                                                     SpellCompositionEngine.Plan composition) {
        List<LivingEntity> result = new ArrayList<>();
        if (self) result.add(player);

        double range = (parts.contains("beam") ? 24.0 : parts.contains("projectile") ? 18.0 : 12.0)
            * composition.rangeMultiplier();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d origin = player.getEyePos();
        Vec3d impact = origin.add(look.multiply(range));
        Box search = player.getBoundingBox().expand(range, 8.0, range);
        List<LivingEntity> candidates = player.getWorld().getEntitiesByClass(
            LivingEntity.class, search, entity -> entity != player && entity.isAlive());

        if (parts.contains("self_and_nearby")) {
            result.add(player);
            candidates.stream().filter(entity -> entity.isTeammate(player))
                .filter(entity -> entity.squaredDistanceTo(player) <= 8.0 * 8.0)
                .limit(4).forEach(result::add);
        }

        if (!self && !parts.contains("self_and_nearby")) {
            if (parts.contains("chain")) {
                LivingEntity current = null;
                Vec3d from = origin;
                for (int i = 0; i < (multiple ? 5 : 1); i++) {
                    final Vec3d chainOrigin = from;
                    LivingEntity next = candidates.stream()
                        .filter(entity -> !result.contains(entity))
                        .filter(entity -> entity.getBoundingBox().getCenter().squaredDistanceTo(chainOrigin) <= 8.0 * 8.0)
                        .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(chainOrigin)))
                        .orElse(null);
                    if (next == null) break;
                    result.add(next);
                    current = next;
                    from = current.getBoundingBox().getCenter();
                }
            } else {
                candidates.stream()
                    .filter(entity -> matchesDeliveryShape(entity, parts, origin, impact, look, range, composition))
                    .filter(entity -> !parts.contains("ally") || entity.isTeammate(player))
                    .filter(entity -> !parts.contains("hostile") || !entity.isTeammate(player))
                    .sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                    .limit(multiple ? 5 : 1)
                    .forEach(result::add);
            }
        }

        if ((parts.contains("barrier") || parts.contains("pulse") || parts.contains("orbit")
            || parts.contains("ground") || parts.contains("block") || parts.contains("projectile_target")
            || hasSelfUtility(parts)) && result.isEmpty()) {
            result.add(player);
        }
        return result;
    }

    private static boolean hasSelfUtility(List<String> parts) {
        return parts.stream().anyMatch(id -> List.of("illumination", "invisibility", "disguise",
            "levitation", "dash", "haste", "fatigue", "silence", "recall", "anchor", "float",
            "water_walking", "climb", "jump", "sprint", "glide", "temperature", "weather",
            "growth", "harvest", "repair", "cleanse", "purify", "ward", "reflection",
            "resistance", "absorption", "regeneration", "stamina", "focus", "fortune",
            "magnetism", "repulsion", "storage", "transmutation", "duplication", "decoy",
            "barrier_reinforcement", "portal", "dimension_sense", "memory", "alarm",
            "night_vision", "water_breathing", "slow_falling").contains(id));
    }

    private static boolean matchesDeliveryShape(LivingEntity entity, List<String> parts,
                                                 Vec3d origin, Vec3d impact, Vec3d look, double range,
                                                 SpellCompositionEngine.Plan composition) {
        Vec3d point = entity.getBoundingBox().getCenter();
        Vec3d offset = point.subtract(origin);
        double distance = offset.length();
        if (distance <= 0.0 || distance > range) return false;

        double areaScale = composition.areaMultiplier();
        double forward = offset.dotProduct(look);
        Vec3d lateral = offset.subtract(look.multiply(forward));

        // Composite delivery is a union of its selected shapes. A spell with
        // Cone + Ring, for example, can affect either region rather than one
        // delivery silently taking precedence over the other.
        if ((parts.contains("pulse") || parts.contains("orbit"))
            && point.distanceTo(origin) <= (parts.contains("orbit") ? 5.0 : 6.0) * areaScale) return true;

        if (parts.contains("ring")) {
            double radius = point.distanceTo(impact);
            if (radius >= 2.0 * areaScale && radius <= 4.5 * areaScale) return true;
        }

        if (parts.contains("rune") || parts.contains("field") || parts.contains("area")) {
            double radius = (parts.contains("rune") ? 3.0 : 2.5) * areaScale;
            if (point.squaredDistanceTo(impact) <= radius * radius) return true;
        }

        if (parts.contains("column")) {
            double horizontal = Math.sqrt(Math.pow(point.x - impact.x, 2) + Math.pow(point.z - impact.z, 2));
            if (horizontal <= 2.5 * areaScale
                && Math.abs(point.y - impact.y) <= 6.0 * areaScale) return true;
        }

        if (parts.contains("wall")) {
            Vec3d fromImpact = point.subtract(impact);
            double depth = Math.abs(fromImpact.dotProduct(look));
            Vec3d wallLateral = fromImpact.subtract(look.multiply(fromImpact.dotProduct(look)));
            if (depth <= 2.0 * areaScale && wallLateral.horizontalLength() <= 5.0 * areaScale
                && Math.abs(fromImpact.y) <= 3.0 * areaScale) return true;
        }

        if (parts.contains("cone") && forward > 0.0 && forward <= range
            && lateral.length() <= Math.max(1.0, forward * 0.55 * areaScale)) return true;

        if (parts.contains("arc") && forward > 0.0) {
            double t = Math.max(0.0, Math.min(1.0, forward / range));
            Vec3d arcPoint = origin.add(look.multiply(forward))
                .add(0.0, Math.sin(t * Math.PI) * 2.5, 0.0);
            if (lateral.length() <= 2.5 * areaScale
                && point.squaredDistanceTo(arcPoint) <= Math.pow(3.5 * areaScale, 2)) return true;
        }

        if (forward > 0.0 && forward <= range) {
            if (parts.contains("tether") && lateral.length() <= 1.75 * areaScale) return true;
            if (parts.contains("wave") && lateral.length() <= 4.0 * areaScale) return true;
            double lineWidth = (parts.contains("beam") ? 0.9 : parts.contains("projectile") ? 1.4 : 2.5)
                * areaScale;
            if ((!parts.contains("cone") && !parts.contains("arc") && !parts.contains("tether")
                && !parts.contains("wave") && !parts.contains("ring") && !parts.contains("pulse")
                && !parts.contains("orbit") && !parts.contains("field") && !parts.contains("rune")
                && !parts.contains("wall") && !parts.contains("column"))
                || parts.contains("beam") || parts.contains("projectile")) {
                if (lateral.length() <= lineWidth) return true;
            }
        }
        return false;
    }

    private static void applyElements(ServerPlayerEntity caster, LivingEntity target, List<String> parts,
                                      List<ElementalReactionEngine.Reaction> reactions) {
        java.util.Set<String> consumed = ElementalReactionEngine.consumedElements(reactions);
        for (ElementalReactionEngine.Reaction reaction : reactions) {
            switch (reaction.id()) {
                case "storm_steam" -> {
                    target.damage(caster.getDamageSources().indirectMagic(caster, caster), 4.0F);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0));
                }
                case "steam" -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 45, 0));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
                }
                case "charged_water" -> {
                    target.damage(caster.getDamageSources().indirectMagic(caster, caster), 4.0F);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
                }
                case "magma" -> {
                    target.setOnFireFor(5);
                    target.damage(caster.getDamageSources().indirectMagic(caster, caster), 2.0F);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
                }
                case "frozen_water" -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 2));
                    target.setFrozenTicks(Math.min(target.getMinFreezeDamageTicks() * 2, target.getFrozenTicks() + 100));
                }
                case "sandstorm" -> {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0));
                }
                case "wildfire" -> {
                    target.setOnFireFor(7);
                    Vec3d push = target.getPos().subtract(caster.getPos()).normalize().multiply(1.1);
                    target.addVelocity(push.x, 0.3, push.z);
                    target.velocityModified = true;
                }
                case "lunar_tide" -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0));
                case "solar_flare" -> {
                    target.setOnFireFor(7);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
                }
                default -> { }
            }
        }

        if (parts.contains("fire") && !consumed.contains("fire")) target.setOnFireFor(5);
        if (parts.contains("water") && !consumed.contains("water")) {
            target.extinguish();
            target.removeStatusEffect(StatusEffects.SLOWNESS);
        }
        if (parts.contains("wind") && !consumed.contains("wind")) {
            Vec3d push = target.getPos().subtract(caster.getPos()).normalize().multiply(0.9);
            target.addVelocity(push.x, 0.35, push.z);
            target.velocityModified = true;
        }
        if (parts.contains("earth") && !consumed.contains("earth")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
        }
        if (parts.contains("lightning") && !consumed.contains("lightning")) {
            target.damage(caster.getDamageSources().indirectMagic(caster, caster), 4.0F);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
        }
        if (parts.contains("ice") && !consumed.contains("ice")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 2));
            target.setFrozenTicks(Math.min(target.getMinFreezeDamageTicks() * 2, target.getFrozenTicks() + 100));
        }

        // Additional registered elements have concrete first-pass status behavior.
        if (parts.contains("light")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
        if (parts.contains("shadow")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 100, 0));
        if (parts.contains("nature")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
        if (parts.contains("metal")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 1));
        if (parts.contains("crystal")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 0));
        }
        if (parts.contains("magma") && !parts.contains("fire") && !consumed.contains("magma")) {
            target.setOnFireFor(6);
            target.damage(caster.getDamageSources().indirectMagic(caster, caster), 3.0F);
        }
        if (parts.contains("smoke")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
        if (parts.contains("sound")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
        }
        if (parts.contains("gravity")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0));
            target.addVelocity(0.0, target.isOnGround() ? 0.45 : -0.35, 0.0);
            target.velocityModified = true;
        }
        if (parts.contains("time")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
        if (parts.contains("space")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0));
        }
        if (parts.contains("poison")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
        if (parts.contains("sand")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0));
        }
        if (parts.contains("wood")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0));
        }
        if (parts.contains("moon")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 200, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
        }
        if (parts.contains("sun")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 0));
        }
    }

    /**
     * Applies emergent interactions calculated from the complete component set.
     * This is separate from individual component effects so combinations can
     * alter outcomes without duplicating every base component implementation.
     */
    private static void applyCompositionInteractions(ServerPlayerEntity caster, LivingEntity target,
                                                     SpellCompositionEngine.Plan plan) {
        if (plan.impactDamageBonus() > 0.0) {
            target.damage(caster.getDamageSources().indirectMagic(caster, caster),
                (float) plan.impactDamageBonus());
        }
        if (plan.stunTicks() > 0) {
            // Minecraft has no universal stun status; use bounded movement
            // and action interruption effects as a practical approximation.
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                plan.stunTicks(), 4));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE,
                plan.stunTicks(), 2));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,
                plan.stunTicks(), 0));
        }
        if (plan.slowTicks() > 0 && plan.stunTicks() == 0) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                plan.slowTicks(), 2));
        }
    }

    private static void applyEffects(ServerPlayerEntity caster, LivingEntity target, List<String> parts) {
        if (parts.contains("healing")) { target.heal(6.0F); target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0)); }
        if (parts.contains("enhancement")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 240, 1));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 240, 0));
        }
        if (parts.contains("barrier") || parts.contains("ward") || parts.contains("barrier_reinforcement")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, parts.contains("barrier_reinforcement") ? 2 : 1));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1));
        }
        if (parts.contains("explosion")) {
            target.damage(caster.getDamageSources().indirectMagic(caster, caster), 8.0F);
            Vec3d away = target.getPos().subtract(caster.getPos()).normalize().multiply(1.4);
            target.addVelocity(away.x, 0.55, away.z); target.velocityModified = true;
        }
        if (parts.contains("binding")) {
            // Avoid extreme Jump Boost amplifiers: they can launch server-side mobs.
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 5));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 1));
        }
        if (parts.contains("night_vision")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0));
        if (parts.contains("water_breathing")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 1200, 0));
        if (parts.contains("slow_falling")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0));

        if (parts.contains("illumination")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 240, 0));
        if (parts.contains("invisibility")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 240, 0));
        if (parts.contains("disguise")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 100, 0));
        if (parts.contains("levitation")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 80, 0));
        if (parts.contains("dash")) {
            Vec3d v = target.getRotationVec(1.0F).multiply(1.8);
            target.addVelocity(v.x, 0.2, v.z); target.velocityModified = true;
        }
        if (parts.contains("haste") || parts.contains("focus")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 300, 1));
        if (parts.contains("fatigue")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, 1));
        if (parts.contains("silence")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0));
        if (parts.contains("echo")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
        if (parts.contains("reveal") || parts.contains("tracking") || parts.contains("waypoint") || parts.contains("dimension_sense"))
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 300, 0));
        if (parts.contains("recall")) {
            target.requestTeleport(caster.getX(), caster.getY(), caster.getZ());
        }
        if (parts.contains("anchor")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 0));
        if (parts.contains("float") || parts.contains("glide")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 300, 0));
        if (parts.contains("water_walking")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 300, 0));
        if (parts.contains("climb")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 240, 1));
        if (parts.contains("jump")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 240, 2));
        if (parts.contains("sprint")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 240, 2));
        if (parts.contains("temperature")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 240, 0));
        if (parts.contains("weather")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 240, 0));
        if (parts.contains("growth")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 160, 1));
        if (parts.contains("harvest")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 1, 0));
        if (parts.contains("repair")) target.heal(10.0F);
        if (parts.contains("cleanse") || parts.contains("purify")) {
            target.removeStatusEffect(StatusEffects.POISON);
            target.removeStatusEffect(StatusEffects.WITHER);
            target.removeStatusEffect(StatusEffects.SLOWNESS);
            target.removeStatusEffect(StatusEffects.MINING_FATIGUE);
            target.removeStatusEffect(StatusEffects.BLINDNESS);
            target.removeStatusEffect(StatusEffects.NAUSEA);
        }
        if (parts.contains("reflection")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 240, 2));
        if (parts.contains("resistance")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 1));
        if (parts.contains("absorption")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 300, 2));
        if (parts.contains("regeneration")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 300, 1));
        if (parts.contains("stamina")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 1, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 0));
        }
        if (parts.contains("fortune")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 600, 0));
        if (parts.contains("magnetism")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 200, 0));
        if (parts.contains("repulsion")) {
            Vec3d away = target.getPos().subtract(caster.getPos()).normalize().multiply(1.2);
            target.addVelocity(away.x, 0.25, away.z); target.velocityModified = true;
        }
        if (parts.contains("storage")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0));
        if (parts.contains("transmutation")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
        if (parts.contains("duplication")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 200, 1));
        if (parts.contains("decoy")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 80, 0));
        if (parts.contains("portal")) {
            Vec3d destination = caster.getPos().add(caster.getRotationVec(1.0F).multiply(6.0));
            teleportSafely(target, destination);
        }
        if (parts.contains("memory")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
        if (parts.contains("alarm")) target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 400, 0));
    }

    private static boolean summon(ServerPlayerEntity player) {
        Entity helper = EntityType.ALLAY.create(player.getWorld());
        if (helper == null) return false;
        Vec3d spawn = player.getPos().add(player.getRotationVec(1.0F).multiply(2.0));
        helper.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, player.getYaw(), 0.0F);
        return player.getWorld().spawnEntity(helper);
    }

    private static void teleportForward(ServerPlayerEntity player, double distance) {
        Vec3d destination = player.getPos().add(player.getRotationVec(1.0F).multiply(distance));
        teleportSafely(player, destination);
    }

    /** Teleport only when the destination is clear for the entity's bounding box. */
    private static boolean teleportSafely(LivingEntity entity, Vec3d requested) {
        Vec3d origin = entity.getPos();
        Vec3d movement = requested.subtract(origin);
        double distance = movement.length();
        if (distance < 0.01) return false;

        Vec3d direction = movement.normalize();
        Vec3d rayStart = origin.add(0.0, 0.5, 0.0);
        Vec3d rayEnd = rayStart.add(movement);
        var hit = entity.getWorld().raycast(new RaycastContext(
            rayStart, rayEnd, RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE, entity));

        Vec3d destination = hit.getType() == HitResult.Type.BLOCK
            ? hit.getPos().subtract(direction.multiply(0.75)).subtract(0.0, 0.5, 0.0)
            : requested;

        for (double backoff = 0.0; backoff <= distance; backoff += 0.5) {
            Vec3d candidate = destination.subtract(direction.multiply(backoff));
            Vec3d offset = candidate.subtract(origin);
            if (entity.getWorld().isSpaceEmpty(entity, entity.getBoundingBox().offset(offset))) {
                entity.requestTeleport(candidate.x, candidate.y, candidate.z);
                return true;
            }
        }
        return false;
    }
}
