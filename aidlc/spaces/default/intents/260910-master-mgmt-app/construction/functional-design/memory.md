<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-13T17:35:17Z — [data-import-export] C13契約note「全体を即時失敗にはしない」を、行単位バリデーションを最初のエラーで中断せず全行分の結果を収集するという意味であると解釈し、DBコミット単位(Q10: 全件検証後の一括コミット、1件でもエラーがあれば全体ロールバック)とは別論点として整理した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-13T17:35:17Z — [data-import-export] BR8.9(ImportExecutedEvent)は実行単位のサマリイベントのみとし、行単位の変更前後値を記録しない。project.mdのMandated「監査ログは変更前後の値を記録する」の字義どおりの適用からは意図的に外れる(Q8 Follow-upでユーザーに明示確認済みのスコープ判断であり、config-engineのBR1.13が採用した行単位イベント発行とは異なる方針)。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-13T17:35:17Z — [data-import-export] NFR1(最大10万行)への対応としてストリーミング処理を必須化する一方、コミットは全件検証後の一括トランザクションとした(Q9=A, Q10=B)。CSVの逐次読み取り自体はストリーミングだが、DB書き込みは全行バリデーション完了後にまとめて行うトレードオフを採用。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-13T17:35:17Z — [data-import-export] エクスポート(W1)が一覧画面の検索条件・ソート順を反映する(Q2確定)ため、Contract Design契約(C1: `/records/export`)にfilter/sortパラメータを追加する追補が、Code Generation着手前に必要(functional-spec.mdのAssumptions & Open Questions参照)。
- 2026-09-13T22:53:33Z — [data-import-export] アーキテクチャレビュー(iteration 1, NOT-READY)で3件のMajor所見: (R-01)列単位READ権限の受け渡し経路が未定義→CsvExportRequest.permittedColumnNamesを追加しC1追補が必要と明記、(R-02)ImportExecutedEvent.actorの取得経路が未定義→CsvImportRequestエンティティを新設しC13へのactor追補が必要と明記、(R-03)ストリーミング処理(BR8.10)と全件検証後一括コミット(BR8.7)の実装方式が未整理→CSV読み取りは1パスストリーミング、変換後の軽量データのみ一時バッファ保持という方式に具体化。あわせて(R-05)config-engineのC9契約に主キー属性が存在しない欠落を発見し、Domain Design/Contract Designへの追補が必要なOpen Questionとして追加した。
- 2026-09-13T23:08:24Z — [data-import-export] アーキテクチャレビュー(iteration 2, READY)。R-01/R-02/R-03/R-04/R-05はResolvedと確認された一方、R-03の修正過程で新規Major所見(R-06)が判明: `CsvImportRowResult`に変換後カラム値を保持する属性(例: `values`)が定義されておらず、BR8.7の一括コミット処理が参照する「変換後データ」の格納先が未定義。Critical 0件・Major 1件でREADY判定の閾値内のため検証は完了としたが、Code Generation着手前に`CsvImportRowResult`へ変換後値の属性を追加する修正を推奨するsuggestionとして、承認ゲートで人間に提示する(review-protocolの「Do NOT apply suggestions, quote them at the gate」原則に従う)。
