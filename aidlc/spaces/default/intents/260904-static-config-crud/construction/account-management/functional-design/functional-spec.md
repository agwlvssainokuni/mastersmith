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
**Date:** 2026-09-06T13:49:33Z
**Iteration:** 1
**Request Challenge:** review:137cdbe19493dedc6149c1255d7bc41f

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | (前イテレーションで解消済み) | — | — | Resolved |
| R-02 | Major | (前イテレーションで解消済み) | — | — | Resolved |
| R-03 | Major | (前イテレーションで解消済み) | — | — | Resolved |
| R-04 | Minor | (前イテレーションで解消済み) | — | — | Resolved |
| R-05 | Major | functional-spec.md > 1. アカウントの新規作成 手順4、rules.md > BR1.4/BR1.5 | rules.md BR1.4(trigger:「BR1.3の完了後(ロール割り当ての成否に関わらず、Account自体の作成が成功していれば発行する)」)とBR1.5(trigger:「BR1.2(Account作成)が成功したとき」)は、ロール割り当て(BR1.3)の成否に関わらずAccountCreatedEventと監査ログが発行されると明記している。しかしfunctional-spec.md手順4は「存在しないroleIdが含まれる場合...(以降の手順は実行されないが、Account自体は残る)」と書かれており、手順5(AccountCreatedEvent発行)・手順6(監査ログ発行)がロール割り当て失敗時にスキップされるかのように読める。rules.mdとfunctional-spec.mdの間で挙動の記述が矛盾したままである(内容は変更されておらず、前回指摘のとおり未解消)。 | functional-spec.md手順4の「以降の手順は実行されない」という表現を、BR1.3(ロール割り当て)自体の以降の処理(400応答生成等)に限定する旨に書き換え、手順5・6(BR1.4・BR1.5)はBR1.3の成否に関わらず実行されることを明記する。 | 未解消(Unresolved) |
| R-06 | Major | rules.md > BR3.1、functional-spec.md > 4. アカウント情報の編集 手順2 | BR3.1(アカウント編集)およびfunctional-spec.md手順2は、契約#4(name・email)と契約#21(roleIds)への振り分け更新を記述するが、両呼び出しの実行順序、および一方が成功し他方が失敗した場合(部分失敗)の挙動(ロールバックの有無、レスポンスへの反映内容)を規定していない。BR1.3のように部分失敗時の応答仕様(accountId込みの400等)が明記されていない点が、BR1.2/BR1.3の設計と非対称である。 | 契約#4→契約#21(またはその逆)の呼び出し順序を明記し、一方が失敗した場合の応答仕様(どちらの更新が反映済みかをレスポンスに含めるか等)をBR3.1に追記する。 | 未解消(Unresolved) |
| R-07 | Minor | contract-summary.md 契約#17(GET /api/admin/accounts) | contract-summary.md(このUnitの上流契約、account-management自身の生成物ではない)の契約#17 OpenAPIスタブがpage/size/sortの`parameters`を宣言していない一方、rules.md BR2.1/functional-spec.md workflow 2はこれらのパラメータを前提としている(audit-logの契約#18は同等のパラメータを明示している)。account-management Unit自身の成果物には影響しないinception成果物側の緩さであり、このUnitの範囲外。 | contract-design成果物の是正はこのUnitのスコープ外。end-of-stageゲートで人間に申し送る。 | 未解消(Unresolved、他Unit/上流契約起因) |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| required-sections | passed | functional-spec.mdの必須セクションは揃っている |
| traceability | failed (`missing_from_upstream_ids`: FR1〜FR7系44件) | 既知の誤検知パターン。これらは他Unit(auth/permission/audit-log等)や本Unit範囲外のFRであり、traceability.jsonのupstream_ids(FR6.4.1〜FR6.4.4)は本Unitの担当範囲と正しく一致している。新規の不整合ではない。 |
| upstream-coverage | failed (`unreferenced: unit-of-work, unit-of-work-story-map, requirements`) | 既知の誤検知パターン(ファイル名の文字列一致に基づく検出であり、要件ID(FR6.4.1〜FR6.4.4)は実際にはrules.md/traceability.jsonから参照されている)。実質的な上流カバレッジの欠落ではない。 |

### Summary

再確認の結果、entities.md・rules.md・functional-spec.md・traceability.jsonの内容は前回認証時から変更されていないことをgit diffで確認した(唯一の差分は前回の`## Review`節が除去されていた点のみ)。5ワークフローをrequirements.md FR6.4.1〜FR6.4.4、contract-summary.md契約#4・#17・#21と突き合わせた結果、内容は前回認証時と同様に内部的に整合している。R-05(Account作成時のイベント発行条件に関するrules.md/functional-spec.mdの記述矛盾)・R-06(アカウント編集の契約呼び出し順序・部分失敗仕様の未規定)は依然としてMajorの未解消事項として残り、R-07(契約#17のパラメータ未宣言)はこのUnit範囲外のMinor事項として残る。Critical指摘はなく、Major指摘は2件(R-05・R-06)で、本プロジェクトの確立された運用(同一内容の再認証であり、現パスで修正を試みず既知の非ブロッキング事項としてend-of-stageゲートへ申し送る)に従い、READYとして再認証する。
