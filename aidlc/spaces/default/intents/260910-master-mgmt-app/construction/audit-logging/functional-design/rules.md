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

# Business Rules — audit-logging (U7)

`functional-design-questions.md`の確定回答に基づく、audit-loggingユニットの業務ルール。ルールIDは`BR7.y`(グループ7 = audit-logging、Unit ID番号に対応)とする。

```yaml
rules:
  - id: BR7.1
    statement: >
      本Boltでは、現時点で実際に存在する3種類のドメインイベント(ConfigChangedEvent・
      PermissionChangedEvent・ImportExecutedEvent)のみをSpringの`@EventListener`経由で
      購読する。user-management・config-import-export・record-edit-engineが発行予定の
      イベント(UserInvited等)はまだ存在しないため対象外とし、将来それぞれのBoltで新しい
      イベントクラスの発行コードと、本ユニット側への対応する購読メソッドを追加する。
    category: policy
    applies_to: [AuditLogEntry]
    trigger: "config-engine・permission-engine・data-import-exportからのConfigChangedEvent・PermissionChangedEvent・ImportExecutedEventの発行時"
    logic: "IF イベントがConfigChangedEvent|PermissionChangedEvent|ImportExecutedEventのいずれか THEN 対応する専用の購読メソッド(BR7.2〜BR7.4)でAuditLogEntryへ変換し記録する ELSE (該当イベントクラスは現時点で存在しないため分岐なし)"
    violation_behaviour: N/A(未実装イベント種別は購読対象外という設計上の境界)
    source: "Q1確定(A)"

  - id: BR7.2
    statement: >
      ConfigChangedEvent(operation, target, actor, occurredAt)を購読した場合、
      targetType="ConfigEngine"固定、targetId=target(自由記述文字列をそのまま)、
      operationType=operationの列挙値の文字列表現(DRAFT_IMPORTED/CONFIG_SET_IMPORTED/
      TRANSLATION_UPSERTED)、actorUserId=null、actorRaw=actor(現状は"system"固定)、
      beforeValue=null、afterValue=nullとしてAuditLogEntryへマッピングし記録する。
    category: calculation
    applies_to: [AuditLogEntry]
    trigger: "ConfigChangedEventの受信時"
    logic: "AuditLogEntry { targetType: \"ConfigEngine\", targetId: event.target, operationType: event.operation.name(), actorUserId: null, actorRaw: event.actor, occurredAt: event.occurredAt, beforeValue: null, afterValue: null }を生成し記録する"
    violation_behaviour: N/A
    source: "Q2確定(A)・Q3確定(A)・Q4確定(B)・Q5確定(A)。実クラス: backend/src/main/java/com/mastersmith/config/event/ConfigChangedEvent.java"

  - id: BR7.3
    statement: >
      PermissionChangedEvent(targetRoleId, scopeType, scopeRef, actor, occurredAt)を
      購読した場合、targetType="PermissionEngine"固定、targetId=targetRoleId、
      operationType="PERMISSION_CHANGED"固定、actorUserId=null、actorRaw=actor(実際には
      operationを実行した利用者のactiveRoleId)、beforeValue=null、afterValue=nullとして
      AuditLogEntryへマッピングし記録する。scopeType・scopeRefはAuditLogEntryへは
      マッピングしない(targetIdはtargetRoleIdのみを保持する、Q2=Aの固定マッピング規則)。
    category: calculation
    applies_to: [AuditLogEntry]
    trigger: "PermissionChangedEventの受信時"
    logic: "AuditLogEntry { targetType: \"PermissionEngine\", targetId: event.targetRoleId, operationType: \"PERMISSION_CHANGED\", actorUserId: null, actorRaw: event.actor, occurredAt: event.occurredAt, beforeValue: null, afterValue: null }を生成し記録する"
    violation_behaviour: N/A
    source: "Q2確定(A)・Q3確定(A)・Q4確定(B)・Q5確定(A)。実クラス: backend/src/main/java/com/mastersmith/permission/event/PermissionChangedEvent.java(1回のassignPermission/assignAuxiliaryPermission呼び出しごとに1件発行される粒度。permission-engine rules.md BR3.11のインポート単位サマリという当初設計から、実装時にコード側の判断で呼び出し単位の粒度へ変更されている点に注意)"

  - id: BR7.4
    statement: >
      ImportExecutedEvent(tableConfigId, actor, successCount, errorCount, committed,
      occurredAt)を購読した場合、targetType="DataImportExport"固定、targetId=tableConfigId、
      operationType=committedがtrueなら"IMPORT_COMMITTED"、falseなら"IMPORT_ROLLED_BACK"、
      actorUserId=actor(このイベントのみ実際にユーザーIDを意味する)、actorRaw=null、
      beforeValue=null、afterValue=nullとしてAuditLogEntryへマッピングし記録する。
      successCount・errorCountはAuditLogEntryへはマッピングしない。
    category: calculation
    applies_to: [AuditLogEntry]
    trigger: "ImportExecutedEventの受信時"
    logic: "AuditLogEntry { targetType: \"DataImportExport\", targetId: event.tableConfigId, operationType: event.committed ? \"IMPORT_COMMITTED\" : \"IMPORT_ROLLED_BACK\", actorUserId: event.actor, actorRaw: null, occurredAt: event.occurredAt, beforeValue: null, afterValue: null }を生成し記録する"
    violation_behaviour: N/A
    source: "Q2確定(A)・Q3確定(A)・Q4確定(B)・Q5確定(A)。実クラス: backend/src/main/java/com/mastersmith/dataio/event/ImportExecutedEvent.java"

  - id: BR7.5
    statement: >
      AuditLogEntryへの記録は追記(INSERT)のみとし、アプリケーション層(リポジトリ/DAO
      インタフェース)にUPDATE/DELETEに相当するメソッドを一切定義してはならない。DBレベルの
      追加防御(トリガー等)は本Boltのスコープでは実装しない。
    category: constraint
    applies_to: [AuditLogEntry]
    trigger: "リポジトリ/DAOインタフェースの設計・実装時全般"
    logic: "AuditLogEntryRepository(仮称)にはsave(insert相当)・findメソッド群のみを定義し、update/deleteメソッドを一切定義しない"
    violation_behaviour: "コードレビュー・アーキテクチャ上の制約として検出する(自動的な実行時エラーではなく、UPDATE/DELETE経路自体を設計・実装しないことで担保する)"
    source: "project.md Mandated(監査ログは改ざん・削除ができないようにする。UPDATE/DELETE経路を持たない追記専用とする)・Q7確定(A)"

  - id: BR7.6
    statement: >
      AuditLogEntryは無期限に保持しなければならない。削除・アーカイブ機能は本MVPスコープでは
      実装しない。
    category: constraint
    applies_to: [AuditLogEntry]
    trigger: N/A(恒久的な制約)
    logic: N/A
    violation_behaviour: N/A
    source: "FR8.3・project.md Out of Scope(監査ログの削除・アーカイブ機能)"

  - id: BR7.7
    statement: >
      イベント購読後、内部設定DBへのAuditLogEntry書き込みが失敗した場合(DB接続断等)、
      失敗を構造化ログ(ERRORレベル)へ記録するのみとし、発行元ユニットの処理へは一切
      影響を与えない(例外の再送出・リトライは行わない)。監査記録の欠落は許容される
      MVPスコープの制約とする。
    category: policy
    applies_to: [AuditLogEntry]
    trigger: "BR7.2〜BR7.4のAuditLogEntry書き込み処理が例外を送出した場合"
    logic: "TRY AuditLogEntryを内部設定DBへ保存する CATCH 例外発生時、構造化ログ(ERRORレベル、購読したイベントの内容を含む)へ記録し、例外を再送出しない(イベント発行元はfire-and-forgetであり、本ユニットの購読処理の成否を待たない設計のため)"
    violation_behaviour: N/A(本ルール自体が失敗時の許容挙動を定める)
    source: "Q8確定(A)。3イベントいずれもfire-and-forget発行であることは各イベントクラスのJavadocに明記済み"

  - id: BR7.8
    statement: >
      複数ユニットから並行してイベントが発行される場合でも、AuditLogEntry間の厳密な到着順・
      グローバルな順序保証は行わない。各AuditLogEntryのoccurredAt(イベント発生元が記録した
      発生日時)のみを正とし、内部設定DBへの書き込み順序とoccurredAtの順序が一致しなくても、
      閲覧画面側でoccurredAtによるソート(BR7.9)を適用すれば実用上問題ないものとする。
    category: policy
    applies_to: [AuditLogEntry]
    trigger: "複数のドメインイベントが短時間内に連続して発行される場合(例: config-import-exportの1回の実行によるConfigChangedEvent・PermissionChangedEventの連続発行)"
    logic: N/A(単調増加のシーケンス番号を追加しない、という設計上の非採用の明記)
    violation_behaviour: N/A
    source: "Q9確定(A)"

  - id: BR7.9
    statement: >
      監査ログ閲覧API(GET /api/audit-log、C6契約)は、occurredAtの降順(新しい記録が先頭)を
      既定の並び順とする。C6契約にはソートパラメータを追加せず、常に固定の降順とする。
    category: policy
    applies_to: [AuditLogEntry]
    trigger: "GET /api/audit-logの呼び出し時"
    logic: "AuditLogEntryをoccurredAtの降順でソートし、page・pageSizeによるページングを適用したうえでitems・totalCountを返す(targetTypeが指定された場合は等価一致でフィルタする)"
    violation_behaviour: N/A
    source: "Q6確定(A)・C6契約(GET /api/audit-log)"

  - id: BR7.10
    statement: >
      監査ログ閲覧画面へのアクセス可否は、AuditLogging自身では判定せず、PermissionEngineの
      canAccessScreen(activeRoleId, screenKey="audit-log")へ委譲する(C10契約、同期呼出)。
      予約screenKey"audit-log"は、permission-engineが監査ログ閲覧画面用にBR3.10・BR3.15で
      既に予約・実装済みのものをそのまま利用し、本ユニット側で新規のscreenKey定義は行わない。
    category: authorization
    applies_to: [AuditLogEntry]
    trigger: "GET /api/audit-logの呼び出し時"
    logic: "IF PermissionEngine.canAccessScreen(activeRoleId, \"audit-log\") = false THEN 403 Forbidden(RFC 9457、C6契約のForbiddenレスポンス)を返す ELSE BR7.9に従い監査ログ一覧を返す"
    violation_behaviour: "403 Forbidden(RFC 9457形式のProblemDetails)を返す(project.md Mandated: 実効権限はサーバー側で再検証する)"
    source: "C6契約・C10契約・permission-engine rules.md BR3.10, BR3.15(既存実装済みのscreenKey予約をそのまま利用)"

  - id: BR7.11
    statement: >
      AuditLogging(共通エンジン層に位置づけられるコンポーネント)には、特定業務固有の
      テーブル名・カラム名・業務ルールをハードコードしてはならない。targetType・targetId・
      operationTypeは、購読したイベントクラスの型・フィールド値からBR7.2〜BR7.4の規則に
      従って汎用的に導出し、特定の業務テーブル・業務ルールに依存する分岐を持たない。
    category: constraint
    applies_to: [AuditLogEntry]
    trigger: N/A(実装全般に適用される横断的制約)
    logic: N/A
    violation_behaviour: "コードレビューで検出する(特定テーブル名・カラム名の分岐が実装に含まれていないことを確認する)"
    source: "FR1.6・project.md Mandated(共通エンジン層には特定業務固有のテーブル名・カラム名・業務ルールをハードコードしない)"
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR7.1 | policy | 本Boltで購読するイベント種別は現存する3種類のみ |
| BR7.2 | calculation | ConfigChangedEvent → AuditLogEntryへのマッピング規則 |
| BR7.3 | calculation | PermissionChangedEvent → AuditLogEntryへのマッピング規則 |
| BR7.4 | calculation | ImportExecutedEvent → AuditLogEntryへのマッピング規則 |
| BR7.5 | constraint | 追記専用(UPDATE/DELETEメソッドをアプリ層に定義しない) |
| BR7.6 | constraint | 無期限保持(削除・アーカイブ機能なし) |
| BR7.7 | policy | 記録失敗時はERRORログのみ、発行元へ影響を与えない |
| BR7.8 | policy | 記録順序はoccurredAtのみを正とし厳密な順序保証は不要 |
| BR7.9 | policy | 閲覧APIの既定並び順はoccurredAt降順 |
| BR7.10 | authorization | 閲覧画面アクセス可否はPermissionEngineへ委譲(screenKey="audit-log") |
| BR7.11 | constraint | 共通エンジン層としての業務固有ハードコード禁止(FR1.6) |
