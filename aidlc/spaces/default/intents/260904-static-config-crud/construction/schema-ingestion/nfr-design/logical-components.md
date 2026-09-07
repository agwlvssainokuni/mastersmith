# Logical Components: schema-ingestion

## コンポーネント構成

schema-ingestionはステートレス(domain-design/components.md、entities.md参照)であり、単一のSpring Bootモジュール(パッケージ)内に、以下2つの論理コンポーネントで構成する。リポジトリ・永続化コンポーネントは持たない。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| RESTコントローラ | 接続テスト(`POST /api/admin/schema-ingestion/connection-test`)・スキーマ一覧(`GET /api/admin/schema-ingestion/schemas`)・プレビュー(`POST /api/admin/schema-ingestion/preview`)の受付、isAdmin認可検証(NFR-AUTHZ.1) | サービス |
| サービス | JDBC `DatabaseMetaData`を用いたスキーマ走査ロジック。driverType(PostgreSQL/MySQL/MariaDB)に応じた方言差異を吸収する。具体的には、`getSchemas()`/`getCatalogs()`の使い分け(BR4.1)、`getTables()`によるTABLE/VIEW取得とストアドプロシージャ除外(BR1.4)、`getColumns()`による型正規化(BR2.1)、`getPrimaryKeys()`による複合主キーのKEY_SEQ順序構成(BR1.1〜BR1.3)、`getImportedKeys()`による外部キー抽出(BR3.1)。RDBMS固有の差異(方言)はdriverTypeによる分岐、またはRDBMSごとの実装を切り替えるStrategyパターンのいずれかで吸収する設計方針とし(具体的な実装選択はcode-generation段階で確定する)、team.md Testing Postureが定める前倒し特性テスト対象(複合主キー・主キーなしテーブル・ビュー・型差異)と1対1で対応させる | (なし。業務DBへJDBC直接接続) |

## 障害ドメインとブラストラディウス

本Unitはステートレスであり、内部状態を持たない(NFR-RESILIENCE.1)。業務DBへの接続失敗・走査失敗は例外として呼び出し元(config-management)へ伝播し、500として応答する(BR6.1)。本Unit自体の障害からの復旧はアプリケーションの再起動のみで完結し、ブラストラディウスは接続テスト・スキーマ一覧取得・プレビュー機能自体に限定される(取り込み済みのTableConfig等、config-management側で既に確定済みの設定には影響しない)。

## 共有リソース

本Unitは内部H2データストアを使用しない(永続エンティティを持たないため)。業務DB(接続先RDBMS)への接続はconfig-managementから渡されたDbConnection情報(復号済み)を都度使用し、コネクションプール等の永続的な共有リソースは保持しない(走査完了後に接続を都度クローズする)。
