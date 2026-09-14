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

# Entities — schema-introspector (U2)

`functional-design-questions.md`の確定回答に基づき、schema-introspectorユニットが扱うデータ形状を定義する。本ユニットは対象RDBMS(業務データ用)のメタデータを読み取り、config-engine(U1)へ設定の初期ドラフトを書き込む一過性の読み取り処理であり、自身が永続化するエンティティを持たない。ここで定義するのは、C8(REST API)のリクエスト/レスポンス形状と、メタデータ読み取り時に内部で組み立てる一時的な値オブジェクトのみである。ドラフトの実体(`TableConfigDraft`/`TableDraftEntry`/`ColumnDraftEntry`)はC9契約でconfig-engineが型定義を所有するため、ここでは再定義せず参照する。

```yaml
entities:
  - name: SchemaIntrospectionRequest
    description: >
      C8(`POST /api/config/schema-introspection`)のリクエストボディ。対象スキーマと
      対象テーブル(省略可)を指定する(BR2.2)。
    attributes:
      - name: schemaName
        type: string
        required: true
        description: メタデータ読み取り対象のスキーマ名
      - name: tableNames
        type: array
        required: false
        item_shape: string
        description: >
          読み取り対象のテーブル名一覧。省略時はschemaName配下の全テーブルを対象とする
          (BR2.2)
    entity_constraints:
      - "永続化しない。呼び出しごとの一時的なリクエストパラメータ"
    relationships:
      - target: RdbmsTableMetadata
        cardinality: "0..*"
        direction: "SchemaIntrospectionRequest 1 -> 0..* RdbmsTableMetadata"
        description: 1回のリクエストは、読み取り範囲(BR2.2)に応じた0件以上のRdbmsTableMetadataを読み取り対象とする

  - name: RdbmsTableMetadata
    description: >
      schema-introspectorが対象RDBMSのメタデータカタログ(information_schema等、方言別の
      実装詳細はcode-generationで確定)から読み取った1テーブル分の構造情報。読み取り結果を
      config-engineのC9契約型`TableDraftEntry`へマッピングする直前の内部読み取りモデル
      (BR2.1、BR2.3)。
    attributes:
      - name: schemaName
        type: string
        required: true
      - name: tableName
        type: string
        required: true
      - name: columns
        type: array
        required: true
        item_shape: RdbmsColumnMetadata
        description: 対象テーブルの全カラムのメタデータ
    entity_constraints:
      - "永続化しない。1回のintrospection呼び出し内でのみ保持する中間データ"
      - "業務データ用RDBMSへの読み取り専用アクセスの結果であり、書き込み(DDL/DML)は一切行わない(BR2.1)"
    relationships:
      - target: RdbmsColumnMetadata
        cardinality: "1..*"
        direction: "RdbmsTableMetadata 1 -> 1..* RdbmsColumnMetadata"
        description: 1つのRdbmsTableMetadataは1個以上のRdbmsColumnMetadataを持つ
      - target: SchemaIntrospectionResult
        cardinality: "0..1"
        direction: "RdbmsTableMetadata 1 -> 0..1 SchemaIntrospectionResult"
        description: >
          writeTableConfigDraft呼び出し後、既存TableConfigが存在せず新規生成された場合は
          生成されたTableConfigIdとして対応する(BR2.7)。既存設定がありスキップされた場合は
          対応する結果を持たない(0件)

  - name: RdbmsColumnMetadata
    description: >
      対象RDBMSのメタデータカタログから読み取った1カラム分の情報。config-engineのC9契約型
      `ColumnDraftEntry`へそのままマッピングされる(BR2.4、BR2.5)。
    attributes:
      - name: columnName
        type: string
        required: true
      - name: rawTypeName
        type: string
        required: true
        description: >
          対象RDBMSのメタデータ型名(例: "varchar(255)", "int unsigned")。ここでは正規化
          せず生の型名のまま保持し、editorType/formatの決定はconfig-engine側の論理型正規化
          ロジック(`construction/config-engine/functional-design/rules.md` BR1.12)に委ねる
          (BR2.5、Q1確定)
      - name: isPrimaryKey
        type: boolean
        required: true
        description: 対象RDBMSの主キー制約から判定した結果(BR2.4)
      - name: nullable
        type: boolean
        required: true
        description: >
          対象RDBMSのNOT NULL制約から判定したNULL可否(FR1.4「テーブル/カラム/型/NULL可否/
          主キー/外部キー等」の読み取り対象に対応)。schema-introspectorはこの値を読み取り、
          RdbmsColumnMetadataとして保持するが、config-engineのC9契約型`ColumnDraftEntry`には
          対応するフィールドが存在しないため、ドラフト生成時にはconfig-engineへ伝搬しない
          (BR2.10、Follow-up確定)
    entity_constraints:
      - "永続化しない。1回のintrospection呼び出し内でのみ保持する中間データ"
      - "楽観ロック対象列(optimisticLockColumn)に相当する情報は保持しない(BR2.6、Q2確定)"
      - "nullableは読み取るが、ColumnDraftEntryへは伝搬しない(BR2.10、Follow-up確定)"
    relationships: []

  - name: SchemaIntrospectionResult
    description: >
      C8のレスポンスボディ。config-engineの`writeTableConfigDraft`呼び出し結果
      (生成された`TableConfig`のID一覧)をそのまま返す。
    attributes:
      - name: generatedTableConfigIds
        type: array
        required: true
        item_shape: string
        description: >
          今回の呼び出しで新規生成されたTableConfigのID一覧。既存設定があり取り込みを
          スキップされたテーブルのIDは含まれない(BR2.7、config-engine側のBR1.8による)
    entity_constraints:
      - "永続化しない。呼び出しごとのレスポンス値"
    relationships: []
```

## 人間可読サマリー

schema-introspectorユニットは永続エンティティを一切持たない。対象RDBMSから読み取ったメタデータを一時的に保持し、config-engine(U1)のC9契約型(`TableConfigDraft`/`TableDraftEntry`/`ColumnDraftEntry`)へマッピングして`writeTableConfigDraft`を呼び出すだけの、一過性の変換・委譲処理である。

- **SchemaIntrospectionRequest**: C8 REST APIのリクエスト(対象スキーマ・対象テーブル)。
- **RdbmsTableMetadata / RdbmsColumnMetadata**: 対象RDBMSのメタデータカタログから読み取った、テーブル・カラムの内部読み取りモデル。生の型名(`rawTypeName`)をそのまま保持し、editorType/formatの決定ロジックそのものは持たない(config-engine側のBR1.12/`LogicalType.defaultEditorType()`に委譲、Q1確定)。楽観ロック対象列に相当する情報も保持しない(Q2確定)。NULL可否(`nullable`)はFR1.4の読み取り対象として保持するが、config-engine側の`ColumnDraftEntry`に対応するフィールドが無いため、ドラフト生成時には伝搬しない(BR2.10、Follow-up確定)。
- **SchemaIntrospectionResult**: C8のレスポンス(生成されたTableConfigのID一覧)。

TableConfig/ColumnConfig自体の永続化・fail-fast検証・楽観ロック対象列の管理はconfig-engine(U1)の責務であり、本ユニットのエンティティとしては再定義しない。
