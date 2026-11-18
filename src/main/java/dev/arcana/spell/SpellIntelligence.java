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

        if (components.size() < 2) issues.add("Choose at least two components.");

        Set<String> selected = components.stream().map(SpellComponent::id).collect(Collectors.toSet());
        if (selected.contains("healing") && selected.contains("explosion"))
            issues.add("Healing and Explosion currently have conflicting primary outcomes.");
        if (selected.contains("teleportation") &&
            (selected.contains("projectile") || selected.contains("beam")))
            issues.add("Teleportation cannot currently be combined with Projectile or Beam delivery.");
        if (selected.contains("summoning") && selected.contains("self"))
            issues.add("Summoning cannot use Self as its target.");
        if (selected.contains("binding") && selected.contains("self"))
            issues.add("Binding cannot target the caster.");

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

        checkNameIntent(spellName, selected, warnings);

        String summary = buildSummary(components, reactions);
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
                                       List<ElementalReactionEngine.Reaction> reactions) {
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
        return result.toString();
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(" + ", values);
    }
}
