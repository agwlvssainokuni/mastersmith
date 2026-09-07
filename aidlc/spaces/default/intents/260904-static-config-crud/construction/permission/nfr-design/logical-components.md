# Logical Components: permission

## コンポーネント構成

permissionは単一のSpring Bootモジュール(パッケージ)内に、以下4つの論理コンポーネントで構成する。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| RESTコントローラ | 管理系エンドポイント(Role/Group CRUD・名称変更・削除、グループメンバー管理、テーブル/カラム権限設定、ロール割当、`GET /api/me/roles`)の受付、isAdmin認可検証(`GET /api/me/roles`を除く、BR4.1は全利用者向け) | サービス |
| 内部呼び出しAPI | 契約#3(dynamic-data-access → permission、テーブル/カラム権限確認)・契約#20(auth → permission、有効ロール集合取得)・契約#21(account-management → permission、初期ロール割当)・契約#22(config-management → permission、canList権限tableId集合取得)によるプロセス内(in-process)呼び出しの受付窓口。REST経由ではなく直接メソッド呼び出しであり、isAdmin検証は適用しない(NFR-AUTHZ.3) | サービス |
| サービス | ロール割当の全置換ロジック(BR3.2、去重〈dedup〉含む)、冪等処理(BR1.2のグループメンバー重複追加、BR3.3のロール割当重複)、有効ロール集合の計算(直接割当+グループ経由の和集合、BR4.1)、テーブル単位権限判定(BR5.1、未設定はデフォルト拒否)、カラム単位権限判定(BR5.2、未設定はデフォルトeditable)、canList権限tableId集合の算出(BR5.3)、ロール・グループ削除時のカスケード削除(BR1.4・BR1.5) | リポジトリ |
| リポジトリ | Role・Group・GroupMembership・RoleAssignment・TablePermission・ColumnPermissionの内部H2への永続化(Spring Data JPA) | 内部H2 |

## 障害ドメインとブラストラディウス

permission自身の障害(内部H2への読み書き失敗等)は、内部呼び出しAPI経由の呼び出し元(dynamic-data-access・auth・account-management・config-management)へ例外として伝播する。これらの呼び出し元はフェイルセーフ設計(reliability-design.md参照)に従い、それぞれ自身の主処理を5xx応答として終端させる(cascading failureではなく、境界での明示的な失敗として扱う)。したがって、ブラストラディウスはpermissionへの呼び出しを含む操作(権限確認を伴う一覧・詳細・編集操作、ログイン、アカウント作成、メニュー取得)に限定され、これらの操作を経由しない機能には波及しない。

## 共有リソース

内部H2データストアは他Unit(audit-log、config-management等)と共有するが、テーブル自体(Role・Group・GroupMembership・RoleAssignment・TablePermission・ColumnPermission)は本Unit専有であり、他Unitとのテーブルレベルの競合はない。
