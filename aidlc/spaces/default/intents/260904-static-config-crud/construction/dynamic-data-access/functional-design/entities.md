# Domain Entities: dynamic-data-access

dynamic-data-accessは、domain-design/components.mdの定義どおり永続エンティティを持たない。業務データそのもの
(対象RDBMSの任意の行)は、config-management(契約#2)から取得するTableConfigに基づき、実行時に動的なSQL
(NamedParameterJdbcTemplate、functional-design-questions.md Q2)で問い合わせる対象であり、本Unit自身が
所有・定義するスキーマは存在しない。

## 取り扱うデータ形状(機械可読)

```yaml
data_shapes:
  - name: RecordId
    description: >
      `/api/data/{tableId}/{recordId}` のrecordIdセグメントの内部表現。JSON**オブジェクト**
      (キー=カラム名、値=そのカラムの値)をBase64(URLセーフ)でエンコードした単一文字列とする
      (配列ではなくオブジェクトにすることで、エンコード・デコード間のカラム順序の一致に依存しない。
      R-04フォロー、dynamic-data-access Unit Functional Designレビューより)。含めるカラムは、
      主キーを持つテーブルの場合はprimaryKeyColumns(TableConfig由来)、主キーを持たないテーブル・
      ビューの場合は全カラムを基準集合とするが、いずれの場合も**BR4.2でaccessLevel=非表示と
      判定されたカラムはこの基準集合から除外する**(recordId生成時点の呼び出しロールについて)。
      これにより、非表示カラムの値がrecordId経由でクライアントへ露出することを防ぐ
      (R-01フォロー、dynamic-data-access Unit Functional Designレビューより)。
      主キー列自体が非表示に設定されている場合、残った可視カラムのみでは行を一意に特定できない
      ことがあるが、可視カラムが1件以上残る限りはこれを主キーなしテーブル・ビューで複数行が
      一致した場合と同様に扱う(最初の1件を返す。管理者が主キー列を非表示に設定するという通常
      想定しない構成の結果であり、許容するリスクとする)。一方、基準集合の全カラムが非表示と
      判定され可視カラムが1件も残らない場合は、WHERE句の一致条件が空になり任意の行(テーブル
      全体)にマッチしてしまうため、この場合に限り404を返し検索自体を実行しない(iteration 2
      レビューR-04フォロー。「残った可視カラムが一部」と「可視カラムが0件」は区別する)。
    attributes: [encodedColumnValues] # デコード後はカラム名→値のマップ(functional-design-questions.md Q1、R-01/R-04フォロー)

  - name: SearchCondition
    description: 一覧画面(FR3.1)の検索フォームから送られる検索条件。TableConfig.columns[].searchOperatorに基づく
    attributes: [columnName, operator, value] # operator: equals/contains/range/in(config-managementのTableColumnConfig.searchOperatorに対応)

  - name: RecordView
    description: 一覧・詳細画面へ返す業務データの1行分。FK値は表示名解決済み(FR4.1)
    attributes: [columnValues, resolvedForeignKeyLabels] # resolvedForeignKeyLabelsはFK列についてのみ、代表表示列(TableConfig.foreignKeyRepresentativeColumns)の値を追加で含む

note: >
  recordIdの全カラム一致方式(主キーなしテーブル・ビュー向け)は、重複行が存在する場合でも
  最初にマッチした1件を返す(読み取り専用画面であり、編集(FR3.3)は主キーを持つテーブルに限定される
  ため、レコード同定の曖昧さによる更新事故のリスクはない)。BR4.2による非表示カラムの除外後に
  一致対象カラムが減った場合も同様に、最初の1件を返す方針で統一する。

  recordIdはクライアントが `/api/data/{tableId}/{recordId}` のパスセグメントとして直接送信する
  値であり、Base64はエンコーディングであって署名・暗号化ではないため、改変された(サーバが
  生成したものではない)recordIdが渡される前提でデコード後の内容を扱う。詳細画面取得時、
  デコードしたカラム名→値のマップは、WHERE句として実行する前に(a)各キーが対象テーブルの
  正しい基準集合(TableConfig由来の既知の識別子)に含まれること、(b)各キーが呼び出しロールに
  ついてBR4.2でaccessLevel=非表示と判定されていないこと、の両方を検証し、いずれかに違反する
  場合は404を返す(検索実行前の防御的検証。BR2.1参照。R-05フォロー、dynamic-data-access Unit
  Functional Designレビューiteration 1より)。これにより、recordIdを改変して非表示カラムの
  値を検索結果の有無から推測するサイドチャネル(検索条件経由でR-01により塞がれたものと同種)を
  詳細画面経路でも防ぐ。
```

## データ形状サマリー

| データ形状 | 用途 | 備考 |
|---|---|---|
| RecordId | recordIdセグメントの内部表現 | 主キー/全カラムのうちaccessLevel≠非表示のものをカラム名→値のJSONオブジェクトとしてBase64エンコード(functional-design-questions.md Q1、R-01/R-04フォロー)。復元時は識別子・accessLevelを検索実行前に再検証(R-05フォロー) |
| SearchCondition | 一覧画面の検索条件 | TableConfig.columns[].searchOperatorに対応 |
| RecordView | 一覧・詳細画面のレスポンス | FK値は表示名解決済み(未解決分は生値のまま) |
