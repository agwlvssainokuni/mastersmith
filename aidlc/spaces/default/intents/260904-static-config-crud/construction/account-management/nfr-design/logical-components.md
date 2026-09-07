# Logical Components: account-management

## コンポーネント構成

account-managementは永続エンティティを持たない(entities.md、AccountおよびRoleAssignmentはそれぞれauth・permissionが所有)ため、以下2つの論理コンポーネントで構成し、リポジトリコンポーネントは持たない。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| RESTコントローラ | アカウント作成・一覧・編集・無効化エンドポイント(`/api/admin/accounts*`)の受付、isAdmin認可検証、入力バリデーション | サービス |
| サービス | auth(契約#4)への委譲呼び出し(Account作成・取得・更新・無効化)、permission(契約#21)への委譲呼び出し(初期ロール割当・変更)、両者の結果を合成したAccountView構成、初期ロール割当失敗時の部分失敗許容ロジック(BR1.3) | auth(契約#4)、permission(契約#21) |

## 障害ドメインとブラストラディウス

account-management自身は状態を持たないため、本Unit自体の障害は次リクエストへ影響を残さない(ステートレス)。サービスコンポーネントが行うauth・permissionへの委譲呼び出しが失敗した場合、それぞれの契約のFailure behaviorに従い呼び出し元(RESTコントローラ)へエラーが伝播する(reliability-design.md参照)。ブラストラディウスはアカウント管理機能自体に限定され、auth・permission自身の他の機能(ログイン等)には影響しない。

## 共有リソース

本Unitは内部H2データストアを直接使用しない。auth・permissionが所有するテーブル(Account、RoleAssignment等)への直接アクセスも行わず、必ず契約#4・#21のプロセス内呼び出し経由でアクセスする。
