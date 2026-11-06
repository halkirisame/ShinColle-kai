# Standard ship example

[日本語](README.md) · The Japanese edition is authoritative. [Start here](../../docs/addon_development.en.md) / [Full guide](../../docs/ship_addon_data.en.md)

Target: ShinColle-kai 1.20.1-0.13.0, Minecraft 1.20.1 / Forge (development environment: Forge 47.4.0, Java 17).
No Java or additional mods are needed. The example references Kashima's model, texture and animations bundled with the main mod.
It adds a separate ship ID, `shincolle_example:training_ship`, without overriding Kashima.

## Installation

The `ship_addon` folder serves as both a datapack and a resource pack.

1. Copy the whole folder into the server world's `datapacks/` (your world folder in singleplayer).
2. Copy the same folder into each client's `resourcepacks/` and enable it in the resource pack screen.
3. Open the world or run `/reload` as an administrator. Check `/datapack list enabled`.

When making a ZIP, place `pack.mcmeta`, `data/` and `assets/` directly at its root, without an extra enclosing folder.
A dedicated server only uses the data side and does not automatically distribute textures to clients.

## Obtaining the ship

Run this as a player with cheats/administrator permission:

```mcfunction
/give @s shincolle_kai:generic_ship_spawn_egg{ShipId:"shincolle_example:training_ship",Hostile:0b} 1
```

Right-click a block face with the egg, and the ship appears as a friendly ship of the player who used it.
In normal play the ship comes from small or large construction. With only this example installed, every construction has a 5% chance of producing it regardless of materials (`weight: 500`).
If all weighted definitions together exceed 50%, they are scaled down proportionally. Wild spawning is disabled.

If you need a hostile egg for testing, change `Hostile:0b` to `Hostile:1b` in the same command.
Unlike an egg, `/summon` does not set an owner or initial supplies, so use an egg when checking how a friendly ship behaves.

## Making your own ship

Install an unchanged copy first, then follow [renaming IDs and creating assets](../../docs/ship_addon_data.en.md#2-copy-before-editing).
Apply server JSON changes with `/reload` and visual changes with client resource reload (F3+T).
Back up the world, save, quit and restart to check that the same ID is retained.
