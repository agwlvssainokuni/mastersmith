# Scope Document — MasterSmith(マスタ管理アプリ)

## In Scope(今回のワークフローで扱う)

### 設定基盤
- 設定の保持: ハイブリッド方式。管理画面から編集可能な内部設定DBを正とし、設定ファイルのexport/importにも対応する(Q1)
- スキーマ読み込み: アプリ本体が起動時に読み込む機能として実装する(独立した補助ツールとしては切り出さない)(Q2)
- 対象RDBMS: PostgreSQL/MySQL/MariaDBの複数対応([feasibility] Q3)
- FK参照によるselect/radioの選択肢は、名称解決のみ実行時に動的取得してよい(それ以外は静的設定に従う)(Q7)

### 画面・機能
- 一覧画面(検索フォーム+一覧表示+ページング+ソート)(Q3)
- 詳細・編集画面(バリデーション+フォーム部品)(Q3)
- メニュー・ナビゲーション: 業務メニュー配下にマスタメンテメニューをN階層、管理メニュー配下に管理者機能(業務メニュー設定、ユーザ管理、監査ログ管理)を配置。sidebar表示、パンくずリストなし(Q3, Q9)
- ユーザ管理: 登録(招待メールでパスワード・氏名設定)、更新、無効化、初期管理者登録(Q3)
- 監査ログ: ユーザ操作の記録・閲覧(Q3)
- 権限制御(RBAC): ロール単位で権限を割り当て、ロールはユーザまたはグループに割り当てる。複数ロールを持つユーザは操作時にロールを選択する。主権限(FULL/READ/NONE/指定なし)をスキーマ・テーブル・カラムの各階層に割り当て、指定なしは上位階層を継承し下位優先。補助権限(CREATE/DELETE、許可/禁止/指定なし)をスキーマ・テーブルに割り当てる(Q5)
- 表示可否(一覧/詳細にどの項目を表示するか)は、権限制御とは独立した設定軸として扱う(Q6)
- 多言語対応(表示名・バリデーションメッセージのi18nキー化)(Q8)

### 非機能・ビルド
- CIパイプライン: GitHub Actionsによるビルド定義。実行可能WAR(frontendを同梱し、Spring Bootから配信)を生成する([intent-capture] Q8, [feasibility] Q5)
- メトリクス・トレース・ログのOTEL基盤へのexport対応、構造化ログの出力([intent-capture] Q8)
- OTEL動作確認用のローカルコンテナ環境([intent-capture] Q8)

### 技術スタック(制約として確定済み、[feasibility] Q5)
- Backend: Java 25 + Spring Boot(最新) + Gradle(最新)
- Frontend: TypeScript + Vite + React、自前のデザインシステム(make-you-chic-ui)を使用(Q9)

## Out of Scope(今回のワークフローでは扱わない)

- 複数テーブルを1画面に合成する機能(マスタ+明細等)。将来拡張として先送り(Q4)
- 実環境へのデプロイ、環境構築、監視基盤の構築、性能検証([intent-capture] Q8)
- MasterMeisterとの連携・データ移行(MasterSmithは別アプリとして新規に立ち上げる、並行稼働。[intent-capture] Q9)
- 社内認証基盤(SSO/LDAP等)との連携([feasibility] Q1)

## Assumptions & Open Questions

None.
