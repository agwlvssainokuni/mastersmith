# Logical Components: config-management

## コンポーネント構成

config-managementは単一のSpring Bootモジュール(パッケージ)内に、以下3つの論理コンポーネントで構成する。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| RESTコントローラ | 管理系エンドポイント(DB接続先・テーブル設定・メニュー構成・エクスポート/インポート・キャッシュクリア)の受付、isAdmin認可検証。GET /api/menu(非管理者向け例外)はX-Active-Role検証のみを行いisAdmin検証は行わない | サービス |
| サービス | スキーマ取り込みからのTableConfig一括作成(BR2.1〜BR2.8、外部キーの遡及解決を含む)、設定インポート時の3種不整合検証(BR5.2)、Caffeineキャッシュの読み取り・無効化管理(BR6.1〜BR6.2)、メニューのcanList権限フィルタリング(BR4.3、permission Unitへの契約#22プロセス内呼び出し) | リポジトリ、schema-ingestion(契約#1)、permission(契約#22) |
| リポジトリ | DbConnection・TableConfig・MenuItemの内部H2への永続化(Spring Data JPA) | 内部H2 |

## 障害ドメインとブラストラディウス

config-management自身の障害(内部H2への読み書き失敗、Caffeineキャッシュの不整合等)は設定管理機能全体(テーブル設定・メニュー・DB接続先の参照・変更)を停止させる。サービスコンポーネントが行うschema-ingestionへのプロセス内呼び出し(契約#1)が失敗した場合はTableConfig一括作成のみが失敗し(reliability-design.md NFR-FAILSAFE.1参照)、既存の確定済み設定には影響しない。同様にpermission Unitへの呼び出し(契約#22)が失敗した場合はGET /api/menuのみが失敗し、他の機能には波及しない。

## 共有リソース

内部H2データストアは他Unit(audit-log、permission等)と共有するが、テーブル自体(DbConnection、TableConfig、MenuItem)は本Unit専有であり、他Unitとのテーブルレベルの競合はない。Caffeineキャッシュはアプリケーション内メモリ上に保持され、他Unitとは共有しない。
