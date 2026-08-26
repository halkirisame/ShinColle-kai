# ShinColle-kai

[![CI](https://github.com/halkirisame/ShinColle-kai/actions/workflows/ci.yml/badge.svg)](https://github.com/halkirisame/ShinColle-kai/actions/workflows/ci.yml)

深海棲艦を建造し、育て、敵として現れる艦娘と戦うMinecraft MODです。
深海棲艦と、撃破後に味方へ迎えられる艦娘、装備システム、レベリングを追加します。

PinkaLulan氏作のShinColleを、kousakirai氏がForge 1.20.1へ移植したもの(ShinColle-Reforge)の
派生版(fork)です。移植版に残っていた不具合の修正と、移植時に取りこぼされた挙動の復元を
進めています。

## 特徴

- **深海棲艦と艦娘** — 深海側の艦を建造・育成。野生の艦娘は敵として出現します
- **建造と育成** — 小型建造、レベリング、婚約
  （大型建造は現在利用できません。「既知の問題」を参照）
- **装備** — 主砲・魚雷・艦載機など。艦の性能を変化させます
- **拡張** — KubeJSスクリプト、datapack、Javaアドオンから独自の艦属性と装備を追加できます

## 動作環境

- Minecraft 1.20.1 / Forge 47系(`[47,)`)。開発・検証は 47.4.0 で行っています

任意で連携するMOD(無くても動作します):

| MOD | 連携内容 |
|---|---|
| Curios | 艦の装備スロット |
| Tinkers' Construct | 修飾子を艦の攻撃効果へ変換 |
| KubeJS | 独自の艦属性・装備をスクリプトから追加 |
| JEI | 艦GUIとアイテム一覧の表示競合を回避 |

## 互換性

**MOD IDを `shincolle` から `shincolle_kai` へ変更したため、*ShinColle-Reforge* との
互換性はありません。**内容ごとに分けて説明します。

| 種別 | 状態 |
|---|---|
| 起動互換性 | **可**。Reforgeで作ったワールドをクラッシュせず開けます |
| データ互換性 | **不可**。旧IDで保存された艦娘・アイテム・装備は引き継げません |
| セーブ互換性 | **不可**。Reforgeからの継続プレイはできません |
| API互換性 | **不可**。旧IDを前提にしたaddon・KubeJSスクリプトは動きません |

設定ファイルは新規生成されます。旧設定は引き継がれません。

## Reforge からの移行

Reforgeのワールドを開くこと自体はできますが、**自動移行は行われません。**

1. Reforgeで作った艦娘・アイテムは、ワールドを開いた時点で失われます。
   残したい場合はReforgeのまま遊び続けてください
2. 設定は `config/shincolle_kai-*.cfg` として新規に作られます。
   旧 `shincolle-*.cfg` の値を引き継ぎたい場合は手で書き写してください
3. KubeJSスクリプトとdatapackは、`shincolle:` を `shincolle_kai:` へ書き換えてください
4. Javaアドオンは再コンパイルが必要です。API境界は
   [docs/java_addon_api.md](docs/java_addon_api.md) を参照してください

**新規ワールドで始めることを推奨します。**

## 既知の問題

現在把握している、遊ぶうえで影響のある問題です。

- **大型建造が利用できません。** 大型造船所を構成するブロック（多金属ブロック・
  深海重怨念ブロック）が右クリックで設置できず、造船所を組み立てられません。
  修正作業中です
- **艦娘タスク（採掘・釣りなど）とクレーンは未検証です。** 1.20.1移植時の欠落が
  未調査のため、動作しない可能性があります
- **艦の感情・反応が発生しません。** 撫でる・被弾する・攻撃する・待機する・命令する
  のいずれでも、感情の表示、音声、士気の変動、押し返し、反撃が起きません
- 指揮棒の単艦モードが機能せず、選択した艦が全て反応します
- 撫でモードで右クリックしても反応しません
- 大型造船所の渦が建造中も停止時と同じ表示のままです（見た目のみ）
- 1.10.2にあったパーティクル49種のうち24種が未移植です（見た目のみ）

不具合の報告は [Issues](https://github.com/halkirisame/ShinColle-kai/issues) へお願いします。

## v1.20.1-1.0.0 の変更点

KubeJS連携・装備datapack・Javaアドオン向けPublic APIを実装しました。
艦が攻撃・反撃しない不具合なども修正しました。

全ての変更点は [CHANGELOG.md](CHANGELOG.md) をご覧ください。

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

ShinColle-kai is a Minecraft mod about building and raising abyssal ships to fight
hostile ship girls. Defeated ship girls can also be recruited as allies. It adds an
equipment system and leveling.

It is a fork of ShinColle-Reforge, kousakirai's Forge 1.20.1 port of PinkaLulan's
ShinColle, fixing bugs left in that port and restoring behaviour lost in the move from
1.10.2.

## Features

- **Abyssal ships and ship girls** — build and raise abyssal ships; wild ship girls
  appear as enemies
- **Construction and growth** — small construction, leveling, marriage
  (large construction is currently unavailable; see Known Issues)
- **Equipment** — cannons, torpedoes, aircraft and more, changing a ship's stats
- **Extensible** — custom ship attributes and equipment from KubeJS scripts, datapacks
  or a Java addon

## Requirements

- Minecraft 1.20.1 / Forge 47.x (`[47,)`); developed and tested against 47.4.0

Optional integrations (all work fine when absent):

| Mod | What it adds |
|---|---|
| Curios | Equipment slots for ships |
| Tinkers' Construct | Modifiers converted into ship attack effects |
| KubeJS | Custom ship attributes and equipment from scripts |
| JEI | Keeps its item list clear of the ship GUI |

## Compatibility

**The mod id changed from `shincolle` to `shincolle_kai`, so this is not compatible with
*ShinColle-Reforge*.** Broken down by kind:

| Kind | Status |
|---|---|
| World loading | **Works.** A world created with Reforge opens without crashing |
| Data | **Broken.** Ships, items and equipment saved under the old id are not carried over |
| Save continuity | **Broken.** You cannot continue a Reforge playthrough |
| API | **Broken.** Addons and KubeJS scripts written against the old id will not work |

Config files are regenerated; old settings are not carried over.

## Migration from Reforge

A Reforge world will open, but **nothing is migrated automatically.**

1. Ships and items created in Reforge are lost the moment the world is opened.
   Keep playing on Reforge if you want to keep them
2. Config is recreated as `config/shincolle_kai-*.cfg`. Copy values across by hand from
   the old `shincolle-*.cfg` if you want to keep them
3. For KubeJS scripts and datapacks, replace `shincolle:` with `shincolle_kai:`
4. Java addons must be recompiled. See
   [docs/java_addon_api.md](docs/java_addon_api.md) for the API boundary

**Starting a new world is recommended.**

## Known Issues

Problems currently known to affect play.

- **Large construction is unavailable.** The blocks that make up the large shipyard
  (polymetal block, heavy grudge block) cannot be placed by right-clicking, so the
  shipyard cannot be assembled. A fix is in progress
- **Ship tasks (mining, fishing) and the crane are unverified.** Gaps from the 1.20.1
  port have not been investigated, so they may not work
- **Ships do not react emotionally.** Petting, taking damage, attacking, idling and
  being commanded all produce no emotion display, voice, morale change, pushback or
  retaliation
- The pointer's single-ship mode does not work; every selected ship responds
- Right-clicking in caress mode does nothing
- The large shipyard's vortex stays in its idle appearance even while building
  (cosmetic only)
- 24 of the 49 particle types from 1.10.2 are not ported (cosmetic only)

Please report bugs at
[Issues](https://github.com/halkirisame/ShinColle-kai/issues).

## Changes in v1.20.1-1.0.0

KubeJS integration, datapack equipment and a public Java addon API are now implemented.
Ships not attacking or retaliating, among other bugs, have been fixed.

See [CHANGELOG.md](CHANGELOG.md) for the full list of changes.

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
