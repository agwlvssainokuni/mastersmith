<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T05:00:00Z — [config-import-export] NFR設計の「3ユニットのキャッシュの無効化」を、実装の調査結果(menu-navigationはキャッシュを持たない)に基づき、config-engine(ConfigCache)とpermission-engine(Caffeine)の2ユニットの無効化と解釈した。menu-navigationのPostCommitは空の動作。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-22T05:00:00Z — [config-import-export] NFR設計書は検証の誤りの位置をJSON Pointer(/schema/tables/0/...)と記述したが、実装は`schema.tables[3].columns[2].editorType`形式(BR9.7・C7追補)にした。設計書の記述と実装の表記が異なる。承認ゲートで人間に提示する。
- 2026-09-22T05:00:00Z — [config-import-export] 計画書の「ロール階層継承」は、permission-engineのBR3.4により実装上は存在しない(スコープ階層COLUMN→TABLE→SCHEMAのみ)。マトリクスはスコープ階層を網羅した。
- 2026-09-22T05:00:00Z — [config-import-export] 既存のPermissionChangedEventは1件ごとの割当用で件数を持てないため、サマリ用のPermissionImportedEvent・リスナーを新設した(Step 12の範囲の拡張)。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-22T05:00:00Z — [config-import-export] 挿入をSpring Dataのsaveではなく、EntityManager.persistにした。採番済みUUIDの新規エンティティはsaveがmergeになり、挿入前に1件ずつSELECTが走る(200件で201ステートメント)ため。バッチの設定(batch_size=50等)をアプリ全体のapplication.ymlに追加したが、既存テスト全件が成功することを確認した。
- 2026-09-22T05:00:00Z — [config-import-export] permission-engineのキャッシュは、呼び出し元がトランザクションの中にいる間はキャッシュを介さず解決する(未確定・古いスナップショットを共有キャッシュに載せないため)。取り込みの検証・反映中のpermission-engineの読み取りは、キャッシュの恩恵を受けない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-22T05:30:00Z — [config-import-export] アーキテクチャレビュー(iteration 1, READY)。Critical 0件、Major 1件・Minor 5件はsuggestionとしてステージ全体の承認ゲートで人間に提示する(適用しない)。Major: (R-01)`config-import-export`画面にREADだけを持つロールが、破壊的な全置換のインポートに到達できる(canAccessScreenはNONE以外で真。昇格判定は、ファイルに書かれたエントリだけを、操作者自身の権限と比べるため、権限を除くだけのファイルは通る。ConfigImportNegativeAuthorizationTestが意図した挙動として固定。BR9.4の確定事項に基づく設計の空白)。Minor: (R-02)反映の順序が設計(schema→ロール・グループ→メニュー→権限、削除は全体で逆順)と異なる(実害なし、code-summaryに記録済み)、(R-03)個別の更新の経路のコミット前のキャッシュ処理は既存のまま、(R-04)トランザクション内の権限解決がキャッシュを介さず、NFR1.1の50ms予算への影響は未計測、(R-05)MenuStructureApiImplの未使用のConfigEngineApi引数と、entityManager.clear()の暗黙の呼び出し順依存、(R-06)traceabilityのNFR2.2・NFR4.4のtargetがプローブのテストを指す。設計の実現(a)〜(g)は、コードとテストで確認された。
- 2026-09-22T05:00:00Z — [config-import-export] ロールの削除とユーザーのroleIds(機能設計の残余リスク6)、permission-engineのブートストラップ判定(残余リスク7)、NFR設計のレビュー指摘R-13(REPEATABLE_READ化で、行が重ならない同時の取り込みでは両ファイルの和集合が残りうる)は、計画どおり対処していない。承認ゲートで人間に提示する。
- 2026-09-22T05:00:00Z — [config-import-export] 性能の確認は、開発エージェントの1回の実行(インポートp95=652ms、エクスポートp95=78ms)のみ。Build and Testで、CI相当の環境で再計測する。
