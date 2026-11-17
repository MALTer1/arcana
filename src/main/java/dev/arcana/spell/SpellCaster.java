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
        if (targets.isEmpty() && !teleport) {
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
        if (self) {
            result.add(player);
            return result;
        }

        double range = parts.contains("beam") ? 24.0 : parts.contains("projectile") ? 18.0 : 12.0;
        double radius = parts.contains("field") || parts.contains("barrier") ? 5.0
            : parts.contains("wave") ? 4.0 : 2.5;
        Box search = player.getBoundingBox().expand(range, 6.0, range);
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d origin = player.getEyePos();

        List<LivingEntity> candidates = player.getWorld().getEntitiesByClass(
            LivingEntity.class, search, entity -> entity != player && entity.isAlive());

        if (area || parts.contains("field") || parts.contains("wave")) {
            candidates.stream()
                .filter(entity -> entity.squaredDistanceTo(player) <= radius * radius)
                .forEach(result::add);
        } else {
            candidates.stream()
                .filter(entity -> {
                    Vec3d to = entity.getBoundingBox().getCenter().subtract(origin);
                    double distance = to.length();
                    return distance <= range && distance > 0.0
                        && look.dotProduct(to.normalize()) >= (parts.contains("beam") ? 0.94 : 0.82);
                })
                .sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                .limit(single || !multiple ? 1 : 5)
                .forEach(result::add);
        }

        if (parts.contains("barrier") && result.isEmpty()) result.add(player);
        return result;
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
