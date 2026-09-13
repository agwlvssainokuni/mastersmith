# Unit of Work — MasterSmith(マスタ管理アプリ)

`inception/domain-design/components.md`(コンポーネントカタログ・ADR)と`units-generation-questions.md`の確定回答(Q1=B: コンポーネント単位1:1、Q2=B: 10ユニット以上)に基づき、Construction フェーズで扱う Unit of Work を定義する。

## Unit一覧

| Unit ID | Directory | Unit名 | kind | 複雑度 |
|---|---|---|---|---|
| U1 | u1-config-engine | config-engine | service | L |
| U2 | u2-schema-introspector | schema-introspector | service | S |
| U3 | u3-permission-engine | permission-engine | service | L |
| U4 | u4-user-management | user-management | service | M |
| U5 | u5-authentication-service | authentication-service | service | M |
| U6 | u6-menu-navigation | menu-navigation | service | S |
| U7 | u7-audit-logging | audit-logging | service | M |
| U8 | u8-data-import-export | data-import-export | service | M |
| U9 | u9-config-import-export | config-import-export | service | M |
| U10 | u10-list-engine | list-engine | service | L |
| U11 | u11-record-edit-engine | record-edit-engine | service | XL |
| U12 | u12-frontend-ui | frontend-ui | ui | XL |
| U13 | u13-packaging | packaging | packaging | S |

## Unit定義

### U1: config-engine

- **境界**: `components.md`の`ConfigEngine`コンポーネントに1:1対応。
- **責務**: テーブル・カラム単位の表示設定(表示名/表示順/書式/編集部品/バリデーション/権限/表示可否)・スキーマ定義の保持、複数RDBMS方言(PostgreSQL/MySQL/MariaDB)の吸収、設定定義の起動時fail fast検証(FR1.1〜FR1.3)、FK参照select/radioの選択肢名称解決(FR1.5)、楽観ロック対象列の定義有無の管理。
- **デプロイモデル**: embedded(単一実行可能WARに統合)。
- **複雑度**: L — 複数RDBMS方言吸収とfail fast検証ロジックが中核であり、他の全サービスユニットの基盤となるため慎重な設計を要する。
- **実装上の注意・制約**: FR1.6(共通エンジン層への業務固有ハードコード禁止)・NFR8(保守性)により、本ユニットはテーブル名・カラム名を一切ハードコードしてはならない。内部設定DB(組込みDB、業務データ用RDBMSとは別接続)を利用する。

### U2: schema-introspector

- **境界**: `SchemaIntrospector`コンポーネントに1:1対応。
- **責務**: 対象RDBMSのメタデータ(テーブル/カラム/型/NULL可否/主キー/外部キー等)読み取りと、config-engineへの設定初期ドラフト生成(FR1.4)。FR1.4・`components.md`により「アプリ本体に統合された機能として提供し、外部ツールを必要としない」ため、frontend-ui(設定管理画面)に管理者向けの起動用API/ボタンを公開する(下記参照)。
- **デプロイモデル**: embedded。
- **複雑度**: S — 一過性の読み取り処理であり、config-engineへの書き込みインタフェースのみに依存する単純な処理。
- **実装上の注意・制約**: config-engineへの一方向依存を持つ。業務データ用RDBMSへの読み取り専用アクセスのみ。起動経路(FR1.4の「アプリ本体への統合」要件): frontend-ui(U12)の設定管理画面に「スキーマからドラフト生成」操作を設け、そこからschema-introspectorのAPIを呼び出す。schema-introspectorはCLIや外部ツールとしては提供しない(`unit-of-work-dependency.md`のfrontend-ui依存に追加済み)。

### U3: permission-engine

- **境界**: `PermissionEngine`コンポーネントに1:1対応。
- **責務**: ロール単位の権限割当(FR4.1)、主権限(FULL/READ/NONE/指定なし)の階層継承解決(FR4.3)、補助権限(CREATE/DELETE)の判定(FR4.4)、サーバー側での実効権限再検証(FR3.3)、権限昇格の防止(FR3.4)。
- **デプロイモデル**: embedded。
- **複雑度**: L — ほぼ全ユニットから横断的に呼び出される中核ロジックであり、NFR1(応答時間3秒以内)への配慮が必要。
- **実装上の注意・制約**: `## Mandated`(project.md)により、画面表示の出し分けだけに依存せず必ずサーバー側で実効権限を再検証しなければならない。権限昇格は権限管理者の明示的操作を経ずに許可してはならない。監査ログ閲覧画面のアクセス権限判定のため、`audit-logging`が本ユニット(permission-engine)へ同期依存する(依存の向きは`permission-engine → audit-logging`ではなく`audit-logging → permission-engine`である点に注意。依存関係は`unit-of-work-dependency.md`参照)。

### U4: user-management

- **境界**: `UserManagement`コンポーネントに1:1対応。
- **責務**: ユーザーの招待・登録・更新・無効化(FR2.1〜FR2.3)、初期管理者アカウントの自動作成(FR2.4)、パスワードのハッシュ化保存(FR2.5, FR2.6)、招待メール送信(FR2.8)、ユーザー単位の表示設定(テーマ/フォントサイズ/言語、FR9.1、FR10.1のユーザー単位切替手段)。
- **デプロイモデル**: embedded。
- **複雑度**: M — 招待フロー・メール送信・パスワードハッシュ化の組み合わせだが、いずれも標準的なパターン。
- **実装上の注意・制約**: パスワード等の認証情報は平文でログ・監査ログ・エラーメッセージに出力してはならない(`## Mandated`)。SMTPサーバー(招待メール送信)への外部依存を持つ。ローカル動作確認用にMailpitを用意する(FR2.8)。

### U5: authentication-service

- **境界**: `AuthenticationService`コンポーネントに1:1対応。
- **責務**: アクセストークン/リフレッシュトークンによるトークンベース認証(FR3.1)、複数デバイス同時ログイン許可(FR3.2)、連続ログイン失敗によるアカウント一時ロック(`application.yml`方式、FR2.7)、セッション単位のアクティブロール選択保持(FR4.2)。
- **デプロイモデル**: embedded。
- **複雑度**: M — リクエストごとに実行される高頻度パスであり、性能・ロック判定ロジックに注意を要する。
- **実装上の注意・制約**: FR2.7の要件定義書文言(「管理画面から設定可能」)と設計(`application.yml`方式)の不一致は既知のフォローアップ事項であり未解消(`inception/domain-design/decisions.md` ADR-004・`components.md`参照)。本ユニットの機能設計(Construction 3.1)で必ず要件定義書側の文言修正を検討すること。ロック中のログイン試行には通常の認証エラーと同一の汎用メッセージのみを返す。

### U6: menu-navigation

- **境界**: `MenuNavigation`コンポーネントに1:1対応。
- **責務**: 業務メニュー(N階層)・管理メニューの構成管理(FR7.1)、ログイン後トップ画面のCard形式表示制御(FR7.2)、空メニュー時の案内表示(FR7.3)。
- **デプロイモデル**: embedded。
- **複雑度**: S — 階層構造の保持と権限による表示可否フィルタリングのみ。
- **実装上の注意・制約**: 権限判定はpermission-engineに委譲する(自前で判定ロジックを持たない)。

### U7: audit-logging

- **境界**: `AuditLogging`コンポーネントに1:1対応。
- **責務**: 他ユニットが発行するドメインイベント(RecordUpdated, UserInvited, PermissionChanged等)の購読による監査記録(FR8.1)、追記専用(append-only)ストレージへの記録(FR8.2)、無期限保持(FR8.3)、監査ログ閲覧画面へのデータ提供(FR8.4)。
- **デプロイモデル**: embedded。
- **複雑度**: M — イベント購読の配線と追記専用ストレージ設計。
- **実装上の注意・制約**: `## Mandated`により、アプリケーションからのUPDATE/DELETE経路を持たない追記専用でなければならない。少なくとも操作者・操作対象・操作種別・日時・変更前後の値を記録する。監査ログ完全性の網羅的検証テストは本MVPスコープでは必須としない(`team.md`確定事項)。閲覧権限判定のためpermission-engineへ同期依存する(イベント発行元との間の意図的な循環については`unit-of-work-dependency.md`のRationaleを参照)。

### U8: data-import-export

- **境界**: `DataImportExport`コンポーネントに1:1対応。
- **責務**: 業務データ(行データ)のCSVエクスポート・インポート(FR12.1)、行単位のインポートバリデーションエラー提示。
- **デプロイモデル**: embedded。
- **複雑度**: M — CSV変換とバリデーションの組み合わせ。
- **実装上の注意・制約**: list-engine・record-edit-engineの一覧画面ツールバーから起動される委譲先として設計する(フロントエンドから直接呼び出さない)。config-engineからカラム定義・バリデーションルールを取得する。

### U9: config-import-export

- **境界**: `ConfigImportExport`コンポーネントに1:1対応。
- **責務**: スキーマ定義・メニュー構成・RBAC設定を含む設定一式のJSON export/import(FR11.1)、インポート時のfail fast検証(FR1.3の適用)。
- **デプロイモデル**: embedded。
- **複雑度**: M — 複数ユニット(config-engine/menu-navigation/permission-engine)の設定を集約する調整ロジック。
- **実装上の注意・制約**: 内部設定DBを正とするハイブリッド方式(FR11.2)。独立した「設定管理」画面から操作する。

### U10: list-engine

- **境界**: `ListEngine`コンポーネントに1:1対応。
- **責務**: 設定駆動の一覧画面(検索・ページング・ソート)生成(FR5.1)、ページサイズ選択(FR5.2)、CREATE権限に応じた「+ 新規作成」ボタン制御(FR5.3)、該当データなし/エラー時表示(FR5.4)、一覧チェックボックスによる一括削除、業務データCSVエクスポート(data-import-exportへ委譲)。
- **デプロイモデル**: embedded。
- **複雑度**: L — 検索・ページング・ソート・権限連動列非表示・CSVエクスポート委譲が組み合わさる。
- **実装上の注意・制約**: READ権限未満のカラムは列自体を非表示にする(FR4.5)。config-engine/permission-engine/authentication-service(アクティブロール取得)/data-import-exportに依存する。

### U11: record-edit-engine

- **境界**: `RecordEditEngine`コンポーネントに1:1対応。
- **責務**: 設定駆動のフォーム部品(1行テキスト/複数行テキスト/整数値/小数値/日付/日時/select/radio/switch/checkbox)による詳細・編集画面生成(FR6.1)、フォーカスアウト時インラインバリデーション(FR6.2)、楽観ロック競合検出(config-engineから対象テーブルの楽観ロック対象列有無を取得し、存在する場合のみ検出。存在しない場合は後勝ち、FR6.3)、権限に基づく読取専用/非表示制御(FR6.4)、業務データCSVインポート(data-import-exportへ委譲)。
- **デプロイモデル**: embedded。
- **複雑度**: XL — 10種類の編集部品・フィールド単位バリデーション・条件付き楽観ロック・権限連動制御が組み合わさり、本アプリの中核かつ最も複雑なユニット。
- **実装上の注意・制約**: 利用者向けのバリデーションエラーは開発者向けスタックトレースではなくフィールド単位のメッセージとして返す(`## Mandated`)。楽観ロック競合検出時は一覧画面へ遷移せず詳細・編集画面に留まる。業務データ作成・更新・削除のドメインイベントをaudit-loggingへ発行する(イベント発行、DAGには含めない。`unit-of-work-dependency.md`参照)。

### U12: frontend-ui

- **境界**: Domain Designのコンポーネントカタログには含まれないが、確定済み技術スタック(TypeScript + Vite + React、社内デザインシステムmake-you-chic-ui)により必要となる、単一のフロントエンドSPAユニット。config-engineが保持する設定に基づき、一覧・詳細編集・メニュー・管理系画面を汎用的にレンダリングする「設定駆動UIシェル」であり、業務テーブル固有のコンポーネントは持たない。
- **責務**: ログイン画面、業務メニュー・管理メニューのトップ画面(FR7.2)、一覧画面(list-engine API呼び出し)、詳細・編集画面(record-edit-engine API呼び出し)、ユーザ管理画面・監査ログ閲覧画面(FR8.4)、設定管理画面(config-import-export API呼び出し、および schema-introspector API呼び出しによる「スキーマからドラフト生成」操作、FR1.4の起動経路)、ヘッダーのロール選択UI(FR4.2)・[User]メニューでのテーマ/フォントサイズ/言語設定(FR9.1, FR10.1)、i18n(日英2言語、FR10.2の翻訳リソース配置)。
- **デプロイモデル**: embedded(ビルド成果物をSpring Bootの静的リソースとして同梱、CORS設定不要)。
- **複雑度**: XL — 設定駆動の汎用一覧/編集レンダリング、全画面共通のロール選択・表示設定・i18n基盤を担うため、record-edit-engineと並ぶ最大規模のユニット。
- **実装上の注意・制約**: 業務テーブル固有のテーブル名・カラム名をハードコードしない(FR1.6)。デザインシステムmake-you-chic-uiの実際のコンポーネント一覧が未確認の場合は想定コンポーネント名で記載し、実装フェーズで読み替える(`project.md`既存学習事項)。対応デバイスはデスクトップ+タブレット、キーボード操作可能・ラベル付与等の個別項目チェックリストでアクセシビリティに対応する(NFR6)。

### U13: packaging

- **境界**: Domain Designのコンポーネントカタログには含まれないが、確定済み技術スタック(実行可能WAR形式、フロントエンド同梱)を実現するために必要なビルド・パッケージングユニット。
- **責務**: frontend-uiのビルド成果物をSpring Boot(record-edit-engine等の全serviceユニットを含むバックエンド)の静的リソースとして同梱し、単一の実行可能WARを生成する(FR13.1の一部、GitHub Actionsビルド定義そのものは後続のCI Pipelineステージで扱う)。
- **デプロイモデル**: 該当なし(ビルド成果物そのものであり、実行時に稼働するコンポーネントではない)。
- **複雑度**: S — Gradleのマルチプロジェクト構成・フロントエンド成果物の静的リソース組み込みが中心。
- **実装上の注意・制約**: 本ユニットはFR13(CIパイプライン)の一部(WARパッケージングの定義)のみを担う。GitHub Actionsのワークフロー自体・OTELエクスポート確認用コンテナ環境(FR14.2)は後続のCI Pipeline / NFR設計ステージで具体化する(Domain Design ADR-007を踏襲)。
