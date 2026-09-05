# ShinColle-kai — 深これ / Abyssal Fleet & Ship Girls for Minecraft 1.20.1

[![CI](https://github.com/halkirisame/ShinColle-kai/actions/workflows/ci.yml/badge.svg)](https://github.com/halkirisame/ShinColle-kai/actions/workflows/ci.yml)

**ShinColle-kai（深これ）**は、Minecraft Java Edition 1.20.1 / Forge向けの
艦隊育成・戦闘MODです。プレイヤーは深海棲艦側となり、深海棲艦を建造・育成・指揮して、
敵として現れる艦娘と戦います。撃破した艦娘を仲間にすることもできます。
ShinColleは日本語圏で「深これ」として知られる、艦これ風の非公式Minecraft MODです。

艦娘・深海棲艦、艦隊戦、建造、レベリング、婚約、主砲・魚雷・艦載機などの装備を追加します。
KubeJS、datapack、Javaアドオンから独自装備・艦属性を追加できる拡張基盤も備えています。

PinkaLulan氏作のShinColleを、kousakirai氏がForge 1.20.1へ移植したもの(ShinColle-Reforge)の
派生版(fork)です。移植版に残っていた不具合の修正と、移植時に取りこぼされた挙動の復元を
進めています。

## 概要

<!-- traceability: readme.gameplay.abyssal-side begin -->
- **深海棲艦側で遊ぶ艦隊戦** — 深海側の艦を建造・育成・指揮。野生の艦娘は敵として出現します
<!-- traceability: readme.gameplay.abyssal-side end -->
<!-- traceability: readme.gameplay.recruit begin -->
- **艦娘を仲間にする** — 敵の艦娘を撃破し、入手したスポーン卵から味方として迎えられます
<!-- traceability: readme.gameplay.recruit end -->
<!-- traceability: readme.gameplay.small-construction begin -->
- **小型建造** — 深海棲艦を小型造船所で建造できます
<!-- traceability: readme.gameplay.small-construction end -->
<!-- traceability: readme.gameplay.leveling begin -->
- **レベリング** — 艦を育成できます
<!-- traceability: readme.gameplay.leveling end -->
<!-- traceability: readme.gameplay.marriage begin -->
- **婚約** — 艦と婚約できます
<!-- traceability: readme.gameplay.marriage end -->
<!-- traceability: readme.gameplay.equipment begin -->
- **装備** — 主砲・魚雷・艦載機など。艦の性能を変化させます
<!-- traceability: readme.gameplay.equipment end -->
<!-- traceability: readme.extension.kubejs begin -->
- **KubeJS拡張** — スクリプトから独自の艦属性と装備を追加できます
<!-- traceability: readme.extension.kubejs end -->
<!-- traceability: readme.extension.datapack begin -->
- **datapack拡張** — datapackから独自の装備を追加できます
<!-- traceability: readme.extension.datapack end -->
<!-- traceability: readme.extension.java-addon begin -->
- **Javaアドオン拡張** — Javaアドオンから独自の艦属性と装備を追加できます
<!-- traceability: readme.extension.java-addon end -->

## 導入方法

1. Minecraft Java Edition 1.20.1へForge 47系を導入します
2. 配布されたShinColle-kaiのJARを`mods`フォルダへ入れます
3. マルチプレイではサーバーと参加クライアントの両方へ同じバージョンを入れます

ShinColle-ReforgeとはMOD IDが異なります。既存データは引き継がれないため、
**初回は新規ワールドで遊ぶことを推奨します。**

## 動作環境

- Minecraft 1.20.1 / Forge 47系(`[47,)`)。開発・検証は 47.4.0 で行っています

任意で連携するMOD(無くても動作します):

| MOD | 連携内容 |
|---|---|
<!-- traceability: readme.optional.curios begin -->
| Curios | 艦の装備スロット |
<!-- traceability: readme.optional.curios end -->
<!-- traceability: readme.optional.tinkers begin -->
| Tinkers' Construct | 修飾子を艦の攻撃効果へ変換 |
<!-- traceability: readme.optional.tinkers end -->
<!-- traceability: readme.optional.kubejs begin -->
| KubeJS | 独自の艦属性・装備をスクリプトから追加 |
<!-- traceability: readme.optional.kubejs end -->
<!-- traceability: readme.optional.jei begin -->
| JEI | 艦GUIとアイテム一覧の表示競合を回避 |
<!-- traceability: readme.optional.jei end -->

## ShinColle-Reforgeとの違い

ShinColle-kaiは単なる名称変更版ではありません。Forge 1.20.1移植で残った不具合を修正し、
ShinColle 1.10.2のプレイヤーが体感できる挙動・演出を復元しながら、内部を現代のMOD環境へ
合わせています。装備datapack、KubeJS連携、Java Public APIにより、第三者が装備や艦属性を
追加できることも本派生版の重点です。

## 互換性

<!-- traceability: readme.reforge-compatibility begin -->
**MOD IDを `shincolle` から `shincolle_kai` へ変更したため、*ShinColle-Reforge* との
互換性はありません。**内容ごとに分けて説明します。

| 種別 | 状態 |
|---|---|
| 起動互換性 | **可**。Reforgeで作ったワールドをクラッシュせず開けます |
| データ互換性 | **不可**。旧IDで保存された艦娘・アイテム・装備は引き継げません |
| セーブ互換性 | **不可**。Reforgeからの継続プレイはできません |
| API互換性 | **不可**。旧IDを前提にしたaddon・KubeJSスクリプトは動きません |

設定ファイルは新規生成されます。旧設定は引き継がれません。
<!-- traceability: readme.reforge-compatibility end -->

## 移行

<!-- traceability: readme.reforge-migration begin -->
Reforgeのワールドを開くこと自体はできますが、**自動移行は行われません。**

1. Reforgeで作った艦娘・アイテムは、ワールドを開いた時点で失われます。
   残したい場合はReforgeのまま遊び続けてください
2. 設定は `config/shincolle_kai-*.cfg` として新規に作られます。
   旧 `shincolle-*.cfg` の値を引き継ぎたい場合は手で書き写してください
3. KubeJSスクリプトとdatapackは、`shincolle:` を `shincolle_kai:` へ書き換えてください
4. Javaアドオンは再コンパイルが必要です。API境界は
   [docs/java_addon_api.md](docs/java_addon_api.md) を参照してください

**新規ワールドで始めることを推奨します。**
<!-- traceability: readme.reforge-migration end -->

## 既知の問題

現在把握している、遊ぶうえで影響のある問題です。

**検証中**は、修正を実装して自動テストは通ったものの、実際のゲーム内での確認が
まだ終わっていないものです。直っているかどうかは未確定として扱ってください。

<!-- traceability: readme.known-issue.pointer-single-ship begin -->
- **検証中: 指揮棒の単艦モード。** 選択した艦が全て反応する問題を修正しました。
  自動テストと独立検証は通過していますが、実機確認が残っています
<!-- traceability: readme.known-issue.pointer-single-ship end -->
<!-- traceability: readme.known-issue.ship-tasks-crane begin -->
- **未修正: 艦娘タスク（採掘・釣りなど）とクレーンは未検証です。** 1.20.1移植時の
  欠落が未調査のため、動作しない可能性があります
<!-- traceability: readme.known-issue.ship-tasks-crane end -->
<!-- traceability: readme.known-issue.particles begin -->
- **未修正: 1.10.2にあったパーティクル49種のうち24種が未移植です**（見た目のみ）
<!-- traceability: readme.known-issue.particles end -->
<!-- traceability: readme.known-issue.large-construction begin -->
- **検証中: 大型建造。** 大型造船所を構成するブロック（多金属ブロック・深海重怨念
  ブロック）を右クリックで設置できず造船所を組み立てられない問題を修正しました。
  実際に建造できるかは未確認です
<!-- traceability: readme.known-issue.large-construction end -->
<!-- traceability: readme.known-issue.emotion begin -->
- **検証中: 艦の感情・反応。** 撫でる・被弾する・攻撃する・待機する・命令する・
  艦娘タスクのいずれでも感情の表示、音声、士気の変動、押し返し、反撃が起きない
  問題を修正しました。表情やパーティクルの表示は未確認です
<!-- traceability: readme.known-issue.emotion end -->
<!-- traceability: readme.known-issue.shipyard-vortex begin -->
- **検証中: 大型造船所の渦**が建造中も停止時と同じ表示のままだった問題を
  修正しました（見た目のみ）。表示は未確認です
<!-- traceability: readme.known-issue.shipyard-vortex end -->

不具合の報告は [Issues](https://github.com/halkirisame/ShinColle-kai/issues) へお願いします。

## β版 v1.20.1-0.9.0 について

配布されている最新版は `v1.20.1.0.8.2`(α版)です。次のリリースは **β版 `0.9.0`** になります。

造船所を建てて艦を建造し、装備させ、艦隊を指揮して戦い、育てて婚約するところまで
一通り遊べます。KubeJS連携・装備datapack・Javaアドオン向けPublic APIはこの版が初出です。
艦が攻撃・反撃しない不具合なども修正しました。

**ただし未完成の領域と既知の不具合があります。**`1.0.0` はそれらが解消された版の
ために取ってあります。

既知の不具合: 撫でても士気が上がらない / 艦が手持ちアイテムを表示しない /
移動指示のマーカーが約0.8ブロック浮く。

未完成の領域: 艦娘タスクとクレーン(本フォークで未着手) / 艦ごとの固有戦闘演出 /
艦AIの作り直し(新コードは本版ではゲーム側から到達しません)。

変更点は [CHANGELOG.md](CHANGELOG.md) をご覧ください。

## ドキュメント

- [CHANGELOG.md](CHANGELOG.md) — 変更履歴
- [docs/kubejs_integration.md](docs/kubejs_integration.md) — KubeJSから艦属性・装備を追加する
- [docs/java_addon_api.md](docs/java_addon_api.md) — Javaアドオン向けPublic APIの境界と例
- [examples/](examples/) — 装備datapackとJavaアドオンの動くサンプル

## 連絡先

本フォークについてのご要望・不具合報告は、こちらへお願いします。

- issue: https://github.com/halkirisame/ShinColle-kai/issues
- X: https://x.com/hal_kirisame

**本家および移植版の作者へのお問い合わせはご遠慮ください。** 本フォークは両氏とは独立して保守されています。

## 由来

- 原作: PinkaLulan氏 — https://github.com/PinkaLulan/ShinColle
- Forge 1.20.1 移植: kousakirai氏 — https://github.com/kousakirai/ShinColle-Reforge

MITライセンスのもとで公開されています。詳細は [LICENSE](LICENSE) をご覧ください。

---

**ShinColle-kai** is an abyssal-fleet and ship-girl combat mod for Minecraft Java
Edition 1.20.1 with Forge. Build, raise, equip and command abyssal ships against hostile
ship girls, then recruit defeated ship girls as allies.
It is an unofficial, Kantai Collection-inspired continuation of the ShinColle mod.

It adds fleet construction, leveling, marriage, cannons, torpedoes, aircraft and other
equipment. Datapacks, KubeJS scripts and Java addons can define custom equipment and
ship attributes.

It is a fork of ShinColle-Reforge, kousakirai's Forge 1.20.1 port of PinkaLulan's
ShinColle, fixing bugs left in that port and restoring behaviour lost in the move from
1.10.2.

## Overview

<!-- traceability: readme.gameplay.abyssal-side-en begin -->
- **Fight for the abyssal fleet** — build, raise and command abyssal ships; wild ship
  girls appear as enemies
<!-- traceability: readme.gameplay.abyssal-side-en end -->
<!-- traceability: readme.gameplay.recruit-en begin -->
- **Recruit ship girls** — defeat hostile ship girls and bring them back as allies
<!-- traceability: readme.gameplay.recruit-en end -->
<!-- traceability: readme.gameplay.small-construction-en begin -->
- **Small construction** — build abyssal ships at a small shipyard
<!-- traceability: readme.gameplay.small-construction-en end -->
<!-- traceability: readme.gameplay.leveling-en begin -->
- **Leveling** — raise ships
<!-- traceability: readme.gameplay.leveling-en end -->
<!-- traceability: readme.gameplay.marriage-en begin -->
- **Marriage** — marry ships
<!-- traceability: readme.gameplay.marriage-en end -->
<!-- traceability: readme.gameplay.equipment-en begin -->
- **Equipment** — cannons, torpedoes, aircraft and more, changing a ship's stats
<!-- traceability: readme.gameplay.equipment-en end -->
<!-- traceability: readme.extension.kubejs-en begin -->
- **KubeJS extensions** — scripts can add custom ship attributes and equipment
<!-- traceability: readme.extension.kubejs-en end -->
<!-- traceability: readme.extension.datapack-en begin -->
- **Datapack extensions** — datapacks can add custom equipment
<!-- traceability: readme.extension.datapack-en end -->
<!-- traceability: readme.extension.java-addon-en begin -->
- **Java addon extensions** — Java addons can add custom ship attributes and equipment
<!-- traceability: readme.extension.java-addon-en end -->

## Installation

1. Install Forge 47.x for Minecraft Java Edition 1.20.1
2. Put the ShinColle-kai JAR in the `mods` folder
3. For multiplayer, install the same version on both the server and every client

ShinColle-kai uses a different mod ID from ShinColle-Reforge, so existing data is not
migrated. **Starting in a new world is recommended.**

## Requirements

- Minecraft 1.20.1 / Forge 47.x (`[47,)`); developed and tested against 47.4.0

Optional integrations (all work fine when absent):

| Mod | What it adds |
|---|---|
<!-- traceability: readme.optional.curios-en begin -->
| Curios | Equipment slots for ships |
<!-- traceability: readme.optional.curios-en end -->
<!-- traceability: readme.optional.tinkers-en begin -->
| Tinkers' Construct | Modifiers converted into ship attack effects |
<!-- traceability: readme.optional.tinkers-en end -->
<!-- traceability: readme.optional.kubejs-en begin -->
| KubeJS | Custom ship attributes and equipment from scripts |
<!-- traceability: readme.optional.kubejs-en end -->
<!-- traceability: readme.optional.jei-en begin -->
| JEI | Keeps its item list clear of the ship GUI |
<!-- traceability: readme.optional.jei-en end -->

## Differences from ShinColle-Reforge

ShinColle-kai is more than a rename. It repairs defects left in the Forge 1.20.1 port,
restores player-visible behaviour and effects from ShinColle 1.10.2, and modernizes the
internal integration layer. Equipment datapacks, KubeJS support and a public Java API
let third-party authors add equipment and ship attributes.

## Compatibility

<!-- traceability: readme.reforge-compatibility-en begin -->
**The mod id changed from `shincolle` to `shincolle_kai`, so this is not compatible with
*ShinColle-Reforge*.** Broken down by kind:

| Kind | Status |
|---|---|
| World loading | **Works.** A world created with Reforge opens without crashing |
| Data | **Broken.** Ships, items and equipment saved under the old id are not carried over |
| Save continuity | **Broken.** You cannot continue a Reforge playthrough |
| API | **Broken.** Addons and KubeJS scripts written against the old id will not work |

Config files are regenerated; old settings are not carried over.
<!-- traceability: readme.reforge-compatibility-en end -->

## Migration from Reforge

<!-- traceability: readme.reforge-migration-en begin -->
A Reforge world will open, but **nothing is migrated automatically.**

1. Ships and items created in Reforge are lost the moment the world is opened.
   Keep playing on Reforge if you want to keep them
2. Config is recreated as `config/shincolle_kai-*.cfg`. Copy values across by hand from
   the old `shincolle-*.cfg` if you want to keep them
3. For KubeJS scripts and datapacks, replace `shincolle:` with `shincolle_kai:`
4. Java addons must be recompiled. See
   [docs/java_addon_api.md](docs/java_addon_api.md) for the API boundary

**Starting a new world is recommended.**
<!-- traceability: readme.reforge-migration-en end -->

## Known Issues

Problems currently known to affect play.

**In verification** means a fix is implemented and the automated tests pass, but it
has not yet been confirmed in an actual game session. Treat whether it is fixed as
undetermined.

<!-- traceability: readme.known-issue.pointer-single-ship-en begin -->
- **In verification: the pointer's single-ship mode.** The bug that made every selected
  ship respond has been fixed and independently reviewed, but still needs an in-game
  check
<!-- traceability: readme.known-issue.pointer-single-ship-en end -->
<!-- traceability: readme.known-issue.ship-tasks-crane-en begin -->
- **Not fixed: ship tasks (mining, fishing) and the crane are unverified.** Gaps from
  the 1.20.1 port have not been investigated, so they may not work
<!-- traceability: readme.known-issue.ship-tasks-crane-en end -->
<!-- traceability: readme.known-issue.particles-en begin -->
- **Not fixed: 24 of the 49 particle types from 1.10.2 are not ported** (cosmetic only)
<!-- traceability: readme.known-issue.particles-en end -->
<!-- traceability: readme.known-issue.large-construction-en begin -->
- **In verification: large construction.** The blocks that make up the large shipyard
  (polymetal block, heavy grudge block) could not be placed by right-clicking, so the
  shipyard could not be assembled. This is fixed; whether construction actually works
  is unconfirmed
<!-- traceability: readme.known-issue.large-construction-en end -->
<!-- traceability: readme.known-issue.emotion-en begin -->
- **In verification: ship emotional reactions.** Petting, taking damage, attacking,
  idling, being commanded and ship tasks produced no emotion display, voice, morale
  change, pushback or retaliation. This is fixed; the on-screen expressions and
  particles are unconfirmed
<!-- traceability: readme.known-issue.emotion-en end -->
<!-- traceability: readme.known-issue.shipyard-vortex-en begin -->
- **In verification: the large shipyard's vortex** stayed in its idle appearance even
  while building. This is fixed (cosmetic only); the appearance is unconfirmed
<!-- traceability: readme.known-issue.shipyard-vortex-en end -->

Please report bugs at
[Issues](https://github.com/halkirisame/ShinColle-kai/issues).

## About the beta release, v1.20.1-0.9.0

The latest distributed build is `v1.20.1.0.8.2` (alpha). The next release is the
**beta, `0.9.0`**.

The mod is playable end to end - build a shipyard, construct ships, equip them, command
a fleet, fight, level up and marry. This release is the debut of KubeJS integration,
datapack equipment and the public Java addon API. Ships not attacking or retaliating,
among other bugs, have been fixed.

**Some systems are known to be incomplete.** `1.0.0` is reserved for the release where
they are closed.

Known defects: petting does not raise morale; ships do not render a held item; a
movement-order marker floats about 0.8 blocks above its destination.

Incomplete systems: ship work tasks and the crane (untouched by this fork); per-ship
combat effects; the ship AI rewrite (its new code is not reachable from gameplay here).

See [CHANGELOG.md](CHANGELOG.md) for the changes.

## Documentation

- [CHANGELOG.md](CHANGELOG.md) — what has changed
- [docs/kubejs_integration.md](docs/kubejs_integration.md) — adding ship attributes and
  equipment from KubeJS
- [docs/java_addon_api.md](docs/java_addon_api.md) — public API boundaries and examples
- [examples/](examples/) — working samples: an equipment datapack and a Java addon

## Contact

For requests and bug reports about this fork, please use:

- Issues: https://github.com/halkirisame/ShinColle-kai/issues
- X: https://x.com/hal_kirisame

**Please do not contact the authors of the original mod or of the 1.20.1 port about this
fork.** It is maintained independently of both.

## Lineage

- Original mod: PinkaLulan — https://github.com/PinkaLulan/ShinColle
- Forge 1.20.1 port: kousakirai — https://github.com/kousakirai/ShinColle-Reforge

Released under the MIT License; see [LICENSE](LICENSE).

---

(original description)
This mod adds cute ship girls.

Features:
1. Friendly and hostile ship girls
2. Ship equipments
3. Leveling system

Seikan / Ship Girls:
1. Destroyer : I, Ro, Ha, Ni, Shimakaze, Akatsuki, Hibiki, Ikazuchi, Inazuma
2. Heavy Cruiser : Ri, Ne, Atago, Takao
3. Light Cruiser : Tenryuu, Tatsuta
4. Carrier : Wo, Kaga, Akagi
5. Battleship : Ru, Ta, Re, Nagato, Yamato, Kongou, Hiei, Haruna, Kirishima
6. Submarine : U511, Ro500, Ka, Yo, So
7. Princess : Airfield, Battleship, Destroyer, HeavyCruiser, Harbour, Northern, Aircraft Carrier, Isolated Island, Midway, Submarine, SubmarineNew
8. Water Demon: Aircraft Carrier
9. Transport: Wa-Class
