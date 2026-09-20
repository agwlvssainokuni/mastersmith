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
        description: user-managementが発行する内部ID(主キー)
      - name: name
        type: string
        required: true
        constraints: [必須プロパティ(fail-fast検証対象)]
      - name: email
        type: string
        required: true
        unique: true
        constraints: [必須プロパティ(fail-fast検証対象), 一意制約, "trim・小文字へ正規化して保持・比較する(BR4.15)"]
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
          直接付与されたロールIDのリスト(0個以上、FR4.2の複数ロール付与)。PermissionEngineが
          所有するRoleエンティティへの不透明な文字列参照(Q6確定により招待・更新時に実在検証を行う。
          他ユニットのscopeRefとは異なり、本エンティティに限り実在検証の対象とする。初期管理者の
          自動作成時は例外、BR4.7)。Group経由の間接付与ロールは本属性には保持せず、C11の
          findByEmail応答時に合成する(BR4.13)
        item_type: string
      - name: invitationToken
        type: string
        required: false
        unique: true
        description: >
          招待受諾用トークン(UUIDv4、Q2確定によりstatus=invitedの間のみ有効で、有効期限は設けない)。
          招待受諾(BR4.2)または招待取消(BR4.11)の時点でnullにする(再利用不可)。再招待(BR4.11)では
          新しい値で置き換え、旧値は無効になる
    entity_constraints:
      - "正規化後のemailで一意"
      - "invitationTokenは非nullの場合に一意、かつstatus=invitedの間のみ非null"
      - "passwordHashはstatus=invitedの間はnull、status=active/disabledでは、招待受諾済みまたは初期管理者作成済みのため非null(招待を取り消されたdisabledはnullのまま)"
    relationships:
      - target: UserPreference
        cardinality: "0..1"
        direction: "User 1 -> 0..1 UserPreference"
        description: >
          各Userは0個または1個のUserPreferenceを持つ。招待受諾時(Q4確定)または初期管理者の自動作成時に
          作成されるため、status=invited(未受諾)のUserと、招待を取り消されたUserは持たない

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
      - "userIdでUserと対応する(招待受諾時または初期管理者作成時に作成され、Userが無効化されてもUserPreferenceは保持する)"
      - "レコードが存在しない場合、GET /api/me/preferencesは既定値を返す(BR4.8)"
    relationships:
      - target: User
        cardinality: "1..1"
        direction: "UserPreference 1 -> 1 User"
        description: 各UserPreferenceは必ず1個のUserに属する

  - name: UserChangedEvent
    description: >
      User(招待・招待受諾・更新・無効化・初期管理者作成)の変更操作をAuditLogging(U7)へ通知するドメインイベント(BR4.9)。
      内部設定DBへ永続化しない値オブジェクトであり、fire-and-forgetで発行される。config-engineの
      ConfigChangedEventと同じ設計パターンを踏襲し、変更前後の値を含める(Q3確定、audit-loggingが
      前提としていた段階的充足を満たす)。
    attributes:
      - name: operation
        type: string
        allowed_values: [INVITED, ACTIVATED, UPDATED, DISABLED, BOOTSTRAPPED]
        required: true
        description: >
          INVITED=招待・再招待、ACTIVATED=招待受諾によるパスワード設定・有効化、UPDATED=管理者による更新、
          DISABLED=無効化・招待取消、BOOTSTRAPPED=初期管理者の自動作成
      - name: targetType
        type: string
        allowed_values: [User]
        required: true
        description: 変更対象の種別(固定値User。AuditLogEntryのtargetType(C6)へ対応付ける)
      - name: targetId
        type: string
        required: true
        description: 変更対象のuserId
      - name: beforeValue
        type: object
        required: false
        description: >
          変更前のスナップショット({name, email, status, roleIds}、passwordHashとinvitationTokenは含めない)。
          新規作成(初回のINVITED、BOOTSTRAPPED)時はnull。再招待のINVITEDは非null
      - name: afterValue
        type: object
        required: true
        description: 変更後のスナップショット({name, email, status, roleIds}、passwordHashとinvitationTokenは含めない)
      - name: actor
        type: string
        required: true
        description: >
          操作者。INVITED・UPDATED・DISABLEDは操作した管理者のuserId、ACTIVATEDは受諾したUser自身のuserId、
          BOOTSTRAPPEDはシステム識別子(固定文字列system。userIdではない値をuserIdとして偽装しない。
          AuditLoggingのactorRawに相当する扱いを想定)
      - name: occurredAt
        type: string
        required: true
        description: 発生日時(ISO 8601)
    entity_constraints: []
    relationships: []
```

## エンティティ概要

- **User**: 招待・登録・更新・無効化の対象。パスワードはArgon2idでハッシュ化して保持し、招待中は未設定。`roleIds`(直接付与分)はPermissionEngineへの参照だが、本ユニットに限り招待・更新時に実在検証を行う(Q6確定)。
- **UserPreference**: ユーザー単位の表示設定(テーマ・フォントサイズ・言語)。招待受諾時(受諾リクエストで指定された値、省略時は既定値、Q4確定)または初期管理者作成時に作成する。招待中のUserは持たない(User 1 対 0..1)。
- **UserChangedEvent**: User変更操作をAuditLoggingへ通知するドメインイベント。変更前後の値(パスワードハッシュ・招待トークンを除く)、対象種別、操作者を含む(Q3確定)。
