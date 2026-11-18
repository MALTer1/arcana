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
        register("night_vision", ComponentCategory.EFFECT, "Night Vision", "Lets the caster see clearly in darkness.");
        register("water_breathing", ComponentCategory.EFFECT, "Water Breathing", "Allows the caster to breathe underwater.");
        register("slow_falling", ComponentCategory.EFFECT, "Slow Falling", "Softens falls for a short time.");
        register("arc", ComponentCategory.DELIVERY, "Arc", "Creates a curved magical path.");
        register("chain", ComponentCategory.DELIVERY, "Chain", "Links a sequence of delivery points.");
        register("cone", ComponentCategory.DELIVERY, "Cone", "Shapes an effect into a forward cone.");
        register("ring", ComponentCategory.DELIVERY, "Ring", "Creates a circular expanding pattern.");
        register("pulse", ComponentCategory.DELIVERY, "Pulse", "Releases a short radial pulse.");
        register("orbit", ComponentCategory.DELIVERY, "Orbit", "Circles an effect around a point.");
        register("tether", ComponentCategory.DELIVERY, "Tether", "Connects two points with a magical link.");
        register("rune", ComponentCategory.DELIVERY, "Rune", "Places a persistent magical mark.");
        register("wall", ComponentCategory.DELIVERY, "Wall", "Creates a broad upright plane.");
        register("column", ComponentCategory.DELIVERY, "Column", "Creates a vertical column of energy.");
        register("light", ComponentCategory.ELEMENT, "Light", "Adds illumination and radiance.");
        register("shadow", ComponentCategory.ELEMENT, "Shadow", "Adds darkness and concealment.");
        register("nature", ComponentCategory.ELEMENT, "Nature", "Adds plant and natural growth themes.");
        register("metal", ComponentCategory.ELEMENT, "Metal", "Adds metallic shaping and properties.");
        register("crystal", ComponentCategory.ELEMENT, "Crystal", "Adds crystalline structures and resonance.");
        register("magma", ComponentCategory.ELEMENT, "Magma", "Adds molten stone properties.");
        register("smoke", ComponentCategory.ELEMENT, "Smoke", "Adds drifting smoke and obscuring mist.");
        register("sound", ComponentCategory.ELEMENT, "Sound", "Adds vibration and audible resonance.");
        register("gravity", ComponentCategory.ELEMENT, "Gravity", "Adds gravitational influence.");
        register("time", ComponentCategory.ELEMENT, "Time", "Adds temporal distortion themes.");
        register("space", ComponentCategory.ELEMENT, "Space", "Adds spatial distortion themes.");
        register("poison", ComponentCategory.ELEMENT, "Poison", "Adds a fictional alchemical toxin theme.");
        register("sand", ComponentCategory.ELEMENT, "Sand", "Adds granular earth and dust.");
        register("wood", ComponentCategory.ELEMENT, "Wood", "Adds wood and living timber.");
        register("moon", ComponentCategory.ELEMENT, "Moon", "Adds cool lunar energy.");
        register("sun", ComponentCategory.ELEMENT, "Sun", "Adds solar warmth and brilliance.");
        register("self_and_nearby", ComponentCategory.TARGET, "Self and Nearby", "Affects the caster and nearby allies.");
        register("ally", ComponentCategory.TARGET, "Ally", "Selects a friendly target.");
        register("hostile", ComponentCategory.TARGET, "Hostile", "Selects a hostile target.");
        register("ground", ComponentCategory.TARGET, "Ground", "Selects a position on the ground.");
        register("block", ComponentCategory.TARGET, "Block", "Selects a block or block face.");
        register("projectile_target", ComponentCategory.TARGET, "Projectile Target", "Selects a moving projectile.");
        register("illumination", ComponentCategory.EFFECT, "Illumination", "Creates a light source.");
        register("invisibility", ComponentCategory.EFFECT, "Invisibility", "Conceals the affected entity.");
        register("disguise", ComponentCategory.EFFECT, "Disguise", "Changes an entity's visible appearance.");
        register("levitation", ComponentCategory.EFFECT, "Levitation", "Allows controlled upward movement.");
        register("dash", ComponentCategory.EFFECT, "Dash", "Provides a short burst of movement.");
        register("haste", ComponentCategory.EFFECT, "Haste", "Improves work and action speed.");
        register("fatigue", ComponentCategory.EFFECT, "Fatigue", "Reduces work and action speed.");
        register("silence", ComponentCategory.EFFECT, "Silence", "Dampens nearby sounds.");
        register("echo", ComponentCategory.EFFECT, "Echo", "Repeats a magical signal after a delay.");
        register("reveal", ComponentCategory.EFFECT, "Reveal", "Highlights hidden or marked objects.");
        register("tracking", ComponentCategory.EFFECT, "Tracking", "Marks a target for easier locating.");
        register("waypoint", ComponentCategory.EFFECT, "Waypoint", "Creates a temporary navigation marker.");
        register("recall", ComponentCategory.EFFECT, "Recall", "Returns the caster to a recorded safe point.");
        register("anchor", ComponentCategory.EFFECT, "Anchor", "Prevents forced movement.");
        register("float", ComponentCategory.EFFECT, "Float", "Allows gentle movement across a surface.");
        register("water_walking", ComponentCategory.EFFECT, "Water Walking", "Allows walking across liquid surfaces.");
        register("climb", ComponentCategory.EFFECT, "Climb", "Improves movement on climbable surfaces.");
        register("jump", ComponentCategory.EFFECT, "Jump", "Improves jumping ability.");
        register("sprint", ComponentCategory.EFFECT, "Sprint", "Improves running movement.");
        register("glide", ComponentCategory.EFFECT, "Glide", "Allows controlled horizontal travel while falling.");
        register("temperature", ComponentCategory.EFFECT, "Temperature", "Changes the local temperature.");
        register("weather", ComponentCategory.EFFECT, "Weather", "Influences local weather conditions.");
        register("growth", ComponentCategory.EFFECT, "Growth", "Encourages nearby plants to grow.");
        register("harvest", ComponentCategory.EFFECT, "Harvest", "Collects mature nearby crops.");
        register("repair", ComponentCategory.EFFECT, "Repair", "Repairs a damaged block or item.");
        register("cleanse", ComponentCategory.EFFECT, "Cleanse", "Removes selected negative status effects.");
        register("purify", ComponentCategory.EFFECT, "Purify", "Purifies nearby water or a chosen fluid.");
        register("ward", ComponentCategory.EFFECT, "Ward", "Creates a protective ward.");
        register("reflection", ComponentCategory.EFFECT, "Reflection", "Reflects selected incoming magical effects.");
        register("resistance", ComponentCategory.EFFECT, "Resistance", "Improves resistance to environmental hazards.");
        register("absorption", ComponentCategory.EFFECT, "Absorption", "Creates a temporary protective buffer.");
        register("regeneration", ComponentCategory.EFFECT, "Regeneration", "Gradually restores health over time.");
        register("stamina", ComponentCategory.EFFECT, "Stamina", "Improves endurance for a short time.");
        register("focus", ComponentCategory.EFFECT, "Focus", "Improves concentration and precision.");
        register("fortune", ComponentCategory.EFFECT, "Fortune", "Temporarily improves luck in suitable interactions.");
        register("magnetism", ComponentCategory.EFFECT, "Magnetism", "Attracts nearby loose items.");
        register("repulsion", ComponentCategory.EFFECT, "Repulsion", "Pushes nearby loose objects away.");
        register("storage", ComponentCategory.EFFECT, "Storage", "Stores a small set of selected items magically.");
        register("transmutation", ComponentCategory.EFFECT, "Transmutation", "Changes one supported material into another.");
        register("duplication", ComponentCategory.EFFECT, "Duplication", "Creates a temporary copy of a non-living object.");
        register("decoy", ComponentCategory.EFFECT, "Decoy", "Creates a temporary visual decoy.");
        register("barrier_reinforcement", ComponentCategory.EFFECT, "Barrier Reinforcement", "Strengthens a nearby magical barrier.");
        register("portal", ComponentCategory.EFFECT, "Portal", "Creates a temporary linked passage between marked points.");
        register("dimension_sense", ComponentCategory.EFFECT, "Dimension Sense", "Senses nearby dimensional boundaries.");
        register("memory", ComponentCategory.EFFECT, "Memory", "Records a short visual snapshot for later review.");
        register("alarm", ComponentCategory.EFFECT, "Alarm", "Creates a signal when a chosen condition is met.");
        bootstrapped = true;
    }

    private static void register(String id, ComponentCategory category, String name, String description) {
        COMPONENTS.put(id, new SpellComponent(id, category, name, description));
    }
    public static SpellComponent get(String id) { return COMPONENTS.get(id); }
    public static Collection<SpellComponent> all() { return Collections.unmodifiableCollection(COMPONENTS.values()); }
}
