# Functional Design Questions: frontend-core

frontend-coreが担う各画面(ログイン・トップ・一覧・詳細・編集・FK参照ポップアップ検索・アカウント自己サービス・ロール切替)は、requirements.md・contract-summary.md(#11 auth、#12 dynamic-data-access、#13 permission)・ideation/rough-mockups/wireframes.md(1〜6、7d、9)・inception/refined-mockups/mockups.md(1、9)・interaction-spec.md(FkReferencePicker、LoginLockoutNotice)・design-system-mapping.mdによって、既に画面遷移・API対応関係が具体的に確定している。dynamic-data-access・auth・permission Unitのfunctional-designも完了済みであり、frontend-core固有の新規に確定すべき論点は見当たらなかった(frontend-adminのようなメニュー管理画面の欠落に相当する契約・画面のギャップは発見しなかった)。そのため、Q1/Q2形式の個別設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

frontend-core Unitのfunctional-design成果物を以下の内容で確定します。

**成果物の構成(functional-spec.md、UI Unitのためentities.md/rules.mdなし)**: 以下のワークフローを定義する。
1. ログイン画面(1): ログイン・ロック中エラー表示(LoginLockoutNotice)
2. トップ画面(2): テーブル/メニューのカード一覧、管理者向けサイドナビ項目の出し分け(表示のみ、判定ロジックはfrontend-adminへの導線の有無で表現)
3. 一覧画面(3): 検索・絞り込み・ページング・ソート、主キーなしテーブル・ビューの編集導線非表示
4. 詳細画面(4): 全カラム表示、FK値の表示名解決
5. 編集画面(5): 新規作成/更新、FK入力のポップアップ検索連携(9)
6. FK参照ポップアップ検索(9): FkReferencePickerコンポーネント
7. アカウント自己サービス画面群(6): パスワード忘れ申請/再設定、アカウント登録完了、氏名・パスワード変更、メールアドレス変更確認
8. 複数ロール保有時のロール切替(7d、Topbarユーザーメニュー内)
9. ログアウト

**トレーサビリティ(traceability.json)**: upstream_ids = FR3.1〜FR3.4, FR3.6, FR4.1〜FR4.2, FR5.4, FR6.1〜FR6.3, FR6.6(unit-of-work.md U9の責務表記、unit-of-work-story-map.mdの個別FR割当と一致)。

**frontend-components.md**: design-system-mapping.md・interaction-spec.mdに基づき、既存コンポーネント(AppShell、Table、TextField、Textarea、Checkbox、RadioGroup、Select)と、確認中コンポーネント(FkReferencePicker、LoginLockoutNotice、Modal)を文書化する。自己サービス画面群(パスワード忘れ・登録完了・情報変更・メールアドレス変更確認)は、interaction-spec.mdに専用コンポーネントの定義がないため、編集画面(5)と同じ1カラムフォームレイアウト(TextField・Buttonの組み合わせ)を踏襲する汎用フォームとして扱う。

[Answer]: Looks correct

## Q2. トップ画面のメニュー取得手段(発見されたギャップ)

トップ画面(2.)は非管理者利用者を含む全利用者が使うが、config-managementの既存エンドポイント(#15)はすべてisAdminクレーム必須(config-management BR7.1)であり、非管理者利用者が自身のトップ画面のメニュー/テーブル一覧を取得する手段が契約上存在しなかった。

- A. 非管理者向けの新規GETをconfig-managementに追加する(呼び出しロールのcanList権限でフィルタ済みのメニュー階層を返す)
- B. ログイン応答にメニュー階層を含める(authがcontract#3経由でpermissionに問い合わせる必要があり、authの責務が肥大化する)
- X. Other (please specify)

[Answer]: A(採用済み)。contract-summary.mdに契約#22(config-management → permission、canList権限を持つtableId集合の取得)・契約#23(config-management → frontend-core、`GET /api/menu`)を新設した。unit-of-work.md・unit-of-work-dependency.mdも合わせて更新済み(config-management → permission、frontend-core → config-managementの依存エッジ追加、循環なしを確認)。ただし、config-management Unit自身は既に完了済みで、現在stage checkboxが[R](Revising)に固着しており、全ユニット完了までは再オープンによるビジネスルール追加ができない状態にある。そのため、config-management側の実際のrules.md/entities.md/functional-spec.md/traceability.jsonへの反映は、frontend-core・notification完了後にまとめて実施することとし、functional-designステージ終了ゲートへの繰延べ事項として記録する。frontend-core自身の成果物は、この契約(#22・#23)を前提に設計を進める。

[Answer]: 上記を反映済み。Looks correct

## Q3. iteration 1レビュー(NOT-READY、Major 3件・Minor 3件)への対応記録

iteration 1レビューで検出された以下の6件を実際に修正した:

- **R-01(Major、修正済み)**: traceability.jsonがFR3.5(後勝ち上書き、functional-spec.mdワークフロー5手順2が既に言及)を取りこぼしていた。upstream_ids・coverageにFR3.5を追加した。
- **R-02(Major、修正済み)**: ワークフロー1に、accessToken期限切れ(401)検知→`POST /api/auth/refresh`自動呼び出し→再試行、リフレッシュ自体が401の場合はログイン画面へ強制遷移、という一連のトークン自動延長フローが記述されていなかった。手順6として追加した。
- **R-03(Major、修正済み)**: TableConfig.columns[]由来のカラムメタデータ(isSearchable・accessLevel等)がどのAPI応答から取得されるか未記載だった。ワークフロー3手順1に、一覧・詳細・編集の各取得レスポンス自身に埋め込まれて返る旨を明記した。
- **R-04(Minor、修正済み)**: 冒頭段落のwireframes.md参照カッコに誤って画面9(mockups.md側)を含めていた。wireframes.mdの参照から「9」を削除した。
- **R-05(Minor、修正済み)**: ワークフロー6手順5が契約上定義されていない404を主張していた。「404となる」という記述を削除し、参照先未解決時はクライアント側判定のみで検索アイコン自体を表示しない(API呼び出し自体が発生しない)という、契約と矛盾しない記述に修正した。
- **R-06(Minor、修正済み)**: ワークフロー2・8にAPI呼び出し失敗時のエラー表示を追記した。

[Answer]: Looks correct
