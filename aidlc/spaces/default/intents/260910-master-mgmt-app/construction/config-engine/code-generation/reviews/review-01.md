## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-16T20:25:55Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/config-engine/functional-design/functional-spec.md > Assumptions & Open Questions | 監査ログactor伝搬ギャップ(W5/W6でもactor="system"となる既知の未解決事項)が、`## Assumptions & Open Questions`セクションに`[open question]`として明記されているかを再確認した。実際に「W5・W6...ConfigChangedEventのactorには操作を行った利用者のIDを記録すべきだが...現状の実装はW5・W6についてもactor="system"を用いており...完全には満たしていない」という`[open question]`項目が存在することを確認した。`code-summary.md`もこの記載箇所を正しく参照している。 | 対応不要(検証済み) | Resolved |
| R-02 | Minor | backend/src/main/java/com/mastersmith/config/store/ConfigModelStore.java > writeTableConfigDraft | BR1.8スキップ判定が`ConfigCache`(インメモリ)ではなく`TableConfigRepository#existsBySchemaNameAndTableName`(DB直接参照)に対して行われるよう修正されていることを確認した。同一トランザクション内での残余競合リスクは、単一インスタンス構成という受入れ済み前提の下でjavadocに明記されている。 | 対応不要(検証済み) | Resolved |
| R-03 | Critical | backend/src/main/java/com/mastersmith/config/event/ConfigChangedEvent.java, ConfigModelStore.java(writeTableConfigDraft/importConfigSet), TranslationStore.java(upsert) | `rules.md` BR1.13は「1回のAPI呼び出しで複数エンティティが変更される場合でも、呼び出し単位で1件にまとめてはならない(変更されたエンティティごとに1件発行)」と明記し、`entities.md`のConfigChangedEventは`targetType`/`targetId`/`beforeValue`/`afterValue`/`operation`(CREATED/UPDATED区別)を必須属性として定義している。しかし実装の`ConfigChangedEvent`レコードは`(operation, target: String, actor, occurredAt)`のみを保持し、`targetType`/`targetId`/`beforeValue`/`afterValue`が存在しない。さらに`writeTableConfigDraft`は複数テーブルを取り込んでも`"tables:%d".formatted(...)`という単一の説明文字列で**呼び出し単位に1件だけ**イベントを発行しており(BR1.13の「呼び出し単位で1件にまとめてはならない」に明確に違反)、`importConfigSet`も同様に呼び出し単位で1件のみ発行する。`TranslationStore.upsert`も新規/更新の区別なく常に`TRANSLATION_UPSERTED`を発行し、`operation`(CREATED/UPDATED)を実体化していない。これはAuditLogging(U7)が要求する`AuditLogEntry`(actorUserId/targetType/targetId/operationType/occurredAt/beforeValue/afterValue)へのマッピングを不可能にし、project.md Mandated(「監査ログは...操作対象・操作種別...変更前後の値を記録する」)にも抵触する。ブラストレディウスはAuditLogging(U7、既に実装済み)のイベント購読契約全体に及ぶ。 | `ConfigChangedEvent`のフィールドを`entities.md`のConfigChangedEvent定義(targetType, targetId, beforeValue, afterValue, operationのCREATED/UPDATED区別)に一致させ、`writeTableConfigDraft`/`importConfigSet`/`TranslationStore.upsert`を変更されたエンティティ単位で個別にイベント発行するよう修正する。 | New |
| R-04 | Major | aidlc/spaces/default/intents/260910-master-mgmt-app/construction/config-engine/code-generation/traceability.json | `traceability.json`の`upstream_ids`/`coverage`は`rules.md`が定義する全14ルール(BR1.1〜BR1.14)のうちBR1.13(監査ログイベント発行)とBR1.14(isPrimaryKey設定)の2件を完全に欠いている。BR1.14は実際に`ColumnConfig`のコンストラクタ制約として実装されているにもかかわらずトレーサビリティに現れず、BR1.13は(R-03のとおり)不完全な実装であるにもかかわらず追跡対象から外れているため、レビュー・監査上の可視性が失われている。ステージ定義(code-generation.md Step 5)は「Enumerate every assigned AC, detailed NFRx.y, and BRx.y」を明記しており、この欠落はステージ契約違反である。 | `traceability.json`にBR1.13・BR1.14を追加し、それぞれの実装対象ファイル(またはGAPステータスと理由)を記載する。 | New |
| R-05 | Major | aidlc/spaces/default/intents/260910-master-mgmt-app/inception/contract-design/contract-summary.md > C9 (config-engine 内部インタフェース契約) types | C9契約の`TableConfig`/`ColumnConfig`型定義は`displayName: string`フィールドを含むが、`entities.md`(本ユニットの機能設計、一次情報源)は「displayNameフィールドは廃止し、i18nキーの機械的導出に置き換えた」と明記しており、実装(`TableConfig.java`/`ColumnConfig.java`)にも`displayName`フィールドは存在しない(`labelI18nKey()`メソッドに置換済み)。また同契約の`ColumnConfig`型は`choiceOptions`/`fkReference`(BR1.4・entities.mdの中核属性)を含んでいない。この契約は`schema-introspector`・`list-engine`・`record-edit-engine`・`permission-engine`・`data-import-export`・`config-import-export`の6ユニットが参照するshared-schemaであり、契約の型形状と実装の乖離は、これらのコンシューマーユニットが存在しないフィールドを期待する実装をする、またはchoiceOptions/fkReferenceの参照方法が分からないままになるリスクを生む。 | `contract-summary.md` C9の`types`ブロックを`entities.md`の実際の属性一覧(displayName削除、choiceOptions/fkReference追加)に合わせて更新する追補を行う。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `./gradlew :backend:checkstyleMain :backend:checkstyleTest :backend:compileJava :backend:compileTestJava` | PASS(出力なし、エラーなし) | linter/type-checkセンサーの対象は問題なし |
| `./gradlew :backend:test --tests "com.mastersmith.config.*"` | PASS(出力なし、エラーなし) | 全テスト成功。`code-summary.md`が主張するテストクラス16・テストケース153件・行カバレッジ91.9%と矛盾する失敗は検出されず |
| required-sections (手動確認) | PASS | `code-generation-plan.md`(H2見出し17個)、`unit-test-instructions.md`(6個)、`code-summary.md`(5個)いずれも2個以上のH2見出しを持つ |
| traceability (手動確認) | FAIL相当 | R-04のとおりBR1.13・BR1.14が`upstream_ids`/`coverage`から欠落している |

### Summary

R-01・R-02(前回レビューの指摘)はいずれも本セッションで正しく是正されていることを確認した。しかし新たに、監査ログドメインイベント(ConfigChangedEvent)の形状と発行方式がBR1.13・entities.mdの明示的な要求(エンティティ単位発行、targetType/targetId/beforeValue/afterValue必須)に違反しており(R-03、Critical)、これがtraceability.jsonから隠れている(R-04)うえ、C9共有契約の型定義が本ユニットの実装・機能設計と食い違っている(R-05)。R-03はAuditLogging(U7)との契約全体に影響するブラストレディウスの大きい欠陥であり、実装の修正が必要。
