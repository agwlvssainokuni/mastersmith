# Functional Specification: account-management

## ワークフロー

### 1. アカウントの新規作成

1. 管理者が `POST /api/admin/accounts` でname・email・initialRoleIds(任意)を送信する(BR5.1で管理者権限を検証)。
2. name・emailの必須項目を検証する(BR1.1)。
3. 契約#4(account-management → auth)を呼び出し、Accountを作成してregistrationTokenを取得する(BR1.2)。emailが既存のAccountと重複する場合、契約#4は例外を返し409を返す(この場合、以降の手順は実行されない)。
4. 契約#21(account-management → permission)を呼び出し、initialRoleIdsを初期ロールとして割り当てる(BR1.3)。存在しないroleIdが含まれる場合、作成済みのaccountIdを含めた400を返す(以降の手順は実行されないが、Account自体は残る)。
5. AccountCreatedEvent(accountId・recipientEmail・registrationToken)をnotificationへ発行する(BR1.4)。NotificationComponentがこれを購読し、アカウント作成通知メールを送信する(FR6.4(1))。
6. 監査ログイベント(actionType=ACCOUNT_CREATED、BR1.5)を発行する。
7. 作成されたAccountView(accountId・name・email・status・isAdmin・roleIds)を返す。

### 2. アカウント一覧の参照

1. 管理者が `GET /api/admin/accounts` を呼び出す(BR5.1)。
2. page・size・sortパラメータを契約#4の一覧取得呼び出しへそのまま渡し、Accountの配列+総件数を取得する(BR2.1)。
3. 各Accountについて契約#21からroleIdsを取得し、AccountViewとして合成する。
4. 一覧を返す。

### 3. アカウント詳細の参照

1. 管理者が `GET /api/admin/accounts/{id}` を呼び出す(BR5.1)。
2. 契約#4の単一取得呼び出しでAccountを取得する(BR2.2)。存在しない場合は404を返す。
3. 契約#21からroleIdsを取得し、AccountViewとして合成して返す。

### 4. アカウント情報の編集

1. 管理者が `PUT /api/admin/accounts/{id}` でname・email・roleIdsの一部または全部を送信する(BR5.1)。
2. 送信されたフィールドに応じて、契約#4(name・email)・契約#21(roleIds、全置換)へ振り分けて更新する(BR3.1)。emailの変更は自己サービスの確認フローを経由せず直接反映される(functional-design-questions.md Q2)。
3. 監査ログイベント(actionType=ACCOUNT_UPDATED、BR3.2)を発行する。
4. 更新後のAccountViewを返す。

### 5. アカウントの無効化

1. 管理者が `DELETE /api/admin/accounts/{id}` を呼び出す(BR5.1)。
2. 契約#4の無効化呼び出しを行う(BR4.1)。論理削除のみで、Accountレコード自体は保持される(監査ログ等の参照整合性のため、FR6.4.4)。authはこの呼び出しに応じて、該当accountIdの有効なリフレッシュトークンをすべて失効させる(functional-design-questions.md Q1)。
3. 監査ログイベント(actionType=ACCOUNT_DISABLED、BR4.2)を発行する。
4. 204 No Contentを返す。

## 状態遷移

account-management自身は永続エンティティを持たないため、状態遷移図は存在しない(Account.statusの状態遷移はauth Unitのfunctional-spec.mdに帰属する)。

## エンティティ関連図

account-managementは永続エンティティを持たないため、ER図は存在しない(entities.md参照)。

## ルールサマリー(rules.mdから導出)

| ワークフロー | 適用ルール |
|---|---|
| 1. アカウントの新規作成 | BR1.1, BR1.2, BR1.3, BR1.4, BR1.5, BR5.1 |
| 2. アカウント一覧の参照 | BR2.1, BR5.1 |
| 3. アカウント詳細の参照 | BR2.2, BR5.1 |
| 4. アカウント情報の編集 | BR3.1, BR3.2, BR5.1 |
| 5. アカウントの無効化 | BR4.1, BR4.2, BR5.1 |

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T02:24:59Z
**Iteration:** 1

### Findings

指摘なし(既知の繰延べ事項R-05〜R-07を除く)

### Summary

entities.md・rules.md・functional-spec.md・traceability.jsonの4ファイルは相互に整合しており、requirements.md FR6.4.1〜FR6.4.4、contract-summary.md契約#4(account-management→auth)・#17(REST API)・#21(account-management→permission)のいずれとも矛盾しない。BR1.1〜BR5.1の各ルールが対応するAPI操作・契約呼び出し・エラー応答(400/404/409/403)と正しく対応し、traceability.jsonのカバレッジもFR6.4.1〜FR6.4.4を過不足なく満たしている。新規のCritical/Major欠陥は検出されなかった。
