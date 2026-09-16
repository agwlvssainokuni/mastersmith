# Unit Test Instructions — menu-navigation (U6)

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5 + Spring Boot Test(`spring-boot-starter-test`)
- **統合テスト**: `@SpringBootTest`(`MenuItemRepository`のFlywayマイグレーション適用後の永続化検証)
- **Webテストスライス**: `@WebMvcTest`または既存パターン(schema-introspectorの`SchemaIntrospectionControllerTest`、audit-loggingの`AuditLogControllerTest`)に合わせた統合テスト
- **ビルドツール**: Gradle(`backend/build.gradle.kts`の`test`タスク、既存ユニットと共通)

## 実行コマンド(このUnitのみに厳密スコープ)

```
./gradlew :backend:test --tests "com.mastersmith.menu.*"
```

## カバレッジ目標

- **行カバレッジ**: 80%以上(`team.md`確定のmvp系スコープフロア)
- **権限マトリクス網羅の追加合格条件**: menu-navigation自身は権限判定ロジックを持たない(`PermissionEngineApi`へ委譲)が、`MenuTreeBuilder`のリーフ/フォルダ/管理メニューそれぞれに対する許可・拒否の組み合わせは本ユニット固有の分岐ロジックであるため、`team.md`インタビューQ6の追加合格条件(主要な組み合わせを網羅するテーブル駆動テスト)を`MenuTreeBuilderTest`(Step 6)に適用する。

## モック/スタブ方針

- `MenuTreeBuilderTest`・`MenuItemCommandServiceTest`・`MenuControllerTest`は、`ConfigEngineApi`・`PermissionEngineApi`をモック(Mockito)して各分岐(存在/不存在、許可/拒否)を独立に検証する。
- `MenuItemRepository`は実際のH2(Flywayマイグレーション適用後)に対して検証し、モックしない(永続化層の実際の挙動を優先)。

## テストデータ管理

- 各テストクラスは、テストメソッドごとに独立したテストデータをファクトリメソッドで構築する。
- `@SpringBootTest`の統合テストは各テストメソッド後にトランザクションをロールバックする。

## 対象テストファイル一覧(想定)

| レイヤー | テストクラス(想定) | 対応Plan Step |
|---|---|---|
| データモデル | `MenuItemJpaTest` | Step 4 |
| ビジネスロジック(木構造・権限フィルタ) | `MenuTreeBuilderTest` | Step 6 |
| ビジネスロジック(CRUD) | `MenuItemCommandServiceTest` | Step 6 |
| API層 | `MenuControllerTest` | Step 8 |

想定合計テスト数: 約25〜35件(Comprehensive戦略の目安「コンポーネントあたり10〜15件」× 主要コンポーネント3件相当)。
