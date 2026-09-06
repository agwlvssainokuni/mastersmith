# Entities: schema-ingestion

schema-ingestionは永続状態を持たないステートレスな走査ロジックである(domain-design/components.md参照)。以下のエンティティは、本Unitが処理・返却するデータの形状(DTO)を表し、永続化テーブルではない。

## エンティティモデル(機械可読)

```yaml
entities:
  - name: SchemaTarget
    description: "1つのDbConnectionで実際に取り込み対象として選択可能な、スキーマ(PostgreSQL)またはデータベース(MySQL/MariaDB)。functional-design-questions.md Q1により新設"
    attributes:
      - name: name
        type: string
        required: true
        constraints: ["PostgreSQLの場合はDatabaseMetaData.getSchemas()が返すスキーマ名、MySQL/MariaDBの場合はDatabaseMetaData.getCatalogs()が返すデータベース名"]
    entity_constraints: []
    relationships: []

  - name: IngestedTable
    description: 1つの取り込み対象テーブルまたはビューの正規化されたメタデータ
    attributes:
      - name: physicalTableName
        type: string
        required: true
      - name: isView
        type: boolean
        required: true
        constraints: ["trueの場合、hasPrimaryKeyは常にfalseとして扱う(BR1.3)。ビューは常に参照専用"]
      - name: hasPrimaryKey
        type: boolean
        required: true
      - name: primaryKeyColumns
        type: array<string>
        required: true
        constraints: ["hasPrimaryKey=falseの場合は空配列。単一主キーの場合は要素数1。複合主キーの場合、JDBC DatabaseMetaData.getPrimaryKeys()のKEY_SEQ列の昇順で並べる(BR1.1、RDBMS間で自然順序が異なる問題を回避する唯一の移植可能な方法)"]
      - name: columns
        type: array<IngestedColumn>
        required: true
    entity_constraints:
      - "ストアドプロシージャは対象外(BR1.4により、そもそも走査結果に含まれない)"
    relationships:
      - target: IngestedColumn
        cardinality: "1:N"
        direction: "IngestedTable → IngestedColumn"
      - target: IngestedForeignKey
        cardinality: "1:N"
        direction: "IngestedTable → IngestedForeignKey"

  - name: IngestedColumn
    description: 1カラムの正規化された型情報
    attributes:
      - name: name
        type: string
        required: true
      - name: logicalType
        type: string
        required: true
        allowed_values: ["string", "text", "integer", "decimal", "boolean", "date", "datetime", "binary", "other"]
        constraints: ["JDBC標準の型コード(java.sql.Types、DatabaseMetaData.getColumns()のDATA_TYPE列)から機械的に導出する。RDBMS固有の型名文字列(TYPE_NAME列、例: PostgreSQLの\"character varying\"、MySQLの\"varchar\")は用いない(BR2.1)。java.sql.Typesの値は3種のJDBCドライバ間で一貫しているため、これを正規化の基点とする"]
      - name: nullable
        type: boolean
        required: true
    entity_constraints: []
    relationships: []

  - name: IngestedForeignKey
    description: "1つの外部キー制約(単一カラムまたは複合カラム)。functional-design-questions.md Q2により配列形式に変更"
    attributes:
      - name: columns
        type: array<string>
        required: true
        constraints: ["単一カラムのFKは要素数1の配列。複合FKは、JDBC DatabaseMetaData.getImportedKeys()のKEY_SEQ列の昇順で並べる(BR3.1)"]
      - name: referencedTable
        type: string
        required: true
      - name: referencedColumns
        type: array<string>
        required: true
        constraints: ["columnsと同じ順序・同じ要素数で対応する(columns[i]がreferencedColumns[i]を参照する)"]
    entity_constraints:
      - "columnsとreferencedColumnsの要素数は必ず一致する"
    relationships: []
```

## エンティティサマリー

| Entity | 説明 | 主な属性 |
|---|---|---|
| SchemaTarget | 取り込み対象として選択可能なスキーマ/データベース(本ステージで新設) | name |
| IngestedTable | 取り込み対象テーブル/ビューの正規化メタデータ | physicalTableName, isView, hasPrimaryKey, primaryKeyColumns(KEY_SEQ順) |
| IngestedColumn | カラムの正規化型情報 | name, logicalType(java.sql.Types由来), nullable |
| IngestedForeignKey | 外部キー(単一・複合両対応) | columns[], referencedTable, referencedColumns[] |

いずれのエンティティも永続化されない(schema-ingestion自身はステートレス)。呼び出し元(config-management、契約#1)およびfrontend-admin向けREST(契約#14)へ、これらの形状のデータをそのまま返却する。
