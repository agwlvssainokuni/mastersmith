# Code Generation Plan — permission-engine (U3)

## Testing Contract

```json
{
  "version": 1,
  "methodology": "custom",
  "source": "team",
  "ordering": "原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを",
  "scope": "config-driven-admin-mvp",
  "test_strategy": "comprehensive",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: custom\n- **Ordering**: 原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを\n  作成・実行する(test-after)。ただし権限判定ロジック(ロール階層継承・主権限\n  FULL/READ/NONE/指定なし・補助権限CREATE/DELETEの解決処理)に限り、実装に先立って\n  権限マトリクス(組み合わせケース)を洗い出してから実装する、test-first/ATDD寄りの\n  例外的な進め方を認める(インタビューQ4: A. 全体はtest-afterを基本方針とする。\n  Q5: A. 権限判定ロジックだけこの例外を認める)。\n- テストはすべてのBoltにおいて第一級の成果物として扱う。\n- **カバレッジフロア**: スコープ `config-driven-admin-mvp`(`mvp` 系)の既定に従い、\n  80%行カバレッジとマージ前のCI実行を課す。\n- **権限・監査ログの追加合格条件**: 権限判定ロジックおよび監査ログ記録の実装に限り、\n  80%行カバレッジに加えて「主要な権限マトリクスの組み合わせを網羅するテーブル駆動\n  テスト」を追加の合格条件とする(80%行カバレッジという基準そのものを緩めるのではなく、\n  この2箇所にのみ上乗せする)(インタビューQ6: A)。\n- **Test Strategy「Comprehensive」の具体的内容**: 単体テストに加え、統合テスト・\n  画面操作を通したE2Eテスト・設定ファイルの契約テスト(config-schemaの形式チェック)を\n  標準として含む。負荷・性能テストは既定には含めない(インタビューQ7: A, B, C選択、\n  Dは対象外)。\n- **設定駆動(config-driven)特有の必須テスト種別**: 「アプリ本体を1つのまま設定の\n  入れ替えだけで複数業務に転用できる」という成功定義を検証するため、以下を必須とする\n  (インタビューQ8: A, B, C選択)。\n  - (a) 設定ファイルが不正・不完全なときにアプリが安全に失敗する(バリデーションエラー\n    になる)ことを確認する安全失敗/バリデーションテスト\n  - (b) 実際に2種類以上の異なる業務ドメインの設定プロファイル(例: 商品マスタ用・\n    蔵書マスタ用)で同一の操作シナリオを流し、「設定を差し替えるだけで動く」ことを\n    確認する複数プロファイル横断E2Eテスト\n  - (c) 権限が不足する操作は確実に拒否されることを狙って確認する認可拒否\n    (negative-authorization)専用テスト\n  - **監査ログ完全性テスト(更新・作成・削除の全操作が監査ログへ記録されることの網羅的\n    検証)は、今回のMVPスコープでは必須としない**。これはインタビューで2度確認した上での\n    意図的なスコープ判断であり(Q8選択肢Dは不採用)、見落としではない。\n- Build and Test ステージは、定義済みカバレッジフロアと確認済み品質目標の充足を検証する。\n  これらのステップを通過させるために基準を緩めることはしない。"
    }
  ],
  "obligations": {
    "strategy": "comprehensive",
    "strategy_volume": [
      "Ten to fifteen tests per component.",
      "Unit, integration, and E2E tests.",
      "Add performance and security tests when NFRs demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "custom",
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
      "Custom ordering - 原則として各テスト対象レイヤーを実装した後にそのレイヤーのテストを",
      "Implementation and tests - preserve that exact ordering; do not convert it to layer-local TDD.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:9ff5f4a3117c43915b0647dc8da168bba8fefba2f289aa7ad964e81afab834bc",
  "contract_sha256": "sha256:4fa8d24c65e3a6e1326c42865b60dbacdbf8f2481aebedce163e1f10630b41d8"
}
```

**適用**: 権限判定ロジック(BR3.4/BR3.5のスコープ階層解決アルゴリズム)は、実装に先立って権限マトリクス(組み合わせケース)を洗い出す(ATDD寄り)。それ以外のレイヤー(エンティティ、Repository、REST/内部API、キャッシュ)はtest-afterとする。

## 実装対象パッケージ

`com.mastersmith.permission`(新規パッケージ)。C10契約(`PermissionEngineApi`)の実装。

## Steps

- [x] Step 1: プロジェクト構造・エンティティ定義
  - `Role`, `PrimaryPermission`, `AuxiliaryPermission`, `Group`, `GroupMembership`, `GroupRole`のJPAエンティティを`com.mastersmith.permission.entity`に作成(entities.md準拠、Role.parentRoleId等の親子関係は持たない)
  - `ScopeType`列挙型(SCHEMA/TABLE/COLUMN)を追加
- [x] Step 2: 権限判定ロジックのテストケース洗い出し(ATDD、権限判定ロジックのみ例外的にtest-first)
  - BR3.4(主権限のスコープ階層解決)・BR3.5(補助権限の解決)・BR3.6(デフォルト値)の権限マトリクス組み合わせケースを洗い出し、`PermissionResolverTest`(テーブル駆動)の失敗するテストを先に書く
  - ケース例: COLUMN明示設定あり/TABLEのみ/SCHEMAのみ/すべて指定なし(NONE)、補助権限も同様(TABLE/SCHEMA/すべて指定なしDeny)
- [x] Step 3: 権限判定ロジックの実装(Green)
  - `PermissionResolver`(スコープ階層探索、BR3.4/BR3.5/BR3.6実装)をStep 2のテストが通るように実装
- [x] Step 4: Repository/データアクセス層
  - Spring Data JPAの`RoleRepository`, `PrimaryPermissionRepository`, `AuxiliaryPermissionRepository`, `GroupRepository`, `GroupMembershipRepository`, `GroupRoleRepository`
  - `(roleId, scopeType, scopeRef)`複合インデックス等をエンティティの`@Table`/`@Index`定義に反映(scalability-design.md準拠)
  - Repository層のテスト作成・実行
- [x] Step 5: キャッシュ層(Caffeine)
  - `build.gradle.kts`にCaffeine依存追加
  - `PermissionCacheConfig`(Caffeineキャッシュのbean定義、TTL・サイズ上限設定)
  - キャッシュ層のテスト作成・実行
- [x] Step 6: ブートストラップ判定・権限昇格チェックの実装
  - `BootstrapStateChecker`(PrimaryPermission行数=0判定、BR3.13)
  - `PermissionEscalationChecker`(BR3.8、操作者の実効権限との比較)
  - テスト作成・実行(昇格許可/拒否/ブートストラップ例外の各ケース)
- [x] Step 7: `PermissionEngineApi`実装(公開API層、C10契約)
  - `resolveEffectivePermission`, `canAccessScreen`, `assignPermission`の実装(W1/W2/W4のワークフローを反映)
  - `getGroupDerivedRoleIds(userId): List<String>`の追加実装(計画外・正当化された逸脱。functional-spec.md「Domain Design/Contract Designへの追補」で識別した契約ギャップに対応。C10の既存コンシューマーであるuser-managementが選択可能ロール一覧算出に利用する。config-engineの`getTableConfigById`追加と同種の、ユニット自身の契約への軽微な拡張)
  - `EffectivePermission`, `PermissionEscalationException`等のDTO/例外クラス
  - `activeRoleId`存在検証(BR実装、security-design.md準拠)
  - テスト作成・実行
- [x] Step 8: PermissionChangedイベント発行
  - `PermissionChangedEvent`(Springアプリケーションイベント、config-engineの`ConfigChangedEvent`と同じパターン)
  - `assignPermission`成功時のfire-and-forget発行(BR3.11、インポート実行単位のサマリではなく、本ユニット単体では各`assignPermission`呼び出しの粒度で発行し、実行単位への集約はconfig-import-export側の責務とする。C13(data-import-export)の`ImportExecutedEvent`集約パターンとは異なり、config-import-exportが複数の`assignPermission`呼び出し結果を集約してサマリイベントとして扱う設計とする)
  - テスト作成・実行
- [x] Step 9: メトリクス・ログ実装(observability-design.md準拠)
  - Micrometerカウンタ`permission_escalation_denied_total`、タイマー`permission_check_duration_seconds`
  - 構造化ログ出力
- [x] Step 10: 統合テスト
  - Spring Boot統合テスト(組込みH2、実際のDB状態を検証)
  - 認可拒否(negative-authorization)専用テスト(team.md Q8-c必須項目)
  - 設定ファイル不正時の安全失敗テスト(該当する場合)
- [x] Step 11: ドキュメント・トレーサビリティ
  - `code-summary.md`, `source-manifest.json`, `traceability.json`の作成

## 既知の未解決事項(実装時に踏襲、修正はスコープ外)

- **R-07(Critical、Functional Designレビューiteration上限で未解消)**: 複数エントリからなる初回RBACインポートで、1件目のコミット直後にブートストラップ状態が終了し2件目以降が再び昇格チェックにかかりうる。本Code Generationでは`reliability-design.md`/`security-design.md`の設計通り、現状の「upsert単位トランザクション」のまま実装する(次回Functional Design見直し時の課題として据え置く)。
- **R-04(Major、NFR Designレビュー、non-blocking suggestion)**: `invalidateAll()`が`assignPermission`の呼び出し頻度(バルクインポート時にエントリ数分)と組み合わさるとキャッシュが実質機能不全になる可能性。本Code Generationでは設計通り`invalidateAll()`を実装するが、この相互作用への追加対応(バッチ末尾でのみ無効化する等)は行わない(承認ゲートで人間に提示済み)。
