# Risk & Sequencing Rationale — MasterSmith(マスタ管理アプリ)

本ドキュメントは、`bolt-plan.md`のBolt順序を選んだ理由(なぜその順番か)を記録する。`delivery-planning-questions.md`の確定回答(Q1〜Q7)を根拠とする。

## 採用した順序付けヒューリスティック

**ウォーキングスケルトン優先(Cockburn, *Crystal Clear*)+ リスク優先(Boehm, Spiral Model)の組み合わせ**を採用する(Q1=A)。形式的なスコアリングモデル(WSJF、Reinertsen/SAFe)は使用しない(Q2=A)。

- **ウォーキングスケルトン優先**: `team-practices.md`で`skeleton: on`が確定しており、Bolt1はアーキテクチャの全レイヤーを貫く最小限のエンドツーエンド実装とする。これは本プロジェクト最大のアーキテクチャ上の賭け(複数RDBMS方言吸収・単一WARパッケージング・設定駆動UIが実際につながるか)を最速で検証するためである。
- **リスク優先**: Walking Skeleton完了後は、`team-practices.md`のテスト方針(権限判定ロジックにtest-first/ATDD寄りの例外を認める)とも整合する形で、RBAC(permission-engine)・監査ログ(audit-logging)の正確性を最優先で固める(Q6=A)。これらは要件定義書FR3.3・FR3.4・project.mdのMandated/Forbidden項目(サーバー側実効権限再検証、権限昇格防止、監査ログ改ざん・削除不可)に直結する、本プロジェクトで最も後戻りのコストが高い領域である。
- **基盤積み上げ**: リスクの高い領域を固めた後は、Units GenerationのDAG(`unit-of-work-dependency.md`)が示す依存関係の下位ユニットから順に積み上げる。

**WSJFを不採用とした理由**(Q2=A): 13ユニットという規模において、価値・時間的緊急性・リスク低減・規模の4軸を厳密に数値化するコストが、定性的な「ウォーキングスケルトン→リスク優先→基盤積み上げ」という方針の明快さに見合わないと判断した。

## Bolt粒度の選択(Q3=B、1Bolt=1Unit)

Units Generationのコンポーネント境界(1:1でUnit化)を最も忠実に反映する細粒度を、ユーザーが明示的に選択した(AIの推奨案=機能テーマ単位の粗いバンドルではなく、より細かい粒度)。この結果、Bolt数は当初の推奨(5〜7Bolt程度)より大幅に増え、14Boltとなった。

**Bolt1(Walking Skeleton)のみ例外扱いとした理由**(Q7=A): `team-practices.md`確定済みのWalking Skeleton要件(ログイン→ロール選択→一覧/編集画面での権限制御→監査ログ記録という一気通貫フロー)は、その定義上、複数ユニット(config-engine, permission-engine, user-management, authentication-service, list-engine, record-edit-engine, audit-logging, frontend-ui, packaging の9ユニット)が協調しなければ成立しない。「1Bolt=1Unit」を機械的に適用すると、この一気通貫検証がBolt14(最終Bolt)まで不可能になり、Walking Skeletonの存在意義(アーキテクチャの早期検証)が失われる。そのためBolt1のみを最小実装での複数ユニット束ねの例外とし、Bolt2以降は厳密に1Bolt=1Unitを適用する。この結果、Bolt1で最小実装された9ユニットも含め、全13ユニットがそれぞれ独立した「完全実装」Boltを持つ(合計14Bolt)。

## 依存整合性の検証(Bolt2〜14)

Units Generationの依存DAG(`unit-of-work-dependency.md`)に対し、Bolt2〜14の順序が各ユニットの依存先を侵害していないことを検証した。

| Bolt | Unit | DAG上の依存先(sync) | 依存先の完全実装(またはBolt1スケルトン)Bolt番号 | 順序整合 |
|---|---|---|---|---|
| 2 | permission-engine | config-engine | Bolt1(スケルトン実装で充足) | ✅ |
| 3 | audit-logging | permission-engine | Bolt2 | ✅ |
| 4 | config-engine | (依存なし) | — | ✅ |
| 5 | schema-introspector | config-engine | Bolt4 | ✅ |
| 6 | user-management | permission-engine | Bolt2 | ✅ |
| 7 | authentication-service | user-management | Bolt6 | ✅ |
| 8 | menu-navigation | permission-engine | Bolt2 | ✅ |
| 9 | data-import-export | config-engine | Bolt4 | ✅ |
| 10 | config-import-export | config-engine, menu-navigation, permission-engine | Bolt4, Bolt8, Bolt2 | ✅ |
| 11 | list-engine | config-engine, permission-engine, data-import-export, authentication-service | Bolt4, Bolt2, Bolt9, Bolt7 | ✅ |
| 12 | record-edit-engine | config-engine, permission-engine, data-import-export, authentication-service | Bolt4, Bolt2, Bolt9, Bolt7 | ✅ |
| 13 | frontend-ui | list-engine, record-edit-engine, menu-navigation, authentication-service, user-management, audit-logging, config-import-export, schema-introspector | Bolt11, Bolt12, Bolt8, Bolt7, Bolt6, Bolt3, Bolt10, Bolt5 | ✅ |
| 14 | packaging | 全12ユニット | Bolt1〜13 | ✅ |

**Bolt2(permission-engine完全実装)がBolt4(config-engine完全実装)より先行する点について**: `unit-of-work-dependency.md`上、permission-engineはconfig-engineに依存する。しかしBolt1(Walking Skeleton)で両ユニットとも最小実装済みであり、permission-engineの完全実装(ロール階層継承・主権限/補助権限マトリクス)はconfig-engineの完全実装(複数RDBMS方言吸収の網羅性)を必須の前提としない(config-engineが提供するスキーマ階層構造の基本インタフェースはBolt1時点で既に利用可能なため)。したがってリスク優先の観点からpermission-engineの完全実装を先行させても、DAG上のブロッキングは発生しない。トポロジカル順からの意図的な逸脱であり、Q1・Q6の確定回答(リスク優先)に基づく。

## 逸脱の総括

Units GenerationのDAGが示す厳密なトポロジカル順(config-engine→schema-introspector/permission-engine/data-import-export→…)からの逸脱は、以下の2点のみである。

1. Bolt1がWalking Skeletonとして9ユニットを横断する(トポロジカル順では本来Bolt1はconfig-engine単体のはず)
2. Bolt2(permission-engine)がBolt4(config-engine)より先行する(リスク優先のため)

いずれも`team-practices.md`・`delivery-planning-questions.md`で確定した経済的判断(スケルトン優先・リスク優先)に基づく意図的な逸脱であり、DAG上のブロッキング依存を侵害していないことを上表で確認済みである。
