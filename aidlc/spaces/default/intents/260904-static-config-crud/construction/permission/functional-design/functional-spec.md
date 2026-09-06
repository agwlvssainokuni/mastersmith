# Functional Specification: permission

## ワークフロー

### 1. ロール・グループの定義

1. 管理者(isAdminクレーム保持者)が`POST/PUT /api/admin/roles`または`POST/PUT /api/admin/groups`でRole・Groupを作成・変更する。
2. name一意性を検証する(BR1.1)。違反時は400エラー(RFC 7807)を返す。

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
3. 検証を通過したら、対象accountIdの直接RoleAssignment(assigneeType=user)をすべて削除し、配列の各roleIdについて新規に作成する(全置換)。グループ経由の割当(GroupMembership)には触れない。
4. 更新後の直接RoleAssignment一覧(roleId配列)を返す。

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
| 2. グループメンバー構成の管理 | BR1.2 |
| 3. テーブル単位・カラム単位権限の設定 | BR2.1, BR2.2 |
| 4. ロールの割り当て | BR3.1, BR3.3 |
| 5. 契約#21によるアカウントの初期ロール割り当て・変更 | BR3.2 |
| 6. 自身の切替可能ロール一覧の取得 | BR4.1 |
| 7. テーブル単位・カラム単位の権限確認 | BR5.1, BR5.2 |

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-06T09:32:52Z
**Iteration:** 2

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | construction/permission/functional-design/rules.md > BR3.2、functional-spec.md > ワークフロー5 手順3 | BR3.2(契約#21の全置換ロジック)は「配列の各roleIdについて新規に作成する」と規定するのみで、渡されたroleId配列自体に重複roleIdが含まれる場合の挙動を定めていない。RoleAssignmentの`entity_constraints`(entities.md)は(roleId, accountId)の組を一意と定めており、素朴な実装(配列を単純にループしてINSERT)では2件目のINSERTで一意制約違反が発生しうる。BR1.2・BR3.3では重複割当を冪等として明示的に扱っているのに対し、BR3.2にはこの配慮が欠けている。 | BR3.2に「配列内の重複roleIdは去重(dedup)してから処理する」旨、またはaccount-management側で去重済みの配列のみを渡す契約上の前提を明記する一文を追加する。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| aidlc-sensor-required-sections | PASS(既報告、3ファイルとも合格) | 必須セクション構成に問題なし |
| aidlc-sensor-traceability / aidlc-sensor-upstream-coverage | 既知の制約により、stories.mdが本プロジェクト全体でSKIPされているためのFRフォールバックで、本Unit固有のFR以外の大半が`missing_from_upstream_ids`として検出される見込み | 新規欠陥として扱わない(プロジェクト全体の既知制約) |

### Summary

iteration 1で指摘されたCritical 1件(契約#21のProvider側実装欠落)・Major 1件(RoleAssignment重複割当時の挙動未定義)・Minor 1件(ER図のPK/FKタグ欠落)は、それぞれBR3.2の新設、BR3.3の新設、mermaid ER図の複合主キータグ追加により解消を独立に確認した。account-management側のBR1.3(初期ロール割り当て)・BR3.1(編集時のロール変更)の呼び出しパターンともBR3.2は矛盾なく対応し、BR3.2とBR4.1(グループ経由ロールの和集合計算)の役割分担、BR3.3とBR3.1(排他検証)の共存、ワークフロー番号の繰り下げ(5〜7)もfunctional-spec.md内で一貫している。unit-of-work-dependency.mdのauth→permission・account-management→permissionエッジも循環を生んでおらず健全。新たに、契約#21で渡されるroleId配列内の重複値に対するBR3.2の挙動未定義という1件のMinor指摘のみを追加する。Critical/Major指摘は0件であり、READY水準を満たす。
