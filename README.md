# Arcana: The Lost Arts

A standalone Minecraft **1.21.1 Fabric** mod project.

## Current prototype
- 21 registered spell components in four categories.
- Up to five components in a spell.
- Component selection through in-game commands.
- Compatibility validation and a spell preview.
- A basic Arcana status command.

This is a command-driven prototype, not the planned graphical Magic Writer screen. Drafts currently reset when the server restarts.

## Commands
- `/arcana components` — list components.
- `/arcana select <component>` — add a component.
- `/arcana remove <component>` — remove a component.
- `/arcana clear` — clear the draft.
- `/arcana preview` — validate and preview.
- `/arcana status` — show prototype Arcana status.

Requires JDK 21. Build with `gradle build`; the JAR is produced in `build/libs/`. GitHub Actions also builds the project and uploads the JAR as an artifact.

Target: Minecraft 1.21.1, Fabric Loader, Fabric API, Java 21.

This is an early prototype, not a complete survival-ready mod. Grimoire items, Magic Writer block/UI, persistent spell pages, scroll learning, actual casting, and Arcana regeneration remain future work and need in-game testing.
