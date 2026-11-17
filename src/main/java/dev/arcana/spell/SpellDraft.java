package dev.arcana.spell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SpellDraft {
    public static final int MAX_COMPONENTS = 5;
    private final List<String> selected = new ArrayList<>();
    public record AddResult(boolean success, String message) {}

    public AddResult add(String id) {
        SpellComponent component = ComponentRegistry.get(id);
        if (component == null) return new AddResult(false, "Unknown component. Use /arcana components.");
        if (selected.contains(id)) return new AddResult(false, "That component is already selected.");
        if (selected.size() >= MAX_COMPONENTS) return new AddResult(false, "Your grimoire draft limit is five components.");
        selected.add(id);
        return new AddResult(true, "Added " + component.displayName() + " (" + selected.size() + "/5).");
    }

    public boolean remove(String id) { return selected.remove(id); }
    public void clear() { selected.clear(); }

    /** Returns component IDs in the Magic Writer's fixed category order. */
    public List<String> selectedIds() {
        return selected.stream()
            .map(ComponentRegistry::get)
            .sorted(Comparator.comparingInt((SpellComponent c) -> c.category().ordinal())
                .thenComparing(SpellComponent::displayName))
            .map(SpellComponent::id)
            .toList();
    }

    public boolean isValid() { return !selected.isEmpty() && validationIssues().isEmpty(); }

    public List<String> validationIssues() {
        return validate(selected.stream().map(ComponentRegistry::get).toList());
    }

    public String behavior() {
        return describe(selected.stream().map(ComponentRegistry::get)
            .sorted(Comparator.comparingInt((SpellComponent c) -> c.category().ordinal())
                .thenComparing(SpellComponent::displayName))
            .toList());
    }

    public List<String> preview() {
        List<String> lines = new ArrayList<>();
        if (selected.isEmpty()) {
            lines.add("Your spell draft is empty. Use /arcana select <component>.");
            return lines;
        }

        List<SpellComponent> components = selected.stream().map(ComponentRegistry::get)
            .sorted(Comparator.comparingInt((SpellComponent c) -> c.category().ordinal())
                .thenComparing(SpellComponent::displayName))
            .toList();
        List<String> issues = validationIssues();
        lines.add("=== MAGIC WRITER PREVIEW ===");
        lines.add("Components (" + components.size() + "/5): " +
            components.stream().map(SpellComponent::displayName).collect(Collectors.joining(" + ")));
        if (!issues.isEmpty()) {
            lines.add("INVALID COMBINATION:");
            issues.forEach(issue -> lines.add("- " + issue));
            return lines;
        }
        lines.add("Status: READY TO ENGRAVE");
        lines.add("Behavior: " + describe(components));
        lines.add("Use /arcana engrave <name> to save this spell in your spellbook.");
        return lines;
    }

    private List<String> validate(List<SpellComponent> components) {
        List<String> issues = new ArrayList<>();
        if (components.size() < 2) issues.add("Choose at least two components.");
        // Components are composable: a spell may combine several deliveries,
        // targets, and elements. Duplicate IDs are still rejected by add().
        boolean healing = selected.contains("healing");
        boolean explosion = selected.contains("explosion");
        boolean teleport = selected.contains("teleportation");
        boolean binding = selected.contains("binding");
        boolean summoning = selected.contains("summoning");
        if (healing && explosion) issues.add("Healing and Explosion cannot be combined.");
        if (teleport && (selected.contains("projectile") || selected.contains("beam")))
            issues.add("Teleportation cannot use Projectile or Beam delivery.");
        if (summoning && selected.contains("self")) issues.add("Summoning cannot use Self as its target.");
        if (binding && selected.contains("self")) issues.add("Binding cannot target the caster.");
        return issues;
    }

    private String describe(List<SpellComponent> components) {
        String elements = components.stream().filter(c -> c.category() == ComponentCategory.ELEMENT)
            .map(SpellComponent::displayName).collect(Collectors.joining(" + "));
        String delivery = components.stream().filter(c -> c.category() == ComponentCategory.DELIVERY)
            .map(SpellComponent::displayName).collect(Collectors.joining(" + "));
        if (delivery.isBlank()) delivery = "Direct magical effect";
        String target = components.stream().filter(c -> c.category() == ComponentCategory.TARGET)
            .map(SpellComponent::displayName).collect(Collectors.joining(" + "));
        if (target.isBlank()) target = "an unspecified target";
        String effects = components.stream().filter(c -> c.category() == ComponentCategory.EFFECT)
            .map(SpellComponent::displayName).collect(Collectors.joining(" + "));
        return delivery + (elements.isBlank() ? "" : " carrying " + elements) + " affecting " + target +
            (effects.isBlank() ? "" : " with " + effects) + ".";
    }
}
