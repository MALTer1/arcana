package dev.arcana.spell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /** Distinct components from the same category are intentionally allowed. */
    /** Preserve the exact glyph order chosen by the player. */
    public List<String> selectedIds() {
        return List.copyOf(selected);
    }

    public boolean isValid() { return !selected.isEmpty() && validationIssues().isEmpty(); }

    public List<String> validationIssues() {
        return SpellIntelligence.analyze("Unnamed Spell", selectedIds()).issues();
    }

    public String behavior() {
        return SpellIntelligence.analyze("Unnamed Spell", selectedIds()).summary();
    }

    public List<String> preview() {
        List<String> lines = new ArrayList<>();
        if (selected.isEmpty()) {
            lines.add("Your spell draft is empty. Use /arcana select <component>.");
            return lines;
        }

        List<SpellComponent> components = selected.stream().map(ComponentRegistry::get).toList();
        SpellIntelligence.Analysis analysis = SpellIntelligence.analyze("Unnamed Spell", selectedIds());
        lines.add("=== MAGIC WRITER PREVIEW ===");
        lines.add("Components (" + components.size() + "/5): " +
            components.stream().map(SpellComponent::displayName).collect(Collectors.joining(" + ")));
        if (!analysis.issues().isEmpty()) {
            lines.add("INVALID COMBINATION:");
            analysis.issues().forEach(issue -> lines.add("- " + issue));
            return lines;
        }
        lines.add("Status: READY TO ENGRAVE");
        lines.add("Behavior: " + analysis.summary());
        analysis.warnings().forEach(warning -> lines.add("Intelligence: " + warning));
        lines.add("Use /arcana engrave <name> to save this spell in your spellbook.");
        return lines;
    }
}
