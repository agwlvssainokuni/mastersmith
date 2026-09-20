<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-13T23:41:00Z — [data-import-export] 本プロジェクトはAWSクラウドデプロイ・Infrastructure Designが対象外(単一実行可能WAR)のため、aws-platform-agentの視点はlogical-components.mdで「本ユニットはインフラ設計対象外」と明記するにとどめ、AWSサービス選定は行わなかった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-14T23:00:00Z — [permission-engine] アーキテクチャレビュー(iteration 1, NOT-READY, Critical 1件・Major 1件・Minor 1件)を受け、キャッシュ無効化方式を当初の「ピンポイント削除」から「invalidateAll()による全体クリア」に変更した(R-01: スコープ階層フォールバックにより下位スコープのキャッシュエントリが取りこぼされる問題を回避するため)。あわせて複数インスタンス構成時の制約をsecurity-design.mdに明記(R-02)、activeRoleIdの信頼境界を明文化(R-03)。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-13T23:41:00Z — [data-import-export] actor取得はSecurityContextHolder方式(C13変更不要)ではなくC13への明示的パラメータ追加を選択(Q1確定)。functional-designで既に指摘済みのContract Design追補課題をそのまま維持する。
- 2026-09-15T13:58:00Z — [audit-logging] BR7.7の例外遮断を`@Async`ではなく同期`@EventListener` + `try-catch`境界で実現する設計を選択(Q1=A)。発行元スレッドをブロックしうるトレードオフを受け入れる代わりに、非同期化に伴うスレッドプール管理・エラーハンドリングの複雑さを避けた(NFR1.3で内部処理時間に数値目標を設けていないことと整合)。ページングもオフセット方式(Q3=A)を選択し、8万行到達までの深いページでの性能劣化を意図的に許容した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-20T01:12:00Z — [user-management] ユーザー提供情報: 自作mustacheエンジンのリポジトリは https://github.com/agwlvssainokuni/java-mustache-processor 。nfr-requirementsのQ7=C(Gitサブモジュール等での取り込み)に対応する。取り込み手順(サブモジュールの固定方法・Gradleからの参照方法)、構文範囲・エスケープ仕様、ライセンス、`<title>`からの件名抽出方法は、Code Generationの計画承認までに、このリポジトリの内容(README・ライセンス・公開API)を確認したうえで、tech-stack-decisions.mdの確認事項1〜4・7に沿って決める。
- 2026-09-14T23:04:00Z — [permission-engine] アーキテクチャレビュー(iteration 2, READY)。iteration 1の3件(R-01/R-02/R-03)はResolved確認。新規Major所見R-04(non-blocking suggestion): `invalidateAll()`の採用理由「assignPermissionは低頻度」が、functional-spec.mdの「RBAC設定の各エントリごとにassignPermissionを呼び出す」実装パターンと矛盾し、1回の一括インポート中に数百〜数千回invalidateAll()が連続実行されキャッシュが実質機能不全になる可能性がある。修正は行わず、承認ゲートで人間に提示する(review-protocolの「Do NOT apply suggestions, quote them at the gate」原則に従う)。
- 2026-09-13T23:41:00Z — [data-import-export] functional-design由来の未解決事項(C13へのactor/permittedColumnNamesパラメータ追加、C1へのfilter/sort/permittedColumnNames追加、config-engineへのisPrimaryKey属性追加)は、Code Generation着手前にContract Design/Domain Designへの追補が必要なまま。NFR Designでは実装方針(パラメータとして直接受け渡す)を具体化したのみで、契約自体はまだ更新されていない。
- 2026-09-13T23:45:54Z — [data-import-export] アーキテクチャレビュー(iteration 1, READY)。1件のMajor所見(riding suggestion、ゲートで人間に提示): (R-01)security-design.mdが提案したC13新シグネチャ(actor/permittedColumnNames追加)自体がまだContract Design未着手であることを、C1の追補課題と同様に明示すべき。1件のMinor(R-02): reliability-design.mdのNFR4.1説明が「全件検証後はコミットをスキップする」というfunctional-spec.md W2手順4の実際のゲーティング機構を再掲していない。いずれもブロッキングではなく修正は行わずゲートで提示する。
- 2026-09-15T06:13:00Z — [schema-introspector] アーキテクチャレビュー(iteration 1, READY)。Major所見2件(riding suggestions、ゲートで人間に提示): (R-01)reliability-design.mdの接続5秒+読み取り25秒はperformance-design.mdの6ステップ直列フロー中のステップ4(メタデータ走査)単体の上限にすぎず、認可判定・方言判定等の他ステップの所要時間がNFR1.1(30秒)の予算に計上されておらず数値的な裏付けが不完全。(R-02)security-design.mdが前提とする`PermissionEngineApi.canAccessScreen`呼び出しについて、`inception/contract-design/contract-summary.md`のC10契約consumers列挙に`schema-introspector`が含まれていない(C9契約では正しくconsumerとして列挙されている対比で確認)。契約更新漏れの可能性があり、Code Generation着手前にContract Designへの追補確認を推奨する。Minor所見1件(R-03): 認可判定呼び出し自体が例外を送出した場合の扱いが未記載。いずれもブロッキングではなく修正は行わずゲートで提示する。
- 2026-09-15T13:59:00Z — [audit-logging] アーキテクチャレビュー(iteration 1, READY)。Major所見2件(riding suggestions、ゲートで人間に提示): (R-01)reliability-design.mdの「いかなる例外も発行元へ伝播させない」という記述は、コード例が`catch (Exception e)`であり`Error`(OutOfMemoryError等)は捕捉されないため、同期`@EventListener`(Q1=A、`@Async`不採用)の下では厳密には成立しない。(R-02)security-design.md/Q4で追加した400 Bad Request(クエリパラメータ検証エラー)が、`inception/contract-design/contract-summary.md`のC6契約(OpenAPI `responses`)に未追補(503のような追補記録がない)。Minor所見2件: (R-03)reliability-requirements.mdの「fire-and-forget」という表現と、実際に選択した同期`@EventListener`設計との用語上の緊張。(R-04)traceability.jsonのNFR2.4ステータスが、ユニット固有の設計判断ではなくteam.md既定への言及のみで"OK"としている点(他ユニットの慣例では"N/A"が妥当ではないか)。いずれもブロッキングではなく修正は行わずゲートで提示する。
