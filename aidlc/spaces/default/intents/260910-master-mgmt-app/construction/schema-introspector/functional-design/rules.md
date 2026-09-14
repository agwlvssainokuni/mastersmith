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

# Rules — schema-introspector (U2)

`functional-design-questions.md`の確定回答(Follow-upによるQ1/Q3のB変更を含む)に基づく、schema-introspectorユニットのビジネスルール。ルールID群は`unit-of-work.md`のUnit番号(U2)に対応し`BR2.y`とする。

```yaml
rules:
  - id: BR2.1
    statement: >
      schema-introspectorは対象RDBMS(業務データ用)に対し読み取り専用アクセスのみを行い、
      DDL/DMLを含む一切の書き込みを行わない。
    category: constraint
    applies_to: [RdbmsTableMetadata, RdbmsColumnMetadata]
    trigger: "対象RDBMSのメタデータカタログへの問い合わせ時"
    logic: "メタデータカタログ(information_schema等)への参照クエリのみを実行し、対象RDBMS上のテーブル・データを一切変更しない"
    violation_behaviour: N/A(設計制約であり違反ケースは想定しない。実装は読み取り専用接続/クエリのみで構成する)
    source: "unit-of-work.md U2実装上の注意・制約(業務データ用RDBMSへの読み取り専用アクセスのみ)"

  - id: BR2.2
    statement: >
      SchemaIntrospectionRequest.tableNamesが指定された場合は指定テーブルのみを、省略された
      場合はschemaName配下の全テーブルを読み取り対象とする。
    category: policy
    applies_to: [SchemaIntrospectionRequest]
    trigger: "POST /api/config/schema-introspection呼び出し時"
    logic: "IF tableNamesが指定される THEN 指定された各テーブルのみをRdbmsTableMetadataとして読み取る ELSE schemaName配下の全テーブルを読み取る"
    violation_behaviour: N/A
    source: "C8契約(tableNamesのnullable説明)"

  - id: BR2.3
    statement: >
      schema-introspectorは対象RDBMSの接続情報からRDBMS方言(PostgreSQL/MySQL/MariaDBの
      いずれか)を判定し、config-engineへのドラフト書込み(`TableConfigDraft.dialect`)に
      設定しなければならない。
    category: policy
    applies_to: [RdbmsTableMetadata]
    trigger: "メタデータ読み取り開始時"
    logic: "対象RDBMSへの接続設定(application.yml等)からRdbmsDialect(POSTGRESQL | MYSQL | MARIADB)を判定し、writeTableConfigDraft呼び出し時のTableConfigDraft.dialectへ設定する"
    violation_behaviour: "未対応の方言が検出された場合、fail fastとしイントロスペクション処理全体を中断する(BR2.9)"
    source: "FR1.2、construction/config-engine/functional-design/rules.md BR1.12"

  - id: BR2.4
    statement: >
      schema-introspectorは対象RDBMSのメタデータカタログから主キー制約を判定し、各カラムの
      RdbmsColumnMetadata.isPrimaryKeyへ設定しなければならない。複合主キー(主キー制約が
      複数カラムにまたがる)の場合、既定動作として、当該制約に含まれる全カラムについて
      isPrimaryKey=trueを設定する(単一列に絞り込まない)。単一主キー列を主な想定とし、
      複合主キーへの詳細な対応(data-import-export側のCSVインポートupsert判定が複合主キーを
      正しく扱えるかどうか等)は本MVPスコープの主要な対象外とするが、schema-introspector
      自身のisPrimaryKey判定ロジックとしては上記既定動作を明確な仕様とする。
    category: policy
    applies_to: [RdbmsColumnMetadata]
    trigger: "対象テーブルのカラムメタデータ読み取り時"
    logic: "IF 対象カラムが主キー制約に含まれる THEN isPrimaryKey=true(制約が複数カラムにまたがる場合も、含まれる全カラムについてtrueとする) ELSE isPrimaryKey=false。判定結果をColumnDraftEntry.isPrimaryKeyへそのまま設定する"
    violation_behaviour: N/A
    source: "Contract Design追補C9(レビュー指摘R-05対応)、construction/config-engine/functional-design/rules.md BR1.14。複合主キー時の既定動作はレビュー指摘R-03対応で明確化"

  - id: BR2.5
    statement: >
      ColumnConfigのeditorType/formatの初期ドラフト値は、schema-introspector自身では決定
      しない。schema-introspectorは対象カラムの生の型名(rawTypeName)をそのまま
      ColumnDraftEntryへ設定してconfig-engineへ渡し、editorTypeの決定はconfig-engine側の
      RDBMS方言正規化ロジック(論理型への正規化と`defaultEditorType()`)に委ねる。外部キー
      参照カラムであっても特別扱いはしない(選択肢自体をselect/radioへ自動変更することは
      行わない)。formatはドラフト生成時には設定せず、未設定(null)のままとする。
    category: policy
    applies_to: [RdbmsColumnMetadata]
    trigger: "対象カラムのColumnDraftEntry組み立て時"
    logic: >
      ColumnDraftEntry.rawTypeName = RdbmsColumnMetadata.rawTypeName(正規化せずそのまま
      設定)。外部キー参照カラムかどうかの判定・分岐は行わない。formatに相当するフィールドは
      ColumnDraftEntryに含めない(config-engine側で未設定のまま生成される)
    violation_behaviour: N/A(委譲ルールであり違反ケースは存在しない)
    source: "Q1確定(Follow-upによりBへ変更。実装済みconfig-engineがFK検出・fkReference設定に未対応のため)"

  - id: BR2.6
    statement: >
      schema-introspectorは楽観ロック対象列(optimisticLockColumn)を自動検出しない。ドラフト
      生成時にこの情報を一切設定・伝搬せず、常に未設定(null)のままとし、業務担当者が
      config-engineの設定画面から後から手動指定する。
    category: policy
    applies_to: [RdbmsTableMetadata]
    trigger: "対象テーブルのTableDraftEntry組み立て時"
    logic: "RdbmsTableMetadataは楽観ロック対象列に相当する属性を持たず、TableDraftEntryにも当該情報を含めない"
    violation_behaviour: N/A
    source: "Q2確定、construction/config-engine/functional-design/rules.md BR1.7"

  - id: BR2.7
    statement: >
      対象のschemaName/tableNameに対応するTableConfigが既にconfig-engineに存在する場合の
      再実行時スキップは、schema-introspector自身では制御しない。schema-introspectorは
      対象範囲(BR2.2)の全テーブルのメタデータを毎回読み取り、そのままwriteTableConfigDraft
      へ渡す。既存設定の保護(テーブル単位のスキップ、新規カラムの追加も行わない)はconfig-
      engine側のBR1.8が担う。
    category: policy
    applies_to: [RdbmsTableMetadata]
    trigger: "writeTableConfigDraft呼び出し時"
    logic: "schema-introspectorは既存TableConfigの有無を事前判定せず、読み取った全テーブルをTableConfigDraftとして一括送信する。スキップ判定はconfig-engine側のwriteTableConfigDraft実装(BR1.8: テーブル単位で既存TableConfigがあれば当該テーブルの取り込みをスキップ)に委ねる"
    violation_behaviour: N/A(委譲ルールであり違反ケースは存在しない)
    source: "Q3確定(Follow-upによりBへ変更。実装済みconfig-engineのwriteTableConfigDraftがテーブル単位スキップのみ実装・テスト済みのため)、construction/config-engine/functional-design/rules.md BR1.8"

  - id: BR2.8
    statement: >
      POST /api/config/schema-introspectionの呼び出しは、frontend-ui(U12)の設定管理画面
      (config-import-exportと同一画面)からのみ起動され、呼び出し元のアクティブロールが
      当該画面へのアクセス権限を持つことをPermissionEngineへ問い合わせて再検証しなければ
      ならない。画面表示の出し分け(クライアント側UI非表示)だけに依存してはならない。
    category: authorization
    applies_to: [SchemaIntrospectionRequest]
    trigger: "POST /api/config/schema-introspection呼び出し時"
    logic: >
      IF PermissionEngine.canAccessScreen(activeRoleId, "config-import-export") == false
      THEN 403 Forbidden(C8のForbiddenレスポンス)を返し、メタデータ読み取り・
      writeTableConfigDraft呼び出しのいずれも実行しない。screenKeyは設定管理画面が
      config-import-exportと共通の画面であるため、既存の予約screenKey("config-import-
      export")をそのまま用いる(新規screenKeyは追加しない)
    violation_behaviour: "403 Forbidden(C8契約のForbiddenレスポンス)"
    source: "project.md Mandated(サーバー側での実効権限再検証)、unit-of-work.md U12(設定管理画面がconfig-import-exportとschema-introspectorの両方の起動経路を持つ)、construction/permission-engine/functional-design/rules.md BR3.10・BR3.13"

  - id: BR2.9
    statement: >
      対象RDBMSへの接続エラー・メタデータ読み取り失敗(未対応方言の検出を含む)が発生した
      場合、schema-introspectorはfail fastとし、部分的なwriteTableConfigDraft呼び出しを
      行わずにイントロスペクション処理全体を中断しなければならない。
    category: validation
    applies_to: [RdbmsTableMetadata]
    trigger: "対象RDBMSのメタデータカタログへの問い合わせ時"
    logic: "IF 対象RDBMSへの接続失敗 OR メタデータ読み取り失敗 OR RdbmsDialect判定不可 THEN 422(C8のValidationErrorレスポンス)を返し、writeTableConfigDraftを呼び出さない(読み取りを全テーブル完了した後に1回だけwriteTableConfigDraftを呼び出す設計のため、部分書込みは発生しない)"
    violation_behaviour: "422 Unprocessable Content(C8契約のValidationErrorレスポンス、RFC 9457のProblemDetails)"
    source: "project.md Mandated(設定定義自体の誤りはfail fast)、Construction Phase Guardrails(Error Handling: integration boundariesでの例外処理、recoverable/fatalの区別)"

  - id: BR2.10
    statement: >
      schema-introspectorは対象RDBMSのメタデータカタログからNULL可否(NOT NULL制約の有無)を
      読み取り、RdbmsColumnMetadata.nullableへ設定する(FR1.4の読み取り対象「テーブル/カラム/
      型/NULL可否/主キー/外部キー等」に対応)。ただし、config-engine(U1)のC9契約型
      `ColumnDraftEntry`にはNULL可否に対応するフィールドが存在せず、`ColumnConfig.
      validationRule`への"required"ルールの自動導出経路も実装されていないため、本MVPでは
      読み取ったnullableの値をwriteTableConfigDraft呼び出しへ伝搬しない。バリデーションルール
      (必須入力等)の設定は、引き続き業務担当者がconfig-engineの設定画面から手動で行う。
    category: policy
    applies_to: [RdbmsColumnMetadata]
    trigger: "対象テーブルのカラムメタデータ読み取り時、およびColumnDraftEntry組み立て時"
    logic: >
      IF 対象カラムがNOT NULL制約を持つ THEN nullable=false ELSE nullable=true。
      RdbmsColumnMetadataとしては保持するが、ColumnDraftEntry組み立て時(BR2.5)には
      nullableを設定・伝搬しない(ColumnDraftEntryに対応フィールドが存在しないため)
    violation_behaviour: N/A(読み取りと伝搬の意図的な分離であり違反ケースは存在しない)
    source: "FR1.4(読み取り対象の明記)、レビュー指摘R-01対応。config-engine側への伝搬経路追加(ColumnDraftEntry拡張・validationRule自動導出)は、Q1/Q3同様に実装済みconfig-engineへの手戻りを伴うため、Code Generation着手前の追加検討事項として`functional-spec.md`のAssumptions & Open Questionsに記録する"
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR2.1 | constraint | 業務データ用RDBMSへの読み取り専用アクセスのみ |
| BR2.2 | policy | tableNames省略時はスキーマ配下の全テーブルが対象 |
| BR2.3 | policy | RDBMS方言を判定しTableConfigDraft.dialectへ設定する |
| BR2.4 | policy | 主キー制約を判定しisPrimaryKeyへ設定する |
| BR2.5 | policy | editorType/formatの決定はconfig-engine側に委譲し、rawTypeNameをそのまま渡す(FK特別扱いなし) |
| BR2.6 | policy | 楽観ロック対象列は自動検出せず常に未設定のまま伝搬する |
| BR2.7 | policy | 再実行時のテーブル単位スキップはconfig-engine側(BR1.8)に委譲する |
| BR2.8 | authorization | 設定管理画面と同一のscreenKeyでcanAccessScreen再検証を行う |
| BR2.9 | validation | メタデータ読み取り失敗時はfail fastし部分書込みを行わない |
| BR2.10 | policy | NULL可否は読み取るがConfigDraftEntryへは伝搬しない(config-engine側に対応フィールドなし) |
