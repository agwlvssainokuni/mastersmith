# Bolt Plan — MasterSmith(マスタ管理アプリ)

**Bolt**とは、Construction フェーズで実行する1回のビルド単位を指す。1つ以上のUnit of Workを束ね、完了の定義(Definition of Done)と、そのBoltを完了させることで何が証明されるか(確信仮説)を持つ。本ドキュメントは、確定した14Boltの順序と内容を定義する(`delivery-planning-questions.md` Q1〜Q7の確定回答に基づく)。

## Bolt一覧サマリー

| Bolt | 内容 | 束ねるUnit | ウォーキングスケルトン | ゲート |
|---|---|---|---|---|
| 1 | Walking Skeleton | config-engine, permission-engine, user-management, authentication-service, list-engine, record-edit-engine, audit-logging, frontend-ui, packaging(9ユニット、最小実装) | ✅ | 単独・ゲート付き(ユーザー明示承認) |
| 2 | permission-engine完全実装 | permission-engine | — | 自律(承認は最終Bolt群のバッチゲート、`aidlc-state.md`参照) |
| 3 | audit-logging完全実装 | audit-logging | — | 自律 |
| 4 | config-engine完全実装 | config-engine | — | 自律 |
| 5 | schema-introspector | schema-introspector | — | 自律 |
| 6 | user-management完全実装 | user-management | — | 自律 |
| 7 | authentication-service完全実装 | authentication-service | — | 自律 |
| 8 | menu-navigation | menu-navigation | — | 自律 |
| 9 | data-import-export | data-import-export | — | 自律 |
| 10 | config-import-export | config-import-export | — | 自律 |
| 11 | list-engine完全実装 | list-engine | — | 自律 |
| 12 | record-edit-engine完全実装 | record-edit-engine | — | 自律 |
| 13 | frontend-ui完全実装 | frontend-ui | — | 自律 |
| 14 | packaging最終化 | packaging | — | 自律 |

Bolt1完了後の残りBolt(2〜14)は、`team-practices.md`のラダー回答(自律継続)に従い自律的に直列実行する。

## Bolt 1: Walking Skeleton(単独・ゲート付き)

- **束ねるUnit**: config-engine(U1), permission-engine(U3), user-management(U4), authentication-service(U5), list-engine(U10), record-edit-engine(U11), audit-logging(U7), frontend-ui(U12), packaging(U13)。いずれも本Boltでは以下の完了の定義を満たす最小実装のみを行い、各ユニットの要件定義書上の全機能を完成させるものではない(残りはBolt 2以降で完全実装する)。
- **ウォーキングスケルトン**: はい。Cockburnのウォーキングスケルトン(アーキテクチャの全レイヤーを貫く最小限のエンドツーエンド実装で、後続のBoltが機能を積み増していく土台とする)に該当する。
- **完了の定義(DoD)**:
  1. 単一の実行可能WAR(packaging)としてビルドされ、起動する
  2. 事前に登録された1ユーザー(単一ロール)がログインでき、アクセストークン・リフレッシュトークンが発行される(authentication-service)
  3. 1つのテスト用テーブルに対する設定(config-engine)が存在し、一覧画面・詳細編集画面がその設定駆動で表示される(list-engine, record-edit-engine)
  4. 当該テストテーブルに対し、1パターンの権限(例: FULL権限)でREAD/編集操作が実行でき、権限判定はサーバー側(permission-engine)で行われる
  5. 詳細編集画面での更新操作が監査ログに記録される(audit-logging)
  6. frontend-uiはログイン画面・ヘッダー(ロール表示)・一覧画面・詳細編集画面の最小限のレンダリングを提供する
- **確信仮説**: 「ブラウザ→REST API→権限判定エンジン→内部データ永続化→監査イベント記録という一連の経路が、単一の実行可能WARとして実際に動作する」ことを証明する。これにより、複数RDBMS方言吸収・単一WARパッケージング・設定駆動UIという本プロジェクト最大のアーキテクチャ上の賭けが早期に検証される。
- **想定デモ**: ブラウザから実際にログイン→一覧画面→詳細編集→保存という一連の操作を行い、監査ログ画面(簡易表示でよい)で記録を確認する。

## Bolt 2: permission-engine 完全実装

- **束ねるUnit**: permission-engine(U3)
- **DoD**: ロール単位の主権限(FULL/READ/NONE/指定なし)のスキーマ・テーブル・カラム階層への割当と階層継承解決(下位優先)、補助権限(CREATE/DELETE)のスキーマ・テーブル単位割当、権限昇格防止(自分自身への昇格を含む、権限管理者の明示的操作を経ない昇格を拒否)が実装され、`team-practices.md`が定める「主要な権限マトリクスの組み合わせを網羅するテーブル駆動テスト」に合格する。
- **確信仮説**: 「本プロジェクト最大のリスクと位置付けたRBAC階層継承・主権限/補助権限の解決ロジックが、要件定義書の定めるすべての組み合わせパターンで正しく動作する」ことを証明する。
- **想定デモ**: 権限マトリクステーブル駆動テストのレポート(全パターンPASS)。

## Bolt 3: audit-logging 完全実装

- **束ねるUnit**: audit-logging(U7)
- **DoD**: config-engine・record-edit-engine・permission-engine・user-management・config-import-export・data-import-exportが発行する全ドメインイベントを購読し、操作者・操作対象・操作種別・日時・変更前後の値を記録する。アプリケーションからのUPDATE/DELETE経路を持たない追記専用ストレージであることをテストで保証する。監査ログ閲覧画面向けAPI(C6)が動作する。
- **確信仮説**: 「監査ログが改ざん・削除不可能であり、かつ横断的な全操作を漏れなく記録する」ことを証明する(ただし`team-practices.md`により監査ログ完全性の網羅的検証テストはMVPスコープの必須要件ではない、意図的なスコープ判断)。
- **想定デモ**: 各種操作(設定変更・ユーザー招待・権限変更等)を行い、監査ログ閲覧画面で記録を確認する。追記専用であることをUPDATE/DELETE試行のテストで示す。

## Bolt 4: config-engine 完全実装

- **束ねるUnit**: config-engine(U1)
- **DoD**: PostgreSQL/MySQL/MariaDBの複数RDBMS方言吸収、設定定義の起動時fail fast検証、FK参照select/radioの動的名称解決、楽観ロック対象列の定義有無の管理が完全実装される。
- **確信仮説**: 「単一の設定モデルで複数RDBMS方言の差異を吸収できる」という、本プロジェクトの2番目に大きい技術的リスクを解消する。
- **想定デモ**: PostgreSQL/MySQL/MariaDBそれぞれに対して同一の設定定義で一覧・編集画面が動作することを確認する。不正な設定定義投入時にfail fastでエラーになることを確認する。

## Bolt 5: schema-introspector

- **束ねるUnit**: schema-introspector(U2)
- **DoD**: 対象RDBMSのメタデータ(テーブル/カラム/型/NULL可否/主キー/外部キー等)を読み取り、config-engineへ設定の初期ドラフトを生成する機能がアプリ本体に統合された形で動作する(外部ツール不要)。

## Bolt 6: user-management 完全実装

- **束ねるUnit**: user-management(U4)
- **DoD**: 招待メールによるユーザー登録・初回パスワード設定、ユーザー情報更新、無効化(リフレッシュトークン即時失効)、初期管理者アカウントの自動作成、パスワードのハッシュ化保存、ユーザー単位の表示設定(テーマ/フォントサイズ/言語)が完全実装される。SMTP送信・Mailpitでのローカル確認が動作する。

## Bolt 7: authentication-service 完全実装

- **束ねるUnit**: authentication-service(U5)
- **DoD**: アクセストークン(10分)・リフレッシュトークン(30分、いずれも`application.yml`設定可能)、複数デバイス同時ログイン、連続ログイン失敗によるアカウント一時ロック(`application.yml`方式)、複数ロール保持時のアクティブロール選択・セッション保持が完全実装される。

## Bolt 8: menu-navigation

- **束ねるUnit**: menu-navigation(U6)
- **DoD**: 業務メニューのN階層構成、管理メニュー構成、トップ画面のCard形式表示制御、権限のあるメニューが1件もない場合の案内表示が完全実装される。

## Bolt 9: data-import-export

- **束ねるUnit**: data-import-export(U8)
- **DoD**: 業務データ(行データ)のCSVエクスポート・インポート(行単位バリデーションエラー提示含む)が完全実装される。

## Bolt 10: config-import-export

- **束ねるUnit**: config-import-export(U9)
- **DoD**: スキーマ定義・メニュー構成・RBAC設定を含む設定一式のJSON export/importが完全実装され、インポート時のfail fast検証が動作する。

## Bolt 11: list-engine 完全実装

- **束ねるUnit**: list-engine(U10)
- **DoD**: 検索フォーム(主要条件+詳細検索)・ページング(10/20/50/100件)・ソート・一括削除・CSVエクスポート委譲・権限連動列非表示・CREATE権限連動ボタン非表示が完全実装される。

## Bolt 12: record-edit-engine 完全実装

- **束ねるUnit**: record-edit-engine(U11)
- **DoD**: 10種類の編集部品(1行テキスト/複数行テキスト/整数値/小数値/日付/日時/select/radio/switch/checkbox)、フィールド単位バリデーション、楽観ロック競合検出(対象列がある場合のみ)、権限連動の読取専用/非表示制御、CSVインポート委譲が完全実装される。

## Bolt 13: frontend-ui 完全実装

- **束ねるUnit**: frontend-ui(U12)
- **DoD**: Bolt1で未実装だった残り画面(ユーザ管理画面、監査ログ閲覧画面、業務メニュー設定画面、設定管理画面)、i18n(日英2言語)、[User]メニューでのテーマ/フォントサイズ/言語の即時反映、アクセシビリティ個別項目チェックリストが完全実装される。

## Bolt 14: packaging 最終化

- **束ねるUnit**: packaging(U13)
- **DoD**: GitHub Actionsによるビルド定義(FR13.1、実際のワークフロー自体はCI Pipelineステージ3.7で扱うが、WARパッケージング処理はここで完成させる)が最終化される。

## 依存整合性の検証

Bolt2〜14の順序は、各ユニットの完全実装Boltがその依存先ユニットの完全実装Bolt(またはBolt1のスケルトン実装)より後になるよう`unit-of-work-dependency.md`のDAGと突き合わせて検証済み(詳細は`risk-and-sequencing-rationale.md`参照)。
