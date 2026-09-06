# Entities: audit-log

domain-design/components.mdのAuditLogComponentが所有するAuditLogEntryエンティティに加え、functional-design-questions.md Q1の回答(保持日数は固定値ではなく設定値)により、AuditLogSettingsエンティティを新設する。

## エンティティモデル(機械可読)

```yaml
entities:
  - name: AuditLogEntry
    description: 設定変更・業務データ操作・アカウント操作の1回の操作記録。作成後は変更されない(保持期間超過による削除のみ許容)
    attributes:
      - name: entryId
        type: identifier
        required: true
        unique: true
      - name: occurredAt
        type: datetime
        required: true
        constraints: ["発行元(config-management/dynamic-data-access/auth/account-management)から受け取ったAuditableActionOccurredEventのoccurredAtをそのまま記録する"]
      - name: actorAccountId
        type: string
        required: false
        allowed_values: null
        constraints: ["システム起因の操作(例: 保持期間超過分の自動的な把握処理)はnull"]
        references:
          entity: Account
          owned_by: auth Unit(共有スキーマ経由でaccount-managementも操作するAccount。契約#4/#19参照)
          relationship: "各AuditLogEntryは、操作を行ったAccountを高々1件参照する(ID参照のみ。外部キー制約は設けない — 発行元Unitと本Unitが独立に進化できるようにするため)"
      - name: actionType
        type: string
        required: true
        allowed_values: "発行元ごとに異なる語彙(contract-summary.md #5〜#8参照)。config-management: CONFIG_TABLE_CREATED | CONFIG_TABLE_UPDATED | CONFIG_TABLE_DELETED | CONFIG_IMPORTED。dynamic-data-access: DATA_RECORD_CREATED | DATA_RECORD_UPDATED | DATA_RECORD_DELETED。auth: LOGIN_SUCCESS | LOGIN_FAILED | LOGIN_LOCKED | SELF_SERVICE_PROFILE_CHANGED。account-management: ACCOUNT_CREATED | ACCOUNT_UPDATED | ACCOUNT_DISABLED"
      - name: targetDescription
        type: string
        required: true
        constraints: ["人間可読な対象の説明。発行元がAuditableActionOccurredEventのtargetDescriptionフィールドとして生成した文字列をそのまま記録する(例: \"table_config: products\", \"account: 42\")"]
    entity_constraints:
      - "作成後は不変(更新操作を持たない)。削除はAuditLogSettings.retentionDaysに基づく保持期間超過分の一括削除のみ"
    relationships:
      - target: Account(auth Unit所有、共有スキーマ)
        cardinality: "N:0..1"
        direction: "AuditLogEntry → Account(ID参照のみ)"

  - name: AuditLogSettings
    description: 監査ログの保持日数を保持する設定エンティティ(functional-design-questions.md Q1)。テーブル単位ではなく監査ログ機能全体で1件のみ存在するシングルトン
    attributes:
      - name: settingsId
        type: identifier
        required: true
        unique: true
        constraints: ["固定値のみを取る(例: 常に文字列定数\"default\"、またはintegerの1)。シングルトンの一意性はこの固定識別子への一意制約(unique constraint)で保証し、アプリケーション層は常にこの1件のみをupsertする(新規行の作成操作は公開しない)"]
      - name: retentionDays
        type: integer
        required: true
        default: 365
        min: 1
        constraints: ["管理者が変更可能。監査ログ画面またはAPIから更新する"]
    entity_constraints:
      - "常に1レコードのみ存在する(シングルトン)。settingsIdへの一意制約により2件目の作成を防ぐ。初期値はretentionDays=365で最初から存在するものとして扱う(マイグレーション時に1行だけ投入する)"
    relationships: []
```

## エンティティサマリー

| Entity | 説明 | 主な属性 | 関連 |
|---|---|---|---|
| AuditLogEntry | 1回の操作記録。作成後不変 | entryId, occurredAt, actorAccountId, actionType, targetDescription | Account(ID参照のみ、外部キー制約なし) |
| AuditLogSettings | 保持日数の設定(シングルトン) | settingsId(固定値)、retentionDays(既定365) | なし |

`actorAccountId`から`Account`への参照は、authが所有する共有スキーマ(contract-summary.md #19)への直接の外部キー制約としては設けない。audit-logとauth/account-managementは独立したUnitであり、Account側のスキーマ変更がaudit-logの記録受付を壊さないようにするため、ID文字列としてのみ保持する(疎結合な参照)。
