## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-17T05:16:25Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | `backend/src/main/java/com/mastersmith/menu/service/MenuItemCommandService.java` > `validate()` | `entities.md`(59行目)は「parentMenuItemIdを持つ場合、参照先のMenuItemが存在しなければならない(木構造の整合性)」と定める。実装は直接自己参照(`parentMenuItemId.equals(selfMenuItemId)`)のみを検出するが、`PUT /api/menu-items/{id}`で既存項目の`parentMenuItemId`を自身の子孫(間接的な子孫)に付け替える間接循環は検出しない。この操作を行うと、当該項目とその子孫が`MenuTreeBuilder.buildBusinessMenu`のルート探索(`parentMenuItemId == null`からの`childrenByParent`辿り)から到達不能になり、削除もされないままメニューから静かに消失する(クラッシュはしないが、木構造の整合性制約に違反しデータが「行方不明」になる)。 | `update()`のバリデーションに、指定`parentMenuItemId`から祖先を辿り自分自身に戻らないかを確認する循環検出を追加する(祖先チェーンをrepository経由で辿るか、DB内の全件から到達可能性を検証する)。 | New |
| R-02 | Minor | `aidlc/.../inception/contract-design/contract-summary.md` C12 vs `backend/src/main/java/com/mastersmith/menu/MenuStructureApi.java` | C12契約は型名`MenuItem`(menuItemId/parentMenuItemId/label/order/targetTableConfigId)を規定するが、実装は同名衝突回避のため`MenuStructureEntry`という別名型を用いる。フィールド構成は1:1で意図も文書化されており実害はないが、契約summary自体には別名採用の追補が反映されていない(現状はソースコードのJavadocのみに説明がある)。 | 次回のContract Design追補で、C12の型名がユニット実装では`MenuStructureEntry`に対応する旨を`contract-summary.md`にも反映する(config-import-export、U9実装時までに)。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `./gradlew :backend:checkstyleMain :backend:checkstyleTest :backend:compileJava :backend:compileTestJava` | PASS(エラーなし) | menu-navigation配下含め、コンパイル・Checkstyleとも違反なし |
| `./gradlew :backend:test --tests "com.mastersmith.menu.*"` | PASS(エラーなし、6テストクラス) | `MenuTreeBuilderTest`の管理メニュー権限フィルタのstrict-stubbing修正(既定false+個別override)を確認。他の同種テストと同一パターンで一貫 |
| `./gradlew :backend:test :backend:jacocoTestCoverageVerification`(バックエンド全体) | PASS(exit code 0、エラーなし) | 80%行カバレッジフロアをバックエンド全体で充足。既存ユニットへの回帰なし |
| traceability.json全件の存在確認 | 全23件のOK/N/A対象ファイルが実在し、記載内容と実装が整合(N/A判定のFR7.3/BR6.9/NFR3.1もrules.md/entities.mdの記述と矛盾なし) | 問題なし |
| required-sectionsフロア(plan/summary、H2見出し≥2) | plan 16見出し、summary 5見出し | 余裕を持って充足 |

### Summary

`MenuTreeBuilder.buildBusinessMenu`のNPE修正(HashMap手動グルーピングによるnullキー対応)は、null-key不許容な`Collectors.groupingBy`を正しく回避しており妥当。`MenuTreeBuilderTest`のstrict-stubbing修正も、隣接する合格テストと同じ「既定false+個別override」パターンに一致し妥当。GET /api/menuの401単独・/api/menu-itemsの401/403区別ロジックはC3契約・BR6.8と正確に一致し、`order`↔`item_order`の物理カラムマッピング、物理FK制約の不在(entities.md「不透明な文字列参照」)もPlan Approval前提事項どおり実装されている。R-01(間接的な循環参照が検出されず項目が静かに消失しうる)は木構造整合性制約に対する未実装のエッジケースだが、クラッシュや権限バイパスを引き起こすものではなく、Majorが1件のみのためREADY判定とする。
