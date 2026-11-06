# 標準艦のデータと見た目

[English](ship_addon_data.en.md) · 日本語版が正です。 [制作の入口](addon_development.md)

この文書では、組み込みの AI・攻撃で動く「標準艦」を、JSON とモデル・画像だけで追加する方法を説明します。Java は使いません。
初めての方は、まず見本をそのまま動かし、それをコピーして自分の艦に作り変える順で進めるのが近道です。

## 1. 対応版と配置

対象は ShinColle-kai **1.20.1-0.13.0**、Minecraft **1.20.1 / Forge**（開発環境: Forge 47.4.0、Java 17）です。
ファイルの `schema` は艦定義・visual とも **1** です。本体の版が違う場合、保存データや API の互換は保証しません。

艦は2つのファイルでできています。

- **艦定義**（データパック、サーバー側）: 能力・入手方法・AI と攻撃を決めます。
- **visual**（リソースパック、クライアント側）: モデル・画像・アニメーションを決めます。

見本の [README](../examples/ship_addon/README.md) のとおり、同じフォルダをサーバーのデータパックと各クライアントのリソースパックの両方に入れてください。
Minecraft 1.20.1 では、どちらも `pack_format: 15` です。シングルプレイでも両方が必要です。

```text
ship_addon/
  pack.mcmeta
  data/shincolle_example/shincolle_kai/ships/training_ship.json
  assets/shincolle_example/shincolle_kai/visuals/training_ship.json
```

ファイルの置き場所が、そのまま ID になります。

- 艦定義 `data/<namespace>/shincolle_kai/ships/<path>.json` の艦 ID は `<namespace>:<path>`。
- visual `assets/<namespace>/shincolle_kai/visuals/<path>.json` の visual ID は `<namespace>:<path>`。
- どの visual を使うかは、艦定義の `visual` で指定します。艦 ID と同じ名前でなくてもかまいません。
- 同じ ID のファイルを複数のパックが持つときは、優先順位の高いパックが使われます。
- 能力と入手条件はサーバーが決めます。見た目の資源はサーバーから配られないので、各クライアントに入れてください。

## 2. コピーしてから変更する

1. 見本を変更せずに入れ、README の `/give` で卵を出して呼び出します。モデルは鹿島と同じですが、鹿島とは別の艦です。
2. 配布用にフォルダをコピーし、`data/shincolle_example/` と `assets/shincolle_example/` を自分の名前空間（例: `my_fleet`）に変えます。
3. 両方の `training_ship.json` を自分の名前（例: `scout.json`）に変え、艦定義の `visual` を `my_fleet:scout` にします。
4. コマンドの `ShipId` も `my_fleet:scout` にします。間の `shincolle_kai/ships`・`shincolle_kai/visuals` は固定なので変えません。
5. 鹿島の資源を借りている間は、visual の `model`・`texture`・`animation` にある `shincolle_kai:` はそのままにします。
6. 自作のモデルや画像に置き換えるときに、それらを自分の名前空間に変え、ファイルを `assets/` に入れます。

名前空間とパスに使える文字は、小文字の英数字と `_`・`.`・`-` です（パスは `/` も可）。艦定義の中の ID とファイルから決まる艦 ID は、名前空間を含めて64文字までです。
一度配布した ID は保存データの識別子になるので、変えないでください。編集の前にはワールドをバックアップしておきましょう。

## 3. 艦定義

次の JSON は [見本の艦定義](../examples/ship_addon/data/shincolle_example/shincolle_kai/ships/training_ship.json) と同じ内容です。

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

各キーの型と数値の上限は、[公開艦 Schema](schemas/shincolle_kai-ship_definition.schema.json) が正本です。エディターに Schema を設定すると、入力の補完と検査が使えます。
1ファイルは最大65536 UTF-8バイト、入れ子は32段までです。キーの重複や、既知のキーに不正な値があると、その定義は丸ごと読み込まれません。知らないキーは警告を出して無視します。

| キー | 意味と省略時の扱い |
|---|---|
| `schema` | 必須。整数の1。`$schema` はエディター用の任意の文字列で、本体の版の指定ではありません |
| `display_name` | 表示名。最大64文字。省略すると艦 ID |
| `ship_type` | 必須の艦種。選べる値は [Schema の `ship_type`](schemas/shincolle_kai-ship_definition.schema.json#/properties/ship_type) を見てください。艦種の名前だけで艦載機や特殊攻撃が使えるようにはなりません |
| `visual` | 必須。クライアントの visual ID。サーバーはその資源が実在するかを確かめません |
| `dimensions.width` / `height` | 当たり判定の幅と高さ（ブロック単位）。省略すると0.5 / 1.5。正の float の最小値以上で、上限は1.6 / 4.4。visual の `scale` とは別の値です |
| `mount_offset` | 乗ったときの高さ。省略すると0.9、範囲は0〜4.4。艦のサイズ段階による倍率が別にかかります |
| `equip_type` | 装備の互換を決める数値の区分。省略すると1、整数0〜3 |
| `attack_profile` | 省略時の値と使える値は Schema の固定値を見てください。どの攻撃を使うかは `behavior` で選びます |
| `base_stats` | 必須の object。味方艦の基礎値6項目。省略した項目は0 |
| `growth` | 味方艦の成長値6項目。省略した項目は0。装備の属性 JSON とは別物です |
| `hostile_modifiers` | 敵個体のサイズ段階ごとの設定に掛ける6項目の倍率。各項目は省略すると1、正の float の最小値以上〜2.4。敵個体は `base_stats`・`growth` を使いません |
| `equipment.slots` | 初期装備リストに書ける件数の上限。省略すると0、整数0〜16 |
| `equipment.initial` | 装備定義 ID の配列（アイテム ID ではありません）。省略すると空。件数は `slots` 以下で、各 ID はサーバーの装備定義に実在する必要があります。同じ ID を別の枠に書くこともできます。読み込み時に検査されますが、この版では艦に初期装備を自動で持たせる手段としては使えません |
| `construction` / `spawn` | 入手の条件。省略すると無効。§4 を参照 |
| `behavior` | AI と攻撃の選択。§5 を参照 |
| `recruitment` | 予約済みのキー。この版では値にかかわらず無視します |
| `hostile_variants` | 敵の固有個体の抽選・名前・特性・補正。下記参照 |

能力値の6項目は `hp`（耐久）・`atk`（攻撃）・`def`（防御）・`spd`（攻撃速度）・`mov`（移動）・`hit`（命中）です。
基礎値と成長値は有限の非負の数で、項目ごとの上限は [base_stats](schemas/shincolle_kai-ship_definition.schema.json#/properties/base_stats) / [growth](schemas/shincolle_kai-ship_definition.schema.json#/properties/growth) にあります。
JSON に書いた値が、そのまま最終的な能力や GUI の表示になるわけではありません。

### 敵の固有個体の定義

`hostile_variants` は任意の object です。省略すると固有個体の定義はありません。
敵の新規艦が出現した後の最初のサーバー tick に、通常枠も含めて一度だけ抽選します。
決まった個体性は保存され、読み込み・次元移動・`/reload` で抽選し直しません。既存セーブの敵は通常個体のままです。
名前は手動の名前、固有名、艦種名の順に優先します。固有名の翻訳が無い場合は艦種名を使います。
`/reload` は敵専用補正の再計算に反映しますが、確定した名前・レア度・特性 ID・外観情報は変えません。Schema の版は引き続き1です。
管理者は `/shincolle ship spawn <ship_id> hostile <variant_id>` で特定の固有個体を召喚できます。不明な variant ID はエラーになります。

撃破で卵が落ちた場合、艦 ID・出自の variant ID・固有名・レア度・確定した特性 ID・visual variant を味方へ引き継ぎます。
加入は卵の使用者の味方としてレベル1から始まり、敵の規模・敵専用補正・育成値・装備・HP・所有者は持ち込みません。
ドロップの確率と時点は通常個体と同じです。加入後の赤い演出の情報は無効になりますが、visual variant の情報は残ります。
味方では `base_stats`・成長値・レベルから求めた元の値に特性だけを掛け、variant の敵専用補正は掛けません。
個体性は味方の保存・読み込み・定義欠落中の休眠・次元移動・死亡保存卵・サファリネット・魂符で保持します。
死亡保存卵では従来の育成値・装備・所有者の復元と併存し、新規加入のレベル1制限で保存値を削りません。
卵の説明には固有名・レア度・特性を表示します。保存された未知の特性 ID は保持して無効と表示し、効果だけを止めます。
不正・不明な艦 ID の卵は理由を表示して消費しません。将来の版の個体情報は変更せず保存し、対応できる版になるまでは効果を止めます。

以下は既存の艦定義に追加する断片です（単独の艦定義ファイルではありません）。

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

- `normal_weight`: 整数0〜10000、既定100。`variants`: 省略時は空、最大16件。
- 各 `id` は必須、`[a-z0-9_]{1,32}`。同じ艦の中で重複できません。艦 ID との組で識別する不変の ID なので、並び順や表示名を識別子にしないでください。
- `weight` は必須の整数0〜10000。合計の最大は170000。通常枠も含めてすべて0なら通常個体です。`rarity` は表示用で、抽選確率とは別です（`common` / `rare` / `epic` / `legendary`、既定 `rare`）。
- `name` は任意の翻訳キー、1〜128文字。`traits` は最大4件、重複不可、省略時は空。[Schema の特性 ID 一覧](schemas/shincolle_kai-ship_definition.schema.json#/$defs/shipTraitId)にある登録済みの ID だけを選べます。Java による独自特性の登録は公開 API ではありません。
- 組み込みの特性は `sturdy`=hp、`heavy_hitter`=atk、`armored`=def、`quick_loader`=spd、`swift`=mov、`keen_eye`=hit（すべて `shincolle_kai:`、倍率は仮の1.1）です。確定した特性 ID から敵と味方の能力に適用します。
- variant の `hostile_modifiers` は艦直下のものと同じ6キー・同じ範囲で、各既定1。敵の元の値（規模ごとの設定 × 艦直下の `hostile_modifiers`）に特性の倍率と variant の補正を掛け、その後に既存の装備・buff・上限を適用します。味方の元の値は `base_stats` と成長値なので、同じ特性倍率でも元の値は異なります。variant が定義から消えると敵専用補正だけが止まり、ID と確定した特性は保存に残ります。定義を戻すと補正が戻ります。
- `visual_variant` は個体ごとの texture の ResourceLocation（明示的な名前空間つき、最大128文字）。艦ID・通常の資源IDの64文字上限、variant IDの規則は変わりません。geometry / animation は差し替えません。未指定なら基本texture、指定したtextureが欠落すれば模型全体をplaceholderにし、資源復旧後の成功reloadで復帰します。艦visual自体が欠落したときもplaceholderを優先します。サーバーは資源の実在を検査しません。加入後もtextureは残ります。
- `red_aura` は任意の真偽値、既定 `true`。生存・非休眠・可視の敵の新規艦だけに赤い半透明layerと小さな粒子を表示し、加入後は停止します。cameraから32block未満で通常の表示条件とfrustumを満たす敵を距離順に16体まで選びます（同距離はentity ID順、壁越しoutlineなし）。textureと名前はこの制限と独立です。
- 赤粒子は各艦の連続20tickで4個以下・最短5tick間隔、全体で連続20tickに64個以下・生存64個以下・寿命20tick以下。最小粒子設定では新規発生せず、減少設定では発生を減らします。描画fpsで増えません。world/camera切替・切断・成功reloadで演出状態を破棄します。赤layerはRGB `(1, 0.15, 0.15)`・alpha `0.25`、通常の奥行き判定・既存Glowを維持します。
- 上限超え・型違い・不正値・不明な特性 ID は艦定義全体の拒否になります。他の艦定義は読み込まれます。未知のキーだけは従来どおり警告して無視します。

## 4. 入手経路

### 建造

`construction.enabled` の既定は `false` です。有効にしても `weight: 0` なら抽選に入りません。
`mode`・`tier`・`material_bias` で選べる値は [construction の Schema](schemas/shincolle_kai-ship_definition.schema.json#/$defs/construction) にあります。

- **material**（既定）: 投入した素材で結果が変わる通常の建造の候補に入ります。`tier` と `material_mean` が必須です。
  `material_mean` と投入素材の合計との距離が、建造の分布の計算に使われます。`material_bias` を書くと、その素材の量を mean から差し引きます。
  `weight` の既定は100で、これが倍率1です。200なら倍率2になります。確定した百分率ではありません。
- **weighted**: 素材とは関係なく、`weight / 10000` の確率で選ばれる別枠です。`weight` の既定は0です。
  `tier` を省略すると、小型・大型の両方の建造が対象です。対象の weighted 定義の合計が50%を超えると、合計が50%になるように比例して縮めます。外れたときは通常の素材依存の候補から抽選します。
- `weight`・`material_mean` は0〜2147483647の整数です。`material_bias` を省略すると補正はかかりません。

艦は建造が終わった時点で決まり、卵の `ShipId` に保存されます。卵を使うたびに抽選し直すことはありません。

### 野生の湧きとドロップ

`spawn.enabled` の既定は `false`、`weight` の既定は0で、整数0〜2147483647です。
有効にして正の重みを付けると、通常の敵艦の抽選に加わります。ボスの抽選には入りません。通常の湧きの条件はそのまま必要なので、`enabled` は艦をすぐに出現させるスイッチではありません。
重みの目安として、組み込み艦の合計は700、組み込みの駆逐艦1隻は46です。同梱の艦や他の定義の分も加わるので、`weight` は百分率ではありません。
敵個体を倒すと、同じ艦 ID の**味方の卵**を一定の確率で落とします。`spawn` はこの卵のドロップをオン・オフする設定ではありません。

### 卵とコマンド

[README の `/give`](../examples/ship_addon/README.md#出し方) で、艦 ID の入った卵を手に入れられます。
`Hostile:0b` なら使った人の味方艦、`Hostile:1b` なら敵個体として出ます。定義が無い ID の卵は使えません。
NBT を付けた `/summon shincolle_kai:generic_ship ~ ~ ~ {ShipId:"shincolle_example:training_ship"}` でも艦 ID を指定できますが、所有者や初期の補給は設定されません。
味方艦の動作を確かめるときは卵を使ってください。`ShipId` の無い汎用艦はサーバーに追加されません。

## 5. AI と攻撃

選べる ID の一覧は、Schema の **[`$defs/shipAiId`](schemas/shincolle_kai-ship_definition.schema.json#/$defs/shipAiId)** と **[`$defs/shipAttackId`](schemas/shincolle_kai-ship_definition.schema.json#/$defs/shipAttackId)** にあります。

- `behavior.ai` を省略すると `shincolle_kai:standard`（味方・敵それぞれの標準の行動）です。
- `behavior.attacks` を省略すると、味方は `light_cannon`・`heavy_cannon`・`melee`、敵は `light_cannon`・`heavy_cannon` です（いずれも `shincolle_kai:`）。
- 書く場合は1〜16件で、重複はできません。空の配列 `attacks: []`、一覧に無い ID、別の名前空間の ID を書くと、定義が丸ごと読み込まれません。
- `aircraft`・`*_skill` は予約済みで、この版で指定すると定義が丸ごと読み込まれません。
- `melee` は、味方では提督が近接攻撃を有効にしているときだけ使います。敵では、書けば近接攻撃をするようになります。
- `light_cannon`・`heavy_cannon` の片方だけを選ぶと、GUI でもう片方を有効にしていても撃ちません。弾薬や行動の条件など、通常の制約はそのまま残ります。

## 6. visual と Blockbench

まず [見本の visual JSON](../examples/ship_addon/assets/shincolle_example/shincolle_kai/visuals/training_ship.json) をコピーしてください。
すべてのキーと上限は [visual の Schema](schemas/shincolle_kai-ship_visual.schema.json) にあります。必須は `schema`・`model`・`texture`・`animation` です。
資源 ID は拡張子まで含めた `namespace:path` の形で、最大256文字です。見本は本体の鹿島の3つの資源を参照しているので、絵やモデルを複製していません。

| 任意のキー | 意味と省略時の扱い |
|---|---|
| `scale` / `shadow_radius` | 表示の倍率（既定1）と影の大きさ（既定0.35）。倍率は正の float の最小値以上〜16、影は0〜16 |
| `rig` | 役割と骨名の対応。省略した役割は、役割と同じ名前の骨を探します |
| `animations` | 姿勢・攻撃のイベントとクリップ名の対応。省略するとイベントのキーと同じ名前のクリップを使います |
| `expressions` | 表情の切り替えに使う骨のリスト。書く場合は空や重複にできません。件数の上限は Schema を見てください |
| `glow_bones` | 発光させる骨（子孫も含む）。省略すると空 |
| `head_follow` | 頭の回転に追従させる骨。省略すると対象なし。係数の既定は0.5で、`overrides` で骨ごとに x / z の係数を変えられます。追従できない骨や存在しない骨を書くと visual が読み込まれません |
| `held_item.offset` / `rotate` | 手に持つ物の位置と回転の補正。各3つの数値で、既定は `[0,0,0]` |
| `lod.distance` / `hide_bones` | 遠くから見たときに隠す骨（子孫も含む）。距離の既定は48で範囲は1〜256。骨は省略すると空 |

骨名は1〜128文字で、制御文字は使えません。リストや係数の上限も Schema で確認してください。

### 鹿島を制作の出発点にする

本体の JAR から `assets/shincolle_kai/model_sources/kashima/kashima.bbmodel` を取り出し、そのコピーを Blockbench で開きます。
このファイルの形式は 4.10 / bedrock です。`.bbmodel` は制作用の元ファイルで、ゲームが直接読むモデルではありません。
編集したら、モデルとアニメーションをゲーム用の `.geo.json`・`.animation.json` として、画像を `.png` として自分の `assets/<namespace>/` に置き、visual の3つの参照をそれに合わせます。
クリップ名を変えたら `animations` を、骨名を変えたら `rig`・表情・追従・LOD などの参照も合わせて直してください。

鹿島の visual では、`rig` は次のように対応しています。

| `rig` キー | 鹿島の骨名 |
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

`root`・`held_item_left` は鹿島の `rig` では指定していません。鹿島のモデルがすべての役割を埋めているわけではない点に注意してください。

### 資源が欠けている・合わないとき

- visual が無いか読み込めなかった場合は、仮のモデル（placeholder）で表示されます。visual が欠けているだけでは、サーバーの艦定義は拒否されません。
- モデル・画像・アニメーションのファイルが無いときや、visual の中身が不正なときは、その visual が読み込まれません。
- `rig` に書いた骨が見つからないときは、その役割の機能だけが無効になります。
- 姿勢のクリップが無いときは次に使える姿勢へ切り替わり、攻撃のクリップが無いときはその演出を省きます。
- 発光（Glow）や LOD に書いた骨が無いときは、それぞれの機能が無効になります。`head_follow` の場合は visual ごと読み込まれないので、扱いが違います。

## 7. reload・保存・削除

- サーバーの `/reload` で、艦定義と装備定義を読み直します。すでに出ている艦も次の tick に更新され、AI・攻撃の Goal が作り直されます。
  攻撃の途中だった動作は途切れることがあります。目標を選ぶ Goal は作り直しません。
- クライアントの visual・モデル・画像を更新するには、リソースパックの再読み込み（F3+T）を使います。サーバーの `/reload` とは別の操作です。
- `ShipId` は保存データに残ります。保存・再起動をまたぐときは、同じ版・同じ ID の定義を残しておいてください。
- 定義を消したり読み込めなかったりしても、すでにいる艦は消えません。休眠状態になって移動と攻撃を止め、元の NBT を保持します。同じ ID の正しい定義を戻して `/reload` すれば元に戻ります。
- ファイル名や名前空間を変えると、別の ID になります。古い ID の艦が新しい ID へ自動で移ることはないので、古い定義を戻せるようにバックアップしておいてください。

## 8. ログから直す

サーバーの `logs/latest.log` と、クライアント側の同じ名前のログを取り違えないでください。以下は、時刻や logger の接頭辞を除いた実際のメッセージの例です。

```text
Rejected ship definition shincolle_example:training_ship: behavior.attacks must not be empty; omit it for the default attacks
Rejected ship definition shincolle_example:training_ship: unknown or unselectable attack 'shincolle_kai:typo'
Rejected ship definition shincolle_example:training_ship: attack 'shincolle_kai:aircraft' is registered but cannot be selected in 1.0
Missing ship definition shincolle_example:training_ship; keeping entity dormant and preserving its NBT
Missing/rejected ship visual shincolle_example:training_ship; using placeholder
```

1. 最初に出た拒否の行で ID と理由を確かめ、ファイルの場所・値の型・Schema の一覧を直します。`attacks` が空なら、キーごと消すか、選べる ID を1つ以上書きます。
2. サーバーで `/reload` します。`Loaded ... ship definitions (generation ...)` が出ても、個別に拒否された定義が残っていないか確かめてください。
3. 仮のモデルで表示されるときは、クライアントのパックを確かめ、資源 ID・骨名・クリップ名を直して F3+T を押します。
   `Rejected ship visual <id>; using placeholder: <例外>` や `Ship visual <id> lacks role <role>; feature disabled` も手がかりになります。
4. ワールドをバックアップしてから、卵での呼び出し・表示・保存・再起動・reload を一通り確かめます。
