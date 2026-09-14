# Code Generation Plan — data-import-export (U8)

data-import-exportは業務データそのものを所有せず、CSVエクスポート/インポート処理そのものを担う。config-engine(U1)のみに依存し(`ConfigEngineApi`)、権限は呼び出し元(list-engine/record-edit-engine)を信頼するため再検証しない(BR8.8)。監査ログはSpringイベント発行による疎結合設計(config-engineの`ConfigChangedEvent`と同じパターン)のためAuditLogging(U7)の実体を必要としない。

## 前提修正: config-engine への isPrimaryKey 反映(Contract Design追補C9、レビュー指摘R-05対応)

本Unitのupsert判定(BR8.3)が必要とする`ColumnConfig.isPrimaryKey`は、Contract Design追補(Q8=A)によりconfig-engine(U1)のC9契約に追加されたが、config-engineの実コード(既にCode Generation完了済み)には未反映である。本Boltの前提修正として、config-engine側に最小限のin-place修正を加える(brownfield、既存ファイルへの追加)。

- [ ] `ColumnConfig`エンティティ(`backend/src/main/java/com/mastersmith/config/entity/ColumnConfig.java`)に`isPrimaryKey`(boolean)フィールドを追加する。既存の3引数コンストラクタ(`tableConfigId, columnName, editorType`、isPrimaryKey=falseで初期化)は既存呼び出し元(テスト等)との互換のため維持し、新規4引数コンストラクタ(`tableConfigId, columnName, editorType, isPrimaryKey`)を追加する。getterのみを公開し、setterは公開しない(BR1.14: writeTableConfigDraft経由の構築時にのみ設定され、他の経路(手動編集・importConfigSet)からは変更不可能とする設計をコンストラクタ限定で保証する)
- [ ] JPAマッピング: `column_config`テーブルへ`is_primary_key`カラム(boolean, not null, default false)を追加する(H2自動DDL)
- [ ] `ColumnDraftEntry`(`backend/src/main/java/com/mastersmith/config/dto/ColumnDraftEntry.java`)に`isPrimaryKey`(boolean)フィールドを追加する(schema-introspectorが主キー判定結果を渡すためのDTO拡張)
- [ ] `ConfigModelStore.buildColumnConfigs`(`backend/src/main/java/com/mastersmith/config/store/ConfigModelStore.java`)で、新規4引数コンストラクタを用いて`columnDraft.isPrimaryKey()`を新規`ColumnConfig`へそのまま設定する(BR1.14)

### 前提修正のテスト(test-after)

- [ ] `ColumnConfigJpaTest`に`isPrimaryKey`の保存・取得往復ケースを追加する
- [ ] `ConfigModelStoreTest`に、`writeTableConfigDraft`が`ColumnDraftEntry.isPrimaryKey`を新規`ColumnConfig.isPrimaryKey`へ正しく伝播することを確認するケースを追加する

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

- **API/endpoint層**: data-import-exportは外部公開REST APIを持たない(`contract-summary.md` C13: 内部Javaインタフェースのみ)。list-engine/record-edit-engineの既存エンドポイント(C1/C2)への実装追加は、当該ユニット自身のBoltで扱う(本Boltの対象外)。
- **フロントエンド層**: data-import-exportはバックエンドサービスでありUIを持たない。対象外。
- **E2Eテスト**: 複数業務プロファイル横断E2Eテスト(`team.md`確定の必須テスト種別)は、list-engine・record-edit-engine・frontend-uiが未実装の現時点では実行不能なため、本Boltでは対象外とする。安全失敗/バリデーションテスト(BR8.5関連)はこの段階で単体テストとして実施可能なため含める。

## Story-to-Code Step Traceability

user-storiesステージはSKIP対象(`project.md`学習事項)のため、`requirements.md`のFR ID・`functional-design/rules.md`のBR ID単位でトレースする。

| FR/BR ID | 概要 | 対応Step |
|---|---|---|
| FR12.1, BR8.1 | CSV形式(UTF-8 BOM付き・カンマ区切り・ヘッダー行・CRLF) | Step 3, 4 |
| BR8.2, BR8.8 | エクスポート列絞り込み・権限は呼び出し元を信頼 | Step 5, 6 |
| BR8.10(エクスポート) | ストリーミングエクスポート | Step 5, 6 |
| BR8.3, BR8.4 | upsert判定・楽観ロック非適用 | Step 7, 8 |
| BR8.5, BR8.6 | 型変換・validationRule適用・行単位エラー | Step 7, 8 |
| BR8.7, BR8.10(インポート) | 全行検証後の一括コミット・ストリーミングインポート | Step 9, 10 |
| BR8.9 | 監査イベント(ImportExecutedEvent)発行 | Step 9, 10 |

## Step 1: プロジェクト構造(パッケージ作成)

- [ ] `backend/src/main/java/com/mastersmith/dataio/`配下にパッケージ構造を作成する(`dto`, `csv`, `service`のサブパッケージ)

## Step 2: テストランナー確認

- [ ] 既存のGradleテストタスク(`./gradlew :backend:test`)がdata-import-export配下の新規テストクラスを実行できることを確認する(config-engineで確立済みのテスト基盤を再利用、追加設定不要)

## Step 3: データモデル層の実装(DTO・値オブジェクト、entities.md準拠)

- [ ] `CsvExportRequest`(record: tableConfigId, filter, sort, permittedColumnNames)を実装する
- [ ] `CsvImportRequest`(record: tableConfigId, file, actor)を実装する
- [ ] `CsvColumnDefinition`(record: columnName, editorType, validationRule, visibility, isPrimaryKey)を実装する
- [ ] `CsvImportRowResult`(record: rowNumber, outcome, operation, errors)、`RowError`(record: field, message)を実装する
- [ ] `ImportResult`(record: successCount, errors)、`ImportExecutedEvent`(record: tableConfigId, actor, successCount, errorCount, committed, occurredAt)を実装する(`ConfigChangedEvent`と同様、Springの`ApplicationEventPublisher`で発行するイベント)

## Step 4: データモデル層のテスト(test-after)

- [ ] 各recordのコンパクトコンストラクタ・不変性の単体テスト(該当する場合)

## Step 5: ビジネスロジック層の実装(エクスポート、BR8.1・BR8.2・BR8.8・BR8.10)

- [ ] `CsvColumnDefinitionResolver`を実装する: `ConfigEngineApi.getColumnConfigs`から取得したColumnConfig一覧を、`visibility: hidden`列および`permittedColumnNames`に含まれない列を除外して`CsvColumnDefinition`一覧へ変換する(BR8.2)
- [ ] `CsvExportService`を実装する: `exportCsv(tableConfigId, filter, sort, permittedColumnNames)`(C13契約)を提供し、業務データ用RDBMSへJDBCカーソル経由で対象データを逐次読み取り、UTF-8 BOM付き・カンマ区切り・ヘッダー行・CRLF形式(BR8.1)で1行ずつ`OutputStream`(CSV)へ書き込む。全件を一括でメモリへ読み込まない(BR8.10)

## Step 6: ビジネスロジック層のテスト(エクスポート)

- [ ] `CsvColumnDefinitionResolverTest`: hidden列・permittedColumnNames対象外列の除外、両方を満たす列のみ残ることを確認するテーブル駆動テスト
- [ ] `CsvExportServiceTest`: 正常系(生成されるCSVの形式・内容がBR8.1に従うこと)、空データ(ヘッダー行のみ)、対象テーブル不在時の`TableConfigNotFoundException`伝播

## Step 7: ビジネスロジック層の実装(インポート、BR8.3〜BR8.7・BR8.10)

- [ ] `CsvRowValidator`を実装する: `CsvColumnDefinition`に基づき、1行分の値を`editorType`に応じた型へ変換し(変換失敗は型変換エラー)、`validationRule`を適用する(BR8.5)。主キー列の値の有無からupsert種別(INSERT/UPDATE)を判定する(BR8.3、対象行が存在しない場合はエラー)
- [ ] `CsvImportService`を実装する: `importCsv(tableConfigId, file, actor)`(C13契約)を提供し、CSVファイルを1回のストリーミング走査で1行ずつ読み取りながら`CsvRowValidator`を適用し、検証済みの軽量な行データ(`CsvImportRowResult`)を一時バッファ(メモリ上のリスト)へ蓄積する(BR8.10)。全行の読み取り完了後、1件でも`outcome=INVALID`があれば何も反映せず`ImportResult`を返す。全行が有効な場合のみ、1つの`@Transactional`メソッド内でINSERT/UPDATEを実行し、楽観ロック競合検出は行わない(BR8.4、常に後勝ち)

## Step 8: ビジネスロジック層のテスト(インポート、安全失敗/バリデーションテスト含む)

- [ ] `CsvRowValidatorTest`(テーブル駆動): 型変換エラー、validationRule各種違反(required/minLength/maxLength/min/max/pattern)、主キー値ありでUPDATE対象行が存在しないケース、正常系のINSERT/UPDATE判定(`team.md`確定の必須テスト種別(a)安全失敗/バリデーションテスト)
- [ ] `CsvImportServiceTest`: 全行有効時の一括コミット、1件でもエラーがあれば全体ロールバック(成功見込みだった行も反映されないことを確認、BR8.7)、既存行への後勝ち上書き(楽観ロック対象列があってもバージョン競合を検出しないこと、BR8.4)

## Step 9: 監査ログ連携の実装(BR8.9)

- [ ] `CsvImportService`の処理完了時(コミット・ロールバックいずれも)に、`ImportExecutedEvent(tableConfigId, actor, successCount, errorCount, committed, occurredAt)`をSpringの`ApplicationEventPublisher`でfire-and-forget発行する

## Step 10: 監査ログ連携のテスト

- [ ] `CsvImportServiceTest`に、成功時・全体ロールバック時それぞれで`ImportExecutedEvent`が正しい内容(successCount/errorCount/committed)で1件発行されることを確認するケースを追加する(Springの`ApplicationEvents`アサーション機構を使用)

## Step 11: 環境・ビルド設定

- [ ] 新規パッケージがconfig-engineと同一のGradle/Spotless/Checkstyle設定下でビルド・整形されることを確認する(追加設定は不要)

## Step 12: ドキュメント・トレーサビリティ

- [ ] 各クラス・メソッドに必要最小限のJavadoc(非自明な設計判断のみ)を付与する
- [ ] `code-summary.md`・`source-manifest.json`・`traceability.json`を作成する(オーケストレーターが実施)
