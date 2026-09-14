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

# Entities — data-import-export (U8)

`functional-design-questions.md`の確定回答に基づき、data-import-exportユニットが扱うデータ形状を定義する。本ユニットは業務データ(行データ)そのものを所有するエンティティを持たない(業務データのテーブル定義・行データは対象RDBMS上の業務テーブルであり、`ColumnConfig`によって記述される)。ここでは、CSVエクスポート・インポート処理そのものに必要な値オブジェクト(リクエスト・結果・ドメインイベント)を定義する。永続化されるのは`ImportExecutedEvent`のみで、それ以外はいずれもリクエスト/レスポンスの一時的な値オブジェクトである。

```yaml
entities:
  - name: CsvExportRequest
    description: >
      CSVエクスポート実行時の内部パラメータ(list-engineからDataImportExportへの内部呼び出し
      専用。WEB APIには公開しない)。一覧画面の現在の検索条件・ソート順(C1: `GET
      /records/export`のfilter/sortクエリパラメータをlist-engineが中継)、および列単位の
      実効READ権限情報(list-engineがサーバー側で算出したpermittedColumnNames)を渡す
      （Q2確定・レビュー指摘R-01対応、Contract Design追補Q6=Aで解決済み）。
    attributes:
      - name: tableConfigId
        type: string
        required: true
        references: TableConfig（config-engine）
        description: エクスポート対象テーブル（config-engineのTableConfig）を指すID
      - name: filter
        type: object
        required: false
        description: >
          一覧画面の検索条件（C1のGET /records と同じ形状、動的スキーマ）。list-engineから
          そのまま引き継ぐ
      - name: sort
        type: string
        required: false
        description: 一覧画面のソート順（C1のGET /records と同じ形式）
      - name: permittedColumnNames
        type: array
        required: true
        item_shape: string
        description: >
          list-engineが呼び出し前にPermissionEngineへ問い合わせ済みの、実行ユーザーが
          実効READ権限を持つ列名の一覧（レビュー指摘R-01対応）。DataImportExport自身は
          PermissionEngineへ問い合わせない（BR8.8）ため、この一覧がBR8.2（hidden列・
          READ権限未満列の除外）の実行時入力として、visibility(hidden除外)と組み合わせて
          用いられる唯一の情報チャネルである
    entity_constraints:
      - "永続化しない。呼び出しごとの一時的なリクエストパラメータ"
    relationships: []

  - name: CsvImportRequest
    description: >
      CSVインポート実行時の内部パラメータ（レビュー指摘R-02対応で明示化。record-edit-engine
      からDataImportExportへの内部呼び出し専用で、WEB API(C2)のリクエストボディには
      actorを公開しない）。実行者情報はrecord-edit-engineが自身のREST層(C2、Bearer認証済み)
      のSpring Security認証済みプリンシパルから取得して渡す（Contract Design追補Q7=Aで
      解決済み）。
    attributes:
      - name: tableConfigId
        type: string
        required: true
        references: TableConfig（config-engine）
        description: インポート対象テーブル（config-engineのTableConfig）を指すID
      - name: file
        type: binary
        required: true
        description: アップロードされたCSVファイル（InputStream、C13契約のfileパラメータ）
      - name: actor
        type: string
        required: true
        description: >
          インポートを実行した利用者のユーザーID（BR8.9の`ImportExecutedEvent.actor`の
          出所）。record-edit-engineが自身のREST層(C2、Bearer認証済み)のSpring Security
          認証済みプリンシパルから取得し、DataImportExportの内部インタフェース`importCsv`
          （C13、Contract Design追補Q7=Aで解決済み）へパラメータとして渡す
    entity_constraints:
      - "永続化しない。呼び出しごとの一時的なリクエストパラメータ"
    relationships: []

  - name: CsvColumnDefinition
    description: >
      CSVの列とconfig-engineのColumnConfigを対応付けるための、エクスポート/インポート
      処理内部で組み立てる一時的な列定義。config-engineのColumnConfig（`getColumnConfigs`）
      から導出し、永続化しない。
    attributes:
      - name: columnName
        type: string
        required: true
        description: CSVヘッダーおよびDB上のカラム名（ColumnConfig.columnNameと同一）
      - name: editorType
        type: string
        required: true
        allowed_values: [text, textarea, integer, decimal, date, datetime, select, radio, switch, checkbox]
        description: 型変換・バリデーションの基準とするエディタ種別（ColumnConfig.editorTypeを参照）
      - name: validationRule
        type: object
        required: false
        description: config-engineのColumnConfig.validationRuleをそのまま引き継ぐ（BR8.4）
      - name: visibility
        type: string
        allowed_values: [visible, hidden]
        description: エクスポート対象列の絞り込みに用いる（BR8.2）
      - name: isPrimaryKey
        type: boolean
        required: true
        description: >
          対象テーブルの主キー列かどうか。インポート時のINSERT/UPDATE判定（Q4/BR8.3）に用いる。
          schema-introspector（U2）が対象RDBMSのメタデータ読み取り時に判定し、config-engine
          のC9契約（`ColumnConfig.isPrimaryKey`、Contract Design追補Q8=Aで解決済み）から
          そのまま取得する（`construction/config-engine/functional-design/rules.md` BR1.14）
    entity_constraints:
      - "永続化しない。エクスポート/インポート実行のたびにconfig-engineから取得し直す一時データ"
    relationships:
      - target: CsvExportRequest
        cardinality: "0..*"
        direction: "CsvExportRequest 1 -> 0..* CsvColumnDefinition"
        description: >
          1回のエクスポート実行(CsvExportRequest)は、対象テーブルの列数に応じた0件以上の
          CsvColumnDefinitionを解決して参照する（レビュー指摘R-04対応で表記・方向を明確化）
      - target: CsvImportRequest
        cardinality: "0..*"
        direction: "CsvImportRequest 1 -> 0..* CsvColumnDefinition"
        description: >
          1回のインポート実行(CsvImportRequest)は、対象テーブルの列数に応じた0件以上の
          CsvColumnDefinitionを解決して参照する（レビュー指摘R-02対応で関係を明示化）

  - name: CsvImportRowResult
    description: >
      CSVインポート時、1行の処理結果を表す値オブジェクト。C13契約の`RowError`を包含し、
      成功行についても内部処理で保持する（最終的な公開レスポンスは成功件数とエラー一覧のみ、
      Q10）。
    attributes:
      - name: rowNumber
        type: integer
        required: true
        description: CSVファイル上の行番号（ヘッダー行を除く、1始まり）
      - name: outcome
        type: string
        required: true
        allowed_values: [VALID, INVALID]
        description: バリデーション結果（BR8.5〜BR8.7の検証を経た結果）
      - name: operation
        type: string
        required: false
        allowed_values: [INSERT, UPDATE]
        description: outcomeがVALIDの場合の確定操作種別（Q4のupsert判定結果、BR8.3）
      - name: errors
        type: array
        required: false
        description: outcomeがINVALIDの場合のフィールド単位エラー一覧
        item_shape: { field: string, message: string }
    entity_constraints:
      - "永続化しない。importCsv呼び出し1回の処理内でのみ保持する中間結果"
    relationships: []

  - name: ImportExecutedEvent
    description: >
      CSVインポート実行そのものをAuditLogging（U7）へ通知するドメインイベント（BR8.9）。
      `components.md`のDataImportExport→AuditLogging（style: event）に対応する。Q8確定により、
      行単位の変更前後値は含めない実行単位のサマリイベントとし、fire-and-forgetで発行される
      値オブジェクトである（内部設定DB・業務データ用RDBMSのいずれにも永続化しない）。
    attributes:
      - name: tableConfigId
        type: string
        required: true
        description: インポート対象テーブル（config-engineのTableConfig）を指すID
      - name: actor
        type: string
        required: true
        description: インポートを実行した利用者のユーザーID
      - name: successCount
        type: integer
        required: true
        description: インポートに成功した行数（0件を含む。Q10により全体ロールバック時は常に0）
      - name: errorCount
        type: integer
        required: true
        description: バリデーションエラーとなった行数
      - name: committed
        type: boolean
        required: true
        description: >
          Q10（全件検証後の一括コミット方式）により、1件でもエラーがあれば全体をロールバック
          するため、成功時はtrue・エラーが1件以上あった場合は必ずfalseとなる
      - name: occurredAt
        type: datetime
        required: true
        description: インポート実行完了日時
    entity_constraints:
      - "内部設定DB・業務データ用RDBMSいずれにも永続化しない（AuditLoggingが購読・記録する側の責務）"
      - "行単位の変更前後の値は含まない（Q8確定・Q8 Follow-up確定の意図的なスコープ判断。project.md Mandatedとの関係はfunctional-spec.mdのAssumptions & Open Questions参照）"
    relationships: []
```

## 人間可読サマリー

data-import-exportユニットは、業務データそのものを所有する永続エンティティを持たない。エクスポート・インポート処理の実行に必要な一時的な値オブジェクトと、監査ログ連携用のドメインイベントのみを保持する。

- **CsvExportRequest**: エクスポート実行時の内部パラメータ(対象テーブル・検索条件・ソート順・実効READ権限列一覧)。list-engineからDataImportExportへの内部呼び出し専用で、WEB APIには公開しない(Contract Design追補Q6=Aで解決済み)。
- **CsvImportRequest**: インポート実行時の内部パラメータ(対象テーブル・CSVファイル・実行者)。record-edit-engineからDataImportExportへの内部呼び出し専用で、WEB APIには公開しない(Contract Design追補Q7=Aで解決済み)。
- **CsvColumnDefinition**: config-engineの`ColumnConfig`から導出する、エクスポート列の絞り込み・インポートバリデーション・主キー判定に使う一時的な列定義。永続化しない。主キー判定用の`isPrimaryKey`は、config-engine側の契約(C9)から取得する(Contract Design追補Q8=Aで解決済み)。
- **CsvImportRowResult**: インポート処理内部で保持する行単位の中間結果(バリデーション結果・INSERT/UPDATE区分・エラー内容)。最終的に公開されるのは成功件数とエラー一覧(C13の`ImportResult`)のみ。
- **ImportExecutedEvent**: AuditLogging(U7)へ発行する実行単位のサマリイベント。Q8確定により行単位の変更前後値は含まない。この点はproject.mdのMandated(監査ログの変更前後値記録)との関係で意図的なスコープ判断として明示している(functional-spec.md参照)。

主キー・業務キーの定義自体、およびエクスポート/インポート対象となる業務データ本体の行データ(実体)は、業務データ用RDBMS上の実テーブルそのものであり、本ユニット固有のエンティティとしては再定義しない。
