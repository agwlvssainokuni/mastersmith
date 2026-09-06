# Functional Specification: audit-log

## ワークフロー

### 1. 記録受付(イベント駆動)

1. config-management・dynamic-data-access・auth・account-managementのいずれかが操作を完了し、AuditableActionOccurredEventを発行する(contract-summary.md #5〜#8)。
2. audit-logのイベントリスナーがイベントを受信する。
3. occurredAt・actorAccountId・actionType・targetDescriptionを取り出す。
4. AuditLogEntryとして永続化する(BR1.1)。
5. 手順3〜4のいずれかで例外が発生した場合、例外を捕捉し、発行元へは伝播させない。同時に、actionType・occurredAt・例外内容を含む構造化ログをERRORレベルで出力する(BR1.2)。発行元の主処理(設定変更・業務データ操作・アカウント操作そのもの)は記録の成否に関わらず完了する。

### 2. 参照・絞り込み

1. 管理者(isAdminクレーム保持者、BR5.1)が監査ログ画面(または`GET /api/admin/audit-log`)を開く。
2. 操作種別(actionType)・利用者(actorAccountId)・期間(occurredAtの範囲)のFilter条件のいずれかまたは組み合わせを指定する(BR2.1)。加えて、Search欄に自由文字列を入力するとtargetDescriptionの部分一致検索がAND条件で適用される(BR2.2)。
3. 条件に一致するAuditLogEntryを、ページング・ソート付きで返す(共通規約: page/size/sort、contract-summary.md参照)。

### 3. エクスポート

1. 管理者が監査ログ画面(または`GET /api/admin/audit-log/export`)でエクスポートを要求し、`format`パラメータでCSVまたはJSON形式を選択する(必須パラメータ、BR3.1)。
2. 現在の絞り込み条件(BR2.1・BR2.2と同じ)に一致するAuditLogEntry全件を、指定された形式のファイルとして生成する。
3. `format`がcsv・json以外の場合は400エラー(RFC 7807)を返す。

### 4. 保持期間超過分の削除

1. 管理者が監査ログ画面を開くと、画面は`GET /api/admin/audit-log/settings`で現在のretentionDays(既定365)を取得し、削除確認ダイアログの日数入力欄の初期値として表示する(refined-mockups/mockups.md 12.の確認ダイアログを経由)。
2. 管理者は表示された日数をそのまま使うか、その場で別の日数に上書きしてから削除を実行できる(BR4.1)。
3. `DELETE /api/admin/audit-log?olderThanDays=<確定した日数>`を呼び出す。`olderThanDays`が1以上の整数であることを検証する(BR4.3)。検証に失敗した場合は400エラー(RFC 7807)を返し、削除は実行しない。
4. `現在日時 - olderThanDays日`を削除基準日時として算出する。
5. `occurredAt`が削除基準日時より前のAuditLogEntryをすべて削除する。この操作はAuditLogSettings.retentionDays自体を変更しない(一時的な上書きは削除操作限りであり、次回以降の初期値には影響しない)。
6. 削除件数を管理者に返す。

### 5. 保持日数の設定変更

1. 管理者が監査ログ画面(または`PUT /api/admin/audit-log/settings`)からretentionDaysの変更を要求する。
2. 指定値が1以上の整数であることを検証する(BR4.2)。検証に失敗した場合は400エラー(RFC 7807)を返す。
3. 検証に成功した場合、AuditLogSettingsの唯一の行(settingsId固定値)を更新する。以降、ワークフロー4の削除確認ダイアログはこの新しい値を初期値として表示する。

## 状態遷移

AuditLogEntryはBR6.1により作成後不変であり、意味のある状態遷移(ライフサイクル)を持たない。存在(作成)から削除(保持期間超過による一括削除)への単純な遷移のみである。

```
[存在しない] --記録受付(ワークフロー1)--> [記録済み(不変)] --保持期間超過分の削除(ワークフロー4)--> [存在しない]
```

## エンティティ関連図(ER図、entities.mdから導出)

```mermaid
erDiagram
    AuditLogEntry {
        string entryId PK
        datetime occurredAt
        string actorAccountId "nullable, Account(auth Unit)へのID参照のみ"
        string actionType
        string targetDescription
    }
    AuditLogSettings {
        string settingsId PK "固定値、シングルトン一意制約"
        integer retentionDays "既定365、最小1"
    }
```

<!-- Text fallback: AuditLogEntryは5つの属性(entryId主キー、occurredAt、actorAccountId(nullable、外部キー制約なしのID参照)、actionType、targetDescription)を持つ。AuditLogSettingsはsettingsId(固定値の主キー、シングルトンを一意制約で保証)とretentionDays(既定365、最小1)を持ち、AuditLogEntryとの直接の関連はない。 -->

## ルールサマリー(rules.mdから導出)

| ワークフロー | 適用ルール |
|---|---|
| 1. 記録受付 | BR1.1, BR1.2 |
| 2. 参照・絞り込み | BR2.1, BR2.2, BR5.1 |
| 3. エクスポート | BR3.1, BR5.1 |
| 4. 保持期間超過分の削除 | BR4.1, BR4.3, BR5.1 |
| 5. 保持日数の設定変更 | BR4.2, BR5.1 |

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-06T13:40:56Z
**Iteration:** 2
**Request Challenge:** review:3ce03f5560a95052c64e889db2972f46

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-09 | Major | contract-summary.md > 監査ログAPI(#18) GET /api/admin/audit-log および /export の query parameters vs. entities.md / rules.md / traceability.json | contract-summary.md #18は`actionType`/`actorAccountId`/`occurredAtFrom`/`occurredAtTo`/`q`の各クエリパラメータをGET /api/admin/audit-logおよびGET /api/admin/audit-log/exportに宣言済み(511行目のR-09フォローアップ追記、527-530行目・546-549行目のパラメータ定義で確認)だが、本Unit自身のentities.md/rules.md/traceability.jsonはこの変更前の状態のままで、BR2.1/BR2.2の記述はクエリパラメータ名との対応を明示していない。以前の修正試行はツール都合でrevertされたことが判明済み。 | entities.mdまたはrules.mdのBR2.1/BR2.2に、契約#18で確定した5つのクエリパラメータ名(actionType/actorAccountId/occurredAtFrom/occurredAtTo/q)との対応を明記する。 | Unresolved |
| R-10 | Minor | entities.md > AuditLogEntry.actionType の allowed_values(32行目) | config-managementのactionType許容値が`CONFIG_TABLE_CREATED\|CONFIG_TABLE_UPDATED\|CONFIG_TABLE_DELETED\|CONFIG_IMPORTED`のみ列挙されており、contract-summary.md #5〜#8に既にある`CONFIG_CONNECTION_*`/`CONFIG_MENU_*`系の値が未反映。 | entities.mdのallowed_valuesにCONFIG_CONNECTION_*/CONFIG_MENU_*を追記する。 | Unresolved |
| R-11 | Minor | traceability.json > coverage[FR7.5].target(10行目) | FR7.5のtargetが`"BR4.1, BR4.2"`のままで、削除操作のolderThanDays検証を担うBR4.3が含まれていない。今回実行したtraceabilityセンサーも同じ欠落を`orphans: ["BR4.3"]`として検出しており、独立に裏付けられる。 | traceability.jsonのFR7.5.targetを`"BR4.1, BR4.2, BR4.3"`に更新する。 | Unresolved |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| required-sections (functional-spec.md) | passed | 必須セクションの欠落なし |
| traceability (traceability.json) | failed — `orphans: ["BR4.3"]`, `missing_from_upstream_ids`に大量のFR1〜FR6系ID | `orphans`のBR4.3欠落はR-11と同一事象で独立に裏付けられる新規の欠陥ではない。`missing_from_upstream_ids`の大量リストはaudit-log Unitのスコープ外FR(他Unit担当分)であり、既知の誤検知パターンとして扱う(新規欠陥ではない) |
| upstream-coverage (traceability.json) | failed — `unreferenced: ["unit-of-work", "unit-of-work-story-map", "requirements"]` | この3契約はunits-generation/requirements-analysis由来の上位契約全体であり、functional-designの成果物(entities.md/rules.md/traceability.json)が個別に逐語引用する性質のものではない。既存の(前回iterationから変化のない)状態であり新規の欠陥ではない |

### Summary

同一内容の再認証。entities.md/rules.md/functional-spec.md/traceability.jsonはiteration 1で認証済みの内容から変更されておらず(functional-spec.mdの`## Review`セクションが再認証前の状態に復元されていることも確認済み)、内部的にも健全(BR一覧・FR対応・状態遷移・ER図の相互整合性を再確認)。既知のR-09(Major)・R-10/R-11(Minor)はend-of-stageゲートでの人間判断に委ねる形でUnresolvedのまま持ち越す。Critical 0件、Major 1件、Minor 2件のためREADYとする。
