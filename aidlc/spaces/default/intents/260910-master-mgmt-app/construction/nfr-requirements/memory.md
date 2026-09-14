<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-14T22:45:00Z — [permission-engine] アーキテクチャレビュー(iteration 1, NOT-READY, Major 3件)を受け、reliability-requirements.md(R-07とのトランザクション粒度の緊張関係を明記)・observability-requirements.md(ブートストラップ時のアラートバースト既知事項を明記)・security-requirements.md(TTLキャッシュのstale-authorization windowとassignPermission成功時のキャッシュ即時無効化要件をNFR2.8として追加)・performance-requirements.md(20列同時呼び出しのキャッシュ非依存予算計算を追記)を修正した。
- 2026-09-13T23:22:00Z — [data-import-export] CSVインジェクション(数式インジェクション)対策を意図的に実装しない(NFR2.3)。社内限定利用・CSV外部配布なしという前提でのリスク受容であり、見落としではない(Q3確定)。
- 2026-09-13T23:22:00Z — [data-import-export] アップロードファイルのサイズ・Content-Type検証を追加しない(NFR2.4)。BR8.1のCSV形式検証で不正ファイルは実質排除されるという判断(Q4確定)。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-13T23:22:00Z — [data-import-export] 一時バッファは常にメモリ上配列とし、一時テーブル方式は採用しない(NFR3.3/tech-stack-decisions.md)。NFR1想定規模(10万行)なら許容範囲という評価に基づく。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-14T22:47:00Z — [permission-engine] アーキテクチャレビュー(iteration 2, READY)。iteration 1のR-01〜R-04は具体的な修正(disclaimerではなく実装可能な方向性)によりResolved確認。新規Major所見R-05(non-blocking suggestion): NFR2.8のキャッシュ無効化記述が「(scopeType, scopeRef)」のみを対象とし、NFR3.4のキャッシュキー3つ組「(activeRoleId, scopeType, scopeRef)」のroleId成分に触れていないため、Code Generation実装者が対象範囲(該当ロールのみか全ロール一括無効化か)を推測する必要がある。修正は行わず、承認ゲートで人間に提示する(review-protocolの「Do NOT apply suggestions, quote them at the gate」原則に従う)。
- 2026-09-13T23:22:00Z — [data-import-export] 同一テーブルへの同時大量インポート(排他制御なし、NFR3.2)が、DB接続プール枯渇によりNFR1.1(5分以内)の目標を満たせない可能性を既知の限界として記録。将来必要になれば排他制御の追加を検討する。
- 2026-09-13T23:28:56Z — [data-import-export] アーキテクチャレビュー(iteration 1, READY)。4件のMinor所見(riding suggestions、ゲートで人間に提示): (R-01)traceability.jsonのNFR6はN/AよりDeferredが適切ではないか、(R-02)tech-stack-decisions.mdの「常にメモリ配列」はrules.md BR8.10の条件付き記述(一時テーブル併記)との関係を明記すべき、(R-03)NFR2.4/NFR4.2に再評価トリガーの明記がなく他の3件と粒度が不揃い、(R-04)scalability-requirements.mdの「50ユーザー」の出典がNFR3ではなくNFR1本文である。いずれもブロッキングではなく、修正は行わずゲートで提示する。
