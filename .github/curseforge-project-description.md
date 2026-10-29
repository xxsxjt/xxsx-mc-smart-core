# Xxsx Smart Core

Xxsx Smart Core is a portable, client-side AI assistant for Minecraft. It helps players inspect the current game, find exact vanilla and mod IDs, use compatible command workflows, and optionally build PMX models as voxel structures through natural-language `/ai` conversations.

It is a client helper for a local client or a singleplayer integrated server. Installing it on a dedicated server has no effect.

## Current File

The current candidate is **v1.1.12** for **Minecraft 26.2 / NeoForge 26.2.0.7-beta** only:

- Smart Core (`xxsx_mc_smart_core`): `xxsx-mc-smart-core-neoforge-26.2-1.1.12.jar` — standalone core.
- Optional Model Import (`xxsx_builder`): `xxsx-mc-model-import-neoforge-26.2-1.1.12.jar` — install alongside Smart Core v1.1.12 to use PMX/OBJ voxel building.
- Environment: Client
- Release stage: candidate; publication and client startup validation are pending.

This file does **not** update the older Forge 1.20.1 or NeoForge 1.21.11 lines. Install only a file that explicitly matches both your Minecraft version and loader. NeoForge 1.21.1 and Forge 26.x are local experiments and are not published releases.

## Core Features

- **Natural-language AI assistance**: talk through `/ai <message>` and let the assistant plan a response or command workflow.
- **Safe command execution**: commands that change a player, world, or server wait for explicit confirmation; read-only queries remain direct.
- **Runtime registry discovery**: the assistant reads the live instance registry before guessing IDs. It can discover exact IDs for items, blocks, entities, status effects, potions, enchantments, structures, and biomes, then use them in `/give`, `/setblock`, `/fill`, `/summon`, `/effect`, item components, `/locate`, or `/place` as appropriate.
- **Bilingual AI behavior**: Chinese and English system prompts, fixed feedback, and generated knowledge summaries follow the Minecraft game language.
- **Integration command catalog**: focused query routes for compatible installed mods and command templates, including Curios, JEI navigation, and supported local integrations.
- **PMX voxel construction**: optional PMX/OBJ model parsing and voxel construction with texture sampling and color matching.
- **Personal provider configuration**: configure an OpenAI-compatible AI provider and model in game. Provider availability and mod integrations depend on the local instance configuration.
- **Conversation continuity**: persisted sessions and context compression for longer tasks.

## Quick Start

1. Put the Smart Core jar in the Minecraft `mods` folder. To use PMX/OBJ voxel building, also add the matching Model Import jar.
2. Start Minecraft and enter a local world or singleplayer integrated server.
3. Use `/ai <message>` to ask for help.
4. Configure a personal provider with `/ai addmodel <url> <key> <model> [context length]` when needed.

For a basic ID lookup, ask the assistant for the desired object or mod namespace. The assistant should use its live registry query first, then generate commands from returned IDs rather than relying on stale documentation or guessed registrations.

## Compatibility Notes

Smart Core does not promise universal control over every installed mod. Available actions depend on the current instance, the installed mod, permissions, and whether a safe command or documented integration exists. Check the returned registry IDs and command feedback before confirming state-changing actions.

For source code, detailed release notes, and version-specific artifacts, visit [GitHub](https://github.com/xxsxjt/xxsx-mc-smart-core).
