## 変更内容 / What changed

<!-- 何を変えたか。理由は下の欄へ。 -->

## 理由 / Why

<!-- なぜ必要か。関連するissue、`docs/specs/`、`docs/development_status.md` のtopicがあればリンク。 -->

## 検証 / Verification

- [ ] `./gradlew compileJava`
- [ ] `./gradlew checkstyleMain`
- [ ] `./gradlew runGameTestServer`（サーバー挙動を変えた場合）
- [ ] `./gradlew runGameTestServer -PshincolleMinimalRuntime=true`（optional MOD境界に触れた場合）
- [ ] 実機確認が必要（描画・GUI・入力を変えた場合。`検証.txt` へ項目を追加したか）

<!-- 結果を貼る。「通った」だけでなく件数まで。例: 通常180/180、最小180/180 -->

---

## Documentation impact

**「変更不要」を選ぶ場合も、一度考えてからチェックすること。**
判断基準は `docs/documentation_qa.md`。

- [ ] ドキュメント変更不要
- [ ] README更新済み
- [ ] CHANGELOG更新済み
- [ ] API Documentation更新済み（`docs/java_addon_api.md`）
- [ ] KubeJS Documentation更新済み（`docs/kubejs_integration.md`）
- [ ] Datapack Documentation更新済み
- [ ] 日本語・英語双方を確認済み
- [ ] セーブ互換性への影響を確認済み
- [ ] Breaking Changeとして明記済み

### 変更場所からの逆引き

該当する行があれば、右側のドキュメントを確認する（`documentation_qa.md` 10節）。

| 変更場所 | 確認するドキュメント |
|---|---|
| Registry | README / Migration |
| Public API | `docs/java_addon_api.md` / `examples/` |
| KubeJS | `docs/kubejs_integration.md` |
| Datapack | Datapack docs / `examples/` |
| Config | README / Config docs |
| Dependency | README「動作環境」/ `mods.toml` |
| Entity behavior | README「特徴」 |
| Save format | README「互換性」/ Migration |

---

## Breaking Change

次のいずれかに該当する場合はチェックし、下の3点を必ず確認すること。

- [ ] MOD ID / registry ID / ResourceLocation
- [ ] NBT構造 / Capability / Packet format
- [ ] config key / item・entity ID / datapack format
- [ ] Public APIシグネチャ / KubeJS API

該当する場合:

- [ ] CHANGELOGへ `Breaking` として記載した
- [ ] READMEの互換性説明を更新した
- [ ] **Migration（どうすればよいか）を書いた**

<!-- Breaking Changeを書くだけではユーザーは困る。移行手順まで書くこと。 -->
