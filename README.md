# Arcana: The Lost Arts

Arcana is an early Minecraft **1.21.1 Fabric** magic-mod prototype.

## Current features

- **102 registered spell components** across Delivery, Element, Target, and Effect categories.
- Create spells with up to five distinct components; different components from the same category can be combined.
- Magic Writer block and graphical component selector.
- Magic Paper engraving and spell data stored on the item.
- Grimoire tiers with stored spell pages.
- Spell Intelligence checks combinations and explains the intended spell behavior.
- Elemental reactions, including Steam, Magma, Frozen Water, Sandstorm, Wildfire, Lunar Tide, and Solar Flare.
- Arcana resource pool with regeneration.
- Spell casting, target selection, and first-pass behavior for the registered components.
- Server-side persistence for spellbooks, Grimoire contents, and Arcana.

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

## Build

Requires **JDK 21**. From the repository root, run:

```bash
./gradlew clean build
```

The mod JAR is generated in `build/libs/`. GitHub Actions also runs the Gradle build on pushes.

## Compatibility

- Minecraft 1.21.1
- Fabric Loader
- Fabric API
- Java 21

## Testing status

This is an early prototype. Component behavior is a first pass: some advanced effects currently use simpler status-effect or movement approximations rather than their full intended systems. The project still needs broad in-game testing, including multiplayer, GUI scaling, item persistence, and combinations of spell components. Please report reproducible bugs with your Minecraft log and the spell components involved.
