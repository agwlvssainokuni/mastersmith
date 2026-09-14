# Entities — permission-engine (U3)

```yaml
entities:
  - name: Role
    description: >
      権限判定の単位となるロール。ユーザーへ直接付与するほか、Groupを介して間接的に付与することもできる。
      Domain Design(components.md)では`parentRoleId`属性(親ロール参照)を持つ木構造として定義されていたが、
      本機能設計での確認の結果、ロール間の階層継承は本MVPでは実装しないことが確定した(Q2 Follow-up)。
      これはDomain Designからの意図的な逸脱であり、`components.md`のRoleエンティティ定義への追補が必要
      (下記「Domain Designからの逸脱」参照)。
    attributes:
      - name: roleId
        type: string
        required: true
        unique: true
      - name: name
        type: string
        required: true
        unique: true
        description: ロール名(表示名)
    constraints:
      - Roleは親ロールを持たない(階層構造なし)。実効権限の解決はスコープ階層(スキーマ→テーブル→カラム)のみで行う(BR3.4参照)
    relationships:
      - target: PrimaryPermission
        cardinality: 1-to-N
        direction: Role has many PrimaryPermission
      - target: AuxiliaryPermission
        cardinality: 1-to-N
        direction: Role has many AuxiliaryPermission
      - target: GroupRole
        cardinality: 1-to-N
        direction: Role has many GroupRole(Groupへの間接付与を表す中間エンティティ)

  - name: PrimaryPermission
    description: >
      ロールに対する主権限(FULL/READ/NONE)の明示的な割当。スキーマ・テーブル・カラムの各階層(scopeType)に
      対して個別に割り当てる。「指定なし」は、当該(roleId, scopeType, scopeRef)の組に対応する行が
      存在しないことで表現する(明示的な第4の値は持たない)。
    attributes:
      - name: primaryPermissionId
        type: string
        required: true
        unique: true
      - name: roleId
        type: string
        required: true
        references: Role
      - name: scopeType
        type: enum
        required: true
        allowed_values: [SCHEMA, TABLE, COLUMN]
      - name: scopeRef
        type: string
        required: true
        description: >
          scopeTypeに応じた対象識別子。SCHEMA: スキーマ名、またはBR3.15が定める予約スキーマ名
          (管理系画面用)。TABLE: config-engineの`tableConfigId`。COLUMN: config-engineの
          `columnConfigId`。permission-engineはscopeRefの値をconfig-engine側の実在チェックに
          かけず、不透明な識別子として扱う(config-engineが認識しない予約スキーマ名も
          そのまま許容する。BR3.14参照)。
      - name: level
        type: enum
        required: true
        allowed_values: [FULL, READ, NONE]
        description: >
          比較可能な順序を持つ(FULL > READ > NONE)。BR3.8の権限昇格判定はこの順序に基づく
          (BR3.4参照)。
    constraints:
      - (roleId, scopeType, scopeRef)の組は一意(同一ロール・同一対象への重複割当を禁止)

  - name: AuxiliaryPermission
    description: >
      ロールに対する補助権限(CREATE/DELETE)の明示的な割当。スキーマ・テーブル単位のみ(カラム単位は対象外)。
      createAllowed/deleteAllowedは、それぞれ独立に「許可/禁止/指定なし」の3値を取る
      (フィールドがnull = 指定なし)。
    attributes:
      - name: auxiliaryPermissionId
        type: string
        required: true
        unique: true
      - name: roleId
        type: string
        required: true
        references: Role
      - name: scopeType
        type: enum
        required: true
        allowed_values: [SCHEMA, TABLE]
      - name: scopeRef
        type: string
        required: true
        description: scopeTypeに応じた対象識別子(SCHEMA: スキーマ名、TABLE: `tableConfigId`)
      - name: createAllowed
        type: boolean
        required: false
        default: null
        description: null = 指定なし。true = 許可。false = 禁止
      - name: deleteAllowed
        type: boolean
        required: false
        default: null
        description: null = 指定なし。true = 許可。false = 禁止
    constraints:
      - (roleId, scopeType, scopeRef)の組は一意

  - name: Group
    description: >
      複数のUserへロールをまとめて付与するための単位(FR4.1「グループ」)。Domain Design(components.md)には
      存在しないエンティティであり、本機能設計での新設(Q1)。GroupMembership(Group⇔User)・GroupRole
      (Group⇔Role)を介して、Userは直接付与のRoleに加え、所属Group経由でもRoleを得る。
    attributes:
      - name: groupId
        type: string
        required: true
        unique: true
      - name: name
        type: string
        required: true
        unique: true
    relationships:
      - target: GroupMembership
        cardinality: 1-to-N
        direction: Group has many GroupMembership
      - target: GroupRole
        cardinality: 1-to-N
        direction: Group has many GroupRole

  - name: GroupMembership
    description: >
      GroupとUser(user-managementが所有するエンティティ、本ユニットからはuserIdの外部参照として扱う)
      の多対多関連を表す中間エンティティ。
    attributes:
      - name: groupId
        type: string
        required: true
        references: Group
      - name: userId
        type: string
        required: true
        description: user-managementが所有するUserのuserId(本ユニットは外部参照として保持するのみ)
    constraints:
      - (groupId, userId)の組は一意

  - name: GroupRole
    description: GroupとRoleの多対多関連を表す中間エンティティ。
    attributes:
      - name: groupId
        type: string
        required: true
        references: Group
      - name: roleId
        type: string
        required: true
        references: Role
    constraints:
      - (groupId, roleId)の組は一意
```

## human-readable summary

permission-engineは、権限判定の基盤となる6エンティティ(Role・PrimaryPermission・AuxiliaryPermission・Group・GroupMembership・GroupRole)を保持する。

- **Role**: 権限判定の単位。Domain Design時点では親ロールを持つ木構造として想定されていたが、本機能設計のインタビューで**ロール間の階層継承は本MVPでは実装しない**ことが確定した。実効権限の階層継承は、スコープ(スキーマ→テーブル→カラム)の1軸のみで行う。
- **PrimaryPermission / AuxiliaryPermission**: それぞれロール単位の主権限・補助権限の明示的割当。「指定なし」は行の不存在(主権限)またはフィールドのnull(補助権限)で表現する。
- **Group / GroupMembership / GroupRole**: FR4.1の「グループ」概念を実現するために本機能設計で新設。UserはGroup所属を通じて間接的にRoleを得られる(直接付与のRoleと合わせて選択可能ロール一覧を構成する、BR3.3参照)。

## Domain Designからの逸脱(要追補)

1. **Roleの`parentRoleId`属性の削除**: `inception/domain-design/components.md`のRoleエンティティは`parentRoleId`(親ロール参照)を属性として明示していたが、本機能設計のインタビューでロール階層継承は本MVPでは実装しないことが確定した(ユーザー確認: 「利用者は自分に割り当てられたロールから一つを選択し、そのロールの権限のもとで操作する」という既存のFR4.2設計と整合させるため、ロール自体の親子階層は不要と判断)。`team.md`のテスト方針が言及する「ロール階層継承」は、本確認の結果、スコープ階層(スキーマ→テーブル→カラム)の継承を指すものと解釈し直す。Domain Design(`components.md`)・`team.md`への追補(注記修正)が望ましいが、Construction段階の後追い変更であり必須のブロッカーではないため、Open Questionとして記録する(`functional-spec.md`参照)。
2. **Groupエンティティの新設**: `components.md`のPermissionEngineエンティティ一覧にはGroupが存在しなかったが、FR4.1が要求する「グループへの割当」を満たすため本機能設計で新設した。これに伴い、Contract Design(C10)への追補(後述)が必要になる。
