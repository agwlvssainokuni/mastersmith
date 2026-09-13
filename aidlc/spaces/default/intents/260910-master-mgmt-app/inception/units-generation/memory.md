<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-13T05:07:00Z — Domain Designコンポーネントカタログのsync/eventが混在した循環依存を、Unit依存DAGでは「eventスタイルの依存(audit-loggingへの発行)はDAGエッジから除外し、syncのみをエッジとする」という解釈で解消した。ADR-005(イベント駆動による疎結合)の趣旨と整合する。
- 2026-09-13T05:07:00Z — Domain Designに存在しないfrontend-ui(UI)・packaging(WARビルド)の2ユニットを、確定済み技術スタック(単一WAR、フロントエンド同梱)の実現に必要なユニットとして追加した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-13T05:07:00Z — user-storiesステージがSKIP対象のため、unit-of-work-story-map.mdおよびtraceability.jsonはUS IDではなくrequirements.mdの全FRを対象に作成した(project.md学習事項を踏襲)。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-13T05:07:00Z — ユニット境界をコンポーネント単位1:1(Q1=B)とし、13ユニットの細粒度構成を選択した。Domain Designの境界に最も忠実である一方、Construction側のper-unitステージ運用コストは増える。ユーザーが明示的に選択した方針であり、粗粒度案(5〜7ユニット)は不採用とした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-13T05:07:00Z — FR2.7(ログイン失敗ロックアウト)の要件定義書文言(「管理画面から設定可能」)と設計(application.yml方式)の不一致は、Domain Design時点から未解消のまま引き継がれている。Construction(機能設計・要件定義書更新)で必ず解消すること。
