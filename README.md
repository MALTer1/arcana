# Arcana: The Lost Arts

Arcana is a Minecraft **1.21.1 Fabric** magic-mod prototype. Its spell system is built around combining components into one interpreted spell rather than executing every component as an unrelated action.

## Current features

- **102 registered spell components** across Delivery, Element, Target, and Effect categories.
- Spell drafts support up to five distinct components; different components from the same category can be combined.
- Magic Writer block and graphical component selector.
- Magic Paper engraving and spell data stored on the item.
- Grimoire tiers with stored spell pages.
- Spell Intelligence validates component selections and explains detected interactions.
- A whole-spell composition engine maps all registered components to semantic properties and calculates bounded modifiers for travel reach, area, and effect duration.
- Delivery shapes can combine; target selection supports single/multiple targets, allies, hostile mobs, and projectile interception.
- Context-aware interactions account for conditions such as wet, burning, and airborne targets.
- Elemental reactions support specific multi-element reactions and independent overlapping reactions.
- Implemented world actions include crop growth and harvesting, held-item repair, dropped-item collection/storage, item attraction/repulsion, supported block transmutations, projectile deflection, and safe same-dimension recall.
- Arcana resource pool with regeneration.
- Server-side persistence for spellbooks, Grimoire contents, and Arcana.

## Important implementation boundaries

This is still a prototype, not a finished gameplay system. Some registered concepts remain approximations or need dedicated systems:

- `projectile` and related moving delivery shapes currently use server-side targeting simulation; they do not yet create fully animated, independently ticking magical projectiles.
- `portal` is currently a safe one-shot teleport, not a persistent two-way portal.
- `weather`, `temperature`, `disguise`, `decoy`, `echo`, `memory`, `alarm`, and `dimension_sense` still need their full world or entity systems.
- `waypoint`, `tracking`, and `reveal` currently rely on entity highlighting rather than a complete navigation/marking interface.
- `water_walking`, `climb`, and `float` use movement/status-effect approximations.
- `duplication` is not yet a true object-copy system.
- Block transmutation is intentionally restricted to a small supported recipe list; it does not rewrite arbitrary blocks or block entities.
- Summoning creates a named temporary Allay helper with a 60-second loaded-world lifetime; follow/command behavior and lifetime persistence across chunk unloads still need work.

The composition engine is deterministic and rule-based. Its interaction library is expandable, but it does not yet infer every possible combination or every world situation automatically.

## Commands

- `/arcana components` — list all registered components.
- `/arcana select <component>` — add a component to your draft.
- `/arcana remove <component>` — remove a component from your draft.
- `/arcana clear` — clear your current draft.
- `/arcana preview` — validate and preview your draft.
- `/arcana engrave <name>` — engrave a valid draft onto Magic Paper.
- `/arcana spells` — list spells in your spellbook.
- `/arcana inspect <name>` — inspect a stored spell.
- `/arcana forget <name>` — remove a spell from your spellbook.
- `/arcana cast <name>` — cast a spell from your spellbook.
- `/arcana status` — show your current Arcana.
- `/arcana grimoire list` — list pages in the held Grimoire.
- `/arcana grimoire remove <name>` — remove a page and return its data-bearing Spell Page.
- `/arcana grimoire cast <name>` — cast a spell stored in the held Grimoire.

## Build and tests

Requires **JDK 21**. From the repository root, run:

```bash
./gradlew clean build
```

The build includes JUnit tests for spell composition and elemental reaction rules. The mod JAR is generated in `build/libs/`. GitHub Actions runs the Gradle build and tests on pushes.

## Compatibility

- Minecraft 1.21.1
- Fabric Loader
- Fabric API
- Java 21

## Testing status

A successful Gradle build and passing unit tests verify compilation and the tested rule logic; they do **not** prove that every spell behaves correctly in a live Minecraft world. Broad in-game testing is still required, including multiplayer, GUI scaling, item persistence, world interactions, and combinations of components. Please report reproducible bugs with your Minecraft log and the spell components involved.
