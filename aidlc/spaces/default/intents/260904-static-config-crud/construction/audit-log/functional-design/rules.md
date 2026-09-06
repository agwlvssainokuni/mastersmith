# Business Rules: audit-log

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: config-management・dynamic-data-access・auth・account-managementが発行するAuditableActionOccurredEventは、受信したものをすべて記録する
    category: policy
    applies_to: AuditLogEntry
    trigger: "4つの発行元のいずれかがAuditableActionOccurredEventを発行したとき"
    logic: "IF イベントを受信 THEN occurredAt/actorAccountId/actionType/targetDescriptionをそのままAuditLogEntryとして永続化する"
    violation_behaviour: "該当なし(受信したイベントを拒否する条件は存在しない)"
    source: FR7.1, FR7.2

  - id: BR1.2
    statement: 監査ログの記録処理で発生した例外は、発行元(呼び出し元)の主処理へ伝播させない。ただし記録失敗はサイレントにせず、構造化ログ(NFR3準拠)へERRORレベルで記録する
    category: policy
    applies_to: AuditLogEntry
    trigger: "監査ログ記録処理(イベントリスナー)の実行中に例外が発生したとき"
    logic: "IF 記録処理中に例外が発生 THEN (1)例外を捕捉し呼び出し元へは再送出しない、(2)actionType・occurredAt・発生した例外の内容を含む構造化ログをERRORレベルで出力する。ELSE 通常どおり記録を完了する"
    violation_behaviour: "記録は失敗として扱われるが、発行元の主処理(設定変更・業務データ操作・アカウント操作そのもの)は正常に完了する。失敗の事実はログから追跡できる(construction phaseガードライン Error Handling準拠)"
    source: contract-summary.md #5〜#8(監査ログイベント契約)、aidlc/spaces/default/memory/phases/construction.md#Error Handling

  - id: BR2.1
    statement: 監査ログの参照は、操作種別(actionType)・利用者(actorAccountId)・期間(occurredAtの範囲)の3条件で絞り込みできる
    category: validation
    applies_to: AuditLogEntry
    trigger: "管理者が監査ログ画面で絞り込み条件(Filter)を指定したとき"
    logic: "IF 絞り込み条件が指定されている THEN 該当条件に一致するAuditLogEntryのみを返す。ELSE 全件(ページング付き)を返す"
    violation_behaviour: "該当なし"
    source: FR7.3, refined-mockups/mockups.md 12.

  - id: BR2.2
    statement: 監査ログの参照は、自由文字列検索(Search欄)によりtargetDescriptionの部分一致検索もできる。BR2.1の絞り込み条件とAND条件で組み合わせられる
    category: validation
    applies_to: AuditLogEntry
    trigger: "管理者が監査ログ画面のSearch欄に文字列を入力したとき"
    logic: "IF Search欄に文字列が指定されている THEN targetDescriptionに指定文字列を部分一致で含むAuditLogEntryのみを返す(BR2.1のフィルタ条件と併用時はAND条件)。ELSE 対象を絞り込まない"
    violation_behaviour: "該当なし"
    source: FR7.3, refined-mockups/mockups.md 12.(Search欄とFilter欄は別個の検索軸として明記)

  - id: BR3.1
    statement: 監査ログのエクスポートはCSV形式・JSON形式の両方をサポートする。形式は`format`クエリパラメータ(`csv`|`json`)で指定し、必須とする
    category: policy
    applies_to: AuditLogEntry
    trigger: "管理者が画面またはAPI経由で`format`パラメータを指定してエクスポートを要求したとき"
    logic: "IF format=csv THEN text/csvとしてファイルを生成する。ELSE IF format=json THEN application/jsonとしてファイルを生成する。ELSE 400エラーとする"
    violation_behaviour: "サポート外の形式が指定された場合は400エラー(RFC 7807)を返す"
    source: FR7.4, functional-design-questions.md Q2, contract-summary.md #18(GET /api/admin/audit-log/export)

  - id: BR4.1
    statement: 保持期間超過分の監査ログ削除は、削除操作の`olderThanDays`パラメータ(必須)で指定された日数を経過したAuditLogEntryを対象とする。フロントエンドは`olderThanDays`の初期値としてAuditLogSettings.retentionDaysを画面に表示し、管理者はその場で値を上書きして一時的な基準日数で削除することもできる(contract-summary.md #18 DELETE `/api/admin/audit-log`)
    category: constraint
    applies_to: AuditLogEntry
    trigger: "管理者が画面またはAPI経由で保持期間超過分の削除を要求したとき"
    logic: "IF occurredAt < (現在日時 - リクエストのolderThanDays日) THEN 削除対象に含める。ELSE 削除対象から除外する。olderThanDaysの値そのものはAuditLogSettings.retentionDaysを変更しない(一時的な上書きであり、次回以降のデフォルト値には影響しない)"
    violation_behaviour: "該当なし(自動実行ではなく管理者操作による明示的な削除)"
    source: FR7.5, functional-design-questions.md Q1, contract-summary.md #18

  - id: BR4.3
    statement: 削除操作の`olderThanDays`は1以上の整数でなければならない(AuditLogSettings.retentionDaysの最小値制約BR4.2と同じ下限を適用する)
    category: validation
    applies_to: AuditLogEntry
    trigger: "管理者が画面またはAPI経由で保持期間超過分の削除を要求したとき"
    logic: "IF 指定されたolderThanDaysが1以上の整数 THEN 削除処理(BR4.1)を実行する。ELSE 400エラー(RFC 7807)を返し、削除は実行しない"
    violation_behaviour: "0以下やマイナス値を指定すると、意図せず監査ログの大部分・全件が削除されうるため(削除基準日時が現在時刻以降になる)、この検証で事前に拒否する"
    source: functional-design-questions.md Q1のレビューで発見された安全性ギャップ(R-07)、contract-summary.md #18

  - id: BR4.2
    statement: AuditLogSettings.retentionDaysの既定値は365、最小値は1とする
    category: validation
    applies_to: AuditLogSettings
    trigger: "管理者がretentionDaysを更新しようとしたとき"
    logic: "IF 指定値が1以上の整数 THEN 更新を受け付ける。ELSE 400エラー(RFC 7807)を返す"
    violation_behaviour: "更新は拒否され、既存の設定値が維持される"
    source: functional-design-questions.md Q1

  - id: BR5.1
    statement: audit-logの全ての操作(参照・エクスポート・削除・設定変更)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる
    category: authorization
    applies_to: AuditLogEntry, AuditLogSettings
    trigger: "audit-logへのいずれかの操作要求を受けたとき"
    logic: "IF アクセストークンのisAdminクレームがtrue THEN 操作を許可する。ELSE 403エラー(RFC 7807)を返す"
    violation_behaviour: "操作は拒否され、403エラーが返される"
    source: FR5.5, FR5.6

  - id: BR6.1
    statement: AuditLogEntryは作成後に内容を変更できない(更新操作を持たない)
    category: constraint
    applies_to: AuditLogEntry
    trigger: "AuditLogEntryに対する更新要求を受けたとき(そもそも更新操作自体が公開されない)"
    logic: "更新操作は提供しない。削除はBR4.1の保持期間超過による一括削除のみ"
    violation_behaviour: "該当なし(更新APIが存在しないため発生し得ない)"
    source: FR7.1, FR7.2(「いつ・誰が・何をしたか」の記録という性質上、事後の改変を許容しない設計判断)
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | policy | 4つの発行元からのイベントはすべて記録する |
| BR1.2 | policy | 記録失敗は発行元の主処理を止めないが、ログには記録する |
| BR2.1 | validation | actionType・利用者・期間で絞り込み可能 |
| BR2.2 | validation | targetDescriptionの自由文字列検索(BR2.1とAND条件) |
| BR3.1 | policy | `format`パラメータでCSV・JSON両形式をエクスポート可能 |
| BR4.1 | constraint | `olderThanDays`(初期値はretentionDays)を超過した記録が削除対象 |
| BR4.2 | validation | retentionDaysの既定値365、最小値1 |
| BR4.3 | validation | olderThanDaysも1以上の整数を要求(誤って全件削除を防止) |
| BR5.1 | authorization | 全操作がisAdminクレーム必須 |
| BR6.1 | constraint | 記録は作成後不変、更新操作なし |
