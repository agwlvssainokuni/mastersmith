# NFR Design Questions — schema-introspector (U2)

## Sources

- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/construction/schema-introspector/nfr-requirements/*.md` — NFR1.1, NFR2.1〜NFR2.3, NFR3.1, NFR4.1〜NFR4.2, NFR5.1
- [scope] `aidlc/spaces/default/intents/260910-master-mgmt-app/construction/schema-introspector/functional-design/functional-spec.md` — W1

schema-introspectorのNFR要件はいずれも軽量(応答時間30秒、数十テーブル規模、fail-fast、構造化ログ)であり、キャッシュ・水平スケール等の複雑な設計判断は不要です。以下は、NFR Requirementsの目標値を実現する具体的な技術方式のうち、既存成果物だけでは決まらない事項です。

## Q1. 実行方式(同期/非同期)

NFR1.1(応答時間目標30秒以内)を踏まえ、「スキーマからドラフト生成」操作をどの実行方式で実現しますか。

- A. HTTPリクエストスレッド内での同期実行とする。30秒以内に完了する前提のシンプルな設計とし、非同期化・進捗通知の仕組みは導入しない(推奨。数十テーブル規模の想定(NFR3.1)であれば同期処理で十分間に合う)
- B. 非同期実行とし、ジョブID発行+ポーリングAPI(または進捗通知)で結果を返す設計とする(将来の大規模スキーマ対応(scalability-requirements.mdの既知の制約)を見据えた拡張性を優先する)
- X. Other (please specify)

[Answer]: A. HTTPリクエストスレッド内での同期実行とする。30秒以内に完了する前提のシンプルな設計とし、非同期化・進捗通知の仕組みは導入しない(数十テーブル規模の想定(NFR3.1)であれば同期処理で十分間に合う)

## Q2. 対象RDBMS接続の耐障害性パターン

NFR4.1(fail-fast、自動リトライなし)を踏まえ、対象RDBMSへのメタデータ読み取り接続に、サーキットブレーカーやリトライ等の耐障害性パターンを適用しますか。

- A. 適用しない。単純な1回限りの試行とし、失敗時は即座に422を返す(推奨。低頻度の管理操作であり、サーキットブレーカー等の複雑な状態管理を導入するメリットが薄い。NFR4.1の設計方針をそのまま実装する)
- B. 接続タイムアウトのみ明示的に設定し(例: 接続5秒、読み取り25秒)、リトライ・サーキットブレーカーは導入しない
- X. Other (please specify)

[Answer]: A. 適用しない。単純な1回限りの試行とし、失敗時は即座に422を返す(低頻度の管理操作でありサーキットブレーカー等の複雑な状態管理を導入するメリットが薄い)

## Consolidated Summary Confirmation

以下の内容でschema-introspectorのNFR Design成果物(performance-design.md/security-design.md/scalability-design.md/reliability-design.md/observability-design.md/logical-components.md/traceability.json)を生成します。

- Q1(実行方式): HTTPリクエストスレッド内での同期実行とする。非同期化・進捗通知は導入しない。
- Q2(耐障害性パターン): サーキットブレーカー・リトライは適用せず、単純な1回限りの試行とする。ただし同期実行の安全のため、接続・読み取りにNFR1.1(30秒)の範囲内に収まる明示的なタイムアウトは設定する。

その他のNFR設計事項(認可の実装、構造化ログ、テーブル単位スキップの委譲等)は、既存のFunctional Design(BR2.1〜BR2.10)・NFR Requirements(NFR1.1〜NFR5.1)をそのまま具体化し、追加の質問なしに設計します。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
