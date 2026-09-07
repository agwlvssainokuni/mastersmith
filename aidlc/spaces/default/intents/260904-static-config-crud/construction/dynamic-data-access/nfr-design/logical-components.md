# Logical Components: dynamic-data-access

## コンポーネント構成

dynamic-data-accessは以下3つの論理コンポーネントで構成する。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| RESTコントローラ | 一覧・詳細・新規作成・更新・FKポップアップ検索エンドポインドの受付、X-Active-Roleヘッダーの必須検証 | サービス |
| サービス | 検索条件組み立て(BR1.1、accessLevel除外含む)、ソート検証(BR1.2、識別子検証・非表示除外)、FK表示名解決(BR1.3)、recordId生成・デコード検証(BR2.1)、新規作成・更新の入力バリデーション(BR3.1〜BR3.4)、テーブル/カラム単位権限確認(BR4.1〜BR4.2、permission Unitへの契約#3プロセス内呼び出し)、FKポップアップ検索ロジック(BR5.1、既知のNFR-SIDECHANNEL.2ギャップを含む)、監査イベントトリガー(BR6.1) | 動的クエリ実行、config-management(契約#2)、permission(契約#3) |
| 動的クエリ実行 | NamedParameterJdbcTemplateによる動的WHERE句・SELECT列・ORDER BY句の組み立てと業務DBへの実行。テーブル名・カラム名はTableConfig由来の既知識別子集合のみを使用し、値は必ずプレースホルダでバインドする(NFR-INJECTION.1の実装責務) | 業務DB(接続先RDBMS) |

## 障害ドメインとブラストラディウス

本Unitの障害ドメインは業務データアクセス機能自体(一覧・詳細・編集・FK検索)に限定される。サービスコンポーネントが行うpermission Unitへの契約#3呼び出し・config-managementへの契約#2呼び出しが失敗した場合、それぞれの契約のFailure behaviorに従いエラーが伝播する。動的クエリ実行コンポーネントの障害(業務DB接続断等)は、当該テーブルへのアクセスのみを失敗させ、他テーブル・他Unitの機能には波及しない。

## 共有リソース

業務DB(接続先RDBMS)は本Unitが唯一データ操作(SELECT/INSERT/UPDATE)を行うUnitであり、他Unit(schema-ingestion)はスキーマ読み込み(メタデータ取得)のみを行い、データ操作は行わない。内部H2データストアは本Unit自身では使用しない。
