# Functional Design Questions: permission

domain-design/components.mdとrequirements.mdだけでは確定しきれない、permission固有の業務ルールを2点確定する。

## Q1. ロール切替(FR5.4)の対象に、グループ経由で割り当てられたロールを含めるか

domain-design/components.mdのRoleAssignmentは、ユーザに直接割り当てる場合とグループに割り当てる場合の両方をサポートする(assigneeTypeで区別)。複数ロール保有時の作業中ロール切替(`GET /api/me/roles`、contract-summary.md #13/#16)で一覧表示・選択可能にするロールの範囲を確定する。

- A. 自身のAccountに直接割り当てられたロールに加え、自身が所属するGroupに割り当てられたロールも切替候補に含める(グループ経由の割り当てが実質的に機能するために必要)(推奨)
- B. 自身のAccountに直接割り当てられたロールのみを切替候補とする(グループへの割り当ては別の意味を持つ)
- X. Other (please specify)

[Answer]: A

## Q2. テーブル単位権限(TablePermission)が未設定のテーブルへのデフォルトアクセス

FR5.1は「テーブル単位の権限(一覧/検索、詳細、作成、編集、削除)をロールに対して設定できること」としているが、あるロール・テーブルの組み合わせに対してTablePermissionレコードが1件も存在しない場合の既定の挙動(拒否/許可)を定めていない。

- A. デフォルト拒否とする。明示的にTablePermissionが設定されていないロール・テーブルの組み合わせは、一覧/詳細/作成/編集/削除のすべてを拒否する(セキュアバイデフォルト。construction phaseガードライン「認証・認可を回避するコードにフラグを立てる」の趣旨に沿う)(推奨)
- B. デフォルト許可とする(新しいテーブルを取り込むたびに全ロールへ明示的な権限設定が必要にならない)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- ロール切替(FR5.4)の候補には、自身のAccountに直接割り当てられたロールに加え、所属するGroupに割り当てられたロールも含める
- TablePermissionが未設定のロール・テーブルの組み合わせは、一覧/詳細/作成/編集/削除のすべてをデフォルト拒否する

- Looks correct
- Request changes

[Answer]: Looks correct

## Q3. 契約#22(config-management → permission)のpermission側への反映(frontend-core Unit Functional Designより)

frontend-core Unitのfunctional-designで、トップ画面のメニュー取得のためconfig-management→permissionの新規プロセス内呼び出し契約#22(指定roleIdがcanList=trueを持つtableId集合を返す)がcontract-summary.mdに新設された。当時はpermission Unit自身のfunctional-design成果物(rules.md/functional-spec.md/traceability.json)への反映は繰延べとしていたが、今回redo-jumpでfunctional-designステージ全体のレビューiterationがリセットされた機会に、この反映をあわせて行う。

- rules.mdにBR5.3(契約#22を受けたとき、指定roleIdがcanList=trueを持つtableId集合を返す。BR5.1のデフォルト拒否により未設定テーブルは含まれない)を追加した。
- functional-spec.mdにワークフロー8(契約#22によるcanList権限tableId集合の取得)を追加した。
- traceability.jsonのreverseにBR5.3(契約#22由来、permission自身のFRには対応しない)を追加した。

[Answer]: Looks correct
