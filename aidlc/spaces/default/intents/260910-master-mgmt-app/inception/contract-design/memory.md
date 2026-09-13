<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-13T07:20:00Z — 同一ユニットが複数の異なる境界(例: user-management→frontend-uiのREST契約とuser-management→authentication-serviceの内部インタフェース契約)を持つ場合、性質の異なる境界として別契約(C5/C11、C3/C12)に分けて管理することとした。
- 2026-09-13T07:20:00Z — エラー形式はユーザーの指摘によりRFC 7807ではなくRFC 9457(2023年7月、RFC 7807を正式にobsolete)を採用した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-13T07:20:00Z — ユーザーの明示的な指定により、URLパスへのバージョン番号予約(`/api/v1/...`)すら行わない(ステージ既定の推奨案よりもさらに単純化)。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-13T07:20:00Z — 内部向けAPIであっても簡易な取り決めで済ませず、標準のOpenAPI形式で正式に仕様化する方針をユーザーが選択した(工数は増えるが、Construction時の解釈齟齬を防ぐ)。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-13T07:20:00Z — FR2.7(ログイン失敗ロックアウト)の要件定義書文言と設計(application.yml方式)の不一致は本ステージでも未解消のまま引き継がれている。Functional Design着手前に解消が必要。
- 2026-09-13T07:20:00Z — パスワードハッシュ化アルゴリズム(bcrypt/argon2等)の具体的選定は未定のまま(requirements.md継続)。
