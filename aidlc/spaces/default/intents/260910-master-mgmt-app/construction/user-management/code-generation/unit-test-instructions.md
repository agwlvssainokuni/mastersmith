# Unit Test Instructions — user-management (U4)

## テストフレームワーク・設定

- **フレームワーク**: JUnit 5 + Spring Boot Test(`spring-boot-starter-test`)、Mockito
- **永続化のテスト**: `@DataJpaTest`(H2、Flywayマイグレーション適用後。既存ユニットの`permission`配下のテストと同じ流儀)
- **統合テスト**: `@SpringBootTest`(コミット後の発行と監査ログの永続化、並行受諾、行ロック、ログイン成功時のハッシュ更新)
- **Webテストスライス**: `@WebMvcTest`(`spring-boot-starter-webmvc-test`。既存の`SchemaIntrospectionControllerTest`・`AuditLogControllerTest`・`MenuControllerTest`に合わせる)
- **安全失敗(起動時のfail fast)のテスト**: `ApplicationContextRunner`
- **ログの捕捉**: Logbackの`ListAppender`(トークン・個人情報の非露出の確認)
- **メトリクス**: `SimpleMeterRegistry`(カウンタ・タイマーの確認)
- **ビルドツール**: Gradle(`backend/build.gradle.kts`の`test`タスク、既存ユニットと共通)

## 実行コマンド(このUnitのみに厳密スコープ)

U4本体(自分のパッケージ):

```
./gradlew :backend:test --tests "com.mastersmith.usermanagement.*"
```

U4が他ユニットへ追加した部分(本Boltで追加・変更したテストのみ):

```
./gradlew :backend:test --tests "com.mastersmith.audit.event.UserChangedEventListenerTest" --tests "com.mastersmith.audit.event.AuditLogEventMapperTest" --tests "com.mastersmith.audit.event.UserChangedEventAuditIntegrationTest" --tests "com.mastersmith.permission.service.PermissionEngineApiImplTest"
```

既存の全テストがグリーンのままであること(他ユニットのテストを壊していないこと)は、ビルドとテストのステージ(Build and Test)が、全ユニットのコマンドをまとめて実行して確認する。

## カバレッジ目標

- **行カバレッジ**: 80%以上(`team.md`確定のmvp系スコープフロア。`backend/build.gradle.kts`の`jacocoTestCoverageVerification`)。基準・閾値は緩めない。
- **追加の合格条件(`team.md`インタビューQ6)**: U4自身は権限判定ロジック(ロール階層継承・主権限の解決)を持たず、`PermissionEngineApi`へ委譲する。ただし、`/api/users`系の認可(401・403)と、権限昇格の防止(自己のroleIds変更の拒否、roleIdの実在検証)は、U4固有の分岐ロジックであるため、主要な組み合わせを網羅するテーブル駆動テストを、`UserApplicationServiceTest`・`UserAuthorizerTest`(Step 13)に適用する。監査ログ記録については、`UserChangedEvent`の5種類の`operation`を網羅するテーブル駆動テストを`AuditLogEventMapperTest`(Step 16)に適用する。
- **性能の目標(NFR1.2)**: ハッシュ計算のp95が300ms以下(`PasswordHasherBenchmarkTest`)。基準を満たさない場合は、緩めずにギャップとして報告する。

## モック/スタブ方針

- `PermissionEngineApi`(C10)は、サービス・コントローラのテストでMockitoによりモックし、許可・拒否・`roleExists`の真偽を独立に検証する。永続化を伴う統合テストでは、実際の`PermissionEngineApiImpl`ではなくモックを用いる(permission-engineの挙動はpermission-engine自身のテストが担う)。
- `JavaMailSender`はモックする(`InvitationMailerTest`・`InvitationFacadeTest`)。実際のSMTPサーバーは起動しない。メール本文・件名・ヘッダーは、組み立てた`MimeMessage`の内容で確認する。
- 自作mustacheエンジンは、モックせず実物(サブモジュール)を用いる(HTMLエスケープの既定の挙動を確認するため)。
- `UserRepository`・`UserPreferenceRepository`は、H2(Flywayマイグレーション適用後)に対して検証し、モックしない。
- 同時実行のテスト(並行受諾、同一Userの同時更新、同一emailの同時招待)は、`ExecutorService`と`CountDownLatch`で開始を揃え、待ち時間の設定(排他の待機、ロック待ち)を短くして確認する。タイミングに依存する固定の`sleep`は避ける。

## テストデータ管理

- 各テストクラスは、テストメソッドごとに独立したテストデータをファクトリメソッドで構築する。emailは`UUID`を含めて一意にする。
- `@DataJpaTest`は各テストメソッド後にトランザクションをロールバックする。`@SpringBootTest`の統合テストは、コミットを伴うため(コミット後の発行・並行受諾の確認)、テストごとに一意なemail・userIdを用い、必要に応じて後始末で削除する。
- パスワード・招待トークンのダミー値は、テスト内の定数とし、リポジトリの設定ファイル(`application.yml`)に置く初期管理者・SMTPのダミー値も、実在しない値とする。

## 対象テストファイル一覧(想定)

| レイヤー | テストクラス(想定) | 対応Plan Step |
|---|---|---|
| データモデル | `UserJpaTest`、`UserPreferenceJpaTest`、`UserRepositoryTest` | Step 5 |
| 基盤(パスワード・同時計算) | `PasswordPolicyTest`、`HashConcurrencyLimiterTest`、`PasswordHasherTest`、`PasswordHasherBenchmarkTest` | Step 7 |
| メール | `MailTemplateRendererTest`、`SubjectExtractorTest`、`InvitationMailerTest`、`MailTemplateValidatorTest`(設定検証を含む) | Step 9 |
| 排他・許可・イベント発行 | `EmailLockRegistryTest`、`InvitationAdmissionTest`、`UserChangedEventPublisherTest` | Step 11 |
| ビジネスロジック | `UserAuthorizerTest`、`UserApplicationServiceTest`、`UserApplicationServiceConcurrencyTest`、`InvitationFacadeTest`、`InvitationAcceptServiceTest`、`UserAccountLookupServiceTest`、`InitialAdminBootstrapTest`、`InitialAdminPropertiesTest`、`UserPreferenceServiceTest`、`PermissionEngineApiImplTest`(`roleExists`の追加) | Step 13 |
| API層 | `UserControllerTest`、`InvitationAcceptControllerTest`、`MePreferencesControllerTest`、`RequestSizeLimitFilterTest`、`UserApiExceptionAdviceTest` | Step 15 |
| audit-logging(追加分) | `UserChangedEventListenerTest`、`AuditLogEventMapperTest`(追加)、`UserChangedEventAuditIntegrationTest` | Step 16 |
| 可観測性・非露出 | `UserManagementNoLeakTest` | Step 17 |

想定合計テスト数: 約150〜200件(Comprehensive戦略の目安「コンポーネントあたり10〜15件」× 主要コンポーネント約15件に、テーブル駆動の展開・並行・安全失敗のケースを加えたもの)。
