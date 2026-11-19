package dev.arcana.spell;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/** Defines and applies the extra signature power granted by a Legendary Scroll. */
public final class LegendarySpellEngine {
    private LegendarySpellEngine() {}

    public static String describe(List<String> ids) {
        if (ids == null) return "Arcane Ascendance";
        if (ids.contains("fire") || ids.contains("magma") || ids.contains("sun"))
            return "Solar Cataclysm — a second wave of searing arcane force.";
        if (ids.contains("water") || ids.contains("ice"))
            return "Glacial Dominion — lingering frost slows and weakens hostile targets.";
        if (ids.contains("lightning") || ids.contains("wind"))
            return "Stormheart — a burst of electrical force and accelerated movement.";
        if (ids.contains("nature") || ids.contains("wood") || ids.contains("growth"))
            return "Worldroot — a restorative surge wrapped in a protective ward.";
        if (ids.contains("shadow") || ids.contains("poison"))
            return "Eclipse — an obscuring field that weakens hostile targets.";
        if (ids.contains("gravity") || ids.contains("space") || ids.contains("time"))
            return "Rift Singularity — a spatial pulse that disrupts nearby movement.";
        if (ids.contains("healing") || ids.contains("regeneration"))
            return "Phoenix Renewal — a powerful restorative surge.";
        if (ids.contains("explosion") || ids.contains("binding"))
            return "Ruinous Resonance — a second controlled force pulse.";
        return "Arcane Ascendance — the spell's signature effect is amplified beyond its normal limits.";
    }

    public static void apply(ServerPlayerEntity caster, LivingEntity target,
                             SpellBookStore.StoredSpell spell) {
        if (spell == null || !spell.legendary || target == null || !target.isAlive()) return;
        List<String> ids = spell.components;
        boolean friendly = target == caster || target.isTeammate(caster);
        if (friendly) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 2));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 1));
            if (ids.contains("healing") || ids.contains("regeneration") || ids.contains("nature")
                || ids.contains("wood") || ids.contains("growth")) {
                target.heal(12.0F);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 400, 2));
            } else {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 2));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 400, 2));
            }
            return;
        }

        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 300, 1));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 300, 1));
        if (ids.contains("fire") || ids.contains("magma") || ids.contains("sun")) {
            target.setOnFireFor(10);
            target.damage(caster.getDamageSources().indirectMagic(caster, caster), 12.0F);
        } else if (ids.contains("lightning") || ids.contains("explosion") || ids.contains("binding")) {
            target.damage(caster.getDamageSources().indirectMagic(caster, caster), 12.0F);
            Vec3d away = target.getPos().subtract(caster.getPos()).normalize().multiply(1.8);
            target.addVelocity(away.x, 0.45, away.z);
            target.velocityModified = true;
        } else if (ids.contains("ice") || ids.contains("water") || ids.contains("shadow")
            || ids.contains("poison") || ids.contains("gravity") || ids.contains("space")
            || ids.contains("time")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 300, 0));
        } else {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
        }
    }
}
