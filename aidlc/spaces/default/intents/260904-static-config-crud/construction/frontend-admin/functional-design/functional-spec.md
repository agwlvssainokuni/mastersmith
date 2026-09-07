# Functional Specification: frontend-admin

frontend-adminは、domain-design/components.mdの定義どおり、管理者ロール(isAdmin)を持つ利用者のみが使う画面群(React/TSX、make-you-chic-ui)であり、永続エンティティ・業務ルールを自身では持たない(entities.md/rules.mdはUI Unitのため作成しない)。各画面は、対応するバックエンドUnitのREST契約を呼び出すクライアントとして振る舞う。画面のレイアウト・状態・アクセシビリティの詳細は、inception/refined-mockups/mockups.md(10〜14)・wireframes.md(7a〜7e)・interaction-spec.md・design-system-mapping.mdを参照する。

## ワークフロー

### 1. ロール一覧・作成・名称変更・削除(7a)

1. 画面表示時、`GET /api/admin/roles`(契約#16)でロール一覧(name・description)を取得する。
2. 「+ New」でロール作成フォームを開き、`POST /api/admin/roles`で新規ロールを作成する。name重複時は400(RFC 7807)をフォーム上部に表示する(permission BR1.1)。
3. 各行の「名称変更」ボタンから小モーダルを開き、`PUT /api/admin/roles/{roleId}`で名称を変更する(permission BR1.3)。name重複時は400、対象が既に存在しない場合は404を表示する。
4. 各行の「削除」ボタンから確認ダイアログ(ConfirmDialog)を開き、確認後`DELETE /api/admin/roles/{roleId}`を呼び出す(permission BR1.4)。当該ロールに割り当て(RoleAssignment)が残っている場合は409が返り、「このロールは利用者またはグループに割り当てられているため削除できません」を表示する。
5. 行を選択すると2.のロール編集画面(7b)へ遷移する。「割り当て」ボタンで3.のロール割り当て画面(7c)へ、「グループ」ボタンで4.のグループ管理画面(7e)へ遷移する。

### 2. ロール編集(テーブル権限・カラム権限)(7b)

1. 画面表示時、`GET /api/admin/roles/{roleId}/table-permissions`(契約#16)でテーブル単位権限(canList/canView/canCreate/canEdit/canDelete)のマトリクスを取得する。
2. 「テーブル権限」タブでのチェック操作後、`PUT /api/admin/roles/{roleId}/table-permissions`で保存する。
3. 「カラム権限」タブに切り替えると、対象テーブル選択に応じて`GET /api/admin/roles/{roleId}/column-permissions`でカラム単位権限(accessLevel: editable/readonly/hidden)を取得する。
4. ラジオ操作後、`PUT /api/admin/roles/{roleId}/column-permissions`で保存する。
5. 保存失敗時はタブ上部にエラーメッセージを表示する。

### 3. ロール割り当て(7c)

1. 「Assign to」でUser/Groupを選択し、対象(Account名またはGroup名)をセレクトで選ぶ。
2. 割り当てるロールを複数選択可能なチェックボックスで指定する。
3. `POST /api/admin/roles/{roleId}/assignments`(契約#16)を呼び出す。assigneeTypeに応じてaccountId・groupIdのいずれか一方のみを送信する(permission BR3.1)。既に割り当て済みの組み合わせは冪等に扱われる(permission BR3.3、エラーにならない)。対象ユーザ/グループ/ロールが存在しない場合は404を表示する。

### 4. グループ管理(一覧・作成・名称変更・削除・メンバー管理)(7e)

1. 画面表示時、`GET /api/admin/groups`(契約#16)でグループ一覧を取得する。
2. 「+ New」で`POST /api/admin/groups`によりグループを作成する。name重複時は400を表示する(permission BR1.1)。
3. 各グループ行の「名称変更」ボタンから、`PUT /api/admin/groups/{groupId}`で名称を変更する(permission BR1.3)。
4. 各グループ行の「削除」ボタンから確認ダイアログを開き、確認後`DELETE /api/admin/groups/{groupId}`を呼び出す(permission BR1.5)。当該グループにロール割り当て(assigneeType=group)が残っている場合は409が返り、「このグループにはロールが割り当てられているため削除できません」を表示する。
5. グループ行を展開(アコーディオン)すると、`GET /api/admin/groups/{groupId}/members`でメンバー一覧を取得する。
6. メンバー追加は`POST /api/admin/groups/{groupId}/members`(既に所属済みのAccountを再度追加した場合は冪等、permission BR1.2)、除去は`DELETE /api/admin/groups/{groupId}/members?accountId=<accountId>`を呼び出す。

### 5. 監査ログ画面(12)

1. 画面表示時、`GET /api/admin/audit-log`(契約#18)をpage/size/sortとともに呼び出し、一覧をAuditLogTableコンポーネントで表示する。
2. Search欄・Filter(操作種別/利用者/期間)の指定に応じて、`actionType`・`actorAccountId`・`occurredAtFrom`・`occurredAtTo`・`q`(自由文字列検索)のクエリパラメータを付与して再取得する(audit-log BR2.1・BR2.2、iteration 2レビューR-09フォローで契約に追加された各パラメータに対応)。
3. 「Export」ボタンで、現在の絞り込み条件と`format`(csv/json)を付与して`GET /api/admin/audit-log/export`を呼び出す。formatがcsv/json以外の場合の400は通常発生しない(画面側でSelect限定のため)。
4. 画面表示時、`GET /api/admin/audit-log/settings`で現在のretentionDays(既定365)を取得し、削除確認ダイアログの日数入力欄の初期値とする。
5. 「n日超過分を削除」ボタンから確認ダイアログを開き、確認した日数(初期値のまま、または上書きした値)で`DELETE /api/admin/audit-log?olderThanDays=<日数>`を呼び出す。1未満または整数でない場合は画面側でバリデーションし、契約上も400(RFC 7807)が返る(audit-log BR4.3)。
6. 保持日数の設定自体を変更する場合は、`PUT /api/admin/audit-log/settings`を呼び出す(この画面では別途の設定変更導線として提供する。1未満の場合は400)。

### 6. アカウント管理画面群(13a/13b)

1. 一覧画面(13a)表示時、`GET /api/admin/accounts`(契約#17)で氏名・メールアドレス・ステータスの一覧を取得する。
2. 「+ New」でアカウント編集フォーム(AccountEditForm、13b)を新規作成モードで開く。氏名・メールアドレス・業務データロール(複数選択チェックボックス)・管理者権限(独立したラジオボタン、業務データロールとは別モデル)を入力し、`POST /api/admin/accounts`(契約#17)で作成する。email重複時は409を表示する(account-management BR1.2)。作成成功時、対象利用者へアカウント作成通知メールが送信される(account-management→notification、FR6.4.1)。新規作成時はStatusを表示せず、常に有効として作成する。
3. 一覧の行選択で13bを編集モードで開く。`GET /api/admin/accounts/{id}`で詳細を取得し、氏名・メールアドレス・割り当てロールの変更は`PUT /api/admin/accounts/{id}`で保存する(account-management BR3.1・BR3.2、iteration 1レビューR-01フォロー: PUTは氏名・メールアドレス・ロールの編集のみを扱い、Statusは変更しない)。
4. 「無効化」ボタンから確認ダイアログを開き、確認後`DELETE /api/admin/accounts/{id}`を呼び出す(account-management BR4.1・BR4.2、iteration 1レビューR-01フォロー: 契約上のDELETEは物理削除ではなく論理無効化を意味する。204が返る)。無効化すると当該アカウントの有効なリフレッシュトークンが一括失効し、ログインできなくなる。アカウントレコード自体は保持され、一覧画面には引き続き「無効化済」ステータスで表示される。
5. バリデーションエラーは各フィールド直下、保存失敗はフォーム上部にエラーメッセージを表示する。

### 7. 設定管理画面(10)

1. 画面表示時、`GET /api/admin/table-configs`(契約#15、page/size/sort対応)でテーブル一覧を取得し、対象テーブルの選択肢(サイドナビまたはセレクト)を構成する(iteration 1レビューR-02フォロー: 一覧取得を経由せず特定テーブルの詳細取得のみを行うと、選択肢自体が構成できないため明記した)。
2. 対象テーブル選択後、`GET /api/admin/table-configs/{tableId}`(契約#15)でテーブル設定の詳細(検索条件・一覧表示項目・編集対象外項目・バリデーション・フォーム部品・論理表示名・外部キー関係・代表表示列)を取得する。
3. 「基本」タブでは、対象テーブルが参照するDB接続先設定を`GET /api/admin/db-connections`・`POST /api/admin/db-connections`・`PUT /api/admin/db-connections/{id}`・`DELETE /api/admin/db-connections/{id}`で管理する。削除時、参照するTableConfigが存在する場合は409が返る(config-management、iteration 1レビューR-05フォローで契約に409レスポンスを明記)。
4. 「検索条件」「一覧表示」「編集対象外」「バリデーション」「フォーム部品」「論理表示名」の各タブは、ConfigTabPanelコンポーネントでカラムを行とする設定マトリクスとして表示・編集し、`PUT /api/admin/table-configs/{tableId}`で保存する。
5. 「メニューでの表示位置を編集」リンクから、対象テーブルのメニューノードをハイライトした状態でメニュー管理画面(14、ワークフロー10)を開く(テーブル単位のタブではメニュー階層全体を管理できないため、専用画面へ委譲する設計とした)。
6. 画面上部(または「基本」タブ)の「キャッシュをクリア」ボタンから、`POST /api/admin/config/cache/clear`(契約#15、FR2.6)を呼び出す。設定変更後に反映が即座に見えない場合の明示的なクリア操作として提供する(iteration 1レビューR-03フォロー: 契約上「frontend-admin向け」と明記されたエンドポイントを、どの画面からも呼び出していなかったため追加した)。
7. スキーマ取り込み未実施の場合(テーブルが1件も存在しない場合)は、「まだテーブルが取り込まれていません」+スキーマ取り込み画面(11、ワークフロー8)への案内を表示する。

### 8. スキーマ取り込み画面(11)

1. 画面表示時、`GET /api/admin/db-connections`(契約#15)でDB接続先設定の選択肢を取得する。DB接続先が1件も設定されていない場合は「接続先を設定してください」+設定管理画面(10)の「基本」タブへの案内を表示する。
2. 「接続テスト」ボタンで`POST /api/admin/schema-ingestion/connection-test`(契約#14、iteration 1レビューR-01フォローで新設された専用エンドポイント)を呼び出す。204で成功、失敗時は「接続に失敗しました」+詳細メッセージ(500の場合)を表示する。
3. 接続先選択後、`GET /api/admin/schema-ingestion/schemas`(契約#14)で取り込み対象として選択可能なスキーマ/データベース一覧を取得する。
4. 「スキーマ取り込み」ボタンでスキーマを選択し、`POST /api/admin/schema-ingestion/preview`(契約#14)でテーブル一覧のプレビュー(SchemaImportPreviewコンポーネント)を取得する。ビューは「PK: なし(view)」と明示する。
5. チェックボックスで選択したテーブルについて、「選択したテーブルを取り込む」ボタンで`POST /api/admin/table-configs/import-from-schema`(契約#15)を呼び出し、選択テーブルのTableConfigを一括作成する。作成後は設定管理画面(10)へ案内する。

### 9. 設定エクスポート/インポート画面(8)

1. 「Export current settings」ボタンで`POST /api/admin/config/export`(契約#15)を呼び出し、設定ファイルをダウンロードする。
2. ファイル選択後、「Import」ボタンで確認ダイアログ(現在の設定を上書きする旨)を表示する。確認後、`POST /api/admin/config/import`を呼び出す。
3. インポートが不整合(不正形式・スキーマ不一致・必須項目欠落)を含む場合、409(RFC 7807)が返り、errors配列の内容を画面に表示する。設定全体は適用されない(config-management FR2.3.1・BR5.3)。
4. インポート成功時は「設定を反映しました」を表示する。

### 10. メニュー管理画面(14、iteration 1レビューより新設)

1. 画面表示時、`GET /api/admin/menu-items`(契約#15)でメニュー階層全体(フォルダ/グループノード・テーブルノード)をツリー(MenuTreeコンポーネント)として取得・表示する。
2. 「+ フォルダ/グループを追加」ボタンで、`POST /api/admin/menu-items`によりtableId=nullのノードを作成する。
3. 各ノードの「編集」から、`PUT /api/admin/menu-items/{id}`でlabel(フォルダ/グループノードの名称、またはテーブルノードの表示名)・displayOrderを変更する。
4. 各ノードの「削除」から確認ダイアログを開き、確認後`DELETE /api/admin/menu-items/{id}`を呼び出す。フォルダ/グループノードの配下にノードが残っている場合は、移動先(親の直下へ繰り上げ)を確認するダイアログを経由する。テーブルノードの削除はメニューからの除去のみを意味し、対象テーブルのTableConfig自体は削除しない。
5. ドラッグ&ドロップ、またはキーボード操作(上下ボタン)でノードの並べ替え・親の変更を行い、`PUT /api/admin/menu-items/{id}`でparentMenuItemId・displayOrderを更新する。循環参照になる変更(config-management BR4.1)は400(RFC 7807)が返り、ツリー上部にエラーメッセージを表示する。

## 管理者ゲーティング(横断的関心事、FR5.5・FR5.6)

上記すべての画面、およびAppShellのサイドナビにおけるこれらの画面への導線は、アクセストークンのisAdminクレームを持つ利用者にのみ表示・アクセス可能とする。isAdminクレームを持たない利用者には、サイドナビの該当項目自体が表示されない。URLを直接指定してアクセスした場合は、各画面のerror状態として「この操作を行う権限がありません」を表示する(バックエンド側は403、frontend-coreとの共通実装パターン)。isAdminクレームの発行元はauth Unit(契約#19共有スキーマ、FR5.5)であり、frontend-admin自身は判定ロジックを持たず、各画面の初回データ取得APIが返す403に応じてエラー表示へ切り替えるのみである。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T02:25:33Z
**Iteration:** 1

### Findings

指摘なし(既知の繰延べ事項R-07・R-08を除く)

### Summary

frontend-adminのfunctional-spec.md記載の10ワークフロー(ロール一覧・編集・割り当て、グループ管理、監査ログ、アカウント管理、設定管理、スキーマ取り込み、エクスポート/インポート、メニュー管理)を、shared契約(inception/contract-design/contract-summary.md)の permission(#16)・audit-log(#18)・account-management(#17)・config-management(#15)・schema-ingestion(#14)・auth(#19共有スキーマ)の各契約と突き合わせて確認した。エンドポイント・クエリパラメータ・ステータスコード(400/404/409)・冪等性の記述はいずれも対応する契約と整合しており、新規のCritical/Major欠陥は検出しなかった。traceability.jsonのFR IDもrequirements-analysis/requirements.mdに遡及可能であることを確認した。既知の非ブロッキング事項(R-07: permission契約にロール割り当て取り消し専用エンドポイントなし、R-08: account-management契約#17のPUTがメール重複時の409を定義していない)は前回レビューでUnresolvedのまま申し送り済みであり、本イテレーションでは再指摘しない。内容は前回READY判定時から変更されていない。
