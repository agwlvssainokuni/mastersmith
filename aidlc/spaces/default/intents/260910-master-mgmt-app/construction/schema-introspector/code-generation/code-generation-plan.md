# Code Generation Plan — schema-introspector (U2)

schema-introspectorは、対象RDBMS(業務データ用)のメタデータを読み取り、config-engine(U1)へ設定の初期ドラフトを書き込む一過性の読み取り処理である(`functional-spec.md` W1)。本Unitはconfig-engineと、認可判定のためpermission-engine(U3)に依存する。自身の永続エンティティは持たない。

## 前提事項: 認可基盤の拡張点定義(authentication-service未実装への対応)

C8契約(`POST /api/config/schema-introspection`)はBearer JWT認証を前提とするが、authentication-service(U5、Bolt 7相当)は本Bolt時点で未実装であり、コードベース全体にSpring Security/JWT関連の実装が存在しない。ユーザー確認の結果、以下の方針で進める:

- `ActiveRoleResolver`という小さな拡張点インタフェース(`resolveActiveRoleId(HttpServletRequest): String`)を定義し、現時点ではリクエストヘッダー(`X-Active-Role-Id`)から直接読み取る最小実装(`HeaderActiveRoleResolver`)を提供する。
- JWT検証そのもの(署名検証・有効期限確認等)は実装しない。認可判定(`PermissionEngineApi.canAccessScreen`)自体はサーバー側で必ず再検証する(`project.md` Mandated)ため、`## Mandated`が求める「実効権限の再検証」自体は満たされる。
- この制約は`code-summary.md`のAssumptions & Open Questionsに明記し、authentication-service(Bolt 7相当)の実装時に`ActiveRoleResolver`の実装をJWT検証ベースのものへ置き換えることを引き継ぎ事項として残す。

## 前提修正: PermissionEngineApiのconsumers Javadoc更新(NFR Designレビュー指摘R-02対応)

`inception/contract-design/contract-summary.md` C10契約のconsumers列挙にschema-introspectorが含まれておらず(NFR Design アーキテクチャレビューR-02で指摘)、`PermissionEngineApi.java`のJavadocも同様に未記載であることを確認した。設計文書(contract-summary.md)自体の修正はInception成果物の変更となるため本Boltの対象外とするが、実コードのJavadoc(`backend/src/main/java/com/mastersmith/permission/PermissionEngineApi.java`)は事実誤りの軽微な追補として本Boltで更新する。

- [ ] `PermissionEngineApi.java`のJavadoc consumers列挙に`schema-introspector`を追加する

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

## 対象外レイヤーの扱い

- **フロントエンド層**: schema-introspectorはバックエンドサービスでありUIを持たない。対象外。
- **複数プロファイル横断E2Eテスト**: `team.md`確定の必須テスト種別(b)は、list-engine・record-edit-engine・frontend-uiが未実装の現時点では実行不能なため、本Boltでは対象外とする(data-import-exportの前例を踏襲)。安全失敗/バリデーションテスト(a)・認可拒否専用テスト(c)はこの段階で単体・統合テストとして実施可能なため含める。
- **JWT署名検証**: 上記「前提事項」のとおり、authentication-service未実装のため本Boltでは実装しない。

## Story-to-Code Step Traceability

user-storiesステージはSKIP対象のため、`requirements.md`のFR ID・`functional-design/rules.md`のBR ID単位でトレースする。

| FR/BR ID | 概要 | 対応Step |
|---|---|---|
| FR1.4 | スキーマメタデータ読み取りとconfig-engineへの初期ドラフト書込み | Step 9, 10, 11, 12 |
| BR2.1 | 読み取り専用アクセスのみ | Step 9, 10 |
| BR2.2 | 読み取り範囲(tableNames省略時は全テーブル) | Step 3, 4, 9, 10 |
| BR2.3 | RDBMS方言判定 | Step 9, 10 |
| BR2.4 | 主キー判定 | Step 9, 10 |
| BR2.5 | editorType/format決定の委譲(rawTypeNameをそのまま渡す) | Step 9, 10 |
| BR2.6 | 楽観ロック対象列の非設定 | Step 9, 10 |
| BR2.7 | 再実行時スキップの委譲 | Step 9, 10 |
| BR2.8 | アクセス制御(canAccessScreen) | Step 5, 6, 11, 12 |
| BR2.9 | fail-fast(接続・読み取り失敗時422) | Step 9, 10, 11, 12 |
| BR2.10 | NULL可否の読み取りと非伝搬 | Step 3, 4, 9, 10 |
| NFR1.1/NFR4.1(タイムアウト) | 接続5秒・読み取り25秒 | Step 9 |
| NFR5.1(可観測性) | メトリクス・構造化ログ | Step 9, 11 |

## Step 1: プロジェクト構造(パッケージ作成)

- [ ] `backend/src/main/java/com/mastersmith/schema/`配下にパッケージ構造を作成する(`dto`, `security`, `rdbms`, `service`, `web`, `exception`のサブパッケージ)

## Step 2: テストランナー確認

- [ ] 既存のGradleテストタスク(`./gradlew :backend:test`)がschema-introspector配下の新規テストクラスを実行できることを確認する(config-engineで確立済みのテスト基盤を再利用、追加設定不要)

## Step 3: データモデル層の実装(DTO・値オブジェクト、entities.md準拠)

- [ ] `SchemaIntrospectionRequest`(record: schemaName, tableNames)を実装する(C8リクエストボディ)
- [ ] `SchemaIntrospectionResult`(record: generatedTableConfigIds)を実装する(C8レスポンスボディ)
- [ ] `RdbmsTableMetadata`(record: schemaName, tableName, columns)、`RdbmsColumnMetadata`(record: columnName, rawTypeName, isPrimaryKey, nullable)を実装する(内部読み取りモデル、entities.md準拠。nullableはBR2.10により保持するがConfigDraftEntryへは伝搬しない)

## Step 4: データモデル層のテスト(test-after)

- [ ] 各recordのコンパクトコンストラクタ・不変性の単体テスト(該当する場合)

## Step 5: 認可拡張点の実装(BR2.8、前提事項参照)

- [ ] `ActiveRoleResolver`インタフェース(`resolveActiveRoleId(HttpServletRequest): String`)を定義する
- [ ] `HeaderActiveRoleResolver`(最小実装、`X-Active-Role-Id`ヘッダーから読み取る。authentication-service実装時に置き換え予定であることをJavadocに明記)を実装する

## Step 6: 認可拡張点のテスト

- [ ] `HeaderActiveRoleResolverTest`: ヘッダーあり/なしのケース

## Step 7: メタデータ読み取り層の実装(BR2.1, BR2.3, BR2.4, BR2.10)

- [ ] `RdbmsMetadataReader`を実装する: `businessDataSource`(`@Qualifier("businessDataSource")`、data-import-exportと同じ`@ConditionalOnProperty`パターンを踏襲)から取得した`Connection`の`DatabaseMetaData`を用いて、対象スキーマ・テーブル(またはスキーマ配下の全テーブル)のカラム名・型名・主キー制約・NULL可否を読み取り`RdbmsTableMetadata`一覧を返す。RDBMS方言(`com.mastersmith.config.rdbms.RdbmsDialect`を再利用)は`DatabaseMetaData.getDatabaseProductName()`等から判定する。接続タイムアウト5秒・読み取りタイムアウト25秒を設定する(NFR4.1)

## Step 8: メタデータ読み取り層のテスト

- [ ] `RdbmsMetadataReaderTest`: H2インメモリDB上に作成したテストスキーマ(複数テーブル・主キー・NULL許容/非許容カラムを含む)に対する読み取り結果を検証する統合テスト。存在しないスキーマ指定時の挙動も確認する

## Step 9: ビジネスロジック層の実装(BR2.2, 2.5, 2.6, 2.7, 2.9)

- [ ] `SchemaIntrospectionService`を実装する: `introspect(SchemaIntrospectionRequest): SchemaIntrospectionResult`を提供し、`RdbmsMetadataReader`で読み取った`RdbmsTableMetadata`一覧をconfig-engineのC9契約型(`TableConfigDraft`/`TableDraftEntry`/`ColumnDraftEntry`)へ変換し、`ConfigEngineApi.writeTableConfigDraft`を1回呼び出す。読み取り失敗時は`SchemaIntrospectionException`(422)を送出し`writeTableConfigDraft`を呼び出さない(BR2.9)

## Step 10: ビジネスロジック層のテスト

- [ ] `SchemaIntrospectionServiceTest`: `RdbmsMetadataReader`・`ConfigEngineApi`をモックした正常系(TableConfigDraftへの変換内容、nullableが伝搬されないことの確認含む)、メタデータ読み取り失敗時に`writeTableConfigDraft`が一切呼び出されないことを確認するテスト(`team.md`確定の必須テスト種別(a)安全失敗/バリデーションテスト)

## Step 11: RESTコントローラ層の実装(C8、BR2.8、BR2.9)

- [ ] `SchemaIntrospectionController`を実装する: `POST /api/config/schema-introspection`で`ActiveRoleResolver`からactiveRoleIdを取得し`PermissionEngineApi.canAccessScreen(activeRoleId, "config-import-export")`を呼び出す。拒否時は403(ProblemDetail)を返す。許可時は`SchemaIntrospectionService.introspect`を呼び出し結果を200で返す
- [ ] `@ExceptionHandler`(コントローラローカル、C8契約準拠のRFC 9457 `ProblemDetail`)で`SchemaIntrospectionException`を422へマッピングする。パスワード等の接続情報の詳細はレスポンスに含めない(NFR2.2)
- [ ] observability-design.md準拠のメトリクス(Micrometer)・構造化ログ(実行開始・完了・失敗)を実装する

## Step 12: RESTコントローラ層のテスト

- [ ] `SchemaIntrospectionControllerTest`(`@WebMvcTest`または同等): 正常系(200、生成されたTableConfigIdの一覧)、権限拒否時403(`team.md`確定の必須テスト種別(c)認可拒否専用テスト)、メタデータ読み取り失敗時422(必須テスト種別(a))

## Step 13: 環境・ビルド設定

- [ ] 新規パッケージがconfig-engine/data-import-exportと同一のGradle/Spotless/Checkstyle設定下でビルド・整形されることを確認する(追加設定は不要)

## Step 14: ドキュメント・トレーサビリティ

- [ ] 各クラス・メソッドに必要最小限のJavadoc(非自明な設計判断のみ、`ActiveRoleResolver`の暫定実装である旨を含む)を付与する
- [ ] `code-summary.md`・`source-manifest.json`・`traceability.json`を作成する(オーケストレーターが実施)
