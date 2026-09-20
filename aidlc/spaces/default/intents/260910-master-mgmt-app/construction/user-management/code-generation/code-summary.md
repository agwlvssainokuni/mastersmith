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

# Code Summary — user-management (U4)

承認済み`code-generation-plan.md`(前提事項12件・全19ステップ)に基づき、user-managementユニットを実装した。実装の途中で確認した事実(mustacheエンジンの座標・API、Argon2の挙動、各Stepの判断・差異)は、同じディレクトリの`code-generation-notes.md`に詳しく記録している。

## 結果の概要

| 項目 | 結果 |
|---|---|
| U4のテスト | 454件、失敗0 |
| 全体のテスト(他ユニットを含む) | 880件、失敗0 |
| 行カバレッジ | U4は96.9%、全体は91.7%(フロア80%を満たす。閾値は緩めていない) |
| `checkstyleMain`・`checkstyleTest` | 合格 |
| `jacocoTestCoverageVerification` | 合格 |
| ハッシュのベンチマーク(NFR1.2) | hash p95 = 31.1ms、verify p95 = 34.3ms(目標300ms以下)。環境: CPU 8コア、最大ヒープ512MiB、macOS(aarch64)、JDK 25.0.4。Argon2id(m=19456KiB, t=2, p=1)、ウォームアップ10回・計測40回 |
| 整形(Spotless) | U4のファイルはgoogle-java-formatの適用済み(プロジェクト全体への`spotlessApply`で、U4および今回変更した他ユニットのファイルに差分が出ないことを確認) |

## 作成・変更したファイル

### 契約・機能設計への追補(Step 1)

- `inception/contract-design/contract-summary.md`のC5・C10・C11に追補。
- `construction/user-management/functional-design/functional-spec.md`・`rules.md`の末尾に「Code Generation着手時の追補」節を追加(既存の記述は書き換えていない)。

### ビルド・設定(Step 2・3)

- `.gitmodules`、`external/java-mustache-processor`(自作mustacheエンジンのサブモジュール、コミット`8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`で固定)。
- `settings.gradle.kts`(`includeBuild`)、`backend/build.gradle.kts`(`cherry-mustache-core`・`spring-boot-starter-mail`・`spring-security-crypto`・`bcprov-jdk18on:1.86`)。
- `backend/src/main/resources/application.yml`: `mastersmith.users.*`・`mastersmith.mail.*`・`spring.mail.*`・接続プールの最大サイズ60・メールのヘルスインジケータの無効化。初期管理者・SMTPなどの実値・既定値は置かず、環境変数のプレースホルダのみ(未設定なら起動時に失敗する)。
- `backend/src/test/resources/application.yml`: 実在しないダミー値。

### 本体コード(`backend/src/main/java/com/mastersmith/usermanagement/`、`backend/src/main/resources/`)

| 領域 | 主なファイル |
|---|---|
| データモデル | `db/migration/V4__create_user_management.sql`、`entity/`(`User`・`UserPreference`・`UserStatus`・`Theme`・`FontSize`・`UiLocale`)、`repository/`(`UserRepository`・`UserRepositoryCustomImpl`(行ロック)・`UserPreferenceRepository`・射影) |
| パスワード・同時計算 | `security/PasswordPolicy`・`PasswordHasher`(Argon2id)・`HashConcurrencyLimiter`、`HashCapacityExceededException` |
| 招待メール | `mail/`(`MailTemplateRenderer`・`SubjectExtractor`・`InvitationMailer`・`InvitationMailExecutor`・`MailTemplateValidator`・`MailSettingsValidator`)、`mail/invitation_ja.html`・`invitation_en.html` |
| 排他・許可・イベント | `security/EmailLockRegistry`・`InvitationAdmission`、`event/`(`UserChangedEvent`・`UserSnapshot`・`UserChangedEventPublisher`) |
| 操作者・認可・検証 | `security/`(`Operator`・`CurrentOperatorProvider`・`HeaderCurrentOperatorProvider`(暫定)・`UserAuthorizer`)、`service/UserInputValidator` |
| 業務ロジック | `service/`(`UserApplicationService`・`InvitationFacade`・`InvitationAcceptService`・`UserPreferenceService`・`UserAccountLookupService`(C11)・`InitialAdminBootstrap`)、`UserAccountLookupApi`・`UserAccount`、`config/InitialAdminProperties` |
| API層 | `web/`(`UserController`・`InvitationAcceptController`・`MePreferencesController`・`RequestSizeLimitFilter`・`UserApiExceptionAdvice`・`UpdateUserRequestFactory`)、`dto/`、`exception/` |
| 可観測性 | `observation/UserObservations`(スパン)、7つのメトリクス(ラベルなし) |

### 他ユニットへの変更(計画の前提事項で許可された最小限)

- permission-engine(U3): `PermissionEngineApi.roleExists`と`PermissionEngineApiImpl`の実装、`PermissionEngineApiImplTest`に3ケース。`PermissionEngineApiImpl`には、整形のみの差分が少量混ざる。
- audit-logging(U7): `UserChangedEventListener`(新規)と`AuditLogEventMapper.fromUserChangedEvent`、対応するテスト。既存のリスナー・マッパーの既存メソッドは変更していない。
- audit-loggingの既存テスト`AuditLogControllerTest`に`@BeforeEach`を1つ(11行)追加した。理由: U4の初期管理者の自動作成(BOOTSTRAPPED)が、アプリケーションの起動時に監査ログへ1行を確定するようになり、空の監査ログを前提とする2件のテストが失敗したため。テストのトランザクションの中でだけ行を除外する(ロールバックされる)。期待値・検証内容は変更していない。
- 他ユニットの整形のみの変更(dataio・menu・permissionなど33ファイル)は、この計画の対象外。別のコミットとして分ける。

## テスト

| レイヤー | 主なテスト |
|---|---|
| データモデル | `UserJpaTest`・`UserPreferenceJpaTest`・`UserRepositoryTest`・`UserRepositoryLockTest` |
| 基盤 | `PasswordPolicyTest`(境界値7・8・128・129とサロゲートペア)・`HashConcurrencyLimiterTest`・`PasswordHasherTest`・`PasswordHasherBenchmarkTest` |
| メール | `MailTemplateRendererTest`(HTMLエスケープ)・`SubjectExtractorTest`(文字参照・改行・200文字)・`InvitationMailerTest`・`MailTemplateValidatorTest`・`MailSettingsValidationTest`(安全失敗) |
| 排他・許可・イベント | `EmailLockRegistryTest`・`InvitationAdmissionTest`・`UserChangedEventPublisherTest` |
| 業務ロジック | `UserAuthorizerTest`・`UserApplicationServiceTest`(テーブル駆動の認可拒否専用テスト)・`UserApplicationServiceConcurrencyTest`・`InvitationFacadeTest`・`InvitationAcceptServiceTest`(並行受諾)・`UserAccountLookupServiceTest`・`InitialAdminBootstrapTest`・`InitialAdminPropertiesTest`(安全失敗)・`UserPreferenceServiceTest` |
| API層 | `UserControllerTest`・`InvitationAcceptControllerTest`・`MePreferencesControllerTest`・`RequestSizeLimitFilterTest`・`UserApiExceptionAdviceTest`・`UserApiIntegrationTest`(実際のフィルタチェーン) |
| audit-logging(追加分) | `AuditLogEventMapperTest`(5種類のoperationのテーブル駆動)・`UserChangedEventListenerTest`・`UserChangedEventAuditIntegrationTest`(コミット後の永続化) |
| 可観測性・非露出 | `UserObservationsTest`・`UserManagementNoLeakTest`(ログ・メトリクスのラベル・スパンの属性・ProblemDetails・スナップショットに認証情報の実値が現れないこと) |
| 起動 | `UserManagementStartupTest`(初期管理者の作成・C11のBean結線・メールのヘルスインジケータがないこと) |

`team.md`の必須テスト種別のうち、(a)安全失敗と(c)認可拒否は本Boltで実施した。(b)複数プロファイル横断E2Eは、list-engine・record-edit-engine・frontend-uiが未実装のため対象外(計画の前提事項12)。

## 主要な実装判断

- **資源の取得順序**: 排他(同一email)→許可(招待の同時実行数・ハッシュ計算)→DB接続。ハッシュ計算はトランザクションの外で行う。`@Transactional`は使わず、`TransactionTemplate`(内部設定DBのトランザクションマネージャを明示)を使う。
- **コミット後のイベント発行**: 排他・許可の返却の後に、`REQUIRES_NEW`のトランザクションの中で同期に`publishEvent`を呼ぶ。発行の例外は、警告ログ(userIdのみ)と`user.event.publish.failed`に記録し、HTTPの結果に影響させない。
- **roleIdの実在検証**: 招待では全件、更新では新たに付与するroleIdだけを対象にした(付与済みのroleIdが後で削除されても、nameなどの更新を妨げないため)。ロールIDの重複は除き、`name`は前後の空白を除去して保持する(rules.mdの追補)。
- **自己のroleIds変更の拒否**: 順序と重複を無視した集合の比較で判定する。付与の追加も削除も拒否する。
- **`needsUpgrade`**: Spring Securityの`Argon2PasswordEncoder#upgradeEncoding`が、メモリ・反復回数を比較する(並列度は比較しない)ため、自前の解析は不要だった。
- **HTMLエスケープ**: 自作mustacheエンジンは`{{変数}}`を既定で`& < > "`にエスケープする(`'`はエスケープしない)。テンプレートの属性値はダブルクォート囲みにし、エスケープなしの展開は使わない。
- **メトリクスの分類**: 件名の拒否とテンプレートのレンダリング失敗は`user.invitation.subject.rejected`だけ、SMTPの不調は`user.invitation.mail.failed`だけを数える。
- **`UserChangedEventListener`のログ**: DBの例外メッセージはスナップショットの氏名・メールアドレスを含みうるため、例外の型名だけを出す(他の3リスナーとの意図的な差異)。
- **スパン**: コンストラクタの引数を増やさず、`@Autowired`のセッターで`ObservationRegistry`を注入する(`@WebMvcTest`の既存テストを壊さないため)。属性は固定値のみで、userIdも含めない。

## 計画・設計からの差異(意図的)

- **C11の`revokeRefreshTokensOnDisable`は実装していない**(前提事項9)。呼び出し方向が未確定のため、U5の機能設計で確定した時点で追加する。
- **`CurrentOperatorProvider`は暫定実装**(前提事項2)。`X-User-Id`・`X-Active-Role-Id`ヘッダーから読み、JWTは検証しない。U5の実装時に、検証済みトークンのクレームから読む実装へ差し替える。認可判定自体は、サーバー側で必ず行う。
- **`instance`の表記**: ProblemDetailsの`instance`は、リクエストの生のパスではなくルートのテンプレートを用いる。URIとして`{}`は符号化されるため、招待受諾APIでは`/api/users/invitations/%7Btoken%7D/accept`となる(設計・C5追補の`/api/users/invitations/{token}/accept`の、JSON上の表現)。トークンの露出抑止の目的は満たしている。
- **`CannotCreateTransactionException`(接続プールの枯渇など)を503にした**。C5の追補に明記のない、実装上の追加。
- **計画のStep 17の文言との差異**: 計画はイベントのスナップショットに氏名・メールアドレスが現れないことを求めるが、設計(entities.md・BR4.9)はスナップショットが`{name, email, status, roleIds}`を持つことを定めている(残余リスク5)。したがって、スナップショットについては、パスワード・招待トークン・passwordHashが現れないことを確認した。
- **`Locale`の列挙は`UiLocale`とした**(`java.util.Locale`との混同を避けるため)。`mastersmith.mail.from`(必須)を追加した(MimeMessageのFromに必要)。
- **`management.health.mail.enabled: false`を追加した**。SMTPの疎通を健全性の判定に含めない(NFR5.4)ため。
- **計画外の追加テスト**: `UserManagementStartupTest`・`UserApiIntegrationTest`。

## 既知の課題・残る作業

- **他ユニットの`spotlessCheck`**: HEADの時点で、dataio・menu・permissionなど他ユニットの既存ファイルが、google-java-formatに未整形で、`./gradlew :backend:spotlessCheck`はプロジェクト全体では不合格になる(U4のファイルは合格)。他ユニットの整形は、この計画の対象外として、別のコミットで行う。
- **共通基盤への要求(本Boltの対象外、NFR2.10・保留11・12・15番)**: Spring MVCが記録する`http.server.requests`の観測は、生のリクエストURLを高カーディナリティの属性`http.url`に持つ。招待受諾APIでは、この値にトークンが含まれる(`uri`のタグはルートのテンプレートで問題ない)。トレーシングのブリッジを導入すると、この値がスパンの属性として書き出され、トークンが露出しうる。共通基盤側で`ServerRequestObservationConvention`の差し替えが必要。U4のスパン・ログ・メトリクスのラベル・ProblemDetailsには、トークンは現れない。
- **共通基盤への要求(続き)**: `Referrer-Policy: no-referrer`、認証フィルタチェーン(`RequestSizeLimitFilter`より後ろに置く)、汎用の例外ハンドラの基底、構造化ログ(JSON)、トレース・メトリクスのエクスポート方式は、U5・packaging等の実装時に対応する。
- **未確認の統合**: 認証フィルタ(U5)との結線と、招待受諾画面(U12)でのフラグメントの読み取りは、それぞれのユニットの実装時に確認する。
- **性能の目標**: NFR1.1(管理系APIの応答時間3秒p95)は、team.mdが負荷・性能テストを既定に含めないため、自動検証はしていない。
