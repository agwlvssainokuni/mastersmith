# Functional Specification: auth

## ワークフロー

### 1. ログイン

1. 利用者がemail/passwordで `POST /api/auth/login` を呼び出す。
2. lockedUntilが未来であれば403を返す(BR4.2)。
3. email/passwordを照合する(BR1.1)。失敗すればconsecutiveFailureCountを加算し(BR4.1)、監査ログイベント(actionType=LOGIN_FAILED。ロック閾値に達しlockedUntilを新規設定した場合はLOGIN_LOCKEDも発行、BR8.1)を発行してから401を返す。
4. 成功すればconsecutiveFailureCount/lockedUntilをリセットする(BR4.3)。
5. 契約#20(auth → permission)でaccountIdの有効ロールID一覧を取得する(BR2.1)。
6. アクセストークン(JWT、claims: sub/isAdmin/roles/iat/exp、有効期限15分)を発行する(BR2.2)。
7. リフレッシュトークンを発行し、ハッシュ化して永続化する(BR2.3)。
8. 監査ログイベント(actionType=LOGIN_SUCCESS、BR8.1)を発行する。
9. accessToken/refreshTokenをレスポンスとして返す。

### 2. アクセストークンのリフレッシュ

1. フロントエンドが、アクセストークン期限切れ(401)を検知して `POST /api/auth/refresh` を呼び出す。
2. 受け取ったリフレッシュトークンをハッシュ化し、有効なRefreshTokenと照合する(BR3.1)。一致しなければ401。
3. 一致すれば、ロール一覧を再取得したうえで新しいアクセストークンと新しいリフレッシュトークンを発行し、古いRefreshTokenを失効させる(BR3.2、ローテーション)。

### 3. ログアウト

1. 認証済み利用者が `POST /api/auth/logout` を呼び出す。
2. 当該accountIdに紐づく有効なRefreshTokenをすべて失効させる(BR3.3)。

### 4. アカウント登録完了(account-managementでの作成後)

1. account-managementが契約#4経由でAccountを新規作成する(status=active、passwordHash=null)。authはこの同一呼び出しの中で、purpose=registration_completionのAccountActionTokenを発行し(BR6.1)、実トークン値(registrationToken)を契約#4の戻り値としてaccount-managementへ返す。
2. account-managementは受け取ったregistrationTokenを用いて、AccountCreatedEvent(通知イベント契約#9、accountId・recipientEmail・registrationToken)をnotificationへ発行する(publisherはaccount-management。authはこのイベントを経由しない)。NotificationComponentがこれを購読し、アカウント作成通知メール(記載URLにトークンを含む)を送信する(FR6.4(1))。
3. 利用者が通知メール内のURLへアクセスし、氏名・パスワードを入力して `POST /api/auth/complete-registration` を呼び出す。
4. トークンの有効性を検証し(BR6.2)、Account.name・passwordHashを設定する(BR6.3、BR5.1)。
5. 監査ログイベント(actionType=SELF_SERVICE_PROFILE_CHANGED、BR8.1)を発行する。
6. 完了後、auth自身がAccountRegistrationCompletedEvent(通知イベント契約#9〜#10、publisher=auth)をnotificationへ発行し、アカウント登録完了通知(FR6.4(2))を送信する。

### 5. パスワード忘れ対応

1. 利用者が `POST /api/auth/forgot-password` にemailを渡す。
2. purpose=password_forgotのAccountActionTokenを発行する(BR6.1)。存在しないemailの場合も202を返す(利用者列挙[user enumeration]を防ぐため、常に同一レスポンスとする)。
3. NotificationComponentへパスワード忘れ対応通知(FR6.4(5))のイベントを発行する。
4. 利用者が通知メール内のURLへアクセスし、新しいパスワードを入力して `POST /api/auth/reset-password` を呼び出す。
5. トークンの有効性を検証し(BR6.2)、Account.passwordHashを設定する(BR5.1)。
6. 監査ログイベント(actionType=SELF_SERVICE_PROFILE_CHANGED、BR8.1)を発行する。
7. 完了後、NotificationComponentへパスワード変更通知(FR6.4(4))のイベントを発行する。

### 6. 自己サービスでの氏名・パスワード変更

1. 認証済み利用者が `PUT /api/me/profile` を呼び出す。
2. nameが空文字でないことを検証する(BR6.5)。
3. Account.name、および(指定があれば)passwordHash(BR5.1)を更新する。
4. 監査ログイベント(actionType=SELF_SERVICE_PROFILE_CHANGED、BR8.1)を発行する。
5. パスワードを変更した場合、NotificationComponentへパスワード変更通知(FR6.4(4))のイベントを発行する。氏名のみの変更の場合はアカウント情報変更通知(FR6.4(3))のイベントを発行する。

### 7. 自己サービスでのメールアドレス変更

1. 認証済み利用者が `POST /api/me/email-change-request` に新アドレスを渡す。
2. purpose=email_changeのAccountActionTokenをpendingNewEmail込みで発行する(BR6.1)。
3. NotificationComponentへメールアドレス変更リクエスト通知(FR6.4(6))のイベントを発行する(送信先は新アドレス)。
4. 利用者が新アドレス宛メール内のURLへアクセスし、現在のパスワードを入力して `POST /api/me/email-change-confirm` を呼び出す。
5. トークンの有効性を検証し(BR6.2)、現在のパスワードを検証したうえでAccount.emailを更新する(BR6.4)。
6. 監査ログイベント(actionType=SELF_SERVICE_PROFILE_CHANGED、BR8.1)を発行する。
7. 完了後、NotificationComponentへアカウント情報変更通知(FR6.4(3))のイベントを発行する。

## 状態遷移

### Account.status

```mermaid
stateDiagram-v2
    [*] --> active: account-managementが作成(契約#4)
    active --> disabled: account-managementが無効化(FR6.4.4)
    disabled --> [*]
```

<!-- Text fallback: Accountはaccount-management作成時にactiveとなり、無効化操作によりdisabledへ遷移する(論理削除、物理削除なし)。disabledから元に戻す操作はスコープ外。 -->

### RefreshToken.revoked

```mermaid
stateDiagram-v2
    [*] --> valid: 発行(BR2.3)
    valid --> revoked_state: ログアウト(BR3.3)またはローテーション(BR3.2)
    valid --> [*]: 有効期限切れ(7日)
    revoked_state --> [*]
```

<!-- Text fallback: RefreshTokenは発行時にvalid(revoked=false)。ログアウトまたはローテーションによりrevoked=trueとなる。有効期限切れも同様に無効として扱う(明示的な状態列は持たない)。 -->

## エンティティ関連図(ER図、entities.mdから導出)

```mermaid
erDiagram
    Account {
        string name
        string email
        string passwordHash
        string status
        boolean isAdmin
        integer consecutiveFailureCount
        datetime lockedUntil
    }
    AccountActionToken {
        string purpose
        datetime expiresAt
        datetime consumedAt
        string pendingNewEmail
    }
    RefreshToken {
        string tokenHash
        datetime expiresAt
        boolean revoked
    }

    Account ||--o{ AccountActionToken : "発行される(accountId参照)"
    Account ||--o{ RefreshToken : "発行される(accountId参照)"
```

<!-- Text fallback: AccountはAccountActionToken(多)とRefreshToken(多)の参照元となる。いずれもaccountIdによるID参照であり、外部キー制約は設けない。 -->

## ルールサマリー(rules.mdから導出)

| ワークフロー | 適用ルール |
|---|---|
| 1. ログイン | BR1.1, BR2.1, BR2.2, BR2.3, BR4.1, BR4.2, BR4.3, BR7.1, BR8.1 |
| 2. アクセストークンのリフレッシュ | BR3.1, BR3.2 |
| 3. ログアウト | BR3.3 |
| 4. アカウント登録完了 | BR6.1, BR6.2, BR6.3, BR5.1, BR8.1 |
| 5. パスワード忘れ対応 | BR6.1, BR6.2, BR5.1, BR8.1 |
| 6. 自己サービスでの氏名・パスワード変更 | BR6.5, BR5.1, BR8.1 |
| 7. 自己サービスでのメールアドレス変更 | BR6.1, BR6.2, BR6.4, BR8.1 |

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T02:26:01Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-03 | Major | construction/auth/functional-design/rules.md > BR8.1・エンティティAccount全体、functional-spec.mdの全ワークフロー | contract-summary.md 契約#4(account-management → auth)は「無効化(disable)の場合、authは該当accountIdの有効な(revoked=falseかつ未期限切れの)RefreshTokenをすべて失効させる(即時のセッション無効化)」と明記し、「auth側のrules.md/functional-spec.mdへの反映はauth Unit側のstage完了ゲートで対応する」と本Unitでの反映を名指しで求めている。しかし現在のrules.md・functional-spec.mdには、account-managementからの契約#4無効化呼び出しを契機としたRefreshToken失効に関するルール・ワークフローが一件も存在しない(BR1.1・BR3.3はいずれもログイン試行時・ログアウト時の挙動であり、無効化時の即時失効は扱っていない)。このままでは無効化された利用者が既存のRefreshTokenで最大7日間セッションを継続できてしまう。 | 契約#4の無効化呼び出しを受けたときの処理をrules.mdに新規BRとして追加し(対象accountIdの有効なRefreshTokenを一括revoked=trueにする)、functional-spec.mdのワークフローにも追記する。entities.mdのAccountエンティティownership記述にも、この呼び出し経路を明記する。 | New |
| R-04 | Major | construction/auth/functional-design/rules.md > BR8.1、functional-spec.mdワークフロー6・7 | contract-summary.md 契約#4は「更新呼び出し(name/emailの変更)によって実際にname/emailが変化した場合、authは呼び出し元(自己サービスの`/api/me/profile`かaccount-managementの契約#4か)に関わらず、自身がAccountInfoChangedEvent(通知イベント契約#10)を発行する」と規定し、ここでも「auth側のrules.md/functional-spec.mdへの反映はauth Unit側のstage完了ゲートで対応する」と本Unitでの反映を名指しで求めている。しかし現在のfunctional-spec.mdはワークフロー6(自己サービス氏名・パスワード変更)・7(自己サービスメールアドレス変更)のみを記述しており、account-managementが契約#4経由でAccount.name/emailを更新した場合にauthがAccountInfoChangedEventを発行するワークフロー・ルールが一件も存在しない。このままでは管理者による氏名・メールアドレス編集(FR6.4.3)時に通知メール(FR6.4(3))が送信されない。 | 契約#4のname/email更新呼び出しを受けたときの処理をrules.mdに新規BRとして追加し(実際に値が変化した場合にAccountInfoChangedEventを発行、changedFieldsを設定)、functional-spec.mdのワークフローにも追記する。 | New |

### Validation Tool Results

本stage定義にはvalidation toolの指定がないため実行していない。traceabilityセンサーは本Unit担当外のFRを大量に誤検知する既知のパターンであるため、依頼のとおり無視した。

### Summary

entities.md・rules.md・functional-spec.md・traceability.jsonの4ファイルは相互に整合しており、requirements.md FR5.5・FR5.6・FR6.1〜FR6.3・FR6.6・NFR7、および契約#11・#19・#20との矛盾も見つからなかった(既知のR-01・R-02は繰延べ事項として今回は再指摘していない)。一方で、契約#4(account-management → auth)がauth Unit側での反映を名指しで求めている2件の未実装事項(無効化時のRefreshToken即時失効、管理者による氏名/メールアドレス編集時のAccountInfoChangedEvent発行)を新たに検出した(R-03・R-04、いずれもMajor)。件数は2件でREADY判定のしきい値(Major 2件以下)の範囲内であり、既存の設計自体に矛盾はないため、この2件の反映を条件に READY とする。
