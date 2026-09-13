# Unit Test Instructions — config-engine (U1)

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5 + Spring Boot Test（`spring-boot-starter-test`）
- **データアクセステスト**: `@DataJpaTest`（組込みH2、テストプロファイル）
- **統合テスト**: `@SpringBootTest`（アプリケーションコンテキスト全体の起動確認を含む）
- **ビルドツール**: Gradle（`backend/build.gradle.kts`の`test`タスク）

## 実行コマンド（このUnitのみに厳密スコープ）

```
./gradlew :backend:test --tests "com.mastersmith.config.*"
```

このコマンドは`com.mastersmith.config`パッケージ配下のテストクラスのみを対象とする（プロジェクト全体のテストは実行しない）。Build and Testステージはこのコマンドをそのまま実行する。

## カバレッジ目標

- **行カバレッジ**: 80%以上（`team.md`確定のmvp系スコープフロア）
- **上乗せ条件**: config-engineは「権限判定ロジック」「監査ログ記録」のいずれにも該当しないため、追加のテーブル駆動テスト合格条件（`team.md`インタビューQ6）は適用対象外。ただし`ConfigValidator`（BR1.1〜BR1.4）の検証はテーブル駆動テストで網羅する（Step 8）。

## モック／スタブ方針

- リポジトリ層のテスト（Step 6）は実際の組込みH2に対する統合テスト（`@DataJpaTest`）とし、モックは使用しない（実データベース挙動の検証を優先）。
- `ConfigModelStore`の単体テスト（Step 10）は、`ConfigCache`・リポジトリをモック（Mockito）してビジネスロジックのみを検証する。
- `ConfigCache`の単体テスト（Step 10）はリポジトリをモックし、キャッシュのロード・差し替えロジックのみを検証する。

## テストデータ管理

- 各テストクラスは、テストメソッドごとに独立したテストデータをJavaのビルダー/ファクトリメソッド（例: `TableConfigTestFactory.aTableConfig()`）で構築する。テストクラス間・テストメソッド間で可変な共有状態を持たない。
- `@DataJpaTest`は各テストメソッド後にトランザクションをロールバックする（Spring Bootのデフォルト挙動）ため、明示的なクリーンアップは不要。

## 対象テストファイル一覧（想定）

| レイヤー | テストクラス（想定） | 対応Plan Step |
|---|---|---|
| データモデル | `TableConfigJpaTest`, `ColumnConfigJpaTest`, `TranslationEntryJpaTest` | Step 4 |
| リポジトリ | `TableConfigRepositoryTest`, `ColumnConfigRepositoryTest`, `TranslationEntryRepositoryTest`, `RdbmsTypeNormalizerTest` | Step 6 |
| ビジネスロジック（検証） | `ConfigValidatorTest`, `ConfigValidationExceptionTest` | Step 8 |
| ビジネスロジック（キャッシュ・ストア） | `ConfigCacheTest`, `ConfigModelStoreTest`, `ConfigEngineFailFastStartupTest`（`@SpringBootTest`）, `ConfigModelStorePerformanceTest` | Step 10 |
| TranslationStore | `TranslationStoreTest`, `I18nKeyDerivationTest` | Step 12 |

想定合計テスト数: 約35〜45件（Comprehensive戦略の目安「コンポーネントあたり10〜15件」× 4コンポーネント（ConfigModelStore/ConfigCache/ConfigValidator/TranslationStore）+ エンティティ・リポジトリのテストを加味）。
