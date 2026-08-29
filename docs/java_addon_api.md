# ShinColle-kai Java Addon API

更新: 2026-08-24

## 配布形態

Public APIはShinColle-kai本体JARの`com.lulan.shincolle.api`に同梱されています。
このAPIはShinColle-kaiで新規に追加したものです。別途APIのMODを導入する必要はありません。

addonの開発環境では、利用する配布元またはローカルMavenからShinColle-kai本体を
ForgeGradleの`compileOnly fg.deobf(...)`へ指定し、実行環境にも同じ本体JARを入れます。
公開座標は配布開始時に確定するため、この文書では仮のMaven座標を定義しません。

addonの`mods.toml`には本体への必須依存を記載します。

```toml
[[dependencies.your_addon]]
modId = "shincolle_kai"
mandatory = true
versionRange = "[1.20.1-1.0.0,)"
ordering = "AFTER"
side = "BOTH"
```

APIは公開前のため、現在は互換性を固定していません。本体内部の
`com.lulan.shincolle.entity`、`utility`、`reference.ID`、`capability`へ直接依存せず、
`com.lulan.shincolle.api`とMinecraft/Forge APIだけを利用してください。

## 独自の艦属性を登録する

<!-- traceability: java-addon.registry-key begin -->
艦属性はForge startup registryです。addon自身の`DeferredRegister`を、Public APIが
所有するregistry keyへ接続します。
<!-- traceability: java-addon.registry-key end -->

<!-- traceability: java-addon.attribute-builder begin -->
```java
public final class AddonShipAttributes {
    public static final DeferredRegister<ShipAttributeType> ATTRIBUTES =
            DeferredRegister.create(ShipAttributeRegistries.REGISTRY_KEY, "your_addon");

    public static final RegistryObject<ShipAttributeType> SONAR_PRECISION = ATTRIBUTES.register(
            "sonar_precision",
            () -> ShipAttributeType.builder()
                    .combiner(ShipAttributeCombiners.ADDITIVE)
                    .minimum(0F)
                    .displayFormat(ShipAttributeDisplayFormat.PERCENT)
                    .translationKey("ship_attribute.your_addon.sonar_precision")
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
    }
}
```
<!-- traceability: java-addon.attribute-builder end -->

登録内容はclient/serverで一致させ、変更後は両方を再起動してください。`/reload`では
startup registryは変更されません。IDは必ずaddon自身のnamespaceを使います。

## Item自身から動的な装備値を返す

<!-- traceability: java-addon.equipment-context-values begin -->
Item固有のNBT等から値を計算する場合は`IShipEquipment`を実装します。受け取った
`ShipEquipmentContext`は副作用のない解決専用で、返す値は不変です。

```java
public final class SonarModuleItem extends Item implements IShipEquipment {
    private static final ResourceLocation SONAR_PRECISION =
            ResourceLocation.fromNamespaceAndPath("your_addon", "sonar_precision");

    public SonarModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ResolvedShipEquipment resolveShipEquipment(ShipEquipmentContext context) {
        ItemStack stack = context.stack();
        float quality = Math.max(0F, stack.getOrCreateTag().getFloat("SonarQuality"));
        ShipAttributeValues values = ShipAttributeValues.builder(context.layout())
                .set(CoreShipAttributes.HIT, quality * 0.1F)
                .set(SONAR_PRECISION, quality)
                .build();
        ResourceLocation glowing = ResourceLocation.fromNamespaceAndPath("minecraft", "glowing");
        ShipAttackEffect effect = new ShipAttackEffect(glowing, 0, 100, 25);
        return new ResolvedShipEquipment(values, ResolvedShipEquipment.DEFAULT_COMPATIBILITY,
                Map.of(glowing, effect));
    }
}
```
<!-- traceability: java-addon.equipment-context-values end -->

<!-- traceability: java-addon.equipment-hit-callback begin -->
非finite値、未登録属性、現在のlayoutと異なる値は装備単位で拒否されます。解決中にworld、
entity、元のItemStackを変更してはいけません。攻撃命中後の副作用が必要な場合だけ
`onShipHit(LivingEntity, Entity, float, ItemStack)`を使い、戦闘判断はserver側で行います。
<!-- traceability: java-addon.equipment-hit-callback end -->

## equipment JSONを使う

固定値だけならItem APIを実装せず、addonのdatapackへ
`data/<namespace>/equipment/<path>.json`を置く方法が最も単純です。

```json
{
  "item": "your_addon:sonar_module",
  "variant": 0,
  "equip_type": "radar_lo",
  "compatible": ["cannon", "aircraft"],
  "enchant_type": "misc",
  "stats": {
    "shincolle_kai:hit": 0.1,
    "your_addon:sonar_precision": 0.25
  },
  "attack_effects": [
    {"effect": "minecraft:glowing", "amplifier": 0, "duration": 100, "chance": 25}
  ]
}
```

`attack_effects`は命中時に付与するMobEffectです。`effect`は登録済みResourceLocation、
`amplifier`は0始まり、`duration`はtick、`chance`は0〜100の百分率です。同じeffect IDを
JSONとItem API/providerが返した場合は動的なItem API/provider側が優先されます。戦闘計算は
server authoritativeで、クライアントへ同期された定義はtooltipとGUI表示専用です。

JSONのbase値とItem API/providerのdynamic値は加算されます。同じ値を両方へ重複して書かないで
ください。JSONはserver authoritativeで、login時と`/reload`後にclientへ同期されます。
表示用データを独自packetで複製する必要はありません。

完全なフィールド定義は[JSON Schema](schemas/shincolle_kai-equipment.schema.json)、追加Java MODなしの
導入例は[JSON-only datapack](../examples/equipment_datapack/README.md)、独自属性・Itemを登録する
一式は[compile-checked Java addon例](../examples/java_addon/README.md)を参照してください。
Schemaで検査できないItem/属性の実登録状態と、全定義横断の`item + variant`重複はserverが
reload時に検証します。

## 友軍艦と所有者を判定する

<!-- traceability: java-addon.player-ownership begin -->
友軍艦の内部classや所有UIDを参照せず、read-only契約を使います。

```java
if (target instanceof PlayerOwnedShip ship && ship.isOwnedByPlayer(player)) {
    // targetはこのplayerが所有するShinColle-kaiの友軍艦。
    // 能力変更等にはLivingEntity/Minecraft APIを使う。
}
```

敵艦、マウント、艦載機、projectileはこの契約を実装しません。このqueryは所有権を変更せず、
clientからの判定結果をserverのゲームロジックで信用してはいけません。
<!-- traceability: java-addon.player-ownership end -->

## Java addonとKubeJSの関係

Java addonとKubeJSは別の装備システムではありません。どちらも
`shincolle_kai:ship_attribute` registry、equipment JSON、同じloader/sync/resolverを通ります。
KubeJSを使う場合のstartup scriptと完全例は[こちら](kubejs_integration.md)を参照してください。

汎用Events、Conditions、Effects、Services、Script Effectsは未確定です。実在する利用例と
server authorityを定義できる段階で追加し、現時点では内部classを代用品にしないでください。
