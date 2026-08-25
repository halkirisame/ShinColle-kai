# ShinColle-kai

PinkaLulan氏作のShinColleを、kousakirai氏がForge 1.20.1へ移植したもの(ShinColle-Reforge)の
派生版(fork)です。移植版に残っていた不具合の修正と、移植時に取りこぼされた挙動の復元を
進めています。

## v1.20.1-1.0.0 の変更点

MOD IDを `shincolle` から `shincolle_kai` へ変更しました。上流(本家・移植版)との
互換はありません。

- 旧IDで保存された艦娘やアイテムは引き継げません。ワールド自体は開けます
- 設定ファイルは新規生成されます。旧設定は引き継がれません
- KubeJSスクリプトやdatapackの `shincolle:` は `shincolle_kai:` へ書き換えが必要です
- Javaアドオンは非互換です。旧 `net.shincolleapi` は廃止し、本体JAR同梱の
  `com.lulan.shincolle.api` へ置き換えました

旧版のjarを残しておけば、旧ワールドは旧版で開けます。

## 動作環境

- Minecraft 1.20.1 / Forge 47系(`[47,)`)。開発・検証は 47.4.0 で行っています

任意で連携するMOD(無くても動作します):

| MOD | 連携内容 |
|---|---|
| Curios | 艦娘の装備スロット |
| Tinkers' Construct | 修飾子を艦の攻撃効果へ変換 |
| KubeJS | 独自の艦属性・装備をスクリプトから追加 |
| JEI | レシピ表示 |

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

ShinColle-kai is a derivative of ShinColle-Reforge, kousakirai's Forge 1.20.1 port of
PinkaLulan's ShinColle. It carries on from that port, fixing bugs left in it and restoring
behaviour that was lost in the move from 1.10.2.

## Changes in v1.20.1-1.0.0

The mod id changed from `shincolle` to `shincolle_kai`. This is not compatible with the
original mod or with ShinColle-Reforge.

- Ships and items saved under the old id are not carried over. The world itself still opens
- Config files are regenerated; old settings are not carried over
- KubeJS scripts and datapacks referring to `shincolle:` need updating to `shincolle_kai:`
- Java addons are incompatible. The old `net.shincolleapi` is gone, replaced by
  `com.lulan.shincolle.api` bundled in the main jar

Keeping the old jar around lets you still open old worlds with it.

## Requirements

- Minecraft 1.20.1 / Forge 47.x (`[47,)`); developed and tested against 47.4.0

Optional integrations (all work fine when absent):

| Mod | What it adds |
|---|---|
| Curios | Equipment slots for ship girls |
| Tinkers' Construct | Modifiers converted into ship attack effects |
| KubeJS | Custom ship attributes and equipment from scripts |
| JEI | Recipe display |

## Documentation

- [CHANGELOG.md](CHANGELOG.md) -- what has changed
- [docs/kubejs_integration.md](docs/kubejs_integration.md) -- adding ship attributes and
  equipment from KubeJS
- [docs/java_addon_api.md](docs/java_addon_api.md) -- public API boundaries and examples
- [examples/](examples/) -- working samples: an equipment datapack and a Java addon

## Contact

For anything about this fork, please use:

- Issues: https://github.com/halkirisame/ShinColle-kai/issues
- X: https://x.com/hal_kirisame

**Please do not contact the authors of the original mod or of the 1.20.1 port about
this fork.** It is maintained independently of both.

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
