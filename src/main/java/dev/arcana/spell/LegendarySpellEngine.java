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
            return "Solar Cataclysm — the spell releases a second wave of searing arcane force.";
        if (ids.contains("water") || ids.contains("ice"))
            return "Glacial Dominion — the spell leaves targets chilled and slowed by lingering frost.";
        if (ids.contains("lightning") || ids.contains("wind"))
            return "Stormheart — the spell chains a burst of speed and electrical force through its target.";
        if (ids.contains("nature") || ids.contains("wood") || ids.contains("growth"))
            return "Worldroot — the spell restores its allies and surrounds them with a protective ward.";
        if (ids.contains("shadow") || ids.contains("poison"))
            return "Eclipse — the spell obscures its target and weakens its movement.";
        if (ids.contains("gravity") || ids.contains("space") || ids.contains("time"))
            return "Rift Singularity — the spell creates a powerful spatial pulse around its target.";
        if (ids.contains("healing") || ids.contains("regeneration"))
            return "Phoenix Renewal — the spell grants a powerful restorative surge.";
        if (ids.contains("explosion") || ids.contains("binding"))
            return "Ruinous Resonance — the spell releases a second controlled force pulse.";
        return "Arcane Ascendance — the spell's signature effect is amplified beyond its normal limits.";
    }

    public static void apply(ServerPlayerEntity caster, LivingEntity target,
                             SpellBookStore.StoredSpell spell) {
        if (spell == null || !spell.legendary || target == null || !target.isAlive()) return;
        List<String> ids = spell.components;
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 2));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 1));
        if (ids.contains("healing") || ids.contains("regeneration") || ids.contains("nature")
            || ids.contains("wood") || ids.contains("growth")) {
            target.heal(12.0F);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 400, 2));
        } else if (ids.contains("explosion") || ids.contains("fire") || ids.contains("magma")
            || ids.contains("lightning") || ids.contains("sun")) {
            if (target != caster) target.damage(caster.getDamageSources().indirectMagic(caster, caster), 12.0F);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 300, 1));
            Vec3d away = target.getPos().subtract(caster.getPos()).normalize().multiply(1.8);
            target.addVelocity(away.x, 0.45, away.z);
            target.velocityModified = true;
        } else if (ids.contains("ice") || ids.contains("water") || ids.contains("shadow")
            || ids.contains("poison")) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 300, 3));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 300, 1));
        } else {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 2));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 400, 2));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
        }
    }
}
