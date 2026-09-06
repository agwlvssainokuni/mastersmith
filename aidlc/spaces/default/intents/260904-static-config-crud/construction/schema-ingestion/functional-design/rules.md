# Business Rules: schema-ingestion

team.mdのTesting Posture(スキーマ読み込み層は実装に先立って特性テストとして期待挙動を洗い出す前倒し運用)の対象そのものが、以下のBR1.1〜BR2.1である。Code Generation以前に、これらのルールに対応する特性テストをRDBMSごと(PostgreSQL/MySQL/MariaDB)に用意すること。

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: 複合主キーのカラム順序は、JDBC DatabaseMetaData.getPrimaryKeys()が返すKEY_SEQ列の昇順で決定する
    category: constraint
    applies_to: IngestedTable
    trigger: "テーブルが複合主キー(2カラム以上)を持つとき"
    logic: "primaryKeyColumns = getPrimaryKeys()の結果をKEY_SEQ昇順でソートしたCOLUMN_NAMEのリスト"
    violation_behaviour: "該当なし(JDBC標準機構によりRDBMS間で一貫した結果が得られる)"
    source: FR1.2, team.md Testing Posture(複合主キーの構成)

  - id: BR1.2
    statement: 主キーを持たないテーブルは取り込み対象に含めるが、hasPrimaryKey=false・primaryKeyColumns=空配列として報告する
    category: constraint
    applies_to: IngestedTable
    trigger: "getPrimaryKeys()が1件も返さないテーブルを走査したとき"
    logic: "hasPrimaryKey=false、primaryKeyColumns=[]として報告する。テーブル自体は走査結果から除外しない"
    violation_behaviour: "該当なし"
    source: FR1.3

  - id: BR1.3
    statement: ビューは常にisView=true・hasPrimaryKey=falseとして報告する。ビューに対してgetPrimaryKeys()が何らかの結果を返した場合でも無視する
    category: constraint
    applies_to: IngestedTable
    trigger: "getTables()の結果でTABLE_TYPE=\"VIEW\"のオブジェクトを走査したとき"
    logic: "IF TABLE_TYPE=VIEW THEN isView=true, hasPrimaryKey=false, primaryKeyColumns=[]を強制する(getPrimaryKeys()の実際の返り値に関わらず)"
    violation_behaviour: "該当なし"
    source: FR1.4

  - id: BR1.4
    statement: ストアドプロシージャは走査対象外とする
    category: constraint
    applies_to: IngestedTable
    trigger: "スキーマ走査を実行するとき"
    logic: "getTables()の呼び出し時、types引数に{\"TABLE\", \"VIEW\"}のみを指定し、プロシージャ関連のメタデータAPI(getProcedures()等)は一切呼び出さない"
    violation_behaviour: "該当なし"
    source: FR1.5

  - id: BR2.1
    statement: カラムの論理型は、JDBC標準の型コード(java.sql.Types、getColumns()のDATA_TYPE列)から導出する。RDBMS固有の型名文字列(TYPE_NAME列)は使用しない
    category: constraint
    applies_to: IngestedColumn
    trigger: "カラムのメタデータを走査するとき"
    logic: "DATA_TYPE列の値(java.sql.Types定数)を、string/text/integer/decimal/boolean/date/datetime/binary/otherのいずれかへ機械的にマッピングする(例: VARCHAR/CHAR→string、INTEGER/BIGINT/SMALLINT→integer、DECIMAL/NUMERIC→decimal、BOOLEAN/BIT→boolean、DATE→date、TIMESTAMP→datetime)"
    violation_behaviour: "マッピング表にない型コードはotherとして報告する(取り込み自体は妨げない)"
    source: FR1.1, team.md Testing Posture(RDBMS間の型差異)

  - id: BR3.1
    statement: 外部キーは、JDBC DatabaseMetaData.getImportedKeys()の結果をFK_NAMEでグルーピングし、KEY_SEQ昇順でカラムを並べた配列として報告する(単一カラムFKも要素数1の配列として表現する)
    category: constraint
    applies_to: IngestedForeignKey
    trigger: "外部キー制約を走査するとき"
    logic: "同一FK_NAMEを持つ行をグルーピングし、KEY_SEQ昇順でFKCOLUMN_NAME(columns)とPKCOLUMN_NAME(referencedColumns)を対応させて配列化する"
    violation_behaviour: "該当なし"
    source: FR1.1, functional-design-questions.md Q2

  - id: BR4.1
    statement: DbConnectionに対して選択可能なスキーマ/データベース(SchemaTarget)一覧は、PostgreSQL系ドライバではDatabaseMetaData.getSchemas()、MySQL/MariaDB系ドライバではDatabaseMetaData.getCatalogs()から取得する
    category: constraint
    applies_to: SchemaTarget
    trigger: "管理者がDB接続先を選択し、取り込み対象のスキーマ/データベース一覧を要求したとき"
    logic: "IF driverType=postgresql THEN getSchemas()の結果をSchemaTarget一覧として返す。IF driverType=mysql または mariadb THEN getCatalogs()の結果をSchemaTarget一覧として返す"
    violation_behaviour: "該当なし"
    source: functional-design-questions.md Q1

  - id: BR5.1
    statement: schema-ingestionの全操作(スキーマ/データベース一覧取得・プレビュー実行)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる
    category: authorization
    applies_to: SchemaTarget, IngestedTable
    trigger: "schema-ingestionへのいずれかの操作要求を受けたとき"
    logic: "IF アクセストークンのisAdminクレームがtrue THEN 操作を許可する。ELSE 403エラー(RFC 7807)を返す"
    violation_behaviour: "操作は拒否され、403エラーが返される"
    source: FR5.5, FR5.6(ADR-002により拡張的に管理者ゲート対象)

  - id: BR6.1
    statement: 業務DBへの接続失敗(認証エラー・ネットワーク到達不可等)は例外として呼び出し元へ伝播し、frontend-admin向けREST境界では500エラー(RFC 7807)として応答する
    category: constraint
    applies_to: SchemaTarget, IngestedTable
    trigger: "業務DBへの接続確立に失敗したとき"
    logic: "IF 接続確立に失敗 THEN 例外を送出する。呼び出し元(config-managementまたはREST層)がこれを捕捉し応答を生成する"
    violation_behaviour: "該当なし"
    source: contract-summary.md #1、construction phaseガードライン(Error Handling)
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | constraint | 複合主キーの列順序はKEY_SEQ昇順 |
| BR1.2 | constraint | 主キーなしテーブルもhasPrimaryKey=falseで取り込む |
| BR1.3 | constraint | ビューは常にhasPrimaryKey=false扱い |
| BR1.4 | constraint | ストアドプロシージャは走査対象外 |
| BR2.1 | constraint | 型正規化はjava.sql.Types基準(TYPE_NAME文字列は不使用) |
| BR3.1 | constraint | 外部キーはFK_NAMEでグルーピングしKEY_SEQ順の配列で表現 |
| BR4.1 | constraint | スキーマ/DB一覧はgetSchemas()またはgetCatalogs() |
| BR5.1 | authorization | 全操作がisAdminクレーム必須 |
| BR6.1 | constraint | 接続失敗は例外伝播、REST境界で500 |
