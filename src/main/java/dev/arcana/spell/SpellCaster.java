package dev.arcana.spell;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/** First-pass spell resolver. This version deliberately supports self-target utility spells only. */
public final class SpellCaster {
    private SpellCaster() {}

    public record CastResult(boolean success, String message, int cost) {}

    public static CastResult cast(ServerPlayerEntity player, SpellBookStore.StoredSpell spell) {
        List<String> components = spell.components;
        if (!components.contains("self")) {
            return new CastResult(false,
                "This first casting update supports Self-target spells. Targeted projectile and area spells are coming next.", 0);
        }

        String effect = components.stream()
            .filter(id -> List.of("healing", "enhancement", "barrier").contains(id))
            .findFirst().orElse(null);
        if (effect == null) {
            return new CastResult(false,
                "This spell's effect is not implemented for casting yet. Healing, Enhancement, and Barrier are currently supported.", 0);
        }

        int cost = 10 + (components.size() * 5);
        if (!ArcanaResource.trySpend(player, cost)) {
            return new CastResult(false, "Not enough Arcana. Need " + cost + ", currently have " +
                ArcanaResource.current(player) + ".", cost);
        }

        switch (effect) {
            case "healing" -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 1));
            case "enhancement" -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 12, 1));
            case "barrier" -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 10, 1));
            default -> {
                ArcanaResource.restore(player, cost);
                return new CastResult(false, "Unsupported spell effect.", cost);
            }
        }

        return new CastResult(true, "Cast '" + spell.name + "' for " + cost +
            " Arcana. Remaining Arcana: " + ArcanaResource.current(player) + ".", cost);
    }
}
