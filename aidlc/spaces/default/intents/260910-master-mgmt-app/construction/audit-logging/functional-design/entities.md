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

# Entities — audit-logging (U7)

```yaml
entities:
  - name: AuditLogEntry
    description: >
      他ユニットが発行するドメインイベント(ConfigChangedEvent・PermissionChangedEvent・
      ImportExecutedEvent)を購読して生成する、追記専用(append-only)の監査記録1件。
      `inception/domain-design/components.md`のAuditLoggingコンポーネント定義に1:1対応する。
      実装済み3ユニット(config-engine・permission-engine・data-import-export)が実際に発行する
      イベントクラスの形状がDomain Design時点の想定と異なっていたため（`actor`の意味論が
      イベントごとに不揃い、`target`が単一自由記述文字列等）、本機能設計でactorRawフィールドを
      新設した(functional-design-questions.md Q4、Domain Designからの逸脱、下記参照)。
    attributes:
      - name: auditLogEntryId
        type: string
        required: true
        unique: true
      - name: actorUserId
        type: string
        required: false
        default: null
        description: >
          操作を行った利用者のユーザーID。ImportExecutedEvent由来のエントリのみ、実際に
          ユーザーIDを意味する値が入る(BR7.4)。ConfigChangedEvent・PermissionChangedEvent
          由来のエントリはnullとし、元の値(システム識別子・activeRoleId)はactorRawへ保持する
          (Q4=B、BR7.2・BR7.3)。
      - name: actorRaw
        type: string
        required: false
        default: null
        description: >
          ConfigChangedEvent.actor(システム操作は"system"固定、利用者操作も現状"system"が
          使われユーザーID未伝播)・PermissionChangedEvent.actor(実際にはactiveRoleId)など、
          actorUserIdの意味(ユーザーID)に一致しない元の値をそのまま保持する。ImportExecutedEvent
          由来のエントリはactorUserIdが真のユーザーIDであるため、actorRawはnullとする(Q4=B)。
          Domain Design(components.md)のAuditLogEntryエンティティ定義には存在しない属性であり、
          本機能設計での新設(下記「Domain Designからの逸脱」参照)。
      - name: targetType
        type: string
        required: true
        allowed_values: [ConfigEngine, PermissionEngine, DataImportExport]
        description: >
          イベント発行元ユニット名の固定文字列(Q2=A)。将来user-management・
          config-import-export・record-edit-engineの購読が追加される際は、それぞれ
          "UserManagement"・"ConfigImportExport"・"RecordEditEngine"が追加される想定
          (Q1=A拡張パターン)。
      - name: targetId
        type: string
        required: true
        description: >
          Q2=Aの固定マッピング規則により、ConfigChangedEventなら`target`文字列(例:
          "schema.table"、自由記述で型とIDが分離されていない)、PermissionChangedEventなら
          `targetRoleId`、ImportExecutedEventなら`tableConfigId`をそのまま格納する。
      - name: operationType
        type: string
        required: true
        description: >
          Q3=Aによりイベントの実態を反映する値を格納する。ConfigChangedEventは`operation`
          列挙値の文字列表現(DRAFT_IMPORTED/CONFIG_SET_IMPORTED/TRANSLATION_UPSERTED)、
          PermissionChangedEventは固定文字列"PERMISSION_CHANGED"、ImportExecutedEventは
          `committed`の値に応じて"IMPORT_COMMITTED"/"IMPORT_ROLLED_BACK"のいずれか。
      - name: occurredAt
        type: datetime
        required: true
        description: イベント発生元(発行元ユニット)が記録した発生日時。監査ログ閲覧APIの並び順
          (BR7.9)・記録順序の正(BR7.8)として用いる唯一の基準。
      - name: beforeValue
        type: object
        required: false
        default: null
        description: >
          変更前スナップショット。ConfigChangedEvent・PermissionChangedEvent・
          ImportExecutedEventのいずれも構造化された変更前後の値を運ばないため、この3イベント
          由来のエントリは常にnullとする(Q5=A)。将来record-edit-engine・user-management実装時に
          非null値が記録されるようになる段階的充足として明記する(rules.md BR7.7参照)。
      - name: afterValue
        type: object
        required: false
        default: null
        description: 変更後スナップショット。beforeValueと同様の理由で常にnull(Q5=A)。
    constraints:
      - 本エンティティに対するUPDATE/DELETE操作は、アプリケーション層(リポジトリ/DAOインタフェース)に
        一切定義しない(project.md Mandated、BR7.5参照)。追記(INSERT)のみを許可する。
      - occurredAtに基づく厳密な到着順・グローバルな順序保証は行わない(Q9=A、BR7.8参照)。
    relationships:
      - target: User
        cardinality: 0-or-1-to-1
        direction: AuditLogEntry may reference one User(owned by UserManagement)via actorUserId
        description: >
          actorUserIdが非nullの場合のみ、その値はUserManagementが所有するUserのuserIdを指す
          (現時点ではImportExecutedEvent由来のエントリのみ)。永続化上の外部キー制約は設けず、
          不透明な文字列参照として扱う(User自体は本ユニットの管轄外)。
```

## human-readable summary

audit-loggingは単一のエンティティ`AuditLogEntry`のみを保持する。Domain Design(`components.md`)が
定義した7属性(`auditLogEntryId`・`actorUserId`・`targetType`・`targetId`・`operationType`・
`occurredAt`・`beforeValue`・`afterValue`)に加え、本機能設計で`actorRaw`を新設した。

これは、実装済み3ユニット(config-engine・permission-engine・data-import-export)が実際に発行する
ドメインイベントの`actor`フィールドの意味論が、イベントごとに「システム識別子」「ロールID」
「ユーザーID」と不揃いであることが判明したためであり(functional-design-questions.md Q4)、
「`actorUserId`という名前でユーザーIDでない値を偽って格納しない」という設計判断に基づく。

## Domain Designからの逸脱(要追補)

1. **`actorRaw`属性の新設**: `inception/domain-design/components.md`のAuditLogEntryエンティティ定義には
   存在しない属性を本機能設計で追加した。`ConfigChangedEvent.actor`・`PermissionChangedEvent.actor`が
   実際にはユーザーIDを意味しないため(前者はシステム識別子固定・利用者操作分も未伝播、後者は
   activeRoleId)、`actorUserId`をnullにしたうえで元の値を保持する退避先として新設した
   (functional-design-questions.md Q4=B)。Contract Design(C6)のAuditLogEntryスキーマへの追補
   (`actorRaw`プロパティの追加)が望ましいが、閲覧APIのレスポンス形状のみに関わる変更であり
   必須のブロッカーではないため、Open Questionとして記録する(`functional-spec.md`参照)。
