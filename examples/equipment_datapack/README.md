# JSON-only装備datapack例

このフォルダは追加Java MODやKubeJSなしで、登録済みItemをShinColle-kaiの艦装備にする
最小datapackです。例ではvanillaの望遠鏡を`shincolle_example:observation_spyglass`として
定義します。

## 試し方

1. `equipment_datapack`フォルダごとworldの`datapacks`へコピーする。
2. worldへ入り、`/reload`を実行する。
3. 望遠鏡を艦のnative装備枠へ入れ、命中と回避の加算を確認する。

削除するときはworldの`datapacks`からこのフォルダを除き、再度`/reload`します。
新規addon定義に旧数値`equip_id`は不要です。定義IDはfile pathから
`shincolle_example:observation_spyglass`になります。同じ`item + variant`を別定義と
重複させないでください。

`$schema`はeditor補完用です。配布先でSchemaの相対位置が変わる場合は、editor設定に
合わせて値を変更または削除できます。serverは登録済みItem/属性、重複、有限値、通信上限を
別途検証します。

サンプルは属性値に加えて、命中時25%で5秒間の`minecraft:glowing`を付与します。
攻撃効果もserverの`/reload`で再読込され、接続中clientのtooltip/GUIへ同期されます。
