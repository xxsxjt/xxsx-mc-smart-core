# xxsx Builder 26.2 Integration List

Scope: MC 26.2 / NeoForge 26.2.0.7-beta only.

Mod version: `xxsx-builder-neoforge-26.2-1.1.5.jar`.

This round adds lightweight, optional integration knowledge, runtime detection, and a small built-in command library for simple executable integrations found in:

`D:\_dx\_Games\MC\xxxxxx\.minecraft\versions\26.2-NeoForge_26.2.0.7-beta\mods`

No hard compile dependency is added. The builder assistant uses `[QUERY]integrations[/QUERY]`, `[QUERY]mod <modid>[/QUERY]`, `[QUERY]commands <modid>[/QUERY]`, `[QUERY]curios <action> [slot] [amount][/QUERY]`, `[QUERY]doc <topic>[/QUERY]`, `[QUERY]recipe <item>[/QUERY]`, and `[QUERY]jei <item>[/QUERY]`.

AI tool loop update: `[QUERY]` and `[CMD]` feedback is returned to the model as `[TOOL_RESULT]`; the next model turn decides whether to execute another command, query again, or summarize completion. Command library lookup now supports portable category queries such as `[QUERY]commands add 饰品栏[/QUERY]`, `[QUERY]commands add ring[/QUERY]`, and `[QUERY]commands slots[/QUERY]`.

## Integrated Targets

| Mod id | Topic | Status | Purpose |
|---|---|---|---|
| `jei` | `jei` | runtime detect + doc | Recipe/use lookup hint; server recipe fallback. |
| `justenoughprofessions` | `jei` | runtime detect + doc alias | Villager profession/workstation hint. |
| `jade` | `jade` | runtime detect + doc | Crosshair block/entity identification. |
| `appleskin` | `appleskin` | runtime detect + doc | Food nutrition/saturation planning. |
| `waystones` | `waystones` | runtime detect + doc | Travel network and build logistics. |
| `naturescompass` | `compasses` | runtime detect + doc alias | Biome search planning. |
| `explorerscompass` | `compasses` | runtime detect + doc alias | Structure search planning. |
| `sophisticatedbackpacks` | `sophisticated` | runtime detect + doc alias | Portable building material storage. |
| `sophisticatedstorage` | `sophisticated` | runtime detect + doc alias | Bulk material warehouse planning. |
| `curios` | `curios` | runtime detect + doc + command templates | Accessory slot awareness and `/curios` slot command templates. |
| `xaerominimap` | `xaero` | runtime detect + doc alias | Local waypoints and navigation. |
| `xaeroworldmap` | `xaero` | runtime detect + doc alias | Region planning and map markers. |
| `enchdesc` | `enchanting` | runtime detect + doc alias | Enchantment text explanation. |
| `enchantmentinsights` | `enchanting` | runtime detect + doc alias | Enchantment table preview awareness. |
| `easymagic` | `enchanting` | runtime detect + doc alias | Enchanting table QoL. |
| `taxfreelevels` | `enchanting` | runtime detect + doc alias | XP cost optimization. |
| `legendarytooltips` | `clientui` | runtime detect + doc alias | Tooltip readability. |
| `controlling` | `clientui` | runtime detect + doc alias | Key conflict diagnosis. |
| `mousetweaks` | `clientui` | runtime detect + doc alias | Inventory operation QoL. |
| `lootr` | `exploration` | runtime detect + doc alias | Exploration loot chest behavior. |
| `shogi` | `exploration` | runtime detect + doc alias | Decorative/build theme reference. |
| `earth_online` | `projectmods` | runtime detect + doc alias | Local 26.2 project mod awareness. |
| `templenihility` | `projectmods` | runtime detect + doc alias | Local 26.2 project mod awareness. |
| `templenihility` | inventory assist | optional client action | When a Nihility Terminal is carried or equipped, Smart Core can open it and quick-move player inventory into the bound vault network. |

## Client Inventory Assist

- Inventory screen: compact icon button for sorting the main backpack area; if a Nihility Terminal is carried or equipped, an extra one-click income button appears.
- Chest-like storage screens: compact icon buttons for sorting the open container, moving container -> player, moving player -> container, and optionally collecting into the Nihility Terminal.
- The feature sends normal client container clicks / `/nihility terminal`; it adds no server-side behavior to `xxsx_builder`.

## Runtime Queries

- `[QUERY]integrations[/QUERY]`
- `[QUERY]integrations jei[/QUERY]`
- `[QUERY]mod jade[/QUERY]`
- `[QUERY]doc integrations[/QUERY]`
- `[QUERY]doc sophisticatedbackpacks[/QUERY]`
- `[QUERY]doc naturescompass[/QUERY]`
- `[QUERY]jei minecraft:diamond_pickaxe[/QUERY]`
- `[QUERY]commands list[/QUERY]`
- `[QUERY]commands add 饰品栏[/QUERY]`
- `[QUERY]commands add ring[/QUERY]`
- `[QUERY]commands set ring 2[/QUERY]`
- `[QUERY]commands curios[/QUERY]`
- `[QUERY]curios add curio[/QUERY]`
- `[QUERY]curios add ring[/QUERY]`

## Not Hard-Integrated

Performance and library mods are intentionally not treated as feature integrations: `sodium`, `sodium_extra`, `lithium`, `balm`, `cloth_config`, `puzzleslib`, `searchables`, `iceberg`, `prism`, `prickle`, `konkrete`, `melody`, `forgeconfigapiport`, `attributefix`, `maxhealthfix`, `aiimprovements`, `clumps`, `imblocker`, `autofish`, `onekeyminer`.

Some of them are still listed under `clientui` when they materially affect the player's client workflow.
