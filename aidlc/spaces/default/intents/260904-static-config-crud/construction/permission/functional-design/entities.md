# Entities: permission

domain-design/components.mdのPermissionComponentが所有する5エンティティ(Role・Group・RoleAssignment・TablePermission・ColumnPermission)に加え、functional-design-questions.md Q1(グループ経由のロール継承)を実現するために必要な、Groupのメンバー構成を表すGroupMembershipエンティティを新設する(domain-design/components.mdはGroupのメンバー構成そのものをモデル化していなかったため、この段階で補完する)。

## エンティティモデル(機械可読)

```yaml
entities:
  - name: Role
    description: テーブル単位・カラム単位の業務データ権限をまとめる単位
    attributes:
      - name: roleId
        type: identifier
        required: true
        unique: true
      - name: name
        type: string
        required: true
        unique: true
      - name: description
        type: string
        required: false
    entity_constraints:
      - "nameは一意(FR5.1〜FR5.4の対象となるロール名の重複を避ける)"
    relationships: []

  - name: Group
    description: 複数のAccountをまとめてロールを割り当てるための単位
    attributes:
      - name: groupId
        type: identifier
        required: true
        unique: true
      - name: name
        type: string
        required: true
        unique: true
    entity_constraints:
      - "nameは一意"
    relationships: []

  - name: GroupMembership
    description: "どのAccountがどのGroupに所属するかを表す(Account:Group = 多対多)。domain-design/components.mdでは明示されていなかったが、Groupへのロール割り当て(FR5.3)が実際に機能するために必要な関連であり、本ステージで補完する"
    attributes:
      - name: accountId
        type: string
        required: true
        references:
          entity: Account
          owned_by: auth Unit(共有スキーマ経由でaccount-managementも操作するAccount。contract-summary.md #4/#19参照)
          relationship: "各GroupMembershipは1つのAccountを参照する(ID参照のみ、外部キー制約なし。auth/account-managementと独立に進化できるようにするため。audit-log Unitの設計判断と同じ考え方)"
      - name: groupId
        type: string
        required: true
        references:
          entity: Group
          owned_by: permission Unit(このUnit自身)
          relationship: "各GroupMembershipは1つのGroupに属する"
    entity_constraints:
      - "(accountId, groupId)の組は一意(同一Accountを同一Groupに重複して所属させない)"
    relationships:
      - target: Group
        cardinality: "N:1"
        direction: "GroupMembership → Group"

  - name: RoleAssignment
    description: ロールをAccountまたはGroupへ割り当てる
    attributes:
      - name: assignmentId
        type: identifier
        required: true
        unique: true
      - name: roleId
        type: string
        required: true
        references:
          entity: Role
          owned_by: permission Unit(このUnit自身)
          relationship: "各RoleAssignmentは1つのRoleを参照する"
      - name: assigneeType
        type: string
        required: true
        allowed_values: ["user", "group"]
      - name: accountId
        type: string
        required: false
        constraints: ["assigneeType=userのときのみ必須(値あり)。assigneeType=groupのときは常にnull"]
        references:
          entity: Account
          owned_by: auth Unit(共有スキーマ経由でaccount-managementも操作するAccount。contract-summary.md #4/#19参照)
          relationship: "assigneeType=userの場合、割り当て先のAccountをID参照のみで指す(外部キー制約なし)"
      - name: groupId
        type: string
        required: false
        constraints: ["assigneeType=groupのときのみ必須(値あり)。assigneeType=userのときは常にnull"]
        references:
          entity: Group
          owned_by: permission Unit(このUnit自身)
          relationship: "assigneeType=groupの場合、割り当て先のGroupを参照する"
    entity_constraints:
      - "accountIdとgroupIdは排他(どちらか一方のみが値を持つ。BR3.1参照)。この排他性は書き込み時のアプリケーション層バリデーション(BR3.1)で保証する"
      - "(roleId, accountId)または(roleId, groupId)の組は一意(同一ロールを同一Account/Groupへ重複割り当てしない)"
      - "読み取り時はassigneeTypeを正とする。万一assigneeTypeと実際に値が入っているフィールドが不一致の行が見つかった場合(バグやデータの手動編集等)、データ不整合として扱いエラーログに記録する。書き込み時バリデーション(BR3.1)がある限り通常は発生しない"
    relationships:
      - target: Group
        cardinality: "N:0..1"
        direction: "RoleAssignment → Group(assigneeType=groupのときのみ)"

  - name: TablePermission
    description: ロールが持つテーブル単位の業務データ操作権限
    attributes:
      - name: tablePermissionId
        type: identifier
        required: true
        unique: true
      - name: roleId
        type: string
        required: true
        references:
          entity: Role
          owned_by: permission Unit(このUnit自身)
          relationship: "各TablePermissionは1つのRoleに対する設定を表す"
      - name: tableId
        type: string
        required: true
        constraints: ["domain-design/components.mdの属性一覧にはtableIdが明記されていなかったが、関連の説明文(「各TablePermissionは1つの設定済みテーブルに対する権限を表す」)自体がテーブルへの帰属を前提としており、その意図を明示的な属性として補完する(ColumnPermission.tableIdと同じ理由)"]
        references:
          entity: TableConfig
          owned_by: config-management Unit
          relationship: "各TablePermissionは1つの設定済みテーブルに対する権限を表す(ID参照のみ、外部キー制約なし。config-managementと独立に進化できるようにするため)"
      - name: canList
        type: boolean
        required: true
      - name: canView
        type: boolean
        required: true
      - name: canCreate
        type: boolean
        required: true
      - name: canEdit
        type: boolean
        required: true
      - name: canDelete
        type: boolean
        required: true
    entity_constraints:
      - "(roleId, tableId)の組は一意"
      - "この組み合わせのレコードが存在しない場合、5つの操作すべてが拒否される(デフォルト拒否。BR5.1、functional-design-questions.md Q2)"
    relationships: []

  - name: ColumnPermission
    description: ロールが持つカラム単位のアクセスレベル
    attributes:
      - name: columnPermissionId
        type: identifier
        required: true
        unique: true
      - name: roleId
        type: string
        required: true
        references:
          entity: Role
          owned_by: permission Unit(このUnit自身)
          relationship: "各ColumnPermissionは1つのRoleに対する設定を表す"
      - name: tableId
        type: string
        required: true
        references:
          entity: TableConfig
          owned_by: config-management Unit
          relationship: "各ColumnPermissionは1つの設定済みテーブルの1カラムに対する権限を表す(ID参照のみ、外部キー制約なし)。domain-design/components.mdの属性一覧にはtableIdが明記されていなかったが、関連の説明文自体はテーブルへの帰属を前提としており、columnNameだけでは複数テーブルにまたがる同名カラムを区別できないため、本ステージで属性として明記する"
      - name: columnName
        type: string
        required: true
      - name: accessLevel
        type: string
        required: true
        default: editable
        allowed_values: ["editable", "readonly", "hidden"]
        constraints: ["未設定(レコードなし)の場合の既定値はeditable(FR5.2の「更新可(デフォルト)」に基づく)。TablePermissionのデフォルト拒否とは異なる既定方針である点に注意(BR5.2参照)"]
    entity_constraints:
      - "(roleId, tableId, columnName)の組は一意"
    relationships: []
```

## エンティティサマリー

| Entity | 説明 | 主な属性 | 関連 |
|---|---|---|---|
| Role | ロール定義 | roleId, name, description | なし |
| Group | グループ定義 | groupId, name | なし |
| GroupMembership | Account-Group所属関係(本ステージで新設) | accountId(ID参照), groupId | Group |
| RoleAssignment | ロールのAccount/Groupへの割り当て | roleId, assigneeType, accountId(ID参照)またはgroupId(排他) | Group(assigneeType=groupのとき) |
| TablePermission | テーブル単位権限。未設定はデフォルト拒否 | roleId, tableId(ID参照), canList/View/Create/Edit/Delete | なし |
| ColumnPermission | カラム単位アクセスレベル。未設定はデフォルトeditable | roleId, tableId(ID参照), columnName, accessLevel(既定editable) | なし |

`accountId`・`tableId`から、それぞれauth Unit所有のAccount・config-management Unit所有のTableConfigへの参照は、いずれもID参照のみとし外部キー制約を設けない(audit-log Unitのentities.mdで採用した設計判断と同じ理由: 各Unitが独立に進化できるようにするため)。
