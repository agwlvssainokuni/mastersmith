# Code Generation Plan: audit-log

## 対象Unit概要

audit-log(U8、domain-design AuditLogComponent対応)。設定変更・業務データ操作・アカウント操作の記録受付(イベント駆動)、条件絞り込み付きの参照、CSV/JSON形式のエクスポート、保持期間超過分の削除、保持日数の設定変更を提供するバックエンドUnit(kind: 未指定 = service、フロントエンドなし)。デプロイ形態はembedded(単一Spring Bootアプリケーション内のパッケージ)。

**本Unitはコード生成順序上、最初に着手されるUnit(DAGレベル0)である。** ワークスペースルートに既存のソースコードは存在しないため、本Unitのコード生成は同時にプロジェクト全体のGradle/Spring Bootスケルトンと、複数Unit共通のクロスカッティング基盤(JWT認証・RFC 7807エラーハンドリング・ページング規約)を立ち上げる。これらの共通基盤は`common`パッケージに配置し、後続Unit(permission・schema-ingestion・auth等)が同じ基盤をそのまま再利用する前提とする。将来Unitが個別の事情でこの基盤を拡張する場合は、加法的な変更(新しいクレーム抽出ロジックの追加等)として扱う。

## Testing Contract

```json
{
  "version": 1,
  "methodology": "custom",
  "source": "team",
  "ordering": "基本は「まず実装し、その後にそのレイヤーのテストを書く」(test-after)",
  "scope": "mastersmith-mvp",
  "test_strategy": "comprehensive",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: custom\n- **Ordering**: 基本は「まず実装し、その後にそのレイヤーのテストを書く」(test-after)\n  の順序とするが、スキーマ読み込み層(JDBC `DatabaseMetaData` によるPostgreSQL/MySQL/\n  MariaDBの3種読み込み)に限っては、実装に先立ってRDBMSごとの期待挙動(複合主キーの\n  構成、主キーなしテーブルの扱い、ビューの読み取り専用扱い)を特性テスト\n  (characterization test)として洗い出してから実装を固める、前倒しの順序を採用する。\n- 上記の前倒し運用は品質担当の提案どおりに採用したものであり、テスト後書きの原則自体は\n  他のレイヤー(内部設定管理、REST API、React/TSXコンポーネント)には変更なく適用する。\n- 適用されるスコープ(mvp/feature等)に応じたカバレッジ床(80%ラインカバレッジ+CI実行)は、\n  スコープ定義ステージで確定するスコープに従う。スキーマ読み込み層については、カバレッジ\n  数値の達成そのものよりも、raid-log.mdで特定済みのリスク(複合主キー、主キーなしテーブル、\n  ビュー、RDBMS間の型差異)が個別のテストケースとして明示的にカバーされているかを優先する。\n- CI/CDプラットフォームそのものは未決定であり、テストの自動実行ゲート(push/PR時のlint+\n  単体テスト実行、失敗時のマージブロック)の具体的な組み込み方は、Construction段階の\n  CI Pipelineステージで確定する。"
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
      "Custom ordering - 基本は「まず実装し、その後にそのレイヤーのテストを書く」(test-after)",
      "Implementation and tests - preserve that exact ordering; do not convert it to layer-local TDD.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:3a59dea9b4bf667397d86c470ef3b2ad16310d7273ceb103e89e474b77acfc73",
  "contract_sha256": "sha256:66eafecee5e72eb5999a7af67d20ebb1ff4af7755156e150f1f3a30562d83e16"
}
```

frontend behaviorレイヤーは本Unitに存在しない(バックエンドのみのUnit)ため省略する。

## 実装計画(ステップ)

### Step 1: プロジェクト構造とビルド設定スケルトン ☐

- [ ] Gradleプロジェクトの初期化(`settings.gradle.kts`、ルート`build.gradle.kts`)。Java 25 toolchain、Spring Boot(実装時点でJava 25と互換の最新安定版。バージョン固定はcode-generation実施時に確定)、Spring Data JPA、Spring Security、H2 Database、Spring Boot Actuator、Micrometer、springdoc-openapi(任意)を依存関係に追加。
- [ ] パッケージ構成: `com.mastersmith.common`(全Unit共通のクロスカッティング基盤)、`com.mastersmith.auditlog`(本Unit固有)。
- [ ] `com.mastersmith.common.security`: JWTからisAdmin/roles/subクレームを抽出しSpring Security認証コンテキストへ設定する`JwtAuthenticationFilter`と、これを組み込む`SecurityConfig`(署名鍵はapplication.yml経由で環境変数`JWT_SIGNING_KEY`から読み込み、HS256検証)。認可判定(isAdminクレーム必須)はコントローラ層で`@PreAuthorize`または同等の仕組みにより行う。
- [ ] `com.mastersmith.common.web`: RFC 7807 `ProblemDetail`形式のエラー応答を返す`@ControllerAdvice`(グローバル例外ハンドラ)。
- [ ] `com.mastersmith.common.config`: 内部H2データストア接続設定(ファイルベースH2、テーブル名`ms_`接頭辞は`@Table(name = "ms_...")`で個別付与)。
- [ ] `application.yml`のベース(プロファイル機構: `application.yml`共通 + `application-dev.yml`)。
- **トレーサビリティ**: NFR4(単一WARパッケージング前提のプロジェクト構造)、NFR5(`ms_`接頭辞)、project.md Mandated(JDBCドライバ内包、内部データはH2のみ)、audit-log security-design.md(認可アーキテクチャの共通化)。

### Step 2: テストランナーのブートストラップ ☐

- [ ] Gradleの`test`タスクにJUnit 5(JUnit Platform)を設定。Spring Boot Test(`@SpringBootTest`、`@DataJpaTest`、`@WebMvcTest`)、AssertJ、Mockito、H2組み込みDB(テスト用インメモリモード)を依存関係に追加。
- [ ] 本Unitのみを対象とするテスト実行コマンドを確定し、`unit-test-instructions.md`に記録する(`./gradlew test --tests "com.mastersmith.auditlog.*"`)。
- [ ] 最初のテスト作成に先立ち、上記コマンドが実行可能であることを確認する(空のプレースホルダテストで検証)。
- **トレーサビリティ**: Testing Contract runner_step。

### Step 3: データモデル層の実装(BR1.1, BR6.1) ☐

- [ ] `AuditLogEntry` JPAエンティティ(`entryId`主キー、`occurredAt`、`actorAccountId`〈nullable、外部キー制約なし〉、`actionType`、`targetDescription`)。更新操作を持たない設計(BR6.1、Repositoryに`save`〈INSERT専用〉と削除系メソッドのみ公開、更新用メソッドは提供しない)。
- [ ] `AuditLogSettings` JPAエンティティ(`settingsId`固定値主キー、`retentionDays`既定365・最小1)。シングルトン制約(`settingsId`への一意制約)。
- [ ] `occurredAt`・`actorAccountId`・`actionType`への単一インデックス(performance-design.md)。
- [ ] Flyway等のマイグレーションツールは導入せず、Spring Data JPAの`ddl-auto`(開発時)またはスキーマ初期化スクリプト(`schema.sql`)でテーブル作成し、`AuditLogSettings`初期行(retentionDays=365)を`data.sql`で投入する。
- **トレーサビリティ**: entities.md AuditLogEntry/AuditLogSettings、BR1.1、BR6.1、NFR1.2(インデックス)。

### Step 4: データモデル層のテスト作成 ☐

- [ ] `AuditLogEntry`・`AuditLogSettings`エンティティのJPAマッピングテスト(`@DataJpaTest`、`TestEntityManager`によるpersist/find検証)。シングルトン一意制約の違反テストを含む。
- **トレーサビリティ**: Testing Contract testable_layers「Data model / database behavior」。

### Step 5: リポジトリ/データアクセス層の実装(BR2.1, BR2.2) ☐

- [ ] `AuditLogEntryRepository`(Spring Data JPA): `actionType`・`actorAccountId`・`occurredAt`範囲・`targetDescription`部分一致(LIKE)の組み合わせ検索を`Specification`またはカスタムクエリメソッドで実装(BR2.1・BR2.2、AND条件)。ページング対応。
- [ ] `AuditLogEntryRepository`: `occurredAt`が指定日時より前の全件削除メソッド(BR4.1)。
- [ ] `AuditLogSettingsRepository`(Spring Data JPA): 唯一のシングルトン行を取得・更新する専用メソッド。
- **トレーサビリティ**: rules.md BR2.1、BR2.2、BR4.1。

### Step 6: リポジトリ層のテスト作成 ☐

- [ ] `AuditLogEntryRepository`の各絞り込み条件(actionType単独・actorAccountId単独・occurredAt範囲単独・Search文字列単独・複数条件のAND組み合わせ)、削除メソッド(境界値: 削除基準日時ちょうど、その前後)のテスト(`@DataJpaTest`)。
- **トレーサビリティ**: Testing Contract testable_layers「Repository / data access」。

### Step 7: ビジネスロジック層(サービス)の実装(BR1.1, BR1.2, BR3.1, BR4.1〜BR4.3) ☐

- [ ] `AuditableActionOccurredEvent`(共通イベントクラス、`common`パッケージ。occurredAt/actorAccountId/actionType/targetDescription、契約#5〜#8のenvelope形状)。後続Unit(config-management・dynamic-data-access・auth・account-management)はこのイベントクラスを再利用してpublishする。
- [ ] `AuditLogEventListener`(`@EventListener`、記録受付。try-catchで例外を自己完結捕捉しERRORレベル構造化ログ出力、発行元へは伝播しない。BR1.1、BR1.2)。
- [ ] `AuditLogService`: 絞り込み検索(BR2.1・BR2.2をRepositoryへ委譲)、CSV/JSON形式エクスポート生成(BR3.1、`format`パラメータに応じたシリアライズ)、保持期間超過分の削除(`現在日時 - olderThanDays日`の削除基準日時算出、BR4.1)、`olderThanDays`/`retentionDays`のバリデーション(1以上の整数、BR4.2・BR4.3)、設定更新。
- **トレーサビリティ**: rules.md BR1.1、BR1.2、BR3.1、BR4.1、BR4.2、BR4.3。functional-spec.mdワークフロー1・3・4・5。

### Step 8: ビジネスロジック層のテスト作成 ☐

- [ ] `AuditLogEventListener`: 正常記録、記録処理中の例外捕捉(発行元への非伝播、ERRORログ出力)のテスト。
- [ ] `AuditLogService`: CSV/JSON各形式のエクスポート内容検証、`olderThanDays`/`retentionDays`の境界値検証(0・負値・非整数での拒否、1以上での受理)、削除基準日時算出の正確性(モック時刻使用)のテスト。
- **トレーサビリティ**: Testing Contract testable_layers「Business logic」。

### Step 9: API/エンドポイント層の実装(BR5.1) ☐

- [ ] `AuditLogController`: `GET /api/admin/audit-log`(参照・絞り込み、page/size/sort + actionType/actorAccountId/occurredAtFrom/occurredAtTo/q)、`DELETE /api/admin/audit-log`(`olderThanDays`必須クエリパラメータ)、`GET /api/admin/audit-log/export`(`format`必須)、`GET`・`PUT /api/admin/audit-log/settings`。
- [ ] 全エンドポイントにisAdminクレーム検証を適用(BR5.1、`common.security`基盤経由)。
- [ ] Bean Validationによる`format`(enum csv/json)・`olderThanDays`/`retentionDays`(`@Min(1)`)の入力検証、違反時400(RFC 7807)。
- **トレーサビリティ**: contract-summary.md audit-log(#18)、rules.md BR5.1、BR3.1、BR4.2、BR4.3。

### Step 10: API/エンドポイント層のテスト作成 ☐

- [ ] `AuditLogController`: 各エンドポイントの正常系(`@WebMvcTest`+MockMvc)、isAdminクレーム欠如時の403(BR5.1、セキュリティテスト)、`format`不正値の400、`olderThanDays`/`retentionDays`不正値の400のテスト。
- **トレーサビリティ**: Testing Contract testable_layers「API / endpoint」、strategy_volume「Add ... security tests when NFRs demand them」(NFR-AUTHZ.1)。

### Step 11: 統合テスト ☐

- [ ] `@SpringBootTest`(組み込みH2、実際のSpring Contextを起動)によるend-to-endの記録受付→参照→エクスポート→削除の一連の流れの検証。イベント発行から実際のDB永続化・REST API経由での取得までを通しで確認する。
- **トレーサビリティ**: Testing Contract strategy_volume「Unit, integration, and E2E tests」。

### Step 12: E2Eテスト ☐

- [ ] MockMvc + 実DB(組み込みH2)を用いた、認証込みの一連のシナリオ(有効なisAdminトークンでのログイン相当の前提→複数件記録→絞り込み参照→CSVエクスポート→保持期間超過削除→削除後の再参照で件数減少を確認)のE2Eテスト。
- **トレーサビリティ**: functional-spec.mdワークフロー1〜5全体。

### Step 13: 環境・ビルド設定 ☐

- [ ] `application.yml`にH2データストア接続設定、JWT署名鍵の環境変数プレースホルダ(`${JWT_SIGNING_KEY}`)、ログ設定(構造化JSON出力、logbackまたはlog4j2設定)を記述。秘密情報の実値はコミットしない(project.md Forbidden)。
- [ ] Actuator/Micrometerの標準メトリクス・ヘルスチェックエンドポイントの有効化確認。
- **トレーサビリティ**: nfr-design/observability-design.md、project.md Forbidden。

### Step 14: ドキュメント・トレーサビリティ ☐

- [ ] 主要クラス(Controller/Service/Repository/Entity)へのJavadocコメント(責務・BR ID参照)。
- [ ] `code-summary.md`・`source-manifest.json`・`traceability.json`の作成(Step 5・6完了後、本ステージ完了処理として実施)。
- **トレーサビリティ**: 全BR・NFRの網羅確認。

## 備考

- 本Unitのfunctional-spec.mdレビューに記録済みの既知の繰延べ事項(R-09: クエリパラメータの明記不足〈Contract Design時点で解消済み〉、R-10: actionType allowed_valuesの未反映〈entities.mdで解消済み〉、R-11: traceabilityのBR4.3 orphan)は、本コード生成の対象外(設計文書上の記録事項であり、実装そのものには影響しない)。
- nfr-design/security-design.mdの既知の繰延べ事項はなし(audit-log単体はREADY・指摘なし)。
- `common`パッケージの JWT認証基盤・エラーハンドリングは、後続Unit(permission・schema-ingestion等)が同じ実装をそのまま再利用する前提。後続Unitのcode-generation-planでこの基盤への追加要件が生じた場合は、加法的な拡張として扱う。
