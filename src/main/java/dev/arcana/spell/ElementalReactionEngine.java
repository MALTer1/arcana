package dev.arcana.spell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves known elemental combinations before individual elements are applied.
 * Reactions are deterministic and can be extended without changing the caster.
 */
public final class ElementalReactionEngine {
    private ElementalReactionEngine() {}

    public record Reaction(String id, String name, String description, Set<String> elements) {}

    private static final List<Reaction> REACTIONS = List.of(
        new Reaction("storm_steam", "Charged Steam",
            "Water, fire, and lightning combine into a charged steam burst.",
            Set.of("fire", "water", "lightning")),
        new Reaction("steam", "Steam",
            "Fire heats water into a hot steam cloud.",
            Set.of("fire", "water")),
        new Reaction("ash_cloud", "Ash Cloud",
            "Smoke and earth form a lingering ash cloud that can be ignited by fire.",
            Set.of("smoke", "earth")),
        new Reaction("charged_water", "Charged Water",
            "Lightning travels through water as an electrical effect.",
            Set.of("water", "lightning")),
        new Reaction("magma", "Magma",
            "Fire melts earth into molten stone.",
            Set.of("fire", "earth")),
        new Reaction("frozen_water", "Frozen Water",
            "Water and ice combine into an intense freezing effect.",
            Set.of("water", "ice")),
        new Reaction("sandstorm", "Sandstorm",
            "Wind carries earth and dust into a blinding sandstorm.",
            Set.of("wind", "earth")),
        new Reaction("wildfire", "Wildfire",
            "Wind feeds and spreads the spell's fire.",
            Set.of("fire", "wind")),
        new Reaction("lunar_tide", "Lunar Tide",
            "Moon energy guides and strengthens water.",
            Set.of("moon", "water")),
        new Reaction("solar_flare", "Solar Flare",
            "Sun energy intensifies the spell's fire and radiance.",
            Set.of("sun", "fire")),
        new Reaction("blizzard", "Blizzard",
            "Wind carries intense cold through a wider area.",
            Set.of("wind", "ice")),
        new Reaction("mist", "Mist",
            "Wind disperses water into a drifting veil.",
            Set.of("wind", "water")),
        new Reaction("mud", "Mud",
            "Water saturates earth into heavy, slowing mud.",
            Set.of("earth", "water")),
        new Reaction("thermal_shock", "Thermal Shock",
            "Fire and ice create a sudden clash of extreme temperatures.",
            Set.of("fire", "ice")),
        new Reaction("eclipse", "Eclipse",
            "Light and shadow interfere, creating a shifting veil.",
            Set.of("light", "shadow")),
        new Reaction("resonance", "Crystal Resonance",
            "Sound vibrates through crystal and amplifies the signal.",
            Set.of("sound", "crystal")),
        new Reaction("verdant_surge", "Verdant Surge",
            "Nature, water, and sunlight combine to nourish living growth.",
            Set.of("nature", "water", "sun")),
        new Reaction("magnetic_arc", "Magnetic Arc",
            "Metal channels lightning into a focused electrical surge.",
            Set.of("metal", "lightning")),
        new Reaction("solar_eclipse", "Solar Eclipse",
            "Moon and sun energies create a temporary dimming effect.",
            Set.of("moon", "sun"))
    );

    /** Returns non-overlapping reactions, preferring the most specific combinations. */
    public static List<Reaction> resolve(List<String> componentIds) {
        Set<String> available = new LinkedHashSet<>(componentIds);
        List<Reaction> matching = REACTIONS.stream()
            .filter(reaction -> available.containsAll(reaction.elements()))
            .sorted((a, b) -> Integer.compare(b.elements().size(), a.elements().size()))
            .toList();

        List<Reaction> resolved = new ArrayList<>();
        for (Reaction candidate : matching) {
            // A specific multi-element reaction replaces its own simpler
            // subsets, but independent overlapping reactions may coexist.
            boolean coveredByMoreSpecific = resolved.stream().anyMatch(selected ->
                selected.elements().containsAll(candidate.elements()));
            if (coveredByMoreSpecific) continue;
            resolved.removeIf(selected -> candidate.elements().containsAll(selected.elements()));
            resolved.add(candidate);
        }
        return List.copyOf(resolved);
    }

    public static Set<String> consumedElements(List<Reaction> reactions) {
        Set<String> consumed = new LinkedHashSet<>();
        for (Reaction reaction : reactions) consumed.addAll(reaction.elements());
        return Set.copyOf(consumed);
    }
}
