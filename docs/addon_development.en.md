# Addon development

[日本語](addon_development.md) · The Japanese edition is authoritative.

Target: ShinColle-kai **1.20.1-0.13.0**, Minecraft **1.20.1 / Forge** (development environment: Forge 47.4.0, Java 17).

## What you can create

- **Standard ships**: Add ships using built-in AI and attacks through JSON, models and textures. No Java is required.
- **Equipment**: Define existing items as ship equipment through datapacks.
- **Java / KubeJS**: Use the published equipment API, custom attributes, ownership queries and KubeJS integration.

This version does not support registering your own AI or attacks, or special ships that need a dedicated entity.

## Where to start

| Goal | Documentation and examples |
|---|---|
| Standard ships | [Ship data and visuals](ship_addon_data.en.md), [copyable example](../examples/ship_addon/README.en.md) |
| Equipment datapacks | [Equipment example and instructions](../examples/equipment_datapack/README.md), [equipment schema](schemas/shincolle_kai-equipment.schema.json) |
| Java equipment addons | [Published Java API](java_addon_api.md), [Java example](../examples/java_addon/README.md) |
| KubeJS | [KubeJS integration](kubejs_integration.md) |

Install ship datapacks on the server and visual resource packs on every client. Singleplayer also needs both.
