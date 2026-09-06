# Business Rules: auth

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: ログインは、Accountのemailおよびpasswordの照合により行う。passwordHashがnull、Argon2照合に失敗、またはstatus=disabledのいずれかに該当する場合は認証失敗とする
    category: business
    applies_to: Account
    trigger: "POST /api/auth/login を受けたとき(ロック中でない場合、BR4.2)"
    logic: "IF passwordHash IS NULL OR Argon2.matches(password, passwordHash)=false OR status=disabled THEN 401を返す(consecutiveFailureCountはBR4.1により加算)。ELSE 認証成功としてBR2.1へ進む"
    violation_behaviour: "401エラー(RFC 7807)"
    source: FR6.1

  - id: BR2.1
    statement: ログイン成功時、契約#20(auth → permission)を呼び出し、対象accountIdの有効なロールID一覧(直接割当 ∪ グループ経由の割当)を取得する
    category: business
    applies_to: Account
    trigger: "BR1.1の認証成功後"
    logic: "permissionへaccountIdを渡し、roleId配列を取得する。取得結果が空配列でもエラーとしない(契約#20 Failure behavior)"
    violation_behaviour: "該当なし(permission呼び出し自体の失敗は5xxとして伝播する)"
    source: FR6.2, contract-summary.md #20

  - id: BR2.2
    statement: BR2.1で取得したロールID一覧を用いて、アクセストークン(JWT、HS256署名)を発行する。claimsはsub(accountId)・isAdmin・roles(BR2.1の結果)・標準クレーム(iat, exp)から構成し、activeRoleIdは含めない
    category: business
    applies_to: Account
    trigger: "BR2.1の完了後"
    logic: "アクセストークンの有効期限は15分(contract-summary.md 共通規約「トークン有効期限」)。ロール切替はX-Active-Roleヘッダーで都度伝えるため、トークン再発行を伴わない(契約#11参照)"
    violation_behaviour: "該当なし"
    source: FR6.2, FR5.5, FR5.6, contract-summary.md 共通規約

  - id: BR2.3
    statement: BR2.2と同時に、リフレッシュトークンを発行する。実トークン値はランダムかつ十分なエントロピーを持つ値とし、そのハッシュ値のみをRefreshTokenとして永続化する(平文は保存しない)。有効期限は7日間とする
    category: business
    applies_to: RefreshToken
    trigger: "BR2.2の完了後"
    logic: "RefreshToken.tokenHash = hash(生成した実トークン値)。実トークン値はログインレスポンスとしてのみクライアントへ返す"
    violation_behaviour: "該当なし"
    source: FR6.3, contract-summary.md 共通規約, functional-design-questions.md Q3

  - id: BR3.1
    statement: POST /api/auth/refresh で受け取ったリフレッシュトークンをハッシュ化し、revoked=falseかつ未期限切れのRefreshTokenと一致するものを検索する。一致しなければ401とする
    category: business
    applies_to: RefreshToken
    trigger: "POST /api/auth/refresh を受けたとき"
    logic: "IF 一致するRefreshTokenが存在しない THEN 401(RFC 7807)。ELSE BR3.2へ進む"
    violation_behaviour: "401エラー(RFC 7807)"
    source: FR6.3

  - id: BR3.2
    statement: リフレッシュトークンの検証に成功した場合、新しいアクセストークン(BR2.1・BR2.2と同じロジックでロールを再取得して発行)と新しいリフレッシュトークンを発行し、検証に使用した古いRefreshTokenをrevoked=trueにする(リフレッシュトークンローテーション)
    category: business
    applies_to: RefreshToken
    trigger: "BR3.1の検証成功後"
    logic: "古いRefreshTokenは即座に無効化し、以後の再利用(トークン使い回し)を防ぐ。ロール一覧はキャッシュせずBR2.1と同様に都度permissionへ問い合わせる(ロール変更の反映漏れを防ぐ)"
    violation_behaviour: "該当なし"
    source: FR6.3, functional-design-questions.md Q3

  - id: BR3.3
    statement: ログアウト(POST /api/auth/logout)は、呼び出し元アクセストークンのaccountIdに紐づく、revoked=falseかつ未期限切れのすべてのRefreshTokenをrevoked=trueにする
    category: business
    applies_to: RefreshToken
    trigger: "POST /api/auth/logout を受けたとき"
    logic: "単一デバイスに限定せず、当該accountIdの有効なリフレッシュトークンを一括で失効させる(シンプルさを優先。デバイス単位の個別ログアウトはスコープ外)"
    violation_behaviour: "該当なし"
    source: contract-summary.md #11(auth API)

  - id: BR4.1
    statement: ログイン失敗のたびにAccount.consecutiveFailureCountを1加算する。加算後の値がn(application.yml設定値、既定5)に達した場合、Account.lockedUntil = 現在時刻 + m秒(application.yml設定値、既定300秒)を設定する
    category: business
    applies_to: Account
    trigger: "BR1.1で認証失敗と判定されたとき"
    logic: "consecutiveFailureCount += 1。IF consecutiveFailureCount >= n THEN lockedUntil = now + m秒"
    violation_behaviour: "該当なし(本ルール自体が違反時の挙動)"
    source: FR6.6, functional-design-questions.md Q1

  - id: BR4.2
    statement: lockedUntilが現在時刻より未来である間、当該Accountへのログイン試行はパスワード照合を行わず403を返す。lockedUntilを過ぎたログイン試行は、通常どおりBR1.1から評価する(明示的なロック解除操作は存在しない)
    category: business
    applies_to: Account
    trigger: "POST /api/auth/login を受けたとき"
    logic: "IF lockedUntil IS NOT NULL AND lockedUntil > now THEN 403を返す(パスワード照合前に判定する)。ELSE BR1.1へ進む"
    violation_behaviour: "403エラー(RFC 7807)"
    source: FR6.6

  - id: BR4.3
    statement: ログイン成功時(BR1.1）、Account.consecutiveFailureCountを0にリセットし、lockedUntilをnullにする
    category: business
    applies_to: Account
    trigger: "BR1.1で認証成功と判定されたとき"
    logic: "consecutiveFailureCount = 0、lockedUntil = null"
    violation_behaviour: "該当なし"
    source: FR6.6

  - id: BR5.1
    statement: パスワードはArgon2(Spring SecurityのArgon2PasswordEncoder、デフォルトパラメータ)でハッシュ化して保存する。平文・可逆暗号化での保存は行わない。ソルトはライブラリが1ハッシュごとに自動生成しハッシュ文字列へ埋め込むため、別途のペッパー等の秘密鍵管理は行わない
    category: constraint
    applies_to: Account
    trigger: "パスワードを新規設定・変更するとき(complete-registration・reset-password・自己サービスのパスワード変更)"
    logic: "passwordHash = Argon2PasswordEncoder.encode(平文パスワード)"
    violation_behaviour: "該当なし"
    source: NFR7, functional-design-questions.md Q4

  - id: BR6.1
    statement: AccountActionTokenを新規発行する際、同一accountId・同一purposeの既存の未消費(consumedAt IS NULL)トークンが存在すればconsumedAtを設定して無効化してから、新しいトークンを発行する。purpose=registration_completionのトークンは、account-managementからの契約#4呼び出し(アカウント新規作成)を処理する同一呼び出し内で同期的に発行し、実トークン値を契約#4の戻り値としてaccount-managementへ返す(account-managementはこの値を自身が発行するAccountCreatedEventのpayloadへ渡す。トークンの発行・検証・永続化はauthが一貫して担い、account-managementはAccountActionTokenテーブルを一切参照しない)
    category: business
    applies_to: AccountActionToken
    trigger: "forgot-password、email-change-request、またはaccount-managementからの契約#4呼び出し(アカウント新規作成)によりトークンを発行するとき"
    logic: "同一accountId・purposeの未消費トークンをconsumedAt=nowで無効化し、新規トークン(expiresAt = now + application.yml設定値、既定24時間)を発行する"
    violation_behaviour: "該当なし"
    source: contract-summary.md #4(Construction / auth Unit Functional Designにより追記)、#9、#11, functional-design-questions.md Q2

  - id: BR6.2
    statement: AccountActionTokenの使用(reset-password、complete-registration、email-change-confirm)にあたり、consumedAtが設定済み、またはexpiresAtを過ぎている場合は400とする
    category: business
    applies_to: AccountActionToken
    trigger: "トークンを用いた完了系エンドポイントを受けたとき"
    logic: "IF consumedAt IS NOT NULL OR expiresAt < now THEN 400(RFC 7807)。ELSE 検証成功として該当する状態変更を行い、consumedAt=nowを設定する"
    violation_behaviour: "400エラー(RFC 7807)"
    source: functional-design-questions.md Q2

  - id: BR6.3
    statement: complete-registration完了時、AccountActionToken.accountIdが指すAccountのname・passwordHash(BR5.1)を設定する。statusはaccount-management作成時点で既にactiveであるため変更しない
    category: business
    applies_to: Account
    trigger: "POST /api/auth/complete-registration の検証成功時(BR6.2)"
    logic: "Account.name = リクエストのname、Account.passwordHash = Argon2.encode(リクエストのpassword)"
    violation_behaviour: "該当なし"
    source: contract-summary.md #11「アカウント作成通知メールのURLからの登録完了」

  - id: BR6.4
    statement: email-change-confirm完了時、リクエストされた現在のパスワードがAccount.passwordHashと一致することをArgon2で検証したうえで、Account.emailをAccountActionToken.pendingNewEmailの値に更新する
    category: business
    applies_to: Account
    trigger: "POST /api/me/email-change-confirm の呼び出し時(トークン自体の有効性はBR6.2で検証済み)"
    logic: "IF Argon2.matches(現在のパスワード, Account.passwordHash)=false THEN 400(RFC 7807)。ELSE Account.email = token.pendingNewEmail"
    violation_behaviour: "400エラー(RFC 7807、パスワード不一致)"
    source: contract-summary.md #11「新アドレス宛メール内URLからの変更確定(現在のパスワード入力)」

  - id: BR7.1
    statement: isAdminクレームはFR5.5(専用の管理者ロール)の判定材料として発行するのみであり、管理系機能へのアクセス制御そのもの(FR5.6)は各消費Unit(config-management、schema-ingestion、permission管理系、account-management、audit-log)がそれぞれ自身の画面・APIでローカルに判定する(ADR-002、横断的関心事)
    category: authorization
    applies_to: Account
    trigger: "アクセストークン発行時(BR2.2)"
    logic: "該当なし(発行のみ。判定ロジックは各消費Unit側)"
    violation_behaviour: "該当なし"
    source: FR5.5, FR5.6

  - id: BR6.5
    statement: 自己サービスでの氏名変更(PUT /api/me/profile)は、nameが空文字でないことを検証する
    category: constraint
    applies_to: Account
    trigger: "PUT /api/me/profile でnameの変更を含むリクエストを受けたとき"
    logic: "IF name IS NULL OR name = '' THEN 400を返す。ELSE Account.nameを更新する"
    violation_behaviour: "400エラー(RFC 7807)"
    source: contract-summary.md #11「自己サービスでの氏名・パスワード変更」

  - id: BR8.1
    statement: ログイン・自己サービス操作の結果を、契約#5〜#8(監査ログイベント契約)に基づきAuditableActionOccurredEventとして発行する(unit-of-work-dependency.mdのauth→audit-log依存に対応)
    category: business
    applies_to: Account
    trigger: "BR1.1(ログイン試行)、BR4.1(ロック発生)、BR6.3(登録完了)、BR6.4(メールアドレス変更確定)、BR5.1経由のパスワード変更(reset-password・自己サービス)、BR6.5(氏名変更)のいずれかが完了したとき"
    logic: >
      IF ログイン成功(BR1.1) THEN actionType=LOGIN_SUCCESS。
      IF ログイン失敗(BR1.1でNG、BR4.1未到達) THEN actionType=LOGIN_FAILED。
      IF BR4.1によりlockedUntilを新規設定 THEN actionType=LOGIN_LOCKED(LOGIN_FAILEDに加えて発行する)。
      IF 登録完了(BR6.3)・パスワード再設定完了(reset-password)・自己サービスでの氏名/パスワード変更(BR6.5/自己サービスのBR5.1)・メールアドレス変更確定(BR6.4)のいずれか THEN actionType=SELF_SERVICE_PROFILE_CHANGED。
      いずれもoccurredAt=現在時刻、actorAccountId=対象accountId、targetDescription="account: {accountId}"とする。
    violation_behaviour: "該当なし(記録失敗は主処理をブロックしない、契約#5〜#8のasync仕様どおり)"
    source: unit-of-work-dependency.md(auth→audit-log)、contract-summary.md #7(auth用actionType語彙)
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | business | ログイン認証(email/password照合) |
| BR2.1 | business | 契約#20経由での有効ロール一覧取得 |
| BR2.2 | business | アクセストークン(JWT)発行、有効期限15分 |
| BR2.3 | business | リフレッシュトークン発行(ハッシュ化永続化)、有効期限7日 |
| BR3.1 | business | リフレッシュトークン検証 |
| BR3.2 | business | リフレッシュトークンローテーション |
| BR3.3 | business | ログアウト時の全リフレッシュトークン失効 |
| BR4.1 | business | ログイン失敗カウント・ロック設定(n/mはapplication.yml) |
| BR4.2 | business | ロック中のログイン試行拒否 |
| BR4.3 | business | ログイン成功時のカウント/ロックリセット |
| BR5.1 | constraint | パスワードハッシュ化(Argon2) |
| BR6.1 | business | AccountActionToken発行時の旧トークン無効化 |
| BR6.2 | business | AccountActionTokenの有効性検証(単回使用・期限) |
| BR6.3 | business | 登録完了時のname/passwordHash設定 |
| BR6.4 | business | メールアドレス変更確定時のパスワード再検証 |
| BR7.1 | authorization | isAdminクレーム発行(判定は各消費Unit側) |
| BR6.5 | constraint | 自己サービス氏名変更の空文字禁止 |
| BR8.1 | business | 監査ログイベント発行(LOGIN_SUCCESS/LOGIN_FAILED/LOGIN_LOCKED/SELF_SERVICE_PROFILE_CHANGED) |
