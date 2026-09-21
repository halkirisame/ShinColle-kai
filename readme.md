# ShinColle-kai（深これ改） — Abyssal Fleet & Ship Girls for Minecraft 1.20.1

[![CI](https://github.com/halkirisame/ShinColle-kai/actions/workflows/ci.yml/badge.svg)](https://github.com/halkirisame/ShinColle-kai/actions/workflows/ci.yml)

**ShinColle-kai（深これ改）**は、Minecraft Java Edition 1.20.1 / Forge向けの
艦隊育成・戦闘MODです。プレイヤーは深海棲艦側となり、深海棲艦を建造・育成・指揮して、
敵として現れる艦娘と戦います。撃破した艦娘を仲間にすることもできます。
ShinColleは日本語圏で「深これ」「艦これMOD」として知られる、艦これ風の非公式Minecraft MODです。

艦娘・深海棲艦、艦隊戦、建造、レベリング、婚約、主砲・魚雷・艦載機などの装備を追加します。
KubeJS、datapack、Javaアドオンから独自装備・艦属性を追加できる拡張基盤も備えています。

PinkaLulan氏作のShinColleを、kousakirai氏がForge 1.20.1へ移植したもの(ShinColle-Reforge)の
派生版(fork)です。移植版に残っていた不具合の修正と、移植時に取りこぼされた挙動の復元を
進めています。

**現在プレイ可能なβ版として公開し、継続して開発しています。**造船所を建てて艦を建造し、
装備を整え、艦隊を指揮して戦い、育てて婚約するところまで一通り遊べます。
未完成のシステムや既知の問題もありますが、通常のワールドで実際に遊びながら開発を進めています。

質問・感想・動作報告は Discord へどうぞ: https://discord.gg/7mMJ47Sjbh

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
- 前提MODはありません

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

深これ改は、1.20.1への移植で遊べなくなっていた致命的な不具合を、原作1.10.2のとおりに
直すところから始めました。現在も1.10.2の実装とゲーム内の挙動を参照しながら、移植で欠落・変更された
機能の復元と不具合の修正を続けています。

**遊べなくなっていた不具合**

- 敵艦がまったく動かなかった問題を直しました。接近して戦います
- 艦の約半数が敵を見つけられなかった問題を直し、索敵・攻撃・反撃が安定しました
- 宝箱からスポーン卵・装備・婚約指輪が一切出なかった問題を直しました
- 艦が轟沈したときにクライアントが落ちる問題を直しました
- 連装砲を召喚したときにクラッシュする問題を直しました
- 燃料切れのときにクラッシュする問題を直しました

**マルチプレイ**

- アイテムを複製できる問題を直しました
- 不正な通信でサーバーを落とせる問題を塞ぎました

**戦う**

- ピースフルにしても敵艦が消えなかった問題を直しました(味方艦は残ります)
- 敵艦の頭や砲身が激しく震える問題を直しました
- 連装砲が攻撃しなかった問題を直しました
- ミサイルが必ず命中していた問題を直し、外れも出るようにしました
- 艦ごとの固有の命中時効果(23艦種)を復元しました

**集める・育てる**

- 動いていなかったレシピ4件を使えるようにしました
- 婚約指輪を艦に使うと、別の操作とぶつかっていた問題を直しました
- 艦が拾ったアイテムが取り出せないページに入り、弾薬や食料が使われない問題を直しました
- 轟沈しても装備とアイテムは艦の卵に残り、再召喚すると戻ります

**拡張の仕組み**

- 装備をdatapack(JSON)で追加・調整できます。JSON Schemaとサンプル付きです
- KubeJSやJavaアドオンから、独自の装備や艦の能力値を追加できます
- Curiosの装備枠、Tinkers' Constructの修飾子を艦の攻撃効果に使えます

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
<!-- traceability: readme.known-issue.multiplayer-ownership begin -->
- **検証中: マルチプレイでの所有権と状態の保持。** 他のプレイヤーが自分の艦や
  クレーンを操作・破壊できないこと、標的・所有者・チームの状態が再接続や
  サーバー再起動をまたいで保たれることは実装済みで自動テストを通していますが、
  2人以上での実機確認が終わっていません
<!-- traceability: readme.known-issue.multiplayer-ownership end -->
<!-- traceability: readme.known-issue.item-duplication begin -->
- **検証中: 自動回収でのアイテム複製。** かまど出力や満杯のスタックを扱うときに
  アイテムが複製されない・装備欄へ紛れ込まない対応を入れていますが、
  実機確認が終わっていません
<!-- traceability: readme.known-issue.item-duplication end -->
<!-- traceability: readme.known-issue.packet-hardening begin -->
- **検証中: 不正なパケットへの耐性。** サーバーが不正・過大なパケットで
  停止しないことと、艦の位置情報が意図せず他プレイヤーへ渡らないことは
  実装済みですが、通常のクライアント以外での確認が終わっていません
<!-- traceability: readme.known-issue.packet-hardening end -->
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

## β版 v1.20.1-0.11.0 について

**β版 `0.11.0`** です。

造船所を建てて艦を建造し、装備させ、艦隊を指揮して戦い、育てて婚約するところまで
一通り遊べます。初めてワールドに入ると説明書が配られ、入門の章「はじめに」で遊び方を
案内します。説明書の誤りを直し、原典 1.10.2 と食い違っていたレシピや深海火山コアを
原典どおりに戻しました。

**ただし未完成の領域と既知の不具合があります。**`1.0.0` はそれらが解消された版の
ために取ってあります。

既知の不具合: 撫でても士気が上がらない / 艦が手持ちアイテムを表示しない。

未完成の領域: 艦娘タスクとクレーン(本フォークで未着手) / 艦ごとの特殊攻撃・固有演出 /
艦AIの作り直し。

変更点は [CHANGELOG.md](CHANGELOG.md) をご覧ください。

## ドキュメント

- [CHANGELOG.md](CHANGELOG.md) — 変更履歴
- [docs/kubejs_integration.md](docs/kubejs_integration.md) — KubeJSから艦属性・装備を追加する
- [docs/java_addon_api.md](docs/java_addon_api.md) — Javaアドオン向けPublic APIの境界と例
- [examples/](examples/) — 装備datapackとJavaアドオンの動くサンプル

## 開発への協力

開発・検証への協力を歓迎しています。コードを書かなくても協力できることはたくさんあります。

- 不具合の報告
- ゲーム内での動作確認
- ShinColle 1.10.2 との挙動の比較
- 再現条件の調査
- UI・操作性についての改善提案
- datapack / KubeJS / Javaアドオンの作成と検証

「旧版ではこう動いていた」程度の情報でも助かります。気軽に相談したいときは Discord へどうぞ。
再現手順がはっきりした不具合は GitHub Issues でも受け付けています。

## コミュニティ・連絡先

ShinColle-kai についての質問、感想、動作報告、開発の相談はこちらへどうぞ。

- Discord: https://discord.gg/7mMJ47Sjbh
- GitHub Issues: https://github.com/halkirisame/ShinColle-kai/issues — 不具合報告・技術的な問題
- X: https://x.com/hal_kirisame — 更新情報・告知

不具合かどうか分からない症状や、ちょっとした相談は Discord で構いません。

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

**It is a playable beta under active development.** You can build a shipyard, construct
ships, equip them, command a fleet in battle, level them up and marry them. Some systems
are still incomplete and there are known issues, but development goes on while playing
in ordinary worlds.

Questions, feedback and test reports are welcome on Discord: https://discord.gg/7mMJ47Sjbh

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
- No other mods are required

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

ShinColle-kai started by fixing the game-breaking bugs left in the 1.20.1 port,
following ShinColle 1.10.2. It continues to restore features that were lost or changed in the
port and to fix bugs, using the 1.10.2 code and in-game behaviour as the reference.

**Game-breaking bugs fixed**

- Hostile ships never moved; they now close in and fight
- About half of all ships could not find targets; target search, attacks and
  retaliation now work reliably
- Chests never contained ship spawn eggs, equipment or marriage rings; chest loot is back
- Fixed a client crash when a ship sinks
- Fixed a crash when a Rensouhou turret is summoned
- Fixed a crash when a ship runs out of fuel

**Multiplayer**

- Fixed an item duplication exploit
- Closed a way to crash the server with malformed packets

**Combat**

- Hostile ships now despawn on Peaceful, as in 1.10.2; friendly ships stay
- Hostile ships' heads and gun barrels no longer shake violently
- Rensouhou turrets now attack
- Missiles could never miss; they now can
- Ship-specific on-hit effects for 23 ship classes are restored

**Collecting and raising**

- Four recipes that never loaded now work
- Using a marriage ring on a ship no longer collides with other actions
- Items a ship picked up could land on a locked inventory page, where the ship could
  not use its ammo or food; fixed
- A sunk ship keeps its equipment and cargo in its ship egg and gets them back when
  summoned again

**Built for extension**

- Add or tune equipment with datapacks (JSON), with a JSON Schema and examples
- Add custom equipment and ship stats from KubeJS or Java addons
- Use Curios equipment slots and Tinkers' Construct modifiers as ship attack effects

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
<!-- traceability: readme.known-issue.multiplayer-ownership-en begin -->
- **In verification: multiplayer ownership and state persistence.** Automated tests pass
  for preventing other players from controlling or destroying your ships and cranes, and
  for preserving target, owner and team state across reconnection and server restarts, but
  verification with two or more players is not complete
<!-- traceability: readme.known-issue.multiplayer-ownership-en end -->
<!-- traceability: readme.known-issue.item-duplication-en begin -->
- **In verification: item duplication during automatic collection.** Safeguards are in
  place to prevent duplication or items entering equipment slots when handling furnace
  outputs and full stacks, but in-game verification is not complete
<!-- traceability: readme.known-issue.item-duplication-en end -->
<!-- traceability: readme.known-issue.packet-hardening-en begin -->
- **In verification: resilience against invalid packets.** Protections are implemented
  to keep invalid or oversized packets from stopping the server and to prevent ship
  positions from unintentionally reaching other players, but verification outside a
  normal client is not complete
<!-- traceability: readme.known-issue.packet-hardening-en end -->
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

## About the beta release, v1.20.1-0.11.0

This is the **beta, `0.11.0`**.

The mod is playable end to end - build a shipyard, construct ships, equip them, command
a fleet, fight, level up and marry. New players receive a guide book whose "Getting
Started" chapter walks through the basics. The book's errors have been fixed, and recipes
and the Abyssal Volcano Core now match ShinColle 1.10.2 again.

**Some systems are known to be incomplete.** `1.0.0` is reserved for the release where
they are closed.

Known defects: petting does not raise morale; ships do not render a held item.

Incomplete systems: ship work tasks and the crane (untouched by this fork); per-ship
special attacks and combat effects; the ship AI rewrite.

See [CHANGELOG.md](CHANGELOG.md) for the changes.

## Documentation

- [CHANGELOG.md](CHANGELOG.md) — what has changed
- [docs/kubejs_integration.md](docs/kubejs_integration.md) — adding ship attributes and
  equipment from KubeJS
- [docs/java_addon_api.md](docs/java_addon_api.md) — public API boundaries and examples
- [examples/](examples/) — working samples: an equipment datapack and a Java addon

## Helping with development

Help with development and testing is welcome, and much of it needs no coding.

- Reporting bugs
- Testing in game
- Comparing behaviour with ShinColle 1.10.2
- Narrowing down how to reproduce a problem
- Suggesting UI and usability improvements
- Making and testing datapacks, KubeJS scripts and Java addons

Even "this is how it worked in the old version" helps. Drop by Discord if you want to talk
it over. Bugs with clear reproduction steps can also be filed on GitHub Issues.

## Community and contact

Questions, feedback, test reports and development discussion are welcome here:

- Discord: https://discord.gg/7mMJ47Sjbh
- GitHub Issues: https://github.com/halkirisame/ShinColle-kai/issues — bug reports and technical problems
- X: https://x.com/hal_kirisame — news and announcements

If you are not sure whether something is a bug, or just want to ask, Discord is fine.

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
