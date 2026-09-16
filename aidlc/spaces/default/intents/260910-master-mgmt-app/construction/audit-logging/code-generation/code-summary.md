<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Code Summary — audit-logging (U7)

承認済み`code-generation-plan.md`(前提修正のFlyway導入 + 全12 Step)に基づき、audit-loggingユニットを実装した。

## 前提修正: Flywayの導入(プロジェクト全体)

- `backend/build.gradle.kts`: `org.flywaydb:flyway-core`と`org.springframework.boot:spring-boot-flyway`を追加。計画では`flyway-database-h2`も追加予定だったが、Maven Central上に存在しないartifactと判明(H2はFlyway Community Editionが標準サポートするため不要)。また`spring-boot-flyway`はSpring Boot 4系でオートコンフィグレーション本体が別モジュールへ分割されているために必要と判明した追加依存(計画からの逸脱、詳細は下記)。
- `backend/src/main/resources/application.yml` / `backend/src/test/resources/application.yml`: `spring.jpa.hibernate.ddl-auto`を`update`/`create-drop`から`validate`へ変更
- `backend/src/main/resources/db/migration/V1__baseline_existing_schema.sql`: 既存9エンティティ(config-engine 3・permission-engine 6)のベースラインマイグレーション。Hibernate `ddl-auto: update`が実際に生成したスキーマをダンプして正本とした
- `backend/src/main/resources/db/migration/V2__create_audit_log_entry.sql`: `audit_log_entry`テーブルと2つのインデックス(`idx_audit_log_entry_occurred_at`・`idx_audit_log_entry_target_type_occurred_at`、NFR1.1/NFR1.2)

## 作成ファイル(本体コード)

| ファイル | 内容 |
|---|---|
| `backend/src/main/java/com/mastersmith/audit/entity/AuditLogEntry.java` | 追記専用エンティティ(setterなし、BR7.5/BR7.8) |
| `backend/src/main/java/com/mastersmith/audit/repository/AuditLogEntryRepository.java` | `Repository`マーカーインタフェース直接継承。save/findByTargetType/findAllのみ宣言し、CrudRepository/JpaRepositoryは継承しない(BR7.5/BR7.6/NFR2.2/NFR4.3) |
| `backend/src/main/java/com/mastersmith/audit/event/AuditLogEventMapper.java` | ConfigChangedEvent/PermissionChangedEvent/ImportExecutedEventの3変換メソッド(BR7.2〜BR7.4/BR7.11/FR1.6) |
| `backend/src/main/java/com/mastersmith/audit/event/ConfigChangedEventListener.java` | 同期`@EventListener`、try-catchで例外遮断、ERRORログ(BR7.1/BR7.7/NFR4.2/NFR5.2) |
| `backend/src/main/java/com/mastersmith/audit/event/PermissionChangedEventListener.java` | 同上(PermissionChangedEvent向け) |
| `backend/src/main/java/com/mastersmith/audit/event/ImportExecutedEventListener.java` | 同上(ImportExecutedEvent向け) |
| `backend/src/main/java/com/mastersmith/audit/exception/AuditLogQueryValidationException.java` | クエリパラメータ検証エラー用例外(400、NFR2.5) |
| `backend/src/main/java/com/mastersmith/audit/exception/AuditLogForbiddenException.java` | 認可拒否用例外(403) |
| `backend/src/main/java/com/mastersmith/audit/web/AuditLogController.java` | `GET /api/audit-log`(C6契約)。`ActiveRoleResolver`+`PermissionEngineApi.canAccessScreen`への認可委譲(BR7.10/NFR2.1)、200/400/403/503のProblemDetailマッピング(NFR4.4)、Micrometer計装(NFR5.1) |
| `backend/src/main/java/com/mastersmith/audit/web/AuditLogEntryView.java` | レスポンス要素(record) |
| `backend/src/main/java/com/mastersmith/audit/web/AuditLogPageResponse.java` | C6契約のレスポンス形状(items, totalCount) |

## 作成ファイル(テスト、計26件)

| ファイル | 対応レイヤー |
|---|---|
| `backend/src/test/java/com/mastersmith/audit/entity/AuditLogEntryJpaTest.java` | データモデル層(Step 4) |
| `backend/src/test/java/com/mastersmith/audit/event/AuditLogEventMapperTest.java` | ビジネスロジック層・マッピング(Step 6、テーブル駆動) |
| `backend/src/test/java/com/mastersmith/audit/event/ConfigChangedEventListenerTest.java` | ビジネスロジック層・イベント購読(Step 6) |
| `backend/src/test/java/com/mastersmith/audit/event/PermissionChangedEventListenerTest.java` | 同上 |
| `backend/src/test/java/com/mastersmith/audit/event/ImportExecutedEventListenerTest.java` | 同上 |
| `backend/src/test/java/com/mastersmith/audit/web/AuditLogControllerTest.java` | API層、認可拒否専用テスト含む(Step 8) |

## 主要な実装判断

- **`AuditLogEntryRepository`への`findAll(Pageable)`直接宣言**: `Repository`マーカーインタフェースへ`SimpleJpaRepository`のシグネチャに一致するメソッドを直接宣言する手法で、UPDATE/DELETE相当のメソッドを一切持ち込まずにSpring Data JPAの標準実装へ委譲させている(security-design.md準拠)。
- **`AuditLogControllerTest`は`@MockitoSpyBean`で実リポジトリをspy**: unit-test-instructions.mdの「実際のH2に対して検証し、モックしない」方針を守りつつ、503テストのみ`doThrow`で一時的に例外を注入する。
- **既存4ユニットのgoogle-java-format整形drift**: `git checkout --`で明示的に本ユニットのスコープ外として取り消した(下記「計画からの逸脱」参照)。

## 計画からの逸脱

- **`flyway-database-h2`は不採用**: 計画が前提としていたartifactがMaven Central上に存在しないことを確認(404)。H2はFlyway Community Edition(`flyway-core`)が標準サポートするため不要。
- **`spring-boot-flyway`を追加(計画外)**: `flyway-core`のみでは自動マイグレーションが実行されないことが判明。Spring Boot 4系はオートコンフィグレーション本体をモジュール分割しており、`FlywayAutoConfiguration`等は別モジュール`spring-boot-flyway`が提供する。追加後、`PermissionEngineIntegrationTest`の「missing table」失敗が解消した。
- **既存4ファイルのspotless整形drift(未修正・スコープ外)**: `config/dto/ColumnDraftEntry.java`・`config/entity/ColumnConfig.java`・`dataio/DataImportExportApi.java`ほか計21ファイルに、本ユニットと無関係なgoogle-java-format整形差分(pre-existing)が存在する。クリーンなチェックアウト状態でも`spotlessJavaCheck`が失敗することを確認済みで、本ユニットの変更が原因ではない(環境/ツールバージョン起因)。CI(`spotlessCheck`はマージ前ブロッキング)に影響するため、別途対応が必要。schema-introspectorユニットのcode-summary.mdでも同一のdriftが既に報告されている。

## テストカバレッジ概要

- `./gradlew :backend:test --tests "com.mastersmith.audit.*"` → 26/26件成功
- 既存4ユニット回帰確認(`config`/`schema`/`permission`/`dataio`パッケージを個別実行)→ すべてBUILD SUCCESSFUL、失敗0件(Flyway導入・`ddl-auto: validate`化後も無修正でグリーン)
- プロジェクト全体`./gradlew :backend:test` + `jacocoTestCoverageVerification`(spotless/checkstyle除く)→ 343件成功、失敗0件、80%行カバレッジフロアも合格
- `spotlessJavaCheck`・`checkstyleMain`・`checkstyleTest`は本ユニットが生成・変更した全ファイルについて合格(既存21ファイルのdriftは上記参照)

## Assumptions & Open Questions

- `actorRaw`属性のContract Design(C6)への追補は、`functional-spec.md`に記録済みの将来課題として引き継ぐ(今回のレスポンス実装には未反映、閲覧APIのレスポンス形状のみに関わる変更でブロッカーではないため)。
- 既存4ファイルのspotless整形driftへの対応は、本Boltのスコープ外の別タスクとして検討が必要。
