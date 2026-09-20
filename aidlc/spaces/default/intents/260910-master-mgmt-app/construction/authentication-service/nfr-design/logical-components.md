# Logical Components — authentication-service (U5)

authentication-serviceを構成する、論理的な部品の一覧と、非機能の設計(NFR Design)が、どの部品に適用されるかを示す。本プロジェクトは、単一の実行可能WAR(1プロセス)であり、AWSなどのクラウドのインフラは設計の対象外である(Infrastructure Designの対象外)。そのため、ここでのコンポーネントは、プロセス内の論理的な部品である。

## コンポーネント一覧

| 部品 | 役割 | 適用するNFRの設計 |
|---|---|---|
| `AuthController` | `/api/auth/login`・`/api/auth/refresh`・`/api/auth/logout`・`/api/auth/active-role`のREST(C4) | NFR2.4・NFR2.8 |
| `AuthenticationApplicationService` | ログイン・リフレッシュ・ログアウト・ロール選択の流れ。外側のトランザクションを作らず、C11をトランザクションの外で呼び、内側の短いトランザクションを`TransactionTemplate`で区切る | NFR1.3・NFR4.1 |
| `LoginAttemptGate` | 予約・補償・成功の更新(`AccountLoginState`)。時刻は`Clock`から | NFR2.4・NFR4.1・NFR4.6 |
| `SessionService` | Sessionの作成・ローテーション・失効・ロール選択。コミット後の`SessionCache`の無効化 | NFR2.3・NFR2.5・NFR4.1・NFR4.3 |
| `SessionRepository` / `AccountLoginStateRepository` | 内部設定DBへの永続化(インデックス付きテーブル)。障害を`AuthStorageUnavailableException`に変換 | NFR3.4・NFR4.2 |
| `SessionCache` | Caffeine(最大1,000件、書き込みから60秒のTTL、統計)。キーごとの原子的な読み込みと、コミット後の無効化 | NFR1.2・NFR3.2・NFR4.3 |
| `AccessTokenIssuer` / `AccessTokenVerifier` | JWT(HS256)の署名と検証(Nimbus JOSE + JWT)。`alg`の固定、時計のずれの許容0 | NFR2.2 |
| `JwtKeyProvider` / `SecretKeyMaterial` | 鍵のBase64のデコードと検証(起動時)。`toString`が伏せ字 | NFR2.2・NFR4.4 |
| `RefreshTokenGenerator` / `RefreshTokenHasher` | 256ビットの乱数(`SecureRandom`、Base64URL)の生成と、SHA-256のハッシュ(Base64URL) | NFR2.3 |
| `BearerAuthenticationFilter` | 認証フィルタ。署名・有効期限・Session・`sub`の一致を確認し、`Operator`をセキュリティコンテキストに設定。401・503 | NFR1.2・NFR2.1・NFR2.2 |
| `SecurityContextOperatorContext` | C15の`OperatorContext`の実装(セキュリティコンテキストから読む、読み取り専用) | NFR2.1 |
| `SessionContextService` | C14の`SessionContextApi.getActiveRoleId`の実装(例外・nullの扱いは、BR5.13) | NFR2.5 |
| `AuthSecurityConfig` | アプリケーション全体の`SecurityFilterChain`(認証の要否の規則、セキュリティヘッダー、CSRF無効・ステートレス、CORSなし)。Q2=A | NFR2.1・NFR2.9 |
| `AuthRequestSizeLimitFilter` | `/api/auth/**`のリクエストボディの64KiB上限(413)。認証フィルタより前 | NFR2.10 |
| `ProblemDetailsWriter` / `AuthApiExceptionAdvice` | フィルタでの応答と、コントローラの例外を、RFC 9457のProblemDetails(401・403・413・503・400)に変換。`code`はi18nキー | NFR2.8・NFR4.2・NFR7.1 |
| `AuthProperties` | `application.yml`の設定の束縛と検証(起動時のfail fast) | NFR4.4 |
| `SessionCleanupJob` | 期限切れ・失効したSessionの定期削除(`@Scheduled`、1,000行ずつ) | NFR4.5 |
| `AuthMetrics` / `AuthEventLogger` | メトリクス(Micrometer)と、認証の出来事のログの窓口 | NFR2.7・NFR5.1・NFR5.2 |
| `Clock`(UTC) | 時刻の唯一の供給元。テストで差し替える | NFR4.6 |

## 外部との境界

| 相手 | 方向 | 契約 | 同期・非同期 |
|---|---|---|---|
| UserManagement(U4) | authentication-service → user-management | C11(`findByEmail`・`findByUserId`・`verifyPasswordHash`・`dummyVerify`・`isDisabled`) | 同期(同一プロセス、トランザクションの外) |
| list-engine(U10)・record-edit-engine(U11) | list-engine・record-edit-engine → authentication-service | C14(`getActiveRoleId`) | 同期(同一プロセス) |
| schema-introspector(U2)・user-management(U4)・menu-navigation(U6)・audit-logging(U7) | 各ユニット → 中立の共有契約 | C15(`Operator`・`OperatorContext`、読み取りのみ。authentication-serviceのコンポーネントを呼ばない) | 同期(同一プロセス) |
| フロントエンド(U12) | ブラウザ → authentication-service | C4(REST) | 同期(HTTP) |
| 内部設定DB | authentication-service → DB | Sessionと`AccountLoginState` | 同期(短いトランザクション) |

## 障害の領域と影響の範囲(failure domain)

| 障害の領域 | 影響を受ける機能 | 影響を受けない機能 |
|---|---|---|
| 内部設定DB | 認証フィルタ(キャッシュミス時)・ログイン・リフレッシュ・ログアウト・ロール選択(503)。ただし、キャッシュに有効なSessionがあるリクエストは、認証フィルタを通る | なし(アプリケーション全体の障害。認証を必要とする他のAPIも、DBを必要とする処理は失敗する) |
| ハッシュ計算の許可枠(user-managementの`HashConcurrencyLimiter`、共有) | ログイン(実際・ダミーの検証)の503。招待受諾・初期管理者の作成も共有 | リフレッシュ・ログアウト・ロール選択・認証フィルタ・その他のAPI(ハッシュ計算の許可を使わない) |
| `SessionCache` | 認証フィルタの処理時間(ミスが増えるとDBを引く。NFR1.2のミス時の目標) | 認証の判定の正しさ(キャッシュはDBの写しで、失われても、DBから読み直す) |
| `SessionCleanupJob` | 期限切れのSessionの行が、削除されるまで残る | 認証のすべて |
| `AccountLoginState` | ロックの判定 | ログイン以外のすべて |

## 共有するリソース

- 内部設定DBの接続プール(HikariCP、他ユニットと共有)。認証は、短いトランザクションだけで使い、ハッシュ計算の順番待ちの間は、接続を保持しない(reliability-design.md NFR4.1)。認証フィルタのミス時の読み込みは、リクエストごとに1回、短時間だけ接続を使う。
- ハッシュ計算の許可枠(user-managementの`HashConcurrencyLimiter`)。
- JVMヒープ: Sessionのキャッシュは、1MiB未満で、無視できる(scalability-design.md NFR3.2)。

## 横断的な部品の所有

user-managementのNFR Designは、次の部品を「共通基盤が所有」とし、担い手を未確定としていた。NFR Design Q2=Aにより、次のとおり、authentication-serviceが担う。

| 部品 | 所有者 | 備考 |
|---|---|---|
| `SecurityFilterChain`(認証の要否の規則、セキュリティヘッダー、ステートレス・CSRF無効) | authentication-service(U5) | Q2=A。`Referrer-Policy`・`nosniff`・`Content-Security-Policy`・`/api/**`の`Cache-Control: no-store`を実装する。user-managementの要求(`Referrer-Policy: no-referrer`を全レスポンスに付与すること)を満たす |
| `BearerAuthenticationFilter`・`SecurityContextOperatorContext` | authentication-service(U5) | C15の値を設定する側(提供側の実装) |
| `Operator`・`OperatorContext`(C15) | 共通基盤の契約(shared kernel) | 型と読み取りインタフェースだけ。authentication-serviceの業務ロジックに依存しない。変更には、authentication-serviceと、すべての読み取り側のユニットの合意を要する(機能設計の追補9番) |
| `AuthRequestSizeLimitFilter` | authentication-service(U5、`/api/auth/**`に限定) | user-managementの`RequestSizeLimitFilter`と同じ方式。認証フィルタより前に置く |
| `AuthApiExceptionAdvice` | authentication-service(U5、`AuthController`に限定、`@Order`を明記) | 共通基盤の基底のハンドラと衝突しないよう、対象を限定する |
| 汎用の例外ハンドラの基底・観測の規約・リクエストログ | 共通基盤 | U5は、これらに依存する。`/api/auth/*`は、パスに認証情報を含まない |

**フィルタの順序**: サイズ制限のフィルタ(`AuthRequestSizeLimitFilter`と、user-managementの`RequestSizeLimitFilter`)は、サーブレットフィルタとして、Spring Securityのフィルタチェーンより前の順序で登録する(`FilterRegistrationBean`。順序の値は、Spring Bootの`SecurityProperties.DEFAULT_FILTER_ORDER`より小さい値)。これにより、認証前の大きなボディを読み込まず、U5の設定が、U4のフィルタの型に依存しない。

## 共通基盤・他ユニットへの要求と契約追補(保留)

NFR Designで判明した、共通基盤・他ユニット・契約への要求を、次のとおり記録する。いずれも、Code Generationの計画承認までに、対象の設計に反映するか、ユーザーに確認する保留事項である(番号は、`nfr-requirements/tech-stack-decisions.md`の「契約追補(保留事項の一覧)」の1〜9の続きである)。

| 番号 | 対象 | 内容 | 出典 |
|---|---|---|---|
| 10 | 共通基盤の契約(C15) | `Operator`(userId・sessionId・activeRoleId)と`OperatorContext`を、共通基盤の契約(shared kernel)として置くパッケージ(たとえば`com.mastersmith.common.security`)の確定。`contract-summary.md`の契約表・所有規則と、`unit-of-work-dependency.md`の統合ポイント表への追補 | 機能設計の追補9番、security-design.md NFR2.1 |
| 11 | schema-introspector(U2)・user-management(U4)・menu-navigation(U6)・audit-logging(U7) | 暫定の操作者取得(ヘッダー方式)を削除し、`OperatorContext`を読む実装に置き換える。コントローラ・サービスの入口は変えない。既存のテストを、`OperatorContext`を差し替える形に更新する | 機能設計W7・追補8番 |
| 12 | permission-engine(U3)のC10 | `canAccessScreen`・`resolveEffectivePermission`が、`activeRoleId`がnullまたは空のとき、fail closed(NONE)で判定し、RBAC設定が空の間の`config-import-export`の例外は、`activeRoleId`にかかわらず適用する | 機能設計の追補6番、tech-stack-decisions.md NFR8.2 |
| 13 | user-management(U4)のC11 | `findByUserId`・`dummyVerify`の追加(`dummyVerify`は129文字以上を計算しない)、`revokeRefreshTokensOnDisable`の削除、`HashCapacityExceededException`の型の公開 | 機能設計の追補4番・11番、tech-stack-decisions.md 追補4番 |
| 14 | 共通基盤(内部設定DBの接続プール) | 接続の取得のタイムアウトを3秒以内とすること(認証は、内部設定DBの障害を、3秒以内に503にする)。最大サイズは、user-managementの要求(通常の同時処理数50に、招待の上限5を加えた値以上)に、認証フィルタのミス時の読み込みの分を、余裕として見込むこと | reliability-design.md NFR4.2、user-managementの追補13番 |
| 15 | 共通基盤(観測) | メトリクスのエクスポートの形式(PrometheusかOTLPか)とトレースの実装の確定。リクエストヘッダー・ボディを、ログ・トレースの属性に収集しない設定にすること。プル型のメトリクスのエンドポイント(Prometheus形式など)を採用する場合は、認証を要するか、ネットワークで制限すること。actuatorは`/actuator/health`だけを公開すること | observability-design.md NFR5.1・NFR5.3、security-design.md NFR2.1・NFR2.7 |
| 16 | 共通基盤・他ユニット(Flyway) | 内部設定DBの移行スクリプトの、名前・版番号の採番規則の共有。U5は`auth_session`・`account_login_state`のテーブルを所有する | scalability-design.md NFR3.4 |
| 17 | frontend-ui(U12) | (a)401でリフレッシュして元のリクエストを再試行する。(b)503を認証の失敗と区別し、セッションを終了せず再試行を促す。(c)`code`(i18nキー)から表示する文言を選ぶ(`auth.login.failed`・`auth.token.invalid`・`auth.refresh.rejected`・`auth.role.not-held`・`auth.service.unavailable`・`auth.request.too-large`・`auth.request.malformed`)。(d)初期の`Content-Security-Policy`(同じオリジンのみ)で動くこと。必要な緩和は、U5へ申し出る。(e)SPAのルート(`/login`・`/invitations/accept`など)は、静的ファイルとして、認証なしで返される | security-design.md NFR2.8・NFR2.9・NFR2.11 |
| 18 | 契約 C4・認証フィルタ | 503・413・400の追加、`Cache-Control: no-store`、`WWW-Authenticate: Bearer`(401)、ProblemDetailsの拡張メンバー`code`(i18nキー)。`nfr-requirements/tech-stack-decisions.md`の追補1〜3・7に統合して反映する | security-design.md NFR2.8・NFR2.9 |
| 19 | 運用の手順(環境側の前提) | `auth.session.revoke-all-on-startup`による全Sessionの削除の手順(バックアップからの復元後・鍵の漏えいの疑いのとき)。設定を戻すことを含める。運用フェーズが本MVPスコープ外のため、担当が定まるまで、環境側の前提として記録する | security-design.md NFR2.5、reliability-design.md NFR4.7 |

## NFR7.1: 認証エラーの文言のi18n

- 認証のエラー(401・403・413・503・400)は、ProblemDetailsの拡張メンバー`code`に、安定したi18nキーを入れる(security-design.md NFR2.8)。文言への変換(翻訳)は、フロントエンド(U12)が、config-engineが管理する翻訳リソースを用いて行う([assumption]、`nfr-requirements/tech-stack-decisions.md` NFR7.1)。
- ログイン失敗のキー(`auth.login.failed`)は、原因を区別しない、単一のキーである(BR5.2)。
- 入力値そのもの(パスワード・トークン)は、エラーに含めない(security-design.md NFR2.7)。
- キーの命名の確定と、契約への反映は、追補の一覧の7番・17番・18番に記録した。

## NFR8.1: 保守性(業務固有情報のハードコード禁止)

- 上記のコンポーネントは、Session・AccountLoginState・AccessTokenClaims・Operatorのみを扱い、業務固有のテーブル名・カラム名・業務ルールを持たない。
- ロックのしきい値・ロック時間・トークンの有効期限・再送の猶予・削除の保持日数・キャッシュの最大件数とTTLは、`application.yml`(鍵は環境変数から注入)に置き、コードに埋め込まない。
- `Operator`と`OperatorContext`(C15)は、authentication-serviceの業務ロジックに依存しない、中立の共有契約とする。

## NFR8.2: テストの設計

`nfr-requirements/tech-stack-decisions.md` NFR8.2のテストを、次の部品ごとの単位で用意する。

| 部品 | 主なテスト |
|---|---|
| `LoginAttemptGate` | 同時の誤った試行が何件あっても、検証できる試行がしきい値を超えないこと。しきい値に達する予約と同時にロックが有効になること。ロックの自動解除のあとの最初の予約で回数が0に戻ること。成功でしきい値に達した試行でもロックが解けること。補償の更新(世代が同じ場合だけ枠を返す)。自己修復(しきい値以上で`locked_until`が空)。時計を差し替えて境界を確認する(NFR4.6) |
| `AuthenticationApplicationService` | 失敗の応答が、原因(未登録・パスワードの誤り・ロック中・無効化済み・招待中)にかかわらず同一であること。実際の検証を行わない場合に`dummyVerify`が呼ばれること。C11をトランザクションの外で呼ぶこと。ハッシュ計算の上限超過が実際・ダミーのどちらでも503になること。成功の更新とSessionの作成が一体で反映されること。DB障害で503になり、補償が試みられること |
| `SessionService`・`SessionRepository` | ローテーションの条件付きの更新(同時の更新で1件のみ成功、負けた側はSessionを失効させない)。猶予内・猶予を超えた再使用。ログアウトが該当のSessionだけを失効させること。ロール選択(保持しないロールは403)。リフレッシュ時のロールの再確認(判定表の全ケース)。失効の冪等 |
| `SessionCache` | 更新のコミット後に無効化されること。読み込みと無効化の競合(ストレステスト)で古い値が残らないこと。TTL(60秒)で解消すること。存在しないSessionを保持しないこと。ヒット・ミスの統計 |
| `BearerAuthenticationFilter`・`AccessTokenVerifier` | トークンなし・`alg: none`・`alg`がHS256以外・署名の不正・必須の値の欠落・期限切れ(時計のずれの許容が0であること)・`sub`とSessionの`userId`の不一致・失効・期限切れのSessionが、いずれも同一の401になること。キャッシュミスでDB障害のとき503、キャッシュヒットのとき通ること。リクエストヘッダー(`X-User-Id`・`X-Active-Role-Id`)が無視されること |
| `JwtKeyProvider`・`AuthProperties` | 鍵の未設定・Base64として不正・32バイト未満、有効期限・しきい値・ロック時間・削除の設定の不正で、起動が失敗すること。エラーのメッセージに鍵の値が含まれないこと(安全失敗のテスト) |
| `AuthSecurityConfig` | 認証の要否の規則(3つの認証不要のAPI・静的ファイル・`/actuator/health`・その他のactuatorの拒否・`/api/**`の残りは認証必須)。セキュリティヘッダーの値。`Cache-Control: no-store`が`/api/**`にだけ付くこと。すべての応答に`Set-Cookie`がないこと(ステートレス) |
| `AuthRequestSizeLimitFilter`・`AuthApiExceptionAdvice` | `Content-Length`がある場合・チャンク転送の場合の413。認証前のボディの拒否。ProblemDetailsの形式と`code` |
| `SessionCleanupJob` | 有効期限から保持日数を過ぎたSession(revokedを含む)だけが削除され、有効なSessionは削除されないこと。1,000行ずつ複数回に分けて削除されること。失敗が認証に影響しないこと。`revoke-all-on-startup` |
| `SessionContextService`(C14) | Sessionの不存在・有効でない場合の例外、未選択のnullの返却(契約テスト) |
| `SecurityContextOperatorContext`(C15) | 認証済みのリクエストで`Operator`を返すこと。`activeRoleId`がnullでも`Operator`が存在すること(契約テスト) |
| 認証情報の非出力(横断) | パスワード・トークン(平文・ハッシュ)・鍵が、ログ・スパンの属性・メトリクスのタグ・応答に現れないこと |
| 統合・E2E | ログイン → ロール選択(複数ロール)→ 一覧・編集での権限制御 → ログアウトの一連の流れ。複数の業務ドメインの設定プロファイルで、同じシナリオが動くこと(シナリオの実装はfrontend-ui(U12)・統合の担当) |
| 他ユニットの置き換え(横断) | ヘッダー方式の暫定実装が残っていないこと(コードの検索・アーキテクチャテスト)。C10のテーブル駆動テスト(`activeRoleId`のnull・空・実在×RBAC設定の有無×画面の組み合わせ) |

80%行カバレッジは、CIでのマージ前に確認する(team.md)。

## 根拠

- 要件: `nfr-requirements/`の各成果物(`performance-requirements.md`・`security-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`・`tech-stack-decisions.md`)、`inception/requirements-analysis/requirements.md`のNFR6〜NFR8
- 機能設計: `functional-design/functional-spec.md`(W1〜W7、追補一覧)、`rules.md`
- 契約: `inception/contract-design/contract-summary.md`のC4・C10・C11・C14
- 質問回答: `nfr-design-questions.md` Q1〜Q3
