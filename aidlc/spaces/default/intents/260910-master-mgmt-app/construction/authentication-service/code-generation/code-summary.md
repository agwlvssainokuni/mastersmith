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

# Code Summary — authentication-service (U5)

承認済み`code-generation-plan.md`(前提事項12件・全17ステップ)に基づき、authentication-serviceユニットを実装した。実装の途中で確認した事実(依存の座標・バージョン、Spring Boot 4.xでの型の配置、各Stepの判断・差異)は、同じディレクトリの`code-generation-notes.md`に詳しく記録している。

## 結果の概要

| 項目 | 結果 |
|---|---|
| U5のテスト(`com.mastersmith.auth.*`) | 379件、失敗0 |
| 全体のテスト(他ユニットを含む) | 1,322件、失敗0・エラー0・スキップ0(着手前は880件)。オーケストレーターが、全体のビルドとテストを再実行して確認 |
| 行カバレッジ | 全体92.6%(3,771 / 4,074行)、`com.mastersmith.auth`配下は95.3%(フロア80%を満たす。閾値は緩めていない)。分岐カバレッジは全体79.6%(参考。閾値の定めはない) |
| `spotlessCheck`・`checkstyleMain`・`checkstyleTest`・`jacocoTestCoverageVerification` | 合格 |
| 同時実行のテスト(実H2) | ロックの予約・補償・リフレッシュ・ロール選択との競合・キャッシュの無効化のすべてで、`CountDownLatch`により開始を揃え、固定の`sleep`に依存しない。同時40件の誤った試行で、検証できる試行がしきい値(5)を超えないことを確認 |
| `team.md`の追加合格条件(権限判定のテーブル駆動テスト) | C10の`activeRoleId`(null・空・空白・実在しない・実在NONE/READ/FULL) × RBAC設定の有無 × 4画面を、`PermissionEngineApiImplTest`(モック)と`PermissionEngineIntegrationTest`(実H2)に追加 |
| `team.md`の必須テスト種別 | (a)安全失敗: 鍵・設定の不正で起動が失敗し、出力に鍵の値・断片が現れないこと(`JwtKeyProviderTest`・`AuthPropertiesTest`)。(c)認可拒否: 未認証の401・保持しないロールの403・ロール未選択の403(`AuthControllerTest`・`AuthenticationFlowIntegrationTest`ほか)。(b)複数プロファイル横断E2Eは、計画どおり本Boltの対象外 |

## 作成・変更したファイル

### 契約・機能設計への追補(Step 1)

- `inception/contract-design/contract-summary.md`: C4・C10・C11・C14の追補と、新しい共有契約C15(契約表・契約の節・所有規則の例外)。C4の「FR2.7の文言の不一致は未解消」は、元の記述を残し、追補とOpen Questions表の「解消済み」の行で更新。
- `inception/units-generation/unit-of-work-dependency.md`: 統合ポイント表にC15(葉の共有契約)を追記。
- `construction/authentication-service/functional-design/functional-spec.md`・`rules.md`: 末尾に「Code Generation着手時の追補」(認証ライブラリ・パッケージ・設定キー・移行スクリプトの確定と、NFR Design保留10〜16・18〜20番の扱い)を追加。既存の記述は書き換えていない。

### ビルド・設定・移行(Step 2・4)

- `backend/build.gradle.kts`: `spring-boot-starter-security`、`spring-security-test`(テスト)、`com.nimbusds:nimbus-jose-jwt:10.10`。NimbusはSpring BootのBOMの管理対象外のため、最新の安定版を固定した。
- `backend/src/main/resources/application.yml`: `mastersmith.auth.*`(既定値はNFR Design)、`spring.datasource.hikari.connection-timeout: 3000`、`/actuator/health`のみの公開・結果の5秒キャッシュ・ヘルスのグループ(probes)の無効化。JWTの鍵の既定値・実値は置かない(環境変数`MASTERSMITH_AUTH_JWT_SECRET`。未設定なら起動失敗)。
- `backend/src/test/resources/application.yml`: テスト専用のダミーの鍵と短縮した設定値。
- `backend/src/main/resources/db/migration/V5__create_authentication.sql`: `auth_session`・`account_login_state`。

### 本体コード(`backend/src/main/java/com/mastersmith/`)

| 領域 | 主なファイル |
|---|---|
| 共通基盤の契約C15 | `common/security/Operator`・`OperatorContext`(実装は`auth/security/SecurityContextOperatorContext`) |
| C14 | `auth/SessionContextApi`、`auth/service/SessionContextService` |
| エンティティ・永続化 | `auth/entity/`(`Session`・`AccountLoginState`・`SessionStatus`)、`auth/repository/`(条件付きの更新) |
| トークン | `auth/token/`(`AccessTokenIssuer`・`AccessTokenVerifier`・`AccessTokenClaims`・`JwtKeyProvider`・`SecretKeyMaterial`・`RefreshTokenGenerator`・`RefreshTokenHasher`・`SessionIdGenerator`) |
| 設定 | `auth/config/`(`AuthProperties`・`AuthConfig`) |
| ビジネスロジック | `auth/service/`(`LoginAttemptGate`・`SessionService`・`AuthenticationApplicationService`・`UserAccountClient`・`SessionCleanupJob`・`SessionStartupRevoker`・`AuthExceptionTranslator`)、`auth/cache/SessionCache` |
| セキュリティ層 | `auth/security/`(`BearerAuthenticationFilter`・`AuthSecurityConfig`・`AuthRequestSizeLimitFilter`・`ProblemDetailsWriter`・`SecurityHeaderValues`・`AuthRequestRules`・`AuthProblem`・`OperatorAuthentication`) |
| API層 | `auth/web/`(`AuthController`・`AuthApiExceptionAdvice`・`AuthCrossCuttingExceptionAdvice`)、`auth/dto/`、`auth/exception/` |
| 可観測性 | `auth/observation/`(`AuthMetrics`・`AuthEventLogger`・`AuthObservations`・`UnauthorizedReason`) |

### 他ユニットの変更(既存のメソッド・振る舞いは変えない)

- **U4(user-management)**: C11に`findByUserId`・`dummyVerify`を追加(`dummyVerify`は`HashConcurrencyLimiter`を共有し、129文字以上・null・空は計算しない)。暫定の`CurrentOperatorProvider`・`HeaderCurrentOperatorProvider`・`usermanagement.security.Operator`を削除し、`UserController`・`MePreferencesController`・`UserAuthorizer`ほかが`OperatorContext`を読む実装に置き換えた。`revokeRefreshTokensOnDisable`はU4に実装がなかったため、契約からの削除の追補のみ。
- **U2・U6・U7**: `ActiveRoleResolver`・`HeaderActiveRoleResolver`を削除し、`SchemaIntrospectionController`・`MenuController`・`AuditLogController`が`OperatorContext`を読む実装に置き換えた。
- **U3(permission-engine)**: `resolveEffectivePermission`が`activeRoleId`のnull・空(空白のみを含む)をNONE(fail closed)で返す。`canAccessScreen`のブートストラップ例外(`config-import-export`)は`activeRoleId`にかかわらず適用される。

### テスト(`backend/src/test/java/com/mastersmith/`)

- U5: `auth/`配下に379件(データモデル・基盤・ビジネスロジック・API/セキュリティ・非露出・統合。詳細は`code-generation-notes.md`)。
- 他ユニットの既存テスト(`UserControllerTest`・`MePreferencesControllerTest`・`UserApiExceptionAdviceTest`・`UserApiIntegrationTest`・`UserManagementNoLeakTest`・`SchemaIntrospectionControllerTest`・`MenuControllerTest`・`AuditLogControllerTest`ほか)を、ヘッダーで操作者を渡す形から`OperatorContext`を差し替える形へ更新した。`HeaderActiveRoleResolverTest`・`HeaderCurrentOperatorProviderTest`は、対象の削除に伴い削除した。
- ヘッダー方式の不存在は、アーキテクチャテスト`HeaderBasedOperatorAbsenceTest`で確認している。

## 主な実装判断(計画・設計との差異)

1. **`Session`のJPAエンティティ名は`AuthSession`**(`@Entity(name = "AuthSession")`)。HQLで`org.hibernate.Session`と混同しないため。テーブル名は`auth_session`のまま。
2. **`ProblemDetailsWriter`・`AuthProblem`を追加した**(計画にない部品)。フィルタとコントローラの応答(ステータス・文言・`code`・`WWW-Authenticate`)を1か所に集約するため。NFR2.8の表にない2つのコード(`auth.forbidden`(403)・`auth.internal-error`(500))を追加し、契約の追補に記載した。
3. **`/actuator/health`が状態だけを返すよう、ヘルスのグループを無効化した**(`management.endpoint.health.probes.enabled: false`)。NFR2.1の「状態(UP・DOWN)だけを返す」を満たすために必要だった。
4. **アクティブロール未選択(null)は、U4の入口でも401にせず、そのままC10へ渡して403にする**(機能設計BR5.12)。「未選択は401」を期待していた既存の4つのテスト(`InvitationFacadeTest`・`UserApplicationServiceTest`・`UserAuthorizerTest`・`UserApiIntegrationTest`)の期待を403へ更新した。401(操作者を解決できない)と403(未選択・権限なし)の両方を維持しており、認可拒否のケースの削除・緩和はしていない。
5. **`AuthEventLogger.startupSettingRejected`は呼び出し元がないため削除した**。起動時の鍵・設定の不備は、設定のキー名と理由だけを持つ例外として、起動失敗で出力される(値・断片が出力全体に現れないことをテストで確認)。
6. **`@SpringBootTest`の既存の統合テストは、認証を通さないテスト用のチェーン(`TestPermitAllSecurityConfig`)と`TestOperatorContext`へ切り替えた**。`@WebMvcTest`のスライスは、Spring Securityの追加で壊れなかった。
7. **H2の`UPDATE`のSET句が更新前の値で評価される動作に、ロックの予約の更新が依存している**(標準のSQLの動作)。MySQL・MariaDBへ内部設定DBを変更する場合は、`AccountLoginStateRepository.reserve`の書き直しが必要(Javadocに明記)。

## 未実施・持ち越し(計画どおり)

- **NFR1.1・NFR1.2の応答時間の計測**: `performance-design.md`の計測の方法は、Build and Testの結果に記録する事項として、本Boltでは実施していない。フィルタの処理時間は`auth.filter.duration`(`hit`・`miss`)で計測できる。
- **リフレッシュの再送の猶予(既定10秒。機能設計のQ5=Aとの差異)**: `mastersmith.auth.refresh-reuse-grace`(0で元の挙動。`RefreshGraceZeroTest`で確認)で実装済み。最終確認は、機能設計ステージの承認ゲートで人間が行う。
- **対象外**: フロントエンド(U12)・複数プロファイル横断E2E・共通基盤の観測の設定(メトリクスのエクスポート・トレース・構造化ログ・`RequestLogFilter`)・HSTS・SAST・シークレットスキャンのCIへの導入。リポジトリにJWTの鍵・認証情報の実値がないこと(テスト専用のダミーを除く)を、検索で確認した。
