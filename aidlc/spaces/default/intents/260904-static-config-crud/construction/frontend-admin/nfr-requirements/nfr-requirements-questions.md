# NFR Requirements Questions: frontend-admin

requirements.mdのNFR1〜NFR9は本プロジェクト(自宅サーバ1台・個人利用中心)の運用規模に即して既に確定済みであり、frontend-admin固有に新たな数値目標は追加しない。frontend-adminはUI Unit(kind: ui)のため、本ステージで作成する成果物はperformance-requirements.md・security-requirements.md・tech-stack-decisions.md・traceability.jsonの4件のみとする(scalability/reliability/observability-requirements.mdは対象外)。functional-design(functional-spec.md、10ワークフロー)で既に確定済みの内容(管理者ゲーティング、各バックエンドUnitへの委譲呼び出し)をNFRx.yとして具体化するのみとする。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

frontend-admin Unitのnfr-requirements成果物を以下の内容で確定します。

**performance-requirements.md**: NFR1(厳密な数値目標なし、著しい遅延の兆候があれば別途検証)を踏襲。監査ログ画面・アカウント一覧・テーブル設定一覧はいずれもバックエンド側のページネーション(page/size)を利用し、一括で大量データを取得しない。

**security-requirements.md**: 全画面(ロール・グループ・監査ログ・アカウント管理・設定管理・スキーマ取り込み・エクスポート/インポート・メニュー管理)はisAdminクレームを持つ利用者のみ表示・アクセス可能とする(functional-spec.md「管理者ゲーティング」節)。frontend-admin自身は認可判定ロジックを持たず、各画面の初回データ取得APIが返す403に応じてエラー表示へ切り替えるのみである(判定はバックエンド側の責務)。React/TSXの標準エスケープにより、業務DB由来の識別子文字列(テーブル名・カラム名等、利用者が直接入力可能な値ではないが表示対象となる)を画面に描画する際のXSSを防ぐ。

**tech-stack-decisions.md**: React/TSX + 自作デザインシステムmake-you-chic-ui(team.md Code Style)、Prettier(フォーマッタ)+ ESLint(リンタ)を踏襲する。

**traceability.json**: upstream_ids = NFR1, NFR3(OK、部分的。UIからのエラー可観測性は各画面のエラー表示に限定)。NFR2・NFR4〜NFR9はN/A(横断方針・他Unit担当、またはUI Unitに適用対象がない)。

[Answer]: Looks correct
