# Units Generation — Questions

domain-design/components.mdの7コンポーネントを、実装・構築(Construction)単位である「Unit」にどう束ねるかを確認する。デプロイ形態(単一実行可能WAR)や技術スタックは既に確定しているため、ここでは「どの単位で作業を分割し、どの順序制約を持つか」だけを扱う(実装着手順の経済的な判断はDelivery Planningで行う)。

## Q1. Unitの境界戦略は、どれに近いイメージですか?

A. domain-designの7コンポーネント(SchemaIngestion/ConfigManagement/DynamicDataAccess/Permission/Account/Notification/AuditLog)にほぼ1:1で対応させ、それぞれ独立したUnitとする(トレーサビリティが明確。推奨)
B. 関連するコンポーネントをまとめて、より粗い単位(例: 「設定系」「実行系」「アカウント系」の3Unit)にする
C. Not yet defined
X. Other (please specify)

[Answer]: A. domain-designの7コンポーネントに1:1対応

## Q2. フロントエンド(React/TSX SPA)と、単一実行可能WARへのビルド配線(Gradle bootWar等)は、それぞれ独立したUnitとして扱いますか?

A. はい。フロントエンド(kind: ui)とビルド配線(kind: packaging)を、バックエンドの各Unitとは別の独立したUnitとする(推奨)
B. フロントエンドはバックエンドの各Unitに含めて扱う(独立させない)
C. Not yet defined
X. Other (please specify)

[Answer]: A. はい、独立させる

## Q3. Unit間の依存順序について、厳密なトポロジカル順序(依存先が完成してから着手)のみを許容しますか、それとも独立したUnit同士の並行着手を許容しますか?

A. 独立したUnit同士(依存関係のないもの)は並行着手を許容する(推奨。ただし開発者一人体制のため実際には順次進めることになる)
B. 厳密なトポロジカル順序のみ(依存先が完成するまで着手しない)
C. Not yet defined
X. Other (please specify)

[Answer]: A. 並行着手を許容

## Q4. Unit間の連携(統合ポイント)は、単一実行可能WAR内でのJavaのメソッド呼び出し(プロセス内呼び出し)を基本とする、という理解でよいですか?(ネットワーク越しのAPI呼び出しではない)

A. その理解でよい(プロセス内呼び出し。AccountComponent→NotificationComponentのようなイベント連携もSpringのアプリケーション内イベント機構を使う)
B. 一部のUnit間は将来的な分離を見据えてネットワークAPI(REST等)で連携させたい
C. Not yet defined
X. Other (please specify)

[Answer]: A. その理解でよい

## Q5. 全Unitのデプロイ形態は「単一実行可能WARに組み込み(embedded)」で統一する、という理解でよいですか?

A. その理解でよい(例外なし)
B. 一部のUnitは独立してデプロイしたい
C. Not yet defined
X. Other (please specify)

[Answer]: A. その理解でよい

## Q6. Plan Approvalでの追加ヒアリング: Accountコンポーネント・フロントエンドの分割について

Q1〜Q5の回答をもとに提示した分解計画に対し、「コンポーネントを複数のUnitに分割することも許容する」との回答があった。これを受け、以下の分割方針を提案する。

- Accountコンポーネントは、auth(ログイン・トークン発行・ログイン試行制限)とaccount-management(管理者によるアカウント作成・一覧・編集・無効化)の2Unitに分割する。パスワード忘れ対応・アカウント登録完了・自己サービスでの情報変更等のセルフサービス系フローはauth Unitが担う。両Unitは同一のAccountエンティティ(永続化スキーマ)を共有し、account-managementはauthが定める境界を踏襲する。
- フロントエンド(kind: ui)は、frontend-core(非管理者画面: ログイン・トップ・一覧・詳細・編集・FK検索・アカウント自己サービス)とfrontend-admin(管理者専用画面: 設定管理・スキーマ取り込み・権限管理・アカウント管理・監査ログ)の2Unitに分割する。
- 合計Unit数は11(schema-ingestion, config-management, permission, dynamic-data-access, auth, account-management, notification, audit-log, frontend-core, frontend-admin, packaging)。

A. この分割方針で進める
B. 分割せず、当初の9Unit構成のままにする
C. Not yet defined
X. Other (please specify)

[Answer]: A. この分割方針で進める

## Consolidated Summary Confirmation

- Unit境界戦略はdomain-designの7コンポーネントに1:1対応させることを基本とし、Accountコンポーネントはauth(ログイン・トークン・ロック)とaccount-management(管理者によるアカウント管理)の2Unitに分割する
- フロントエンドはfrontend-core(非管理者画面)とfrontend-admin(管理者専用画面)の2Unitに分割し、ビルド配線は独立したpackaging Unitとする
- 合計Unit数は11。バックエンドの各Unitはkind未指定(フル設計マトリクス適用)、frontend-core/frontend-adminはkind: ui、packagingはkind: packaging
- Unit間の依存順序は、独立したUnit同士の並行着手を許容する
- Unit間の連携はプロセス内呼び出しを基本とし、Account系(auth/account-management)からNotificationへの連携はSpringのイベント機構によるイベント購読とする
- 全Unitのデプロイ形態は単一実行可能WARへの組み込み(embedded)で統一する

Does this all look correct before I generate the unit artifacts?

A. Looks correct
B. Request changes

[Answer]: Looks correct
