# Code Generation Notes — authentication-service (U5)

オーケストレーターが`code-summary.md`を作るための根拠として、実物で確認した事実・設計とのズレ・判断・テスト結果を記録する。承認済みの計画(`code-generation-plan.md`)のStep 1〜17は、すべて完了した(チェックボックスは更新済み)。

## 実行結果(最終)

| 項目 | 結果 |
|---|---|
| `./gradlew :backend:clean :backend:test :backend:spotlessCheck :backend:checkstyleMain :backend:checkstyleTest :backend:jacocoTestCoverageVerification` | BUILD SUCCESSFUL |
| テスト件数(全体) | 1,322件、失敗0・エラー0・スキップ0(着手前のベースラインは880件) |
| U5のテスト(`com.mastersmith.auth.*`) | 379件 |
| 行カバレッジ(全体) | 92.6%(3,773 / 4,074行)。閾値80%(`jacocoTestCoverageVerification`)を満たす。閾値・基準は変更していない |
| 分岐カバレッジ(全体、参考) | 79.6%(閾値の定めはない) |
| 行カバレッジ(`com.mastersmith.auth`配下) | 95.3%(948 / 995行)。`security` 93.4%・`service` 96.5%・`token` 92.2%・`cache` 97.3%・`web` 95.5%・`config`・`exception` 100% |
| 他ユニット(行カバレッジ) | `usermanagement` 96.9%・`menu` 96.5%・`audit` 96.0%・`schema` 88.9%・`permission` 80.6% |
| 計画のU5単位のコマンド(`com.mastersmith.auth.*`と`com.mastersmith.common.security.*`) | 成功(`common.security`にはテスト支援クラスだけがあり、C15の契約テストは`auth.security.SecurityContextOperatorContextTest`) |
| 計画の他ユニット単位のコマンド(9つのテスト指定) | 成功(253件) |

全体のテストは、2回連続(修正後に`clean`してもう1回)で成功した。同時実行のテストは、`CountDownLatch`で開始を揃えており、固定の`sleep`に依存しない。

## 実物で確認した事実(依存・環境)

- Spring Boot 4.1.1のBOMが、Spring Security 7.1.1(`spring-boot-starter-security`・`spring-security-web`・`spring-security-config`・`spring-security-test`)を管理している。`backend/build.gradle.kts`へ、`spring-boot-starter-security`(implementation)・`spring-security-test`(testImplementation)を、バージョンなしで追加した。
- Nimbus JOSE + JWT(`com.nimbusds:nimbus-jose-jwt`)は、Spring Boot BOMの管理対象外(`spring-boot-dependencies-4.1.1.pom`に記述なし)。Spring Security 7.1.1の`oauth2-jose`が取り込むのは10.9.1で、Maven Centralの最新の安定版は10.10のため、`10.10`を固定した。
- Spring Boot 4.xのパッケージ変更: ヘルスの型は`org.springframework.boot.health.contributor.Health`・`HealthIndicator`(`org.springframework.boot.actuate.health`ではない)。`FilterRegistrationBean`は`org.springframework.boot.web.servlet`、`SecurityFilterProperties.DEFAULT_FILTER_ORDER`は`org.springframework.boot.security.autoconfigure.web.servlet`。
- `@WebMvcTest`のスライスは、既存テストの範囲では、Spring Securityの追加で壊れなかった(既存のコントローラのテストは、`SecurityFilterChain`がスライスに含まれないため、そのまま動いた)。`@SpringBootTest`(実チェーン)では、認証フィルタが有効になるため、既存の統合テストは、`TestPermitAllSecurityConfig`(認証を通さない`SecurityFilterChain`)と`TestOperatorContext`(C15の差し替え)へ切り替えた。
- H2で、`UPDATE`のSET句の各式が更新前の値で評価されることを、`AccountLoginStateRepositoryTest`・`LoginAttemptGateTest`(ロックの回数・世代・`locked_until`の判定に依存)で確認した(標準のSQLの動作。reliability-design.mdの前提のとおり)。MySQL・MariaDBへ変更する場合は、`AccountLoginStateRepository.reserve`の書き直しが必要(Javadocに明記)。
- テストの`@MockitoSpyBean`(`UserAccountLookupApi`)は、インタフェース型で、実装のBeanに対して機能した。
- Micrometerの観測(`ObservationRegistry`)は、観測ごとに、同名のタイマーを自動で登録し、低カーディナリティの属性(`unit`)と`error`(例外の型名)をタグにする。`AuthenticationNoLeakTest`は、このタグを許容している(値は、固定の分類・例外の型名だけ)。

## 実装した範囲

- **共通基盤の契約C15**: `com.mastersmith.common.security.Operator`・`OperatorContext`。実装は`auth.security.SecurityContextOperatorContext`。
- **U5本体**(`com.mastersmith.auth`): `entity`(`Session`・`AccountLoginState`・`SessionStatus`)、`repository`(条件付きの更新)、`token`(JWT発行・検証・リフレッシュトークン・sid・鍵)、`config`(`AuthProperties`・`AuthConfig`)、`service`(`LoginAttemptGate`・`SessionService`・`AuthenticationApplicationService`・`UserAccountClient`・`SessionContextService`・`SessionCleanupJob`・`SessionStartupRevoker`・`AuthExceptionTranslator`)、`cache`(`SessionCache`)、`security`(`BearerAuthenticationFilter`・`AuthSecurityConfig`・`AuthRequestSizeLimitFilter`・`ProblemDetailsWriter`・`SecurityHeaderValues`・`AuthRequestRules`・`AuthProblem`・`OperatorAuthentication`)、`web`(`AuthController`・`AuthApiExceptionAdvice`・`AuthCrossCuttingExceptionAdvice`)、`observation`(`AuthMetrics`・`AuthEventLogger`・`AuthObservations`)、`exception`、`dto`。
- **移行**: `backend/src/main/resources/db/migration/V5__create_authentication.sql`(`auth_session`・`account_login_state`。`user_id`のインデックスなし、`refresh_expires_at`のインデックスあり)。
- **設定**: `mastersmith.auth.*`(既定値はNFR Design)、`spring.datasource.hikari.connection-timeout: 3000`、`management.endpoints.web.exposure.include: health`・ヘルスの5秒キャッシュ・ヘルスのグループ(probes)の無効化。JWTの鍵は、リポジトリに置かない(環境変数`MASTERSMITH_AUTH_JWT_SECRET`。テスト用の`application.yml`に、テスト専用のダミーの鍵)。
- **他ユニットの変更**(既存のメソッド・振る舞い・アサーションは変えない):
  - U4: `UserAccountLookupApi`へ`findByUserId`・`dummyVerify`を追加(`PasswordHasher.dummyVerify`は`HashConcurrencyLimiter`を共有、129文字以上・null・空は計算しない)。`CurrentOperatorProvider`・`HeaderCurrentOperatorProvider`・`usermanagement.security.Operator`を削除し、`UserController`・`MePreferencesController`・`UserAuthorizer`が`OperatorContext`を読む形へ。`revokeRefreshTokensOnDisable`は、U4に実装がなかったため、契約からの削除の追補のみ。
  - U2・U6・U7: `ActiveRoleResolver`・`HeaderActiveRoleResolver`を削除し、`SchemaIntrospectionController`・`MenuController`・`AuditLogController`が`OperatorContext`を読む形へ。
  - U3: `PermissionEngineApiImpl.resolveEffectivePermission`が、`activeRoleId`のnull・空(空白のみを含む)を、NONE(fail closed)で返す。`canAccessScreen`のブートストラップ例外(`config-import-export`)は、`activeRoleId`にかかわらず適用される(実装の順序上、もともと先に評価される)。`PermissionEngineApi`のJavadocを追補した。
- **ドキュメント(Step 1)**: `inception/contract-design/contract-summary.md`へ、C4・C10・C11・C14の追補と、新しいC15(契約表・契約の節・Contract Ownership Rulesの例外)を追記した。C4の「FR2.7の文言の不一致は未解消」は、元の記述を残し、追補と、Open Questions表の「解消済み」の行で更新した。`inception/units-generation/unit-of-work-dependency.md`の統合ポイント表へC15の行を追記した。`construction/authentication-service/functional-design/functional-spec.md`・`rules.md`の末尾へ「Code Generation着手時の追補」を追加した(認証ライブラリ・パッケージ・設定キー・移行スクリプトの確定と、NFR Design保留10〜16・18〜20番の扱い)。

## 設計・計画とのズレ、判断の記録

1. **`Session`のJPAエンティティ名を`AuthSession`にした**(`@Entity(name = "AuthSession")`)。HQLで`org.hibernate.Session`との混同を避けるため。テーブル名は`auth_session`のまま。
2. **`ProblemDetailsWriter`・`AuthProblem`(計画にない部品)を、エラーの内容の1か所への集約のために追加した**。フィルタ(`ProblemDetailsWriter`)とコントローラ(`AuthApiExceptionAdvice`・`AuthCrossCuttingExceptionAdvice`)が、同じステータス・文言・`code`・`WWW-Authenticate`を使う。NFR2.8の表にない2つのコード(`auth.forbidden`: 認証の要否の規則による拒否の403、`auth.internal-error`: 分類できない例外の500)を加えた(契約の追補に記載)。
3. **`type`(`about:blank`)は、Spring MVC経由の応答では省略される**(RFC 9457では、省略は`about:blank`と同義)。フィルタが直接書く応答には、`"type":"about:blank"`を明示している。テストは、この差を許容している。
4. **`AuthCrossCuttingExceptionAdvice`は、依存を持たない**(`@Order(HIGHEST_PRECEDENCE + 10)`、対象の例外の型を3つに限定)。どの`@WebMvcTest`のスライスにも、追加の部品なしに読み込める。
5. **`AuthEventLogger.startupSettingRejected`は、呼び出し元がないため削除した**。起動時の鍵・設定の検証の失敗は、例外(設定のキー名と理由だけ。値を含めない)がSpring Bootの起動失敗として出力される。起動失敗の出力全体に鍵の値・断片が現れないことは、`JwtKeyProviderTest`が確認している。
6. **`GET /actuator/health`のグループ(liveness・readiness)を無効化する設定を加えた**(`management.endpoint.health.probes.enabled: false`)。Spring Bootは、既定で`groups`を応答に含めるため、「状態(UP・DOWN)だけを返す」(NFR2.1)を満たすために必要だった(`AuthSecurityConfigTest`で確認)。
7. **`revoke-all-on-startup`のテスト**: Beanの初期化の時点でSessionを作り、`WebServerInitializedEvent`の時点の件数(0件)を記録する方法で、Webサーバーの起動より前の実行を確認した(`RevokeAllOnStartupTest`。対照として、既定(false)では3件が残る`RevokeAllOnStartupDisabledTest`)。
8. **`UserAuthorizer`・`InvitationFacade`などのU4の入口**: アクティブロールがnullの操作者は、401ではなく、そのままC10へ渡し、403になる(機能設計BR5.12)。`Operator`のuserIdが常に解決済みになったため、`requireOperatorUserId`は、操作者そのものがnullの場合だけ401とする。既存のテストのうち、「アクティブロール未選択は401」を期待していたものは、この仕様変更(機能設計の追補8番・BR5.12)に合わせ、期待を403へ更新した(`InvitationFacadeTest`・`UserApplicationServiceTest`・`UserAuthorizerTest`・`UserApiIntegrationTest`)。「認可拒否のケースを削除・緩和」してはおらず、401(操作者が解決できない)と403(未選択・権限なし)の両方を維持している。
9. **U4のコントローラのテスト**: `UserControllerTest`・`MePreferencesControllerTest`・`UserApiExceptionAdviceTest`は、操作者を`TestOperatorContext`で渡し、操作者を解決できない場合は、サービスへ`null`が渡される形へ更新した(従来の`Operator.unresolved()`に相当)。`HeaderCurrentOperatorProviderTest`・`HeaderActiveRoleResolverTest`は、対象の削除に伴い削除した。
10. **`NoLeak`の検査の対象**: `AuthenticationNoLeakTest`は、実際のフィルタチェーン・実H2・実C11(Argon2id)で、ログイン(成功・誤り・未登録・ロック・503(上限超過(実際・ダミー)・DB障害))・ロール選択・リフレッシュ(成功・猶予内の再使用・猶予を超えた再使用・失効済み・不正)・認証フィルタの401(失効・トークンなし・不正・期限切れ)・ログアウトを流す。上限超過の例外のメッセージは、実際の固定の文言(`Password hash capacity exceeded`)を用いた(観測が例外のメッセージを記録するため、テストが独自の秘密の文字列を例外に入れる想定は、設計上、無い。DB障害の例外の詳細は、`AuthStorageUnavailableException`への変換で、連鎖の内側に閉じる)。

## 追加・拡充したテスト(主なもの)

- データモデル: `SessionJpaTest`・`AccountLoginStateJpaTest`・`SessionRepositoryTest`・`AccountLoginStateRepositoryTest`・`SessionRepositoryConcurrencyTest`(8スレッドの同時のローテーションで1件だけ成功)。
- 基盤: `AuthPropertiesTest`(既定値と18+の不正な設定の安全失敗)・`JwtKeyProviderTest`(未設定・不正・32バイト未満・環境変数からの読み取り・起動失敗の出力全体に鍵の値・断片がないこと)・`AccessTokenTest`(`alg: none`・HS256以外・署名・必須の値・期限切れ(リーウェイ0)のテーブル駆動)・`RefreshToken*`・`SessionIdGeneratorTest`・`ProblemDetailsWriterTest`・`SecurityHeaderValuesTest`。
- ビジネスロジック: `LoginAttemptGateTest`(同時40件で検証できる試行がしきい値を超えない、補償、自己修復、時計の境界)・`AuthenticationApplicationServiceTest`(失敗の一本化・`dummyVerify`・C11がトランザクションの外・順番待ちの間の使用中の接続が0・上限超過・DB障害・想定外の例外は補償しない・同時12件の初回)・`SessionServiceTest`(ロールの再確認の判定表の全8ケース・猶予の境界・同時のリフレッシュ・ログアウト・ロール選択)・`SessionRefreshRaceTest`(ロール選択との競合を決定的に再現)・`RefreshGraceZeroTest`・`LoginSuccessAtomicityTest`・`SessionCacheTest`(150回の更新と読み込みの競合のストレス)・`SessionContextServiceTest`・`SessionCleanupJobTest`・`RevokeAllOnStartup*Test`・`UserAccountClientTest`。
- API・セキュリティ: `BearerAuthenticationFilterTest`(49件、認証不要のパスで期限切れ・不正なヘッダーでも401にならない、原因を区別しない同一の401)・`AuthSecurityConfigTest`(実チェーンで、認証の要否の規則・ヘッダー・`Set-Cookie`なし・ヘルスの5秒キャッシュ・413の2経路のヘッダーが同じ)・`AuthControllerTest`・`AuthApiExceptionAdviceTest`・`AuthCrossCuttingExceptionAdviceTest`・`AuthRequestSizeLimitFilterTest`・`SecurityContextOperatorContextTest`。
- 他ユニット: `PermissionEngineApiImplTest`(activeRoleId(null・空・空白・実在しない・実在NONE・READ・FULL) × RBAC設定の有無 × 4つの画面のテーブル駆動、`lenient`のモック)・`PermissionEngineIntegrationTest`(実H2、ブートストラップ例外と、RBAC設定後の全画面での拒否)・`UserAccountLookupServiceTest`(`findByUserId`・`dummyVerify`)・`HeaderBasedOperatorAbsenceTest`(ヘッダー方式の不存在: コードからコメントを除いて検索、クラスがクラスパスにないこと、U2・U3・U4・U6・U7が`com.mastersmith.auth`をimportしないこと)。
- 非露出・統合: `AuthenticationNoLeakTest`(6件)・`AuthenticationFlowIntegrationTest`(ログイン → ロール選択 → 権限制御(許可・権限なし403・未選択403) → リフレッシュ → ログアウト後の401、複数端末、無効化後のリフレッシュ、期限切れのアクセストークンからの復旧、ロールの付け外し、ロックの流れ、他ユニットのコントローラが同じ`OperatorContext`で動くこと)。
- テスト支援: `common.security.TestOperatorContext`・`TestOperatorContextConfig`・`TestPermitAllSecurityConfig`、`auth.testsupport.MutableClock`・`AuthTestClockConfig`・`AuthIntegrationTestBase`・`AuthTestEndpoints`(検証用のコントローラ)ほか。

## 未実施・対象外(計画どおり)

- **NFR1.1・NFR1.2の応答時間の計測**(`performance-design.md`の計測の方法)は、Build and Testの結果に記録する事項として、本Boltでは実施していない(CIのマージ前の必須ゲートではない、と設計にある)。フィルタの処理時間は、`auth.filter.duration`(`hit`・`miss`)で計測できるようにしてある。
- フロントエンド(U12)・複数プロファイル横断E2E・共通基盤の観測の設定(メトリクスのエクスポート・トレース・構造化ログ・`RequestLogFilter`)・HSTS・SAST・シークレットスキャンのCIへの導入は、対象外(共通基盤・CI・環境の担当)。リポジトリに、JWTの鍵・認証情報の実値がないことは、検索(`application*.yml`と`src/main`のパスワード・秘密の代入)で確認した(テスト専用のダミーの鍵を除く)。業務固有のテーブル名・カラム名・業務ルールは、U5のコードにない。

## 作業の記録(注意点)

- 中断前の作業で、削除した5つのソースと2つのテストに、`git rm`を使った(インデックスに削除が記録されている)。他のgitの書き込み操作(`add`・`commit`・`push`など)と、`aidlc`コマンドは、使っていない。インデックスの状態が問題であれば、オーケストレーターが、コミット前に整えてほしい。
- `aidlc/spaces/default/intents/260910-master-mgmt-app/audit/`配下の変更は、フレームワークのフックによるもので、本作業では編集していない。
