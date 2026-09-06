# Domain Entities: config-management

domain-design/components.mdのConfigManagementComponentが持つエンティティ(DbConnection、MenuItem、TableConfig)を
出発点とし、本Unit(config-management)の責務([FR2.1〜FR2.6、FR2.3.1、FR3.4]、契約#1・#2・#15)に
必要な属性の具体的な形状を確定する。

## エンティティ一覧(機械可読)

```yaml
entities:
  - name: DbConnection
    identifier: connectionId
    attributes:
      - name: string
      - jdbcUrl: string
      - driverType: enum(postgresql, mysql, mariadb)
      - credentialRef: string # 暗号化された認証情報の実体。鍵はapplication.yml [schema-ingestion/functional-design-questions.md Q4、domain-design/components.md DbConnection行, project.md Forbidden]
    ownership: config-management(U2)が唯一の所有者。schema-ingestion(U1)へは契約#1呼び出し時にconnectionId経由で参照を渡すのみで、schema-ingestion自身はDbConnectionを永続化しない。

  - name: TableConfig
    identifier: tableId
    attributes:
      - connectionId: identifier # どのDbConnectionから取り込んだか
      - schemaTarget: string # 取り込み元のスキーマ/データベース名(schema-ingestion SchemaTarget.name) [functional-design-questions.md Q1]
      - physicalTableName: string
      - isView: boolean
      - primaryKeyColumns: array<string> # KEY_SEQ順(schema-ingestion由来)
      - tableDisplayName: string # テーブルの論理表示名 [FR2.1(9)]
      - columns: array<TableColumnConfig> # カラムごとの設定(下記参照) [FR2.1(4)〜(9)]
      - foreignKeys: array<object{ columns: array<string>, referencedTableId: identifier, nullable, referencedTablePhysicalName: string, referencedColumns: array<string> }> # 複合FK対応。referencedTableIdは同一connectionId・schemaTarget内で参照先のphysicalTableNameと一致するTableConfigが存在すれば解決済みの値を持ち、まだインポートされていなければnullのまま保持する(referencedTablePhysicalNameは常に非null。schema-ingestionのIngestedForeignKey.referencedTableをそのまま保持し、後続インポート時の解決に使う) [FR4.1, functional-design-questions.md Q1(R-01フォロー、Construction / config-management Unit Functional Designレビューより)]
      - foreignKeyRepresentativeColumns: array<object{ referencedTableId: identifier, representativeColumnName: string }> # FK名称解決・ポップアップ検索で使う代表表示列 [FR4.1, FR4.2]
    ownership: config-management(U2)が唯一の所有者。dynamic-data-access(U4)は契約#2経由でキャッシュ済みの有効な設定を参照するのみで、TableConfigテーブルへ直接アクセスしない。
    nested_types:
      - name: TableColumnConfig
        attributes:
          - columnName: string
          - displayName: string # カラムの論理表示名 [FR2.1(9)]
          - isReadOnly: boolean # 編集対象外項目 [FR2.1(6)]
          - isSearchable: boolean # 検索条件項目として使うか [FR2.1(4)]
          - searchOperator: enum(equals, contains, range, in), nullable # isSearchable=trueの場合のみ有効
          - searchDefaultValue: string, nullable
          - listOrder: integer, nullable # 一覧表示項目としての表示順。nullなら一覧に表示しない [FR2.1(5)]
          - listSortable: boolean
          - formWidget: enum(text, textarea, checkbox, switch, radio, select) # [FR2.1(8)]
          - required: boolean # バリデーション: 必須 [FR2.1(7)]
          - maxLength: integer, nullable
          - minValue: number, nullable
          - maxValue: number, nullable
          - pattern: string, nullable # 正規表現
          - unique: boolean

  - name: MenuItem
    identifier: menuItemId
    attributes:
      - label: string
      - parentMenuItemId: identifier, nullable # 階層構造。nullならルート直下 [FR2.4]
      - tableId: identifier, nullable # フォルダ/グループノードはnull。テーブルノードはTableConfigを指す
      - displayOrder: integer
    ownership: config-management(U2)が唯一の所有者。

note: >
  「設定全体」の永続化はconfig-managementが内部H2データストア(H2)にのみ保持し、業務DBには
  一切保存しない [FR2.5]。設定変更の反映は明示的なキャッシュクリア、またはapplication.yml
  設定によるキャッシュTTL経由でのみ行い、リクエストのたびにDBへ問い合わせる実装は行わない
  [FR2.6, functional-design-questions.md Q2]。キャッシュ自体は永続エンティティではなく
  Caffeine(com.github.ben-manes.caffeine:caffeine)によるインメモリキャッシュとする。
```

## エンティティサマリー

| エンティティ | 所有Unit | 識別子 | 主な属性 | 備考 |
|---|---|---|---|---|
| DbConnection | config-management | connectionId | name, jdbcUrl, driverType, credentialRef | 認証情報は暗号化して保持(契約#19注記) |
| TableConfig | config-management | tableId | connectionId, schemaTarget, physicalTableName, columns[], foreignKeys[] | columns[]がFR2.1(4)〜(9)の全カラム単位設定を保持 |
| MenuItem | config-management | menuItemId | label, parentMenuItemId, tableId, displayOrder | 階層構造(フォルダ/グループ) |
