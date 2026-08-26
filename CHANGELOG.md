# 変更履歴 / Changelog

## v1.20.1-1.0.0 (未リリース)

**このバージョンはまだリリースしていません。**配布されている最新版は
`v1.20.1.0.8.2`(2026-08-23、α版)です。以下はそこからの変更で、
1.0.0 をリリースするまで追記が続きます。

KubeJS連携・装備datapack・Javaアドオン向けPublic APIは、このリリースが初出です。

### MOD IDを `shincolle_kai` へ変更

- MOD IDを `shincolle` から `shincolle_kai` へ変更しました。MOD名は ShinColle-kai に統一しています。
- 旧IDで保存された艦娘やアイテムは引き継げません。1.20.1では移行する手段がありません。ワールド自体は開けます(起動時の「missing registry entries」で止まらないようにしてあります)。旧版のjarを残しておけば、旧ワールドは旧版で開けます。
- 設定ファイルは `shincolle_kai-common.toml` / `shincolle_kai-mining.cfg` として新規生成されます。旧設定は引き継がれません。
- Javaアドオンおよび装備JSONの名前空間も `shincolle_kai:` へ変わりました。
- 2026-08-09の「MOD IDは `shincolle` のままなので、既存のアドオンとワールドはそのまま利用できます」という記述は、この変更で無効になりました。
- 内部ロガー名が旧称 `Shinkeiseikan Collection` のままだったのを ShinColle-kai へ修正しました。

### 攻撃AIの再起動ループを修正

- 遮蔽物などで視線が切れるたび攻撃AIが完全に再起動し、砲撃間隔が最短照準時間へ巻き戻る問題を修正しました。
- 視線復帰時は同じ攻撃AIを継続し、砲撃・航空攻撃の待ち時間と標的追跡を維持するようにしました。
- 再起動に伴って装備属性の再計算と経路再発行が過剰に行われていた問題も解消しました。

### 指揮棒の操作と移動先表示を改善

- 指揮棒のモードをShift+マウスホイールで前後へ切り替え、HUDの3行表示で現在位置を確認できるようにしました。
- スプリント操作中はモード切替とHUDを抑止し、艦隊操作との競合を防ぎました。
- 移動指示の目的地と艦から目的地への線を継続表示し、燃料切れで命令を受けられない艦を赤マーカーで示します。
- 移動先・攻撃対象マーカーが巨大爆発へ誤変換され、目的地で爆風が再生成され続ける問題を修正しました。
- Y=0以下の目的地が無効扱いされる問題を修正し、カスタムディメンションを含む正確な目的地dimensionを同期するようにしました。
- 艦同期payloadの変更に伴いnetwork protocolを8へ更新し、旧JARとの誤decodeを接続時に拒否します。

### 宝箱の戦利品を復元

- 存在していた戦利品JSONがどの宝箱にも接続されず、スポーン卵・武装・婚約指輪などが一切出現しなかった問題を修正しました。
- ボーナスチェスト、イグルー、ダンジョン、村の武器鍛冶屋、廃坑、砂漠の寺院、ジャングルの寺院、ネザー要塞、要塞、エンドシティへ原典相当の戦利品を追加しました。
- スポーン卵の建造区分・艦種と、武装のランダムvariantを正しいNBTで生成するようにしました。
- 原典の抽選回数、幸運による追加抽選、重み、確率、個数を復元しました。

### 艦の標的保持と反撃を修正

- 反撃対象を得た直後に反撃AIが終了し、照準が毎回やり直しになって攻撃できなかった問題を修正しました。
- 複数の敵から攻撃されても照準中の標的を保持し、照準時間を正常に進めるようにしました。
- 索敵後に地形などで視線が一瞬切れても標的を即座に捨てず、視線復帰後に攻撃を継続できるようにしました。
- 標的の死亡、射程外、無敵化では従来どおり標的を解除します。

### 味方艦の感情・反応を復旧

- 味方艦の撫で・攻撃・被弾・待機・指揮・艦娘タスクに対応する感情反応が呼ばれず、表情やemoteが出なかった問題を修正しました。
- 反応ごとの発生確率と待ち時間を原典1.10.2へ戻し、被弾反応の割り込みとshock反応も復旧しました。
- 給餌成功時と満腹で拒否した時の専用emoteが表示されなくなる問題も同時に修正しました。

### 大型造船所の組み立て操作を修正

- ポリメタル・深海重怨念ブロックへの右クリックが手持ちブロックの設置を握り潰す問題を修正しました。
- 形成時だけ操作を消費し、未形成・スニーク時は通常のアイテム使用へ進むようにしました。
- 破損した旧マルチブロックを復旧するときも、クライアントとサーバーが同じクリックで同じ結果を返すようにしました。

### 艦固有の命中時効果を復元

- 原典1.10.2に存在した23艦class・24定義の固有MobEffectを、効果種類・強度・時間・確率を維持して復元しました。
- 味方艦のlevelと敵艦のscaleに応じて効果を再構築し、装備の着脱や定期再計算後も固有效果だけが正しく残るようにしました。
- 近接、軽攻撃、艦載機、ミサイル、ビーム、連装砲、浮遊要塞の命中処理を共通化し、艦固有・装備・Java addon由来の効果を成功命中時に一度だけ適用します。
- ミサイルは発射時の効果snapshotを維持し、マウントはhost艦への委譲による二重適用を防ぎます。

### 装備攻撃効果のResourceLocation化

- 装備の命中時MobEffectを数値IDと可変配列から、ResourceLocationで識別する不変Public API値へ移行しました。
- equipment JSONへ`attack_effects`を追加し、Java addon・KubeJS Item・Tinkers・既存弾薬を同じ装備resolverと戦闘経路へ統一しました。
- 攻撃効果を専用サーバーからクライアントへ同期し、装備ツールチップとCurios詳細でも確認できるようにしました。
- 不明効果、重複、範囲外値、過大件数、破損通信を状態変更前に拒否し、未解決効果は戦闘中に安全に無視します。
- 既存worldの附魔弾`PList`数値NBTは維持し、読込境界だけでResourceLocationへ変換します。

### 装備datapack・Javaアドオン公開用成果物

- equipment JSONの完全なJSON Schemaと、追加MODなしで試せるJSON-only datapack例を追加しました。
- 独自艦属性、独自Item、動的NBT値をPublic APIへ接続するJava addon例を追加し、本体buildで毎回コンパイル検証するようにしました。
- runtime loader、Schema、資料、サンプルの固定語彙がずれた場合にGameTestで検出するようにしました。
- 不明なJSON field、compatibility、enchant、開発材料、fractional/negative整数を曖昧に読み替えず、該当definitionだけ拒否するようにしました。
- Java addon資料の受理不能だった`equip_type: radar`例を`radar_lo`へ修正しました。

### Javaアドオン向けPublic API境界

- 艦属性registryの正式なResourceLocationとregistry keyをPublic APIへ移し、JavaアドオンとKubeJSが同じ正本を参照するようにしました。
- 友軍艦の内部classや所有UIDへ依存せず、プレイヤーの所有艦かを問い合わせられるread-only APIを追加しました。
- Public APIはShinColle-kai本体JARへ同梱する配布方針を明文化しました。別途APIのMODを導入する必要はありません。
- Javaアドオン向けに属性登録、動的装備、equipment JSON、所有関係、client/server lifecycleの利用例を追加しました。
- 旧版向けJavaアドオンが無変更で動くというREADMEの誤った記述を訂正しました。

### KubeJS装備・艦属性連携

- KubeJSのstartup scriptからResourceLocation付きの独自艦属性を登録できるRegistry DSLを追加しました。
- KubeJSで作成したItemを既存の装備JSONへ指定し、Javaアドオンと同じ装備解決・集計・同期経路で利用できるようにしました。
- 合成方式、表示形式、倍率区分、エンチャント規則を安全な名前付き設定として公開し、不正値はstartup時に明示的に拒否します。
- KubeJSは任意依存のまま維持し、未導入環境では連携classを読み込まず、release JARにもKubeJS本体を同梱しません。
- client/server配置、再起動と`/reload`の違いを含む完全な導入例を追加しました。

### 艦属性の動的レイヤー化

- 艦の基礎値・装備・士気・ポーション・陣形・最終値を、アドオン独自属性も失わず保持できる動的レイヤーへ移行しました。
- 属性ごとの計算規則・倍率・上下限を登録定義から処理する共通計算エンジンを追加し、既存21属性の原典準拠式と数値を維持しました。
- native装備とCurios装備の集計を同じ拡張可能な値型へ統一し、不正値やオーバーフローを装備単位で隔離するようにしました。
- アドオンから艦のレイヤー別属性と最終属性を問い合わせられる、変更不能な公開APIを追加しました。
- 艦属性の通信をResourceLocation/value形式へ更新し、専用サーバーでもアドオン独自属性を全レイヤー欠落なくクライアントへ同期できるようにしました。
- 古い・破損した属性通信を状態変更前に全体拒否し、追跡開始時の全量同期と通常更新のdirty管理を安全にしました。
- アドオン独自属性の上限を`namespace:path`で設定できるようにし、既存21属性の上限設定と登録型の上限を同じ経路で安全に解決するようにしました。
- 短い上限設定を再読み込みした際に以前の値が末尾へ残る問題を防ぎ、不正・重複・過大なID指定は有効な設定を巻き込まず個別に無視します。
- 艦GUIから同期済みの独自最終属性を確認できるようにし、未翻訳属性はstable IDで表示、項目が多い場合は24件までに制限しました。

## 2026-08-23 — 装備属性API・同期の動的化

- 装備JSONの能力値を固定21枠の番号ではなくResourceLocationで識別し、アドオンが独自属性を安全に定義・同期・表示できる基盤を追加しました。
- 専用サーバーからクライアントへ送る装備定義を同期schema v2へ更新し、未知属性を欠落させず保持しながら、不正値・過大データ・重複・途中データを全体拒否するようにしました。
- 既存94装備の能力値、エンチャント計算、倍率、ツールチップ順を維持し、異常な装備値があってもその装備・属性軸だけを隔離して他の再計算を継続するようにしました。
- 装備JSON・Item自身・第三者providerを1つの装備解決APIへ統一し、アドオンの任意Itemを艦のnative 6枠で利用できるようにしました。
- native装備、Curios、Tinkers、敵味方艦、艦載機・ミサイル・ビームを含む攻撃時効果を同じ経路へ接続し、二重適用や未配線を防ぎました。
- 敵艦のCurios能力が128tickごとに累積し、装備を外しても残る問題を修正しました。
- 原典1.10.2にあった装備固有の攻撃効果、附魔弾効果、ミサイル設定が移植時に失われていた問題を修正しました。
- Curios/Tinkersが無い環境でも共通装備処理が任意MODのclassを参照しないよう、連携境界を分離しました。

## 2026-08-22 — 装備データのアドオン対応

- EasyModeで資源を繰り返し投入すると10倍が累積していた問題を修正し、小型・大型造船所と艦卵解体で倍率を一度だけ安全に適用するようにしました。
- 装備定義と索引を専用サーバーからクライアントへ同期し、マルチプレイでも艦砲・艦載機・ドラム缶のアイコンと装備ツールチップを正しく表示できるようにしました。データパックの再読み込みにも追従します。
- 造船所への装備投入・艦の解体で耐久値を装備variantと取り違えていた処理を修正し、装備ごとに正しい資源量を還元するようにしました。

## 2026-08-21 — マウント同期・騎乗移動修正

- クライアントでホスト艦より先にマウントの同期を受信した場合も、ホスト艦の生成後に関連付けを復元するようにしました。リログや追跡再開後にWASDで騎乗マウントが動かなくなる問題を修正します。

## 2026-08-18 — セキュリティ監査・状態管理修正

### 不具合・脆弱性修正

- サーバー向けパケットの配列長と文字列長を割り当て前に制限し、過大長・欠損payloadによるメモリ枯渇を防止しました。
- クレーンへUUID所有者を追加し、GUI、設定変更、破壊、停泊艦選択へ同じ所有者検証を適用しました。貨物搬送が装備スロットを使用しないようにも修正しました。
- ウェイポイント／コンテナ連携で、対象レンチ、設定距離、ロード済みチャンク、ワールド境界、操作権限、UUID所有者をサーバー側で検証するようにしました。
- 艦インベントリへの全量挿入を原子的にし、かまど回収が部分挿入後に失敗扱いとなってアイテムを複製できる問題を修正しました。
- 標的クラス追加を、近距離・視認可能な実在Entityのクラスに限定し、長さ、件数、連打、旧セーブ読込へ上限を追加しました。
- 未使用の旧レーダー要求から周囲の全艦座標を返す処理を無効化しました。
- 艦所有者変更時にMinecraft側UUIDとShinColle側UIDを一括更新し、キャッシュと追跡クライアントへ同期するようにしました。
- チーム作成／解散のクールダウンをサーバー側で強制し、残り時間をクライアントへ同期するようにしました。
- 複数の婚約指輪を所持している場合も、全インベントリとオフハンドから有効状態を集約するようにしました。
- 索敵・射撃AIが無効化された対象や別処理で置換された対象を保持し続けないようにしました。
- 装備JSONの`enchantType`をランダム付与と能力計算の両方で使用し、装備種別との取り違えを修正しました。
- 艦キャッシュ削除をdirty化し、ワールド保存へ確実に反映するようにしました。

### 検証

- 専用サーバーGameTestを54件へ拡張し、全件成功を確認しました。

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
- 燃料切れ時のAI再構築を安全なタイミングへ移し、ゴール実行中のリスト変更でクラッシュする問題と、燃料切れ状態でAIが再登録される問題を修正しました。
- プレイヤー・友軍・中立Mobの敵味方判定、カスタム索敵対象、索敵継続条件を修正し、意図しない対象への攻撃や友軍化後も攻撃を続ける問題を修正しました。
- 護衛対象のUUIDとディメンションを保存し、再読み込み後やカスタムディメンションでも護衛状態を復元できるようにしました。
- 逃走・空母攻撃・航空機攻撃・射撃・徘徊・アイテム回収・ドア開閉AIの実行条件と更新間隔を修正しました。
- `/shipstopai` が通常艦・深海棲艦・随伴EntityのバニラAIを含めて停止するようにしました。
- 深海棲艦をMinecraft標準の敵対Mob（`Enemy`）として分類し、他MODの敵対Mob判定にも参加できるようにしました。

**スポーン・死亡保持**

- 指輪条件付きのボス艦が、指輪なし・海洋外でも初期値0のクールダウンから出現抽選へ進む問題を修正しました。
- 艦娘の死亡イベントでCuriosが装備を消去する前に退避し、死亡卵へ保存して再召喚時に復元するようにしました。
- ボス艦をNBTから再読込した際にHPバーを再生成し、リログ・サーバー再起動・チャンク再読込後も表示されるようにしました。再生成によるHP全回復も起きません。

**同期・マルチプレイ**

- 艦娘GUIの操作パケットに所有者・距離・ディメンション・開いているメニューの検証と値域制限を追加しました。
- 艦隊メンバーを永続UIDで復元し、再ログイン後に古いEntity IDを別Entityへ誤適用する問題を修正しました。
- 途中から艦を追跡し始めたクライアントへ、能力値・装備・騎乗状態・名前・バフを同期するようにしました。
- カスタム索敵対象のクラス名をサーバーから同期し、GUIで対象を正しく表示・削除できるようにしました。
- 指揮棒のモードをサーバー側の所持品へ同期し、旧版の単艦/グループ選択状態を保存・同期して、移動・攻撃・護衛・着座命令の対象へ反映するようにしました。

### 開発面

- 深海棲艦AIの検証のため、索敵・攻撃・追従・戦闘計算の各所に診断ログを追加しました。

## 2026-08-10 — 所持アイテムの確認

### 追加

- 味方の艦娘にShiftを押しながらカーソルを合わせると、その艦娘が持っているアイテムを画面に表示するようにしました。所有者本人だけが見られます。
- 轟沈した艦娘の卵にカーソルを合わせると、中に保管されている装備とアイテムを一覧表示するようにしました。装備欄と収納アイテムは区切って表示されます。

## 2026-08-09 — ShinColle-kai 最初の修正群

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

- MOD名を **ShinColle-kai** に変更しました。MOD IDは `shincolle` のままなので、既存のアドオンとワールドはそのまま利用できます。
- 診断コマンド `/shipai` を追加しました。艦娘のAI・経路・移動指示を確認できます。
- 接近距離の設定 `engageDistance` を追加しました。既定値100は原典と同一の挙動です。
- 到達不能なコードを整理しました。

---

## v1.20.1-1.0.0 (Unreleased)

**This version has not been released yet.** The latest distributed build is
`v1.20.1.0.8.2` (2026-08-23, alpha). The entries below are the changes since then,
and more will be added until 1.0.0 ships.

KubeJS integration, datapack equipment and the public Java addon API all debut in
this release.

### Mod id changed to `shincolle_kai` (compatibility break)

- The mod id changed from `shincolle` to `shincolle_kai`. The mod name is now consistently ShinColle-kai.
- Ships and items saved under the old id are not carried over; there is no way to migrate them on 1.20.1. The world itself still opens (the "missing registry entries" prompt is suppressed). Keeping the old jar around lets you still open old worlds with it.
- Config files are regenerated as `shincolle_kai-common.toml` / `shincolle_kai-mining.cfg`. Old settings are not carried over.
- Java addon and equipment JSON namespaces moved to `shincolle_kai:` as well.
- **This supersedes the 2026-08-09 note that said "the mod id stays `shincolle`, so existing addons and worlds keep working".**
- The internal logger name still said `Shinkeiseikan Collection`; it now says ShinColle-kai.

## 2026-08-10 — Seeing what a ship carries

### Added

- Sneaking while the crosshair is on one of your own ship girls shows the items
  it is carrying. Only the owner gets an answer; a ship's cargo is not public
  information on a shared world.
- The ship egg dropped by a sunk ship now lists the equipment and cargo stored
  inside it, with equipment shown separately from cargo.

## 2026-08-09 — First round of ShinColle-kai changes

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

- Renamed from `Shinkeiseikan Collection` to **ShinColle-kai**. The mod id stays `shincolle`, so existing addons and worlds keep working.
- Added the `/shipai` command for inspecting a ship's AI, path and movement input.
- Added the `engageDistance` config option; its default of 100 matches the original behaviour.
- Removed unreachable code.
