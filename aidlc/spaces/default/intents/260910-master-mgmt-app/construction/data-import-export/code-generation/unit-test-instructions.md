# Unit Test Instructions — data-import-export (U8)

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5 + Spring Boot Test（`spring-boot-starter-test`）
- **統合テスト**: `@SpringBootTest`（`CsvImportService`のトランザクション境界・イベント発行検証）
- **ビルドツール**: Gradle（`backend/build.gradle.kts`の`test`タスク、config-engineと共通）

## 実行コマンド（このUnitのみに厳密スコープ）

```
./gradlew :backend:test --tests "com.mastersmith.dataio.*" --tests "com.mastersmith.config.entity.ColumnConfigJpaTest" --tests "com.mastersmith.config.store.ConfigModelStoreTest"
```

前者は本Unit本体（`com.mastersmith.dataio`パッケージ）、後者2つは前提修正（config-engineのisPrimaryKey対応）に追加したテストケースを含む既存テストクラスである。Build and Testステージはこのコマンドをそのまま実行する。

## カバレッジ目標

- **行カバレッジ**: 80%以上（`team.md`確定のmvp系スコープフロア）
- **上乗せ条件**: data-import-exportは「権限判定ロジック」「監査ログ記録」のいずれの追加テーブル駆動テスト合格条件（`team.md`インタビューQ6）にも該当しない。ただしCSVインポートのバリデーション（BR8.5）はテーブル駆動テストで網羅する（Step 8）。

## モック／スタブ方針

- `CsvExportService`/`CsvImportService`の単体テストは、`ConfigEngineApi`をモック（Mockito）してconfig-engineとの結合を切り離す。
- ストリーミングI/O（CSV読み書き）は実際の`ByteArrayInputStream`/`ByteArrayOutputStream`を用いて検証し、モックしない（実際のCSVパース・生成挙動を優先）。
- `CsvImportService`の`@SpringBootTest`統合テストは組込みH2（config-engineの内部設定DB接続を再利用）を用いる。

## テストデータ管理

- 各テストクラスは、テストメソッドごとに独立したテストデータをビルダー/ファクトリメソッド（例: `CsvColumnDefinitionTestFactory`）で構築する。
- `@SpringBootTest`の統合テストは各テストメソッド後にトランザクションをロールバックする。

## 対象テストファイル一覧（想定）

| レイヤー | テストクラス（想定） | 対応Plan Step |
|---|---|---|
| データモデル | （recordのコンパクトコンストラクタテスト、該当する場合） | Step 4 |
| ビジネスロジック（エクスポート） | `CsvColumnDefinitionResolverTest`, `CsvExportServiceTest` | Step 6 |
| ビジネスロジック（インポート） | `CsvRowValidatorTest`, `CsvImportServiceTest` | Step 8, 10 |
| 前提修正（config-engine） | `ColumnConfigJpaTest`（isPrimaryKey往復追加）, `ConfigModelStoreTest`（writeTableConfigDraft伝播追加） | 前提修正 |

想定合計テスト数: 約25〜35件（Comprehensive戦略の目安「コンポーネントあたり10〜15件」× 3コンポーネント（CsvColumnDefinitionResolver/CsvExportService/CsvRowValidator+CsvImportService）+ 前提修正分数件）。
