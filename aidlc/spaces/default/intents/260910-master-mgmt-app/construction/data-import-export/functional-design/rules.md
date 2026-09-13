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

# Business Rules — data-import-export (U8)

`functional-design-questions.md`の確定回答に基づく、data-import-exportユニットの業務ルール。ルールIDは`BR8.y`(グループ8 = data-import-export)とする。

```yaml
rules:
  - id: BR8.1
    statement: >
      CSVファイルの形式は、文字コードUTF-8(BOM付き)、区切り文字はカンマ、1行目はカラム名の
      ヘッダー行、改行コードはCRLFで統一しなければならない。エクスポート・インポート双方で
      同一形式を用いる。
    category: constraint
    applies_to: [CsvExportRequest, CsvImportRowResult]
    trigger: "CSVエクスポート実行時のファイル生成、CSVインポート実行時のファイル読み取り"
    logic: "エクスポート: UTF-8(BOM付き)・カンマ区切り・ヘッダー行付きCRLF形式でファイルを生成する。インポート: 同形式でファイルを読み取る(異なる形式のファイルはBR8.7のパースエラーとして扱う)"
    violation_behaviour: "インポート時、指定形式でパースできない場合はファイル全体をエラーとする(行単位エラーではなくファイル形式エラー)"
    source: "Q1確定"

  - id: BR8.2
    statement: >
      CSVエクスポート時、対象テーブルのColumnConfigのうち`visibility: hidden`の列、および
      実行ユーザーがREAD権限未満の列は出力対象から除外しなければならない。列単位の実効
      READ権限情報は、DataImportExport自身が判定するのではなく、呼び出し元list-engineが
      `CsvExportRequest.permittedColumnNames`(レビュー指摘R-01対応で追加)として渡す。
    category: authorization
    applies_to: [CsvColumnDefinition, CsvExportRequest]
    trigger: "CSVエクスポート実行時の列選定"
    logic: "IF ColumnConfig.visibility = hidden OR columnName NOT IN CsvExportRequest.permittedColumnNames THEN 当該列をCSV出力列から除外する"
    violation_behaviour: N/A(該当列を出力しないことで対応。権限判定自体は呼び出し元list-engineが実施済みの実効権限を`permittedColumnNames`として渡す、BR8.8参照)
    source: "Q3確定・レビュー指摘R-01対応"

  - id: BR8.3
    statement: >
      CSVインポート時、対象テーブルの主キー列に対応するCSV列に値が設定されている行は既存行の
      更新(UPDATE)、当該列が存在しない・値が空の行は新規作成(INSERT)として扱う(upsert方式)。
    category: policy
    applies_to: [CsvImportRowResult]
    trigger: "CSVインポートの各行の処理開始時"
    logic: "IF CSV行の主キー列に非空の値が設定されている THEN 当該主キー値で対象テーブルを検索し、UPDATE操作とする(該当行が存在しない場合はBR8.6の行単位バリデーションエラーとする) ELSE INSERT操作とする"
    violation_behaviour: "UPDATE対象の主キー値に該当する既存行が存在しない場合、当該行を行単位のバリデーションエラーとする(BR8.6)"
    source: "Q4確定"

  - id: BR8.4
    statement: >
      CSVインポートには楽観ロック競合検出を適用しない。対象テーブルに楽観ロック対象列
      (config-engineの`optimisticLockColumn`)が設定されている場合でも、インポートによる
      更新は常に後勝ちで上書きする。
    category: policy
    applies_to: [CsvImportRowResult]
    trigger: "BR8.3によりUPDATE操作と判定された行の保存"
    logic: "楽観ロック対象列の設定有無に関わらず、`optimisticLockVersion`相当の値による競合検出は行わず、CSVの内容で対象行を無条件に上書きする"
    violation_behaviour: N/A(後勝ちであり競合エラーは発生しない。record-edit-engineの詳細・編集画面との同時実行競合は本ユニットの対象外)
    source: "Q5確定"

  - id: BR8.5
    statement: >
      CSVインポートの各行は、config-engineから取得した対象列の`validationRule`(BR1.9参照)を
      すべて適用してバリデーションしなければならない。加えて、CSVの値を対象列の`editorType`
      に応じた型へ変換できない場合も、行単位のバリデーションエラーとして扱わなければならない。
    category: validation
    applies_to: [CsvImportRowResult]
    trigger: "CSVインポートの各行の処理"
    logic: >
      FOR EACH 列 IN 対象テーブルのCsvColumnDefinition:
        IF 列の値をeditorTypeに応じた型へ変換できない THEN 型変換エラーとして当該行にエラーを追加する
        ELSE 変換後の値に対しvalidationRule(required/minLength/maxLength/min/max/pattern等)を適用し、
          違反があれば当該行にエラーを追加する
    violation_behaviour: "エラーが1件以上ある行はoutcome=INVALIDとし、フィールド単位のエラーメッセージ(BR8.6)を記録する。当該行は保存しない(BR8.7)"
    source: "Q6確定・FR12.1"

  - id: BR8.6
    statement: >
      CSVインポートのバリデーションエラーは、開発者向けのスタックトレースではなく、行番号・
      フィールド単位のエラーメッセージ(`RowError { row, message }`、C13契約)として利用者に
      返さなければならない。
    category: policy
    applies_to: [CsvImportRowResult]
    trigger: "BR8.3(主キー不一致)・BR8.5(バリデーション)の違反検出時"
    logic: "違反が検出された行について、CSVファイル上の行番号(ヘッダー行を除く1始まり)と、違反したフィールド名・エラー内容を組み合わせたメッセージを生成する"
    violation_behaviour: N/A(本ルール自体がエラー提示の形式を定める)
    source: "project.md Mandated(利用者向け入力データ検証エラーはフィールド単位のメッセージで返す)・C13契約"

  - id: BR8.7
    statement: >
      CSVインポートは、ファイル内の全行のバリデーションを実施したうえで、1行でもバリデーション
      エラー(BR8.5・BR8.6)が存在する場合は、当該インポート全体をDBへ反映せず、成功行を含めて
      すべてロールバックしなければならない。全行が有効な場合にのみ、1つのデータベース
      トランザクションで一括してコミットする。BR8.10のストリーミング読み取りと本ルールの
      一括コミットを両立させる具体的な実装方式は、BR8.10のlogic欄(レビュー指摘R-03対応)を
      参照する。
    category: policy
    applies_to: [CsvImportRowResult, ImportExecutedEvent]
    trigger: "CSVインポートの全行バリデーション完了時"
    logic: >
      全行のバリデーション(BR8.3〜BR8.6)を、最初のエラーで中断せず最後まで実施し、
      各行のCsvImportRowResult(バリデーション結果・変換後の値・確定した操作種別)をBR8.10の
      一時バッファへ収集する。
      IF 収集結果に1件以上のoutcome=INVALIDが存在 THEN バッファの内容を破棄しDBへの反映
        (INSERT/UPDATE)を一切行わず、ImportExecutedEvent.committed=false、successCount=0、
        errorCount=INVALID件数として記録する
      ELSE バッファに保持した全行の変換後データを用いて、INSERT/UPDATEを1つのトランザクション
        でコミットし、ImportExecutedEvent.committed=true、successCount=全行数、errorCount=0
        として記録する
    violation_behaviour: "バリデーションエラーが1件でもあれば、当該インポート全体を不成立とし、成功していたはずの行も含めて一切DBへ反映しない"
    source: "Q10確定・C13契約note(全体を即時失敗にはしない=行単位バリデーションを中断せず全行分の結果を収集するという意味であり、コミット単位は別論点として整理、Q10 Answer参照)・レビュー指摘R-03対応"

  - id: BR8.8
    statement: >
      DataImportExportは、CSVエクスポート・インポートの実行時に実効権限の再検証を行わない。
      list-engine(エクスポート起動元、READ権限)およびrecord-edit-engine(インポート起動元、
      CREATE権限、UPDATEを伴う場合はFULL権限)が、DataImportExportを呼び出す前に実効権限を
      検証済みであることを前提とする。
    category: authorization
    applies_to: [CsvExportRequest, CsvImportRowResult]
    trigger: "list-engineからのexportCsv呼び出し、record-edit-engineからのimportCsv呼び出し"
    logic: "DataImportExportはPermissionEngineへの問い合わせを行わない。呼び出し元ユニットが同一プロセス内で事前に実効権限を検証済みという契約(内部委譲)を前提として処理を進める"
    violation_behaviour: N/A(本ユニット自体は権限違反を検出しない。呼び出し元の権限判定漏れは呼び出し元ユニットの責務)
    source: "Q7確定"

  - id: BR8.9
    statement: >
      DataImportExportは、CSVインポートの実行完了時(成功・全体ロールバックのいずれの場合も)、
      AuditLogging(U7)へ実行単位のサマリイベント(ImportExecutedEvent)を1件発行しなければ
      ならない。本イベントには行単位の変更前後の値を含めない。
    category: policy
    applies_to: [ImportExecutedEvent]
    trigger: "CSVインポート処理の完了時(BR8.7のコミットまたはロールバック確定後)"
    logic: "IMPORTの実行が完了したら、ImportExecutedEvent(tableConfigId, actor, successCount, errorCount, committed, occurredAt)を1件生成しAuditLoggingへ発行する(fire-and-forget)"
    violation_behaviour: N/A(イベント発行はfire-and-forgetであり、AuditLogging側の購読処理完了を待たない)
    source: >
      Q8確定・Q8 Follow-up確定(意図的なスコープ判断)。project.md Mandated「監査ログは
      少なくとも操作者・操作対象・操作種別・日時・変更前後の値を記録する」との関係は、
      functional-spec.mdのAssumptions & Open Questionsに明記する
    notes: >
      本ルールはproject.mdのMandatedが要求する「変更前後の値」の記録範囲を、CSVインポートに
      関しては意図的にサマリレベルへ限定するスコープ判断である(BR1.13のconfig-engineにおける
      行(エンティティ)単位イベント発行とは異なる粒度)。これは見落としではなく、
      functional-design-questions.md Q8 Follow-upでの確認を経た明示的な合意である。

  - id: BR8.10
    statement: >
      CSVエクスポート・インポートは、対象RDBMSカーソル/CSVファイルからの逐次読み取りに基づく
      ストリーミング処理として実装しなければならない。エクスポートは対象データ全件を一括で
      アプリケーションのメモリへ読み込んではならない。インポートは、CSVファイル自体を1回の
      ストリーミング走査で逐次読み取りながら各行を検証し、検証済みの変換後データ(生のCSV
      文字列ではなく、型変換・validationRule適用後の軽量な行データのみ)を一時バッファへ
      保持することで、BR8.7の全件検証後一括コミットと両立させる(レビュー指摘R-03対応)。
    category: constraint
    applies_to: [CsvExportRequest, CsvImportRowResult]
    trigger: "CSVエクスポート・インポートの実行全体"
    logic: >
      エクスポート: 業務データ用RDBMSからカーソル経由で1行ずつ読み取り、都度CSV出力ストリーム
      へ書き込む(生データを全件メモリへ保持しない)。
      インポート: CSVファイルを1回のストリーミング走査で1行ずつ逐次読み取り、都度BR8.5の
      バリデーション・型変換を行単位で実施し、変換後の行データ(CsvImportRowResult相当の
      軽量なオブジェクト、生のCSV行文字列は保持しない)を一時バッファ(アプリケーション内
      メモリ上の配列、または行数が多い場合は一時テーブル)へ蓄積する。全行の読み取り・検証が
      完了した時点でBR8.7の判定(コミット/ロールバック)を行う。CSVファイルの2回読み取り
      (2パス方式)は行わない。
    violation_behaviour: N/A(実装方針の制約であり違反ケースは想定しない)
    source: "Q9確定・NFR1(1テーブル最大10万行程度の性能確保)・レビュー指摘R-03対応"
    notes: >
      NFR1の想定規模(1テーブル最大10万行程度)において、一時バッファが保持するのは変換後の
      軽量な行データ(列数×10万行のプリミティブ値)のみであり、CSVファイルの生データや
      HTTPリクエスト全体を複製して保持するわけではないため、アプリケーションメモリへの
      影響は限定的と評価する(BR8.10・BR8.7の整合、レビュー指摘R-03対応)。より大規模な
      データ量を扱う必要が生じた場合は、一時テーブルへのバッファリング方式への切り替えを
      拡張余地として残す。
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR8.1 | constraint | CSVファイル形式(UTF-8 BOM付き・カンマ区切り・ヘッダー行・CRLF) |
| BR8.2 | authorization | エクスポート対象列からhidden・READ権限未満列を除外 |
| BR8.3 | policy | 主キー列によるINSERT/UPDATE判定(upsert方式) |
| BR8.4 | policy | インポートには楽観ロックを適用せず後勝ち |
| BR8.5 | validation | validationRule全適用+型変換エラーの検出 |
| BR8.6 | policy | 行番号・フィールド単位のエラーメッセージ形式 |
| BR8.7 | policy | 全行検証後の一括コミット(1件でもエラーがあれば全体ロールバック) |
| BR8.8 | authorization | 権限は呼び出し元の事前検証を信頼し、本ユニットでは再検証しない |
| BR8.9 | policy | 実行単位のサマリ監査イベント(ImportExecutedEvent)を1件発行 |
| BR8.10 | constraint | ストリーミング処理による大量データ対応(NFR1) |
