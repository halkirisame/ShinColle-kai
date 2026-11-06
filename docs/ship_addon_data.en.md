# Standard ship data and visuals

[日本語](ship_addon_data.md) · The Japanese edition is authoritative. [Addon development](addon_development.en.md)

This guide explains how to add a "standard ship" — a ship that runs on the built-in AI and attacks — using only JSON, a model and a texture. No Java is involved.
If this is your first ship, the quickest route is to get the example running unchanged, then copy it and turn the copy into your own ship.

## 1. Versions and layout

Target: ShinColle-kai **1.20.1-0.13.0**, Minecraft **1.20.1 / Forge** (development environment: Forge 47.4.0, Java 17).
Both ship definitions and visuals use **schema 1**. Saves and APIs are not guaranteed to stay compatible across mod versions.

A ship is made of two files:

- **Ship definition** (datapack, server side): stats, how the ship is obtained, and its AI and attacks.
- **Visual** (resource pack, client side): model, texture and animations.

As the example's [README](../examples/ship_addon/README.en.md) describes, install the same folder both as a datapack on the server and as a resource pack on every client.
On Minecraft 1.20.1 both use `pack_format: 15`. Singleplayer needs both as well.

```text
ship_addon/
  pack.mcmeta
  data/shincolle_example/shincolle_kai/ships/training_ship.json
  assets/shincolle_example/shincolle_kai/visuals/training_ship.json
```

Where a file lives determines its ID:

- A ship definition at `data/<namespace>/shincolle_kai/ships/<path>.json` has the ship ID `<namespace>:<path>`.
- A visual at `assets/<namespace>/shincolle_kai/visuals/<path>.json` has the visual ID `<namespace>:<path>`.
- The ship definition's `visual` field picks the visual. It does not have to match the ship ID.
- When several packs provide the same ID, the file from the higher-priority pack wins.
- Stats and acquisition rules are decided by the server. Visual assets are not sent from the server, so every client needs them installed.

## 2. Copy before editing

1. Install the example unchanged, get an egg with the README's `/give`, and use it. The ship looks like Kashima but is a separate ship.
2. Copy the folder for distribution and rename `data/shincolle_example/` and `assets/shincolle_example/` to your own namespace, such as `my_fleet`.
3. Rename both `training_ship.json` files to your own name, such as `scout.json`, and set the ship definition's `visual` to `my_fleet:scout`.
4. Change `ShipId` in the command to `my_fleet:scout` as well. The `shincolle_kai/ships` and `shincolle_kai/visuals` parts in between are fixed; leave them alone.
5. While you are borrowing Kashima's assets, keep the `shincolle_kai:` in the visual's `model`, `texture` and `animation`.
6. When you replace them with your own model or texture, switch those references to your namespace and put the files under `assets/`.

Namespaces and paths may use lowercase letters, digits, `_`, `.` and `-` (paths may also use `/`). IDs inside a ship definition and ship IDs derived from file names are limited to 64 characters, namespace included.
Once you have released a ship, keep its ID: saved ships are identified by it. Back up your world before editing.

## 3. Ship definition

The JSON below is identical to the [example ship definition](../examples/ship_addon/data/shincolle_example/shincolle_kai/ships/training_ship.json).

```json
{
  "schema": 1,
  "display_name": "Example Training Ship",
  "ship_type": "shincolle_kai:light_cruiser",
  "visual": "shincolle_example:training_ship",
  "base_stats": {"hp": 42, "atk": 13, "def": 0.16, "spd": 1, "mov": 0.42, "hit": 8},
  "growth": {"hp": 0.4, "atk": 0.4, "def": 0.2, "spd": 0.6, "mov": 0.9, "hit": 0.4},
  "behavior": {
    "ai": "shincolle_kai:standard",
    "attacks": ["shincolle_kai:light_cannon", "shincolle_kai:heavy_cannon", "shincolle_kai:melee"]
  },
  "construction": {"enabled": true, "mode": "weighted", "weight": 500},
  "spawn": {"enabled": false}
}
```

The [public ship Schema](schemas/shincolle_kai-ship_definition.schema.json) is the authority on every key's type and numeric limits. Point your editor at it to get completion and validation while you type.
A file may be at most 65536 UTF-8 bytes and 32 levels deep. A duplicate key, or an invalid value in a known key, rejects the whole definition. Unknown keys are ignored with a warning.

| Key | Meaning and default |
|---|---|
| `schema` | Required; the integer 1. `$schema` is an optional string for editors and does not select a mod version |
| `display_name` | Display name, up to 64 characters. Defaults to the ship ID |
| `ship_type` | Required ship type. See [the Schema's `ship_type`](schemas/shincolle_kai-ship_definition.schema.json#/properties/ship_type) for the allowed values. A type name alone does not enable aircraft or special attacks |
| `visual` | Required client visual ID. The server does not check that the assets exist |
| `dimensions.width` / `height` | Hitbox width and height in blocks. Defaults 0.5 / 1.5; at least the smallest positive float, at most 1.6 / 4.4. Independent of the visual's `scale` |
| `mount_offset` | Riding height. Default 0.9, range 0–4.4. A multiplier for the ship's size stage is applied on top |
| `equip_type` | Numeric equipment-compatibility class. Default 1, integer 0–3 |
| `attack_profile` | See the Schema's fixed values for the default and allowed values. Attacks themselves are chosen in `behavior` |
| `base_stats` | Required object with the six base stats for a friendly ship. Each omitted stat is 0 |
| `growth` | The six growth values for a friendly ship. Each omitted value is 0. Unrelated to equipment attribute JSON |
| `hostile_modifiers` | Six multipliers applied to a hostile ship's per-size-stage settings. Each defaults to 1; at least the smallest positive float, at most 2.4. Hostile ships do not use `base_stats` or `growth` |
| `equipment.slots` | Maximum number of entries in the initial equipment list. Default 0, integer 0–16 |
| `equipment.initial` | Array of equipment definition IDs (not item IDs). Default empty. At most `slots` entries, and each ID must exist among the server's equipment definitions. The same ID may appear in more than one slot. It is validated on load, but in this version it cannot be used to give a ship equipment automatically |
| `construction` / `spawn` | Acquisition rules. Disabled when omitted. See §4 |
| `behavior` | AI and attack selection. See §5 |
| `recruitment` | Reserved. Ignored in this version whatever its value |
| `hostile_variants` | Hostile individual selection, names, traits and modifiers; see below |

The six stats are `hp` (durability), `atk` (attack), `def` (defense), `spd` (attack speed), `mov` (movement) and `hit` (accuracy).
Base and growth values must be finite and non-negative; per-stat limits are in [base_stats](schemas/shincolle_kai-ship_definition.schema.json#/properties/base_stats) / [growth](schemas/shincolle_kai-ship_definition.schema.json#/properties/growth).
The numbers you write are not shown as-is as final stats or in the GUI.

### Hostile individual variants

`hostile_variants` is an optional object. Omission defines no individual variants.
New hostile data-defined ships draw once on their first server tick after spawning, including the normal slot.
The selected individual is saved and is not rerolled on load, dimension transfer or `/reload`. Enemies in existing saves remain normal individuals.
Manual names take priority over the individual name, then the ship type name. Missing individual translations fall back to the ship type name.
`/reload` updates hostile-only modifier calculations, but not the snapshotted name, rarity, trait IDs or visual metadata. The Schema version remains 1.
Administrators can use `/shincolle ship spawn <ship_id> hostile <variant_id>` to summon a specific variant. Unknown variant IDs are command errors.

When a defeated individual drops an egg, its ship ID, origin variant ID, individual name, rarity, snapshotted trait IDs and visual variant carry over to the ally.
Recruitment starts at level 1, owned by the egg's user; enemy size, hostile-only modifiers, training, equipment, HP and ownership do not carry over.
Drop probability and timing are the same as for normal individuals. Recruitment disables red-aura metadata but retains visual-variant metadata.
Friendly ships multiply their raw values from `base_stats`, growth and level by traits only, never by variant hostile-only modifiers.
Individual identity survives friendly saves, loads, dormancy while a definition is missing, dimension transfers, death eggs, Safari Nets and Soul Talismans.
Death eggs still restore their existing training, equipment and ownership alongside individual identity; the fresh-recruitment level-1 rule does not strip saved values.
Egg tooltips show the individual name, rarity and traits. Unknown saved trait IDs are retained and shown as inactive, with their effects disabled.
Eggs with invalid or unavailable ship IDs report the reason and are not consumed. Future-version individual data is saved unchanged and remains inert until supported.

This is a fragment to merge into an existing ship definition, not a standalone definition file.

```json
{"hostile_variants": {
  "normal_weight": 90,
  "variants": [{
    "id": "flagship",
    "name": "variant.my_fleet.training_ship.flagship",
    "rarity": "rare",
    "weight": 10,
    "traits": ["shincolle_kai:sturdy"],
    "hostile_modifiers": {"hp": 1.5},
    "visual_variant": "my_fleet:textures/entity/training_ship_flagship.png",
    "red_aura": true
  }]
}}
```

- `normal_weight`: integer 0–10000, default 100. `variants`: empty when omitted, at most 16 entries.
- Each `id` is required and matches `[a-z0-9_]{1,32}`. IDs must be unique within a ship. Identity is the stable pair of ship ID and variant ID, never array order or display name.
- `weight` is a required integer 0–10000. Total weight is at most 170000. All-zero weights, including the normal slot, produce a normal individual. `rarity` is display-only, separate from draw probability (`common` / `rare` / `epic` / `legendary`, default `rare`).
- `name` is an optional translation key, 1–128 characters. `traits` defaults to empty, at most four unique entries. Only registered IDs from [the Schema's trait list](schemas/shincolle_kai-ship_definition.schema.json#/$defs/shipTraitId) are selectable. Custom Java trait registration is not a public API.
- Built-in traits are `sturdy`=hp, `heavy_hitter`=atk, `armored`=def, `quick_loader`=spd, `swift`=mov and `keen_eye`=hit (all in `shincolle_kai:`, each with a provisional multiplier of 1.1). Snapshotted trait IDs apply to hostile and friendly stats.
- Variant `hostile_modifiers` uses the same six keys and bounds as the ship-level object, each defaulting to 1. Hostile raw values (per-size configuration × ship-level `hostile_modifiers`) are multiplied by traits and variant modifiers, before existing equipment, buffs and limits. Friendly raw values come from `base_stats` and growth, so the same trait multiplier has different starting values on each side. Removing a variant stops only its hostile-only modifiers; its ID and snapshotted traits remain saved. Restoring the definition restores the modifiers.
- `visual_variant` is a per-individual texture ResourceLocation (explicit namespace, at most 128 characters). Ship IDs and ordinary resource IDs retain their 64-character limit; variant ID rules are unchanged. It does not replace geometry or animation. Omission uses the base texture; a missing individual texture switches the entire model to the placeholder until a successful reload restores the resource. Missing base visuals take precedence and also use the placeholder. The server does not check resource existence. Recruitment retains the texture.
- `red_aura` is an optional boolean, default `true`. Only living, non-dormant, visible hostile data-defined ships receive a translucent red model layer and small particles; recruitment stops both. Each frame selects the nearest 16 eligible ships strictly within 32 blocks of the camera, subject to normal entity display and frustum conditions (entity ID breaks ties; no through-wall outline). Textures and names are independent of this limit.
- Red particles are limited to four per ship in any rolling 20 ticks, at least five ticks apart, 64 total per rolling 20 ticks, 64 alive, and a lifetime of at most 20 ticks. Minimal particle settings disable new particles; decreased settings reduce emission. Rendering fps does not increase emission. World/camera changes, disconnects and successful resource reloads discard effect state. The red layer uses RGB `(1, 0.15, 0.15)` and alpha `0.25`, preserving normal depth testing and existing Glow.
- Invalid known values, wrong types, exceeded limits and unknown trait IDs reject the whole ship definition, without rejecting other ships. Unknown keys alone are still warned about and ignored.

## 4. Acquisition

### Construction

`construction.enabled` defaults to `false`. Even when enabled, `weight: 0` keeps the ship out of the draw.
The allowed values for `mode`, `tier` and `material_bias` are in [the construction Schema](schemas/shincolle_kai-ship_definition.schema.json#/$defs/construction).

- **material** (default): the ship joins the normal construction pool, where the result depends on the materials used. `tier` and `material_mean` are required.
  The distance between `material_mean` and the total materials put in feeds the construction distribution. `material_bias`, if given, subtracts that material's amount from the mean.
  `weight` defaults to 100, which is a multiplier of 1; 200 doubles it. It is not a fixed percentage.
- **weighted**: a separate draw that ignores materials and succeeds with probability `weight / 10000`. `weight` defaults to 0.
  Omitting `tier` applies the ship to both small and large construction. If the eligible weighted definitions add up to more than 50%, they are scaled down proportionally to 50%. When the separate draw misses, the normal material-based pool is used.
- `weight` and `material_mean` are integers from 0 to 2147483647. Omitting `material_bias` applies no adjustment.

The ship is decided when construction completes and is stored in the egg's `ShipId`. Using the egg does not roll again.

### Wild spawns and drops

`spawn.enabled` defaults to `false` and `weight` to 0; `weight` is an integer from 0 to 2147483647.
With it enabled and a positive weight, the ship joins the normal hostile-ship draw. It never joins the boss draw. The usual spawn conditions still apply, so `enabled` does not make the ship appear on the spot.
For scale, the built-in ships add up to 700, and a single built-in destroyer is 46. Bundled ships and other definitions add to the total, so `weight` is not a percentage.
A defeated hostile ship may drop a **friendly egg** with the same ship ID. `spawn` does not switch this egg drop on or off.

### Eggs and commands

The [README's `/give`](../examples/ship_addon/README.en.md#obtaining-the-ship) gives you an egg carrying the ship ID.
With `Hostile:0b` the ship becomes a friendly ship of whoever uses the egg; with `Hostile:1b` it becomes a hostile ship. An egg whose ID has no definition cannot be used.
`/summon shincolle_kai:generic_ship ~ ~ ~ {ShipId:"shincolle_example:training_ship"}` with NBT also sets the ship ID, but it does not set an owner or initial supplies.
Use the egg when you want to check a friendly ship. A generic ship without a `ShipId` is never added to the server.

## 5. AI and attacks

The selectable IDs are listed in the Schema's **[`$defs/shipAiId`](schemas/shincolle_kai-ship_definition.schema.json#/$defs/shipAiId)** and **[`$defs/shipAttackId`](schemas/shincolle_kai-ship_definition.schema.json#/$defs/shipAttackId)**.

- Omitting `behavior.ai` gives `shincolle_kai:standard` (the standard behaviour for friendly and hostile ships respectively).
- Omitting `behavior.attacks` gives `light_cannon`, `heavy_cannon` and `melee` for friendly ships, and `light_cannon` and `heavy_cannon` for hostile ships (all `shincolle_kai:`).
- If you write it, list 1–16 IDs without duplicates. An empty array (`attacks: []`), an ID not in the list, or an ID from another namespace rejects the whole definition.
- `aircraft` and `*_skill` are reserved; specifying them in this version rejects the whole definition.
- `melee`: a friendly ship melees only while its admiral has melee enabled; a hostile ship melees whenever it is listed.
- If you pick only one of `light_cannon` and `heavy_cannon`, the ship never fires the other, even when the GUI has it enabled. Ammunition and the other usual conditions still apply.

## 6. Visuals and Blockbench

Start by copying the [example visual JSON](../examples/ship_addon/assets/shincolle_example/shincolle_kai/visuals/training_ship.json).
Every key and limit is in [the visual Schema](schemas/shincolle_kai-ship_visual.schema.json). `schema`, `model`, `texture` and `animation` are required.
Asset IDs are `namespace:path` including the file extension, up to 256 characters. The example points at Kashima's three assets in the base mod instead of duplicating any art.

| Optional key | Meaning and default |
|---|---|
| `scale` / `shadow_radius` | Render scale (default 1) and shadow size (default 0.35). Scale ranges from the smallest positive float to 16; shadow from 0 to 16 |
| `rig` | Maps roles to bone names. An omitted role looks for a bone with the role's own name |
| `animations` | Maps pose and attack events to clip names. An omitted event uses a clip named after the event key |
| `expressions` | Bones used to switch facial expressions. If present, must be non-empty without duplicates; see the Schema for the count limit |
| `glow_bones` | Bones (and their descendants) that glow. Empty by default |
| `head_follow` | Bones that follow the head's rotation. None by default. The coefficient defaults to 0.5, and `overrides` sets per-bone x / z coefficients. Naming a bone that cannot follow, or one that does not exist, rejects the visual |
| `held_item.offset` / `rotate` | Position and rotation adjustment for the held item. Three numbers each, default `[0,0,0]` |
| `lod.distance` / `hide_bones` | Bones (and their descendants) hidden at a distance. Distance defaults to 48, range 1–256. Bones default to empty |

Bone names are 1–128 characters with no control characters. Check the Schema for list and coefficient limits as well.

### Using Kashima as a starting point

Extract `assets/shincolle_kai/model_sources/kashima/kashima.bbmodel` from the base mod's JAR and open a copy in Blockbench.
The file format is 4.10 / bedrock. A `.bbmodel` is a source file for editing; the game does not read it directly.
After editing, put the model and animations as game-ready `.geo.json` and `.animation.json`, and the texture as a `.png`, under your own `assets/<namespace>/`, then point the visual's three references at them.
If you rename clips, update `animations`; if you rename bones, update `rig` and every other bone reference — expressions, head follow, LOD and so on.

Kashima's visual maps `rig` as follows.

| `rig` key | Kashima bone |
|---|---|
| `body` | `BodyMain` |
| `head` | `Head` |
| `arm_left` | `ArmLeft01` |
| `arm_right` | `ArmRight01` |
| `leg_left` | `LegLeft01` |
| `leg_right` | `LegRight01` |
| `equipment` | `EquipBase` |
| `face` | `Face0` |
| `mouth` | `Mouth0` |
| `flush` | `Flush0` |
| `held_item` | `held_item` |
| `muzzle_light` | `muzzle_light` |
| `muzzle_heavy` | `muzzle_heavy` |

Kashima's `rig` leaves `root` and `held_item_left` unset. Do not assume Kashima's model fills every role.

### Missing or mismatched assets

- If the visual is missing or fails to load, the ship is drawn with a placeholder model. A missing visual alone does not reject the server's ship definition.
- A missing model, texture or animation file, or invalid visual content, rejects that visual.
- If a bone named in `rig` cannot be found, only that role's feature is disabled.
- A missing pose clip falls back to the next available pose; a missing attack clip skips that effect.
- Missing bones named for glow or LOD disable that feature. `head_follow` is different: it rejects the whole visual.

## 7. Reload, saves and removal

- The server's `/reload` re-reads ship and equipment definitions. Ships already in the world update on the next tick, and their AI and attack goals are rebuilt.
  An attack in progress may be cut short. The target-selection goal is not rebuilt.
- To update visuals, models or textures on the client, reload resource packs (F3+T). This is separate from the server's `/reload`.
- `ShipId` is saved. Across saves and restarts, keep a definition with the same ID on the same mod version.
- Removing a definition, or having it rejected, does not delete existing ships. They go dormant, stop moving and attacking, and keep their original NBT. Restore a valid definition with the same ID and `/reload` to bring them back.
- Renaming a file or namespace creates a different ID. Ships with the old ID are not migrated to the new one, so keep a backup from which you can restore the old definition.

## 8. Fixing problems from the log

Do not mix up the server's `logs/latest.log` with the client's log of the same name. These are real messages, shown without timestamps or logger prefixes.

```text
Rejected ship definition shincolle_example:training_ship: behavior.attacks must not be empty; omit it for the default attacks
Rejected ship definition shincolle_example:training_ship: unknown or unselectable attack 'shincolle_kai:typo'
Rejected ship definition shincolle_example:training_ship: attack 'shincolle_kai:aircraft' is registered but cannot be selected in 1.0
Missing ship definition shincolle_example:training_ship; keeping entity dormant and preserving its NBT
Missing/rejected ship visual shincolle_example:training_ship; using placeholder
```

1. Read the ID and reason on the first rejection line, then fix the file location, the value's type, or the Schema list it refers to. For an empty `attacks`, remove the key or list at least one selectable ID.
2. Run `/reload` on the server. Even when `Loaded ... ship definitions (generation ...)` appears, check that no individual definition was rejected.
3. If you see the placeholder, check the client's pack and fix the asset IDs, bone names or clip names, then press F3+T.
   `Rejected ship visual <id>; using placeholder: <exception>` and `Ship visual <id> lacks role <role>; feature disabled` are also useful clues.
4. Back up the world, then check the whole loop: spawning from the egg, rendering, saving, restarting and reloading.
