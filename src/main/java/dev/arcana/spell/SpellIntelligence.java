package dev.arcana.spell;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Local spell interpreter. It combines component roles, elemental reactions,
 * compatibility checks, and name-based intent hints into one spell analysis.
 * A spell name can produce a warning, but can never grant a missing ability.
 */
public final class SpellIntelligence {
    private SpellIntelligence() {}

    public record Analysis(
        String summary,
        List<String> issues,
        List<String> warnings,
        Map<ComponentCategory, Integer> categoryCounts,
        List<ElementalReactionEngine.Reaction> reactions
    ) {
        public boolean isValid() { return issues.isEmpty(); }
    }

    public static Analysis analyze(String spellName, List<String> componentIds) {
        List<SpellComponent> components = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<ComponentCategory, Integer> counts = new EnumMap<>(ComponentCategory.class);
        Set<String> ids = new LinkedHashSet<>();

        for (String id : componentIds) {
            SpellComponent component = ComponentRegistry.get(id);
            if (component == null) {
                issues.add("Unknown component: " + id + ".");
                continue;
            }
            if (!ids.add(id)) {
                issues.add("The component " + component.displayName() + " is listed more than once.");
                continue;
            }
            components.add(component);
            counts.merge(component.category(), 1, Integer::sum);
        }

        if (components.size() < 2) issues.add("Choose at least two glyphs.");

        SpellProgram.Compilation program = SpellProgram.compile(
            components.stream().map(SpellComponent::id).toList());
        issues.addAll(program.issues());

        Set<String> selected = components.stream().map(SpellComponent::id).collect(Collectors.toSet());
        Set<String> movingForms = Set.of("projectile", "arc", "chain", "beam", "wave");
        Set<String> areaForms = Set.of("field", "wave", "cone", "ring", "pulse", "orbit", "rune", "wall", "column");
        boolean hasMovingForm = selected.stream().anyMatch(movingForms::contains);
        boolean hasAreaForm = selected.stream().anyMatch(areaForms::contains);
        if ((selected.contains("pierce") || selected.contains("homing")
            || selected.contains("accelerate") || selected.contains("ricochet")) && !hasMovingForm) {
            issues.add("Pierce, Homing, Accelerate, and Ricochet need a moving Form such as Projectile, Arc, Chain, Beam, or Wave.");
        }
        if (selected.contains("expand") && !hasAreaForm) {
            issues.add("Expand needs an area-capable Form such as Field, Wave, Cone, Ring, Pulse, or Rune.");
        }
        if (selected.contains("amplify") && selected.contains("dampen")) {
            issues.add("Amplify and Dampen oppose each other; remove one so the spell has a clear intensity.");
        }
        if (selected.contains("self") && selected.contains("hostile")) {
            issues.add("Self targeting cannot be combined with the Hostile target filter.");
        }
        if (selected.contains("healing") && selected.contains("explosion"))
            warnings.add("Healing + Explosion is interpreted as a restorative shockwave: allies are healed while hostile targets receive the burst.");
        if (selected.contains("teleportation") &&
            (selected.contains("projectile") || selected.contains("beam")))
            warnings.add("Teleportation is delivered through the selected moving shape; valid targets are moved to the caster's forward destination.");
        if (selected.contains("summoning") && selected.contains("self"))
            warnings.add("Self-targeted summoning places the helper beside the caster.");
        if (selected.contains("binding") && selected.contains("self"))
            warnings.add("Self-targeted binding acts as a temporary self-anchor.");
        if (selected.contains("ally") && selected.contains("hostile"))
            issues.add("Ally and Hostile are mutually exclusive target filters.");
        if (selected.contains("single_target") && selected.contains("multiple_targets"))
            warnings.add("Single Target and Multiple Targets are both selected; Multiple Targets takes precedence.");

        for (Map.Entry<ComponentCategory, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                warnings.add(entry.getValue() + " " + entry.getKey().displayName().toLowerCase()
                    + " components will be composed together.");
            }
        }

        List<ElementalReactionEngine.Reaction> reactions =
            ElementalReactionEngine.resolve(components.stream().map(SpellComponent::id).toList());
        for (ElementalReactionEngine.Reaction reaction : reactions) {
            warnings.add("Elemental reaction: " + reaction.name() + " — " + reaction.description());
        }

        SpellCompositionEngine.Plan composition = SpellCompositionEngine.compose(
            components.stream().map(SpellComponent::id).toList());
        for (String interaction : composition.interactions()) {
            warnings.add("Composition: " + interaction + ".");
        }
        for (String explanation : composition.explanations()) {
            warnings.add("Spell reasoning: " + explanation);
        }

        checkNameIntent(spellName, selected, warnings);

        String summary = buildSummary(components, reactions, composition);
        return new Analysis(summary, List.copyOf(issues), List.copyOf(warnings),
            Map.copyOf(counts), reactions);
    }

    private static void checkNameIntent(String name, Set<String> selected, List<String> warnings) {
        if (name == null || name.isBlank()) return;
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        checkHint(normalized, selected, warnings, new String[]{"heal", "mend", "restore", "cure"}, "healing",
            "The name suggests healing, but the spell has no Healing component.");
        checkHint(normalized, selected, warnings, new String[]{"fire", "flame", "blaze", "inferno"}, "fire",
            "The name suggests fire, but the spell has no Fire element.");
        checkHint(normalized, selected, warnings, new String[]{"ice", "frost", "freeze"}, "ice",
            "The name suggests ice, but the spell has no Ice element.");
        checkHint(normalized, selected, warnings, new String[]{"storm", "thunder", "shock"}, "lightning",
            "The name suggests lightning, but the spell has no Lightning element.");
        checkHint(normalized, selected, warnings, new String[]{"teleport", "blink", "warp"}, "teleportation",
            "The name suggests teleportation, but the spell has no Teleportation effect.");
        checkHint(normalized, selected, warnings, new String[]{"summon", "calling"}, "summoning",
            "The name suggests summoning, but the spell has no Summoning effect.");
    }

    private static void checkHint(String name, Set<String> selected, List<String> warnings,
                                  String[] words, String required, String warning) {
        for (String word : words) {
            if (name.contains(word) && !selected.contains(required)) {
                warnings.add(warning + " The name does not add this ability.");
                return;
            }
        }
    }

    private static String buildSummary(List<SpellComponent> components,
                                       List<ElementalReactionEngine.Reaction> reactions,
                                       SpellCompositionEngine.Plan composition) {
        Map<ComponentCategory, List<String>> grouped = new LinkedHashMap<>();
        for (SpellComponent component : components) {
            grouped.computeIfAbsent(component.category(), ignored -> new ArrayList<>())
                .add(component.displayName());
        }

        String delivery = join(grouped.get(ComponentCategory.DELIVERY));
        if (delivery.isBlank()) delivery = "Direct magical delivery";
        String targets = join(grouped.get(ComponentCategory.TARGET));
        if (targets.isBlank()) targets = "the selected target";

        List<String> elements = new ArrayList<>();
        for (ElementalReactionEngine.Reaction reaction : reactions) elements.add(reaction.name());
        Set<String> consumed = ElementalReactionEngine.consumedElements(reactions);
        for (SpellComponent component : components) {
            if (component.category() == ComponentCategory.ELEMENT && !consumed.contains(component.id()))
                elements.add(component.displayName());
        }

        String effects = join(grouped.get(ComponentCategory.EFFECT));
        StringBuilder result = new StringBuilder(delivery).append(" affecting ").append(targets);
        if (!elements.isEmpty()) result.append(" with ").append(String.join(" + ", elements));
        if (!effects.isBlank()) result.append("; effects: ").append(effects);
        result.append(".");
        if (!composition.interactions().isEmpty()) {
            result.append(" Combined behavior: ")
                .append(String.join(", ", composition.interactions())).append(".");
        }
        return result.toString();
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(" + ", values);
    }
}
