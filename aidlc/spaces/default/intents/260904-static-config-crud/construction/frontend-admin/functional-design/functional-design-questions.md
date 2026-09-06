# Functional Design Questions: frontend-admin

requirements.md・contract-summary.md・refined-mockups(ideation/rough-mockups/wireframes.md、inception/refined-mockups/mockups.md・interaction-spec.md・design-system-mapping.md)だけでは確定しきれない、frontend-admin固有の設計上のギャップを2点確定する。

## Q1. ロール・グループの名前変更・削除

permission Unit(BR1.1)は「名称変更」をトリガーとして言及していたが、contract-summary.mdのpermission API(#13/#16)にはロール・グループの名前変更(PUT)・削除(DELETE)のRESTエンドポイントが一切存在しなかった(一覧・作成のみ)。wireframes.mdの7e(グループ管理)も「削除・改名の導線は本ラフ案では省略している」と明記しており、未解決のまま現在に至っていた。

- A. 名前変更のみサポートする。削除はMVP対象外とする
- B. 名前変更・削除ともにサポートする。削除時の既存RoleAssignment/GroupMembershipの扱い(カスケード削除か、使用中なら409拒否か)を決める必要あり
- C. 現状維持(名前変更・削除ともに提供しない)
- X. Other (please specify)

[Answer]: B(採用済み)。permission Unitのfunctional-designを再オープンし、BR1.3(名称変更)・BR1.4(ロール削除、RoleAssignment参照が残っていれば409拒否・TablePermission/ColumnPermissionは道連れ削除)・BR1.5(グループ削除、RoleAssignment(assigneeType=group)参照が残っていれば409拒否・GroupMembershipは道連れ削除)を新設した。contract-summary.mdにPUT/DELETE /api/admin/roles/{roleId}・/api/admin/groups/{groupId}を追加済み。ロール一覧画面(7a)・グループ管理画面(7e)に名称変更・削除ボタンを追加する(mockups.md更新済み)。

## Q2. メニュー階層全体の編集手段

設定管理画面(10.)の「メニュー」タブは、interaction-spec.mdのConfigTabPanelのpropsが`tableName`のみ(特定テーブルの設定画面)であり、メニュー階層全体の編集(フォルダ/グループノード(tableIdなし)の作成、複数テーブル間での並べ替え等)を行う手段が現状のモックアップには見当たらなかった。config-managementのBR4.1(メニュー階層の循環参照禁止)はツリー全体を前提としており、テーブル単位のタブだけでは管理しきれない。

- A. 専用のツリー編集画面を新設する。設定管理画面(10.)の「メニュー」タブは廃止し、代わりにこの画面へのリンクを置く
- B. 設定管理画面(10.)の「メニュー」タブを拡張し、フォルダノードの作成・全体ツリー表示も行えるようにする
- X. Other (please specify)

[Answer]: A(採用済み)。mockups.mdに14. メニュー管理画面(ツリー表示・フォルダ/グループノードの追加・ノードごとの名称変更/削除・ドラッグ&ドロップまたは上下ボタンでの並べ替え)を新設し、設定管理画面(10.)の「メニュー」タブは廃止して14.へのリンクに置き換えた。既存のcontract-summary.md #15(GET/POST /api/admin/menu-items、PUT/DELETE /api/admin/menu-items/{id})で実装可能であり、契約側の追加変更は不要。

## Consolidated Summary Confirmation

Q1・Q2の回答を踏まえ、frontend-admin Unitのfunctional-design成果物を以下の内容で確定します。

**上流成果物の修正(既に反映済み)**: mockups.mdに14. メニュー管理画面を新設し、10.の「メニュー」タブを廃止・7a/7eに名称変更・削除ボタンを追加した。permission Unitのfunctional-designを再オープンし、BR1.3〜BR1.5(ロール・グループの名称変更・削除)を新設、contract-summary.mdにPUT/DELETE /api/admin/roles/{roleId}・/api/admin/groups/{groupId}を追加した(いずれもコミット済み)。

**成果物の構成(functional-spec.md、UI Unitのためentities.md/rules.mdなし)**: 以下のワークフローを定義する。
1. 権限管理: ロール一覧・作成・名称変更・削除(7a)/ロール編集(テーブル権限・カラム権限、7b)/ロール割り当て(7c)/グループ管理・作成・名称変更・削除(7e)
2. 監査ログ画面(12): 参照・絞り込み・エクスポート・保持期間超過分の削除・保持日数設定変更
3. アカウント管理画面群(13a/13b): 一覧・新規作成・編集・無効化
4. 設定管理画面(10): 基本(DB接続先)・検索条件・一覧表示・編集対象外・バリデーション・フォーム部品・論理表示名の各タブ
5. スキーマ取り込み画面(11): 接続テスト・スキーマ一覧取得・取り込みプレビュー・取り込み確定
6. 設定エクスポート/インポート画面(8)
7. メニュー管理画面(14): ツリー表示・ノード追加・名称変更・削除・並べ替え
8. 管理者ゲーティング(横断的関心事): 上記すべての画面・サイドナビ項目は、isAdminクレームを持たない利用者に対しては非表示、URL直接アクセス時はアクセス拒否エラーを表示する[FR5.5][FR5.6]

**トレーサビリティ(traceability.json)**: upstream_ids = FR1.1〜FR1.6, FR2.1〜FR2.6, FR2.3.1, FR5.1〜FR5.6, FR6.4.1〜FR6.4.4, FR7.1〜FR7.5(unit-of-work.md U10の責務表記と一致。FR7.1・FR7.2はaudit-log(U8)自身の記録受付であり、frontend-adminはFR7.3〜FR7.5(参照・エクスポート・削除)のみ画面を提供するが、Unit定義の範囲表記に合わせてtraceabilityの参照対象としては触れておく)。

**frontend-components.md**: design-system-mapping.md・interaction-spec.mdに基づき、既存コンポーネント(AppShell、Table、TextField、Select、Textarea、Checkbox、Switch、RadioGroup、ConfirmDialog、Tabs、Accordion)と、新規/確認中コンポーネント(Modal、CheckableList、MatrixGrid、AuditLogTable、AccountEditForm、ConfigTabPanel、SchemaImportPreview)に加え、今回新設するMenuTree(ツリー表示・ドラッグ&ドロップ/上下ボタン並べ替え、メニュー管理画面14専用)を文書化する。

[Answer]: Looks correct

## Q3. iteration 1レビュー(NOT-READY、Critical 1件・Major 5件・Minor 1件)への対応記録

iteration 1レビューで検出された以下の6件のうち5件を実際に修正し、1件(R-07)は繰延べとした:

- **R-01(Critical、修正済み)**: アカウント無効化(ワークフロー6手順4)の実装がcontract-summary.mdの実際の契約(DELETE=論理無効化、PUT=プロフィール編集)と逆になっていた(PUTでStatus変更としていた)。ワークフロー6を、PUTは氏名・メールアドレス・ロールの編集のみ、DELETEが無効化、という契約どおりの対応に修正した。mockups.md 13bのStatus表示も、編集フォームと同じSaveボタンで送信するラジオボタンから、読み取り専用表示+独立した「無効化する」ボタンに変更した。
- **R-02(Major、修正済み)**: 設定管理画面(10)がテーブル一覧取得(`GET /api/admin/table-configs`)を経由せずテーブル詳細取得のみを行っており、テーブル選択肢自体の構成方法が未記載だった。ワークフロー7に一覧取得の手順を追加した。
- **R-03(Major、修正済み)**: 契約上「frontend-admin向け」と明記された`POST /api/admin/config/cache/clear`(FR2.6)をどの画面からも呼び出しておらず、traceability.jsonもFR2.6をN/Aとしていた。ワークフロー7に「キャッシュをクリア」操作を追加し、traceability.jsonのFR2.6をOKに変更した。
- **R-04(Major、修正済み)**: メニュー管理画面(14)手順5が循環参照時の400を主張していたが、contract-summary.mdのmenu-items系エンドポイントには成功レスポンスしか定義されていなかった。契約に400(循環参照)・404(対象なし)レスポンスを追加した。
- **R-05(Major、修正済み)**: 設定管理画面(10)手順3がDB接続先削除時の409(参照が残っている場合)を主張していたが、契約には204のみ定義されていた。契約に409・404レスポンスを追加した。
- **R-06(Major、修正済み)**: unit-of-work.mdのU10責務行が「切り替え」をfrontend-adminの責務として列挙しており、unit-of-work-story-map.mdのFR5.4行(frontend-core側)と矛盾していた。unit-of-work.mdの記述を訂正し、ロール切り替えUI自体はfrontend-core(U9)の責務であることを明記した。
- **R-07(Minor、繰延べ)**: permission契約にロール割り当ての個別解除(unassign)用エンドポイントが存在しない。ワークフロー3(ロール割り当て)は割り当てのみを扱い、解除機能は現時点で提供しない。permission Unitは既に完了済みのため、この対応はfunctional-designステージ終了ゲートへの繰延べ事項として記録する。

[Answer]: 上記を反映済み。Looks correct
