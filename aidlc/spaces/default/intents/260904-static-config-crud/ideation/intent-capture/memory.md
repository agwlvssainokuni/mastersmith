<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-04T13:20:51Z — composerが想定した課題(動的解決の複雑さ・不具合)ではなく、実際の課題は「業務ごとにカスタマイズできない」という点だった(Q1/Q4)。MasterMeisterの運用障害は無かった。この違いはFeasibility以降のトレードオフ検討で重要になる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-04T13:20:51Z — mastersmith-mvpスコープの承認済みグリッドでは認証・権限まわりを対象外としていたが、ユーザーから「利用者ごとの権限を後から組み込むと手戻りが大きい」との懸念が出たため、フル認証基盤(ログイン等)は範囲外のまま、権限"設定"の枠組み(テーブル単位・操作単位)のみMVP範囲に含めることで合意(Q12)。ワークフローのステージ構成(スコープグリッド)自体は変更せず、成果物の内容として反映する。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-04T13:20:51Z — 権限"設定"の枠組みをどの段階(ドメイン設計/契約設計/機能設計)でどこまで具体化するかは未確定。Feasibility以降で技術的な実現方式を検討する必要がある。
- 2026-09-04T13:38:32Z — ログイン(認証)と権限定義(認可)の適用範囲・関係(パスワードポリシー、セッション管理の要否、権限定義の実際の適用タイミングがMVP内かどうか)は、レビュー(R-02 Resolved)からの申し送り事項として、ドメイン設計・契約設計で具体化する必要がある。
