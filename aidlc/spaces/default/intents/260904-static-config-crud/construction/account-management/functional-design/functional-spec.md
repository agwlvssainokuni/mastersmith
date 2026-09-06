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
**Date:** 2026-09-06T07:52:02Z
**Iteration:** 2

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major (Iteration 1、Resolved) | contract-summary.md 契約#4 | 一覧取得(page/size/sort)の呼び出し形状が未定義だった | 契約#4のConsumer passes/Provider returnsに一覧取得のページネーション条件と配列+総件数の返却形状を追記 | Resolved |
| R-02 | Major (Iteration 1、Resolved) | rules.md BR2.2、functional-spec.md ワークフロー3、契約#4 | 契約#17のGET /api/admin/accounts/{id}に対応するBR・ワークフローが存在しなかった | 契約#4に単一取得対象のaccountIdを追記し、BR2.2・ワークフロー3(アカウント詳細の参照)を新設 | Resolved |
| R-03 | Major (Iteration 1、Resolved) | rules.md BR1.2、functional-spec.md ワークフロー1手順3 | アカウント作成時のemail重複検証が本Unit・契約#4いずれにも規定されていなかった | 契約#4のFailure behaviorに409応答を追記し、BR1.2・ワークフロー1手順3に反映 | Resolved |
| R-04 | Minor (Iteration 1、Resolved) | rules.md BR1.3、functional-spec.md ワークフロー1手順4 | 初期ロール割り当て失敗時の400応答にaccountIdを含めるか未規定だった | BR1.3・ワークフロー1手順4に「400エラー応答本体に作成済みのaccountIdを含める」ことを明記 | Resolved |
| R-05 | Major | aidlc/spaces/default/intents/260904-static-config-crud/construction/account-management/functional-design/functional-spec.md > ワークフロー1(アカウントの新規作成)手順4〜6、および rules.md > BR1.4・BR1.5 | rules.md BR1.4のtriggerは「BR1.3の完了後(ロール割り当ての成否に関わらず、Account自体の作成が成功していれば発行する)」と明記し、BR1.5のtriggerも「BR1.2(Account作成)が成功したとき」とBR1.3非依存で規定している。ところがfunctional-spec.mdワークフロー1の手順4は「存在しないroleIdが含まれる場合...400を返す(**以降の手順は実行されないが**、Account自体は残る)」としており、手順5(BR1.4のAccountCreatedEvent発行)・手順6(BR1.5の監査ログ発行)が実行されないと読める。rules.mdの規定(ロール割り当て失敗時もAccountCreatedEventと監査ログは発行される)とfunctional-spec.mdの逐次手順記述(ロール割り当て失敗で以降の手順が止まる)が直接矛盾しており、この逐次手順書だけを見た開発者は通知・監査ログ発行をスキップする実装をしてしまう。 | functional-spec.mdワークフロー1の手順4の「以降の手順は実行されない」を、BR1.3(ロール割り当て)の失敗時に限った限定表現に修正する(例:「手順5終了までの間、契約#21呼び出し以降のロール割り当てに関する処理のみを中断し、AccountCreatedEvent発行(手順5)・監査ログ発行(手順6)はAccount作成(手順3)が成功していれば実行する」)。rules.md BR1.4・BR1.5の規定と矛盾しない形にワークフロー記述を訂正する。 | New |
| R-06 | Major | aidlc/spaces/default/intents/260904-static-config-crud/construction/account-management/functional-design/rules.md > BR3.1、functional-spec.md > ワークフロー4(アカウント情報の編集)手順2 | BR3.1は「name・emailの変更は契約#4のupdate呼び出しで反映し、roleIdsの変更は契約#21のupdate呼び出し(全置換)で反映する」とし、violation_behaviourで404(契約#4)・400(契約#21)を個別に規定するが、2つの契約呼び出しの**実行順序**と、一方が成功し他方が失敗した場合の**部分適用の扱い**(例: 契約#4のname/email更新が成功した後に契約#21のroleIds更新が無効なroleIdで400になった場合、name/emailの変更は既に反映済みのままクライアントには400のみが返る)が、rules.md・functional-spec.mdのいずれにも規定されていない。アカウント作成フロー(BR1.2→BR1.3)では契約呼び出しの順序とロールバック不可の扱いがBR1.3/ワークフロー手順4で明示されているのに対し、編集フロー(BR3.1)では同種の部分失敗ケースが未規定であり、開発者が実装時に順序・整合性方針を推測せざるを得ない。 | BR3.1に契約#4→契約#21(またはその逆)の呼び出し順序と、一方が成功し他方が失敗した場合にクライアントへ返すエラー本体の内容(例: どちらのフィールドが実際に反映されたか)を明記する。functional-spec.mdワークフロー4手順2にも同様に反映する。 | New |
| R-07 | Minor | aidlc/spaces/default/intents/260904-static-config-crud/inception/contract-design/contract-summary.md > account-management(#17) `GET /api/admin/accounts` | rules.md BR2.1・functional-spec.mdワークフロー2はGET /api/admin/accountsがpage/size/sortクエリパラメータを受け付けることを前提としているが、契約#17のOpenAPIスタブはこのエンドポイントにparametersを一切宣言していない(同じ契約-summary.md内のaudit-log #18のGETエンドポイントはpage/size/sortをparametersとして明示的に宣言しており、書き方の粒度に差がある)。今回のiteration 2の修正対象(契約#4)ではなく既存の契約#17側の記述レベルの粗さであり、実装を妨げるものではないが、契約定義の一貫性という観点では望ましくない。 | 契約#17のGET /api/admin/accountsにpage/size/sortのparametersを追記し、audit-log #18と記述粒度を揃える(account-management側での修正、または次回のcontract-summary.mdメンテナンス時の対応で可)。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| aidlc-sensor-traceability / aidlc-sensor-upstream-coverage | 既知の制約により、stories.mdがプロジェクト全体でSKIPされているためのFRフォールバックで、FR1系〜FR5系・FR7系の大半が`missing_from_upstream_ids`として検出される想定 | 本Unit(account-management、FR6.4系)固有の欠陥ではない。事前に合意された既知の制約として記録し、新規欠陥として扱わない |
| 手動クロスリファレンス検証(契約#4↔BR1.2/BR1.3/BR2.1/BR2.2/BR3.1/BR4.1、契約#21↔BR1.3/BR3.1、契約#17↔ワークフロー1〜5、unit-of-work-dependency.md循環チェック) | 契約#4の一覧取得・単一取得・email重複検証の追記はBR2.1・BR2.2・BR1.2・ワークフロー1〜3と矛盾なく対応。ワークフロー番号の繰り下げ(旧3→4、旧4→5)はfunctional-spec.md内(本文・ルールサマリー表とも)で一貫。BR1.3の400応答へのaccountId追加はワークフロー1手順4に正しく反映。account-management→permissionの依存エッジ追加後もunit-of-work-dependency.mdの依存レベル(account-managementはレベル2、permissionはレベル0)上、循環は発生しない | R-05・R-06の2件を除き、iteration 1指摘4件はすべて独立検証の上で解消を確認 |

### Summary

Iteration 1で指摘したMajor 3件・Minor 1件はいずれも正しく修正されており、契約#4・BR2.1・BR2.2・BR1.2・ワークフロー1〜3の対応関係、ワークフロー番号の繰り下げ、BR1.3の400応答へのaccountId反映を独立検証の上で確認した。一方で、新規にMajor 2件(R-05: 作成フローでロール割り当て失敗時に通知・監査ログイベントが発行されるかどうかについてrules.mdとfunctional-spec.mdが直接矛盾、R-06: 編集フローで契約#4/#21呼び出しの順序・部分失敗時の扱いが未規定)を検出した。いずれもUnit境界を越える設計破綻ではなく、当該Unit内の仕様記述レベルの矛盾・欠落であり、Major 2件・Critical 0件のためREADY判定とするが、次のConstruction作業(code-generation)に進む前にR-05・R-06の記述訂正を推奨する。
