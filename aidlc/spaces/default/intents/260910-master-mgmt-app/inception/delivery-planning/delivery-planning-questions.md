# Delivery Planning — 確認事項(MasterSmith)

Units Generation(`unit-of-work.md`・`unit-of-work-dependency.md`)・Contract Design(`contract-summary.md`)・Practices Discovery(`team-practices.md`、`skeleton: on`、Walking Skeleton最小スコープ・ラダー回答=自律継続が確定済み)を踏まえ、Construction フェーズで実行するBolt(Unit of Workを束ねた1回のビルド単位。1回のビルドパスで、実際に動くものが出来上がる)の順序・粒度を確定する前に、以下を確認します。

## Q1. 最初に何を作るか

- A. Walking Skeleton(薄い一気通貫スライス)を最優先とし、完了後はリスク優先(RBAC・監査ログの正確性)、その後は基盤(依存DAGの下位ユニット)から積み上げる、という組み合わせ方針とする(推奨。`team-practices.md`のWalking Skeleton節で既に確定しているBolt1の最小スコープ=ログイン→ロール選択→一覧/編集画面での権限制御→監査ログ記録、と整合)
- B. 価値優先(ユーザーへの価値が高い画面から着手)
- C. リスク優先のみ(Walking Skeletonのような一気通貫スライスは考慮しない)
- D. 完全にUnits GenerationのDAGのトポロジカル順(config-engineから機械的に、経済的判断を行わない)
- X. Other (please specify)

[Answer]: A(B/C/Dはいずれも`team-practices.md`確定済みの`skeleton: on`と矛盾するため不採用と確認済み)

## Q2. 形式的なスコアリングモデル(WSJF等)を使うか

- A. 正式なスコアリングモデルは使わず、「Walking Skeleton→リスク優先→基盤積み上げ」という定性的な方針のみで十分とする(推奨。13ユニット規模ではWSJFの厳密な数値化コストが見合わない)
- B. WSJF(価値+時間的緊急性+リスク低減 ÷ 規模)で各Boltをスコアリングする
- X. Other (please specify)

[Answer]: A

## Q3. Boltの粒度

- A. 複数の関連ユニットを機能テーマ単位でバンドルする(推奨)。例: 「RBAC・監査基盤」「ユーザー・認証」「データエンジン(一覧・編集)」「設定入出力・フロントエンド仕上げ・パッケージング」といったテーマごとに複数ユニットを束ねる
- B. 1Bolt = 1Unit(13Bolt、最も細粒度)
- C. ユニット横断の薄いスライスのみ(機能ごとに全ユニットを少しずつ触る)
- X. Other (please specify)

[Answer]: B(1Bolt=1Unit、13Bolt。ただしBolt1=Walking Skeletonのみ、`team-practices.md`確定済みの複数ユニット横断フロー要件との整合方法を別途確認する)

## Q4. 複数Boltの並行/直列実行

- A. Bolt 1(Walking Skeleton)は単独・ゲート付きとし、ユーザー承認後の残りのBoltは基本的に自律的に直列実行する(`team-practices.md`のラダー回答=自律継続と整合、推奨)。ただしDAG上依存のないBolt同士は技術的には並行実装可能な余地を残す
- B. 全Boltを厳密に直列実行する(並行の余地なし)
- C. 依存のないBolt群は明示的に並行実行する運用とする
- X. Other (please specify)

[Answer]: A

## Q5. チーム外の依存(外部API・データ・承認・他チームからの引き継ぎ)

- A. 外部依存なし。要件定義書の制約(既存システム連携なし、SSO/LDAP等の社内認証基盤との連携は対象外、実環境デプロイ・環境構築は本ワークフロー対象外)により、Bolt実行を妨げる外部要因は存在しない(推奨)
- B. 外部依存がある(具体的に記載してください)
- X. Other (please specify)

[Answer]: A

## Q6. 最も不安な点(早期に着手すべきリスク)

- A. RBAC(ロール階層継承・主権限/補助権限の解決)と監査ログの正確性が最大のリスク。Bolt 1で骨格を作り、後続Boltで権限マトリクスのテーブル駆動テスト(`team-practices.md`のテスト方針)を含めて早期に固める(推奨)
- B. 複数RDBMS方言(PostgreSQL/MySQL/MariaDB)の吸収が最大のリスク
- C. フロントエンドの汎用UIレンダリング(設定駆動)が最大のリスク
- X. Other (please specify)

[Answer]: A

## Q7. Bolt1とBolt粒度方針の整合方法

Bolt1(Walking Skeleton)はconfig-engine、permission-engine、user-management、authentication-service、list-engine、record-edit-engine、audit-logging、frontend-ui、packagingの9ユニットを最小実装で束ねる必要がある(`team-practices.md`確定要件)。これとQ3「1Bolt=1Unit」方針をどう整合させるか。

- A. Bolt1は例外として複数ユニットを束ねる(推奨)。Bolt2以降は厳密に1Bolt=1Unitとし、Bolt1で最小実装された9ユニットも含め全13ユニットそれぞれが別途「完全実装」Boltを持つ(合計14Bolt: Bolt1スケルトン + 13Unit完全実装Bolt)
- B. Walking Skeletonの範囲を狭める(team-practices.mdの趣旨から外れるため不採用)
- X. Other (please specify)

[Answer]: A

## Decomposition Plan Summary

- **総Bolt数**: 14(Bolt1=Walking Skeleton[9ユニット最小実装] + Bolt2〜14=13ユニットそれぞれの完全実装)
- **順序方針**: Bolt1(スケルトン、単独・ゲート付き)→ Bolt2: permission-engine完全実装(RBAC、最大リスク)→ Bolt3: audit-logging完全実装(監査、最大リスク)→ Bolt4: config-engine完全実装(多重RDBMS方言)→ Bolt5: schema-introspector → Bolt6: user-management完全実装 → Bolt7: authentication-service完全実装 → Bolt8: menu-navigation → Bolt9: data-import-export → Bolt10: config-import-export → Bolt11: list-engine完全実装 → Bolt12: record-edit-engine完全実装 → Bolt13: frontend-ui完全実装(残り全画面) → Bolt14: packaging最終化(CI連携含む)
- **依存整合性**: Bolt2〜14の順序は、各ユニットの完全実装Boltがその依存先ユニットの完全実装Bolt(またはBolt1のスケルトン実装)より後に来るよう検証済み(`unit-of-work-dependency.md`のDAGと矛盾なし)
- **Bolt2以降の自律実行**: `team-practices.md`のラダー回答(自律継続)に従い、Bolt1承認後はBolt2〜14を自律的に直列実行する
- **並行性**: DAG上依存のないBolt同士(例: Bolt5 schema-introspectorとBolt6 user-management)は技術的に並行実装可能な余地を残すが、Bolt順序としては直列に記載する

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
