# KubeJS連携

ShinColle-kai 1.20.1では、KubeJSのstartup scriptから艦属性を登録し、KubeJSで
作ったItemを通常の装備JSONで艦装備にできます。Java addonとKubeJSは同じ
ShinColle Public API、Forge registry、装備loader/resolverを使います。

## 必要なもの

- Minecraft 1.20.1 / Forge 47系
- ShinColle-kai
- KubeJS 2001.6.5系
- KubeJSが要求するRhinoとArchitectury API

KubeJS、startup scripts、KubeJSで登録するItemはclientとserverの両方へ同じ内容を
導入してください。艦属性はstartup registryなので、script変更後は`/reload`ではなく
ゲームの再起動が必要です。

装備JSONはserver datapackが正です。装備定義はShinColle-kaiの既存同期処理によって
login時と`/reload`時にclientへ送られます。

equipment JSONの完全な語彙は[JSON Schema](schemas/shincolle_kai-equipment.schema.json)を参照して
ください。KubeJSが不要な既存Itemの固定値装備なら、より小さい
[JSON-only datapack例](../examples/equipment_datapack/README.md)から始められます。

## 1. 艦属性を登録する

`kubejs/startup_scripts/shincolle_kai_attributes.js`:

```javascript
StartupEvents.registry('shincolle_kai:ship_attribute', event => {
  event.create('kubejs:sonar_precision')
    .displayName('Sonar Precision')
    .raw(0)
    .equipment(0)
    .morale(0)
    .potion(0)
    .formation(0)
    .additive()
    .minimum(0)
    .maximum(1)
    .scaleGroup('none')
    .displayFormat('percent')
    .enchantRule('none')
})
```

`displayName`はKubeJSの言語生成を使います。既存lang fileのkeyを直接使う場合は、
代わりに`.translationKey('ship_attribute.example.sonar_precision')`を指定できます。

### layer初期値

- `raw(value)` — 艦の基礎値
- `equipment(value)` — 装備値
- `morale(value)` — 士気値
- `potion(value)` — ポーション値
- `formation(value)` — 陣形値
- `defaultValue('raw', value)` — layer名を指定する共通形。`buffed`は計算結果なので指定不可

全数値は有限値だけを受け付けます。`NaN`とInfinityは登録時に拒否されます。

### 合成方式

1属性につき、次のいずれか1つを指定します。省略時は`additive`です。

- `additive()` — 全layerを加算
- `scaledAdditive(includeFormation)` — morale/potionと、指定時はformationへ既存倍率を適用して加算
- `multiplicative(potionMultiplier)` — raw/equipment/potionを加算後、moraleとformationを乗算
- `defense()` — 防御属性用の合成式

JavaScript関数を独自合成式として渡すことはできません。戦闘中にscriptを呼ばず、
client/serverで同じ計算を再現できるよう、Java側の名前付き方式だけを選びます。

### 設定値

- `scaleGroup`: `none`, `hp`, `atk`, `def`, `spd`, `mov`, `hit`
- `displayFormat`: `decimal`, `integer`, `percent`
- `enchantRule`: `none`, `multiply`, `weapon_multiply`, `armor_multiply`,
  `signed_multiply`, `weapon_additive`, `non_weapon_additive`
- `enchantEffectSource('namespace:path')` — 別属性のenchant効果値を参照。省略時は自分自身
- `minimum(value)`, `maximum(value)` — 型自身の下限・上限

名前は大文字小文字を区別せず、`weapon-additive`のようなハイフンも使えます。
不明な名前、空のID、`minimum > maximum`は曖昧なfallbackをせずstartup errorになります。

## 2. KubeJS Itemを登録する

`kubejs/startup_scripts/shincolle_kai_items.js`:

```javascript
StartupEvents.registry('item', event => {
  event.create('sonar_module').displayName('Sonar Module')
})
```

通常のKubeJS Itemです。ShinColle-kai専用Item classは不要です。

## 3. Itemを艦装備として定義する

`kubejs/data/kubejs/equipment/sonar_module.json`:

```json
{
  "item": "kubejs:sonar_module",
  "variant": 0,
  "equip_type": "radar_lo",
  "compatible": [
    "cannon",
    "aircraft"
  ],
  "enchant_type": "misc",
  "develop": {
    "material": "grudge",
    "amount": 100,
    "rare_mean": 100
  },
  "roll_type": 14,
  "stats": {
    "shincolle_kai:hit": 2.0,
    "kubejs:sonar_precision": 0.15
  },
  "attack_effects": [
    {
      "effect": "minecraft:glowing",
      "amplifier": 0,
      "duration": 100,
      "chance": 25
    }
  ]
}
```

`attack_effects`もJava addonと同じloader/sync/resolverを通ります。`effect`は登録済み
MobEffect ID、`amplifier`は0始まり、`duration`はtick、`chance`は百分率です。任意の
JavaScript callbackを戦闘中に実行するScript Effect APIはまだ公開していません。

装備定義IDはfile pathから`kubejs:sonar_module`になります。addon装備に旧数値IDの
`equip_id`は不要です。`item + variant`がItemStackから定義を引く組み合わせなので、
同じItemで複数variantを使う場合は重複させないでください。

この装備は既存の`ShipEquipmentResolver`を通るため、native 6枠、対応するCurios枠、
装備集計、専用server同期、tooltipでJava addonの装備と同じ扱いになります。

## 4. reloadと責任範囲

| 変更 | 必要な操作 | 配置 |
|---|---|---|
| startup属性・Item | client/serverを再起動 | client/server双方で同一 |
| equipment JSONの数値 | `/reload` | serverが正、clientへ自動同期 |
| Item texture/model/lang | client resource reloadまたは再起動 | client |

戦闘、能力、装備可否、建造の判断は常にserverが行います。clientへ同期された定義と
属性値は表示用であり、client scriptの値をゲームロジックの正として信用しません。

## 5. 現在の範囲

現在のKubeJS Adapterは属性Registry DSLと、既存equipment JSONを使う装備作成経路を
提供します。汎用の艦イベント、条件、効果、任意Script Effectsはまだ公開していません。
これらはCore内部を直接公開せず、Java addonとKubeJSが共有できるPublic API契約を
個別に固めてから追加します。
