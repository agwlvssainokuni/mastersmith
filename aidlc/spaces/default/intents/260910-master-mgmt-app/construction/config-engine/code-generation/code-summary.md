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

# Code Summary — config-engine (U1)

## プロジェクト基盤（本Boltで新規構築）

config-engineはプロジェクト最初のUnitであり、バックエンドのプロジェクト基盤（Gradleマルチプロジェクト + Spring Boot骨格）を併せて構築した。以降のUnit（U2〜U13）はこの基盤に追加していく。

- `settings.gradle.kts`, `build.gradle.kts`（ルート）, `backend/build.gradle.kts`: Spring Boot 4.1.1（最新安定版）+ Java 25ツールチェーン。Spotless（google-java-format + Apache-2.0ライセンスヘッダー強制）とCheckstyleを`check`タスクに組み込み、JaCoCoで80%行カバレッジゲート（`jacocoTestCoverageVerification`）も`check`に組み込み済み
- `backend/src/main/resources/application.yml`: 内部設定DB（H2、ファイルモード）を業務データ用RDBMS接続とは別プロファイルで定義
- `backend/src/main/java/com/mastersmith/MastersmithApplication.java`: 唯一のSpring Bootエントリポイント。`com.mastersmith`ルートに配置し、以降の全Unitのパッケージをコンポーネントスキャン対象とする

## config-engine実装

`backend/src/main/java/com/mastersmith/config/`配下に、`nfr-design/logical-components.md`で確定した4つの論理コンポーネントに対応する実装を配置した。

| ディレクトリ | 内容 |
|---|---|
| `entity/` | TableConfig, ColumnConfig, TranslationEntry(+複合ID), EditorType, Visibility |
| `model/` | ValidationRule, ChoiceOption, FkReference（JSON型カラム、Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)`） |
| `repository/` | Spring Data JPAリポジトリ3種 |
| `rdbms/` | RdbmsTypeNormalizer（BR1.12: PostgreSQL/MySQL/MariaDBの型名正規化） |
| `validation/` | ConfigValidator + カスタム制約`@ChoiceOrFkExclusive`（BR1.4） |
| `cache/` | ConfigCache（起動時全読込・原子的スナップショット差し替え、performance-design.md準拠） |
| `store/` | ConfigEngineApi（C9契約）, ConfigModelStore, ConfigEngineStartupRunner（fail-fast起動） |
| `translation/` | TranslationStore, I18nKeyDerivation（BR1.5, BR1.6, BR1.10） |
| `event/` | ConfigChangedEvent（AuditLoggingへのイベント駆動連携フック） |
| `dto/`, `exception/` | Import/Export用DTO、ConfigValidationException等 |

## 主要な実装判断

- `ColumnConfig.tableConfigId`はJPA `@ManyToOne`ではなく単純な文字列カラムとした。C9契約とConfigCacheはIDのみを必要とするため
- TableConfig/ColumnConfigの必須項目検証はDBの`NOT NULL`制約ではなくConfigValidator（Bean Validation）で行う。アプリケーション層での検知（fail-fast）という要件、および起動時fail-fastの統合テストで不正な行をあえて投入する必要があるための設計判断
- `ConfigChangedEvent`は現状`actor="system"`で発行される（schema-introspector等システム起点の書き込み）。`importConfigSet`/将来のW6経由での実際の操作者伝播は、`functional-spec.md`のAssumptions & Open Questionsに記載済みの認証コンテキスト契約の追補待ちの既知のギャップとして残る
- WARパッケージング（`team.md`確定）はpackagingユニット（U13）に委譲し、本Boltでは標準のSpring Boot jarとしてビルドする

## テストカバレッジ

- テストクラス16、テストケース145件（JPAマッピング、リポジトリ統合、RDBMS正規化・バリデーションのテーブル駆動テスト、キャッシュ/ストアの単体テスト、起動時fail-fastの実統合テスト、性能マイクロベンチマークを含む）
- 全テスト成功、行カバレッジ91.1%（フロア80%）。`./gradlew :backend:check`（checkstyle + spotlessCheck + test + jacocoTestCoverageVerification）・`:backend:assemble`ともに成功を確認済み（オーケストレーターが独立して再実行し確認）

## 計画からの逸脱

- なし。全14ステップを計画どおり実施した（`code-generation-plan.md`のチェックボックスを参照）。
