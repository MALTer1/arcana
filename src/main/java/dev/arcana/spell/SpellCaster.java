package dev.arcana.spell;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
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

        List<LivingEntity> targets = selectTargets(player, parts, self, area, multiple, single);
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
        for (LivingEntity target : targets) {
            if (target.isRemoved()) continue;
            applyElements(player, target, parts);
            applyEffects(player, target, parts);
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

        String detail = didSummon ? " A magical helper was summoned." : "";
        return new CastResult(true, "Cast '" + spell.name + "' for " + cost +
            " Arcana. Affected " + affected + " target(s)." + detail +
            " Remaining Arcana: " + ArcanaResource.current(player) + ".", cost);
    }

    private static List<LivingEntity> selectTargets(ServerPlayerEntity player, List<String> parts,
                                                     boolean self, boolean area, boolean multiple, boolean single) {
        List<LivingEntity> result = new ArrayList<>();
        if (self) result.add(player);

        double range = parts.contains("beam") ? 24.0 : parts.contains("projectile") ? 18.0 : 12.0;
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d origin = player.getEyePos();
        Vec3d impact = origin.add(look.multiply(range));
        Box search = player.getBoundingBox().expand(range, 8.0, range);
        List<LivingEntity> candidates = player.getWorld().getEntitiesByClass(
            LivingEntity.class, search, entity -> entity != player && entity.isAlive());

        if (!self) {
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
                    .filter(entity -> matchesDeliveryShape(entity, parts, origin, impact, look, range))
                    .sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                    .limit(multiple ? 5 : 1)
                    .forEach(result::add);
            }
        }

        if ((parts.contains("barrier") || parts.contains("pulse") || parts.contains("orbit")) && result.isEmpty()) {
            result.add(player);
        }
        return result;
    }

    private static boolean matchesDeliveryShape(LivingEntity entity, List<String> parts,
                                                 Vec3d origin, Vec3d impact, Vec3d look, double range) {
        Vec3d point = entity.getBoundingBox().getCenter();
        Vec3d offset = point.subtract(origin);
        double distance = offset.length();
        if (distance <= 0.0 || distance > range) return false;

        if (parts.contains("pulse") || parts.contains("orbit")) {
            double radius = parts.contains("orbit") ? 5.0 : 6.0;
            double distanceFromCaster = point.distanceTo(origin);
            return distanceFromCaster <= radius;
        }

        if (parts.contains("ring")) {
            double radius = point.distanceTo(impact);
            return radius >= 2.0 && radius <= 4.5;
        }

        if (parts.contains("rune") || parts.contains("field") || parts.contains("area")) {
            double radius = parts.contains("rune") ? 3.0 : 2.5;
            return point.squaredDistanceTo(impact) <= radius * radius;
        }

        if (parts.contains("column")) {
            double horizontal = Math.sqrt(
                Math.pow(point.x - impact.x, 2) + Math.pow(point.z - impact.z, 2));
            return horizontal <= 2.5 && Math.abs(point.y - impact.y) <= 6.0;
        }

        if (parts.contains("wall")) {
            Vec3d fromImpact = point.subtract(impact);
            double depth = Math.abs(fromImpact.dotProduct(look));
            Vec3d lateral = fromImpact.subtract(look.multiply(fromImpact.dotProduct(look)));
            return depth <= 2.0 && lateral.horizontalLength() <= 5.0
                && Math.abs(fromImpact.y) <= 3.0;
        }

        double forward = offset.dotProduct(look);
        Vec3d lateral = offset.subtract(look.multiply(forward));
        if (parts.contains("cone")) {
            return forward > 0.0 && forward <= range
                && lateral.length() <= Math.max(1.0, forward * 0.55);
        }

        if (parts.contains("arc")) {
            double t = Math.max(0.0, Math.min(1.0, forward / range));
            Vec3d arcPoint = origin.add(look.multiply(forward))
                .add(0.0, Math.sin(t * Math.PI) * 2.5, 0.0);
            return forward > 0.0 && lateral.length() <= 2.5
                && point.squaredDistanceTo(arcPoint) <= 3.5 * 3.5;
        }

        if (parts.contains("tether")) {
            return forward > 0.0 && forward <= range && lateral.length() <= 1.75;
        }

        if (parts.contains("wave")) {
            return forward > 0.0 && forward <= range && lateral.length() <= 4.0;
        }

        // Projectile and beam use a narrow line; the default direct delivery
        // uses a slightly wider line so ordinary spells remain forgiving.
        double width = parts.contains("beam") ? 0.9 : parts.contains("projectile") ? 1.4 : 2.5;
        return forward > 0.0 && forward <= range && lateral.length() <= width;
    }

    private static void applyElements(ServerPlayerEntity caster, LivingEntity target, List<String> parts) {
        if (parts.contains("fire")) target.setOnFireFor(5);
        if (parts.contains("water")) {
            target.extinguish();
            target.removeStatusEffect(StatusEffects.SLOWNESS);
        }
        if (parts.contains("wind")) {
            Vec3d push = target.getPos().subtract(caster.getPos()).normalize().multiply(0.9);
            target.addVelocity(push.x, 0.35, push.z);
            target.velocityModified = true;
        }
        if (parts.contains("earth")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 5, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 3, 0));
        }
        if (parts.contains("lightning")) {
            target.damage(caster.getDamageSources().magic(), 4.0F);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 4, 0));
        }
        if (parts.contains("ice")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 6, 2));
            target.setFrozenTicks(Math.min(target.getMinFreezeDamageTicks() * 2, target.getFrozenTicks() + 100));
        }
    }

    private static void applyEffects(ServerPlayerEntity caster, LivingEntity target, List<String> parts) {
        if (parts.contains("healing")) {
            target.heal(6.0F);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 4, 0));
        }
        if (parts.contains("enhancement")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 12, 1));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 12, 0));
        }
        if (parts.contains("barrier")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 10, 1));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 10, 0));
        }
        if (parts.contains("explosion")) {
            target.damage(caster.getDamageSources().magic(), 8.0F);
            Vec3d away = target.getPos().subtract(caster.getPos()).normalize().multiply(1.4);
            target.addVelocity(away.x, 0.55, away.z);
            target.velocityModified = true;
        }
        if (parts.contains("binding")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 8, 5));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20 * 8, 128));
        }
        if (parts.contains("night_vision")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 20 * 60, 0));
        }
        if (parts.contains("water_breathing")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 20 * 60, 0));
        }
        if (parts.contains("slow_falling")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20 * 20, 0));
        }
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
        player.requestTeleport(destination.x, destination.y, destination.z);
    }
}
