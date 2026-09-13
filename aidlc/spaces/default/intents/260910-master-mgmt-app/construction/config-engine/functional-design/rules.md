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

# Business Rules — config-engine (U1)

`functional-design-questions.md`の確定回答に基づく、config-engineユニットの業務ルール。ルールIDは`BR1.y`（グループ1 = config-engine）とする。

```yaml
rules:
  - id: BR1.1
    statement: >
      設定定義自体に誤り（必須プロパティ欠落等）がある場合、システムは起動時・設定読込時・
      設定インポート時にこれを検知し、ConfigValidationExceptionをfail fastで送出しなければ
      ならない。
    category: validation
    applies_to: [TableConfig, ColumnConfig]
    trigger: "アプリ起動時の設定読込、または importConfigSet 実行時"
    logic: "IF 必須プロパティ（BR1.2, BR1.3参照）のいずれかが欠落 THEN ConfigValidationExceptionを送出し、処理を中断する"
    violation_behaviour: "起動時: アプリケーション起動を中断する。importConfigSet時: インポート全体を中断し、変更を内部設定DBへ反映しない（フィールド単位のエラー情報を返す、BR1.11参照）"
    source: FR1.3

  - id: BR1.2
    statement: TableConfigはschemaName・tableNameを必須プロパティとして持たなければならない。
    category: validation
    applies_to: [TableConfig]
    trigger: "TableConfigの作成・更新（writeTableConfigDraft, importConfigSet, 管理画面からの編集）"
    logic: "IF schemaNameまたはtableNameが未設定 THEN BR1.1のfail-fast検証エラーとする"
    violation_behaviour: "ConfigValidationExceptionを送出する"
    source: FR1.3

  - id: BR1.3
    statement: >
      ColumnConfigはtableConfigId・columnName・editorTypeを必須プロパティとして持たなければ
      ならない。
    category: validation
    applies_to: [ColumnConfig]
    trigger: "ColumnConfigの作成・更新"
    logic: "IF tableConfigId、columnName、editorTypeのいずれかが未設定 THEN BR1.1のfail-fast検証エラーとする"
    violation_behaviour: "ConfigValidationExceptionを送出する"
    source: FR1.3

  - id: BR1.4
    statement: >
      editorTypeがselect/radioのColumnConfigは、choiceOptions（静的選択肢）とfkReference
      （FK参照）のいずれか一方を必ず設定しなければならず、両方の設定・両方の未設定は許容
      されない。
    category: validation
    applies_to: [ColumnConfig]
    trigger: "editorTypeがselect/radioのColumnConfigの作成・更新"
    logic: "IF editorType IN (select, radio) AND (choiceOptionsとfkReferenceの両方が設定 OR 両方とも未設定) THEN BR1.1のfail-fast検証エラーとする"
    violation_behaviour: "ConfigValidationExceptionを送出する"
    source: FR1.5

  - id: BR1.5
    statement: >
      TableConfig・ColumnConfigの表示名i18nキーは、schemaName・tableName・columnNameから
      機械的に導出する。表示名テキストそのものはTableConfig/ColumnConfigに保持しない。
    category: policy
    applies_to: [TableConfig, ColumnConfig]
    trigger: "表示名の解決（一覧・詳細編集画面のレンダリング時）"
    logic: >
      IF 対象がTableConfig THEN i18nキー = "table.{schemaName}.{tableName}.label"
      IF 対象がColumnConfig THEN i18nキー = "table.{schemaName}.{tableName}.{columnName}.label"
    violation_behaviour: N/A（導出ルールであり違反ケースは存在しない）
    source: FR10.1

  - id: BR1.6
    statement: >
      ColumnConfigのバリデーションルール（validationRule）に定義された各ルールのエラー
      メッセージi18nキーは、schemaName・tableName・columnName・ルール種別から機械的に
      導出する。
    category: policy
    applies_to: [ColumnConfig]
    trigger: "バリデーションエラーメッセージの解決（フォーカスアウト時のインラインバリデーション、FR6.2）"
    logic: 'i18nキー = "table.{schemaName}.{tableName}.{columnName}.validation.{ruleType}"（例: ruleType=required, minLength等）'
    violation_behaviour: N/A（導出ルールであり違反ケースは存在しない）
    source: FR6.2

  - id: BR1.7
    statement: >
      TableConfigの楽観ロック対象列（optimisticLockColumn）は管理者が明示的に設定した値の
      みを用い、RDBMS方言に基づく自動検出は行わない。未設定（null）の場合は楽観ロック非対象
      として扱う。
    category: policy
    applies_to: [TableConfig]
    trigger: "record-edit-engineからのgetOptimisticLockColumn呼び出し（保存処理時）"
    logic: "RETURN TableConfig.optimisticLockColumn（明示設定値、方言別の自動推定ロジックは適用しない）"
    violation_behaviour: N/A
    source: "Q2確定（FR6.3の前提条件）"

  - id: BR1.8
    statement: >
      schema-introspectorからのwriteTableConfigDraft呼び出しは、対象のschemaName/tableName
      に対応するTableConfigが既に存在する場合、fail fastではなく当該テーブルの取り込みを
      スキップし、既存設定を上書きしない。
    category: policy
    applies_to: [TableConfig, ColumnConfig]
    trigger: "writeTableConfigDraft呼び出し"
    logic: "IF (schemaName, tableName)に対応するTableConfigが既に存在 THEN 当該テーブルの取り込みをスキップする（エラーとしない） ELSE 新規TableConfig/ColumnConfigを作成する"
    violation_behaviour: N/A（スキップは正常系の挙動）
    source: "C9契約（writeTableConfigDraftのnote）"

  - id: BR1.9
    statement: >
      ColumnConfigのvalidationRuleは、ルール種別（required/minLength/maxLength/min/max/
      pattern等）をキーとする構造化データとし、各ルールのエラーメッセージはi18nキー
      （BR1.6の導出規則）で指定する。固定文字列の直接保持は行わない。
    category: constraint
    applies_to: [ColumnConfig]
    trigger: "ColumnConfigの作成・更新、およびバリデーション実行時"
    logic: "validationRuleオブジェクトの各キーはルール種別に対応し、そのルールに違反した場合はBR1.6のi18nキーで解決されるメッセージを返す"
    violation_behaviour: "利用者向けにはフィールド単位のエラーメッセージ（開発者向けスタックトレースは返さない）"
    source: FR1.1

  - id: BR1.10
    statement: >
      業務設定層のi18nキー（BR1.5・BR1.6で導出されるキー、および静的選択肢のi18nキー）に
      対する言語別テキストは、TranslationEntryとして内部設定DBに保持し、管理画面から登録・
      編集可能とする。基盤（エンジン）層の固定UI文言はこの対象に含まない。
    category: policy
    applies_to: [TranslationEntry]
    trigger: "TranslationEntryの登録・編集、および表示名・バリデーションメッセージの解決時"
    logic: "IF i18nKeyがTableConfig/ColumnConfig由来（BR1.5・BR1.6・choiceOptions） THEN TranslationEntryを解決対象とする。基盤層固定UI文言のi18nキーはビルド成果物の翻訳リソースを解決対象とし、TranslationEntryの対象外とする"
    violation_behaviour: "対応するTranslationEntryが存在しない場合、フロントエンド側で未翻訳表示にフォールバックする（本ユニットの責務範囲外）"
    source: "Q4 Follow-up確定（FR10.1/FR10.2の適用範囲整理）"

  - id: BR1.11
    statement: >
      利用者（業務担当者）向けの設定バリデーションエラー（インポート時のfail-fast検証を
      含む）は、開発者向けのスタックトレースではなく、フィールド単位のエラーメッセージ
      として返さなければならない。
    category: policy
    applies_to: [TableConfig, ColumnConfig]
    trigger: "importConfigSet実行時のfail-fast検証エラー"
    logic: "ConfigValidationExceptionは、エラーの生じたフィールド（schemaName/tableName/columnConfigId等）とルール種別を含む構造化エラー情報を保持する"
    violation_behaviour: "呼び出し元（config-import-export、C7契約）はこのエラー情報をRFC 9457のerrors配列（field, message）へマッピングして返す"
    source: "project.md Mandated（設定定義自体の誤りはfail fast検知、利用者向けはフィールド単位メッセージ）"

  - id: BR1.12
    statement: >
      ConfigEngineは、アプリ起動時に読み込む設定について、PostgreSQL/MySQL/MariaDBの
      複数RDBMS方言の差異を吸収した内部モデルへ変換しなければならない。吸収対象は
      (a) schema-introspectorが読み取ったDBメタデータの型名をConfigEngine内部の論理型
      へ正規化する変換、および (b) 物理層のSQL生成方言（クォーティング・LIMIT/OFFSET
      構文等）をlist-engine/record-edit-engineへ提供することの2点とする。楽観ロック
      対象列の自動検出ルールの方言別吸収は対象外とする（BR1.7参照）。
    category: policy
    applies_to: [TableConfig, ColumnConfig]
    trigger: "アプリ起動時の設定読込（W1）、およびschema-introspectorからのドラフト取り込み（W2）"
    logic: >
      IF 対象RDBMSがPostgreSQL/MySQL/MariaDBのいずれか THEN
      (a) メタデータ型名をConfigEngine内部論理型（editorType初期値推定に用いる共通型）
      へ正規化し、(b) 物理層SQL生成方言情報をlist-engine/record-edit-engineへの
      問い合わせ応答に含める
    violation_behaviour: N/A（変換ロジックであり違反ケースは想定しない。未対応の方言が
      検出された場合はConfigValidationExceptionとして扱う）
    source: FR1.2
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | validation | 設定定義の必須プロパティ欠落をfail-fastで検知する |
| BR1.2 | validation | TableConfigの必須プロパティ（schemaName, tableName） |
| BR1.3 | validation | ColumnConfigの必須プロパティ（tableConfigId, columnName, editorType） |
| BR1.4 | validation | select/radioはchoiceOptions/fkReferenceのいずれか一方が必須 |
| BR1.5 | policy | TableConfig/ColumnConfigの表示名i18nキー導出規則 |
| BR1.6 | policy | バリデーションメッセージi18nキー導出規則 |
| BR1.7 | policy | 楽観ロック対象列は明示設定のみ、方言別自動検出なし |
| BR1.8 | policy | schema-introspectorのドラフト書込みは既存設定を上書きしない |
| BR1.9 | constraint | validationRuleの構造化データ形式 |
| BR1.10 | policy | 業務設定層i18nはTranslationEntryで管理、基盤層は対象外 |
| BR1.11 | policy | 設定バリデーションエラーはフィールド単位で返す |
| BR1.12 | policy | 複数RDBMS方言（型名正規化・物理層SQL方言）の吸収 |
