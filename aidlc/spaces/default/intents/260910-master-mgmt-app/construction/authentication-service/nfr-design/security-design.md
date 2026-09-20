# Security Design — authentication-service (U5)

`nfr-requirements/security-requirements.md`の各要件を、設計に落とし込む。authentication-serviceは、アプリケーション全体の認証の入口であり、アクセストークンの署名の鍵・リフレッシュトークン・ログイン失敗の記録を扱う。Q2=Aにより、アプリケーション全体の`SecurityFilterChain`(セキュリティヘッダーを含む)も所有する。

## 全体の構成

```
リクエスト
  → AuthRequestSizeLimitFilter(/api/auth/**、413)
  → セキュリティヘッダー(Referrer-Policy・nosniff・CSP・Cache-Control)
  → BearerAuthenticationFilter(認証を要するパス(NFR2.1の順2)にだけ適用。署名・有効期限・Session・sub一致 → Operatorを設定、401・503)
  → 認可の規則(SecurityFilterChainのmatcher。認証の要否だけ。権限判定は各ユニットが行う)
  → 各ユニットのController → 入口でcanAccessScreen(C10)の再検証

ログイン/リフレッシュ/ログアウト/ロール選択 → AuthController → AuthenticationApplicationService
                                                    ├→ UserAccountLookupApi(C11)
                                                    ├→ LoginAttemptGate / SessionService(内部設定DB)
                                                    └→ AccessTokenIssuer / RefreshTokenGenerator
```

- 認証フィルタは、認証(操作者が誰か、失敗は401)だけを担う。権限判定(失敗は403)は、従来どおり、各ユニットが、入口で、C10を呼んで行う(project.md Mandated)。

## NFR2.1: 認証の適用範囲と、認証・認可の分担

`SecurityFilterChain`の規則(上から順に評価し、最初に一致したものを適用する)。

| 順 | 対象 | 規則 |
|---|---|---|
| 1 | `POST /api/auth/login`・`POST /api/auth/refresh`・`POST /api/users/invitations/*/accept` | 認証を要しない(BR5.11の3つだけ) |
| 2 | `/api/**` の残りすべて | 認証を要する(Bearerトークン、`BearerAuthenticationFilter`を通す) |
| 3 | `GET /actuator/health`(完全一致のみ) | 認証を要しない。応答は状態(UP・DOWN)だけで、詳細を含めない。ヘルスのグループ(`/actuator/health/**`、livenessなど)は設けない。DBのチェックの結果は、5秒間キャッシュする(`management.endpoint.health.cache.time-to-live`。認証なしの呼び出しが、内部設定DBの接続を、5秒に1回を超えて使わないようにする) |
| 4 | `/actuator/**` の残り(すべてのメソッド) | 拒否する(公開しない。ヘルスチェック以外のactuatorのエンドポイントは、公開設定にも含めない。プル型のメトリクスのエンドポイントを採用する場合は、この規則より前に、認証を要する規則として、明示的に追加する) |
| 5 | `/api/**`・`/actuator/**`以外のパスへの、`GET`・`HEAD`(フロントエンドの静的ファイル、SPAのルートのフォールバック) | 認証を要しない(業務データ・個人情報を含まない静的な成果物のため) |
| 6 | 上記のいずれにも一致しないすべてのリクエスト(`/api/**`・`/actuator/**`以外への、`GET`・`HEAD`以外のメソッドなど) | 拒否する |

- 順2と順6により、`/api/**`のうち、順1に列挙した3つ以外の、新しく追加されるAPIは、既定で認証を要し、`/api`の外にマップされる、意図しないコントローラや、H2コンソールなどの開発用の管理機能も、認証なしでは公開されない(deny by default)。H2コンソールなどは、本番相当のプロファイルで無効にする(既定で無効。共通基盤への要求)。
- **フィルタの適用範囲**: `BearerAuthenticationFilter`は、順2に該当するパス(`/api/**`から、順1の3つを除いたもの)にだけ適用する(`shouldNotFilter`で、順1・3・4・5・6のパスでは、フィルタを実行しない)。順1・3・5のパスでは、`Authorization`ヘッダーの有無・内容(期限切れ・不正なトークンを含む)にかかわらず、フィルタは何も検証せず、素通しにする。これにより、期限切れのアクセストークンを付けたまま、`POST /api/auth/refresh`・`POST /api/auth/login`・招待受諾を呼んでも、フィルタでは401にならず、NFR2.2・NFR2.11が前提とする「リフレッシュで復旧する」経路が保たれる。
- **操作者の受け渡し**: `BearerAuthenticationFilter`が、`Operator`(userId・sessionId・activeRoleId)を、リクエストのセキュリティコンテキストに設定する。他ユニットは、`OperatorContext`(C15、読み取り専用)だけを読む。`OperatorContext`の実装(`SecurityContextOperatorContext`)は、セキュリティコンテキストから読む。ヘッダー(`X-User-Id`・`X-Active-Role-Id`)を読む実装は、どのプロファイルにも残さない(機能設計W7・Q9=A)。
- **アクティブロールが未選択(null)**: 認証は成功し、`Operator.activeRoleId`はnullのまま、各ユニットへ渡る。各ユニットは、nullを、自前で拒否せず(401にせず)、C10へそのまま渡し、権限なし(403)として判定させる(BR5.12)。
- 認証フィルタの結果が、認可の根拠にならないことの担保として、各ユニットのコントローラ・サービスの入口の、`canAccessScreen`の呼び出しは、変更しない。

## NFR2.2: アクセストークン(JWT)の署名と検証

- **実装**: Nimbus JOSE + JWTを、自前の`BearerAuthenticationFilter`から直接使う(Spring SecurityのOAuth2 Resource Serverの`JwtDecoder`は使わない)。理由は、(1)トークンの検証と、Sessionの確認(`sid`)と、`sub`の一致の確認を、1つのフィルタの中で、一貫した順序で行うため、(2)内部設定DBの障害を、401ではなく503にするため(NFR4.2)、である。NFR Requirementsの確認事項3への、設計としての回答である([assumption]。Code Generationの計画承認で確認する)。
- **検証の手順**(認証を要するパス(NFR2.1の順2)にだけ適用する。いずれかに失敗した時点で、401。応答は、原因を区別しない、NFR2.8):
  1. `Authorization`が、`Bearer `で始まる(スキーム名は、大文字小文字を区別しない、RFC 7235)。なければ`missing`。`Authorization`ヘッダーが複数ある場合も、`missing`とする。
  2. トークンを解析できる(JWSのコンパクト形式)。できなければ`invalid`。
  3. ヘッダーの`alg`が`HS256`である。`none`・`HS384`・`HS512`・`RS256`などは、署名の検証に進まず、`invalid`とする。
  4. HMAC-SHA256の署名が、注入された鍵で検証できる。できなければ`invalid`。
  5. `sub`・`sid`・`exp`が存在し、文字列・日時として妥当である。欠けていれば`invalid`。
  6. `exp`が、現在時刻(注入された`Clock`)より後である。**時計のずれの許容(リーウェイ)は0**とする(Spring SecurityのJWTの検証は、既定で60秒のずれを許容するが、本設計では、Nimbusを直接使うため、許容は入らない。0であることを、テストで確認する)。過ぎていれば`expired`。
  7. `sid`のSessionを、`SessionCache`から引く。存在しない・有効でない(statusがactiveでない、または`refreshExpiresAt`が現在時刻以前)なら`session_inactive`。
  8. トークンの`sub`が、Sessionの`userId`と一致する。一致しなければ`invalid`(NFR Requirementsで追加した確認。鍵が漏えいしても、攻撃者は、有効な`sid`を知らなければ、他のユーザーになりすませない)。
- `reason`(`missing`・`invalid`・`expired`・`session_inactive`)は、メトリクスのタグと、DEBUGログの分類にだけ用い、応答には出さない(observability-design.md NFR5.1)。NFR Requirementsの3分類に、`missing`(ヘッダーなし・Bearerでない)を加えた。走査や、認証なしの呼び出しと、トークン自体の不備(`invalid`)を、アラートで区別するためである([assumption])。
- **鍵の外部表現**: `auth.jwt.secret`(設定のキーの名称はCode Generationで確定)は、**標準のBase64**で表した文字列とし、デコード後が**32バイト(256ビット)以上**であること。デコードできない、または32バイト未満の場合は、起動時に失敗させる(reliability-design.md NFR4.4)。Nimbusの`MACSigner`・`MACVerifier`に、デコード後のバイト列を渡す。
- **鍵を漏らさない**: 鍵は、専用の型(`SecretKeyMaterial`。`toString`が固定の伏せ字を返し、`equals`・`hashCode`にも値を使わない)に保持する。鍵は、`AuthProperties`(`@ConfigurationProperties`の束縛)には**含めない**。Spring Bootの束縛の失敗の診断は、`Value:`として、元の値を表示する(Bean Validationの`Rejected value`も同様)ため、鍵を束縛の対象にしない。`JwtKeyProvider`が、`Environment`から、キー`auth.jwt.secret`の値を直接読み、Base64のデコードと長さの検証を、自前で行う。失敗の例外(`IllegalStateException`)のメッセージには、設定のキーの名前と理由(未設定・形式不正・長さ不足)だけを含め、値の断片・デコードの例外のメッセージ(値を含みうる)を含めない(例外の原因として連鎖させない)。
- **鍵の入れ替え**: 鍵を差し替えて再起動する。Sessionは失効しないため、発行済みのアクセストークンが1回使えなくなるだけで、フロントエンドが、リフレッシュ(または再ログイン)で復旧する。無停止の入れ替え(`kid`による旧鍵・新鍵の併存)は行わない(Q1=A)。
- 発行: ログイン成功時とリフレッシュ成功時に、`sub`・`sid`・`iat`・`exp`だけを持つトークンを、HS256で署名する。`exp`は、発行時刻に、設定のアクセストークンの有効期限を加えた値とする。

## NFR2.3: リフレッシュトークンの生成・保存・ローテーション

- **生成**: `RefreshTokenGenerator`が、`SecureRandom`で32バイトを生成し、Base64URL(パディングなし、43文字)にする。
- **sessionId(`sid`)の生成**: `SessionIdGenerator`が、`SecureRandom`で16バイト(128ビット)を生成し、Base64URL(パディングなし、22文字)にする。`sid`の推測不能性は、NFR2.2の手順8(`sub`とSessionの`userId`の照合)と、NFR Requirementsの残余リスク9(有効な`sid`を知らない攻撃者は、鍵が漏えいしても、なりすませない)の根拠であり、`refresh_token_hash`と同様に、暗号論的乱数から作る。
- **保存**: `RefreshTokenHasher`が、トークンの文字列(UTF-8)の、SHA-256を求め、Base64URL(43文字)で保存する。列は、`refresh_token_hash`と`previous_refresh_token_hash`(いずれも、一意なインデックスを持つ)。平文は、応答にだけ含め、保存・ログ・トレースに出さない。
- **検索**: 提出された値を、同じ方法でハッシュ化し、`refresh_token_hash`で検索する。見つからなければ、`previous_refresh_token_hash`で検索する。
- **判定表**(リフレッシュの入口、functional-spec.md W2、rules.md BR5.6):

| 検索の結果 | Sessionの状態 | 結果 |
|---|---|---|
| `refresh_token_hash`に一致 | 有効(activeかつ`refreshExpiresAt`が未経過) | ユーザーの無効化の確認(C11)→ ロールの再確認(読み取った`active_role_id`をaとする)→ 条件付きの更新(下記)。成功なら200。更新の件数が0件なら、Sessionを読み直す: `refresh_token_hash`が変わっている(同時の更新に負けた)なら401(Sessionは失効させない)。`refresh_token_hash`は同じで、`active_role_id`だけが変わっている(並行するロール選択に負けた)なら、C11から得たロール集合Rは変えずに、読み直した`active_role_id`に、NFR2.6の判定表を適用して新しいロールを決め、条件付きの更新を**1回だけ**やり直す。やり直しも0件なら、401(Sessionは失効させない) |
| `refresh_token_hash`に一致 | 有効でない(revokedまたは期限切れ) | 401(Sessionは変更しない) |
| `previous_refresh_token_hash`に一致 | active | `lastRefreshedAt`から猶予(既定10秒)以内なら401(Sessionは失効させない、`auth_refresh_reuse_within_grace_total`を数える)。猶予を超えていれば、Sessionをrevokedにして401(`auth_refresh_token_reuse_detected_total`を数える) |
| `previous_refresh_token_hash`に一致 | revoked・期限切れ | 401(Sessionは変更しない。すでに有効でないため) |
| どちらにも一致しない | — | 401 |

- **条件付きの更新**(ローテーション。同一のトークンの同時の更新で、1件だけが成功する):

```
UPDATE auth_session
   SET previous_refresh_token_hash = refresh_token_hash,
       refresh_token_hash = :newHash, last_refreshed_at = :now,
       refresh_expires_at = :newRefreshExpiresAt, active_role_id = :newRoleId
 WHERE session_id = :sid AND refresh_token_hash = :oldHash
   AND status = 'active' AND refresh_expires_at > :now
   AND (active_role_id = :readRoleId OR (active_role_id IS NULL AND :readRoleId IS NULL))
```

  `:newRefreshExpiresAt`は、Java側で、`:now`に設定の有効期限を加えて計算した値をパラメータとして渡す(SQLの中で日時に加算しない、reliability-design.md NFR4.1)。`:readRoleId`は、更新の前に読み取った`active_role_id`である(NULLを含めて、等しいことを条件にする)。これにより、読み取りと更新の間に、`PUT /api/auth/active-role`がコミットされても、その選択を、古いロールで上書きしない(失われた更新の防止)。更新の件数が1なら成功、0なら、判定表のとおりに扱う。コミットの後に、Sessionのキャッシュを無効化する(reliability-design.md NFR4.3)。ロール選択(`PUT /api/auth/active-role`)の更新は、`session_id`と`status = 'active'`だけを条件とし、リフレッシュの更新が先にコミットされていれば、その後に、選択が反映される。
- 401の応答は、いずれの場合も、同じ内容(`auth.refresh.rejected`)とする(NFR2.8)。

## NFR2.4: ログインの保護

処理の順序と、各段階の失敗の扱い(functional-spec.md W1、rules.md BR5.1〜BR5.3・BR5.15)。

1. **入力の確認**: `email`または`password`が、空(null・空白のみ)の場合は、C11を呼ばずに、失敗の応答(401、`auth.login.failed`)を返す。JSONの形式が不正な場合は、400(`auth.request.malformed`)を返す。
2. **正規化**: `email`を、trimして小文字にする(BR5.1)。
3. **C11の`findByEmail`**(トランザクションの外)。登録がない・statusがactiveでない場合は、6へ。
4. **予約**(`LoginAttemptGate.reserve`、短いトランザクション): 枠を確保できなければ、6へ。確保できたら、予約(`generation`と、この予約が設定した`lockedUntil`。設定しなかった場合はnull)を保持する。`account_login_state`の行の初回作成で、同時の試行により、主キーの一意制約に違反した場合は、`AuthenticationApplicationService`が、トランザクションの外で捕捉し、新しいトランザクションで、最大3回まで、やり直す(reliability-design.md NFR4.1)。この違反は、500にも401にもならず、利用者からは観測されない(存在の推測を防ぐ)。
5. **検証**(C11の`verifyPasswordHash`、トランザクションの外)。
   - 成功: `LoginAttemptGate.succeed`と`SessionService.create`を、同一の短いトランザクションで行い、トークンを発行して200(reliability-design.md NFR4.1)。
   - 失敗: 追加の更新は行わず、失敗の応答(401)を返す。
   - `HashCapacityExceededException`、またはC11の内部設定DBの障害(`AuthStorageUnavailableException`に変換されたもの): `LoginAttemptGate.compensate`(条件付きの補償の更新)を試みたあと、503(`auth.service.unavailable`)を返す。
   - 上記以外の想定外の例外: 補償はせず(枠は失敗として数えたまま。安全側)、500を返す(NFR2.8)。
6. **実際の検証を行わない場合**(登録がない・activeでない・ロック中): C11の`dummyVerify(password)`を行い、失敗の応答を返す。`HashCapacityExceededException`なら、503を返す(BR5.15)。
- **応答の同一性**: 失敗の応答(401)は、原因(未登録・パスワードの誤り・ロック中・無効化済み・招待中)にかかわらず、同じステータス・タイトル・詳細・`code`(`auth.login.failed`)とし、ヘッダーも同じにする。テストで、原因ごとの応答の全体(ステータス・ヘッダー・本文)が一致することを確認する。
- **長いパスワード**: `verifyPasswordHash`と`dummyVerify`は、129文字以上の入力を、ハッシュ計算をせずに拒否する(C11の追補、契約追補の一覧)。実際の検証・ダミーの検証で、長い入力の扱いが揃うため、未登録のメールアドレスにだけ応答時間の差が出ない。
- **ロックの予約型の更新**: 条件付きの更新の具体は、reliability-design.md NFR4.1に示す。しきい値(既定5回)・ロック時間(既定15分)は、`application.yml`の設定である(NFR4.4)。
- 登録されていない・招待中・無効化済みのメールアドレスには、`AccountLoginState`の行を作らない(記録を残さない)。

## NFR2.5: Sessionの管理と失効

- **失効の契機とキャッシュの無効化**: Sessionを書き換えるすべての操作は、内部設定DBの更新がコミットされてから、`SessionCache`の該当のキーを無効化する(reliability-design.md NFR4.3)。

| 契機 | 更新の内容 | 無効化 |
|---|---|---|
| ログイン成功 | Sessionの作成(新規) | 不要(キャッシュに存在しない) |
| リフレッシュ成功 | ハッシュの更新、`refreshExpiresAt`の延長、`activeRoleId`の再確認 | 必要 |
| ログアウト | statusをrevokedに | 必要 |
| 無効になったトークンの再使用の検知 | statusをrevokedに | 必要 |
| リフレッシュ時のユーザーの無効化の検知 | statusをrevokedに | 必要 |
| ロール選択 | `activeRoleId`の更新 | 必要 |
| 定期削除 | 行の削除(有効でないもののみ) | 不要(有効でない行は、認証フィルタが、時刻の判定でも拒否する) |

- **Sessionの絶対期限を設けない**: リフレッシュは、成功のたびに有効期限を延長する(最後の更新から30分)。利用が続く限り、Sessionは、継続して有効になる。要件のFR3.1・機能設計に、Sessionの最大の存続期間の定めがないため、設けない([assumption]。残余リスク11)。
- **全Sessionの失効の手段**(バックアップからの復元後や、鍵の漏えいの疑いのときの運用の手順の前提、NFR4.7): 設定`auth.session.revoke-all-on-startup`(既定はfalse)を、trueにして起動すると、起動時に、`auth_session`のすべての行を削除する。削除は、Flywayの移行の後、Webサーバーがリクエストを受け付ける前に実行する(`SmartInitializingSingleton`で実行し、Flywayの初期化に`@DependsOn`を付ける。`ApplicationRunner`は、Webサーバーの起動の後に実行され、その間に作られたSessionを削除しうるため使わない)([assumption]。運用フェーズが本MVPスコープ外のため、この設定の存在を、運用の手順の前提として記録する。設定を戻さないまま起動を繰り返すと、そのたびに削除されるため、手順に、設定を戻すことを含める)。鍵の入れ替えだけでは、Sessionは失効しない点に注意する。
- ログアウトは、認証フィルタ(BR5.11)を通るため、期限切れのアクセストークンでは401になる。frontend-ui(U12)は、先にリフレッシュしてからログアウトする(NFR2.11)。

## NFR2.6: アクティブロールと権限昇格の防止

- **ロール選択**(`PUT /api/auth/active-role`): C11の`findByUserId(userId)`から、選択可能なロール(`UserAccount.roleIds`、直接付与分とGroup経由分の和集合)を取得し、指定された`roleId`が含まれない(空・null・未保持)場合は、403(`auth.role.not-held`)を返して、Sessionを変更しない。含まれる場合は、Sessionを更新し、コミット後にキャッシュを無効化して、200を返す。
- **リフレッシュ時の再確認**(BR5.10)の判定表: C11から最新のロール集合Rを得て、現在のアクティブロールaに対して、次のとおり決める。

| 現在のa | 集合R | 新しいa |
|---|---|---|
| 選択済みで、Rに含まれる | 任意 | a(変更なし) |
| 選択済みで、Rに含まれない | Rがちょうど1つ | そのロール(自動選択) |
| 選択済みで、Rに含まれない | Rが0個・2個以上 | null |
| 未選択(null) | Rがちょうど1つ | そのロール(自動選択) |
| 未選択(null) | Rが0個・2個以上 | null |

- 認証フィルタは、リクエストごとには、アクティブロールがRに含まれるかを、再確認しない(キャッシュされたSessionの値を、そのまま`Operator`に設定する)。ロールを外された利用者が、旧ロールで操作できる期間は、最大10分(アクセストークンの有効期限)である(NFR Requirementsの残余リスク7)。

## NFR2.7: 認証情報の非出力

- **型による防止**: パスワード・トークン・鍵を運ぶ値は、`toString`が伏せ字を返す専用の型(`SecretString`・`TokenValue`・`SecretKeyMaterial`)に保持する。リクエストの型(`LoginRequest`など)の`toString`は、`password`・`refreshToken`を含めない(Javaの`record`の既定の`toString`は、全項目を出すため、上書きする)。
- **ログの窓口の一本化**: 認証の出来事のログは、`AuthEventLogger`(専用の部品)だけが出力し、引数は、userId・sessionId・roleId・分類(列挙型)・件数に限る。呼び出し側が、任意の文字列を渡せないため、パスワード・トークンを、誤って出力する経路を作らない。
- **HTTPのログ・自動計装**: リクエストのボディ・`Authorization`ヘッダーを、ログ・トレースの属性に出力しない設定にする(HTTPの自動計装が、リクエストヘッダーを収集する設定を、空にする)。フレームワークのリクエストログ(`CommonsRequestLoggingFilter`など、ペイロードを出力するもの)を、有効にしない。
- **例外のメッセージ**: トークンの解析の例外(Nimbusの`ParseException`など)のメッセージは、ログに出力せず、分類(`reason`)だけを出力する。
- **確認**: テストで、ログ・トレース・メトリクスのラベル・応答に、テスト用のパスワード・トークン・鍵の文字列が現れないことを確認する(tech-stack-decisions.md NFR8.2)。

## NFR2.8: エラーレスポンスの情報開示制御

エラーは、RFC 9457のProblemDetails(`application/problem+json`)とし、`type`は`about:blank`、`title`・`detail`は、状況ごとの固定の文言(原因の詳細を含めない)、`code`は、i18nキー(拡張メンバー。frontend-ui(U12)が、キーから、表示する文言を選ぶ、NFR7.1)とする。スタックトレース・SQL文・内部実装の詳細・入力値は含めない。

| 状況 | ステータス | `code` | 備考 |
|---|---|---|---|
| ログイン失敗(全原因) | 401 | `auth.login.failed` | 原因を区別しない(BR5.2) |
| 認証フィルタの失敗(全原因) | 401 | `auth.token.invalid` | ヘッダー`WWW-Authenticate: Bearer`だけを付ける(`error`などの理由は付けない) |
| リフレッシュの失敗(全原因) | 401 | `auth.refresh.rejected` | 猶予内の再送・盗用の疑いを区別しない(BR5.6) |
| 保持していないロールの選択 | 403 | `auth.role.not-held` | |
| 一時的に処理できない | 503 | `auth.service.unavailable` | ハッシュ計算の待機超過(BR5.15)と、内部設定DBの障害(NFR4.2)。原因を区別しない |
| リクエストボディが大きすぎる | 413 | `auth.request.too-large` | NFR2.10 |
| JSONの形式が不正 | 400 | `auth.request.malformed` | |
| 他ユニットのリクエスト処理の中で、C14(`getActiveRoleId`)が投げる`SessionNotFoundException`・`SessionExpiredException` | 401 | `auth.token.invalid` | C14の契約(frontend-ui向けには401)。`WWW-Authenticate: Bearer`を付ける |
| 他ユニットのリクエスト処理の中で、C14・C11が投げる`AuthStorageUnavailableException` | 503 | `auth.service.unavailable` | NFR4.2 |

- **フィルタでの応答**: 認証フィルタは、コントローラの手前で応答を返すため、`@RestControllerAdvice`が使えない。`ProblemDetailsWriter`(専用の部品)が、フィルタでの401・413・503を、上の表の形式で書き出す。`ProblemDetailsWriter`は、NFR2.9のセキュリティヘッダーも、自前で付ける(後述)。ProblemDetailsの`instance`は、含めない(生のパスを入れない、user-managementのC5の追補と同じ)。
- **コントローラでの応答**: `AuthApiExceptionAdvice`(`@RestControllerAdvice`。対象を`AuthController`に限定し、`@Order`を明記する)が、認証系のコントローラの例外を、上の表に変換する。共通基盤の汎用の例外ハンドラ(基底)と衝突しないよう、対象を限定する(user-managementの`UserApiExceptionAdvice`と同じ方式)。
- **他ユニットのコントローラの中で投げられる、U5の例外**: `AuthCrossCuttingExceptionAdvice`(`@RestControllerAdvice`。対象のコントローラは限定せず、対象の**例外の型**を、`SessionNotFoundException`・`SessionExpiredException`・`AuthStorageUnavailableException`の3つに限定する。`@Order`を明記し、共通基盤の基底のハンドラより優先する)が、上の表の最後の2行に変換する。U5自身の例外の型だけを扱うため、他ユニットの例外の型に依存せず、他ユニットの例外の変換とも衝突しない。list-engine(U10)・record-edit-engine(U11)は、C14の例外を、握りつぶさず、そのまま伝播させる(logical-components.mdの追補20番)。
- 分類できない例外は、500(詳細なし、`code`は共通基盤の既定)とし、ERRORログに、例外の種類と、リクエストIDを記録する(メッセージ全文は出さない)。

## NFR2.9: 通信路・レスポンスヘッダー・CSRF・CORS

Q2=Aにより、authentication-serviceが、アプリケーション全体の`SecurityFilterChain`を所有し、次を設定する。

| ヘッダー | 値 | 対象 |
|---|---|---|
| `Referrer-Policy` | `no-referrer` | すべてのレスポンス(user-managementの要求、招待トークンのRefererによる漏えいの防止) |
| `X-Content-Type-Options` | `nosniff` | すべてのレスポンス |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` | すべてのレスポンス。初期値であり、frontend-ui(U12)の実装で必要になれば緩める |
| `Cache-Control` | `no-store` | `/api/**`のすべてのレスポンス(ログイン・リフレッシュの応答を含む、NFR2.9)。静的ファイルには付けない |
| `X-Frame-Options` | `DENY` | すべてのレスポンス(Spring Securityの既定) |
| `Strict-Transport-Security` | Spring Securityの既定(HTTPSのリクエストにのみ付く) | HTTPSの環境 |

- **サイズ制限のフィルタが返す413**: `AuthRequestSizeLimitFilter`は、Spring Securityのチェーンより前に置かれ、`Content-Length`超過の413を直接書くため、チェーンの`HeaderWriterFilter`が付けるヘッダーが付かない。`ProblemDetailsWriter`が、上の表のヘッダー(`Referrer-Policy`・`X-Content-Type-Options`・`Content-Security-Policy`・`X-Frame-Options`・`/api/**`の`Cache-Control: no-store`)を、自前で付ける。ヘッダーの値は、`SecurityHeaderValues`(専用の部品。設定の1か所)から取り、`SecurityFilterChain`の設定と`ProblemDetailsWriter`が、同じ値を使う(チャンク転送の413と、値がそろう)。テストで、両方の経路の413のヘッダーが同じであることを確認する。
- **`Strict-Transport-Security`**: アプリケーションがTLSを終端しない場合、Spring Securityは、HTTPSのリクエストと判定できず、ヘッダーを付けない。HTTPSの環境で付けるには、フォワードヘッダーの設定(`server.forward-headers-strategy`)を前提とするか、リバースプロキシが付ける。どちらも環境側の前提とする(共通基盤への要求)。
- **Spring Securityの既定のキャッシュ抑止**は、静的ファイルのキャッシュを妨げるため、無効にし、`/api/**`にだけ`no-store`を付ける([assumption]。要件が求めるのは、ログイン・リフレッシュの応答だけであり、`/api/**`全体への適用は、本設計の判断)。
- **CSRF**: 認証は`Authorization: Bearer`ヘッダーで行い、Cookieを使わない。CSRFの保護は、無効にする。`SessionCreationPolicy.STATELESS`とし、HTTPセッションを作らず、`Set-Cookie`を返さない。認証のためのCookieを、今後追加しないことを、不変条件とし、テストで、全応答に`Set-Cookie`がないことを確認する。
- **CORS**: 設定しない(単一の実行可能WARからフロントエンドを配信するため)。
- **TLS**: アプリケーションは、TLSを終端せず、終端は、環境(リバースプロキシなど)の前提とする(NFR Requirementsの環境側の前提)。本番相当の環境でHTTPSであることの強制は、環境の責任とする。

## NFR2.10: リクエストの大きさの上限

- `AuthRequestSizeLimitFilter`(U5が所有するサーブレットフィルタ)が、`/api/auth/**`を対象に、リクエストボディを64KiBに制限する。フィルタチェーンで、認証フィルタより前に置く(認証前の大きなボディを読み込ませないため)。
  - `Content-Length`が64KiBを超える場合は、内容を読まずに、フィルタの中で、413(`auth.request.too-large`)を返す。
  - `Content-Length`がない場合(チャンク転送)は、リクエストの入力ストリームを、読み込み量を数えるストリームで包み、64KiBを超えた時点で、専用の例外(`RequestBodyTooLargeException`、`java.io.IOException`のサブクラス)を投げる。この例外は、JSONの読み取りの中で、`HttpMessageNotReadableException`(400)に包まれる場合と、包まれずに直接伝わる場合の、両方がありうるため、`AuthApiExceptionAdvice`は、例外の原因の連鎖をたどって、`RequestBodyTooLargeException`があれば、いずれの場合も413に変換する。
- パスワードの長さの上限(128文字)は、C11が、ハッシュ計算の前に判定する(NFR2.4)。
- 64KiBの上限を、`/api/auth/**`以外のAPIへ広げない(他のユニットの上限は、各ユニットが所有する)。

## NFR2.11: ブラウザ側のトークンの扱い(frontend-ui, U12への要求)

U5の設計が、U12に前提として求める動作を、次のとおり記録する。U12のNFR Requirements・Code Generationで具体化する。

- アクセストークンは、ページのメモリに保持する。リフレッシュトークンは、複数のタブで共有できる保管先に置く(機能設計の追補7の(b))。この保管先から、スクリプトが読み取れる(残余リスク8)。緩和は、`Content-Security-Policy`(NFR2.9)である。
- APIが**401**を返したときは、リフレッシュを試み、成功すれば元のリクエストを再試行する。リフレッシュの応答が401のときの扱いは、機能設計の追補7の(b)に従う。
- APIが**503**を返したときは、認証の失敗ではなく、一時的な障害として扱う。セッションを終了せず、利用者に、時間を置いた再試行を促す。
- トークン(平文)を、URL・ログ・エラー報告・外部へ送信される情報に含めない。ログアウトの前に、アクセストークンの期限が切れていれば、先にリフレッシュする。
- 初期の`Content-Security-Policy`が厳しいため、U12は、実装で必要になったものを、U5の設定に、変更として申し出る(インラインのスタイルなど)。

## NFR2.12: ロックの悪用・パスワードスプレーへの備え

- NFR Requirementsの決定(Q3=A)に従い、追加の対策(IP単位のレート制限、初期管理者のロック対象外)を、設計に含めない。
- 検知は、`auth_account_locked_total`・`auth_login_failed_total`のカウンタと、アラートの暫定値(observability-design.md NFR5.1)で行う。
- **ダミー検証が、ハッシュ計算の許可枠を占有するリスク**(NFR Designのレビューではなく、NFR Requirementsのレビューで指摘された事項)は、Q3(NFR Design)=Aにより、受容し、残余リスク10として記録する。

## NFR2.13: セキュリティ関連CIゲート・鍵の管理(開発・テスト)

- team.mdの既定(SAST・シークレットスキャンのマージ前のCIブロッキング)を適用する。
- リポジトリに、JWTの鍵を置かない。`application.yml`には、`auth.jwt.secret`のキーだけを置き、値は、環境変数から注入する(既定値なし)。
- 開発の環境では、開発者が、Git管理外のローカル設定または環境変数に、開発用の鍵を置く。
- 自動テストは、テストの実行時に、`SecureRandom`で鍵を生成し、テスト用の設定として、プログラムから渡す(リポジトリに固定値を置かない)。
- シークレットスキャン(gitleaks等)の対象に、`application*.yml`・テスト用の設定を含める(鍵らしい値の混入を、CIで止める)。

## 脅威の考慮(STRIDE)

| 分類 | 想定される脅威 | 設計上の対応 |
|---|---|---|
| なりすまし | トークンの偽造・`alg`の差し替え、盗まれたリフレッシュトークンの再使用、総当たり | HS256に固定し、`alg`を検証(NFR2.2)。`sub`とSessionの`userId`の照合。ローテーションと、猶予を超えた再使用でのSessionの失効(NFR2.3)。予約型のロック(NFR2.4) |
| 改ざん | トークンの内容の書き換え、リフレッシュトークンの推測 | HMACの署名の検証。256ビットの乱数(NFR2.2・NFR2.3) |
| 否認 | ログイン・ログアウト・盗用の疑いの否認 | `AuthEventLogger`が、userIdとsessionIdで、構造化ログに記録する(observability-design.md NFR5.2) |
| 情報漏えい | アカウントの存在・ロック状態の推測、トークン・鍵・パスワードの流出 | 同一の応答(NFR2.4・NFR2.8)、専用の型と`AuthEventLogger`(NFR2.7)、`Cache-Control: no-store`・`Referrer-Policy`(NFR2.9) |
| サービス拒否 | ログインの大量の試行によるハッシュ計算の枯渇、大きなボディ | 許可枠と2秒の打ち切り(user-managementのNFR1.3)。64KiBの上限(NFR2.10)。ダミー検証による占有は受容(残余リスク10) |
| 権限昇格 | 保持しないロールの選択、認証を経ない経路、旧ロールの継続 | ロールの再確認(NFR2.6)。deny by default(NFR2.1)。最大10分の遅れは受容(NFR Requirementsの残余リスク7) |

## 残余リスク(NFR Designで追加したもの)

`nfr-requirements/security-requirements.md`の残余リスク1〜9に加えて、次を記録する。いずれもMVPでは受容する。

| 番号 | リスク | 内容 | 判断 | 見直し先・見直しのきっかけ |
|---|---|---|---|---|
| 10 | ダミー検証によるハッシュ計算の許可枠の占有 | ログインは認証不要で、未登録のメールアドレスでも`dummyVerify`がハッシュ計算の許可枠を使う。大量のログインの送信で、許可枠が占有されると、正規のログインと招待受諾が、2秒の打ち切りで503になる。サーバーの資源そのものは、打ち切りで守られる | 受容(NFR Design Q3=A)。`auth_login_hash_capacity_exceeded_total`で検知する。Q3=A(NFR Requirements、IP単位のレート制限を設けない)と整合する | 実際に観測されたとき。ダミー検証の同時数の制限、IP単位のレート制限を検討する |
| 11 | Sessionの絶対期限がない | リフレッシュは、成功のたびに有効期限を延長するため、利用が続く限り、Sessionは継続して有効になる。盗まれたリフレッシュトークンが、盗用の検知(猶予を超えた再使用)を受けずに使われ続ける場合、攻撃者のSessionも、継続して有効になる | 受容([assumption]。要件・機能設計に、Sessionの最大の存続期間の定めがない) | 最大の存続期間が求められたとき。Sessionに絶対期限を加える(機能設計の変更) |

## 根拠

- 要件: `nfr-requirements/security-requirements.md`(NFR2.1〜NFR2.13)、`nfr-requirements/performance-requirements.md`、`nfr-requirements/scalability-requirements.md`、`nfr-requirements/reliability-requirements.md`(NFR4.2・NFR4.3・NFR4.4・NFR4.7)、`nfr-requirements/observability-requirements.md`(NFR5.1)、`nfr-requirements/tech-stack-decisions.md`、`inception/requirements-analysis/requirements.md`のNFR2
- 機能設計: `functional-design/functional-spec.md`(W1〜W7、追補一覧)、`rules.md`(BR5.1〜BR5.16)
- 契約: `inception/contract-design/contract-summary.md` のC4(REST API)・C10・C11・C14
- 質問回答: `nfr-design-questions.md` Q2・Q3
- ルール: `aidlc/spaces/default/memory/project.md`(Mandated・Forbidden)
