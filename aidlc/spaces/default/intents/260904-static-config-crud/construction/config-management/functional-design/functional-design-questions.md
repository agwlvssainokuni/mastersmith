# Functional Design Questions: config-management

requirements.md・contract-summary.mdだけでは確定しきれない、config-management固有の業務ルールを3点確定する。

## Q1. スキーマ取り込み結果からのテーブル設定作成フロー

schema-ingestion Unitのfunctional-spec.mdは「管理者が実際に取り込むテーブルを選択し、設定として確定する処理は、config-management Unit(契約#1の呼び出し元)が担当する」としているが、contract-summary.md #15(config-management API)には、この「選択したテーブルを取り込んでTableConfigを作成する」ためのエンドポイントがまだ定義されていない。追加するエンドポイントの形と、TableConfig初期値の決定方針を確定する。

- A. `POST /api/admin/table-configs/import-from-schema`(connectionId・schemaTarget・選択したphysicalTableName配列を受け取る)を新設する。作成される各TableConfigの初期値は、契約#1が返す正規化スキーマ情報から機械的に導出する: listColumns=全カラム、readOnlyColumns=空、searchConditions=全カラム(演算子はカラム型から妥当なものを既定選択)、formWidgets=カラム型からのデフォルト決定(FR2.1(8))、validationRules=DB制約由来(NOT NULL→必須、カラム長→文字数上限)、displayNames=物理名をそのまま初期値、foreignKeys/foreignKeyRepresentativeColumns=schema-ingestionが返す外部キー情報をそのまま反映(代表表示列は参照先テーブルの先頭の文字列型カラムを暫定選択)。作成後は通常のテーブル設定編集画面(`PUT /api/admin/table-configs/{tableId}`)で個別に調整する(推奨)
- B. 異なるエンドポイント形状・初期値決定方針を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q2. 設定キャッシュの実現方式と有効期限(FR2.6)

FR2.6は「キャッシュの明示的クリア操作またはキャッシュ設定のexpireを経由する」としており、両方の経路が言及されている。具体的な実現方式を確定する。

- A. Spring内蔵の`ConcurrentMapCacheManager`相当のシンプルなインメモリキャッシュとする。既定では無期限(明示的クリア`POST /api/admin/config/cache/clear`が唯一の更新契機)とし、application.yml設定でTTL(expire秒数)を任意指定できるようにする(未設定なら無期限)(推奨: 単純さを優先しつつFR2.6の「expire」文言にも対応)
- B. 異なる実現方式・既定値を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: X. Caffeine(`com.github.ben-manes.caffeine:caffeine`)をCacheManagerのバックエンドとして使用する。application.ymlでTTL(`expireAfterWrite`)を指定できるようにし、未設定の場合はexpireポリシーを設定しない(既定無期限、明示的クリアが唯一の更新契機)。それ以外(既定無期限・application.yml設定・明示的クリアAPIとの関係)はA案のまま踏襲する。

## Q3. 設定インポート時の「不整合」の具体的な判定基準(FR2.3.1)

FR2.3.1は「不正形式・スキーマ不一致・必須項目欠落」を不整合の例としているが、それぞれの具体的な判定基準を確定する。

- A. 「不正形式」=JSONとしてパース不能、またはトップレベル構造(tableConfigs/menuItems/dbConnections等の配列)が期待するスキーマに合わない。「スキーマ不一致」=各TableConfigのphysicalTableNameやカラム名が、対応するDbConnection経由でschema-ingestionへ問い合わせた現在の業務DBスキーマに存在しない。「必須項目欠落」=TableConfigのtableId・physicalTableName、DbConnectionのjdbcUrl・driverType等、各エンティティで必須のフィールドが欠落している。この3種のいずれかに該当すれば設定全体を拒否し(部分適用なし)、該当箇所すべてをerrors配列に列挙する(推奨)
- B. 異なる判定基準を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Q1〜Q3の回答を踏まえ、config-management Unitのfunctional-design成果物を以下の内容で確定します。

**エンティティ(entities.md)**: `DbConnection`(既存)。`TableConfig`にconnectionId・schemaTarget・tableDisplayName・columns[](カラムごとのdisplayName/isReadOnly/isSearchable/searchOperator/listOrder/formWidget/required/maxLength等をまとめたTableColumnConfig)・foreignKeys[]・foreignKeyRepresentativeColumns[]を具体化。`MenuItem`(既存、label/parentMenuItemId/tableId/displayOrder)。

**業務ルール(rules.md)**: BR1.1〜BR1.2(DbConnection CRUD、参照中削除拒否)、BR2.1〜BR2.6(契約#1駆動のTableConfig一括作成と初期値の機械的導出)、BR3.1(フォーム部品/検索演算子の列挙値検証)、BR4.1〜BR4.2(メニュー階層の循環参照禁止・tableId存在検証)、BR5.1〜BR5.3(エクスポート/インポート、3種不整合判定、インポート成功時のキャッシュ全体クリア)、BR6.1〜BR6.2(Caffeineキャッシュ、TTL/明示的クリア、個別無効化)、BR7.1(isAdminゲーティング)、BR8.1(監査ログイベント発行、CONFIG_TABLE_CREATED/UPDATED/DELETED/CONFIG_IMPORTED)。

**ワークフロー(functional-spec.md)**: DB接続先CRUD/スキーマ取り込みからのテーブル設定作成/テーブル設定の個別編集/メニュー構成CRUD/エクスポート/インポート/契約#2経由の設定参照、の7つ。

**契約(contract-summary.md)**: #15(config-management API)に`POST /api/admin/table-configs/import-from-schema`を加法的に追加。

**トレーサビリティ(traceability.json)**: upstream_ids = FR2.1〜FR2.6, FR2.3.1, FR3.4。isAdminゲーティング(BR7.1)と監査ログ発行(BR8.1)はreverseで横断的関心事として説明。

[Answer]: Looks correct
