# 変更履歴 / Changelog

## v1.20.1-1.1.0 (2026-11-13)

### 原典 ShinColle 1.10.2 から復元

- 大型造船所の土台（ポリメタルブロック）に付けたホッパーや液体パイプから、資材・燃料を搬入できるようにしました。形成前に付けたパイプも接続し、構造を組み直した後も置き直さずに搬入できます。

### 艦の調整

- 鹿島を火力・防御を控えめにした育成支援型へ調整し、燃料のある鹿島と同じ保存艦隊の味方艦に経験値+20%を追加しました。同じ所有者・同じ次元のロード済みの艦が対象で、鹿島本人は未編成でも有効です。複数の鹿島では重複せず、少量の経験値の端数も保存・復活をまたいで繰り越します。
- 雪風の能力配分と成長を調整し、味方の雪風に回避+15ポイントと通常攻撃後の待ち時間÷1.1を追加しました。初撃・艦載機・固有スキルの待ち時間は変わりません。両艦の固有能力はNEW AIでのみ有効で、敵艦には付きません。

### 艦隊画面

- 艦隊画面で艦隊ごとに個体メニューを開ける距離を16/32/64ブロックから選べるようにしました(既定16)。遠くで開いたメニューがすぐ閉じる問題も修正しました。読み込まれていない艦は開けません。
- 艦隊画面の選択中の艦に×ボタンを追加しました。同じ×を3秒以内に2回押すと、遠方・未ロード・別次元の艦も編成から外せます。空き枠は詰めず、隊の陣形は無陣形に戻ります。

### 構造物の宝箱

- 古代都市・森の洋館・埋もれた宝・砦の遺跡の宝物庫・難破船・海底遺跡の宝箱にも深これ改のアイテムが入るようにしました。古代都市と森の洋館は要塞、埋もれた宝はジャングルの寺院、砦の遺跡の宝物庫はネザー要塞と同じ中身です。難破船と海底遺跡には、整備員訓練書・近代化改修ツールを含む海専用の中身が入ります。生成済みのチャンクの宝箱は変わりません。

### 新しい設定

- 敵艦のスポーンエッグのドロップ確率を大きさの段階ごとに変える設定 `eggDropChanceMobSmall` / `eggDropChanceMobLarge` / `eggDropChanceBossSmall` / `eggDropChanceBossLarge` を追加しました。範囲は0.0〜1.0、既定は順に0.2・1/3・0.9・1.0で、従来の確率を維持します。
- 怨念のドロップ数にドロップ増加を反映する設定 `lootingAffectsGrudge` を追加しました。有効にするとドロップ倍率にドロップ増加のレベルを加えます（倍率1.0・ドロップ増加IIIなら4個）。既定は無効で、従来のドロップ数を維持します。
- 小型・大型造船所に燃料の消費倍率（`tileShipyardSmallFuelUse` / `tileShipyardLargeFuelUse`）を追加しました。既定は1.0で従来どおりです。値を下げると、建造時間や燃料1個の価値を変えずに燃料が長持ちします。

### 修正

- 訓練書を使った直後に、新しいレベルの必要経験値と表示を更新するよう修正しました。現在の経験値はそのまま保持します。
- 小型・大型造船所で燃料の価値の倍率を高くしても燃料が詰まらないようにしました。満タン未満なら1個取り込み、上限を超えた燃料も保持します。燃料ゲージの表示は100%までです。
- 味方艦の死亡待機中に保存・再読込すると Curios 装備が失われる問題を修正しました。退避した装備は Curios を一時的に外した環境でも死亡卵まで保持します。
- 死亡演出時間を0にしたときに味方艦の死体が残り、卵が出ない問題を修正しました。待機中に設定時間を短くした場合も、次の死亡 tick で卵に戻ります。
- 通常の敵艦生成の確率が、設定より1ポイント高くなる問題を修正しました。確率0では生成せず、既定の10は10%として判定します。ボスの生成は従来どおりです。
- 通常の敵艦生成が、群の隻数や複数の群によって設定上限を超える問題を修正しました。同じ次元の読み込み済み敵艦の残り枠まで生成し、上限0では停止します。ボス自体の生成条件は変わりません。
- 怨念の重塊を小型建造所へ投入したときに格納資材が失われる問題、撤去・再設置で未変換の溶岩が失われる問題、格納資材がツールチップに表示されない問題を修正しました。中身入りの大型コアは通常のクラフトで消費されなくなり、燃料入りのコアは建造所で資材化できなくなりました。タンクの昇格では同じ液体を容量内で合算して引き継ぎ、異なる液体や容量を超える組み合わせはクラフトできません。理由はツールチップで確認できます。
- 保存艦入りの卵を小型・大型建造所が資材として消費しないようにしました。未保存の卵の資材化は変わりません。保存卵のツールチップにも資材化できないことを表示します。
- `despawnEgg=-1` のとき、タグなしの専用ドロップが即座に消える不具合を修正しました。
- 非クリエイティブで艦を解体したときに追加装備が回収されない問題と、保存先のない追加装備枠へ格納した装備が消える問題を修正しました。死亡卵から復活した際、現在の追加装備枠に収まらない装備は、復活させたプレイヤーの所持品へ返します。満杯で入りきらない分はプレイヤーの足元へ落ち、通常のアイテムと同様に放置で消えます。
- 死亡待機中の艦が修理女神で回復したとき、退避していた追加装備を現在の枠へ戻すようにしました。回復後の解体や再死亡でも装備が失われません。枠に収まらない装備は艦の足元へ落ち、通常のアイテムと同様に放置で消えます。
- 艦の復活に失敗した場合に卵や経験値を失う問題と、クリエイティブの所持品が満杯のときに保存卵を拾うと消える問題を修正しました。
- データ定義が欠けて休眠中の汎用艦について、積荷・装備の画面と自動搬送を止め、定義が戻ったときのアイテムの消失・複製を防ぎました。
- プレイヤー名の変更・名前の再利用や古い実体IDにより、所有艦・保存卵・チーム・陣形の操作が誤った相手に及ぶ問題を修正しました。旧形式の所有者情報は数値UIDで安全に補完し、管理者用の移行コマンドを追加しました。

### 更新時の注意

- 更新の前にワールドをバックアップしてください。サーバーとすべてのクライアントを同じ版にそろえてください。
- 追加した設定は、どれも既定値で従来の挙動を保ちます。

### English

#### Restored from the original ShinColle 1.10.2

- Restored inserting materials and fuel into a large shipyard through hoppers and fluid pipes attached to its base (polymetal blocks). Pipes attached before the structure forms also connect, and they keep working after the structure is rebuilt without being placed again.

#### Ship balance

- Rebalanced Kashima as a lower-firepower, lower-defense training support ship. A fueled, loaded Kashima grants +20% XP to friendly ships sharing a saved fleet, owner and dimension; she also supports herself without a fleet. Multiple Kashimas do not stack. Fractional bonus XP carries over between small rewards, saves and revival.
- Rebalanced Yukikaze's stats and growth, and gave friendly Yukikaze +15 percentage points of dodge and ordinary post-attack intervals divided by 1.1. First shots, aircraft and special skills are unchanged. Both ships' innate traits apply only under NEW AI, never to enemies.

#### Fleet screen

- The fleet screen now lets each fleet choose a ship-menu range of 16/32/64 blocks (default 16). Fixed remotely opened menus closing immediately. Unloaded ships still cannot be opened.
- Added a × button for the selected ship in the fleet screen. Click it twice within three seconds to remove even distant, unloaded, or cross-dimension ships from the team. The slot stays empty and the team's formation resets to none.

#### Structure loot

- ShinColle loot now also appears in ancient city, woodland mansion, buried treasure, bastion treasure, shipwreck, and underwater ruin chests. Ancient cities and woodland mansions share the stronghold loot, buried treasure shares the jungle temple loot, and bastion treasure shares the nether fortress loot. Shipwrecks and underwater ruins get new ocean loot that includes training books and modernization kits. Chests in already generated chunks are unchanged.

#### New options

- Added `eggDropChanceMobSmall` / `eggDropChanceMobLarge` / `eggDropChanceBossSmall` / `eggDropChanceBossLarge` to configure hostile ship spawn egg drop chances by scale level (0.0–1.0). Defaults of 0.2, 1/3, 0.9, and 1.0 preserve existing probabilities.
- Added `lootingAffectsGrudge`, an option to add the killer's Looting level to the grudge drop rate (rate 1.0 with Looting III drops 4). It is disabled by default, preserving existing drop counts.
- Added small/large shipyard fuel-use multipliers (`tileShipyardSmallFuelUse` / `tileShipyardLargeFuelUse`). The default of 1.0 preserves existing behavior; lower values make fuel last longer without changing build time or fuel value per item.

#### Fixes

- Fixed stale experience requirements after using training books. The new level and experience now sync immediately while preserving current experience.
- Fixed small/large shipyards rejecting fuel at high fuel-value multipliers. Below full, they accept one fuel unit and retain any excess above capacity; the fuel gauge display stops at 100%.
- Fixed Curios equipment being lost when a friendly ship is saved and reloaded during its death wait. Captured equipment is retained in the death egg even if Curios is temporarily absent.
- Fixed friendly ships leaving a permanent corpse without an egg when the death animation time is zero. Shortening the configured wait below the elapsed time now completes death on the next death tick as well.
- Fixed regular hostile ship spawn chances being one percentage point too high. A chance of 0 now disables regular spawns, and the default value of 10 means exactly 10%. Boss spawns are unchanged.
- Fixed regular hostile ship groups exceeding the configured cap. Spawns now fit the remaining capacity for loaded hostile ships in the player's dimension, including across multiple groups; a cap of 0 disables regular spawns. Boss spawn conditions are unchanged.
- Fixed stored materials being lost when recycling a heavy grudge core in a small shipyard, unconverted lava being lost on removal and placement, and missing stored-material tooltips. Heavy cores with stored contents are no longer consumed by regular crafting, and shipyards no longer recycle fueled cores. Tank upgrades preserve matching fluids within output capacity and reject mixed or over-capacity contents. Tooltips explain these restrictions.
- Small and large shipyards no longer recycle eggs containing saved ships. Recycling of fresh eggs is unchanged, and saved-egg tooltips explain the restriction.
- Fixed untagged protected drops disappearing immediately when `despawnEgg=-1` disables despawning.
- Fixed missing extra-equipment drops when dismantling ships outside Creative mode, and prevented storing equipment in extra slots without persistent backing. When reviving a ship from a saved egg, extra equipment that no longer fits is returned to the reviving player's inventory. Any remainder drops at their feet and expires like ordinary dropped items.
- When a dying ship recovers through a Repair Goddess, saved extra equipment is restored to its current slots, preventing loss on subsequent dismantling or death. Equipment that no longer fits drops at the ship's feet and expires like ordinary dropped items.
- Failed ship revivals no longer consume eggs or experience. Saved ship eggs also remain recoverable when picked up with a full Creative inventory.
- Blocked inventory/equipment menus and automatic transfers for dormant data-defined ships, preventing item loss or duplication when their definitions return.
- Fixed owner, saved-egg, team, and formation identity checks after player renames, name reuse, or entity ID reuse. Legacy ownership is safely upgraded by numeric UID, and administrator migration commands were added.

#### Updating

- Back up your worlds before updating, and use the same version on the server and every client.
- All new options default to the existing behavior.

## v1.20.1-1.0.0 (2026-11-06)

### 新しい艦

- 雪風を追加しました。野生の敵艦娘として出現し、建造では入手できません。モデル・素材を提供してくださったズィズィ氏に感謝します。
- 鹿島が野生の敵艦娘として出現するようになりました。倒すと鹿島の卵を落とすことがあり、味方に迎えられます。建造では入手できません。
- 鹿島に自動のまばたき、感情・姿勢・攻撃に応じた表情の変化、首かしげ、頭の向きに合わせた髪とツインテールの動きを追加しました。

### 新しいアイテム

- サファリネットを追加しました。自分の味方艦を状態・装備ごと持ち運べ、繰り返し使えます。しゃがまずに艦を右クリックすると収納し、ブロックを右クリックすると放出します。他のプレイヤーの艦や敵艦は収納できず、中身入りのネットから放出・拾得できるのは元の所有者だけです。しゃがみ右クリックで艦の画面を開く操作は従来どおりです。テクスチャ制作: しらたき（他のMOD・フォークへの取り込みは不可。実況・二次創作・アドオンは自由）。
- 深海棲艦魂符を先行要素（試験的な機能）として追加しました。自分の艦を最大6隻まで入れて持ち運べます。1隻ずつ、または艦隊ごと格納し、現在の艦隊の順に1隻ずつ、またはクリックした場所の周りへ一度に放出します。モードは指揮棒と同じ修飾キーとマウスホイールで切り替えます。作業台では作れず、敵対モブからごく稀に手に入ります。

### 他のMODとの連携

- Mekanism 連携を追加しました。Mekanism を入れていると、ポリメタル鉱石を Mekanism の鉱石処理で2〜5倍に加工できます（粉・汚れた粉・凝塊・欠片・結晶と、汚れた／純粋な懸濁液）。粉はかまど・溶鉱炉でポリメタルに戻せます。Mekanism がなくても従来どおり動作します。

### 原典 ShinColle 1.10.2 から復元

- 騎乗した艦の武装を、ホットバー1〜4番のキーで撃てるようにしました。照準のマーカー、再使用までの時間、艦載機の数を表示します。キーを押し続けると、ホットバーを切り替えずに撃ち続けます。騎乗中のインベントリ操作と、指揮棒のチーム操作のショートカットが優先されます。
- 近くにある治癒・再生のポーションの雲や、飛んでいるスプラッシュ・残留ポーションで、味方艦の回復量が増える効果を復元しました。雲があればそちらを優先し、1秒に1回まで働きます。誰が投げたポーションでも効きます。
- 金床でエンチャントの本を使い、艦の装備にエンチャントできるようにしました。費用は30レベル固定です。同時に名前を変える・消す場合も、追加の費用はかかりません。
- 指揮棒でチーム管理の修飾キー（既定はShift）を押しながら自分の艦以外の生物を左クリックすると、その種類を攻撃目標リストへ登録・解除できるようにしました。プレイヤーは対象外です。登録・解除できたときはアクションバーで知らせ、提督の机の剣タブにも反映されます。通常の左クリックと、自分の艦へのチーム操作は従来どおりです。
- 小型・大型の建造卵を高速建造材へ解体するレシピを、JEI で表示するようにしました。
- 騎乗体に専用の移動制御と原典の段差高3を復元し、騎乗体ごとの旋回上限と陣形速度を反映しました。

### クレーン

- 数量モードで、指定個数に達した品物まで余計に積み下ろしする問題を修正しました。
- 荷役中の連続出力と、正常終了時の短いパルスを、赤石モードの設定どおりに出力するようにしました。
- ターゲットレンチで、クレーンとチェストなどの経由点のブロックをつなげるようにしました。
- 入港の順番待ち、荷物の積み下ろし、時間を決めた待機、作業後につないだ経由点への出発を復元しました。
- アイテムを損傷値・NBT・共通の Forge タグで見分ける設定と、右クリックで設定する除外フィルターを復元しました。
- 艦のポンプ装備を使った液体の貯蔵と容器への出し入れ、FE の蓄電とバッテリーの充電・放電を追加しました。
- 画面に除外の印、停泊中の艦の名前、たまっている液体と FE の量を表示するようにしました。
- 取り込み範囲の外で待っている艦は、通れる道があればクレーンの真下まで歩き続けるようにしました。
- クレーンを壊したとき、フィルターに登録した見本が実物として落ちないようにしました。
- 画面で、プレイヤーのインベントリの文字がフィルターに重ならないようにしました。

### 戦闘と艦AI

- 提督が直前に近接攻撃や投射物を当てた相手を、手動で指示した相手の次に優先して援護するようにしました。味方艦と、出撃中の艦載機が対象です。自動で敵を探す範囲と、艦載機が帰る条件は変わりません。
- 指揮棒で射程の外にいる敵へ攻撃を指示すると、艦が近づいて撃つようにしました。追従・護衛中は、提督や護衛先から離れてよい範囲の端まで追います。艦から64ブロック以内（目標を保持する距離がそれより長い艦はその距離まで）なら狙い続け、離れると狙いを外しますが、指示は残り、近づくと再び狙います。自動の索敵・反撃、座っているときや燃料切れでの停止、指示の取り消しは従来どおりです。
- 敵艦が自動で敵を探す距離と、狙い続ける距離を10ブロック延ばしました。そのかわり、敵艦が味方艦を撃つときは、その味方艦の射程に入るまで近づいてから撃ちます。敵艦がプレイヤーを撃つ距離と、味方艦の索敵・射撃の距離は変わりません。
- 射程の端にいる相手への狙いが途切れて、攻撃できないことがある問題を修正しました。敵艦・味方艦の両方が対象です。
- 陣形移動で、参加できる艦が5隻以上いれば、その場にいない艦・別の次元にいる艦・範囲外の艦などを今回だけ外して移動するようにしました。外した艦は、理由と隊の何番目の艦かを知らせます。単縦陣と梯形陣では参加する艦を隊の順に前へ詰め、ほかの陣形では従来の位置に並びます（ダイヤモンド陣で残りが5隻のときは、従来どおり詰めて並びます）。隊の登録と移動の速さは変わりません。
- 追従中や、動く相手を護衛している間に、相手が動いてから艦が追いつくまでの遅れを減らしました。開始・停止の距離、瞬間移動する条件、地点の護衛、経由点の巡回は従来どおりです。
- 高速で移動したあとの呼び戻しで、艦の読み込みに時間がかかっても途中でやめず、一定の時間まで待つようにしました。呼び戻せなかった艦は、まとめて知らせます。命令を変えるなどして取り消した呼び戻しは再開しません。
- 艦が行き先にたどり着けずに引っかかったとき、サーバーが一瞬止まる問題を軽くしました。届かない場所が多いところでは、回り道を始めるまでに最大0.8秒ほどかかることがあります。
- 連装砲（通常型・S型）の攻撃にも、所有者・同盟・誤射の設定による保護が効くようにしました。保護される相手にはダメージを与えず、ひるませもしません。相手を追う動きと、6発の弾の消費は変わりません。
- 水面から1段の岸へ通常の経路探索がつながらず、艦が水側で止まる問題を修正しました。安全に浮上・上陸できる岸への経路接続を追加し、浮力や移動速度は変更していません。
- 水上の艦が岸の縁で止まる問題を修正しました。味方艦・騎乗体・敵艦が、それぞれの段差高までの岸へ浮き上がって上陸します。プレイヤー操縦中の騎乗体は従来どおりです。
- 敵艦の段差高を大きさの段階に合わせた1〜4段へ戻しました。

### アイテムと卵

- 有効なデータ定義の艦すべての味方卵を、艦ID順でクリエイティブタブへ追加しました。同期や再読込の後に更新するようにしました。
- 婚約指輪を、同じものなら64個まで重ねられるようにしました。右クリックの ON/OFF は手に持った束全体に効き、結婚では1回につき1個だけ使います。
- 艦の卵を、種類と中身が同じものなら16個まで重ねられるようにしました。使うときも解体するときも1個ずつ消費します。
- 大型の建造卵を解体して得られる高速建造材を、64個から8個に減らしました。小型は8個のままです。持ち主や保存データのない個別艦の卵も、8個に解体できます（データ定義の艦の汎用卵は除きます）。艦が沈んだときに保存された卵は、これまでどおり解体できません。
- 味方艦が沈んで卵に戻ったときも、解体ハンマーで倒したときも、レベルが下がらないようにしました（これまでは1下がっていました）。すでに持っている卵のレベルは変わりません。
- レシピ用紙を主手か副手に持って開き、材料の写し取り・消去と、製作結果の確認ができるようにしました。変更はその用紙にすぐ保存され、用紙を動かすと画面が閉じます。中身が壊れた用紙や、古い結果の表示からは製作しません。
- 艦のデータが入った卵の説明欄の要求レベルが、実際に消費する経験値レベルより大きく表示されていた問題を修正しました。
- 死亡卵から復活した味方艦が、育成・装備を反映した最大HPまで全回復するように修正しました。保存していた警戒命令が低HPの逃走で中断される問題を解消します。ネット・魂符で運ぶ際の負傷HPは従来どおり保持します。

### 操作と説明書

- 艦の「設定」画面の先頭に、右クリックとしゃがみ右クリックの操作を入れ替える設定を追加しました。オンにすると右クリックで艦のインベントリ、しゃがみ右クリックで座る・立つを切り替えます。既定は従来どおりで、自分のクライアントの全艦に適用されます。アイテム固有の操作は変わりません。
- 説明書で開いている章を強調し、リンク先から戻る操作と画面内の章別ページ記憶を追加しました。図鑑モデルの回転中心を足元から上げました。
- 説明書の「ハジメニ」章を見直しました。最初の艦を建造するページに深海ノ熱水噴出孔のレシピを載せ、関係する章へクリックで移れるリンクを付けました。資材の入手方法、陣形の画面の開き方、陣形で動かした艦が到着後もその場に留まること、射程外の敵へは近づいて攻撃することなどを今の動作に合わせて書き直し、サファリネット・深海棲艦魂符・騎乗中の武装の説明を加えました。

### 修正

- 新しく設置した小型ドックを使える人を、設置者・その同盟・サーバーの管理者（OP）に限定しました。所有者情報のない既存ドックは、これまでどおり誰でも使えます。
- 他のプレイヤーが所有する経由点を、所有者か OP 以外が通常の操作で撤去できないようにしました。クリエイティブモードだけでは保護を回避できません。
- コンパスの周囲読み込みが設定に従うようにし、装備を外したときや艦がいなくなった後も読み込みが残る問題を修正しました。
- 通常のビームを艦が受けたとき、防御力による軽減が二重にかかる問題を修正しました。
- 資材を保存した重怨を大型設備へ再投入すると、中身の資材が失われる問題を修正しました。Easy モードでも、保存資材には倍率を重ねてかけません。
- 多金属鉱石・海底多金属砂利の生成回数と、砂利が置き換えるブロックを設定から読むようにしました。生成回数は新しい `polymetalOreClusters`・`polymetalGravelClusters` で指定し、既定の生成量は維持します。
- 艦の採掘の既定表で、多金属資材の枠から深海金属が出る問題を修正しました。既存の採掘設定は、該当する未編集の既定行だけを更新します。
- 敵艦の群数を0にすると通常の敵艦生成を止められるようにしました。
- 設備設定の再読込後、小型・大型建造所と火山コアの稼働にも新しい値を反映するようにしました。
- 味方艦がケーキなどを製作したとき、材料の空の容器がなくなる問題を修正しました。チェストが満杯のときは、製作結果と同じく空の容器を地面に落とします。
- 味方艦が落ちているアイテムを拾っても、育成の経験値が増えない問題を修正しました。拾えるすべての艦が対象で、拾ったものを全部しまえたときに1回分の経験値を得ます（拾った個数には比例しません）。
- 味方艦の自動ポンプと、経験値の瓶詰めが動かない問題を修正しました。有限の液体の源は、容器に全部入るときだけ汲みます。経験値は8ポイントにつき瓶1本です。無限に汲める深さの設定を0以下にしても、有限の源は汲めます。
- 多金属鉱石を掘っても経験値が出ない問題を修正しました。通常は1〜4、幸運付きの道具では幸運のレベル+1倍の経験値が出ます。シルクタッチでは出ません。Curios を併用していても、適さない道具では出ません。
- 味方艦のミサイルで敵を沈めても、撃った艦の撃沈数と士気が増えない問題を修正しました。
- 艦がミサイルを受けたとき、艦独自の軽減に耐性ではなく耐火が使われていた問題を修正しました。耐性は1レベルにつき20%（最大4レベル）軽減し、耐火はミサイル以外の、艦からの直撃を軽減します。
- 提督の机と携帯の深海レーダーに、自分の艦が表示されない問題を修正しました。同じ次元にいる読み込み済みの自分の艦を、設定した更新間隔で表示します。
- 騎乗体に座った艦が近くのプレイヤーを見るとき、顔の向きに合わせて体も少し向くように修正しました。
- 騎乗体8種が水上で上下に揺れる様子が表示されない問題を修正しました。
- データ定義の艦（鹿島・雪風など）で、歩くアニメーションと止まっているアニメーションが頻繁に切り替わる問題を軽くしました。
- 戦闘糧食の説明文に、改行の記号がそのまま表示される問題を修正しました。
- MOD一覧の作者欄に、テクスチャ制作のしらたき氏、開発協力の Ciao_Min 氏、雪風のモデル制作のズィズィ氏を加えました。
- 装備の説明表示が、画面下端でも下枠まで収まるようにしました。
- 装備詳細画面の長い行がパネル幅で折り返され、右端で切れないようにしました。
- 中国語（簡体字・繁体字）の訳の抜けを補い、装備名を英語版と同じ個別の名前に改めました。装備詳細画面などで英語のまま表示されていた文言を、各言語で表示するようにしました。
- 負の高さで、足元の液体判定・水深が1ブロック上を参照する問題を修正しました。艦・召喚物・騎乗体、操縦・航跡・モデル・炉の液体判定に共通して適用します。

### アドオン制作者向け

- datapack の艦定義で、建造と通常の敵としての出現を指定できるようにしました。建造は、資材による建造・固定の確率での建造・建造しない、の中から艦ごとに選べます。重み付きの建造の割合は合計50%までです。完成した建造卵は、選ばれた艦を保ちます。
- 艦定義の `hostile_variants` で、固有名・特性・敵専用の補正を持つ敵の固有個体を出せるようにしました。固有個体が落とした卵からは、固有名・特性・レア度を引き継いだレベル1の味方を迎えられます（敵専用の補正は引き継ぎません）。個体の情報は、沈んだときの卵・サファリネット・魂符でも保たれます。個体ごとに、色違いのテクスチャ（`visual_variant`）と赤いオーラ（`red_aura`）を指定でき、色違いの見た目は加入後も保たれます。
- `/reload` で艦定義の攻撃設定を変えると、すでに出現している艦にもすぐ反映するようにしました。定義で `melee` を指定した敵艦は、新しく出現したものから近接攻撃をします。味方艦の近接攻撃の設定と、組み込みの敵の攻撃は変わりません。
- 艦IDのない汎用艦は、召喚しても世界に残らないようにしました。IDはあるが定義だけが見つからない艦は、これまでどおり保存したまま、定義が戻るのを待ちます。
- 艦定義の公開 JSON Schema に、選べる AI・攻撃の ID と、省略したときの既定値を書き足しました。
- 標準の艦を追加する方法の日英の文書と、見本の艦を追加しました。

### 更新時の注意

- 更新の前にワールドをバックアップしてください。サーバーとすべてのクライアントを同じ版にそろえてください。
- 0.13.0 から、味方艦がプレイヤーへ新たに反撃するのは、許可した場合だけになっています（0.13.0 の変更履歴に書き漏れていました）。許可を設定していない艦は、以前からいる艦も含めて反撃しません。艦の AI 設定か、指揮棒の艦隊設定で許可でき、艦自身か所属する艦隊のどちらかが許可していれば反撃します。これまでの敵味方の判定は変わりません。また、この設定はすべての攻撃をすぐに止めるスイッチではありません。

### English

#### New ships

- Added Yukikaze. She appears as a wild enemy shipgirl and cannot be obtained through construction. Thanks to ズィズィ for the model and assets.
- Kashima now appears as a wild enemy shipgirl. Defeating her can drop a Kashima egg that you can use to recruit her. She cannot be obtained through construction.
- Added automatic blinking, expressions that follow emotion, pose and attacks, head tilting, and hair and twin-tail movement that follows the head to Kashima.

#### New items

- Added the Safari Net, a reusable item that carries one of your own ships together with its state and equipment. Right-click a ship without sneaking to store it, and right-click a block to release it. Other players' ships and enemy ships cannot be stored, and only the original owner can release or pick up a filled net. Sneak-right-clicking a ship still opens its screen. Texture by しらたき (not to be included in other mods or forks; free for videos, fan works and addons).
- Added the Abyssal Soul Talisman as a preview (experimental) feature. It carries up to six of your own ships. Store ships one at a time or a whole fleet, then release them one by one in your current fleet order or all at once around the clicked spot. Switch modes with the same modifier key and mouse wheel as the Command Baton. It cannot be crafted; it drops very rarely from hostile mobs.

#### Mod integration

- Added Mekanism integration. With Mekanism installed, Polymetal Ore can be processed 2x–5x through Mekanism's ore processing (dust, dirty dust, clumps, shards and crystals, plus dirty and clean slurry). The dust can be smelted back into Polymetal in a furnace or blast furnace. Everything works as before without Mekanism.

#### Restored from the original ShinColle 1.10.2

- Mounted ship weapons can be fired with the hotbar 1–4 keys again, with aim markers, cooldowns and aircraft counts. Hold a key to keep firing without changing hotbar slots. Mount inventory and Command Baton team shortcuts take priority.
- Restored the healing boost for friendly ships from nearby healing or regeneration clouds and from flying splash or lingering potions. Clouds take priority, the boost applies at most once per second, and it works no matter who threw the potion.
- Ship equipment can be enchanted with enchanted books at an anvil again, for a fixed cost of 30 levels. Renaming or clearing the name at the same time costs nothing extra.
- Holding the team-management modifier key (Shift by default) while left-clicking a creature other than your own ship with the Command Baton adds or removes its kind from the attack-target list. Players are excluded. Successful changes are shown on the action bar and in the sword tab of the Admiral's Desk. Normal left-clicks and team operations on your own ships are unchanged.
- JEI shows the recipes for dismantling small and large construction eggs into instant construction materials again.
- Restored dedicated movement control and the original step height of 3 for mounts, applying each mount's turn limit and formation speed.

#### Cranes

- Fixed quantity modes loading or unloading extra items after their specified count had already been reached.
- The redstone mode setting now controls continuous output during cargo work and short pulses on normal completion.
- Target Wrenches can link cranes to chests and other waypoint blocks.
- Restored arrival queues, cargo work, timed waits, and departure to the linked waypoint after the work is done.
- Restored item matching by damage, NBT and shared Forge tags, and right-click exclusion filters.
- Added fluid storage and container transfers powered by ship pump equipment, and FE storage with battery charging and discharging.
- The crane screen shows exclusion marks, the docked ship's name, and the stored fluid and FE amounts.
- Ships waiting outside the capture area keep walking toward the crane's underside when a path exists.
- Filter samples no longer drop as real items when a crane is broken.
- The player inventory label no longer overlaps the filters on the crane screen.

#### Combat and ship AI

- Friendly ships and launched aircraft now assist against whatever the admiral last hit with a melee attack or projectile, right after any manually ordered target. Automatic search range and aircraft return conditions are unchanged.
- Ordering an attack on an enemy out of range with the Command Baton now makes the ship close in and fire. While following or escorting, it chases up to the edge of the area it may leave. The target stays locked within 64 blocks of the ship (or its longer target-holding range); beyond that the lock is released but the order remains, and the ship aims again when it comes back in range. Automatic search, retaliation, stopping while seated or out of fuel, and order cancellation are unchanged.
- Enemy ships search for and keep targets 10 blocks farther. In exchange, when an enemy ship attacks a friendly ship, it closes in until it is within that friendly ship's range before firing. The range at which enemy ships fire at players, and friendly ships' search and firing ranges, are unchanged.
- Fixed ships losing aim at targets right at the edge of their range and failing to attack. This applies to both enemy and friendly ships.
- Formation moves now proceed when at least five ships can take part, leaving out ships that are absent, in another dimension or out of range for that move only. You are told which ships were left out, why, and their position in the team. Line ahead and echelon move the remaining ships forward in team order; other formations keep their usual positions (diamond still closes up when five ships remain). Team membership and speed are unchanged.
- Ships that follow you or escort a moving target catch up sooner after it moves. Start and stop distances, teleport conditions, position escort and waypoint patrol are unchanged.
- Recalls after fast travel no longer give up when ships take a while to load; they wait up to a fixed limit and report any ships that could not be recalled together. Recalls cancelled by a new order are not resumed.
- Reduced the brief server freeze when a ship gets stuck because it cannot reach its destination. Where many spots are unreachable, a detour may take up to about 0.8 seconds to start.
- Twin guns (normal and S type) now respect owner, alliance and friendly-fire protection: protected targets take no damage and do not react. Chasing and the six-shot ammunition use are unchanged.
- Fixed ordinary path searches stopping on the water side of a one-block bank. Added safe surface-water-to-bank connections without changing buoyancy or movement speed.
- Ships now lift over shoreline edges up to their step height, including allied ships, mounts and hostile ships. Player-controlled mounts are unchanged.
- Restored hostile ship step heights of one to four blocks according to their size stage.

#### Items and eggs

- Added friendly spawn eggs for all valid data-defined ships to the creative tab, sorted by ship ID and refreshed after synchronization or reload.

- Engagement rings with the same data stack up to 64. The right-click ON/OFF toggle applies to the whole stack, and each marriage uses only one ring.
- Ship eggs of the same kind and contents stack up to 16. Using or dismantling them still consumes one at a time.
- Dismantling a large construction egg now gives 8 instant construction materials instead of 64; small eggs still give 8. Individual ship eggs without an owner or saved data can also be dismantled into 8 (generic eggs for data-defined ships excluded). Eggs saved when a ship sank still cannot be dismantled.
- Friendly ships no longer lose a level when they sink and return to an egg, or when they are defeated with the Kaitai Hammer (previously they lost one level). Existing eggs keep their current level.
- Recipe papers can be opened from the main or off hand to copy or clear materials and check the crafting result. Changes are saved to the paper immediately, and the screen closes if the paper is moved. Ships do not craft from damaged papers or stale result displays.
- Fixed saved ship eggs showing a required experience level higher than the amount actually consumed.
- Ships revived from saved death eggs now heal to their final maximum HP after restoring growth and equipment, avoiding low-health fleeing that interrupts a saved guard order. Net and talisman transport still preserve wounded health.

#### Controls and guide book

- Added a client-local option at the top of ship Settings to swap right-click and sneak-right-click actions for all your ships. When enabled, right-click opens the ship inventory and sneak-right-click toggles sitting. Disabled by default; item-specific actions are unchanged.
- Highlighted the guide book's current chapter, added return links and per-chapter page memory while the screen is open, and raised the gallery model's rotation pivot above its feet.
- Revised the Introduction chapter of the guide book. The first-build page now shows the Small Shipyard recipe, and clickable links jump to related chapters. Descriptions now match current behavior (where materials come from, how to open the formation screen, ships moved in formation staying in place, closing in on out-of-range targets), and the Safari Net, the Abyssal Soul Talisman and mounted weapons are covered.

#### Fixes

- Newly placed small docks can now be used only by the player who placed them, that player's allies, and server operators (OPs). Existing docks without owner information remain open to everyone.
- Owned waypoints can now be removed through normal interactions only by their owner or a server operator (OP). Creative mode alone does not bypass this protection.
- Compass chunk loading now follows the settings and no longer stays active after the equipment is removed or the ship is gone.
- Fixed defense reduction being applied twice when ships are hit by normal beams.
- Fixed stored materials being lost when a Heavy Grudge Lump is fed back into a large facility. Stored materials are not multiplied again, even in Easy mode.
- Generation counts for Polymetal Ore and seafloor Polymetal Gravel, and the blocks the gravel replaces, now follow the settings. Use the new `polymetalOreClusters` and `polymetalGravelClusters` keys to set the generation counts; default generation amounts are unchanged.
- Fixed the default ship mining table giving Abyssal Metal from entries meant for Polymetal materials. Existing mining settings are updated only for the affected default lines that have not been edited.
- Setting the enemy ship group count to 0 now stops normal enemy ship spawning.
- Reloading facility settings now also applies the new values to small and large shipyards and the Volcano Core during operation.
- Fixed empty material containers being lost when friendly ships craft cakes and similar items. If the chest is full, the empty containers drop on the ground like the crafted items.
- Fixed friendly ships not gaining training experience when picking up items. Every ship that can pick up items gains one portion of experience each time it stores everything it picked up, regardless of the item count.
- Fixed friendly ships' automatic pumping and experience bottling not working. Finite fluid sources are pumped only when the container can take all of it, and experience is bottled at one bottle per 8 points. Finite sources can still be pumped when the infinite-pumping depth is set to 0 or below.
- Fixed Polymetal Ore giving no experience when mined. It gives 1–4 normally and (Fortune level + 1) times that with Fortune, and none with Silk Touch. With Curios installed, unsuitable tools still give none.
- Fixed sinking an enemy with a friendly ship's missile not raising the firing ship's kill count and morale.
- Fixed ships using Fire Resistance instead of Resistance for their own missile damage reduction. Resistance reduces it by 20% per level (up to 4 levels), and Fire Resistance reduces direct hits from ships other than missiles.
- Fixed your own ships not appearing on the Admiral's Desk radar and the portable Abyssal Radar. Loaded ships of yours in the same dimension are shown at the configured radar update interval.
- Ships seated on a mount now turn their body slightly with their face when looking at a nearby player.
- Fixed the bobbing of the eight mount types on water not being shown.
- Reduced frequent switching between walking and idle animations for data-defined ships such as Kashima and Yukikaze.
- Fixed literal line-break tags appearing in combat ration tooltips.
- Added しらたき (textures), Ciao_Min (development contributor) and ズィズィ (Yukikaze model) to the authors in the mod list.
- Equipment tooltips now fit inside their bottom border even at the bottom edge of the screen.
- Long lines on the equipment detail screen now wrap at the panel width instead of being cut off at the right edge.
- Filled missing Simplified and Traditional Chinese translations and renamed Chinese equipment names to match the English item names. Text on the equipment detail screen and similar places that was always shown in English is now translated.
- Fixed feet liquid and depth queries reading one block too high at negative heights. The shared correction also applies to summons, mounts, steering, wakes, models and furnace liquid checks.

#### For addon authors

- Ship definitions in datapacks can specify construction and normal hostile spawning. Each ship can use material-based construction, fixed-probability construction, or no construction. Weighted construction shares are capped at 50% in total, and completed construction eggs keep their selected ship.
- `hostile_variants` in a ship definition spawns enemy unique individuals with their own names, traits and enemy-only modifiers. Eggs dropped by unique individuals recruit a level-1 friendly ship that keeps the name, traits and rarity (enemy-only modifiers are not inherited). Individual data is kept in sunk-ship eggs, Safari Nets and talismans. Each individual can set a texture variant (`visual_variant`) and a red aura (`red_aura`); the texture variant is kept after recruitment.
- Changing a ship definition's attack settings with `/reload` now applies to ships already in the world right away. Newly spawned enemy ships whose definition specifies `melee` use melee attacks. Friendly ships' melee setting and built-in enemies' attacks are unchanged.
- Generic ships without a ship ID no longer stay in the world when summoned. Ships whose ID is set but whose definition is missing are still saved and wait for the definition to return.
- The public JSON Schema for ship definitions now lists the selectable AI and attack IDs and the defaults used when they are omitted.
- Added Japanese and English documents on adding standard ships, with a sample ship.

#### Updating

- Back up your worlds before updating, and use the same version on the server and every client.
- Since 0.13.0, friendly ships only start retaliating against players when allowed (this was missing from the 0.13.0 changelog). Ships without the setting, including existing ones, do not retaliate. Allow it in the ship's AI settings or the Command Baton's fleet settings; a ship retaliates if either the ship itself or its fleet allows it. Existing friend-or-foe rules are unchanged, and this is not a switch that immediately stops all attacks.

## v1.20.1-0.13.0 (β版)

- 釣りモードの艦へ指揮棒のシングルモードで通常の移動を指示すると、到着後もその釣り場に留まるようにしました。同じ地点を再指定すると追従へ戻ります。グループ指示と移動専用指示の動作は従来どおりです。

- 従来艦の手持ちアイテムが表示されない問題を修正しました。左右の腕への配置とモデルごとの位置補正を復元し、艦GUIを開かなくても釣竿など両手の内容が追跡クライアントへ届くようにしました。クライアントとサーバーは両方とも protocol 17 に対応する同じ版を使用してください。

- 鹿島に走り・撫で・燃料切れの姿勢と、座り相当の騎乗姿勢を追加しました。姿勢ごとの接地を補正し、座り時の手提げの高さを調整しました。姿勢のアニメーションが欠ける場合は、利用できる下位の姿勢に戻ります。既存艦の描画や当たり判定は変更しません。

- データ定義の艦に、顔パーツの発光、サイズ段階に応じた描画と影、手持ちアイテム、遠距離での細部省略を追加しました。鹿島の副手は対応する骨がないため表示しません。当たり判定と既存艦の描画は変更しません。

- 鹿島の顔パーツが重なって表示される問題と、座ると地面にめり込む問題を修正しました。現在の表情と艤装の表示設定を反映します（自動まばたきなどの時間変化は未対応）。既存艦の描画は変更しません。

- リソースパックの visual 定義で、新規艦のモデル・テクスチャ・基本アニメーションを表示できるようになりました。縮尺・影・骨の役割対応を指定でき、見た目の定義が欠ける艦は仮モデルで表示します。鹿島 (`shincolle_kai:kashima`) を表示確認用に追加しました（能力値は暫定、コマンド・専用 egg のみ。建造・自然出現は無効）。既存艦の描画は変更しません。クライアント・サーバーは両方とも protocol 16 に対応する同じ版を使用してください。

- Iron's Spellbooks の魔法が味方艦に当たり、艦同士が戦い始める問題を修正しました。自分・自分の召喚物・艦の持ち主と同盟したプレイヤーの魔法のダメージは、その持ち主の艦（自分の艦と同盟相手の艦）に対しては無効になり、無効になった攻撃で艦が相手を反撃の対象にすることもなくなりました。無関係なプレイヤーの魔法と、ほかのMODの魔法のダメージは変わりません。保存データと通信のプロトコルは変わりません。

- 艦・召喚物による通常モブの撃破について、実績・総撃破数・種類別撃破数・撃破スコアボードの不足分を補います。既存のプレイヤー受領者を優先し、受領者がいなければ同じ次元にいるオンライン提督へ計上します。FTB Quests 2001.4.12では提督のチームの標準撃破タスクも通常の開始条件に従って補完します。攻撃者は艦・召喚物のままなので、軽減・ノックバック・報復対象を計上のために変えません。補完はtick終了時に行い、救命・死亡キャンセルでは加算しません。XP・ドロップ・Lootingは変更せず、特殊ボスの独自死亡や他MODの蘇生等は保証範囲外です。

- **アドオン制作者向け:** 艦定義（`data/<namespace>/shincolle_kai/ships/<path>.json`、`"schema": 1`）の艦を、管理者用のコマンド `/shincolle ship spawn <ship_id> [hostile]` か、`ShipId` を持つ `generic_ship_spawn_egg` で出せるようになりました。省略できる項目として `dimensions`、`mount_offset`、`equip_type`、`attack_profile`（`shincolle_kai:default` のみ）、`hostile_modifiers` に対応します。味方の艦は `base_stats` と `growth` を使います。敵の艦は設定の段階ごとの値に `hostile_modifiers`（既定1、最大2.4）を掛けた値を使い、味方用の基礎値・成長値は使いません。`dimensions` と `hostile_modifiers` は、正の最小の IEEE 754 浮動小数点数（`Float.MIN_VALUE`）以上にしてください。定義が見つからない艦は、保存データを保ったまま休眠し、定義が戻るまでピストンや乗り物でも動かされません。保存された汎用艦の卵にも、組み込みの艦の卵と同じく、持ち主だけが拾える保護と管理者の例外があります。組み込みの艦は変わりません。クライアントとサーバーは、どちらも protocol 16 に対応する版を使ってください。自動テスト用の見本は JAR に含めていません。

- GeckoLib 4.8.4 を本体に同梱しました（別途導入は不要）。鹿島のモデル・アニメーション・テクスチャ素材を収録しました。

- ポーションの効果が、艦独自のダメージの計算と釣りの判定に効いていなかった問題を修正しました。艦（または攻撃する側の艦娘・深海棲艦）に付いた耐性・耐火・暗視・幸運が効くようになります。耐性は深海棲艦のミサイルのダメージを1レベルにつき20%（最大4レベル）減らし、耐火はほかの艦の直接攻撃のダメージを同じだけ減らします。攻撃する側に暗視があると、昼夜によるダメージの補正が0.8だけ明るい側へ寄ります。幸運は、釣りで宝物が出る確率を上げます。これらは Minecraft 本来の耐性の効果に重ねてかかるため、耐性が付いていると攻撃は2回軽減されます。ポーションによる艦の能力値は変わりません。効果が切れる・消される・弱い隠れた効果に置き換わると、すぐに効かなくなります。

- **アドオン制作者向けの互換性のない変更:** 公開 API を、安定版（`com.lulan.shincolle.api`）と試験版（`com.lulan.shincolle.api.experimental`。予告なく変わることがあります）に分けました。`ShinColleApi.API_VERSION`（現在は `1`）は、安定版の API に互換性のない変更があったときだけ上げ、追加だけでは上げません。`onShipHit` は `IShipEquipment` と `ShipEquipmentProvider` から削除し、`api.experimental.equipment.ShipEquipmentHitCallback` へ移しました。命中時の呼び出しを受けたい Item や provider は、このインターフェイスも実装してください。実装したものだけが呼ばれます（組み込みの Tinkers' Construct の provider は実装しています）。`ShipAttributeLayout.initialize(...)` は公開をやめました。レイアウトは確定した艦属性のレジストリから1回だけ作られ、`ShipAttributeLayout.current()` で読みます。レジストリの確定前に呼ぶと `IllegalStateException` を投げます。`ShipEquipmentResolver`（内部用で、`api.equipment` にありました）は `com.lulan.shincolle.equip` へ移り、API には含まれません。アドオンの依存範囲は `versionRange = "[1.20.1-1.0.0,1.20.1-2)"` と記載しました。1.0 より前の版では API を固定しません。保存データと通信のプロトコルは変わりません。

- **アドオン制作者向けの互換性のない変更:** 艦の装備の provider は、`ShipEquipmentProviders.register(...)` ではなく、Forge のレジストリ `shincolle_kai:equipment_provider` に登録するようになりました（`register` は `freeze`、`frozen`、`detached`、`Registry` クラスとともに削除しました）。`ShipEquipmentProviders.REGISTRY_KEY` に対して `DeferredRegister<ShipEquipmentProvider>` を作り、自分の MOD のイベントバスに登録してください。優先順位は、新しい `ShipEquipmentProvider.priority()`（既定0。大きいほど先で、同じならレジストリ ID の順）で provider 自身が決めます。このレジストリは保存も同期もされません。組み込みの Tinkers' Construct の provider は、ID `shincolle_kai:tconstruct_tools` と優先順位0のままです。保存データと通信のプロトコルは変わりません。

- **アドオン制作者向けの互換性のない変更:** 戦闘の目標の特性を Java で割り当てる方式をやめました。`TargetTraits.registerTargetTrait(...)`（`freeze`、`frozen`、`detached`、`traitsFor`、`Registry` を含む）と、列挙型 `api.target.TargetTrait` を削除しました。特性は、エンティティタイプのタグ `<trait namespace>:shincolle_kai/target_trait/<trait path>` で割り当てます。たとえば、自分の航空機を対空の目標にするには `data/shincolle_kai/tags/entity_types/shincolle_kai/target_trait/anti_air_eligible.json`、潜水艦なら `anti_submarine_eligible.json` を使います。Java は不要で、割り当ては `/reload` に追従します。`CoreTargetTraits` は、組み込みの2つの特性の ID と `tag(id)` を持ちます。レジストリ `shincolle_kai:target_trait` とその要素の型 `TargetTrait` は試験版（`api.experimental.target`）で、今のところ読むのは組み込みの2つの特性だけです。エンティティのクラス（航空機・ミサイル・潜水艦）による分類は変わりません。

- **アドオン制作者向けの互換性のない変更:** 装備の定義の場所を `data/<namespace>/equipment/` から `data/<namespace>/shincolle_kai/equipment/` に移しました。装備の JSON は、すべて `"schema": 4` で始める必要があります（公開している JSON Schema は `equipment:4` になりました）。古い場所は読みません。古い場所に JSON ファイルが残っている名前空間ごとに、ログへ警告を1回出します。`schema` がない・整数でない・4でない定義は、その定義だけを読み込まず、ほかの定義は読み込みます。定義の ID、保存データ、通信のプロトコルは変わりません。装備の JSON を書き出す KubeJS のスクリプトは、`kubejs/data/<namespace>/shincolle_kai/equipment/` を使ってください。

- 整備員訓練書を、元の MOD と同じく64個まで重ねられるようにしました。

- 艦が沈んだときに保存された卵が作業台で高速建造材に変えられ、艦の装備・積み荷・持ち主のデータが失われる問題を修正しました。解体ハンマーで解体できるのは、造船所で作ったものを含むふつうの建造卵（名前の有無は問いません）だけになりました。保存された艦の卵、特定の艦の卵、タグのない卵・古い形式の卵・壊れた卵からは何も得られず、そのまま残ります。大型の建造卵からは、元の MOD と同じく高速建造材が64個得られるようになりました（これまでは8個）。小型は8個のままで、ハンマーの消耗もこれまでどおりです。なお、保存された卵をこの方法で高速建造材に変えることはできなくなりました。

- 提督の机、小型造船所、解体ハンマー、ターゲットレンチ、深海火山ブロック、深海クレーンのレシピを、元の MOD のものに戻しました。材料が増えるもの: 提督の机は黒曜石5個（これまでは2個）で、羊毛は色を問わず1個（これまでは白2個）。小型造船所は怨念2個・溶岩入りバケツ3個・黒曜石4個（これまでは1・1・3個）を3×3に並べます。解体ハンマーは深海金属6個（これまでは5個）、ターゲットレンチは深海金属6個（これまでは4個）。材料が減るもの: 深海火山ブロックは2通りの並べ方とも、中央の怨念ノ塊が1個（これまでは4個）になり、周りにマグマブロック4個と黒曜石4個を置きます。深海クレーンは深海金属7個・ピストン・怨念ノ塊のままで、並べ方が元の MOD のもの（怨念ノ塊が中央、ピストンがその下）になりました。これらのレシピで使う溶岩入りバケツとミルク入りバケツは、これまでどおり空のバケツになって戻ります。

- 元の MOD の素材のレシピを戻しました。ポリメタル1個をポリメタルナゲット9個に分けられるようになり、ナゲット9個（3×3）で元に戻せます。どちらの向きでも増減はありません。軽弾薬と重弾薬は、錫・鉛・青銅・鋼・銀・ウランのインゴットからも作れます（ほかの金属と同じ並べ方で、インゴット7個・怨念・火薬かブレイズパウダー）。ただし、これらのレシピは、対応する `forge:ingots/<metal>` タグを MOD や datapack が用意しているときだけ現れ、その金属がなければ何も変わりません。バニラの素材を1種類使っていた既存の10個のレシピ（鉄からの深海金属、ラピスラズリを使うレシピ用紙、銅・鉄・金・ダイヤモンドからの軽弾薬と重弾薬）は、同じ `forge:ingots/…` か `forge:gems/…` のタグの素材ならどれでも使えるようになり、ほかの MOD の銅・鉄・金・ダイヤモンド・ラピスラズリでも作れます。バニラの素材、並べ方、できる個数は変わりません。

- 元の MOD の戦闘糧食のレシピを戻しました。小麦のレシピは、小麦1個ではなく、元の MOD と同じく、基になるもの（パンか焼いた肉、金のニンジン、怨念）の周りに小麦6個が必要になり、これまでの安いレシピはなくなりました。焼いた肉からは、ふつうの糧食ではなく肉の糧食（2番）ができます。アイスクリームからは、本来のアイスクリームの糧食（4番。与えると悪い効果を消します）ができます。上位の糧食も再び作れます: 糧食＋怨念ノ塊＋ポリメタルノ砂利（1番と3番）、アイスクリーム＋怨念ノ塊（5番）。炊いた米や小麦粉からも作れますが、datapack かほかの MOD が `shincolle_kai:cooked_rice` か `shincolle_kai:flour` のアイテムタグを埋めたときだけです。どちらのタグも既定では空なので、導入したままの状態では小麦6個のレシピだけが使えます。

- 提督が短い間に2回遠くへテレポートしたとき、追従する艦が置き去りになる問題を修正しました。20秒以内にもう一度テレポートしても（`/tp` やウェイストーンなど）、そのたびに追従する艦がついてきます。これまでは、1回目の行き先へ移った艦が、提督が戻ってくるまでそこに残っていました。約5秒の待ち時間はテレポートごとに同じで、前のテレポートの待ち時間の途中にいる艦は、その待ち時間を保ちます。座っている艦・護衛中の艦・移動を禁止した艦・追従していない艦・燃料切れの艦・ほかの提督の艦は、これまでどおりテレポートしません。

- 空母棲姫のミサイルによる近接攻撃を戻しました。近接攻撃は、接触でダメージを与えるのではなく、近接用のミサイルの設定（近接ダメージの2倍、ノックバックなし、寿命100 tick）で、足元から1.3ブロック上から相手の下半身へ向けてミサイルを1発撃ちます。ダメージはミサイルが着弾したときだけ入ります。攻撃は近接攻撃として数え、砲の弾薬・艦載機の枠・怨念は使いません。近接攻撃には、これまでどおり「近接攻撃を使う」の設定が必要で、既定はオフのままです。
- 北方棲姫の浮遊要塞を戻しました。重砲を撃つと、ふつうのミサイルではなく要塞を打ち出します。要塞は目標へ飛び、目標から約2.4ブロック以内に入ったとき、目標を失ったとき、北方棲姫が呼び戻したとき、または25秒たったときに爆発して、半径4.5ブロックに北方棲姫の重砲のダメージを与えます。重弾薬を1単位使い、重い艦載機の枠は使わず、終わっても弾薬は戻りません。撃った艦がいなくなると、爆発せずに消えます。重い艦載機は再びふつうのたこ焼きになり、ブロックを狙った重砲はふつうのミサイルのままです。
- クラスターミサイルが、親のミサイルが飛び続けている間に8 tick おきに子弾を5発出す動きを戻しました。子弾はそれぞれ親の発射時のダメージの半分を持ち、親の動きを受け継いで、水平方向に減速しながら落ち、航跡は少し遅れて出ます。子弾の軌道が、ワールドの原点からの距離に左右されなくなりました。

- 戦艦ル級の、狙いを固定した重砲の連射を戻しました。4 tick ごとに5発以上のミサイルを撃ち、それぞれ重砲のダメージの20%を与えます。あわせて、近接攻撃のダメージを軽砲の基礎値の2倍に戻しました。連射の消費は1回分だけで、撃っている間に重砲の入力を繰り返しても何も消費しません。座る・クレーンでの積み込み・燃料切れ・AIの停止・死亡・消滅・保存データの読み込み・次元の移動のいずれかで、撃ち終わっていない弾は取り消され、消費は戻らず、自動では再開しません。軽砲と移動は、これとは別に動きます。

- 味方のヨ級・呂500・U511・潜水棲姫・SSNH と、野生の U511・呂500 の軽魚雷の弾を戻しました。カ級とソ級は変わりません。装備による軽魚雷の速さの上乗せと、SSNH の近接攻撃のダメージも戻しました。
- 潜水棲姫と SSNH が、軽・重のミサイルを左右2発ずつ撃つ攻撃を戻しました。ブロックを狙った重砲も含みます。弾1発ごとに弾薬や怨念を別に消費することはありません。
- 左右2発のミサイルの短い航跡と、レールガンの先端が遅れて出る演出を、モデルを隠さずに戻しました。ミサイルが出現するときの同期で残りの遅れの時間を保つため、あとから見え始めたプレイヤーにも正しく表示されます。クライアントとサーバーは、通信のプロトコルを更新した同じ版を使ってください。

- 霧島・雷・空母水鬼と該当する姫の艦の、艦ごとの近接ダメージと、北方棲姫・港湾棲姫の重砲の倍率を戻しました。艦載機のダメージは変わりません。
- 戦艦棲姫の既定のクラスターミサイル、港湾棲姫のレールガン、離島棲鬼の未婚時の重力ミサイル、愛宕の結婚後の指輪の効果による重力ミサイル、空母棲姫の近接用ミサイルの設定の下限を戻しました。装備で指定したミサイルの種類は、これまでどおり有効です。
- 空母水鬼の、艦載機と並んで撃つ軽砲を戻しました。すでにいる艦と保存された卵にも適用されます。重砲の入力は、騎乗中も含め、消費せずに受け付けません。保存されている弾薬の使用の設定は保たれます。

- 戦闘のあとの士気の判定を、厳密に128 tick の区切りに戻しました。定期的な回復が早く再開し、戦闘のあとに余分に士気が下がる時間も早く終わります。量・士気の段階・更新の間隔・糧食の順番は変わりません。
- Y=0 と地下での、移動による消費と輸送の経験値を修正しました。出現・読み込み・次元の移動のあと、最初の移動の測定では距離による消費をしません。待機中の消費と、士気の最低限の減少はこれまでどおりです。同じ次元の中でのテレポートは、これまでどおり移動した距離として数えます。

- 味方の艦25種で、艦種ごとに設定された怨念と弾薬の消費を戻しました。すでにいる艦と保存された卵にも適用されます。これらの艦種で保存されていた独自の消費の基礎値は、読み込み時に置き換わります。更新の前に、ワールドと保存された卵をバックアップしてください。これらの艦の既定の消費は多くなります。ただし、駆逐艦・潜水艦・輸送艦の弾薬の消費は変わりません。
- 指輪の効果を有効にして結婚した暁の、生まれつきの探照灯を戻しました。探照灯を何個装備していても、最終的な探照灯のレベルはちょうど1です。指輪の効果を無効にすると、次の再計算で装備によるレベルに戻ります。
- 駆逐イ級・ロ級・ハ級・ニ級、暁・響・雷・電、駆逐棲姫、戦艦レ級が、結婚指輪の効果として提督に与える効果を、元の MOD の種類と強さに戻しました。暁は移動速度上昇ではなく採掘速度上昇を与えます。駆逐棲姫の移動速度上昇と採掘速度上昇は、別々の割合で強くなります。レ級の耐性は、レベル50未満では変わらず、レベル50からは強くなります。効果の時間、128 tick の周期、16ブロックの範囲、結婚・指輪の効果・怨念の条件は変わりません。レベルごとの強さは変わります。レ級の耐性は強くなり、ほかの多くは、レベルによってはこれまでと同じか弱くなります。
- 重巡ネ級・重巡リ級・重巡棲姫・戦艦ル級の、自分にかける能力を元の MOD のものに戻しました。ネ級・リ級・重巡棲姫は昼間以外のときに自分へ移動速度上昇と跳躍力上昇を、ル級は昼間に自分へ幸運を与えます。どれも150 tick 続き、128 tick ごとに更新されます。結婚も怨念も不要になり、提督がその場にいる必要もありません。関係するのは、艦の指輪の効果の設定だけです。ネ級とリ級は強さ（レベル/70 とレベル/60）を保ち、時間が短くなります（200 tick から150 tick へ）。重巡棲姫は提督ではなく自分を強化し（移動速度上昇はレベル/50、跳躍力上昇はレベル/40）、ネザーのような昼夜のない場所でも強化します。ル級の幸運は、「レベル/80 を 50＋レベル tick」から「レベル/140 を150 tick」に変わります。設定をオフにすると新しい効果が止まるだけで、かかっている効果は時間切れで消えます。
- 金剛・比叡・榛名・霧島・長門・大和・戦艦タ級・戦艦棲姫・空母棲姫・空母水鬼・赤城・加賀・空母ヲ級が、結婚指輪の効果として周りの艦に与える効果を、元の MOD の種類と強さに戻し、届く範囲を次のようにしました。効果を出す艦の体から16ブロック広げた立方体に体が触れている、同じ持ち主の生きている艦すべてが効果を受けます。効果を出す艦自身も含み、球の範囲の制限も艦の数の上限もありません（赤城・加賀・ヲ級・戦艦棲姫・空母棲姫・空母水鬼は自分を除かなくなり、戦艦棲姫・空母棲姫・空母水鬼は12ブロックと「レベル/50＋1隻」で止まらなくなりました）。金剛は体力増強（レベル/120、140＋レベル tick）、比叡は満腹度回復（レベル/120、100＋レベルの2倍）、榛名は幸運（レベル/120、100＋レベル）、霧島は暗視（100＋レベル）、長門は攻撃力上昇（レベル/70）、大和と戦艦棲姫は耐性と耐火（どちらもレベル/70）、タ級は移動速度上昇（レベル/80）、空母棲姫は跳躍力上昇（レベル/70）、空母水鬼は採掘速度上昇（レベル/70）、ヲ級は攻撃力上昇（レベル/80、30＋レベル）を与えます。赤城と加賀は跳躍力上昇（レベル/85）のまま、タ級は計算式も同じままです。時間は、書いたもの以外は50＋レベルのままです。128 tick の周期と、結婚・指輪の効果・怨念の条件は変わりません。ほかの持ち主の艦・提督・モブには効果がなく、死にかけている艦には効果を与えなくなりました。強さは上がるものも下がるものもあります。金剛・比叡・榛名は効果のレベルが下がるかわりに時間が長くなり、長門と大和は効果のレベルが上がり、戦艦棲姫・空母棲姫・空母水鬼は効果のレベルが下がって時間も短くなります（戦艦棲姫の耐火は、常にレベルIではなくレベル/70 になります）。どの艦も、届く範囲は広がります。空母水鬼の自分の暗視は変わりません。指輪をオフにしたり艦が離れたりしても、かかっている効果は消えず、時間切れで消えます。
- 中間棲姫の結婚指輪の効果を元の MOD に戻し、離島棲鬼が周りに与えていた攻撃力上昇をなくしました。中間棲姫の衝撃吸収は、体から16ブロック広げた立方体に体が触れている、同じ持ち主の生きている艦すべて（自分を含み、艦の数の上限なし）に、効果のレベル「レベル/50」、時間「50＋レベル tick」で届くようになりました。これまでは、12ブロック以内の最大「レベル/50＋1隻」に、効果のレベル「レベル/35＋1」、時間「80＋レベル tick」でした。16ブロック以内にいる持ち主も、同じ結婚・指輪・距離の条件で、新しい効果のレベルと時間（レベル/50、50＋レベル tick）を受けます。中間棲姫自身の回復は、128 tick ごとに最大体力の9%＋1になりました。ただし、怨念が少しでも残っているときだけです（結婚と指輪は以前から不要でした）。離島棲鬼は周りの艦に攻撃力上昇を与えなくなったため、艦隊はその強化を失います。目をくらませる攻撃と、指輪の効果をオンにして結婚しているときに加える毒は変わりません。すでにかかっている攻撃力上昇は、時間切れで消えます。
- 同じ艦が複数のチームに入っているとき、陣形モードの命令が「陣形の条件を満たさない」で拒否される不具合を修正。艦は陣形を持つ5隻以上のチームの陣形に従い、チームに陣形を設定すると、同じ艦を含む他チームの陣形は元の MOD と同じく解除される。

## v1.20.1-0.12.1 (β版)

**β版です。**0.12.0 からの変更は、未移植だった演出と表示の復元、騎乗体まわりの修正、新AIの不具合修正と旧AIの撤去です。

- 旧AI（LEGACY）を撤去し、艦のAIを新しいAI（NEW）に統一しました。`config/shincolle_kai-common.toml` の `shipAiTargetAuthority` と `shipAiNewDefaultApplied` は互換のために読み込みますが、`LEGACY` を指定しても旧AIには戻りません。移行の印の `shipAiNewDefaultApplied` は変更しないでください。`/shipaishadow` は互換のために残していますが、旧AIとの比較は行わず「比較未実施」と表示します。

- ウェイポイントやクレーンで、経路設定用の道具を持つと所有者名と前地点・次地点・チェストの座標が表示されるようになりました。
- 艦の頭上に所属する全チームの名前を表示する演出を復元しました。既存の名札設定・距離とスプリントキーに従って表示します。
- 高速・低速で通常飛翔するミサイルに、速度に応じて青緑・赤へ変わる航跡を復元しました。
- デバッグ設定を有効にしてポインタを撫でモードで持つと、狙っている高さに緑の平面が表示されるようになりました。
- 感情コマンドをコマンドブロックやコンソールから実行すると、その実行位置に感情が表示されるようになりました。

- 飛行場姫・港湾棲姫・北方棲姫の回復量・周期・対象・怨消費を元の MOD に合わせ、味方プレイヤーや同盟艦も回復するようになりました。成功した対象へ回復の線が表示されます。
- 輸送ワ級が周囲の同じ提督の艦へ怨・軽弾薬・重弾薬を補給する能力と、その対象線を復元しました。ワ級の積載欄も元の MOD と同じ3ページになります。

- 艦タンクで水や溶岩を置いたとき、元の MOD と同じバケツの設置音が鳴るようになりました。

- 大和のビームが敵や味方に当たると、元の MOD と同じ命中音が鳴るようになりました。同じ提督の艦に当たったときもダメージなしで怯み、命中の火花が出ます。
- 大和がチャージ・発射するとき、元の MOD と同じく一定の確率で声（敵は攻撃音）が鳴るようになりました。
- 大和の発射時のレーザーと先端の光が、カメラの向きによって消えることがある問題を修正しました。
- 大和が後ろや横にいる敵へビームを撃つとき、チャージ中に敵のほうへ振り向き、ビームが正面から出るようになりました。
- 大和がチャージしてからビームを発射するまでの待ち時間を、半分に短縮しました。
- 大和がビームをチャージしている間は軽砲を撃たず、ビームを発射したあとに撃つようになりました。軽砲が敵を倒してチャージが消えることがなくなります。
- 大和のチャージ中に出る白い球体と電撃が、艦の中心ではなく立方体と同じ砲口側（艦体の向きの前方）に表示されるようになりました。
- 大和のチャージ中の震える立方体と放射状の電撃、発射時のビーム先端の立方体とレーザーを、元の MOD どおり表示するようにしました。
- HbH 騎乗体のレールガンの電撃、レールガンミサイルの先端の電撃、修理女神が発動したときの火花、艦載機が墜ちるときの最後の爆発を、元の MOD どおり表示するようにしました。
- 守衛対象の艦に向かう守衛線と、指揮棒を持ってスプリントキーを押している間の味方艦の位置線を、元の MOD どおり表示するようにしました。
- 小造船所の煙が、元の MOD どおり3本の煙突から出るようになりました。艦タンクの水をネザーなどに置くと、水は蒸発して煙が出ます。

- 解体ハンマーで騎乗マウントを左クリックすると通常攻撃になっていた問題を修正しました。自分のマウントを左クリックすると、乗っている艦本体に一撃撃破の効果が働きます。

- 水上を移動する騎乗体・艦の航跡の水しぶきが、実際の10倍の大きさで描かれていた問題を修正しました。元の MOD と同じ大きさ（体の幅に比例）に戻りました。

- 長門の拳フィニッシュの赤い輪の粒が、元の MOD より小さく描かれていた問題を修正しました。元の大きさに戻りました。

- 騎乗体に座っている艦娘・深海棲艦が、勝手に回らず、騎乗体の向きに揃うようにしました。

- 空母棲鬼・空母水鬼の騎乗位置を調整しました。乗っているプレイヤーと姫が、胴にめり込まず、椅子に座ったように見えます。

- 騎乗体に乗ったとき、カメラがモデルの内側に入って画面が暗い面で埋まる問題を修正しました。元の MOD と同じく、モデルの裏面を描かないようにしています。

- 騎乗体 AfH・CaWD が陸上でも上下に揺れていた問題を修正しました。水上にいるときだけ揺れ、AfH は揺れが描画に反映されるようになります。

- 騎乗体 BaH と HbH（座っているとき）の姿勢による下がり量を元の MOD どおりに戻しました。

- 騎乗体（BaH・CaH・CaWD・HbH・IsH・MiH・SuH）の大きさと高さを元の MOD どおりに戻しました。搭乗時にカメラがモデルの内側に入る問題が直ります。

- 騎乗体で水中にいるとき、ジャンプキー(スペース)の長押しで浮上しない問題を修正しました。

- 解体ハンマーで自分の艦を左クリックしても一撃で倒せない問題を修正しました。自分の艦を左クリックすると一撃で倒れて物品に戻ります（レベル-1）。サバイバルではハンマーの耐久が1減り、クリエイティブでは減りません。他人の艦には効きません。

- 新しい艦のAIで、警備中の艦が設定した距離の内側に入ったときや、別の地点へ切り替えたときに、遠距離のテレポートまでの時間を最初から数え直すようにしました。これまでは、前の巡回の区間や前の命令の時間を引き継いでいました。

- 指揮棒で艦を撫でたときの士気の変化を、定期的な更新を待たずに、すぐクライアントへ反映するようにしました。

- 新AIで、エリトラ・ボート・馬などで速く遠くへ移動したとき、置き去りになった追従艦が、提督が着地して落ち着いた後に提督の近くへ来るようにしました。飛行中は空中へ飛ばしません。艦のいた場所が読み込まれていなくても、一時的に読み込んで呼びます（ゲームを再起動すると呼び戻しは行われません）。

- 新AIで、提督がエリトラで滑空中、または高所から落下中は、追従艦が提督のもとへ瞬間移動しなくなりました（空中に出て落下するのを防ぎます）。着地してから呼ばれます。歩行・水中・通常のジャンプ中は変わりません。

- 新AIで、提督が1秒に12ブロック以上動いたとき（エリトラ・氷上のボート・馬など）にも、追従艦の呼び戻しが働くようにしました。これまでは30ブロック以上でした。

- シップタンクで水源・溶岩源を汲んだとき、操作した本人に採取音が聞こえない問題を修正しました。

- 釣り中の艦から離れて戻ったとき、釣り糸が古い艦の位置につながったままになる問題を修正しました。

- 新AIで、龍田のゲイボルグに2・3連撃のダメージ補正が乗る問題を修正しました。
- 新AIの天龍・龍田の技能による吹き飛ばしを艦の向きにそろえました。
- 新AIで、長門の決め技の瞬間移動前に移動速度を消し、移動後に流れる問題を修正しました。

- 騎乗中に艦のインベントリをEキーで閉じると、同じ押下で再び開いてしまう問題を修正しました。
- 大和は戦闘目標を失うとビームのチャージを解除し、次の戦闘で再チャージします。消費済みの弾薬は戻りません。チャージの玉とプラズマは座り・騎乗の姿勢に追従します。

- 通常砲撃と命中の煙・爆発、艦の装備煙・水しぶき・損傷表示、艦載機と連装砲ちゃんの航跡、座騎の水滴、釣りの水面、ウェイポイントとクレーンの接続表示を復元しました。粒子番号の混同も修正し、回避文字・雪の命中・回復・燃料切れの赤い印を区別します。クライアントとサーバーに同じ版のMODが必要です。

## v1.20.1-0.12.0 (β版)

- 艦の所有権を譲渡した直後、旧提督へ呼び戻される問題を修正しました。

- 艦AIの判断部分（`src/ai_core`、`com.lulan.shincolle.ai.domain`）のライセンスを PolyForm Shield License 1.0.0 に変更しました。遊ぶこと、サーバーでの利用、MODパックへの同梱、公開APIを使うアドオンは引き続き自由です。この部分を本MODと競合するMODや製品に使うことはできません。旧ShinColle・ShinColle-Reforge 由来の部分と、v1.20.1-0.11.0 までに公開したコードはMITのままです。それ以外の部分もMITのままです。jar の `META-INF` に両方のライセンス文を同梱しました。

- 味方艦の釣りで浮きが沈む・一度釣ると再投入できない・釣り糸が表示されない問題を修正しました。旧版の深さ3ブロックの釣り場で宝物も抽選でき、幸運の効果を反映します。成果は持ち物へ入り、満杯なら足元へ落ちます。クライアントとサーバーに同じ版のMODが必要です。

- 新AI（NEW）で味方・敵の天龍・龍田・長門の特殊攻撃を復元しました。天龍の連続斬撃、龍田の突進・回転攻撃とゲイボルグ、長門の徹甲拳が発動します。天龍・龍田は技能中だけ現在の目標をそれぞれ10・8ブロックまで保持し、初期射程でも連撃を継続します。技能の瞬間移動は安全な着地点と命令範囲を確認し、座り・燃料切れ・再読込などで中断します。クライアントとサーバーには同じ版のMODが必要です。

- 新AIで提督との上下差が1秒以内に10ブロック以上増えた艦を、離脱後30秒以内の指揮棒のブロック移動指示で指定地点へ呼び戻せるようにしました。差が10未満へ戻れば解除し、約3秒離れたままなら艦名をアクションバーへ通知します。

- 新AIのエンドで、艦が40 tick連続落下するか落下中にY<0へ達すると、接地した提督の近くへ救出するようにしました。対象ディメンションは `voidRescueDimensions` で変更でき、空にすると無効になります。

- 新AI（NEW）で同じディメンション内の遠方へTPしたとき、追従艦の周囲を20秒間ロードして、動ける艦が約5秒でプレイヤーへ追いつくようにしました。

- 指揮棒の撫でモードで、一人称の杖表示を自分の手に切り替える描画を復元しました。右クリック中はモードごとに手が動きます。

- 島風の軽攻撃が出す連装砲ちゃんを通常の「連装砲」に固定しました。隠した外観設定の変更要求もサーバーで拒否します。

- 敵艦の軽砲撃、艦載機の軽・重攻撃、大和ビーム、連装砲ちゃんで欠落していたプレイヤーへの75%ダメージ軽減と上限59を復元しました。

- 艦の貨物容量判定がドラム缶で開放していないページを空きとして数える問題を修正しました。全量を収納できない場合は所持品と投入物を変更せず、開放済みの貨物枠が満杯なら拾得用の空き枠を返さなくなります。

- 正式な5隻編成の再読込時に陣形設定が消える問題を修正。艦が未登録・一時的に未解決の間は設定を保持し、隊が揃ってから陣形効果を復元する。

- 艦インベントリの長い項目名と値を2行に分け、文字が重ならないようにしました。長い艦名や値は省略し、マウスを重ねると全文を読めます。

- 被弾ボイスに20〜49tickの待ち時間を戻し、連続被弾で重なる発声を抑えました。通常レベルアップの専用音（75%）と、育成書の専用音も復元しました。

- シップタンク4種で水源・溶岩源を右クリックして1,000 mBずつ汲めるようにしました。液体入りでも同じ液体を継ぎ足せます。満杯・残容量不足・別液体入りでは源とタンクを変更しません。

- 艦のAIを新しいAI（`shipAiTargetAuthority = NEW`）へ切り替えました。既存の設定も初回更新時にこの項目だけ1度NEWになります。以前のAIに戻す場合は設定をLEGACYへ変更してワールドに入り直してください。その後は手動の選択を維持します。

- `shipAiTargetAuthority = NEW` で、召喚後に艦本体の移動速度が変わっても騎乗体に反映されず、低速のまま残る問題を修正しました。騎乗体の属性は32 tickごとに本体へ追随し、負傷したHPは回復しません。

- 手持ちの説明書を開き直すと、最後に読んだ章・ページから表示するようにしました。ゲームを再起動しても読書位置を記憶します。

- `shipAiTargetAuthority = NEW` で、採掘の艦が交戦中や追従・警備の範囲の外へは歩かないようにしました(以前は追従・警備に引き戻されて行き来することがありました)。着席・リード・騎乗・クレーン中や怨が無い艦は、作業のための移動を要求しなくなります(歩き方は変わりません)。

- `shipAiTargetAuthority = NEW` で、ウェイポイントの巡回が途中で止まる問題を修正しました。次の行き先がある点では、艦(騎乗体を含む)は点の中心から約 2 ブロックの内側まで寄ります。FollowMin が大きい艦や幅の広い騎乗体が点の手前で止まり、待機も進行も数えられなかったのが直ります。点の近くに立った艦(徘徊や押し出しで離れた艦)も、約 2.6 ブロックを超えると点へ戻ります。交戦中と、拾い物を拾っている間は戻らず、終わると点へ戻って進みます。満杯や拾得遅延で拾えない物の前では、しばらく試した後に諦めて点へ戻ります。終点・巡回でない警備・追従・護衛の距離と、FollowMin・FollowMax の設定値は変わりません。

- ウェイポイントの待機時間の表示を、設定値の数字ではなく実際の時間(秒・分)にしました。ターゲットレンチで切り替えたときのメッセージと艦の AI 設定画面が対象で、設定値・待機の長さ・保存は変わりません。

- `shipAiTargetAuthority = NEW` で、ウェイポイントの巡回を原作に合わせました。騎乗体(艤装)に乗った姫も次のウェイポイントへ進みます。巡回で行き先が替わっても、戦闘中の目標・手動攻撃のロックは切れず、感情や視線も出ません。経路の終点はブロックの中心になり、待機中に提督の命令で別の点へ行かせても、次の点では待機を最初から数えます。

- 外れた重砲が、目標を越えて上へ飛んで空中で爆発するのではなく、目標の脇の地面を狙うようにしました。水中から撃つ艦は、元の高さから重砲を撃ちます。

- `shipAiTargetAuthority = NEW` で、提督についていく艦が、原作どおり32 tick ごとに距離を測り直すようにしました。近づいた瞬間に止まらず、提督がいた場所まで歩き続けます。

- `shipAiTargetAuthority = NEW` で、提督についていく間や警備で歩く間も、敵と交戦している艦は頭を提督や行き先ではなく撃っている相手へ向けるようにしました。交戦していなければ今までどおりです。体は進む向きのままなので、真後ろの相手は向ききれません。

- `shipAiTargetAuthority = NEW` で、手動で指示した相手が透明になっても撃ち続けていた問題を修正しました。照明弾・探照灯のない艦は、透明になってから最大64tick(約3秒)で撃つのをやめ、見えるようになれば指示し直さなくても同じ相手を撃ち直します。無敵のプレイヤー(クリエイティブなど)は手動攻撃に指示できなくなり、指示の後に無敵になった相手への指示は解除されます。

- `shipAiTargetAuthority = NEW` で、歩き続ける提督についていく艦が約20秒ごとに提督の足元へ飛んでいたのをやめました。追従・護衛の艦が時間で飛ぶのは、進めずに詰まっている間だけです。詰まった艦はまず経路を引き直し、次に行き先の横の届く場所へ回り込みます。届かない物を拾いに行く艦はしばらくその物を諦め、敵に近づけない艦はその場で撃ちます。テレポートは、行き先に艦が収まる空きがあるとき(無ければ周りの空いた場所)だけ行い、壁や天井に埋まらなくなりました。続けて飛ぶのは5秒に1回までです。

- `shipAiTargetAuthority = NEW` で、艤装(騎乗体)を出した姫が水中でゆっくり沈んでいく問題を修正しました。原作どおり、艦を乗せた艤装が水面に浮きます。

- `shipAiTargetAuthority = NEW` で、艦が追従で提督のもとへ戻る間、HP が減って逃げる間も目標を撃ち、近接の艦は移動中でも隣の目標を殴るようにしました。撃つ間隔は艦ごとに1つの待ち時間で数えるので、護衛の移動から到着に切り替わった直後に余分な1発を撃たなくなりました。目標を指示し直すと、移動の途中でもすぐ新しい目標を撃ちます。

- `shipAiTargetAuthority = NEW` で、追従・護衛中の艦が遠くの敵を追って提督から離れ、引き戻されて往復する動きをなくしました。敵が追従の範囲の外なら、範囲の縁の手前で止まって撃ちます。リードに繋いだ護衛の艦は歩かず、HP が逃走の割合以下の艦は提督のそばにとどまって撃ちます。

- ネ級・重巡棲姫の目・口・赤面が顔から上にずれていた配置を原作に合わせ、響・ヲ級の口と赤面が別の絵を表示していたのと、赤面の奥行きも原作どおりに直しました。

- `shipAiTargetAuthority = NEW` で、攻撃しながら移動する護衛の艦が、手動で指示した相手を移動中も撃つようにしました。近くの別の敵に気を取られません。PassiveAI の艦も、手動・反撃の相手なら移動中に撃ちます。

- `shipAiTargetAuthority = NEW` で、艤装(騎乗体)に乗った姫が原作どおり提督についていき、動く相手の護衛を続けるようにしました。プレイヤーが艤装に乗って操縦している間は、艤装が勝手に歩かないようにしました。

- `shipAiTargetAuthority = NEW` で指揮棒からチームを座らせる・立たせるとき、原作どおり先頭の艦の状態を基準に全員をそろえるようにしました。SINGLE で未選択の艦をクリックしても、選択中の艦を立たせられます。

- 駆逐艦以外の艦で、待機中の怨念の消費が原作より多くなっていた問題を修正しました(赤城では約4.7倍)。原作どおり128tickごとの消費だけに戻しました。
- 赤城と加賀が零戦・天山を発艦し、発艦時に弓の音を鳴らすようになりました(原作どおり)。
- 赤城・加賀・ヲ級・戦艦レ級の艦載機の最大数が、レベルに応じて艦ごとに増えるようにしました(原作どおり)。レベル100の赤城は軽56/重32機になります。
- 味方の空母系の艦載機が、艦ごとに原作と同じ高さから発艦するようにしました。発艦位置が水中や当たり判定のないブロックでも、塞がれたとみなさないようにしました。

- 警備中に移動しながら攻撃する艦の攻撃・照準や移動の更新間隔を原典の速さに戻しました。
- 指揮棒で FORMATION の移動を指示したとき、到着しても陣形を保ち、同じ地点を再指示すると追従へ戻るようにしました。

- 指揮棒の指示が届かなかったときの案内や隊への加入・脱退通知を、画面左下に折り返し付きで表示するようにしました。沈没の案内は別の行に分け、長い文が画面からはみ出さないようにしました。

- 味方の艦が1段の段差の向こうの目標を見て攻撃できるようになりました。
- 北方棲姫と SSNH が乗った直後に降りなくなりました。天井や戸口で窒息判定を受けても降りません。

- 簡体字・繁体字の未翻訳テキストを補い、AI設定、指揮棒、艦種、説明書や更新通知などを中国語で表示できるようにしました。

- 艦への補給で、弾薬箱の軽弾・重弾の種類と補給量が正しく反映されるようにしました。携行食4・5でデバフを除去し、補給直後に艦の弾数・士気などの表示を同期します。

- 北方棲姫と SSNH が退屈して座っているとき、所有者の右クリックでプレイヤーに乗るようになりました。近くの味方の艦への気まぐれな騎乗と、しゃがみ・被弾などによる降車も復元しました。
- 北方棲姫と SSNH が乗ってくる操作を、素手でしゃがまずに右クリックしたときに限定しました。しゃがみ右クリックでは GUI を開け、アイテムを持っているときは通常のアイテム操作ができます。観戦中の提督には自分から乗りに行きません。
- 指揮棒を構えると、艦が護衛している相手の足元に円が表示されるようになりました。
- `shipAiTargetAuthority = NEW` で指揮棒の指示が届かなかったとき、選択や距離・次元・陣形の条件をアクションバーで案内するようにしました。
- 艦が沈んだ場所が死亡メッセージと、NEW の指揮棒で指示が届かなかったときの案内に表示されるようになりました。SINGLE では沈んだ艦を飛ばして次の艦へ指示します。

- 深海棲艦と敵艦載機が、自陣営のミサイル・ビーム・艦載機を誤って狙う問題を修正しました。深海棲艦のミサイル・ビーム・設置弾は、深海棲艦側の艦や艦載機を巻き込まなくなりました。艦娘は対空戦闘ONなら深海棲艦が撃ったミサイルと艦載機を迎撃し、OFFなら狙いません。
  あわせて、艦娘のビーム・設置弾(ブラックホールの引き寄せなど)・北方棲姫の浮遊要塞は、持ち主のプレイヤーとそのペットを巻き込まなくなりました(原作どおり)。深海棲艦からプレイヤーへの攻撃は従来どおりです。

- 管理者向けに、艦の攻撃対象選別を変更せず新旧AIの差分を計測する `/shipaishadow` コマンドを追加しました。

- 管理者向け設定 `shipAiTargetAuthority` を追加しました。0.12.0の既定は `NEW` で、手動指示、反撃、自動索敵の順に1つの目標権限で管理します。`LEGACY` を選ぶと従来のAIを使えます。

- プレイヤーが攻撃したときや攻撃されたときに周囲の同じ持ち主の艦娘が加勢し、深海棲艦が攻撃されたときに周囲の深海棲艦が応援する反撃連携を復元しました。

- 燃料回復やAI設定の切り替え後、実行中だったAIの占有だけが残り、艦が目標を取らない・移動しない状態になる問題を修正しました。

- ミサイルを強い攻撃や魔法系ダメージで撃ち落とせるようにし、別の持ち主のミサイル爆発へ巻き込まれると誘爆するようにしました。

- `shipAiTargetAuthority = NEW` では、深海棲艦が別の持ち主のミサイルを自動で狙って撃ち落とすようになりました。

### Breaking

- マルチプレイでは、サーバーとすべてのクライアントを同じ0.12.0へ更新してください。0.11.0との混在接続はできません。
- 艦AIの判断部分にPolyForm Shield License 1.0.0を適用しました。適用範囲とMITのまま残る部分はLICENSEを参照してください。

### Migration

- 更新前にワールドをバックアップしてください。確認した0.11.0の保存例では、所有者・編成・婚姻・装備・貨物と死亡卵を引き継ぎ、継続プレイと卵からの復活を確認しています。
- 初回更新時に `config/shincolle_kai-common.toml` の `shipAiTargetAuthority` が一度NEWになります。旧AIを使う場合は初回更新後に `shipAiTargetAuthority = "LEGACY"` へ変更してワールドに入り直してください。その後の手動選択は保持されます。`shipAiNewDefaultApplied` は変更しないでください。
- 公開Java APIとKubeJSの定義は維持しています。内部AIクラスへ直接依存するアドオンは互換保証の対象外です。

### English summary — 0.12.0 (Beta)

- The redesigned ship AI (NEW) is now the default. Follow recall, vertical-separation notices, void rescue in the End, and Tenryuu, Tatsuta and Nagato's special attacks are implemented.
- Fixes cover fishing, cargo capacity, ship tanks, text overlap, damage voices, level-up sounds and first-person petting-hand rendering. In-game visuals, sounds and the feel of the AI still need verification.
- The ship AI core uses PolyForm Shield License 1.0.0. Other code, upstream-derived portions and code published in 0.11.0 or earlier remain MIT; see LICENSE for the scope.
- **Breaking:** use the same 0.12.0 version on the server and every client. Mixed connections with 0.11.0 are not supported.
- **Migration:** back up your world first. Tested 0.11.0 saves preserve ships, formations, marriage, equipment, cargo and sunk-ship eggs. The first update switches `shipAiTargetAuthority` to NEW once. To use the previous AI, set it to LEGACY after updating and re-enter the world. Leave `shipAiNewDefaultApplied` unchanged. Public Java API and KubeJS definitions are preserved; internal AI dependencies are outside the compatibility guarantee.
- **Known issues:** petting may not raise morale; ships do not render held items. Some ship work tasks, combat effects and models still need in-game checks or restoration.

## v1.20.1-0.11.0 (β版)

**β版です。**0.10.3 からの変更は、ボイス設定の追加、造船所・航海日誌・指揮棒まわりの改善と、マルチプレイを含む不具合の修正です。

- 指揮棒の地点護衛をブロックへのShift＋左クリック、陣形画面をShift＋右クリックに変更しました。自艦へのShift＋左クリックは従来どおり編成操作です。

- 深海棲艦の被弾・撃沈ボイスをクライアントごとに既定OFFにし、艦娘設定画面から
  味方艦の通常ボイスとは独立してON/OFF・音量調整できるようにしました。

- 艦娘の専用ボイスが2つ後ろの艦種で再生されていた問題を修正しました。響・電・愛宕・金剛は
  自分の専用ボイスを使い、専用ボイスのない天龍・榛名は共通ボイスを使うようになります。

- 空容器専用出力スロットの追加前に設置した小型造船所へ溶岩容器を入れると、保存されていた
  古いスロット数が読み込まれてサーバーが落ち続ける問題を修正しました。既存スロットの中身を
  保ったまま、現在必要なスロット数へ自動的に拡張します。

- 島風が召喚した連装砲ちゃんがその場から動かず、攻撃もしなかった問題を修正しました。攻撃対象へ接近して6回攻撃し、対象を失った場合は島風の新しい対象へ切り替えます。未使用弾薬の返却で召喚前より弾薬が増える問題も修正しました。

- リログやディメンション移動後に指揮棒の所属艦が内部ID表示のままになり、再選択するまで指揮しにくい問題を改善しました。指揮棒GUIを開くと、ロード済みの所属艦を自動的に再取得します。

- 航海日誌の図鑑で、艦娘と深海棲艦の座り・走行・攻撃ボタンが動作するようになりました。

- 航海日誌の図鑑で、深海棲艦にも感情ボタンが効くようにし、プレビューの時間が止まるか遅く進んで表情やまばたきが固着する問題を修正しました。

- 小型造船所の燃料スロットへ、溶岩入りバケツだけでなく艦載タンクなどの溶岩入り液体コンテナも投入できるようにしました。ショートカット投入は左の燃料スロットへ入り、複数バケツ分を持つ容器は空になるまで保持します。空容器は怨念スロット直下の専用出力枠へ移り、燃料満タン時は溶岩容器を1個待機させます。

- 艦娘の通常ボイスと時報をクライアントごとに既定OFFにし、艦娘設定画面からそれぞれ独立してON/OFF・音量調整できるようにしました。設定が再生へ反映されない不具合も修正し、他のプレイヤーの聞こえ方には影響しません。

- 艦娘の経験値バーの分母が実際と違う値で出ていた不具合を修正しました。また、一度に多くの経験値を得たときに複数レベル上がるようにしました。

- 航海日誌の図鑑で艦種と縦書き艦名を原典のリボン位置へ戻し、モデルの初期角度・拡大率・ドラッグ操作と感情ボタンを復元しました。

- 航海日誌でページ全体がページ送りとして反応していた判定を、左右下部の矢印付近だけに縮小しました。

- 航海日誌の日本語を漢字カタカナ交じりに統一し、承認済みの説明・制作クレジットを英語・簡体字・繁体字にも反映しました。見出しと図の配置を調整し、旧変身ページを削除しました。

- チュートリアル本と提督の机の図鑑で、艦モデルが右下へずれ、反転して表示される問題と、縦ドラッグがモデルに反映されない問題を修正しました。

- 小型造船所を壊すと、中の資材と燃料が失われていた不具合を修正しました。壊した造船所を置き直すと元に戻ります。

- マルチプレイで、別のプレイヤーの艦娘同士が召喚直後から攻撃し合う不具合を修正しました。PvP優先を設定した艦娘が敵対チームの艦娘を狙う動作は変わりません。

- 轟沈した艦娘の卵を他のプレイヤーが使っても、元の持ち主の艦娘として出るように修正しました。

- 小型・大型造船所で建造が完了した後も、次回投入する4種類の資材数を保持するようにしました。

- 大型造船所の放出ボタンが機能しなかった問題を修正しました。選択中の資材を入力枠へ戻し、
  放出モード中はホッパーなどから取り出せるようになります。

## v1.20.1-0.10.3 (β版)

**β版です。**0.10.2 からの変更は、攻撃頻度の移植不具合の修正です。

- 1.20.1 への移植時に、深海棲艦・艦娘の通常砲撃、近接攻撃、空母の発艦、
  艦載機の攻撃間隔が原典の約2倍になっていた不具合を修正しました。

## v1.20.1-0.10.2 (β版)

**β版です。**0.10.1 からの変更は、婚約と表示まわりの不具合の修正です。

- 他MOD併用時などに艦の持ち主情報がずれ、指輪をシフト＋右クリックしても婚約できずGUIが開くことがある不具合を修正しました。
  持ち主が艦を右クリックしたときや艦が読み込まれたときに、ずれた持ち主情報を自動で直します。
  `/shipupdateowneruid` も、卵から出した艦を対象にするようにしました。

- 製品版で結婚指輪のツールチップに「結婚シタ数」の数値が表示されない不具合と、結婚直後に数値が更新されない不具合を修正しました。

- 同じ原因で製品版では働いていなかった、指揮棒のツールチップ、指揮棒を持ったときの艦隊の艦の表示、
  装備のツールチップで Ctrl を押したときのエンチャント表示の切り替えを直しました。

## v1.20.1-0.10.1 (β版)

**β版です。**0.10.0 からの変更は、ポリメタル鉱石の生成分布の修正だけです。

- ポリメタル鉱石が深層岩の層でも生成されるようにしました。生成範囲を Y 3〜50 から
  Y -61〜50 へ広げ、1チャンクあたりの生成回数を 7 から 20 に増やしています。
  一番多く出る高さは Y 26 付近から Y -5 付近へ下がり、Y 19 付近より上では以前より
  少なくなります。深層岩の中にも通常のポリメタル鉱石が置かれます。

## v1.20.1-0.10.0 (β版)

**β版です。**0.9.0 からの変更です。

初めてワールドに入ると説明書（深海提督ノ航海日誌）が1冊配られ、入門の章「はじめに」で
建造から艦隊の指揮までを案内するようになりました。既存のワールドでも、まだ受け取っていない
プレイヤーには次のログインで配られます。説明書は本文の誤り・日本語の抜け・はみ出しを直し、
図鑑を最新の内容にしています。原典 1.10.2 と食い違っていたレシピ、深海火山コア、怨念重弾薬を
原典どおりに戻し、JEI から説明書のレシピを開けるようにしました。

- 説明書の事実誤り、日本語の抜け、はみ出し、誤植を直し、図鑑と艦・アイテム名を最新の内容にしました。

- 指揮棒・深海ノ女神・近代化改修ツール・修業ノ書・怨念経験値・模型の分解のレシピを、説明書の絵のとおり原典に戻しました。

- 怨念重弾薬をブレイズパウダーと各種素材から作れるようにしました。

- 深海火山コアの回復・着火・燃料・エモート・泡の効果を原典どおりに戻しました。

- 図鑑の説明文を上に詰めて読みやすくし、JEI 導入時は説明書のレシピのアイコンをクリックするとレシピ画面が開くようにしました。

- JEI 導入時、説明書のレシピのアイコンから JEI のレシピ・使い道の表示とブックマークへの追加ができるようにしました。

- 説明書の本文がアイコンや絵に重なる問題、図鑑に `<br>` が表示される問題、章名がページタイトルに重なる問題を修正しました。

- 深海提督ノ航海日誌と提督の机の画面が中央に収まり、JEI の一覧との重なりで文字が崩れる問題を修正しました。

- 指揮棒で艦隊へ追加した艦をその場で選択状態にし、直後の移動・攻撃・護衛指示が届くようにしました。

- アドオンが対空・対潜属性を付けた艦を、同じ所有者の艦が攻撃目標として選んでしまう問題を修正しました。

- 艦の通常攻撃と連装砲召喚体の発砲で攻撃モーションが再生されない問題を修正し、赤城の攻撃時の布の動きも復元しました。

- Forgeの更新確認で新版を検出したとき、起動1・2・4・8・16回目に配布ページへのリンクを
  チャットへ表示する更新通知を追加しました。クライアント設定で無効化できます。

- 艦娘の座り・ごろ寝・怨念切れ等で高さがずれる移植不備を修正しました。
  原典の姿勢移動、サイズ別分岐、身体部品の移動を復元しています。
  自動接地補正ではないため、原典由来の浮き・沈み込みは残り得ます。

- 指揮棒で指定した移動先の白いマーカーが、ブロック面から約0.8ブロック浮いて表示される
  問題を修正しました。即時表示の緑マーカーと同じ高さになります。

- クリーパーなどの爆発で壊れたブロックから怨念が出る問題を修正しました。怨念が付くのは
  モブの死亡ドロップだけになります(原典1.10.2と同じ範囲)。爆発は壊したブロックの大半を
  ドロップさせないため、これまでは「土ではなく怨念が出る」状態になっていました。
- あわせて、怨念のドロップ数を設定 `dropRateGrudge` に従わせました(既定1.0なら1個)。
  これまでは設定を読まずに1〜3個のランダムだったため、**入手量は減ります。**
  スライムとゴーレムからも怨念が出るようになり、こちらは原典どおりに戻ります。

- OP向けの `/shipsummonmount` を追加しました。32ブロック以内で見ている対応艦の
  外見トグルを有効にし、連装砲の騎乗体を即時召喚します。再実行しても騎乗体は増えず、
  燃料切れ・非対応艦・対象不在では理由を表示します。

- 昼夜サイクルを止めたワールドで、時刻がちょうど時の区切りにあると時報が絶えず鳴り続ける
  問題を修正しました。同じ時刻の区切りでは1艦につき1回だけ鳴ります。原典由来の不具合です。

- 艦インベントリのレベル表示が、接続先のサーバーの絶対レベル上限に到達したときだけ
  金色になるようにしました。上限は接続・リスポーン・次元の移動・設定の再読み込みのときに同期され、
  クライアント側の設定がサーバーと異なる場合も、サーバーの値に従います。

- 深海棲艦の乗り物が解除後も残って積み重なる問題を修正しました。解除時に乗客を降ろして
  乗り物を消し、宿主を失った個体も自動で消えます。移植で欠落した原典の挙動の回復です。

- 指揮棒の陣形GUI・地点護衛・実体護衛・チーム管理・モード切替に使うキーを、
  操作設定の「ShinColle-kai：指揮棒」から変更できるようにしました。
  クリック／ホイール時の修飾入力を単独キーとして表示し、既定設定では従来のCtrl・
  スニーク状態・ダッシュ状態を維持します。重複時は実体への操作、陣形GUI、地点指示の
  既存の優先順に従います。

- 指揮棒の自艦のチーム管理をShift+左クリックへ統合しました。未編成なら追加、編成済みなら選択、
  選択中なら削除し、追加・削除は艦種と艦名をアクションバーへ表示します。
  通常の左クリックは何もせず、攻撃指示と紛らわしい対象クラス登録通知も出しません。
  モード切替後も選択と護衛先を保持し、全モードで選択マーカーを表示します。

- 艦が乗り物に乗っている場合も、通常の移動指示は乗り物の経路完了時に解除されるよう修正しました。

- 指揮棒の通常のブロック右クリックは移動指示になり、経路完了後に地点指定を解除して追従へ戻ります。
  Shift+右クリックでは到達後もその地点を護衛します。陣形GUIの入口をCtrl+右クリックへ移しました。

- 陸上の移動指示で、艦が通り過ぎた経路点へ戻ろうとして旋回し続ける問題を修正しました。
  非飛行艦は高さの変わり目までの経路で、直進できる最も遠い点を目指すようになります。
  先読みでは進行線上の足元も確認し、床のない隙間を避ける迂回経路を維持します。
  幅1.0以上2.0未満の騎乗体などで先読み目標がセルの角にあると、足元走査が終了せず
  サーバーが停止する問題を修正しました。支持確認を移動区間内に制限します。

- 指揮棒の攻撃指示を艦が保持するようにしました。自動索敵で指示を失わず、報復が終わると
  指定対象へ戻ります。同じ対象への再指示で解除できます。射程外では指示を残して自衛し、
  対象が戻れば再開します。着席・燃料切れ・対象の無効化などで解除され、再読み込み時は保持しません。

- 以前に外観で切り替えた島風も、読み込み時に連装砲ちゃんが通常の連装砲で出るようにしました。
  外観グリッドからは既にこのビットのセルが消えていましたが、以前に立てた値は保存データに
  残ったままでした。

- 初めてワールドに入ると説明書「深海提督ノ航海日誌」が1冊配られるようにしました。入門の章
  「はじめに」を14ページに書き直し、このMOD・資材・最初の建造・小型/大型造船所・艦の世話・
  艦隊・指揮・困ったとき・深これ改の独自要素を、今の遊び方に合わせて説明します。第1章の
  指揮棒のページも今の操作に直しました。設定で配布を止められます。

## v1.20.1-0.9.0 (β版)

**β版です。**CurseForgeでの配布はこのバージョンが初回になります。
以下は最後のα版(`1.20.1.0.8.2`、2026-08-23)からの変更です。

造船所を建てて艦を建造し、装備させ、艦隊を指揮して戦い、育てて婚約するところまで
一通り遊べます。ただし未完成の領域と既知の不具合があります。**`1.0.0` は
それらが解消された版のために取ってあります。**既知の問題は配布ページに掲載しています。

KubeJS連携・装備datapack・Javaアドオン向けPublic APIは、このリリースが初出です。

### 敵艦がピースフルで消えない問題を修正

- 難易度をピースフルにしても金剛(敵)などの深海棲艦が消えずに残る問題を修正しました。
  1.10.2 と同じく、ピースフルでは敵対する艦が消えます。味方艦は残ります。

### 移動経路に九一式徹甲弾の画像が出る問題を修正

- 艦が移動すると、その航跡に九一式徹甲弾の画像が並ぶ問題を修正しました。
  航跡のしぶきが参照するテクスチャが誤っていたためで、通常の水しぶきになります。

### 拾ったアイテムが開けないページへ入る問題を修正

- 艦が拾ったアイテムが、ドラム缶で解放していないインベントリページへ格納され、
  GUIから取り出せなくなる問題を修正しました。**そのアイテムは艦自身の消費からも
  見えていなかった**ため、弾薬や食料を拾わせても使われないことがありました。
- 解放済みのページが満杯になった時点で、それ以上は拾わなくなります(1.10.2 と同じ)。

### 艦の一覧画面で首が横を向く問題を修正

- スニーク+右クリックで開く画面の右上プレビューで、艦の首が真横まで曲がる問題を
  修正しました。艦が向いている方向によって曲がり方が変わる症状でした。

### 大型造船所の渦のちらつきと暗さを修正

- 渦がちらつき、見る角度によって暗くなる問題を修正しました。真上から見ると通常の明るさで、
  横から見るほど暗くなる症状です。
- 原因は移植時の退行でした。原典の渦はライティングの影響を受けませんが、移植版は
  法線で明るさが変わる描画経路を通っていました。渦はプレイヤーの方を向く板なので、
  カメラを動かすと明るさが脈打っていました。
- 建造開始で渦のテクスチャと回転速度が変わる挙動は元から正常です。建造を始めていない
  待機状態では変わりません。

### 艦の外観設定を艦のインベントリ画面へ移動

- 設定画面にあった外観の切り替え(モデルパーツのON/OFFと手持ちアイテム表示)を、
  艦のインベントリ画面の「設定」ボタンの下へ移しました。設定画面を開かずに切り替えられます。
- 設定ボタンの左端に揃えて1行4個で並び、パーツ数が多い艦は2段になります。パーツを持たない艦では表示されません。
- 設定画面の「外観」セクションは無くなりました。他の項目の位置は変わりません。

### 中間棲姫の名前を修正

- 「ミッドウェー棲姫」と表示されていた艦名を、艦これの名称に合わせて「中間棲姫」へ修正しました。
- 使われていない古い翻訳キーを4言語から計320件削除しました。表示に変化はありません。

### 戦闘演出を一部追加

- **スキル発動時に爆発と煙が出るようになりました。**これまでスキルは無音・無演出で
  発動していました。艦ごとの固有演出ではなく共通の演出です。
- 北方棲姫のモデルパーツを有効にすると水滴が滴るようになりました。
- 内部的にパーティクル6種(水滴・雪煙・回避表示・スプレー3種)を追加しました。
- **これは応急処置です。**1.10.2 の艦ごとの固有演出は入っていません。詳細は
  配布ページの既知の問題を参照してください。


### アイテム回収ボイスを復元

- 味方艦がアイテムを回収した際、50%の確率で艦種に応じた回収ボイスを再生するようにしました。
- ボイスの連続再生を40〜49tick抑え、拾得音とボイスの音量を既存の艦音量設定へ統一しました。
  これまで拾得音は設定に関わらず固定音量で鳴っていたため、既定設定ではやや大きくなり、
  艦音量を0にしている場合は鳴らなくなります。

### 色のレベル上限を設定可能に

- 共通設定（common config）の`maxLevelUnmarried`と`maxLevel`で、未婚色と既婚色のレベル上限を別々に設定できるようにしました。既定値は100/150のままです。
- レベル1000までの育成・管理コマンド・遠征計算に対応しました。設定上限を上げると、遠征の失敗率と運はその上限に対する現在レベルで正規化されます。
- レベル150超で照準時間が負になり即発砲してしまうため、原典の最短値10tickを下限にしました。150以下の値は変わりません。

- 装備定義へ入手経路フラグを追加し、能力確認用のデバッグ装備が造船所・宝箱・メインタブ・JEIへ混入しないようにしました。デバッグ装備は専用クリエイティブタブへ移し、既に所持している装備の挙動は変わりません。
- 付与弾(Enchant Shell)がポーション効果を持たなかった問題を修正しました。クラフト結果のNBTが読み取り側の形式と食い違っていたため、ツールチップに何も出ず命中時にも何も付与されませんでした。同じポーションを重ねてクラフトすると持続と発動率が伸びる原典挙動も戻っています。
- 日本語未訳だった装備名11件(一式徹甲弾・対空拡散弾・三式焼霰弾・劣化ウラン弾・重力弾・反重力制御弾・付与弾・クラスター弾・15inch四連装砲・12inch三連装砲・深海伏撃魚雷)を追加しました。
- 装備ツールチップの素材欄と稀少度欄を別行に分け、横幅が伸びるのを抑えました。挙動が自明でない装備には説明行が付きます(現在は付与弾のみ)。
- 弾薬5種(91式徹甲弾・一式徹甲弾・三式焼霰弾・劣化ウラン弾・反重力制御弾)の命中時効果を、Java内の固定値から装備定義JSONの`attack_effects`へ移しました。効果・強さ・持続・発動率は変わりません。datapackやアドオンから弾薬の効果を変更・追加できるようになりました。ツールチップ上では効果行が素材・稀少度の上へ移動します。

### つないだウェイポイントの巡回と待機時間を復元

- 味方艦がウェイポイントへ到着すると、ターゲットレンチでつないだ次・前のリンクをたどって、次の地点へ進むようになりました。前の地点へ戻るリンクでは前側を選ぶため、往復する経路も巡回できます。
- 艦側とウェイポイント側の待機設定のうち長い方を採用します。待機設定0は即座に次の地点へ進み、1〜16は原典どおり段階ごとの時間へ変換されます。
- ワールドの原点、Y=0、負のYにあるウェイポイントのリンクも、保存・再起動の後に正しい経路として維持されます。
- ターゲットレンチを持って自分のウェイポイントを右クリックすると、ウェイポイント側の待機設定を0〜16で循環変更できます。

### MOD IDを `shincolle_kai` へ変更

- MOD IDを `shincolle` から `shincolle_kai` へ変更しました。MOD名は ShinColle-kai に統一しています。
- 旧IDで保存された艦娘やアイテムは引き継げません。1.20.1では移行する手段がありません。ワールド自体は開けます(起動時の「missing registry entries」で止まらないようにしてあります)。旧版のJARを残しておけば、旧ワールドは旧版で開けます。
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
- 単艦モードが選択中の全艦へ作用していた問題を修正し、選択済みかつ実体がある最小スロットの1隻だけへ指示するようにしました。
- 単艦モードの対象艦が射程外または別の次元にいる場合は、後続艦へフォールバックせず何も起こらない原典挙動を復元しました。
- スプリント操作中はモード切替とHUDを抑止し、艦隊操作との競合を防ぎました。
- 移動指示の目的地と艦から目的地への線を継続表示し、燃料切れで命令を受けられない艦を赤マーカーで示します。
- 経路表示へ武装用の粒子が誤用され、九一式徹甲弾の画像が大量表示される問題を修正しました。原典の白い目的地マーカーとガード線へ戻し、同じ地点の重複・点滅も抑えました。
- 目的地マーカーの描画状態が後続描画へ漏れ、艦モデルが暗転する問題を修正しました。
- 移動先・攻撃対象マーカーが巨大爆発へ誤変換され、目的地で爆風が再生成され続ける問題を修正しました。
- Y=0以下の目的地が無効扱いされる問題を修正し、カスタムディメンションを含む正確な目的地の次元を同期するようにしました。
- 新規艦や一部の旧保存艦が、未命令なのに旧未設定座標`(-1,-1,-1)`へ移動しようとして追従しない問題を修正しました。正規の負Y目的地は維持されます。
- 単艦モードの先頭選択艦が別の次元にいるか未ロードのとき、後続艦へ命令が誤って移る問題を修正しました。
- 艦の同期の内容を変えたため通信のプロトコルを8へ上げ、旧版のJARとの接続は拒否します。

### 婚約指輪の右クリックを修正

- Forge 1.20.1の生物右クリック経路へ婚約指輪を接続し、スニークして所有艦へ使った際にインベントリ操作や通常の指輪切替と競合する問題を修正しました。

### ウェイポイントと艦タスクの誤動作を修正

- チェストを設定していないウェイポイントがワールド原点`(0,0,0)`を作業先として扱い、そこにあるかまどやインベントリを誤使用する問題を修正しました。
- ワールド原点、Y=0、負のYにある容器もターゲットレンチで正規の作業先として設定でき、保存後も維持されます。
- 艦タスクの入力・出力・燃料面設定が無視され、調理時にかまどのスロットを直接操作する問題を修正しました。Forgeの面別インベントリ経由で搬入・搬出します。
- 全面が有効な既定設定で、原木など燃料にもなる調理材料が入力スロットではなく燃料スロットへ誤投入される問題を修正しました。回収後に艦の荷物へ収まらない出力も消失せず、その場へドロップします。
- メタデータ・NBTの比較の設定を、クラフトと調理の材料選択・結果回収に反映しました。

### 宝箱の戦利品を復元

- 存在していた戦利品JSONがどの宝箱にも接続されず、スポーン卵・武装・婚約指輪などが一切出現しなかった問題を修正しました。
- ボーナスチェスト、イグルー、ダンジョン、村の武器鍛冶屋、廃坑、砂漠の寺院、ジャングルの寺院、ネザー要塞、要塞、エンドシティへ原典相当の戦利品を追加しました。
- スポーン卵の建造区分・艦種と、武装のランダムな種類を正しいNBTで生成するようにしました。
- 原典の抽選回数、幸運による追加抽選、重み、確率、個数を復元しました。

### 艦の標的保持と反撃を修正

- 反撃対象を得た直後に反撃AIが終了し、照準が毎回やり直しになって攻撃できなかった問題を修正しました。
- 複数の敵から攻撃されても照準中の標的を保持し、照準時間を正常に進めるようにしました。
- 索敵後に地形などで視線が一瞬切れても標的を即座に捨てず、視線復帰後に攻撃を継続できるようにしました。
- 標的の死亡、射程外、無敵化では従来どおり標的を解除します。

### 味方艦の感情・反応を復旧

- 味方艦の撫で・攻撃・被弾・待機・指揮・艦娘タスクに対応する感情反応が呼ばれず、表情やエモートが出なかった問題を修正しました。
- 反応ごとの発生確率と待ち時間を原典1.10.2へ戻し、被弾反応の割り込みとショックの反応も復旧しました。
- 給餌成功時と満腹で拒否した時の専用のエモートが表示されなくなる問題も同時に修正しました。
- 原典で汗・しずくを表す感情タイプ0がクライアント描画直前に破棄され、満腹拒否など一部の反応だけ表示されない問題を修正しました。
- 指揮棒で撫でた実際の部位がサーバーへ伝わらず、別の部位として反応する問題を修正しました。
- 頭から胸までの撫での姿勢を原典どおり約4秒表示し、不機嫌時などの押し返しで反応対象を見失う問題を修正しました。
- 押し返しの速度が対象のプレイヤー本人へ同期されず、実際には動かなかった問題を修正しました。

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

### 提督状態の所有境界を整理

- 提督capabilityの永続状態、実行時キャッシュ、クライアント同期値を独立した内部状態へ分離し、保存・respawn・GUI同期の責任を明確にしました。
- 既存saveのNBT形式、通信形式、公開メソッド、艦隊・婚約・cooldownの挙動は維持しています。
- クライアント同期listは外部から書き換えられないviewにし、入力listと配列は防御コピーするようにしました。

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

## Historical English notes — mod ID migration

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
