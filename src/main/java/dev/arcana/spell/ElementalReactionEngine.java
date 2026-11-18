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
            Set.of("sun", "fire"))
    );

    /** Returns non-overlapping reactions, preferring the most specific combinations. */
    public static List<Reaction> resolve(List<String> componentIds) {
        Set<String> available = new LinkedHashSet<>(componentIds);
        List<Reaction> resolved = new ArrayList<>();
        boolean changed;
        do {
            changed = false;
            for (Reaction reaction : REACTIONS) {
                if (available.containsAll(reaction.elements())) {
                    resolved.add(reaction);
                    available.removeAll(reaction.elements());
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return List.copyOf(resolved);
    }

    public static Set<String> consumedElements(List<Reaction> reactions) {
        Set<String> consumed = new LinkedHashSet<>();
        for (Reaction reaction : reactions) consumed.addAll(reaction.elements());
        return Set.copyOf(consumed);
    }
}
