# Java addon最小例

この例はShinColle-kai本体JAR同梱の`com.lulan.shincolle.api`だけを使い、独自艦属性、
独自Item、固定JSON値、Item NBT由来の動的値を同じcanonical resolverへ接続します。

本体の`build`はこのJava sourceを専用source setでコンパイルします。サンプルclass/resourcesは
本体release JARへ入りません。

実際のaddon projectへコピーするときは、Forge 1.20.1の通常のMDKを用意し、
ShinColle-kai本体を`compileOnly fg.deobf(...)`と実行環境へ追加してください。公開Maven座標は
配布開始時に確定するため、この例では架空の座標を記載しません。

起動時registryをclient/server双方で一致させます。属性やItem登録の変更には再起動が必要です。
equipment JSONの値だけを変更した場合はserverの`/reload`で反映され、clientへ同期されます。

JSONの`shincolle_example:sonar_precision`は登録済みcustom属性なので受理されます。JSONの
0.15とItem NBTの`SonarCalibration`は加算されます。dynamic値が不要ならItemは通常の`Item`でよく、
JSON-only datapack例と同じ経路を使えます。

動的Item APIは`ResolvedShipEquipment`からResourceLocation識別の`ShipAttackEffect`も返せます。
固定効果だけならequipment JSONの`attack_effects`を使い、Java callbackは不要です。
