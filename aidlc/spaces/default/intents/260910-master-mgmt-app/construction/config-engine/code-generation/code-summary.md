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
- **（2026-09-17追記、アーキテクチャレビューR-03是正）** `ConfigChangedEvent`に`targetType`/`targetId`/`beforeValue`/`afterValue`（entities.md ConfigChangedEvent定義）を追加し、`writeTableConfigDraft`/`importConfigSet`/`TranslationStore.upsert`を、変更されたエンティティ（TableConfig/ColumnConfig/TranslationEntry）ごとに個別の`ConfigChangedEvent`を発行するよう修正した（BR1.13「呼び出し単位で1件にまとめてはならない」）。スナップショット構築は新設の`ConfigChangeSnapshots`（JPA管理下エンティティの後続save/マージによるインプレース書き換えを避けるため、発行時点の属性値をイミュータブルな`Map`へコピー）が担う。`importConfigSet`は上書き前の既存状態を`beforeValue`として反映するため、`saveAll`実行前に各エンティティを`findById`で確認する（新規作成時は`beforeValue=null`）
  - **意図的な逸脱（スコープ外として据え置き）**: entities.mdは`operation`を`CREATED`/`UPDATED`で区別することを要求するが、本修正では既存の`ConfigChangeOperation`（呼び出し種別: DRAFT_IMPORTED/CONFIG_SET_IMPORTED/TRANSLATION_UPSERTED）をそのまま維持し、CREATED/UPDATED区別の実装は行っていない（R-03是正dispatchの明示的な指示によるスコープ限定）。`beforeValue`は`writeTableConfigDraft`（常にnull、新規作成のため妥当）・`TranslationStore.upsert`（既存値を反映）・`importConfigSet`（`findById`による実データ照会で反映）のいずれも実データに基づいて正確に設定しており、将来operationのCREATED/UPDATED区別を実装する場合はこの`before != null`判定ロジックがそのまま再利用できる
  - **他ユニット（AuditLogging, U7）への影響なし**: `ConfigChangedEvent`は旧シェイプ（`operation, target, actor, occurredAt`）の4引数コンストラクタを後方互換のため保持しており（新フィールドはnullになる）、AuditLogging（U7、既にコード生成・レビュー済み）の`AuditLogEventMapper#fromConfigChangedEvent`（rules.md BR7.2で承認済み: `targetType="ConfigEngine"`固定・`targetId=event.target()`）や関連テストは無修正のまま再コンパイル・動作する。`of(...)`ファクトリメソッドは`target`を`"<targetType>:<targetId>"`として自動導出するため、AuditLogEntry.targetIdは従来の集約的な説明文字列（例: `"tables:3"`）よりも具体的なエンティティ単位の識別子を受け取るようになる（意図しない副作用ではあるが、既存契約を壊さない改善）
  - `traceability.json`にBR1.13・BR1.14を追加した（アーキテクチャレビューR-04是正）。BR1.14（`ColumnConfig.isPrimaryKey`は`writeTableConfigDraft`経由でのみ設定）については、`importConfigSet`が呼び出し元（config-import-export）から渡された`ColumnConfig`オブジェクトを`saveAll`するのみでisPrimaryKeyを変更するsetter/APIパラメータを持たないこと、および`ColumnConfig`にsetterが公開されていないことを確認済み。ただし4引数コンストラクタ（`ColumnConfig(tableConfigId, columnName, editorType, isPrimaryKey)`）自体はpublicであり、他ユニット（config-import-export等）がこのコンストラクタを直接呼び出して`isPrimaryKey=true`のオブジェクトを構築し`importConfigSet`に渡すことをconfig-engine側だけでは型システム上禁止できない。BR1.14の完全なコンパイル時保証には、コンストラクタの可視性をパッケージプライベート化するか、`importConfigSet`側でBR1.14違反（手動構築されたisPrimaryKey=true）を検知する追加バリデーションが必要であり、これは本修正（R-03/R-04是正）のスコープ外の追加検討事項として残す
- WARパッケージング（`team.md`確定）はpackagingユニット（U13）に委譲し、本Boltでは標準のSpring Boot jarとしてビルドする
- `ConfigModelStore.writeTableConfigDraft`のBR1.8既存判定は、インメモリキャッシュではなくリポジトリ（DB直接参照）で行う。直前の書込みの`cache.reload()`完了前に後続呼び出しが判定を素通りする競合を防ぐため（アーキテクチャレビューR-02指摘対応）。同一トランザクション内での極めて低確率な残余競合は、単一インスタンス構成という受入れ済み前提の下で許容されるリスクとしてjavadocに明記している

## テストカバレッジ

- テストクラス20、テストケース165件（JPAマッピング、リポジトリ統合、RDBMS正規化・バリデーションのテーブル駆動テスト、キャッシュ/ストアの単体テスト、起動時fail-fastの実統合テスト、性能マイクロベンチマーク、`ConfigChangedEvent`/`ConfigChangeSnapshots`の単体テストを含む。2026-09-17のR-03是正で新設2クラス・既存3クラスへのテスト追加により16クラス153件から増加）
- 全テスト成功、config-engineパッケージ（`com.mastersmith.config.*`）の行カバレッジ93.6%（フロア80%、R-03是正前は91.9%）。`./gradlew :backend:test --tests "com.mastersmith.config.*"`（165件成功）、`:backend:checkstyleMain`/`:backend:checkstyleTest`/`:backend:spotlessJavaCheck`（config-engine配下は違反なし。他ユニット(dataio)の既存フォーマット違反は本修正のスコープ外として不変更）、`:backend:test`＋`:backend:jacocoTestCoverageVerification`（バックエンド全体355件成功、カバレッジゲート通過、行カバレッジ86.8%）をいずれも確認済み

## 計画からの逸脱

- なし。全14ステップを計画どおり実施した（`code-generation-plan.md`のチェックボックスを参照）。
- 前回セッション終了後の巻き戻し（contract-design差し戻し等）でPlan Approvalレシートおよびアーキテクチャレビューが無効化されたため、本セッションで新しいフィンガープリントによるPlan Approval再承認と、実装済みコードの再検証を実施した。旧レビューの指摘2件（R-01: functional-spec.mdへの監査ログactor伝搬ギャップの記載漏れ、R-02: BR1.8スキップ判定の競合）のうち、R-01は本セッション以前の別コミットで既に是正済みと確認し、R-02は本セッションで修正した（上記参照）。
- **（2026-09-17追記）** アーキテクチャレビュー2回目（iteration 1、review-01.md）でR-03（Critical）・R-04（Major）の指摘を受け、上記「主要な実装判断」に記載のとおり是正した。R-05（contract-summary.md C9型定義の乖離）はオーケストレーターが別途是正済みのため本ドキュメントでは扱わない。
