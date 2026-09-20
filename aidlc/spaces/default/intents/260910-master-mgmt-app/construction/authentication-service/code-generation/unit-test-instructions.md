# Unit Test Instructions — authentication-service (U5)

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5 + Spring Boot Test(`spring-boot-starter-test`)、Mockito、必要なら`spring-security-test`
- **永続化のテスト**: `@DataJpaTest`(H2、Flywayマイグレーション適用後。既存ユニットのテストと同じ流儀)
- **同時実行・統合テスト**: `@SpringBootTest`(実際のH2、認証フィルタ越し。ロックの予約・補償・リフレッシュ・ロール選択・キャッシュの無効化の並行実行、認証の一連の流れ)。**同時実行のテストはCIの必須の合格条件**
- **Webテストスライス**: `@WebMvcTest`(`spring-boot-starter-webmvc-test`。既存の`UserControllerTest`・`MenuControllerTest`等に合わせる)
- **安全失敗(起動時のfail fast)のテスト**: `ApplicationContextRunner`(鍵・設定の不正で起動が失敗すること)
- **ログの捕捉**: Logbackの`ListAppender`(認証情報・トークン・鍵の非露出の確認)
- **メトリクス**: `SimpleMeterRegistry`(カウンタ・タイマーの確認)
- **時刻**: `Clock`を差し替える(ロックの自動解除・有効期限・再送の猶予の境界。実時間の経過を待たない)
- **ビルドツール**: Gradle(`backend/build.gradle.kts`の`test`タスク、既存ユニットと共通)

## 実行コマンド(このUnitのみに厳密スコープ)

U5本体(自分のパッケージ):

```
./gradlew :backend:test --tests "com.mastersmith.auth.*" --tests "com.mastersmith.common.security.*"
```

U5が他ユニットへ加えた変更(本Boltで追加・更新したテストのみ):

```
./gradlew :backend:test --tests "com.mastersmith.permission.service.PermissionEngineApiImplTest" --tests "com.mastersmith.permission.PermissionEngineIntegrationTest" --tests "com.mastersmith.usermanagement.service.UserAccountLookupServiceTest" --tests "com.mastersmith.usermanagement.service.UserAuthorizerTest" --tests "com.mastersmith.usermanagement.web.*" --tests "com.mastersmith.usermanagement.UserManagementNoLeakTest" --tests "com.mastersmith.schema.web.SchemaIntrospectionControllerTest" --tests "com.mastersmith.menu.web.MenuControllerTest" --tests "com.mastersmith.audit.web.AuditLogControllerTest"
```

既存の全テストがグリーンのままであること(他ユニットのテストを壊していないこと)は、ビルドとテストのステージ(Build and Test)が、全ユニットのコマンドをまとめて実行して確認する。

## カバレッジ目標

- **行カバレッジ**: 80%以上(`team.md`確定のmvp系スコープフロア。`backend/build.gradle.kts`の`jacocoTestCoverageVerification`)。基準・閾値は緩めない。
- **追加の合格条件(`team.md`インタビューQ6)**: U5自身は権限判定ロジック(ロール階層継承・主権限の解決)を持たない。U5が変更するC10(`canAccessScreen`・`resolveEffectivePermission`)の`activeRoleId`のnull・空の扱いは、`activeRoleId`(null・空・実在・実在しない) × RBAC設定の有無 × 画面(`config-import-export`を含む)の組み合わせを網羅するテーブル駆動テストを、`PermissionEngineApiImplTest`・`PermissionEngineIntegrationTest`に追加して満たす。ロックの予約・補償、リフレッシュの判定表(ロールの再確認)、認証フィルタの401の条件は、それぞれテーブル駆動テストで主要な組み合わせを網羅する。
- **同時実行の目標**: 同時の誤った試行が何件あっても、検証できる試行がしきい値を超えないこと(実H2)。基準を満たさない場合は、緩めずにギャップとして報告する。

## モック/スタブ方針

- C11(`UserAccountLookupApi`)は、`AuthenticationApplicationService`・`UserAccountClient`の単体テストではMockitoでモックし、実在・不存在・無効化・ロック中の各ケースと、`HashCapacityExceededException`・DB障害を独立に検証する。**ロック・Session・キャッシュに関する同時実行・統合のテストでは、C11もモックし、内部設定DB(H2)は実物を用いる**。
- `PermissionEngineApi`(C10)は、認証の統合テストの検証用コントローラで、実物の`PermissionEngineApiImpl`を用いる範囲と、モックする範囲を分ける(認証フィルタ越しの権限制御の確認は実物、その他はモック)。permission-engineの挙動そのものはU3自身のテストが担う。
- `SessionRepository`・`AccountLoginStateRepository`は、H2(Flywayマイグレーション適用後)に対して検証し、モックしない。
- 時刻は`Clock`のテスト用実装を差し替える。乱数(`SecureRandom`)は、生成のテスト以外では、固定値を返すテスト用の生成器を用いてもよい(トークンの値に依存する検証のため)。
- 同時実行のテストは、`ExecutorService`と`CountDownLatch`で開始を揃え、設定値(しきい値・猶予・TTL)を短くして確認する。タイミングに依存する固定の`sleep`は避ける。

## テストデータ管理

- 各テストクラスは、テストメソッドごとに独立したテストデータをファクトリメソッドで構築する。userId・emailは`UUID`を含めて一意にする。
- `@DataJpaTest`は各テストメソッド後にトランザクションをロールバックする。`@SpringBootTest`の同時実行・統合テストは、コミットを伴うため、テストごとに一意なuserIdを用い、必要に応じて後始末で削除する。
- パスワード・トークン・JWTの鍵のダミー値は、テスト内の定数、またはテスト専用の`application.yml`に置く実在しない値とする(実値・本番相当の値をリポジトリに置かない)。

## 対象テストファイル一覧(想定)

| レイヤー | テストクラス(想定) | 対応Plan Step |
|---|---|---|
| データモデル | `SessionJpaTest`、`AccountLoginStateJpaTest`、`SessionRepositoryTest`、`AccountLoginStateRepositoryTest` | Step 5 |
| 基盤 | `AuthPropertiesTest`、`JwtKeyProviderTest`、`AccessTokenTest`、`RefreshTokenGeneratorTest`、`RefreshTokenHasherTest`、`SessionIdGeneratorTest`、`ProblemDetailsWriterTest`、`SecurityHeaderValuesTest` | Step 7 |
| ビジネスロジック | `LoginAttemptGateTest`、`AuthenticationApplicationServiceTest`、`SessionServiceTest`、`SessionCacheTest`、`SessionContextServiceTest`、`SessionCleanupJobTest`、`RevokeAllOnStartupTest`、`UserAccountClientTest` | Step 9 |
| API・セキュリティ | `BearerAuthenticationFilterTest`、`AuthSecurityConfigTest`、`AuthControllerTest`、`AuthRequestSizeLimitFilterTest`、`AuthApiExceptionAdviceTest`、`AuthCrossCuttingExceptionAdviceTest`、`SecurityContextOperatorContextTest` | Step 11 |
| 他ユニットの変更 | `PermissionEngineApiImplTest`・`PermissionEngineIntegrationTest`(追加)、`UserAccountLookupServiceTest`(追加)、`UserAuthorizerTest`(更新)、既存のコントローラのテスト(更新)、ヘッダー方式の不存在を確認するアーキテクチャテスト | Step 13 |
| 可観測性・非露出 | `AuthenticationNoLeakTest` | Step 14 |
| 統合・E2E | `AuthenticationFlowIntegrationTest`(ログイン → ロール選択 → 権限制御 → ログアウト、ロックの一連の流れ) | Step 15 |

想定合計テスト数: 約180〜250件(Comprehensive戦略の目安「コンポーネントあたり10〜15件」× 主要コンポーネント約18件に、テーブル駆動の展開・同時実行・安全失敗・他ユニットの既存テストの更新に伴う追加のケースを加えたもの)。
