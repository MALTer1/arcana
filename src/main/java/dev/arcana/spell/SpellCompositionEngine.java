package dev.arcana.spell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic, whole-spell composition layer.
 *
 * Components contribute semantic properties. Rules combine those properties
 * into spell-wide modifiers and interactions before the caster executes the
 * result. This deliberately contains no Minecraft classes so the rules can be
 * tested independently and expanded without coupling them to entity code.
 */
public final class SpellCompositionEngine {
    private SpellCompositionEngine() {}

    public enum Property {
        MOVING_DELIVERY, LINEAR_DELIVERY, AREA_DELIVERY, DEFENSIVE_DELIVERY,
        HEAT, FLUID, AIRFLOW, EARTH, ELECTRICITY, COLD, RADIANCE, DARKNESS,
        NATURE, METALLIC, CRYSTALLINE, VIBRATION, GRAVITY, TEMPORAL, SPATIAL,
        RESTRAINT, MOBILITY, PROTECTION, RESTORATION, CONCEALMENT, CONTROL,
        PROJECTILE_IMPACT, REPEATING, PERSISTENT
    }

    public record Plan(
        Set<Property> properties,
        double travelSpeedMultiplier,
        double rangeMultiplier,
        double areaMultiplier,
        double durationMultiplier,
        double impactDamageBonus,
        int stunTicks,
        int slowTicks,
        List<String> interactions,
        List<String> explanations
    ) {
        public Plan {
            properties = Set.copyOf(properties);
            interactions = List.copyOf(interactions);
            explanations = List.copyOf(explanations);
        }
    }

    public static Plan compose(List<String> componentIds) {
        Set<String> ids = new LinkedHashSet<>(componentIds);
        Set<Property> properties = new LinkedHashSet<>();
        List<String> interactions = new ArrayList<>();
        List<String> explanations = new ArrayList<>();

        for (String id : ids) properties.addAll(propertiesFor(id));

        double speed = 1.0;
        double range = 1.0;
        double area = 1.0;
        double duration = 1.0;
        double damage = 0.0;
        int stun = 0;
        int slow = 0;

        // Delivery + element interactions.
        if (ids.contains("projectile") && ids.contains("lightning")) {
            speed *= 1.35;
            range *= 1.20;
            interactions.add("Conductive projectile");
            explanations.add("Lightning energizes the projectile, increasing its planned travel speed and reach.");
        }
        if (ids.contains("projectile") && ids.contains("wind")) {
            speed *= 1.25;
            range *= 1.10;
            interactions.add("Wind-accelerated projectile");
            explanations.add("Wind accelerates the projectile and carries it farther.");
        }
        if (ids.contains("projectile") && ids.contains("gravity")) {
            interactions.add("Gravity-shaped trajectory");
            explanations.add("Gravity can bend the projectile's path instead of acting as a separate impact effect.");
        }
        if (ids.contains("beam") && ids.contains("lightning")) {
            range *= 1.15;
            interactions.add("Conductive beam");
            explanations.add("Lightning reinforces the beam's reach.");
        }
        if ((ids.contains("field") || ids.contains("wave") || ids.contains("cone"))
            && ids.contains("wind") && ids.contains("fire")) {
            area *= 1.30;
            interactions.add("Wind-fed spread");
            explanations.add("Wind helps the fire spread through the delivery shape, increasing its coverage.");
        }
        if ((ids.contains("field") || ids.contains("wave") || ids.contains("cone"))
            && ids.contains("smoke")) {
            area *= 1.20;
            duration *= 1.15;
            interactions.add("Dispersing smoke");
            explanations.add("Smoke disperses through the delivery area and lingers slightly longer.");
        }
        if ((ids.contains("ring") || ids.contains("pulse") || ids.contains("orbit"))
            && ids.contains("lightning")) {
            area *= 1.15;
            interactions.add("Radial electrical discharge");
            explanations.add("Lightning follows the radial delivery pattern.");
        }

        // Element + effect interactions.
        if (ids.contains("lightning") && ids.contains("binding")) {
            stun = 50;
            slow = 80;
            interactions.add("Electrified binding");
            explanations.add("Binding gives the electrical impact a way to hold its target; the combined effect briefly stuns and restrains.");
        }
        if (ids.contains("ice") && ids.contains("binding")) {
            slow = Math.max(slow, 120);
            interactions.add("Frozen restraint");
            explanations.add("Ice hardens the binding, making the restraint last longer.");
        }
        if (ids.contains("water") && ids.contains("lightning") && !ids.contains("fire")) {
            damage += 1.0;
            interactions.add("Conductive medium");
            explanations.add("Water provides a medium for the electrical effect to spread through.");
        }
        if (ids.contains("crystal") && ids.contains("lightning")) {
            duration *= 1.20;
            interactions.add("Resonant crystal");
            explanations.add("Crystal resonance sustains the electrical energy.");
        }
        if (ids.contains("metal") && ids.contains("lightning")) {
            damage += 1.0;
            interactions.add("Metal conduction");
            explanations.add("Metal conducts the electrical component, strengthening its impact.");
        }
        if (ids.contains("earth") && ids.contains("water")) {
            area *= 1.10;
            interactions.add("Saturated earth");
            explanations.add("Water saturates the earth component, allowing it to spread or settle as a heavier material.");
        }
        if (ids.contains("moon") && ids.contains("shadow")) {
            duration *= 1.20;
            interactions.add("Lunar concealment");
            explanations.add("Moon energy stabilizes the shadow effect.");
        }
        if (ids.contains("sun") && ids.contains("light")) {
            area *= 1.10;
            interactions.add("Solar radiance");
            explanations.add("Solar energy amplifies the spell's radiance.");
        }
        if (ids.contains("sound") && (ids.contains("field") || ids.contains("wave") || ids.contains("pulse"))) {
            area *= 1.20;
            interactions.add("Resonant propagation");
            explanations.add("Sound propagates through the selected area pattern.");
        }
        if (ids.contains("gravity") && ids.contains("levitation")) {
            duration *= 1.20;
            interactions.add("Gravitational suspension");
            explanations.add("Gravity control stabilizes levitation.");
        }
        if (ids.contains("nature") && ids.contains("water")) {
            duration *= 1.15;
            interactions.add("Nourishing growth");
            explanations.add("Water supports the nature component, extending its restorative or growth behavior.");
        }
        if (ids.contains("fire") && ids.contains("ice")) {
            interactions.add("Thermal conflict");
            explanations.add("Fire and ice oppose each other; the spell should resolve them as a controlled thermal clash rather than blindly stacking both.");
        }

        // Multiple components in a category are composed, not rejected.
        long deliveryCount = ids.stream().filter(id -> isDelivery(id)).count();
        long elementCount = ids.stream().filter(id -> isElement(id)).count();
        long effectCount = ids.stream().filter(id -> isEffect(id)).count();
        if (deliveryCount > 1) {
            interactions.add("Composite delivery");
            explanations.add("Multiple delivery components contribute shape and movement properties to one combined delivery plan.");
        }
        if (elementCount > 1) {
            interactions.add("Mixed elements");
            explanations.add("All selected elements are considered together; known reactions are resolved before leftover properties are applied.");
        }
        if (effectCount > 1) {
            interactions.add("Layered effects");
            explanations.add("Effect components are layered and can modify the same outcome when their properties are compatible.");
        }

        // Clamp accumulated modifiers so adding many compatible components
        // cannot create unbounded range, coverage, duration, or damage.
        speed = clamp(speed, 0.50, 2.0);
        range = clamp(range, 0.50, 1.75);
        area = clamp(area, 0.50, 2.0);
        duration = clamp(duration, 0.50, 2.0);
        damage = clamp(damage, 0.0, 4.0);
        stun = Math.min(stun, 100);
        slow = Math.min(slow, 200);

        if (explanations.isEmpty()) {
            explanations.add("The selected components contribute their individual properties. No special cross-component interaction was triggered.");
        }

        return new Plan(properties, speed, range, area, duration, damage, stun, slow,
            interactions, explanations);
    }

    private static Set<Property> propertiesFor(String id) {
        Set<Property> result = new LinkedHashSet<>();
        switch (id) {
            case "projectile", "arc", "chain", "tether" -> result.add(Property.MOVING_DELIVERY);
            case "beam", "wall", "column" -> result.add(Property.LINEAR_DELIVERY);
            case "wave", "cone", "ring", "pulse", "orbit", "field", "rune" -> result.add(Property.AREA_DELIVERY);
            case "barrier" -> result.add(Property.DEFENSIVE_DELIVERY);
            case "fire", "magma", "sun" -> result.add(Property.HEAT);
            case "water" -> result.add(Property.FLUID);
            case "wind" -> result.add(Property.AIRFLOW);
            case "smoke" -> { result.add(Property.AIRFLOW); result.add(Property.CONCEALMENT); }
            case "earth", "sand" -> result.add(Property.EARTH);
            case "lightning" -> result.add(Property.ELECTRICITY);
            case "ice" -> result.add(Property.COLD);
            case "light" -> result.add(Property.RADIANCE);
            case "shadow" -> result.add(Property.DARKNESS);
            case "nature", "wood" -> result.add(Property.NATURE);
            case "metal" -> result.add(Property.METALLIC);
            case "crystal" -> result.add(Property.CRYSTALLINE);
            case "sound" -> result.add(Property.VIBRATION);
            case "gravity" -> result.add(Property.GRAVITY);
            case "time" -> result.add(Property.TEMPORAL);
            case "space", "teleportation", "portal" -> result.add(Property.SPATIAL);
            case "binding", "anchor" -> result.add(Property.RESTRAINT);
            case "dash", "levitation", "float", "water_walking", "climb", "jump", "sprint", "glide" ->
                result.add(Property.MOBILITY);
            case "ward", "resistance", "absorption", "reflection", "barrier_reinforcement" ->
                result.add(Property.PROTECTION);
            case "healing", "regeneration", "cleanse", "purify", "repair" ->
                result.add(Property.RESTORATION);
            case "invisibility", "disguise", "decoy" -> result.add(Property.CONCEALMENT);
            case "explosion", "fatigue", "silence", "repulsion", "poison" -> result.add(Property.CONTROL);
            case "summoning", "echo", "alarm", "memory" -> result.add(Property.REPEATING);
            case "field", "waypoint", "storage" -> result.add(Property.PERSISTENT);
            case "rune" -> { result.add(Property.AREA_DELIVERY); result.add(Property.PERSISTENT); }
            default -> { }
        }
        if (id.equals("projectile") || id.equals("beam") || id.equals("arc")) result.add(Property.PROJECTILE_IMPACT);
        return result;
    }

    private static boolean isDelivery(String id) {
        return Set.of("projectile", "beam", "wave", "barrier", "field", "arc", "chain",
            "cone", "ring", "pulse", "orbit", "tether", "rune", "wall", "column").contains(id);
    }

    private static boolean isElement(String id) {
        return Set.of("fire", "water", "wind", "earth", "lightning", "ice", "light", "shadow",
            "nature", "metal", "crystal", "magma", "smoke", "sound", "gravity", "time",
            "space", "poison", "sand", "wood", "moon", "sun").contains(id);
    }

    private static boolean isEffect(String id) {
        return !isDelivery(id) && !isElement(id) && !Set.of("single_target", "multiple_targets",
            "self", "area", "self_and_nearby", "ally", "hostile", "ground", "block",
            "projectile_target").contains(id);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
