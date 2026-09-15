<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-14T22:45:00Z — [permission-engine] アーキテクチャレビュー(iteration 1, NOT-READY, Major 3件)を受け、reliability-requirements.md(R-07とのトランザクション粒度の緊張関係を明記)・observability-requirements.md(ブートストラップ時のアラートバースト既知事項を明記)・security-requirements.md(TTLキャッシュのstale-authorization windowとassignPermission成功時のキャッシュ即時無効化要件をNFR2.8として追加)・performance-requirements.md(20列同時呼び出しのキャッシュ非依存予算計算を追記)を修正した。
- 2026-09-13T23:22:00Z — [data-import-export] CSVインジェクション(数式インジェクション)対策を意図的に実装しない(NFR2.3)。社内限定利用・CSV外部配布なしという前提でのリスク受容であり、見落としではない(Q3確定)。
- 2026-09-13T23:22:00Z — [data-import-export] アップロードファイルのサイズ・Content-Type検証を追加しない(NFR2.4)。BR8.1のCSV形式検証で不正ファイルは実質排除されるという判断(Q4確定)。
- 2026-09-15T06:01:00Z — [schema-introspector] アーキテクチャレビュー(iteration 1, NOT-READY)でMajor所見3件: (R-01)reliability-requirements.mdの全行にNFR4.x形式のIDが欠落 → NFR4.1/NFR4.2見出しを追加。(R-02)observability-requirements.mdにNFR5.xのIDが無いのにtraceability.jsonが実在しない「NFR5.1相当」を参照 → NFR5.1見出しを追加し参照を修正。(R-03)traceability.jsonのNFR3行がstatus=N/Aとしつつtarget欄でNFR3.1(実在する定義済み要件)を根拠にする自己矛盾 → status=OKへ修正。
- 2026-09-15T02:48:00Z — [audit-logging] アーキテクチャレビュー(iteration 1, NOT-READY)でMajor所見3件を修正: (R-01)reliability-requirements.md NFR4.4・security-requirements.md NFR2.5が導入した503レスポンスがContract Design(contract-summary.md C6)の`responses`(200/403のみ)と不整合 → C6契約へ503(ServiceUnavailable)を加法的変更として追補し、追補の経緯をC6スペック直下に明記。(R-02)NFR1の「1テーブル最大10万行程度」想定と、本ユニットの成長見積り(Q2: 年間数万〜数十万件、無期限保持)が1〜2年で矛盾しうるのに再検討トリガーが数値化されていなかった → scalability-requirements.md NFR3.3に「8万行(10万行の8割)到達で再検討開始」という定量的トリガーを追加。(R-03)BR7.7(サイレント失敗)・Q3(専用メトリクスなし)・Q4(内部処理時間計装なし)の組み合わせにより、記録パイプライン全断を自動検知する手段が皆無だが、これが受容リスクとして記録されていなかった → reliability-requirements.mdにNFR4.6として意図的な受容リスクであることを明記。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-13T23:22:00Z — [data-import-export] 一時バッファは常にメモリ上配列とし、一時テーブル方式は採用しない(NFR3.3/tech-stack-decisions.md)。NFR1想定規模(10万行)なら許容範囲という評価に基づく。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-14T22:47:00Z — [permission-engine] アーキテクチャレビュー(iteration 2, READY)。iteration 1のR-01〜R-04は具体的な修正(disclaimerではなく実装可能な方向性)によりResolved確認。新規Major所見R-05(non-blocking suggestion): NFR2.8のキャッシュ無効化記述が「(scopeType, scopeRef)」のみを対象とし、NFR3.4のキャッシュキー3つ組「(activeRoleId, scopeType, scopeRef)」のroleId成分に触れていないため、Code Generation実装者が対象範囲(該当ロールのみか全ロール一括無効化か)を推測する必要がある。修正は行わず、承認ゲートで人間に提示する(review-protocolの「Do NOT apply suggestions, quote them at the gate」原則に従う)。
- 2026-09-13T23:22:00Z — [data-import-export] 同一テーブルへの同時大量インポート(排他制御なし、NFR3.2)が、DB接続プール枯渇によりNFR1.1(5分以内)の目標を満たせない可能性を既知の限界として記録。将来必要になれば排他制御の追加を検討する。
- 2026-09-13T23:28:56Z — [data-import-export] アーキテクチャレビュー(iteration 1, READY)。4件のMinor所見(riding suggestions、ゲートで人間に提示): (R-01)traceability.jsonのNFR6はN/AよりDeferredが適切ではないか、(R-02)tech-stack-decisions.mdの「常にメモリ配列」はrules.md BR8.10の条件付き記述(一時テーブル併記)との関係を明記すべき、(R-03)NFR2.4/NFR4.2に再評価トリガーの明記がなく他の3件と粒度が不揃い、(R-04)scalability-requirements.mdの「50ユーザー」の出典がNFR3ではなくNFR1本文である。いずれもブロッキングではなく、修正は行わずゲートで提示する。
- 2026-09-15T06:01:00Z — [schema-introspector] アーキテクチャレビュー(iteration 2, NOT-READY、reviewer_max_iterations=2到達により終端)。R-01〜R-03はResolvedと確認された一方、新規Major所見(R-04)が判明: traceability.jsonのNFR4行がstatus=N/Aとしつつ、target欄でNFR4.1(実在し適用対象であるfail-fast要件、BR2.9由来)を根拠として引用する、R-03と同型の自己矛盾。イテレーション上限到達によりreview-freezeフックが有効化され追加修正ができないため未解消のまま終端し、本ユニットはunit-major方式のため後段のカスケード承認ゲートで人間に提示する。修正案: NFR4のstatusをOKにし、target="NFR4.1(対象RDBMS接続失敗時のfail-fast対応)。NFR4.2(監査ログ改ざん防止・楽観ロック)は本ユニット非該当"とする(config-engine側のNFR4と同じOKパターン)。
- 2026-09-15T02:53:00Z — [audit-logging] アーキテクチャレビュー(iteration 2, READY)。R-01/R-02/R-03はResolved確認。新規Minor所見R-04(non-blocking suggestion): C6契約への503レスポンス追加を正当化する「Contract Ownership Rules」の引用が、同ルールの文言(「オプションフィールド追加、新規エンドポイント追加等」)を厳密には指しておらず、「既存エンドポイントへの新規レスポンスコード追加」という具体例が同ルールに明記されていない点のギャップ。実害は小さい(403で既に使っているproblem+json/ProblemDetails形状を再利用しているため)が、修正は行わず承認ゲートで人間に提示する。
