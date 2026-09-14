# NFR Requirements Questions — schema-introspector (U2)

## Sources

- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/construction/schema-introspector/functional-design/functional-spec.md` — W1ワークフロー
- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/construction/schema-introspector/functional-design/rules.md` — BR2.1〜BR2.10
- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/inception/requirements-analysis/requirements.md` — NFR1〜NFR8

schema-introspectorは、管理者が設定管理画面から低頻度に実行する一過性の管理操作(スキーマからドラフト生成)であり、一覧/詳細画面のような高頻度アクセスパスとは性質が異なります。以下は、既存のNFR1〜NFR8だけでは本ユニット固有の目標値が決まらない事項です。

## Q1. 応答時間目標

NFR1は「一覧・詳細画面とも応答時間3秒以内(95パーセンタイル)」を定めていますが、schema-introspectorの「スキーマからドラフト生成」操作は管理者による低頻度実行であり、対象スキーマの規模(テーブル数・カラム数)次第では処理時間が変動します。この操作にどの応答時間目標を適用しますか。

- A. 一覧/詳細画面と同じ3秒以内(95パーセンタイル)を目標とする
- B. 管理操作用に緩和した目標(例: 30秒以内)を設定する(推奨。対象スキーマのテーブル数によっては3秒を超えうるため、非同期化までは不要だが余裕を持たせる)
- C. 明示的な時間目標は設定せず、タイムアウトなしで完了を待つ(対象スキーマ規模に依らず必ず完了させる)
- X. Other (please specify)

[Answer]: B. 管理操作用に緩和した目標(例: 30秒以内)を設定する(対象スキーマのテーブル数によっては3秒を超えうるため、非同期化までは不要だが余裕を持たせる)

## Q2. 対象スキーマの想定最大規模

タイムアウト設計・性能検証の基準として、1回のイントロスペクション実行が対象とする最大テーブル数・カラム数はどの程度を想定しますか。

- A. 数十テーブル程度(1テーブルあたり数十カラム)を主な想定とする(推奨。MasterSmithの想定利用規模=数十名程度の業務担当者向け業務データベースの規模感に対応)
- B. 数百テーブル以上の大規模スキーマも想定する(明示的な性能検証・分割実行の設計が必要になる)
- X. Other (please specify)

[Answer]: A. 数十テーブル程度(1テーブルあたり数十カラム)を主な想定とする(MasterSmithの想定利用規模=数十名程度の業務担当者向け業務データベースの規模感に対応)

## Q3. 可観測性(ログ・メトリクス)の最低要件

schema-introspectorはAuditLogging(監査ログ)の購読対象外(`components.md`のイベント発行元一覧に含まれない)ですが、NFR5(可観測性)により構造化ログ・メトリクスのOTELエクスポートは全ユニット共通で求められています。本操作に固有で最低限記録すべき情報は何ですか。

- A. 実行開始・完了(成功/失敗)・対象テーブル数・生成件数・所要時間を構造化ログ(INFO)として記録し、失敗時はERRORログに理由を含める(推奨)
- B. Aに加え、実行時間の分布をメトリクスとしても記録し、ダッシュボードで監視できるようにする
- X. Other (please specify)

[Answer]: A. 実行開始・完了(成功/失敗)・対象テーブル数・生成件数・所要時間を構造化ログ(INFO)として記録し、失敗時はERRORログに理由を含める

## Consolidated Summary Confirmation

以下の内容でschema-introspectorのNFR Requirements成果物(performance-requirements.md/security-requirements.md/scalability-requirements.md/reliability-requirements.md/observability-requirements.md/tech-stack-decisions.md/traceability.json)を生成します。

- Q1(応答時間目標): 管理操作用に緩和した目標(30秒以内)を設定する。
- Q2(想定最大規模): 数十テーブル程度(1テーブルあたり数十カラム)を主な想定とする。
- Q3(可観測性の最低要件): 実行開始・完了(成功/失敗)・対象テーブル数・生成件数・所要時間を構造化ログ(INFO)として記録し、失敗時はERRORログに理由を含める。

その他のNFRカテゴリ(セキュリティ・信頼性・国際化・アクセシビリティ等)は、既存のFunctional Design(BR2.1〜BR2.10)・NFR2/NFR8等の全ユニット共通方針で構造的に充足済み、またはUIを持たない本ユニットには該当しないものとして、追加の質問なしにtraceability.jsonでN/A/OK判定を行います。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
