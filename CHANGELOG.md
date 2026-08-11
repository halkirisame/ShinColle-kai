# 変更履歴 / Changelog

## 2026-08-12 — 装備データ刷新とAI/戦闘の全面見直し

### 追加

- 装備データをJavaのハードコード配列からデータパック(JSON、1装備1ファイル)へ移行しました。艦娘・アドオン開発者が装備を追加・調整しやすくなります。
- Curiosスロット対応の艦娘用装備欄と、Tinkers' Construct製ツールの特性を読み取る仕組みを追加しました。
- 艦娘のAI設定画面を全面刷新しました。従来は右側の66×77pxの帯に8ページ・14個のトグル・5本のスライダーが詰め込まれ、説明もほぼ無い状態でした。新しい独立画面では全設定を1つのスクロールリストにまとめ、全項目にホバー説明を付け、対応していない設定は理由付きでグレーアウト表示するようにしました。

### 不具合修正

**戦闘**

- 連装砲(EntityRensouhou)が描画されない問題を修正しました。IShipEmotionへの無条件キャストが原因でした。
- 連装砲が一切攻撃しない問題を修正しました。召喚時に渡されるターゲットが使われていませんでした。
- 軽弾薬の発射エフェクトが出ない問題を修正しました。発射位置・方向の情報がパケットで送られていませんでした。あわせて実際に着弾点まで届くビーム表示を追加しました。
- 重弾薬(ミサイル)に外れ判定が無く、必ず命中していた問題を修正しました。
- 重弾薬の着弾ダメージ計算で、対空/対潜補正・命中率/クリティカル判定・プレイヤー与ダメージ上限が抜けていた問題を修正しました。
- 重弾薬の着弾ダメージで、防御力補正と昼夜補正が二重に適用されていた問題を修正しました。

**AI**

- 全ての艦が攻撃AIを2つ同時に走らせていた問題を修正しました。基底クラスとサブクラスの両方が攻撃ゴールを登録していたため、索敵〜攻撃のラグや不安定な追跡の原因になっていました。
- 索敵の探索範囲が原典と異なり、奥行き方向にだけ2倍広い非対称な形状になっていた問題を修正しました。
- 索敵の8tickごとのスキャン間引きが、Minecraft 1.20のAI実行間隔と噛み合わず艦の約半数が索敵不能になっていた問題を修正しました。
- 攻撃ゴールが弾薬切れでも保持され続け、移動を占有し続けていた問題を修正しました。
- 攻撃ゴールがリセットされるたびに艦が急停止していた問題を修正しました。
- 追従AIでリード(首縄)で繋いだ艦が引っ張り合う問題、オーナーが別次元にいる場合に誤った座標へテレポートしようとする問題、テレポート時に転送先チャンクの読み込みを確認していなかった問題、転送後に古い経路へ歩いて戻ってしまう問題を修正しました。護衛AIのテレポートも同様に修正しました。

### 開発面

- 深海棲艦AIの検証のため、索敵・攻撃・追従・戦闘計算の各所に診断ログを追加しました。

## 2026-08-10 — 所持アイテムの確認

### 追加

- 味方の艦娘にShiftを押しながらカーソルを合わせると、その艦娘が持っているアイテムを画面に表示するようにしました。所有者本人だけが見られます。
- 轟沈した艦娘の卵にカーソルを合わせると、中に保管されている装備とアイテムを一覧表示するようにしました。装備欄と収納アイテムは区切って表示されます。

## 2026-08-09 — 深これ re:fork 最初の修正群

### 不具合修正

**戦闘・移動**

- 敵艦がまったく移動しなかった問題を修正しました。4つの原因が重なっていました。
  - 移動条件が原典と反転しており、目標が見えている間は接近しませんでした。
  - ナビゲータが二重に存在し、経路を保持している側が処理されていませんでした。
  - 敵艦に水上移動が実装されておらず、バニラの遊泳物理では前に進めませんでした。
  - 近接型が常に接近する例外と、32tickごとの再経路探索が移植時に失われていました。
- 敵艦の頭部と艤装の砲身が激しく振動する問題を修正しました。AI処理が毎tick2回実行されていたためです。
- 走行モーションの異常を修正しました。倍速再生と、その後に判明した完全停止の両方に対応しています。

**表示**

- 艦娘の顔が黒く潰れる問題を修正しました。発光レイヤーの首の回転が同期されず、顔が頭部内に埋没していました。金剛型4隻を含む11モデルが対象です。
- 水しぶきが巨大化し過剰に発生する問題を修正しました。旧バージョンの単位が残って10倍になっていたことと、毎tick無条件に生成していたことが原因です。
- 艦娘UIの火力欄で、ラベルと数値が重なっていたのを解消しました。

**その他**

- 艦娘の死亡時にクライアントが落ちる問題を修正しました。
- 機能していなかったレシピ4件を復活させました。名前空間の誤記が2件、ファイル名に空白が含まれていたものが2件です。

### 仕様変更

- 艦娘の死亡時に、装備とアイテムを失わなくなりました。従来は地面に散らばっていましたが、艦娘の卵に格納され、再召喚時にそのまま戻ります。

### 開発面

- MOD名を **深これ re:fork** に変更しました。MOD IDは `shincolle` のままなので、既存のアドオンとワールドはそのまま利用できます。
- 診断コマンド `/shipai` を追加しました。艦娘のAI・経路・移動指示を確認できます。
- 接近距離の設定 `engageDistance` を追加しました。既定値100は原典と同一の挙動です。
- 到達不能なコードを整理しました。

---

## 2026-08-10 — Seeing what a ship carries

### Added

- Sneaking while the crosshair is on one of your own ship girls shows the items
  it is carrying. Only the owner gets an answer; a ship's cargo is not public
  information on a shared world.
- The ship egg dropped by a sunk ship now lists the equipment and cargo stored
  inside it, with equipment shown separately from cargo.

## 2026-08-09 — First round of re:fork changes

### Bug fixes

**Combat and movement**

- Hostile ships would not move at all. Four separate causes were involved:
  - the movement condition was inverted, so a ship never closed in while it could see its target;
  - two navigation objects existed, and the one holding the path was never ticked;
  - hostile ships had no water movement, leaving them on vanilla swimmer physics;
  - the melee exception and the 32-tick re-path throttle had been lost in the port.
- Hostile ships' heads and gun barrels shook violently, because the AI step ran twice per tick.
- Run animations played at double speed, and then not at all once that was fixed; both are now correct.

**Rendering**

- Ship girls' faces rendered as a black patch. The glow layer's neck rotation was never synchronised, sinking the face inside the head. Eleven models were affected, including all four Kongou-class ships.
- Water spray was far too large and far too frequent: a scale value from the old version was ten times too big, and the spray spawned every tick without a gate.
- In the ship UI, the firepower value overlapped its own label.

**Other**

- Fixed a client crash when a ship girl died.
- Restored four recipes that never loaded: two with the wrong namespace, two whose filenames contained a space.

### Changed behaviour

- A ship girl no longer scatters its inventory on death. Equipment and cargo are stored in the ship egg and come back with the ship when it is summoned again.

### Development

- Renamed from `Shinkeiseikan Collection` to **深これ re:fork**. The mod id stays `shincolle`, so existing addons and worlds keep working.
- Added the `/shipai` command for inspecting a ship's AI, path and movement input.
- Added the `engageDistance` config option; its default of 100 matches the original behaviour.
- Removed unreachable code.
