# ShinColle-kai

このmodは、PinkaLulan氏作のShinColleをkousakirai氏がForge1.20.1に移植したもの(ShinColle-Reforge)の派生版(fork)です。
移植版で残っていた不具合の修正と、移植時に取りこぼされた挙動の復元を進めています。

**MOD IDは `shincolle_kai` です。v1.20.1-1.0.0 で `shincolle` から変更しました。**
本家および移植版(ShinColle-Reforge)とは別IDなので同時に導入できますが、互換性はありません。

**旧版で作成したワールドは引き継げません。新規ワールドが必要です。**
`shincolle` 名前空間で保存された艦娘・アイテム・ブロックは読み込めなくなります。
Minecraftはワールド内のアイテムとエンティティをバニラのレジストリ経由で解決しており、
Forgeの移行機構(`MissingMappingsEvent`)が張るエイリアスがそこへ届かないためです。
1.20.1では回避手段がありません。

旧ワールドを開くこと自体はできます。起動時の警告画面で止まらないようにしてありますが、
深海棲艦MODの要素は失われた状態になります。

Javaアドオンも非互換です。旧`net.shincolleapi`は廃止し、本体JAR同梱の
`com.lulan.shincolle.api`へ置き換えました。設定ファイルも新規生成されます。

変更内容は [CHANGELOG.md](CHANGELOG.md) をご覧ください。

KubeJSから独自の艦属性と装備を追加できます。導入方法と完全なサンプルは
[docs/kubejs_integration.md](docs/kubejs_integration.md)をご覧ください。
Javaアドオン向けPublic APIの境界と例は
[docs/java_addon_api.md](docs/java_addon_api.md)をご覧ください。

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
PinkaLulan's ShinColle. It carries on from that port, fixing bugs left in it and
restoring behaviour that was lost in the move from 1.10.2.

**The mod id is `shincolle_kai`, changed from `shincolle` in v1.20.1-1.0.0.**
It no longer shares an id with the original or with ShinColle-Reforge, and it is not
compatible with either.

**Worlds created with earlier versions cannot be carried over. A new world is required.**
Ships, items and blocks saved under the `shincolle` namespace will not load. Minecraft
resolves saved items and entities through the vanilla registries, which never consult the
alias table that Forge's `MissingMappingsEvent` writes to, so there is no workaround on
1.20.1.

An old world still opens -- the startup prompt is suppressed -- but its ShinColle content
is gone.

Java addons are incompatible as well: the old `net.shincolleapi` is gone, replaced by
`com.lulan.shincolle.api` bundled in the main jar. Config files are regenerated too.

See [CHANGELOG.md](CHANGELOG.md) for what has changed.

KubeJS can register custom ship attributes and turn scripted items into ship equipment.
See [docs/kubejs_integration.md](docs/kubejs_integration.md) for setup and complete examples.
Java addon boundaries and examples are documented in
[docs/java_addon_api.md](docs/java_addon_api.md).

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
