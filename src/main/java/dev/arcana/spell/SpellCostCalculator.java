package dev.arcana.spell;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes spell cost from the actual program rather than a flat per-glyph fee.
 * Costs are deterministic and shared by previews and casting.
 */
public final class SpellCostCalculator {
    private SpellCostCalculator() {}

    public record Cost(int total, int formCost, int effectCost, int augmentCost,
                       int elementalCost, int complexityCost, int legendaryCost) {}

    public static Cost calculate(List<String> ids, boolean legendary) {
        if (ids == null || ids.isEmpty()) return new Cost(0, 0, 0, 0, 0, 0, 0);
        SpellProgram.Compilation program = SpellProgram.compile(ids);
        int form = 0, effects = 0, augments = 0, elements = 0;
        Set<String> uniqueEffects = new HashSet<>();
        for (SpellProgram.Step step : program.steps()) {
            SpellComponent component = ComponentRegistry.get(step.componentId());
            if (component == null) continue;
            switch (step.role()) {
                case FORM -> form += formWeight(step.componentId());
                case EFFECT -> {
                    effects += effectWeight(step.componentId());
                    uniqueEffects.add(step.componentId());
                }
                case AUGMENT -> {
                    if (component.category() == ComponentCategory.ELEMENT) {
                        elements += elementWeight(step.componentId());
                    } else {
                        augments += augmentWeight(step.componentId());
                    }
                }
            }
        }
        int formCost = form;
        int effectCost = effects;
        int augmentCost = augments;
        int elementalCost = elements;
        int complexity = Math.max(0, uniqueEffects.size() - 1) * 3
            + Math.max(0, ids.size() - 3) * 2;
        int legendaryCost = legendary ? 35 : 0;
        int subtotal = formCost + effectCost + augmentCost + elementalCost + complexity + legendaryCost;
        if (ids.contains("efficiency")) subtotal = (int)Math.ceil(subtotal * 0.75);
        if (ids.contains("dampen")) subtotal = (int)Math.ceil(subtotal * 0.85);
        int total = Math.max(1, subtotal);
        return new Cost(total, formCost, effectCost, augmentCost, elementalCost, complexity, legendaryCost);
    }

    private static int formWeight(String id) {
        return switch (id) {
            case "projectile", "beam", "wave", "cone", "arc" -> 8;
            case "field", "barrier", "wall", "column", "chain" -> 11;
            case "ring", "pulse", "orbit", "tether", "rune" -> 10;
            default -> 8;
        };
    }

    private static int effectWeight(String id) {
        return switch (id) {
            case "healing", "enhancement", "resistance", "absorption", "regeneration",
                 "teleportation", "summoning", "portal", "transmutation", "duplication" -> 16;
            case "explosion", "binding", "weather", "repair", "harvest", "growth",
                 "reflection", "storage", "decoy" -> 13;
            case "invisibility", "disguise", "levitation", "dash", "haste", "fatigue",
                 "silence", "recall", "water_walking", "glide", "fortune" -> 11;
            default -> 8;
        };
    }

    private static int augmentWeight(String id) {
        return switch (id) {
            case "multiple_targets", "self_and_nearby", "area" -> 8;
            case "hostile", "ally", "projectile_target" -> 4;
            case "single_target", "self", "ground", "block" -> 2;
            case "amplify", "repeat", "pierce", "ricochet", "split" -> 9;
            case "accelerate", "homing", "expand", "extend", "linger" -> 7;
            case "efficiency" -> 4;
            case "dampen", "stabilize" -> 3;
            default -> 5;
        };
    }

    private static int elementWeight(String id) {
        return switch (id) {
            case "time", "space", "gravity", "lightning", "sun", "moon" -> 8;
            case "magma", "crystal", "metal", "poison" -> 7;
            default -> 5;
        };
    }
}
