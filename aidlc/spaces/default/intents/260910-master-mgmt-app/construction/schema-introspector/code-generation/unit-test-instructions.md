# Unit Test Instructions — schema-introspector (U2)

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5 + Spring Boot Test(`spring-boot-starter-test`)
- **統合テスト**: `@SpringBootTest`/`@WebMvcTest`(`RdbmsMetadataReader`のH2実データ検証、`SchemaIntrospectionController`のHTTP層検証)
- **ビルドツール**: Gradle(`backend/build.gradle.kts`の`test`タスク、config-engine/data-import-exportと共通)

## 実行コマンド(このUnitのみに厳密スコープ)

```
./gradlew :backend:test --tests "com.mastersmith.schema.*"
```

## カバレッジ目標

- **行カバレッジ**: 80%以上(`team.md`確定のmvp系スコープフロア)
- **上乗せ条件**: schema-introspectorは「権限判定ロジック」「監査ログ記録」のいずれの追加テーブル駆動テスト合格条件(`team.md`インタビューQ6)にも該当しない(権限判定自体はpermission-engine側の責務であり、本ユニットは呼び出すのみ)。標準の80%行カバレッジのみを適用する。

## モック/スタブ方針

- `SchemaIntrospectionService`の単体テストは、`RdbmsMetadataReader`・`ConfigEngineApi`をモック(Mockito)して結合を切り離す。
- `SchemaIntrospectionController`の単体テストは、`SchemaIntrospectionService`・`PermissionEngineApi`・`ActiveRoleResolver`をモックし、HTTP層(ステータスコード・ProblemDetail形式)の検証に専念する。
- `RdbmsMetadataReaderTest`は、組込みH2データベース上に実際にテストスキーマ(複数テーブル・主キー・NULL許容/非許容カラム)を作成し、実際のJDBC `DatabaseMetaData`を用いて検証する(モックしない。方言吸収の正しさを実データで確認するため)。

## テストデータ管理

- 各テストクラスは、テストメソッドごとに独立したテストデータをビルダー/ファクトリメソッドで構築する。
- `RdbmsMetadataReaderTest`は、テストメソッドごとに専用のH2インメモリスキーマ(ランダムなDB名、またはテストごとのDROP/CREATE)を用いて分離する。

## 対象テストファイル一覧(想定)

| レイヤー | テストクラス(想定) | 対応Plan Step |
|---|---|---|
| データモデル | (recordのコンパクトコンストラクタテスト、該当する場合) | Step 4 |
| 認可拡張点 | `HeaderActiveRoleResolverTest` | Step 6 |
| メタデータ読み取り | `RdbmsMetadataReaderTest`(H2実データ統合テスト) | Step 8 |
| ビジネスロジック | `SchemaIntrospectionServiceTest` | Step 10 |
| RESTコントローラ | `SchemaIntrospectionControllerTest`(正常系・403・422) | Step 12 |

想定合計テスト数: 約20〜30件(Comprehensive戦略の目安「コンポーネントあたり10〜15件」× 3主要コンポーネント(RdbmsMetadataReader/SchemaIntrospectionService/SchemaIntrospectionController)、複雑度Sのため下限寄り)。
