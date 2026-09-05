# Units of Work: MasterSmith MVP

domain-design/components.mdの7コンポーネントを基に、units-generation-questions.md Q1〜Q6の回答に基づき11のUnitへ分解する。デプロイ形態(単一実行可能WARへのembedded)は全Unit共通(Q5)であり、Unit間の連携はプロセス内呼び出しを基本とする(Q4)。

## Unit一覧

| Unit ID | Unit名 | Directory | kind | 複雑度 |
|---|---|---|---|---|
| U1 | schema-ingestion | u1-schema-ingestion | (未指定) | M |
| U2 | config-management | u2-config-management | (未指定) | L |
| U3 | permission | u3-permission | (未指定) | M |
| U4 | dynamic-data-access | u4-dynamic-data-access | (未指定) | L |
| U5 | auth | u5-auth | (未指定) | M |
| U6 | account-management | u6-account-management | (未指定) | M |
| U7 | notification | u7-notification | (未指定) | S |
| U8 | audit-log | u8-audit-log | (未指定) | M |
| U9 | frontend-core | u9-frontend-core | ui | L |
| U10 | frontend-admin | u10-frontend-admin | ui | L |
| U11 | packaging | u11-packaging | packaging | S |

## Unit定義

### U1. schema-ingestion

- **境界**: domain-design SchemaIngestionComponentに対応。
- **責務**: 業務DB(PostgreSQL/MySQL/MariaDB)へ接続し、JDBC DatabaseMetaDataでテーブル・カラム・型・PK・FK・制約を取得、3種RDBMS間の差異を吸収・正規化する [FR1.1〜FR1.6]。
- **デプロイ形態**: embedded(単一実行可能WARに組み込み)。
- **実装メモ**: 永続状態を持たない、ステートレスな走査ロジック。呼び出し元(U2)から接続情報を受け取り結果を返す。スキーマ取り込み画面(U10 frontend-admin経由)が管理者ゲーティング対象である根拠について、FR5.5の文言はFR2.2(設定管理)・FR6.4.1〜FR6.4.4(アカウント管理)・FR7.3〜FR7.5(監査ログ)のみを列挙しFR1.x(スキーマ取り込み)を明示していない。取り込んだスキーマはFR2.2が既に管理者ゲーティング対象とするTableConfigの初期値になるため、設定管理の一部として拡張的に管理者ゲーティング対象とする(domain-design/decisions.md ADR-002を参照)。

### U2. config-management

- **境界**: domain-design ConfigManagementComponentに対応。
- **責務**: DB接続先設定・メニュー構成・検索条件・一覧表示項目・編集対象外項目・バリデーション・フォーム部品・論理表示名・FK関係/代表表示列という設定全体の保持・CRUD・エクスポート/インポート、静的設定駆動のためのキャッシュ管理 [FR2.1〜FR2.6、FR2.3.1]。
- **デプロイ形態**: embedded。
- **実装メモ**: U1を呼び出してスキーマを取り込む。設定変更はU8(audit-log)へ記録する。管理者ロールを持つ利用者のみが利用できる(U10のfrontend-adminから呼ばれる)。

### U3. permission

- **境界**: domain-design PermissionComponentに対応。
- **責務**: ロール・グループの定義、テーブル単位・カラム単位の業務データ権限の設定、ロールのユーザ/グループへの割り当てと切り替え [FR5.1〜FR5.4]。
- **デプロイ形態**: embedded。
- **実装メモ**: 管理者ゲーティング(FR5.5/FR5.6)とは別モデルであり、isAdminクレームを扱わない。

### U4. dynamic-data-access

- **境界**: domain-design DynamicDataAccessComponentに対応。
- **責務**: 設定駆動の業務データ一覧/詳細/編集(新規・更新)の実行、FK参照値の表示名解決とポップアップ検索、後勝ちの同時編集制御 [FR3.1〜FR3.6、FR4.1、FR4.2]。
- **デプロイ形態**: embedded。
- **実装メモ**: U2(有効な設定)・U3(権限確認)・U8(操作記録)を呼び出す。永続エンティティは持たない(業務データは対象RDBMSの動的な問い合わせ対象)。

### U5. auth

- **境界**: domain-design AccountComponentの一部(ログイン・トークン・ロック・セルフサービス系フロー)。account-management(U6)とAccountエンティティの永続化スキーマを共有する。
- **責務**: ID/パスワードによるログイン、ステートレスなアクセストークン(isAdminクレームを含む)・リフレッシュトークンの発行、ログイン試行回数制限(連続n回失敗でm秒ロック)、パスワード忘れ対応・アカウント登録完了・自己サービスでの氏名/パスワード/メールアドレス変更 [FR5.5、FR5.6、FR6.1〜FR6.3、FR6.6、NFR7]。
- **デプロイ形態**: embedded。
- **実装メモ**: U8(ログイン操作を記録)を呼び出す。U7(notification)はU5が発行するイベントを購読する(style: event)。

### U6. account-management

- **境界**: domain-design AccountComponentの一部(管理者によるアカウント管理)。U5(auth)とAccountエンティティの永続化スキーマを共有し、U5が定める境界を踏襲する。
- **責務**: 管理者によるアカウントの新規作成・一覧参照・編集・無効化(論理削除のみ、物理削除なし) [FR6.4.1〜FR6.4.4]。
- **デプロイ形態**: embedded。
- **実装メモ**: U5(共有スキーマの境界)・U8(操作記録)を呼び出す。U7(notification)はU6が発行するアカウント作成イベントを購読する(style: event)。管理者ロールを持つ利用者のみが利用できる(U10のfrontend-adminから呼ばれる)。

### U7. notification

- **境界**: domain-design NotificationComponentに対応。
- **責務**: アカウント作成通知・アカウント登録完了・アカウント情報変更通知・パスワード変更通知・パスワード忘れ対応・メールアドレス変更リクエストの6種のメールフロー、自作Mustacheエンジンによるテンプレート描画・送信 [FR6.4、FR6.5]。
- **デプロイ形態**: embedded。
- **実装メモ**: U5・U6が発行するライフサイクルイベントをSpringのアプリケーション内イベント機構で購読する。U5・U6への直接の呼び出し依存は持たない。

### U8. audit-log

- **境界**: domain-design AuditLogComponentに対応。
- **責務**: 設定変更・業務データ操作・アカウント操作の記録受付、条件絞り込み付きの参照、エクスポート、保持期間超過分の削除 [FR7.1〜FR7.5]。
- **デプロイ形態**: embedded。
- **実装メモ**: 管理者ロールを持つ利用者のみが利用できる(U10のfrontend-adminから呼ばれる)。

### U9. frontend-core

- **境界**: 管理者ロールを持たない利用者も使う画面群(make-you-chic-ui、React/TSX)。
- **責務**: ログイン画面、トップ画面、業務データの一覧・詳細・編集画面、FK参照ポップアップ検索、アカウント自己サービス画面(パスワード忘れ・登録完了・情報変更・メールアドレス変更確認)、複数ロール保有時のロール切替(Topbarのユーザーメニュー)[FR3.1〜FR3.6、FR4.1、FR4.2、FR5.4、FR6.1〜FR6.3、FR6.6]。
- **デプロイ形態**: embedded(ビルド成果物をU11でバックエンドの静的コンテンツとして取り込む)。
- **実装メモ**: U4(業務データ)・U5(認証・自己サービス)・U3(自身に割り当てられたロール一覧の取得・ロール切替)をREST API経由で呼び出す(ブラウザ-サーバ間はネットワーク境界であり、Q4のプロセス内呼び出しの対象外)。

### U10. frontend-admin

- **境界**: 管理者ロールを持つ利用者のみが使う画面群(make-you-chic-ui、React/TSX)。
- **責務**: 設定管理画面(タブ形式)、スキーマ取り込み画面、権限管理画面群(ロール一覧・編集・割り当て・切り替え・グループ管理)、アカウント管理画面群(一覧・編集)、監査ログ画面 [FR1.1〜FR1.6、FR2.1〜FR2.6、FR2.3.1、FR5.1〜FR5.6、FR6.4.1〜FR6.4.4、FR7.1〜FR7.5]。
- **デプロイ形態**: embedded。
- **実装メモ**: U1・U2・U3・U6・U8をREST API経由で呼び出す。アクセストークンのisAdminクレームで自身へのアクセスを制御する。

### U11. packaging

- **境界**: 二言語モノレポ(Gradleバックエンド+frontend/配下の独立したViteプロジェクト)を単一実行可能WARへ組み立てるビルド配線。
- **責務**: Gradleの`bootWar`ライフサイクルへフロントエンドのビルド成果物を組み込み、`./gradlew build`一発で単一デプロイ可能物を生成する。
- **デプロイ形態**: embedded(このUnit自体が最終的なデプロイ物を生成する)。
- **実装メモ**: 全Unitのビルド成果物に依存する最終組み立て工程。具体的な配線方式は未確定(evidence.mdの持ち越し事項)であり、本Unitで具体化する。

## Assumptions & Open Questions

- U5(auth)とU6(account-management)が共有するAccountエンティティの永続化スキーマの具体的な所有・マイグレーション責任分担は、Contract Design以降で具体化する **[assumption]**。
- U11(packaging)の具体的なビルド配線方式(フロントエンド成果物の取り込み方法)は、practices-discoveryのevidence.mdで持ち越された未確定事項であり、本ステージでは境界のみ定義しUnit内部の実装はContract Design/Functional Design以降で具体化する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-05T22:13:25Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | unit-of-work-dependency.md > 統合ポイント表(account-management → auth) | account-management→authのエッジは通常の呼び出しスタイルの実線として描かれているのに、統合ポイント表の説明が「共有スキーマ・呼び出しなし」となっており、文書自身の凡例(実線=呼び出し)と矛盾していた | 統合ポイント表の記述を、実際の同期呼び出し(authが公開するAccountリポジトリ/サービス経由)として書き換え、凡例と整合させる | Resolved |
| R-02 | Major | unit-of-work-dependency.md(YAML/mermaid)およびunit-of-work.md > U9 | ストーリーマップはFR5.4(ロール切替)をfrontend-coreに割り当てているのに、frontend-core→permissionの依存エッジがYAML・mermaid・U9の責務/実装メモのどこにも存在しなかった | frontend-core→permissionのエッジをYAML・mermaidに追加し、対応する統合ポイント行を追加、U9の責務・実装メモ・引用FRリストにFR5.4呼び出しを明記する | Resolved |
| R-03 | Minor | unit-of-work.md > U1(schema-ingestion) | FR5.5の文言はスキーマ取り込みを管理者ゲート対象の機能として列挙していないのに、アーキテクチャ(ドメイン設計由来)はスキーマ取り込み画面を管理者ゲート対象として扱っていた | U1の実装メモに、FR5.5の文言がスキーマ取り込みを明示していないこと、および取り込んだスキーマがFR2.2の管理者ゲート対象であるTableConfigの初期値になることを理由に拡張的に管理者ゲート対象とする旨(ADR-002参照)を明記する | Resolved |
| R-04 | Minor | unit-of-work-dependency.md > YAMLエッジスキーマ | YAMLエッジスキーマにはevent系エッジとcall系エッジを区別するstyleフィールドが存在しない | 対応不要(本ステージが要求する必須スキーマ(name/kind/depends_on)そのものであり、欠陥ではないため現状のまま是認) | Accepted risk |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| aidlc-sensor-required-sections (unit-of-work-dependency.md) | PASS — edge_block: "ok", 4 required H2 headings present | 新規エッジ追加後もYAMLエッジブロックは整形式で、必須セクションも揃っている |
| aidlc-sensor-upstream-coverage (unit-of-work.md) | PASS (no upstream contract file for this artifact type) | 影響なし |
| aidlc-sensor-traceability (traceability.json) | FAIL — 全FR IDに対し"target is not mapped in unit-of-work-story-map.md"、および"FR1"〜"FR7"の見出しレベルIDがgapとして検出 | ツール側の既知の制約であり本改訂の欠陥ではない。センサーのstoryAssignments()はUSx.y形式のID(`ID_PATTERNS.US`)専用のパターンで固定されており、本プロジェクトのようにuser-storiesステージがスコープ上SKIPされFR直接マッピング方式を採る場合、story-map表のFRセルを一切認識できず機械的に全件不一致になる。この挙動は今回のR-01/R-02/R-03修正の前後で変わらない、修正前から存在した構造的なツール制約であり、conductorの変更が引き起こしたものではない。人間可読の依存関係・統合ポイント・カバレッジ確認セクションおよびtraceability.jsonの手動突合では、41件のFRが漏れなくUnitへ割り当てられていることを確認済み |

### 手動再検証(依存グラフの整形式性)

- 11個のUnit名(schema-ingestion, config-management, permission, dynamic-data-access, auth, account-management, notification, audit-log, frontend-core, frontend-admin, packaging)はすべて一意。
- YAMLの`depends_on`に列挙される名前はすべて宣言済みのUnit名として解決する(未解決参照なし)。自己依存なし。
- トポロジカル順序: レベル0(schema-ingestion, permission, audit-log) → レベル1(config-management, auth) → レベル2(dynamic-data-access, account-management) → レベル3(notification, frontend-core, frontend-admin) → レベル4(packaging)。この順序は非循環であり、unit-of-work-dependency.mdの「並行開発の機会」表と一致する。
- `kind`値(ui, packaging, および未指定)はすべてステージ定義の許容集合(service | spec | ui | packaging | library)の範囲内。
- 新規追加されたfrontend-core→permissionエッジは既存のトポロジカル順序(frontend-coreはレベル3、permissionはレベル0)と矛盾せず、循環を生じさせていない。

### Summary

R-01・R-02・R-03はいずれも記述の書き換え・エッジ追加という形で実際に解消されており、新たな矛盾も生じていない。依存グラフは新エッジ追加後も一意名・解決可能な参照・非循環という整形式性を保っている。traceability sensorのFAILはFRベースのフォールバック経路に対するツール側の既知の制約であり、今回の改訂やこのステージの成果物自体の欠陥ではないため、READY判定を妨げるものではない。


