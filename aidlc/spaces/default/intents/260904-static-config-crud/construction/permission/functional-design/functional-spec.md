# Functional Specification: permission

## ワークフロー

### 1. ロール・グループの定義

1. 管理者(isAdminクレーム保持者)が`POST /api/admin/roles`または`POST /api/admin/groups`でRole・Groupを作成する。
2. name一意性を検証する(BR1.1)。違反時は400エラー(RFC 7807)を返す。

### 1a. ロール・グループの名称変更

1. 管理者が`PUT /api/admin/roles/{roleId}`または`PUT /api/admin/groups/{groupId}`でRole・Groupの名称を変更する(BR1.3、frontend-admin Unit Functional Designレビューより新設)。
2. 対象roleId/groupIdの存在を確認する。存在しなければ404を返す。
3. name一意性を検証する(BR1.1)。違反時は400エラー(RFC 7807)を返す。
4. 検証を通過したら、name属性のみを更新する。

### 1b. ロール・グループの削除

1. 管理者が`DELETE /api/admin/roles/{roleId}`または`DELETE /api/admin/groups/{groupId}`でRole・Groupを削除する(BR1.4・BR1.5、frontend-admin Unit Functional Designレビューより新設)。
2. 対象roleId/groupIdの存在を確認する。存在しなければ404を返す。
3. ロールの場合はRoleAssignment(roleId=対象)、グループの場合はRoleAssignment(assigneeType=group, groupId=対象)の存在を確認する。1件以上存在すれば409を返し削除を拒否する。
4. 参照が存在しなければ、ロールの場合はTablePermission・ColumnPermission、グループの場合はGroupMembershipを道連れに削除したうえで、対象Role/Group自体を削除する。

### 2. グループメンバー構成の管理

1. 管理者が`GET /api/admin/groups/{groupId}/members`で対象Groupの所属Account一覧を取得する。
2. 管理者が`POST /api/admin/groups/{groupId}/members`で対象GroupへAccountを追加する(GroupMembershipの作成)。既に所属済みのAccountを重複追加しようとした場合は既存の所属を維持し何もしない(冪等、BR1.2)。
3. 管理者が`DELETE /api/admin/groups/{groupId}/members?accountId=<accountId>`で対象GroupからAccountを除く(GroupMembershipの削除)。

### 3. テーブル単位・カラム単位権限の設定

1. 管理者が対象のRole・テーブルについて、TablePermission(canList/canView/canCreate/canEdit/canDelete)を設定する(BR2.1)。
2. 管理者が対象のRole・テーブル・カラムについて、ColumnPermission(accessLevel: editable/readonly/hidden)を設定する(BR2.2)。

### 4. ロールの割り当て

1. 管理者が`POST /api/admin/roles/{roleId}/assignments`でロールをAccountまたはGroupへ割り当てる。
2. assigneeTypeに応じてaccountId・groupIdのいずれか一方のみが指定されていることを検証する(BR3.1)。違反時は400エラー(RFC 7807)を返す。
3. 指定された(roleId, accountId)または(roleId, groupId)の組が既に存在する場合、既存の割当を維持し何もしない(BR3.3、冪等)。存在しない場合は新規に作成する。

### 5. 契約#21(account-management → permission)によるアカウントの初期ロール割り当て・変更

1. account-managementが、アカウント新規作成時(初期ロール割り当て)またはアカウント編集時(割り当てロール変更)に、accountIdとroleId配列を渡して本Unitを呼び出す(契約#21)。
2. 渡されたroleId配列に存在しないロールが含まれていないか検証する(BR3.2)。含まれる場合は例外を送出し、account-management側で400として応答される。
3. 検証を通過したら、配列内の重複roleIdを去重(dedup)したうえで、対象accountIdの直接RoleAssignment(assigneeType=user)をすべて削除し、去重後の各roleIdについて新規に作成する(全置換。iteration 2レビューR-01フォロー)。グループ経由の割当(GroupMembership)には触れない。
4. 更新後の直接RoleAssignment一覧(去重済みroleId配列)を返す。

### 6. 自身の切替可能ロール一覧の取得(`GET /api/me/roles`)

1. 利用者(管理者・非管理者を問わず全利用者)が自身の切替可能ロール一覧を要求する。
2. 自身のAccountに直接割り当てられたロール(RoleAssignment、assigneeType=user)を取得する。
3. 自身が所属するGroup(GroupMembership)を取得し、それらのGroupに割り当てられたロール(RoleAssignment、assigneeType=group)を取得する。
4. 手順2・3の和集合を切替可能ロール一覧として返す(BR4.1)。
5. 利用者は返された一覧から作業中ロールを選択し、以後のリクエストで`X-Active-Role`ヘッダー(contract-summary.md共通規約)として送る。ロール切替自体はクライアント側で完結し、本Unitへの追加の呼び出しは発生しない。

**ログイン時のアクセストークンrolesクレームとの関係(contract-summary.md #20)**: 手順2・3と全く同じ「有効なロール集合の計算」(直接割り当て+グループ経由の割り当ての和集合)は、auth Unitのログイン処理でも必要になる。auth Unitはログイン成功時にpermission Unitをプロセス内呼び出しし、対象accountIdの有効なロールID一覧を取得してアクセストークンのrolesクレームへそのまま埋め込む(contract-summary.md #20で新設)。`GET /api/me/roles`(本ワークフロー)とこの内部呼び出しは、同じロジックを異なる2つの消費者(利用者本人のロール切替UI、auth Unitのトークン発行処理)に提供する。

### 7. テーブル単位・カラム単位の権限確認(dynamic-data-access Unitからの呼び出し、contract-summary.md #3)

1. dynamic-data-access Unitが、対象テーブル・対象アクション(list/view/create/edit/delete)・判定対象ロールID(`X-Active-Role`由来)を渡してテーブル単位権限を問い合わせる。
2. 対象ロール・テーブルのTablePermissionレコードを検索する。存在すれば該当操作のcanXxx値を返す。存在しなければすべての操作を拒否と返す(BR5.1)。
3. カラム単位の権限が必要な場合、対象ロール・テーブル・カラムのColumnPermissionレコードを検索する。存在すればそのaccessLevelを返す。存在しなければeditableを返す(BR5.2)。

### 8. 契約#22(config-management → permission)によるcanList権限tableId集合の取得(frontend-core Unit Functional Designより新設)

1. config-managementが、`GET /api/menu`(契約#23)の処理中に、判定対象のroleId(`X-Active-Role`ヘッダー由来)を渡してcanList権限を持つtableId集合を問い合わせる。
2. 対象roleIdについて、TablePermission.canList=trueのレコードを検索し、該当するtableIdの集合を返す(BR5.3)。TablePermissionが1件も存在しないテーブルはBR5.1のデフォルト拒否により集合に含まれない。該当tableIdが0件の場合は空集合を返す(エラー条件ではない)。

## 状態遷移

本Unitの各エンティティ(Role・Group・GroupMembership・RoleAssignment・TablePermission・ColumnPermission)はいずれも意味のある状態遷移(ライフサイクル)を持たない。作成・更新・削除の単純なCRUD対象である。

## エンティティ関連図(ER図、entities.mdから導出)

```mermaid
erDiagram
    Role {
        string roleId PK
        string name
        string description
    }
    Group {
        string groupId PK
        string name
    }
    GroupMembership {
        string accountId PK "複合主キーの一部。Account(auth Unit)へのID参照のみ"
        string groupId PK,FK "複合主キーの一部。(accountId, groupId)の組で一意(entities.md参照、R-03フォロー)"
    }
    RoleAssignment {
        string assignmentId PK
        string roleId FK
        string assigneeType "user | group"
        string accountId "nullable、Account(auth Unit)へのID参照のみ"
        string groupId "nullable"
    }
    TablePermission {
        string tablePermissionId PK
        string roleId FK
        string tableId "TableConfig(config-management Unit)へのID参照のみ"
        boolean canList
        boolean canView
        boolean canCreate
        boolean canEdit
        boolean canDelete
    }
    ColumnPermission {
        string columnPermissionId PK
        string roleId FK
        string tableId "TableConfig(config-management Unit)へのID参照のみ"
        string columnName
        string accessLevel "既定editable"
    }

    Group ||--o{ GroupMembership : "所属するAccount"
    Role ||--o{ RoleAssignment : "割り当てられる"
    Group ||--o{ RoleAssignment : "割り当てられる(assigneeType=groupのとき)"
    Role ||--o{ TablePermission : "設定を持つ"
    Role ||--o{ ColumnPermission : "設定を持つ"
```

<!-- Text fallback: RoleとGroupは独立したエンティティ。GroupMembershipはGroupに対して多、Accountへの参照を1件持つ(外部キー制約なし)。RoleAssignmentはRoleに対して多、assigneeType=groupのときのみGroupへの参照を持ち、assigneeType=userのときはAccountへの参照(外部キー制約なし)のみを持つ(排他)。TablePermission・ColumnPermissionはいずれもRoleに対して多で、TableConfig(config-management Unit)へのID参照を持つ。 -->

## ルールサマリー(rules.mdから導出)

| ワークフロー | 適用ルール |
|---|---|
| 1. ロール・グループの定義 | BR1.1 |
| 1a. ロール・グループの名称変更 | BR1.1, BR1.3 |
| 1b. ロール・グループの削除 | BR1.4, BR1.5 |
| 2. グループメンバー構成の管理 | BR1.2 |
| 3. テーブル単位・カラム単位権限の設定 | BR2.1, BR2.2 |
| 4. ロールの割り当て | BR3.1, BR3.3 |
| 5. 契約#21によるアカウントの初期ロール割り当て・変更 | BR3.2 |
| 6. 自身の切替可能ロール一覧の取得 | BR4.1 |
| 7. テーブル単位・カラム単位の権限確認 | BR5.1, BR5.2 |
| 8. 契約#22によるcanList権限tableId集合の取得 | BR5.3 |

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T01:39:38Z
**Iteration:** 1

### Findings

指摘なし

### Summary

今回の唯一の変更点であるBR5.3(契約#22対応)を精査した。rules.md BR5.3のConsumer passes/Provider returns/Failure behaviorは、contract-summary.mdの契約#22定義(「判定対象のroleId」「当該roleIdがcanList=trueを持つtableIdの集合」「該当なしエラー条件ではない」)と文言レベルで一致している。BR5.3はBR5.1(TablePermission未設定はデフォルト拒否)を明示的に参照し、「未設定のテーブルは集合に含まれない」という記述も矛盾なく整合している。functional-spec.mdワークフロー8はBR5.3のロジック(対象tableId集合の内包表記、空集合はエラーでない旨)をそのまま反映しており齟齬はない。traceability.jsonのreverseエントリ(BR5.3)は、config-management側のFR3.4(requirements.mdに実在確認済み)を支える契約起源のルールであり、permission自身のFRには対応しないという説明で、Mandatedルール(cross-unit境界の扱い)にも整合する。既存のBR1.1〜BR6.1・ワークフロー1〜7・エンティティモデル(TablePermission.canListを含む)には、今回の追加によって生じた矛盾や重複定義は見当たらない。よってREADYと判定する。
