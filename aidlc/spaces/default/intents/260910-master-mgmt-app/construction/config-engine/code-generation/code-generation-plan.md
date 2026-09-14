# Code Generation Plan — config-engine (U1)

config-engineはプロジェクト最初のUnitであり、本Boltでバックエンドのプロジェクト基盤（Gradle・Spring Boot骨格）も併せて構築する。以降のUnit（U2〜U13）はこの基盤に追加していく。

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

- **API/endpoint層**: config-engineは現時点で外部公開REST APIを持たない（`contract-summary.md` C9: 内部Javaインタフェースのみ）。本Boltでは対象外とする。将来の管理用REST API（W6）はContract Design追補後の別Boltで扱う。
- **フロントエンド層**: config-engineはバックエンドサービスでありUIを持たない。対象外。
- **E2Eテスト**: 複数業務プロファイル横断E2Eテスト（`team.md`確定の必須テスト種別）は、list-engine・record-edit-engine・frontend-uiが未実装の現時点では実行不能なため、本Boltでは対象外とする。該当ユニット（list-engine, record-edit-engine, frontend-ui）が揃うBoltで実施する。config-engine単体では、設定ファイルの安全失敗/バリデーションテスト（(a)）はこの段階で実施可能なため含める。

## Story-to-Code Step Traceability

user-storiesステージはSKIP対象（`project.md`学習事項）のため、`requirements.md`のFR ID・`functional-design/rules.md`のBR ID・`nfr-design`のNFR ID単位でトレースする。

| FR/BR/NFR ID | 概要 | 対応Step |
|---|---|---|
| FR1.1, BR1.2, BR1.3 | TableConfig/ColumnConfig必須プロパティ | Step 3, 4 |
| FR1.2, BR1.12 | 複数RDBMS方言吸収（型名正規化） | Step 5 |
| FR1.3, BR1.1, BR1.11 | fail-fast検証・フィールド単位エラー | Step 6 |
| FR1.5, BR1.4 | 静的選択肢/FK参照の排他検証 | Step 6 |
| BR1.5, BR1.6 | i18nキー導出 | Step 4 |
| BR1.7 | 楽観ロック対象列（明示設定のみ） | Step 4 |
| BR1.8 | schema-introspectorドラフト取り込み時の非上書き | Step 7 |
| BR1.9 | validationRule構造化データ | Step 4 |
| BR1.10 | TranslationEntry（業務設定層i18n） | Step 8 |
| NFR1.1, NFR1.2 | 50ms以内p95、起動時全読み込みキャッシュ | Step 9 |
| NFR2.1〜NFR2.4 | セキュリティ（認可委譲・エラー安全化） | Step 6, 10 |
| NFR4.1, NFR4.2 | fail-fast起動中断・H2永続化 | Step 2, 6 |

## Step 1: プロジェクト構造・本番設定の骨格

- [x] Gradleマルチプロジェクト骨格を作成する: ルート`build.gradle.kts`・`settings.gradle.kts`（プロジェクト名`mastersmith`、単一の`backend`サブプロジェクトを想定した構成。将来U12のフロントエンド成果物を同梱する`packaging`ユニット（U13）が静的リソースとして取り込む前提のディレクトリ構成とする）
- [x] Java 25 + Spring Boot（最新の安定版、`plugins { id("org.springframework.boot") }`）+ Gradleの依存関係を`backend/build.gradle.kts`に定義する（`spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `com.h2database:h2`, `com.fasterxml.jackson.core:jackson-databind`）
- [x] パッケージ構成: `com.mastersmith.config`（`contract-summary.md` C9契約のpackage指定を踏襲）
- [x] Apache License 2.0ヘッダー（`project.md`規約、年2026・著作権者agwlvssainokuni）をすべての生成Javaファイル先頭に挿入する
- [x] `application.yml`骨格を作成する: 内部設定DB（H2、ファイルモード、業務データ用RDBMS接続とは別プロファイル）の接続設定を定義する。業務データ用RDBMS接続は本Boltでは未使用のためプレースホルダのみ

## Step 2: テストランナーの起動・実行コマンド確定

- [x] Gradle `test`タスクがJUnit 5（Spring Boot Test含む）で実行可能であることを確認する
- [x] `backend/src/test/java`配下にプレースホルダテストを1つ作成し、`./gradlew :backend:test --tests "com.mastersmith.config.*"`が実行可能であることを確認する（`unit-test-instructions.md`に正確なコマンドを記録）

## Step 3: データモデル層の実装（entities.md準拠）

- [x] `TableConfig`エンティティ（JPA `@Entity`）を実装する: `tableConfigId`（PK, UUID）, `schemaName`, `tableName`, `displayOrder`, `optimisticLockColumn`
- [x] `ColumnConfig`エンティティを実装する: `columnConfigId`（PK, UUID）, `tableConfigId`（FK）, `columnName`, `displayOrder`, `format`, `editorType`, `validationRule`（JSON型カラム）, `visibility`, `choiceOptions`（JSON型カラム）, `fkReference`（JSON型カラム）
- [x] `TranslationEntry`エンティティを実装する: 複合主キー`(i18nKey, locale)`, `text`
- [x] `EditorType`列挙型（text/textarea/integer/decimal/date/datetime/select/radio/switch/checkbox）を実装する
- [x] JSON型カラムのシリアライズ/デシリアライズ（`ValidationRule`, `ChoiceOption[]`, `FkReference`のJavaレコード/クラス + Jacksonコンバータ、Hibernateの`@Convert`または`@JdbcTypeCode(SqlTypes.JSON)`）を実装する

## Step 4: データモデル層のテスト（test-after）

- [x] `TableConfig`/`ColumnConfig`/`TranslationEntry`のJPAマッピングテスト（Spring Boot `@DataJpaTest`、組込みH2使用）: 保存・取得の往復、複合主キーの一意性制約、JSON型カラムのシリアライズ往復
- [x] `EditorType`列挙型の全値網羅テスト

## Step 5: リポジトリ／データアクセス層の実装（BR1.12の型正規化を含む）

- [x] `TableConfigRepository`（Spring Data JPA、`(schemaName, tableName)`によるfindメソッド）を実装する
- [x] `ColumnConfigRepository`（`tableConfigId`による一覧取得メソッド）を実装する
- [x] `TranslationEntryRepository`（`(i18nKey, locale)`によるfindメソッド）を実装する
- [x] `RdbmsTypeNormalizer`（BR1.12: PostgreSQL/MySQL/MariaDBのメタデータ型名をConfigEngine内部論理型へ正規化するコンポーネント）を実装する

## Step 6: リポジトリ層のテスト（test-after）

- [x] 各Repositoryの`@DataJpaTest`統合テスト（組込みH2）: 基本CRUD・検索メソッドの動作確認
- [x] `RdbmsTypeNormalizer`の単体テスト: PostgreSQL/MySQL/MariaDBそれぞれの代表的な型名（varchar, SERIAL/AUTO_INCREMENT等）が正しい論理型へ変換されることを確認する表駆動テスト

## Step 7: ビジネスロジック層の実装（ConfigValidator, rules.md BR1.1〜BR1.4準拠）

- [x] `ConfigValidator`を実装する: Jakarta Bean Validationアノテーション + カスタム`ConstraintValidator`でBR1.1（必須プロパティ）・BR1.2（TableConfig必須項目）・BR1.3（ColumnConfig必須項目）・BR1.4（choiceOptions/fkReference排他）を検証する
- [x] `ConfigValidationException`（フィールド名・ルール種別を含む構造化例外、スタックトレース非公開、`security-design.md`準拠）を実装する

## Step 8: ビジネスロジック層のテスト（fail-fast検証、安全失敗テスト）

- [x] `ConfigValidator`の単体テスト（テーブル駆動）: BR1.1〜BR1.4の各違反ケース（必須プロパティ欠落、choiceOptions/fkReference両方設定・両方未設定）を網羅する安全失敗/バリデーションテスト（`team.md`確定の必須テスト種別(a)）
- [x] `ConfigValidationException`がスタックトレースを含まずフィールド単位の情報のみを保持することを確認するテスト

## Step 9: ビジネスロジック層の実装（ConfigCache, ConfigModelStore, performance-design.md準拠）

- [x] `ConfigCache`を実装する: 起動時（`ApplicationRunner`）に全`TableConfig`/`ColumnConfig`/`TranslationEntry`を読み込み、不変スナップショット（`Map`）として保持する。書込み時は全体再構築・原子的差し替え
- [x] `ConfigModelStore`を実装する: `ConfigEngineApi`契約（C9: `getTableConfig`, `getColumnConfigs`, `getOptimisticLockColumn`, `writeTableConfigDraft`, `getExportableConfigSet`, `importConfigSet`）を実装し、読み取り系は`ConfigCache`へ委譲、書き込み系は`ConfigValidator`検証後に内部設定DBへ永続化＋`ConfigCache`再構築を行う
- [x] fail-fast起動処理: `ApplicationRunner`が検証エラー時に`ConfigValidationException`を伝播させ、Spring Bootコンテキスト起動を失敗させる（NFR4.1）
- [x] `writeTableConfigDraft`のBR1.8（既存設定がある場合はスキップ、上書きしない）を実装する

## Step 10: ビジネスロジック層のテスト（性能・起動時fail-fast）

- [x] `ConfigCache`の単体テスト: 起動時ロード、書込み後の全体差し替え、スナップショットの不変性（並行読み取り時に一貫したビューを返すこと）
- [x] `ConfigModelStore`の単体テスト: `getTableConfig`/`getColumnConfigs`の正常系・`TableConfigNotFoundException`系、`writeTableConfigDraft`のBR1.8スキップ挙動
- [x] Spring Boot統合テスト（`@SpringBootTest`）: 不正な設定データ（必須プロパティ欠落）を含むテスト用DBで起動し、アプリケーションコンテキスト起動が失敗することを確認する（NFR4.1のfail-fast、`team.md`必須テスト種別(a)の起動時版）
- [x] `getTableConfig`/`getColumnConfigs`呼び出しの実行時間が50ms未満（p95相当、JUnitでの実行時間アサーションによる簡易検証）であることを確認するマイクロベンチマーク的テスト（NFR1.1）

## Step 11: TranslationStoreの実装（BR1.10, i18n業務設定層）

- [x] `TranslationStore`を実装する: `TranslationEntry`の登録・更新（upsert）、`(i18nKey, locale)`単位の解決。`ConfigCache`のTranslationEntryスナップショットと連携する
- [x] i18nキー導出ユーティリティ（BR1.5, BR1.6: `table.{schemaName}.{tableName}.label`等の命名規則に従うキー生成）を実装する

## Step 12: TranslationStoreのテスト（test-after）

- [x] `TranslationStore`の単体テスト: upsert（新規登録・既存更新）、未登録キーの解決結果（null等の明確な未登録シグナル）
- [x] i18nキー導出ユーティリティの単体テスト: TableConfig/ColumnConfigの各パターン（label/validationメッセージ）でのキー生成を網羅

## Step 13: 環境・ビルド設定

- [x] Spotless（Java/Gradle、`team.md`確定）をGradleビルドへ導入し、コードフォーマットを統一する
- [x] Checkstyle（`team.md`確定）の基本ルールセットを導入する
- [x] `application.yml`の内部設定DB（H2ファイルモード）接続設定を本番相当（テスト時はインメモリH2に切り替え可能な設定）で完成させる

## Step 14: ドキュメント・トレーサビリティ

- [x] 各クラス・メソッドに必要最小限のJavadoc（非自明な設計判断のみ、実装が自明な内容は記述しない）を付与する
- [x] `code-summary.md`・`source-manifest.json`・`traceability.json`を作成する（Step 5〜6として本ステージ手順に規定済み、開発者エージェントは対象外。オーケストレーターが実施）
