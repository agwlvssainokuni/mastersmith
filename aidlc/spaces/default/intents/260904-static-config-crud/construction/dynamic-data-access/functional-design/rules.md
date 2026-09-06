# Business Rules: dynamic-data-access

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: 一覧画面は、契約#2(dynamic-data-access → config-management)から取得したTableConfigの検索条件設定(columns[].isSearchable/searchOperator)に基づき検索フォームを構成し、NamedParameterJdbcTemplateで動的にWHERE句を組み立てて検索する。ただし、呼び出しロールについてBR4.2でaccessLevel=非表示と判定されるカラムは、isSearchable=trueであっても検索条件として受け付けない(iteration 2レビューR-01フォロー)
    category: business
    applies_to: RecordView
    trigger: "GET /api/data/{tableId} を受けたとき"
    logic: "BR4.1のテーブル単位権限確認・BR4.2のカラム単位可視性判定を検索条件の受付より先に適用する。isSearchable=trueかつaccessLevel≠非表示のカラムのみ検索条件として受け付ける(accessLevel=非表示のカラムの検索条件は、リクエストに含まれていても無視する。非表示カラムの値を検索条件の一致/不一致という応答の違いから推測できてしまうサイドチャネルを防ぐため)。operatorがequalsなら完全一致、containsならLIKE(部分一致)、rangeなら範囲(2値)、inなら複数値のいずれかに一致、として動的にSQL条件を組み立てる(テーブル名・カラム名はTableConfigが提供する既知の識別子のみ使用し、値は必ずプレースホルダでバインドする)"
    violation_behaviour: "該当なし(非表示カラムの検索条件は静かに無視する。明示的なエラーとはしない、BR4.2と同様の扱い)"
    source: FR3.1, functional-design-questions.md Q2, functional-design-questions.md Q4 R-01(iteration 2レビューより)

  - id: BR1.2
    statement: 一覧画面は、TableConfig.columns[].listOrder(nullでないもの)に基づき表示列を決定し、ページネーション(page・size)とソート(sort)を受け付ける
    category: business
    applies_to: RecordView
    trigger: "GET /api/data/{tableId} を受けたとき"
    logic: "listOrderが設定されたカラムのみ、その順序で一覧に表示する"
    violation_behaviour: "該当なし"
    source: FR3.1

  - id: BR1.3
    statement: 一覧・詳細画面で返す各行のFK列について、TableConfig.foreignKeyRepresentativeColumnsで解決済みの代表表示列の値を実行時に問い合わせ、resolvedForeignKeyLabelsとして付加する。参照先が未解決(referencedTableId=null、config-management BR2.5参照)のFK列は名称解決の対象外とし、生の値のみを返す
    category: business
    applies_to: RecordView
    trigger: "GET /api/data/{tableId} または GET /api/data/{tableId}/{recordId} を受けたとき"
    logic: "該当なし"
    violation_behaviour: "該当なし(未解決FKはエラーではない、config-management functional-spec.mdワークフロー7参照)"
    source: FR4.1

  - id: BR2.1
    statement: recordIdは、一覧・詳細画面の各行を生成する時点で、主キーを持つテーブルはprimaryKeyColumns、主キーを持たないテーブル・ビュー(FR1.3、FR1.4)は全カラムを基準集合とし、そのうちBR4.2でaccessLevel=非表示と判定されたカラムを除外したカラム名→値のJSONオブジェクトをBase64エンコードして生成する(functional-design-questions.md Q1、R-01・R-04フォロー)。詳細画面の取得時は、Base64デコード→JSONパースで復元したカラム名→値のマップを、WHERE句の一致条件として組み立てる前に必ず検証する(recordIdはクライアントが直接送信するURLパスセグメントであり、Base64はエンコーディングであって署名や暗号化ではないため、改変されたrecordIdが渡される前提で扱う。R-05フォロー、dynamic-data-access Unit Functional Designレビューiteration 1より)
    category: business
    applies_to: RecordView
    trigger: "一覧・詳細画面の各行を生成するとき(生成)、またはGET /api/data/{tableId}/{recordId} を受けたとき(復元・検証・検索)"
    logic: "生成: BR4.2の列単位権限判定の後に、可視(非表示でない)基準集合カラムのみでオブジェクトを組み立てる。復元・検証(WHERE句組み立て・検索の実行より前に行う): (1)デコードしたカラム名→値のマップの各キーが、対象テーブルの正しい基準集合(主キーを持つテーブルはprimaryKeyColumns、主キーを持たないテーブル・ビューは全カラム、いずれもTableConfig由来の既知の識別子集合)に含まれるかを検証し、含まれないキーが1件でもあれば404を返す(BR1.1と同様、既知の識別子のみをSQL識別子位置で使用するための検証であり、未検証のキーをそのままSQL識別子として動的WHERE句に使わない)。(2)検証を通過したキー集合について、呼び出しロールのBR4.2 accessLevelを再確認し、accessLevel=非表示のキーが1件でも含まれていれば404を返す(正規に生成されたrecordIdには非表示カラムは含まれないため、含まれている場合は改変されたrecordId、またはrecordId生成後にロール・権限設定が変更された結果であり、いずれの場合も404として扱う)。上記2つの検証を通過した場合のみ、そのカラム名→値のマップをそのままWHERE句の一致条件として検索を実行する。検索実行後: recordIdのデコードに失敗した場合、または該当する行が0件の場合は404を返す。一致対象カラムが基準集合の一部のみ(非表示カラム除外により)、または主キーなしテーブル・ビューで複数行が一致した場合は、最初の1件を返す(entities.md note参照)。可視カラムが1件も残らない(基準集合の全カラムがaccessLevel=非表示)場合は、WHERE句を条件なしで実行せず404を返す(iteration 2レビューR-04フォロー)"
    violation_behaviour: "404エラー(RFC 7807、該当行なし・デコード失敗時・キー検証失敗時・accessLevel再検証失敗時のいずれも404で統一し、応答からは失敗理由を区別できないようにする。区別できてしまうこと自体がサイドチャネルになるため)"
    source: FR3.1, FR3.2, FR1.3, FR1.4, functional-design-questions.md Q1, functional-design-questions.md Q4 R-05(iteration 1レビューより)

  - id: BR3.1
    statement: 新規作成・更新画面は、主キーを持つテーブルのみを対象とする。主キーを持たないテーブル・ビューはPOST/PUTの対象外とする
    category: constraint
    applies_to: RecordView
    trigger: "POST /api/data/{tableId} または PUT /api/data/{tableId}/{recordId} を受けたとき"
    logic: "IF 対象テーブルのTableConfig.primaryKeyColumnsが空 THEN 403を返す(編集権限の有無に関わらず、構造上編集不可)"
    violation_behaviour: "403エラー(RFC 7807)"
    source: FR1.3, FR1.4, FR3.3

  - id: BR3.2
    statement: 新規作成・更新画面の入力値は、TableConfig.columns[]のバリデーション設定(required・maxLength・minValue・maxValue・pattern・unique)に基づき検証する。isReadOnly=trueのカラムは入力対象から除外する
    category: constraint
    applies_to: RecordView
    trigger: "POST /api/data/{tableId} または PUT /api/data/{tableId}/{recordId} を受けたとき"
    logic: "IF いずれかのバリデーションに違反 THEN 400を返す(RFC 7807、違反したカラムとルールを含む)"
    violation_behaviour: "400エラー(RFC 7807)"
    source: FR2.1(7)(config-management由来)、FR3.3

  - id: BR3.3
    statement: 更新画面は、対象RDBMSのバージョン列・最終更新日時列の存在を前提とした競合検出を行わず、PUT時点の主キー一致のみでUPDATE文を実行する(後勝ち)
    category: business
    applies_to: RecordView
    trigger: "PUT /api/data/{tableId}/{recordId} を受けたとき"
    logic: "UPDATE文のWHERE句はprimaryKeyColumnsの一致のみとする。事前読み取り時の値との比較(楽観的ロック相当の検証)は行わない"
    violation_behaviour: "該当なし"
    source: FR3.5

  - id: BR3.4
    statement: 新規作成・更新時、外部キー制約違反(参照先に存在しない値の指定)はDB側の制約エラーとして検出し、400として応答する
    category: constraint
    applies_to: RecordView
    trigger: "POST /api/data/{tableId} または PUT /api/data/{tableId}/{recordId} でFK列に値を指定したとき"
    logic: "該当なし(対象RDBMSの外部キー制約に委ねる)"
    violation_behaviour: "400エラー(RFC 7807、契約#12 PUT/POST responsesの「FK制約違反」)"
    source: contract-summary.md #12, FR3.3

  - id: BR4.1
    statement: 一覧・詳細・新規作成・更新のいずれの操作も、契約#3(dynamic-data-access → permission)を呼び出し、X-Active-Roleヘッダーのroleidに対するテーブル単位の権限(list/view/create/edit)を確認する。不許可の場合は403を返す
    category: authorization
    applies_to: RecordView
    trigger: "dynamic-data-accessへのいずれかの操作要求を受けたとき"
    logic: "IF X-Active-Roleがアクセストークンのrolesクレームに含まれない THEN 403。IF テーブル単位の該当アクション権限がない THEN 403。ELSE BR4.2(カラム単位)へ進む"
    violation_behaviour: "403エラー(RFC 7807)"
    source: FR5.1, FR5.3, FR5.4, contract-summary.md #3, FR3.1, FR3.2, FR3.3(R-05フォロー、dynamic-data-access Unit Functional Designレビューより)

  - id: BR4.2
    statement: 一覧・詳細・新規作成・更新の各カラムについて、契約#3から取得したカラム単位のaccessLevel(更新可/表示のみ/非表示)に基づき、レスポンス・入力受付を制御する
    category: authorization
    applies_to: RecordView
    trigger: "BR4.1のテーブル単位権限確認の成功後"
    logic: "accessLevel=非表示のカラムはレスポンスに含めない。accessLevel=表示のみのカラムは一覧・詳細に含めるが、更新画面での入力を受け付けない(送信されても無視する)。accessLevel=更新可のカラムのみ更新を受け付ける"
    violation_behaviour: "該当なし(非表示・表示のみは静かに除外・無視する。明示的なエラーとはしない)"
    source: FR5.2, contract-summary.md #3, FR3.1, FR3.2, FR3.3(R-05フォロー、dynamic-data-access Unit Functional Designレビューより)

  - id: BR5.1
    statement: FKポップアップ検索(GET /api/data/{tableId}/fk-search/{columnName})は、対象カラムが参照する先テーブルに対し、カラムごとの絞り込み条件付きで検索を行い、代表表示列と主キー(または全カラム、recordId生成に使う)を返す。参照先テーブルについてもBR1.1と同様、accessLevel=非表示のカラムは絞り込み条件として受け付けない(iteration 2レビューR-01フォロー)
    category: business
    applies_to: RecordView
    trigger: "GET /api/data/{tableId}/fk-search/{columnName} を受けたとき"
    logic: "参照先テーブルのTableConfigに対してもBR4.1(テーブル単位権限、対象アクションはview)を適用する。参照先が未解決(config-management側でreferencedTableId=null)の場合は404を返す。参照先テーブルの列単位accessLevel(BR4.2)を絞り込み条件の受付より先に確認し、accessLevel=非表示のカラムの絞り込み条件は無視する"
    violation_behaviour: "404エラー(RFC 7807、参照先テーブルが未解決またはtableId/columnNameが不正な場合)"
    source: FR4.2, functional-design-questions.md Q4 R-01(iteration 2レビューより)

  - id: BR6.1
    statement: 業務データの作成・更新の成功を、契約#5〜#8(監査ログイベント契約)に基づきactionType=DATA_RECORD_CREATED/DATA_RECORD_UPDATEDのAuditableActionOccurredEventとして発行する。本Unitに削除操作(FR3.3は新規作成・更新のみを対象とし、契約#12にもDELETEエンドポイントは存在しない)は存在しないため、契約#7が語彙として持つDATA_RECORD_DELETEDは発行しない(R-02フォロー、dynamic-data-access Unit Functional Designレビューより)
    category: business
    applies_to: RecordView
    trigger: "BR3.2の検証を通過したPOST/PUTが成功したとき"
    logic: "targetDescription = \"{physicalTableName}: {recordIdの人間可読な表現}\""
    violation_behaviour: "該当なし(記録失敗は主処理をブロックしない)"
    source: unit-of-work-dependency.md(dynamic-data-access→audit-log)、contract-summary.md #7(dynamic-data-access用actionType語彙。DATA_RECORD_DELETEDは本Unitでは未使用)
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | business | 一覧画面の動的検索条件組み立て |
| BR1.2 | business | 一覧画面の表示列・ページネーション・ソート |
| BR1.3 | business | FK値の表示名解決(未解決FKは対象外) |
| BR2.1 | business | recordIdの生成(非表示カラム除外)・デコード後の識別子/accessLevel検証・WHERE句組み立て |
| BR3.1 | constraint | 主キーなしテーブル・ビューの編集不可 |
| BR3.2 | constraint | 入力バリデーション(TableConfig由来) |
| BR3.3 | business | 後勝ち更新(競合制御なし) |
| BR3.4 | constraint | 外部キー制約違反の400応答 |
| BR4.1 | authorization | テーブル単位権限確認(契約#3) |
| BR4.2 | authorization | カラム単位権限制御(表示・入力の制御) |
| BR5.1 | business | FKポップアップ検索 |
| BR6.1 | business | 監査ログイベント発行(DATA_RECORD_CREATED/UPDATEDのみ、DELETEDは未使用) |
