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

# Entities — config-engine (U1)

`functional-design-questions.md`の確定回答に基づき、config-engineユニットが保持するエンティティを定義する。`inception/domain-design/components.md`のTableConfig/ColumnConfig（属性の大枠）を、機能設計として具体化・修正したものである（displayNameフィールドは廃止し、i18nキーの機械的導出に置き換えた。Q5 Follow-up参照）。

```yaml
entities:
  - name: TableConfig
    description: >
      対象RDBMS上の1テーブルに対応する表示設定。スキーマ・テーブル単位の表示順・楽観ロック
      対象列の有無を保持する。表示名はi18nキー（table.{schemaName}.{tableName}.label）として
      schemaName/tableNameから機械的に導出し、テキストそのものは保持しない（TranslationEntry
      が言語別テキストを保持する）。
    attributes:
      - name: tableConfigId
        type: string
        required: true
        unique: true
        description: ConfigEngineが発行する内部ID（主キー）
      - name: schemaName
        type: string
        required: true
        constraints: [必須プロパティ（fail-fast検証対象、BR1.2）]
      - name: tableName
        type: string
        required: true
        constraints: [必須プロパティ（fail-fast検証対象、BR1.2）, "(schemaName, tableName)の組でユニーク"]
      - name: displayOrder
        type: integer
        required: false
        defaults: "0（未設定時。同順位はtableName昇順）"
      - name: optimisticLockColumn
        type: string
        required: false
        allowed_values: "対象テーブルの実在カラム名、またはnull"
        constraints: ["nullの場合は楽観ロック非対象（後勝ち、FR6.3）。RDBMS方言による自動検出は行わず、明示設定のみを用いる（Q2確定）"]
    entity_constraints:
      - "(schemaName, tableName)の組み合わせで一意"
      - "表示名テキストは保持しない。表示名i18nキーは `table.{schemaName}.{tableName}.label` として実行時に導出する（Q5 Follow-up、Q6）"
    relationships:
      - target: ColumnConfig
        cardinality: "1..*"
        direction: "TableConfig 1 -> * ColumnConfig"
        description: 1つのTableConfigは1個以上のColumnConfigを持つ

  - name: ColumnConfig
    description: >
      TableConfigに属する1カラムの表示設定（表示順・書式・編集部品・バリデーション・表示可否・
      静的選択肢）。表示名・バリデーションメッセージはi18nキーとして機械的に導出し（Q5
      Follow-up・Q6）、テキストそのものは保持しない。
    attributes:
      - name: columnConfigId
        type: string
        required: true
        unique: true
        description: ConfigEngineが発行する内部ID（主キー）
      - name: tableConfigId
        type: string
        required: true
        references: TableConfig
        constraints: [必須プロパティ（fail-fast検証対象、BR1.3）]
      - name: columnName
        type: string
        required: true
        constraints: [必須プロパティ（fail-fast検証対象、BR1.3）, "(tableConfigId, columnName)の組でユニーク"]
      - name: displayOrder
        type: integer
        required: false
        defaults: "0（未設定時。同順位はcolumnName昇順）"
      - name: format
        type: string
        required: false
        description: 表示・入力時の書式パターン（数値の桁区切り、日付書式等）。未設定時は編集部品の既定書式を用いる
      - name: editorType
        type: string
        required: true
        allowed_values: [text, textarea, integer, decimal, date, datetime, select, radio, switch, checkbox]
        constraints: [必須プロパティ（fail-fast検証対象、BR1.3）]
      - name: validationRule
        type: object
        required: false
        description: >
          構造化されたバリデーションルール（Q3確定）。ルール種別（required/minLength/
          maxLength/min/max/pattern等）ごとにキーを持ち、各ルールのエラーメッセージは
          i18nキー（table.{schemaName}.{tableName}.{columnName}.validation.{ruleType}、
          Q6）で指定する。詳細構造は`rules.md`のBR1.9参照
      - name: visibility
        type: string
        allowed_values: [visible, hidden]
        required: false
        defaults: "visible（未設定時）"
        description: 権限とは独立した表示可否軸（FR4.5）。READ権限未満の場合の非表示制御はPermissionEngine側の判定と組み合わされる
      - name: choiceOptions
        type: array
        required: false
        description: >
          editorTypeがselect/radioで、かつFK参照でない場合の静的選択肢定義（Q4確定）。
          各要素は{value, i18nKey}のペア。fkReferenceと同時設定不可（BR1.4）
        item_shape: { value: string, i18nKey: string }
      - name: fkReference
        type: object
        required: false
        description: >
          editorTypeがselect/radioで、FK参照による動的名称解決を行う場合の参照先定義
          （FR1.5）。名称解決自体はConfigEngineではなく、業務データRDBMSへアクセス可能な
          list-engine/record-edit-engineが実行時に行う。choiceOptionsと同時設定不可（BR1.4）
        item_shape:
          referencedSchemaName: string
          referencedTableName: string
          referencedValueColumnName: string
          referencedLabelColumnName: string
    entity_constraints:
      - "(tableConfigId, columnName)の組み合わせで一意"
      - "editorTypeがselect/radioの場合、choiceOptionsとfkReferenceのいずれか一方を必ず設定する（両方設定・両方未設定は不可、BR1.4）"
      - "表示名・バリデーションメッセージのテキストは保持しない。i18nキーは schemaName/tableName/columnName から導出する（Q5 Follow-up・Q6）"
    relationships:
      - target: TableConfig
        cardinality: "*..1"
        direction: "ColumnConfig * -> 1 TableConfig"
        description: 各ColumnConfigは1個のTableConfigに属する

  - name: TranslationEntry
    description: >
      業務設定層のi18nキーに対する言語別テキスト（Q4 Follow-up確定）。TableConfig/
      ColumnConfigの表示名・バリデーションメッセージ・静的選択肢displayNameのi18nキーに
      対応する言語別テキストを、管理画面から登録・編集可能な実行時データとして保持する。
      基盤（エンジン）層の固定UI文言（共通ボタン・汎用エラーメッセージ等）は対象外とし、
      引き続きビルド成果物の翻訳リソースファイルで管理する（FR10.2の適用範囲はこちらに限定）。
    attributes:
      - name: i18nKey
        type: string
        required: true
        description: "Q6の命名規則に従うi18nキー（例: table.public.products.label）"
      - name: locale
        type: string
        required: true
        allowed_values: [ja, en]
      - name: text
        type: string
        required: true
        description: 当該i18nKey・localeの組に対応する表示テキスト
    entity_constraints:
      - "(i18nKey, locale)の組み合わせで一意（複合主キー）"
      - "対応するTranslationEntryが未登録のi18nKeyは、フロントエンド側で未翻訳表示（例: キー文字列そのものの表示）にフォールバックする（本ユニットはフォールバック値そのものは保持しない）"
    relationships: []
```

## 人間可読サマリー

config-engineユニットは3つのエンティティを保持する。

- **TableConfig**: テーブル単位の表示設定（表示順・楽観ロック対象列の有無）。表示名テキストは持たず、i18nキーを`schemaName`/`tableName`から機械的に導出する。
- **ColumnConfig**: カラム単位の表示設定（表示順・書式・編集部品種別・バリデーションルール・表示可否・静的選択肢またはFK参照）。TableConfigと同様、表示名・バリデーションメッセージのテキストは持たずi18nキーを導出する。`editorType`がselect/radioの場合は、静的選択肢（`choiceOptions`）とFK参照（`fkReference`）のいずれか一方を必ず設定する。
- **TranslationEntry**: 業務設定層のi18nキーに対する言語別（日本語/英語）テキスト。管理画面から登録・編集できる実行時データであり、Q4 Follow-upで新設が確定した。基盤層の固定UI文言（ビルド成果物の翻訳リソース）とは明確に区別される。

権限（Role/PrimaryPermission/AuxiliaryPermission）はPermissionEngineが別途保持し、`scopeRef`として`schemaName.tableName`等の文字列キーでTableConfig/ColumnConfigと間接的に対応付く（Q1確定）。ConfigEngine側のエンティティに権限フィールドは持たない。
