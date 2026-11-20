package dev.arcana.spell;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered, compiled representation of a spell.
 *
 * The first delivery glyph is the Form. Later delivery, element, and target
 * glyphs become Augments attached to the nearest preceding Form or Effect.
 * Effects remain ordered payload steps and execute in sequence.
 */
public final class SpellProgram {
    private SpellProgram() {}

    public enum Role { FORM, EFFECT, AUGMENT }

    public record Step(int index, String componentId, Role role, int modifiesStep,
                       ComponentCategory sourceCategory) {}

    public record Compilation(List<Step> steps, List<String> issues) {
        public Compilation {
            steps = List.copyOf(steps);
            issues = List.copyOf(issues);
        }
        public boolean valid() { return issues.isEmpty(); }
        public long effectCount() {
            return steps.stream().filter(step -> step.role() == Role.EFFECT).count();
        }
    }

    public static Compilation compile(List<String> orderedIds) {
        List<Step> steps = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        if (orderedIds == null || orderedIds.isEmpty()) {
            return new Compilation(steps, List.of("Add a Form to define how the spell is delivered."));
        }

        int formIndex = -1;
        int lastPayloadIndex = -1;
        for (String id : orderedIds) {
            SpellComponent component = ComponentRegistry.get(id);
            if (component == null) {
                issues.add("Unknown glyph: " + id + ".");
                continue;
            }

            int index = steps.size();
            switch (component.category()) {
                case DELIVERY -> {
                    if (formIndex < 0) {
                        formIndex = index;
                        steps.add(new Step(index, id, Role.FORM, -1, component.category()));
                    } else {
                        steps.add(new Step(index, id, Role.AUGMENT, formIndex, component.category()));
                    }
                }
                case EFFECT -> {
                    if (formIndex < 0) {
                        issues.add("A spell must begin with a Form before adding Effects.");
                        steps.add(new Step(index, id, Role.EFFECT, -1, component.category()));
                    } else {
                        steps.add(new Step(index, id, Role.EFFECT, -1, component.category()));
                        lastPayloadIndex = index;
                    }
                }
                case ELEMENT, TARGET, AUGMENT -> {
                    if (formIndex < 0) {
                        issues.add(component.displayName() + " must come after a Form.");
                        steps.add(new Step(index, id, Role.AUGMENT, -1, component.category()));
                    } else {
                        int target = lastPayloadIndex >= 0 ? lastPayloadIndex : formIndex;
                        steps.add(new Step(index, id, Role.AUGMENT, target, component.category()));
                    }
                }
            }
        }

        if (formIndex < 0) issues.add("Choose a Form first (for example, Projectile, Beam, or Field).");

        return new Compilation(steps, issues);
    }
}
