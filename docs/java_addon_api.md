# ShinColle-kai Java Addon API

更新: 2026-10-20

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
versionRange = "[1.20.1-1.0.0,1.20.1-2)"
ordering = "AFTER"
side = "BOTH"
```

`versionRange`の上限`1.20.1-2`は、API版が変わる次の大きな版（2.0.0）の手前です。
1.0より前の本体の版はAPIを固定していないため、1.0より前の本体で試すときは範囲を広げてください。

本体内部の`com.lulan.shincolle.entity`、`utility`、`reference.ID`、`capability`などへ直接依存せず、
`com.lulan.shincolle.api`とMinecraft/Forge APIだけを利用してください。

## 安定APIとexperimental API

| 区分 | パッケージ | 含まれるもの |
|---|---|---|
| 安定 | `com.lulan.shincolle.api` | `ShinColleApi`（`MOD_ID`、`API_VERSION`） |
| 安定 | `api.attribute` | 艦属性の型、layout（読み取りの`current()`）、registryのキー |
| 安定 | `api.equipment` | `IShipEquipment`、`ShipEquipmentContext`、`ResolvedShipEquipment`、`ShipAttackEffect`、`ShipEquipmentProvider`、`ShipEquipmentProviders`（`REGISTRY_KEY`だけ） |
| 安定 | `api.ship` | `PlayerOwnedShip` |
| 安定 | `api.target` | `CoreTargetTraits`（IDの定数とタグを作る関数） |
| experimental | `api.experimental.equipment` | `ShipEquipmentHitCallback` |
| experimental | `api.experimental.target` | `TargetTrait`、`TargetTraitRegistries.REGISTRY_KEY` |

安定APIは、公開の型・メソッドの削除、シグネチャの変更、意味の変更をするときだけ
`ShinColleApi.API_VERSION`を上げます（現在は`1`）。追加では上げません。`api.experimental`は
予告なく変わり、`API_VERSION`の対象外です。`API_VERSION`は実行中に確かめるための値で、
依存の宣言は上の`versionRange`（本体の版）で行います。

艦属性のlayoutを作る・差し替える公開の入口はありません。`ShipAttributeLayout.current()`は、
registryが凍結された後（`FMLCommonSetupEvent`以降）にだけ読めます。それより前に呼ぶと
`IllegalStateException`になります。

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
entity、元のItemStackを変更してはいけません。

攻撃命中後の副作用が必要な場合だけ、`IShipEquipment`（またはproviderの`ShipEquipmentProvider`）に加えて
`com.lulan.shincolle.api.experimental.equipment.ShipEquipmentHitCallback`を実装し、
`onShipHit(LivingEntity, Entity, float, ItemStack)`を書きます。これはexperimentalで、
攻撃の公開APIと一緒に形が変わりえます。実装したItemまたはproviderだけが呼ばれ、
戦闘判断はserver側で行います。
<!-- traceability: java-addon.equipment-hit-callback end -->

## 既存のItemをproviderで装備として扱う

自分で作っていないItem（ほかのMODの道具など）は`IShipEquipment`を実装できません。その場合は
`ShipEquipmentProvider`を実装し、`ShipEquipmentProviders.REGISTRY_KEY`のForge registryへ登録します。
`matches`は副作用のない判定、`resolveShipEquipment`は`IShipEquipment`と同じ解決です。

```java
public static final DeferredRegister<ShipEquipmentProvider> PROVIDERS =
        DeferredRegister.create(ShipEquipmentProviders.REGISTRY_KEY, "your_addon");
public static final RegistryObject<ShipEquipmentProvider> SONAR =
        PROVIDERS.register("sonar_tools", SonarToolProvider::new);
```

`PROVIDERS.register(modEventBus)`を忘れずに呼びます。複数のproviderが一致しうるときは、
`priority()`が大きいものから順に調べ、同じ値ならregistry IDの昇順です（既定は0）。最初に
`matches`が真になったproviderだけが使われ、それが解決に失敗しても下位のproviderへは
フォールバックしません。Item自身が`IShipEquipment`ならproviderより優先されます。
registryは起動時に凍結され、保存も同期もされず、上書きもできません。IDは必ずaddon自身の
namespaceを使います。

## 目標のtraitをタグで割り当てる

艦が目標を選ぶときの区分（対空の対象、対潜の対象）は、entity typeのタグで割り当てます。
Javaを書く必要はありません。traitのID `<namespace>:<path>` に対応するタグは
`<namespace>:shincolle_kai/target_trait/<path>`で、ファイルは次の場所です。

```
data/<namespace>/tags/entity_types/shincolle_kai/target_trait/<path>.json
```

本体のtraitは2つだけで、IDは`CoreTargetTraits.ANTI_AIR_ELIGIBLE`
（`shincolle_kai:anti_air_eligible`）と`CoreTargetTraits.ANTI_SUBMARINE_ELIGIBLE`
（`shincolle_kai:anti_submarine_eligible`）です。自分の飛行体を対空の対象にするには、
`data/shincolle_kai/tags/entity_types/shincolle_kai/target_trait/anti_air_eligible.json`を
addonのdata packに置きます。

```json
{
  "values": [
    "your_addon:seaplane"
  ]
}
```

タグはdata packの再読み込み（`/reload`）で変わるので、割り当ても再読み込みで変わります。
艦載機・ミサイル・潜水型のように、実体のクラスで決まる区分は今までどおりで、タグで外せません。
`CoreTargetTraits.tag(id)`は、traitのIDからこのタグのキーを作ります。

新しいtraitの登録（`TargetTraitRegistries.REGISTRY_KEY`の`DeferredRegister<TargetTrait>`、
`com.lulan.shincolle.api.experimental.target`）はexperimentalです。本体が読むのは上の2つだけで、
addonが登録したtraitを読む側は、1.0より後の公開AI APIと一緒に作ります。それまでは形が変わりえます。

## equipment JSONを使う

固定値だけならItem APIを実装せず、addonのdatapackへ
`data/<namespace>/shincolle_kai/equipment/<path>.json`を置く方法が最も単純です。

```json
{
  "schema": 4,
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

`schema`は必須の整数で、現在は`4`だけを受け付けます。欠けている、整数でない、版が違う定義は
その定義だけ拒否され、ほかの定義は読み込まれます。旧パス`data/<namespace>/equipment/`は
読まれません。JSONがあれば、名前空間ごとに警告が1回ログへ出ます。

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
