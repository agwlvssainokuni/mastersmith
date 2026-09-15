# Unit Test Instructions — audit-logging (U7)

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5 + Spring Boot Test(`spring-boot-starter-test`)
- **統合テスト**: `@SpringBootTest`(`AuditLogEntryRepository`のFlywayマイグレーション適用後の永続化検証、`AuditLogController`のエンドツーエンド検証)
- **Webテストスライス**: `@WebMvcTest`または既存パターン(schema-introspectorの`SchemaIntrospectionControllerTest`)に合わせた統合テスト
- **ビルドツール**: Gradle(`backend/build.gradle.kts`の`test`タスク、config-engineと共通)

## 実行コマンド(このUnitのみに厳密スコープ)

```
./gradlew :backend:test --tests "com.mastersmith.audit.*"
```

前提修正(Flyway導入)の回帰確認は、既存4ユニットの既存テストコマンドを個別に再実行することで別途行う(本コマンドのスコープには含めない)。Build and Testステージは本コマンドをそのまま実行する。

## カバレッジ目標

- **行カバレッジ**: 80%以上(`team.md`確定のmvp系スコープフロア)
- **権限・監査ログの追加合格条件**: audit-loggingは「監査ログ記録」の実装ユニットであるため、`team.md`インタビューQ6の追加合格条件(主要な権限マトリクスの組み合わせを網羅するテーブル駆動テスト)が適用され得るが、本ユニット自身は権限判定ロジックを持たず(認可はpermission-engineへ委譲)、監査ログ「記録」側にマトリクスは存在しない。該当する上乗せ条件は「イベント種別ごとのマッピング規則」をテーブル駆動テストで網羅すること(`AuditLogEventMapperTest`、Step 6)とする。

## モック/スタブ方針

- `AuditLogController`の単体テストは、`PermissionEngineApi`をモック(Mockito)して認可判定の各分岐(許可/拒否)を独立に検証する。
- `AuditLogEntryRepository`は実際のH2(Flywayマイグレーション適用後)に対して検証し、モックしない(永続化層の実際の挙動を優先)。
- 各EventListenerの単体テストは、`AuditLogEntryRepository`をモックして例外送出ケース(NFR4.2の非伝播検証)を再現する。

## テストデータ管理

- 各テストクラスは、テストメソッドごとに独立したテストデータをファクトリメソッドで構築する。
- `@SpringBootTest`の統合テストは各テストメソッド後にトランザクションをロールバックする。

## 対象テストファイル一覧(想定)

| レイヤー | テストクラス(想定) | 対応Plan Step |
|---|---|---|
| データモデル | `AuditLogEntryJpaTest` | Step 4 |
| ビジネスロジック(マッピング) | `AuditLogEventMapperTest` | Step 6 |
| ビジネスロジック(イベント購読) | `ConfigChangedEventListenerTest`, `PermissionChangedEventListenerTest`, `ImportExecutedEventListenerTest` | Step 6 |
| API層 | `AuditLogControllerTest` | Step 8 |

想定合計テスト数: 約20〜30件(Comprehensive戦略の目安「コンポーネントあたり10〜15件」× 主要コンポーネント2〜3件相当)。
