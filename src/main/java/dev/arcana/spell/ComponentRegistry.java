package dev.arcana.spell;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComponentRegistry {
    private static final Map<String, SpellComponent> COMPONENTS = new LinkedHashMap<>();
    private static boolean bootstrapped;
    private ComponentRegistry() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        register("projectile", ComponentCategory.DELIVERY, "Projectile", "Travels toward a point or target.");
        register("beam", ComponentCategory.DELIVERY, "Beam", "Creates a focused line of magical energy.");
        register("wave", ComponentCategory.DELIVERY, "Wave", "Sends an expanding or travelling wave.");
        register("barrier", ComponentCategory.DELIVERY, "Barrier", "Creates a defensive magical structure.");
        register("field", ComponentCategory.DELIVERY, "Field", "Creates an effect over a defined area.");
        register("fire", ComponentCategory.ELEMENT, "Fire", "Adds heat and fire-based behavior.");
        register("water", ComponentCategory.ELEMENT, "Water", "Adds fluid and water-based behavior.");
        register("wind", ComponentCategory.ELEMENT, "Wind", "Adds air movement and force.");
        register("earth", ComponentCategory.ELEMENT, "Earth", "Adds stone, soil, and earth shaping.");
        register("lightning", ComponentCategory.ELEMENT, "Lightning", "Adds electrical energy.");
        register("ice", ComponentCategory.ELEMENT, "Ice", "Adds cold and freezing behavior.");
        register("single_target", ComponentCategory.TARGET, "Single Target", "Focuses on one target.");
        register("multiple_targets", ComponentCategory.TARGET, "Multiple Targets", "Can affect several targets.");
        register("self", ComponentCategory.TARGET, "Self", "Affects the caster.");
        register("area", ComponentCategory.TARGET, "Area", "Affects a defined area.");
        register("explosion", ComponentCategory.EFFECT, "Explosion", "Creates a burst of force.");
        register("healing", ComponentCategory.EFFECT, "Healing", "Restores health.");
        register("summoning", ComponentCategory.EFFECT, "Summoning", "Calls a temporary magical helper.");
        register("enhancement", ComponentCategory.EFFECT, "Enhancement", "Improves a target's capability.");
        register("teleportation", ComponentCategory.EFFECT, "Teleportation", "Moves the caster or a target.");
        register("binding", ComponentCategory.EFFECT, "Binding", "Restrains or holds a target.");
        bootstrapped = true;
    }

    private static void register(String id, ComponentCategory category, String name, String description) {
        COMPONENTS.put(id, new SpellComponent(id, category, name, description));
    }
    public static SpellComponent get(String id) { return COMPONENTS.get(id); }
    public static Collection<SpellComponent> all() { return Collections.unmodifiableCollection(COMPONENTS.values()); }
}
