# Business Rules: config-management

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: DbConnectionの作成・更新時、jdbcUrl・driverType・nameが必須であることを検証する
    category: constraint
    applies_to: DbConnection
    trigger: "POST /api/admin/db-connections または PUT /api/admin/db-connections/{id} を受けたとき"
    logic: "IF jdbcUrl、driverType、nameのいずれかが空 THEN 400を返す"
    violation_behaviour: "400エラー(RFC 7807)"
    source: FR2.1(1)

  - id: BR1.2
    statement: DbConnectionの削除は、当該connectionIdを参照するTableConfigが1件でも存在する場合は拒否する
    category: business
    applies_to: DbConnection
    trigger: "DELETE /api/admin/db-connections/{id} を受けたとき"
    logic: "IF TableConfig.connectionId = idのレコードが1件以上存在 THEN 409を返す。ELSE 削除を実行する"
    violation_behaviour: "409エラー(RFC 7807、参照が残っている旨を示す)"
    source: contract-summary.md #15(config-management API)、Construction phaseガードライン(Error Handling)

  - id: BR2.1
    statement: POST /api/admin/table-configs/import-from-schema で受け取ったconnectionId・schemaTarget・選択テーブル名配列に対し、契約#1(config-management → schema-ingestion)を呼び出して正規化スキーマ情報を取得し、選択された各テーブルについてTableConfigを新規作成する
    category: business
    applies_to: TableConfig
    trigger: "POST /api/admin/table-configs/import-from-schema を受けたとき"
    logic: "契約#1のProvider returnsに含まれる各テーブルのphysicalTableName・isView・primaryKeyColumns・columns(名前・型)・foreignKeysを基に、BR2.2〜BR2.6の初期値導出ルールを適用してTableConfigを作成する"
    violation_behaviour: "契約#1の失敗(接続失敗等)はそのまま呼び出し元へ伝播し、500として応答する"
    source: functional-design-questions.md Q1, contract-summary.md #1

  - id: BR2.2
    statement: TableColumnConfig.formWidgetの初期値は、カラムの論理型(schema-ingestionのlogicalType)から機械的に決定する
    category: business
    applies_to: TableConfig
    trigger: "BR2.1によるTableConfig新規作成時"
    logic: "IF logicalType=boolean THEN formWidget=checkbox。IF logicalType=text THEN formWidget=textarea。IF logicalType IN (string, integer, decimal, date, datetime) THEN formWidget=text。IF 外部キーの一部であるカラム THEN formWidget=select(参照先の候補から選択)。ELSE formWidget=text"
    violation_behaviour: "該当なし"
    source: functional-design-questions.md Q1, FR2.1(8)

  - id: BR2.3
    statement: TableColumnConfig.requiredおよびmaxLengthの初期値は、schema-ingestionが返すDB制約(NOT NULL・カラム長)から機械的に決定する
    category: business
    applies_to: TableConfig
    trigger: "BR2.1によるTableConfig新規作成時"
    logic: "required = (カラムがNOT NULL制約を持つ)。maxLength = (文字列型カラムでDB側に長さ制約がある場合、その値。それ以外はnull)"
    violation_behaviour: "該当なし"
    source: functional-design-questions.md Q1, FR2.1(7)

  - id: BR2.4
    statement: TableColumnConfig.isSearchable/listOrder/displayNameの初期値は、それぞれtrue(全カラム検索可能)/物理カラム順/物理カラム名とする
    category: business
    applies_to: TableConfig
    trigger: "BR2.1によるTableConfig新規作成時"
    logic: "isSearchable=true、searchOperator=equals(既定)、listOrder=schema-ingestionが返すカラム順、displayName=physicalColumnName、tableDisplayName=physicalTableName"
    violation_behaviour: "該当なし"
    source: functional-design-questions.md Q1, FR2.1(4), FR2.1(5), FR2.1(9)

  - id: BR2.5
    statement: TableConfig.foreignKeysの初期値は、schema-ingestionが返す各IngestedForeignKeyのcolumns[]・referencedColumns[]をそのまま複写しつつ、referencedTable(参照先の物理テーブル名)を、同一connectionId・schemaTarget内でphysicalTableNameが一致する既存TableConfigのtableIdへ解決してreferencedTableIdに設定する。一致するTableConfigがまだ存在しない(参照先テーブルが未インポート)場合は、referencedTableIdをnullのまま、referencedTablePhysicalNameに物理テーブル名を保持する
    category: business
    applies_to: TableConfig
    trigger: "BR2.1によるTableConfig新規作成時"
    logic: "IF 同一connectionId・schemaTarget内にphysicalTableName=referencedTableのTableConfigが存在 THEN referencedTableId=そのtableId。ELSE referencedTableId=null(未解決)。いずれの場合もreferencedTablePhysicalName=referencedTableを保持する"
    violation_behaviour: "該当なし(未解決は拒否理由にならない。BR2.7で後続インポート時に解決する)"
    source: functional-design-questions.md Q1, FR4.1

  - id: BR2.7
    statement: 新規TableConfigをインポートした際、既存の他TableConfigが持つ未解決の外部キー(referencedTableId=null)のうち、referencedTablePhysicalNameが新規TableConfigのphysicalTableNameと一致するものについて、referencedTableIdを新規TableConfigのtableIdへ遡って解決する
    category: business
    applies_to: TableConfig
    trigger: "BR2.1によるTableConfig新規作成が完了したとき(同一connectionId・schemaTarget内の既存TableConfigすべてに対して実行する)"
    logic: "該当する既存TableConfigのforeignKeys配列内の該当エントリのreferencedTableIdのみを更新する(他の属性は変更しない)。該当キャッシュエントリも合わせて無効化する(BR6.2)"
    violation_behaviour: "該当なし"
    source: functional-design-questions.md Q1, FR4.1(R-01フォロー、Construction / config-management Unit Functional Designレビューより)

  - id: BR2.8
    statement: TableConfig.foreignKeyRepresentativeColumnsの初期値は、参照先テーブルの最初の文字列型(logicalType=string)カラムを暫定選択する。referencedTableIdが未解決(BR2.5でnull)の間は代表表示列も決定できないため、参照先解決後(BR2.7時点)に決定する
    category: business
    applies_to: TableConfig
    trigger: "BR2.1によるTableConfig新規作成時、またはBR2.7による遡及解決時"
    logic: "各参照先テーブルについて、logicalType=stringの最初のカラムを選択する(該当なしの場合は主キーの最初のカラムを代替選択する)。参照先が未解決の間はforeignKeyRepresentativeColumnsに当該エントリを追加しない"
    violation_behaviour: "該当なし(未解決の間はFR4.1のFK名称解決・FR4.2のポップアップ検索を提供できないが、それ自体はエラーではない。dynamic-data-access側はreferencedTableIdがnullのFKを名称解決対象外として扱う)"
    source: functional-design-questions.md Q1, FR4.1, FR4.2(R-01フォロー、Construction / config-management Unit Functional Designレビューより)

  - id: BR2.6
    statement: BR2.1〜BR2.5により作成されたTableConfigの初期値は、作成後にPUT /api/admin/table-configs/{tableId}で個別に上書き・調整できる
    category: business
    applies_to: TableConfig
    trigger: "TableConfig作成後の通常の編集操作"
    logic: "該当なし(通常のCRUD更新と同一)"
    violation_behaviour: "該当なし"
    source: functional-design-questions.md Q1, FR2.2

  - id: BR3.1
    statement: TableColumnConfigの更新時、formWidgetが許容される列挙値(text/textarea/checkbox/switch/radio/select)以外、またはsearchOperatorが許容される列挙値(equals/contains/range/in)以外の場合は拒否する
    category: constraint
    applies_to: TableConfig
    trigger: "PUT /api/admin/table-configs/{tableId} を受けたとき"
    logic: "IF いずれかのカラム設定のformWidgetまたはsearchOperatorが許容列挙値の範囲外 THEN 400を返す"
    violation_behaviour: "400エラー(RFC 7807)"
    source: FR2.1(8)

  - id: BR4.1
    statement: MenuItemの作成・更新時、parentMenuItemIdをたどった結果、自身に戻る循環参照を形成する場合は拒否する
    category: constraint
    applies_to: MenuItem
    trigger: "POST /api/admin/menu-items または PUT /api/admin/menu-items/{id} を受けたとき"
    logic: "parentMenuItemIdを再帰的にたどり、自身のmenuItemIdに到達する場合は400を返す"
    violation_behaviour: "400エラー(RFC 7807)"
    source: FR2.4

  - id: BR4.2
    statement: MenuItemのtableIdが指定されている場合、対応するTableConfigが存在することを検証する
    category: constraint
    applies_to: MenuItem
    trigger: "POST /api/admin/menu-items または PUT /api/admin/menu-items/{id} を受けたとき"
    logic: "IF tableId IS NOT NULL AND 対応するTableConfigが存在しない THEN 400を返す"
    violation_behaviour: "400エラー(RFC 7807)"
    source: FR3.4

  - id: BR4.3
    statement: GET /api/menu(契約#23、非管理者向け、BR7.1のisAdmin必須の対象外)は、呼び出しロール(X-Active-Roleヘッダー由来)がcanList権限を持つtableIdを持つMenuItem(テーブルノード)のみを含むメニュー階層を返す。フォルダ/グループノード(tableId=null)は、配下に1件も可視なテーブルノードがない場合は結果から除外する
    category: business
    applies_to: MenuItem
    trigger: "非管理者を含む全利用者がGET /api/menuを呼び出したとき(frontend-core Unit Functional Designより新設)"
    logic: "IF X-Active-Roleがアクセストークンのrolesクレームに含まれない THEN 403を返す。ELSE 契約#22(config-management → permission)でX-Active-Roleに対応するroleIdが持つcanList=trueのtableId集合を取得し、テーブルノード(tableId IS NOT NULL)はその集合に含まれるもののみ残す。フォルダ/グループノード(tableId IS NULL)は、再帰的に配下をたどって1件でも可視なテーブルノードが残る場合のみ結果に含める"
    violation_behaviour: "403エラー(RFC 7807、X-Active-Roleがrolesクレームに含まれない場合)"
    source: contract-summary.md #22・#23(frontend-core Unit Functional Designより新設)

  - id: BR5.1
    statement: 設定エクスポート(POST /api/admin/config/export)は、DbConnection・TableConfig・MenuItemの全件を単一の設定ファイル(JSON)として出力する。DbConnection.credentialRefは暗号化された値のまま出力し、復号は行わない
    category: business
    applies_to: DbConnection, TableConfig, MenuItem
    trigger: "POST /api/admin/config/export を受けたとき"
    logic: "該当なし"
    violation_behaviour: "該当なし"
    source: FR2.3

  - id: BR5.2
    statement: 設定インポート(POST /api/admin/config/import)時、不正形式(JSONパース不能またはトップレベル構造がスキーマに合わない)・スキーマ不一致(TableConfigのphysicalTableName/カラム名が対応するDbConnection経由の現在の業務DBスキーマに存在しない)・必須項目欠落(各エンティティの必須フィールド欠落)のいずれかを検出した場合、設定全体の適用を拒否し(部分適用なし)、該当箇所すべてをerrors配列として返す
    category: business
    applies_to: DbConnection, TableConfig, MenuItem
    trigger: "POST /api/admin/config/import を受けたとき"
    logic: "3種の不整合いずれについても検証を最後まで行い(最初の1件で打ち切らない)、検出した全件をerrors配列に含めて409を返す。1件も検出されなければ、既存の設定全体を置き換えて200を返す"
    violation_behaviour: "409エラー(RFC 7807、errors配列に不整合内容を列挙)"
    source: FR2.3.1, functional-design-questions.md Q3

  - id: BR5.3
    statement: 設定インポートが成功した場合、設定キャッシュを即座にクリアする(BR6.1と連携)
    category: business
    applies_to: DbConnection, TableConfig, MenuItem
    trigger: "BR5.2でインポートが成功したとき"
    logic: "該当なし(BR6.1のキャッシュクリア処理を呼び出す)"
    violation_behaviour: "該当なし"
    source: FR2.3.1, FR2.6

  - id: BR6.1
    statement: 設定キャッシュはCaffeineによるインメモリキャッシュとし、DbConnection・TableConfig・MenuItemの読み取りは常にキャッシュ経由で行う。キャッシュの更新は、POST /api/admin/config/cache/clear による明示的クリア、またはapplication.yml設定によるTTL(expireAfterWrite)経過時の自動失効のいずれかでのみ発生する。TTL未設定の場合は無期限(明示的クリアのみが更新契機)とする
    category: constraint
    applies_to: DbConnection, TableConfig, MenuItem
    trigger: "設定の作成・更新・削除・インポートを行うとき、および契約#2(dynamic-data-access → config-management)経由での参照時"
    logic: "設定の作成・更新・削除・インポートの都度、DBへの書き込みに続けて該当キャッシュエントリを明示的に無効化する(明示的クリアの一種)。契約#2経由の参照はキャッシュヒット時のみキャッシュから返し、ミス時のみDBへ問い合わせてキャッシュに格納する"
    violation_behaviour: "該当なし"
    source: FR2.6, functional-design-questions.md Q2

  - id: BR6.2
    statement: 設定の作成・更新・削除の都度、変更されたエンティティ自身のキャッシュエントリを明示的に無効化する(BR6.1の一部の具体化)
    category: business
    applies_to: DbConnection, TableConfig, MenuItem
    trigger: "DbConnection・TableConfig・MenuItemのいずれかの作成・更新・削除操作が成功したとき"
    logic: "該当エンティティのキャッシュエントリのみを無効化する(全体クリアは行わない。ただしBR5.3のインポート成功時は全体クリアとする)"
    violation_behaviour: "該当なし"
    source: FR2.6

  - id: BR7.1
    statement: config-managementの全操作(DB接続先設定・テーブル設定・メニュー構成・エクスポート/インポート・キャッシュクリア)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる。ただしGET /api/menu(契約#23、非管理者向け)を除く。同エンドポイントの認可判定はBR4.3(X-Active-Roleがrolesクレームに含まれるか)に従う(frontend-core Unit Functional Designより除外を追記)
    category: authorization
    applies_to: DbConnection, TableConfig, MenuItem
    trigger: "GET /api/menu(BR4.3)を除く、config-managementへのいずれかの操作要求を受けたとき"
    logic: "IF アクセストークンのisAdminクレームがtrue THEN 操作を許可する。ELSE 403エラー(RFC 7807)を返す"
    violation_behaviour: "操作は拒否され、403エラーが返される"
    source: FR5.5, FR5.6

  - id: BR8.1
    statement: DbConnection・TableConfig・MenuItemの作成・更新・削除、および設定インポートの成功を、契約#5〜#8(監査ログイベント契約)に基づきAuditableActionOccurredEventとして発行する。DbConnection・MenuItemには、契約#7の枠内で追加的に所有する専用のactionType語彙(CONFIG_CONNECTION_CREATED/UPDATED/DELETED、CONFIG_MENU_CREATED/UPDATED/DELETED)を用い、TableConfigのCONFIG_TABLE_*とは区別する
    category: business
    applies_to: DbConnection, TableConfig, MenuItem
    trigger: "作成・更新・削除操作、またはBR5.2のインポート成功が完了したとき"
    logic: >
      IF TableConfig作成(BR2.1経由含む) THEN actionType=CONFIG_TABLE_CREATED。
      IF TableConfig更新(BR2.6、BR3.1、BR2.7の遡及解決は対象外) THEN actionType=CONFIG_TABLE_UPDATED。
      IF TableConfig削除 THEN actionType=CONFIG_TABLE_DELETED。
      IF DbConnection作成/更新/削除 THEN actionType=CONFIG_CONNECTION_CREATED/CONFIG_CONNECTION_UPDATED/CONFIG_CONNECTION_DELETED。
      IF MenuItem作成/更新/削除 THEN actionType=CONFIG_MENU_CREATED/CONFIG_MENU_UPDATED/CONFIG_MENU_DELETED。
      IF 設定インポート成功(BR5.2) THEN actionType=CONFIG_IMPORTED(この場合、個別のCONFIG_TABLE_*/CONFIG_CONNECTION_*/CONFIG_MENU_*イベントは発行せず1件のCONFIG_IMPORTEDのみとする)。
      targetDescription = "table_config: {physicalTableName}"(TableConfigの場合)または"db_connection: {name}"/"menu_item: {label}"とする。
    violation_behaviour: "該当なし(記録失敗は主処理をブロックしない、契約#5〜#8のasync仕様どおり)"
    source: unit-of-work-dependency.md(config-management→audit-log)、contract-summary.md #7(config-management用actionType語彙、CONFIG_CONNECTION_*/CONFIG_MENU_*はR-02フォロー・Construction / config-management Unit Functional Designレビューで加法的に追記)
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | constraint | DbConnection必須項目検証 |
| BR1.2 | business | 参照中のDbConnection削除拒否 |
| BR2.1 | business | スキーマ取り込みからのTableConfig一括作成 |
| BR2.2 | business | formWidget初期値の機械的決定 |
| BR2.3 | business | required/maxLength初期値のDB制約由来決定 |
| BR2.4 | business | isSearchable/listOrder/displayName初期値決定 |
| BR2.5 | business | foreignKeysの初期値決定(名前→ID解決、未解決時はnull保持) |
| BR2.6 | business | 作成後の個別調整(通常CRUD) |
| BR2.7 | business | 新規インポート時の未解決FKの遡及解決 |
| BR2.8 | business | 代表表示列の初期値決定(解決後に決定) |
| BR3.1 | constraint | formWidget/searchOperatorの列挙値検証 |
| BR4.1 | constraint | メニュー階層の循環参照禁止 |
| BR4.2 | constraint | メニューのtableId存在検証 |
| BR4.3 | business | GET /api/menu: canList権限フィルタ済みメニュー階層(契約#22・#23) |
| BR5.1 | business | 設定エクスポート(暗号化のまま出力) |
| BR5.2 | business | 設定インポート時の3種不整合判定・全体拒否 |
| BR5.3 | business | インポート成功時のキャッシュ全体クリア |
| BR6.1 | constraint | Caffeineキャッシュ、TTL/明示的クリア |
| BR6.2 | business | 個別変更時のキャッシュエントリ無効化 |
| BR7.1 | authorization | isAdminクレーム必須(GET /api/menuを除く。BR4.3参照) |
| BR8.1 | business | 監査ログイベント発行(CONFIG_TABLE_*/CONFIG_CONNECTION_*/CONFIG_MENU_*/CONFIG_IMPORTED) |
