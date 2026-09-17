<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Entities — user-management (U4)

`inception/domain-design/components.md`のUserManagementコンポーネント定義(`User`/`UserPreference`の属性大枠)を、機能設計として具体化したものである。

```yaml
entities:
  - name: User
    description: >
      招待・登録・更新・無効化の対象となるユーザーアカウント。パスワードはハッシュ化して保持し、
      平文はいかなる経路(ログ・監査ログ・エラーメッセージ)にも出力しない(project.md Mandated)。
    attributes:
      - name: userId
        type: string
        required: true
        unique: true
        description: user-managementが発行する内部ID(主キール)
      - name: name
        type: string
        required: true
        constraints: [必須プロパティ(fail-fast検証対象)]
      - name: email
        type: string
        required: true
        unique: true
        constraints: [必須プロパティ(fail-fast検証対象), 一意制約]
      - name: passwordHash
        type: string
        required: false
        description: >
          Argon2id(functional-design-questions.md Q1)でハッシュ化した値。招待中(status=invited)は
          未設定(null)であり、招待受諾時に設定される
      - name: status
        type: string
        allowed_values: [invited, active, disabled]
        required: true
        defaults: "invited(招待作成時)"
      - name: roleIds
        type: array
        required: false
        description: >
          割り当てられたロールIDのリスト(0個以上、FR4.2の複数ロール付与)。PermissionEngineが
          所有するRoleエンティティへの不透明な文字列参照(Q6確定により実在検証を行う。他ユニットの
          scopeRefとは異なり、本エンティティに限り実在検証の対象とする)
        item_type: string
      - name: invitationToken
        type: string
        required: false
        unique: true
        description: >
          招待受諾用トークン(UUIDv4、Q2確定によりstatus=invitedの間のみ意味を持ち、有効期限は
          設けない)。status=activeへ遷移した後は用済みとなるが、値自体は保持してよい(参照されない)
    entity_constraints:
      - "emailの組で一意"
      - "invitationTokenは非nullの場合に一意"
      - "passwordHashはstatus=invitedの間はnullを許容し、status=active/disabledでは非null"
    relationships:
      - target: UserPreference
        cardinality: "1..1"
        direction: "User 1 -> 1 UserPreference"
        description: 各Userは1個のUserPreferenceを持つ(招待受諾時に作成、Q4確定)

  - name: UserPreference
    description: >
      ユーザー単位の表示設定(テーマ・フォントサイズ・表示言語、FR9.1・FR10.1のユーザー単位切替手段)。
      招待受諾時に、受諾リクエストで指定された値(省略時は既定値)で作成する(Q4確定)。
    attributes:
      - name: userId
        type: string
        required: true
        unique: true
        references: User
        description: 主キー(Userと1:1)
      - name: theme
        type: string
        allowed_values: [light, dark]
        required: true
        defaults: "light(招待受諾リクエストで省略された場合)"
      - name: fontSize
        type: string
        allowed_values: [large, medium, small]
        required: true
        defaults: "medium(招待受諾リクエストで省略された場合)"
      - name: locale
        type: string
        allowed_values: [ja, en]
        required: true
        defaults: "ja(招待受諾リクエストで省略された場合)"
    entity_constraints:
      - "userIdと1:1対応(Userのライフサイクルと同期。Userが無効化されてもUserPreferenceは保持する)"
    relationships:
      - target: User
        cardinality: "1..1"
        direction: "UserPreference 1 -> 1 User"
        description: 各UserPreferenceは1個のUserに属する

  - name: UserChangedEvent
    description: >
      User(登録・更新・無効化)の変更操作をAuditLogging(U7)へ通知するドメインイベント(BR4.9)。
      内部設定DBへ永続化しない値オブジェクトであり、fire-and-forgetで発行される。config-engineの
      ConfigChangedEventと同じ設計パターンを踏襲し、変更前後の値を含める(Q3確定、audit-loggingが
      前提としていた段階的充足を満たす)。
    attributes:
      - name: operation
        type: string
        allowed_values: [INVITED, UPDATED, DISABLED]
        required: true
      - name: targetId
        type: string
        required: true
        description: 変更対象のuserId
      - name: beforeValue
        type: object
        required: false
        description: >
          変更前のスナップショット({name, email, status, roleIds}、passwordHashは含めない)。
          新規作成(INVITED)時はnull
      - name: afterValue
        type: object
        required: true
        description: 変更後のスナップショット({name, email, status, roleIds}、passwordHashは含めない)
      - name: actor
        type: string
        required: true
        description: 操作を実行した管理者のuserId
      - name: occurredAt
        type: string
        required: true
        description: 発生日時(ISO 8601)
    entity_constraints: []
    relationships: []
```

## エンティティ概要

- **User**: 招待・登録・更新・無効化の対象。パスワードはArgon2idでハッシュ化して保持し、招待中は未設定。`roleIds`はPermissionEngineへの参照だが、本ユニットに限り実在検証を行う(Q6確定)。
- **UserPreference**: ユーザー単位の表示設定(テーマ・フォントサイズ・言語)。招待受諾時に、受諾リクエストで指定された値(省略時は既定値)で作成する(Q4確定)。
- **UserChangedEvent**: User変更操作をAuditLoggingへ通知するドメインイベント。変更前後の値(パスワードハッシュを除く)を含む(Q3確定)。
