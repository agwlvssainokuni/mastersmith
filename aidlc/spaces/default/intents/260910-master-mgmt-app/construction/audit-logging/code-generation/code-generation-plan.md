# Code Generation Plan — audit-logging (U7)

audit-loggingは他ユニット(config-engine・permission-engine・data-import-export)が発行するドメインイベントを購読し、追記専用(append-only)の`AuditLogEntry`として内部設定DBへ記録する。閲覧API(`GET /api/audit-log`)は`PermissionEngineApi`(C10)への認可委譲のみを行い、自前の権限判定ロジックは持たない。

## 前提修正: Flywayの導入(プロジェクト全体、ユーザー承認済み)

`nfr-design/performance-design.md`(Q2=A、スキーマ移行ツールによる明示的インデックス定義)と、既存4ユニット(config-engine・permission-engine・data-import-export・schema-introspector)が前提とするHibernate `ddl-auto: update`(マイグレーションツール未導入、`application.yml`のコメント参照)との間に矛盾が判明した。ユーザー確認の結果、audit-loggingを機に**プロジェクト全体でFlywayを新規導入し、実装済み4ユニットの既存スキーマ(9エンティティ)についてもベースラインマイグレーションを作成する**方針とする(意図的な前提修正、data-import-exportがconfig-engineへ行った前提修正と同種のbrownfield対応)。

- [x] `backend/build.gradle.kts`へ`org.flywaydb:flyway-core`を追加する(`flyway-database-h2`はMaven Central上に存在しないことを確認し不採用。代わりにSpring Boot 4.xのモジュール分割オートコンフィグレーション本体である`org.springframework.boot:spring-boot-flyway`を追加した。計画時点の想定からの正当化された逸脱、詳細はCode Generation報告のIssues/Concerns参照)
- [x] `backend/src/main/resources/application.yml`: `spring.jpa.hibernate.ddl-auto`を`update`から`validate`へ変更する(Flywayが作成したスキーマとJPAエンティティのマッピング一致を起動時に検証するのみとし、スキーマの自動生成・変更は行わない)。マイグレーションツール未導入を説明していたコメントを、Flyway導入を反映した内容へ更新する
- [x] `backend/src/test/resources/application.yml`: `ddl-auto: create-drop`を`validate`へ変更し、テスト用インメモリH2(`jdbc:h2:mem:mastersmith-config-test-${random.uuid}`)でも`@SpringBootTest`起動時にFlywayマイグレーションが自動実行されるようにする(`spring.flyway.enabled`の既定値`true`をそのまま利用、追加設定不要)
- [x] `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql`を新規作成する。既存9エンティティ(下表)が現在Hibernate `ddl-auto: update`で自動生成しているテーブル定義を、各エンティティの`@Entity`/`@Table`/`@Column`/`@Id`/`@Embeddable`/`@UniqueConstraint`/`@Index`アノテーションを正本として過不足なく再現する。カラム型はH2方言(文字列は`VARCHAR`、UUID文字列は`VARCHAR(36)`、`JdbcTypeCode(SqlTypes.JSON)`は`JSON`型)とする。既存の`ddl-auto: update`が生成した実スキーマとの整合を優先し、推測でカラムを追加・削除しない

  | # | エンティティ | テーブル名 | 所属パッケージ |
  |---|---|---|---|
  | 1 | TableConfig | table_config | com.mastersmith.config.entity |
  | 2 | ColumnConfig | column_config | com.mastersmith.config.entity |
  | 3 | TranslationEntry(+TranslationEntryId) | translation_entry | com.mastersmith.config.entity |
  | 4 | Role | role | com.mastersmith.permission.entity |
  | 5 | Group | permission_group | com.mastersmith.permission.entity |
  | 6 | GroupRole(+GroupRoleId) | group_role | com.mastersmith.permission.entity |
  | 7 | GroupMembership(+GroupMembershipId) | group_membership | com.mastersmith.permission.entity |
  | 8 | PrimaryPermission | primary_permission | com.mastersmith.permission.entity |
  | 9 | AuxiliaryPermission | auxiliary_permission | com.mastersmith.permission.entity |

- [x] 開発環境のファイルモードH2データ(`./data/mastersmith-config*`)は本Boltの対象外とする(本プロジェクトはgreenfieldでありproduction相当のデータは存在しないため、既存ファイルの破棄・再作成は開発者の判断に委ねる。マイグレーション自体は空DBからの初回適用を正とする)
- [x] 前提修正の回帰確認: 既存4ユニットの全テスト(`config`・`schema`・`permission`・`dataio`パッケージ)が、`ddl-auto: validate` + Flywayベースラインマイグレーション後も無修正でグリーンのまま通ることを確認した(317件全green)

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

- **フロントエンド層**: audit-loggingはバックエンドサービスでありUIを持たない。監査ログ閲覧画面(frontend-ui、U12)からの呼び出しは対象外。
- **E2Eテスト**: 複数業務プロファイル横断E2Eテスト(`team.md`確定の必須テスト種別)は、list-engine・record-edit-engine・frontend-uiが未実装の現時点では実行不能なため、本Boltでは対象外とする。認可拒否(negative-authorization)専用テスト(`team.md`必須テスト種別(c))はコントローラ単体テストとして実施可能なため含める(Step 8)。

## Story-to-Code Step Traceability

user-storiesステージはSKIP対象(`project.md`学習事項)のため、`requirements.md`のFR ID・`functional-design/rules.md`のBR ID・`nfr-design`のNFR ID単位でトレースする。

| FR/BR/NFR ID | 概要 | 対応Step |
|---|---|---|
| FR8.1, BR7.1〜BR7.4 | ドメインイベント購読とAuditLogEntryへのマッピング | Step 5, 6 |
| FR8.2, BR7.5, NFR2.2, NFR4.1 | 追記専用リポジトリ(UPDATE/DELETE経路なし) | Step 3, 4 |
| FR8.3, NFR4.3 | 無期限保持(削除機能を実装しない) | Step 3 |
| FR8.4, NFR1.1, NFR1.2 | 閲覧API・インデックス設計 | Step 3(migration), Step 7, 8 |
| NFR2.1, BR7.10 | 認可委譲(canAccessScreen) | Step 7, 8 |
| NFR2.5, Q4 | クエリパラメータ入力検証(400) | Step 7, 8 |
| NFR4.2, NFR4.5, Q1(nfr-design) | 例外遮断(同期try-catch、発行元への非伝播) | Step 5, 6 |
| NFR4.4 | 内部設定DB接続断時の503 | Step 7, 8 |
| NFR5.1, NFR5.2 | メトリクス・構造化ログ | Step 9, 10 |

## Step 1: プロジェクト構造(パッケージ作成)

- [x] `backend/src/main/java/com/mastersmith/audit/`配下にパッケージ構造を作成する(`entity`, `repository`, `event`, `web`のサブパッケージ。加えて`exception`サブパッケージを既存ユニットの慣例に合わせて追加した)

## Step 2: テストランナー確認

- [x] 既存のGradleテストタスク(`./gradlew :backend:test`)がaudit-logging配下の新規テストクラスを実行できることを確認する(前提修正のFlyway導入後も、config-engineで確立済みのテスト基盤・`@SpringBootTest`起動フローが維持されることを含めて確認する)

## Step 3: データモデル層の実装(エンティティ・マイグレーション、entities.md準拠)

- [x] `backend/src/main/resources/db/migration/V2__create_audit_log_entry.sql`を作成する。`audit_log_entry`テーブル(`audit_log_entry_id` PK VARCHAR(36)、`actor_user_id` VARCHAR nullable、`actor_raw` VARCHAR nullable、`target_type` VARCHAR NOT NULL、`target_id` VARCHAR NOT NULL、`operation_type` VARCHAR NOT NULL、`occurred_at` TIMESTAMP NOT NULL、`before_value` JSON nullable、`after_value` JSON nullable)と、`performance-design.md`のインデックス設計(`idx_audit_log_entry_occurred_at`: `occurred_at DESC`、`idx_audit_log_entry_target_type_occurred_at`: `target_type, occurred_at DESC`)を定義する
- [x] `AuditLogEntry`エンティティ(`com.mastersmith.audit.entity`)を実装する。JPA永続化のみを担い、UPDATE/DELETEに相当する操作用のsetterは公開しない(entities.md準拠、`beforeValue`/`afterValue`は`JdbcTypeCode(SqlTypes.JSON)`型、常にnull)

## Step 4: データモデル層のテスト(test-after)

- [x] `AuditLogEntryJpaTest`(`@DataJpaTest`相当、既存パターンに合わせて`@SpringBootTest`+テスト用DB): INSERT後の読み取り往復、`beforeValue`/`afterValue`がnullで永続化されることを確認する

## Step 5: ビジネスロジック層の実装(イベント購読・マッピング、BR7.1〜BR7.4・NFR4.2・NFR4.5)

- [x] `AuditLogEventMapper`(`com.mastersmith.audit.event`)を実装する: `fromConfigChangedEvent`(BR7.2)・`fromPermissionChangedEvent`(BR7.3)・`fromImportExecutedEvent`(BR7.4)の3つの変換メソッドを提供する
- [x] `AuditLogEntryRepository`(`com.mastersmith.audit.repository`)を実装する。Spring Dataの`Repository<AuditLogEntry, String>`(メソッドを持たないマーカーインタフェース)を継承し、`save`・`findByTargetType(String, Pageable)`・`findAll(Pageable)`のみを宣言する。`CrudRepository`/`JpaRepository`は継承しない(UPDATE/DELETE相当のメソッドを一切持ち込まないため、security-design.md準拠)
- [x] `ConfigChangedEventListener`・`PermissionChangedEventListener`・`ImportExecutedEventListener`(`com.mastersmith.audit.event`)をそれぞれ実装する。同期の`@EventListener`とし、マッピング〜`AuditLogEntryRepository.save`までを`try-catch (Exception e)`で囲み、例外を発行元へ伝播させない(捕捉した例外はERRORレベルの構造化ログへ記録、reliability-design.md Q1確定)

## Step 6: ビジネスロジック層のテスト(イベント購読・マッピング)

- [x] `AuditLogEventMapperTest`(テーブル駆動): 3種類のイベントそれぞれについて、`functional-design/rules.md` BR7.2〜BR7.4のマッピング規則通りに`AuditLogEntry`が生成されることを確認する
- [x] `ConfigChangedEventListenerTest`・`PermissionChangedEventListenerTest`・`ImportExecutedEventListenerTest`: 正常系(リポジトリへの保存呼び出し)、リポジトリが例外を送出した場合でもリスナーメソッドが例外を再送出しないこと(発行元への非伝播、NFR4.2・NFR4.5の直接的な検証)

## Step 7: API層の実装(閲覧API、C6契約・BR7.9・BR7.10・NFR2.1・NFR2.5)

- [x] `AuditLogController`(`com.mastersmith.audit.web`)を実装する。`GET /api/audit-log`で`page`(既定1)・`pageSize`(既定20、1〜100)・`targetType`(任意、`ConfigEngine`/`PermissionEngine`/`DataImportExport`のいずれか)を受け取る
  - クエリパラメータが範囲外・不正な場合は400 Bad Request(RFC 9457 `ProblemDetail`、security-design.md Q4)を返す
  - `com.mastersmith.schema.security.ActiveRoleResolver`(既存の暫定拡張点、schema-introspectorと共有)でactiveRoleIdを解決し、`PermissionEngineApi.canAccessScreen(activeRoleId, "audit-log")`へ委譲する(BR7.10)。拒否時は403を返す
  - `targetType`指定時は`findByTargetType`、未指定時は`findAll`を`occurredAt`降順・`page`/`pageSize`で呼び出し、C6契約のレスポンス形状(`items`, `totalCount`)で返す
  - 内部設定DBが利用不可の場合(`DataAccessException`等)は503 Service Unavailable(RFC 9457、NFR4.4、C6契約追補)を返す

## Step 8: API層のテスト(認可拒否専用テスト含む)

- [x] `AuditLogControllerTest`(`@WebMvcTest`または既存パターンに合わせた統合テスト): 正常系(200、ページング・`targetType`フィルタ)、クエリパラメータ検証エラー(400、テーブル駆動でpage/pageSize/targetTypeの境界値を網羅)、**認可拒否専用テスト**(403、`canAccessScreen`がfalseを返すケース。`team.md`必須テスト種別(c))、内部設定DB利用不可時(503)

## Step 9: 可観測性の実装(NFR5.1・NFR5.2)

- [x] `AuditLogController`にMicrometer計装(`audit_logging.get_audit_log.duration`・`audit_logging.get_audit_log.error_count`)を追加する(他ユニットと同様のパターン)
- [x] 各EventListenerの`try-catch`ブロックにERRORレベルの構造化ログ出力(target・actor・occurredAt等を含む、機微情報を含まないことは`security-design.md` NFR2.3で確認済み)を実装する

## Step 10: 可観測性のテスト

- [x] メトリクス・ログ出力の直接テストは行わない(Micrometerの`Counter`/`Timer`登録自体はSpring Bootの自動構成に委ね、既存ユニットと同様、専用の単体テストは設けない方針を踏襲)

## Step 11: 環境・ビルド設定

- [x] 新規パッケージ・マイグレーションファイルが既存のGradle/Spotless/Checkstyle設定下でビルド・整形されることを確認する(spotlessCheck・checkstyleMain・checkstyleTestすべて本ユニットのファイルに関して合格。既存4ファイルに前から存在するgoogle-java-format整形差分は本ユニットの変更と無関係のため対象外、Issues/Concerns参照)

## Step 12: ドキュメント・トレーサビリティ

- [x] 各クラス・メソッドに必要最小限のJavadoc(非自明な設計判断のみ)を付与する
- [x] `code-summary.md`・`traceability.json`はオーケストレーターが実施。`source-manifest.json`はdispatch指示により本エージェントが作成した
