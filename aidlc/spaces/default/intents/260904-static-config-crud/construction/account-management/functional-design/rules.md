# Business Rules: account-management

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: アカウント新規作成時、name・emailが必須であることを検証する
    category: constraint
    applies_to: AccountView
    trigger: "POST /api/admin/accounts を受けたとき"
    logic: "IF name、emailのいずれかが空 THEN 400を返す"
    violation_behaviour: "400エラー(RFC 7807)"
    source: FR6.4.1

  - id: BR1.2
    statement: BR1.1を通過したら、契約#4(account-management → auth)を呼び出してAccountを新規作成し、registrationTokenを取得する。指定されたemailが既存のAccountと重複する場合、契約#4は例外を返し409として応答する
    category: business
    applies_to: AccountView
    trigger: "BR1.1の検証成功後"
    logic: "契約#4へname・emailを渡し、accountId・status(active)・isAdmin(既定false)・registrationTokenを取得する。emailの一意性検証はAccountの唯一の所有者であるauthが行う(account-management自身では重複チェックを行わない)"
    violation_behaviour: "409エラー(RFC 7807、email重複時。契約#4 Failure behavior参照)"
    source: contract-summary.md #4, FR6.4.1(R-03フォロー、account-management Unit Functional Designレビューより)

  - id: BR1.3
    statement: BR1.2で作成されたaccountIdに対し、契約#21(account-management → permission)を呼び出して初期ロール割り当てを行う
    category: business
    applies_to: AccountView
    trigger: "BR1.2の完了後"
    logic: "リクエストのinitialRoleIds(空配列可)を契約#21へ渡す。存在しないroleIdが含まれる場合は契約#21が例外を返し、400として応答する。この場合BR1.2で作成済みのAccountは残るが、ロールなしの状態になるため、400エラー応答本体に作成済みのaccountIdを含め、管理者がそのアカウントをPUT編集でロール再設定できるようにする(同一emailでの再作成を防ぐため、re-POSTを促さない)"
    violation_behaviour: "400エラー(RFC 7807、accountIdを含む。存在しないroleIdを含む場合)"
    source: contract-summary.md #21, FR6.4.1(R-04フォロー、account-management Unit Functional Designレビューより)

  - id: BR1.4
    statement: BR1.3の完了後、AccountCreatedEvent(accountId・recipientEmail・registrationToken)をnotificationへ発行する
    category: business
    applies_to: AccountView
    trigger: "BR1.3の完了後(ロール割り当ての成否に関わらず、Account自体の作成が成功していれば発行する)"
    logic: "該当なし"
    violation_behaviour: "該当なし(送信失敗はログ記録のみでリトライしない、契約#9 Q12の耐障害性方針)"
    source: contract-summary.md #9, FR6.4.1

  - id: BR1.5
    statement: アカウント作成の成功を、契約#5〜#8(監査ログイベント契約)に基づきactionType=ACCOUNT_CREATEDのAuditableActionOccurredEventとして発行する
    category: business
    applies_to: AccountView
    trigger: "BR1.2(Account作成)が成功したとき"
    logic: "targetDescription = \"account: {accountId}\""
    violation_behaviour: "該当なし(記録失敗は主処理をブロックしない)"
    source: unit-of-work-dependency.md(account-management→audit-log)、contract-summary.md #7(account-management用actionType語彙)

  - id: BR2.1
    statement: アカウント一覧取得は、ページネーション(page・size)とソート(sort)を受け付ける
    category: business
    applies_to: AccountView
    trigger: "GET /api/admin/accounts を受けたとき"
    logic: "既定page=0、size=20とする。page・size・sortを契約#4の一覧取得呼び出しへそのまま渡し、Accountの配列+総件数を取得する。各Accountについて、契約#21から取得したroleIdsを合成してAccountViewとして返す"
    violation_behaviour: "該当なし"
    source: FR6.4.2, contract-summary.md #4(R-01フォロー)

  - id: BR2.2
    statement: アカウント詳細取得は、契約#4の単一取得呼び出しでAccountを取得し、契約#21から取得したroleIdsを合成してAccountViewとして返す
    category: business
    applies_to: AccountView
    trigger: "GET /api/admin/accounts/{id} を受けたとき"
    logic: "存在しないaccountIdの場合、契約#4が例外を返し404として応答する"
    violation_behaviour: "404エラー(RFC 7807、契約#4 Failure behavior参照)"
    source: FR6.4.2, contract-summary.md #4, #17(R-02フォロー)

  - id: BR3.1
    statement: アカウント編集時、name・emailの変更は契約#4のupdate呼び出しで反映し、roleIdsの変更は契約#21のupdate呼び出し(全置換)で反映する
    category: business
    applies_to: AccountView
    trigger: "PUT /api/admin/accounts/{id} を受けたとき"
    logic: "リクエストに含まれるフィールドのみを更新する(部分更新)。emailの変更は自己サービスの確認フローを経由せず、契約#4経由で直接上書きする(functional-design-questions.md Q2)"
    violation_behaviour: "存在しないaccountIdの場合は404(契約#4 Failure behavior)、存在しないroleIdを含む場合は400(契約#21 Failure behavior)"
    source: contract-summary.md #4, #21, FR6.4.3, functional-design-questions.md Q2

  - id: BR3.2
    statement: アカウント編集(BR3.1)の成功を、actionType=ACCOUNT_UPDATEDのAuditableActionOccurredEventとして発行する
    category: business
    applies_to: AccountView
    trigger: "BR3.1が成功したとき"
    logic: "targetDescription = \"account: {accountId}\""
    violation_behaviour: "該当なし"
    source: unit-of-work-dependency.md(account-management→audit-log)、contract-summary.md #7

  - id: BR4.1
    statement: アカウント無効化は、契約#4の無効化呼び出し(disable)を通じて行う(論理削除のみ、物理削除は行わない)。この呼び出しにより、authは該当accountIdの有効なリフレッシュトークンをすべて失効させる
    category: business
    applies_to: AccountView
    trigger: "DELETE /api/admin/accounts/{id} を受けたとき"
    logic: "契約#4へ無効化対象のaccountIdを渡す。account-management自身はリフレッシュトークンの失効処理を行わない(authの責務、契約#4 Provider returns参照)"
    violation_behaviour: "存在しないaccountIdの場合は404(契約#4 Failure behavior)"
    source: contract-summary.md #4, FR6.4.4, functional-design-questions.md Q1

  - id: BR4.2
    statement: アカウント無効化(BR4.1)の成功を、actionType=ACCOUNT_DISABLEDのAuditableActionOccurredEventとして発行する
    category: business
    applies_to: AccountView
    trigger: "BR4.1が成功したとき"
    logic: "targetDescription = \"account: {accountId}\""
    violation_behaviour: "該当なし"
    source: unit-of-work-dependency.md(account-management→audit-log)、contract-summary.md #7

  - id: BR5.1
    statement: account-managementの全操作(アカウント作成・一覧・編集・無効化)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる
    category: authorization
    applies_to: AccountView
    trigger: "account-managementへのいずれかの操作要求を受けたとき"
    logic: "IF アクセストークンのisAdminクレームがtrue THEN 操作を許可する。ELSE 403エラー(RFC 7807)を返す"
    violation_behaviour: "操作は拒否され、403エラーが返される"
    source: FR5.5, FR5.6
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | constraint | アカウント作成の必須項目検証 |
| BR1.2 | business | 契約#4経由のAccount作成 |
| BR1.3 | business | 契約#21経由の初期ロール割り当て |
| BR1.4 | business | AccountCreatedEvent発行 |
| BR1.5 | business | 監査ログ発行(ACCOUNT_CREATED) |
| BR2.1 | business | アカウント一覧のページネーション(契約#4への一覧取得呼び出し) |
| BR2.2 | business | アカウント詳細取得(契約#4への単一取得呼び出し) |
| BR3.1 | business | アカウント編集(契約#4・#21への振り分け、email直接上書き) |
| BR3.2 | business | 監査ログ発行(ACCOUNT_UPDATED) |
| BR4.1 | business | アカウント無効化(契約#4経由、authがトークン失効) |
| BR4.2 | business | 監査ログ発行(ACCOUNT_DISABLED) |
| BR5.1 | authorization | isAdminクレーム必須 |
