# Xxsx Smart Core v1.1.11

**Minecraft 26.2 / NeoForge 26.2.0.7-beta / Client only**

## Highlights

- Added unified runtime registry discovery with `[QUERY]registry <kind> [namespace/keyword]` and shortcut queries for items, blocks, entities, effects, potions, enchantments, structures, and biomes.
- The assistant now resolves exact live IDs before generating `/give`, `/setblock`, `/fill`, `/summon`, `/effect`, item component, `/locate`, or `/place` commands. It only reads documents or source when behavior, drops, recipes, or mechanics are needed.
- Added Chinese and English AI system prompts, language-aware knowledge summaries, and localized fixed feedback that follows the Minecraft game language.
- Added a focused integration command catalog and safer multi-turn tool-result handling.
- Kept Smart Core as a pure client-side portable assistant. Dedicated server installation has no effect.

## Compatibility

This upload updates only Minecraft 26.2 with NeoForge 26.2.0.7-beta. It does not update Forge 1.20.1 or NeoForge 1.21.11. NeoForge 1.21.1 and Forge 26.x are not published releases.

## Verification

- Built with `gradlew clean build --no-daemon --offline` using Java 25.
- Deployed jar SHA-256: `C36A0C86731000CF10A8CBCB813617A427F9F27D939B9800A3FEBFD662E809E0`.
- In-game smoke testing is pending a client restart, so this file is published as Beta.

## CurseForge Submission

- Project: `1599752` ([Xxsx Smart Core](https://www.curseforge.com/minecraft/mc-mods/xxsx-smart-core))
- File: `8456858`
- State: submitted as Beta with manual release enabled; pending CurseForge approval and the author's manual release action.
