## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-16T13:56:41Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | `construction/menu-navigation/nfr-requirements/traceability.json` の `coverage[]`(NFR8エントリ)と、対応する6ファイルのいずれにも存在しない「NFR8」見出し | `traceability.json`のNFR1〜NFR7のエントリは`target`にすべて実在の見出しID(例: `NFR1.1, NFR1.2`)を記載し、それが対応ファイル内の`## NFRx.y`見出しとして実在することを本レビューで確認した。しかしNFR8のエントリだけは`status: "OK"`としながら、`target`に見出しIDではなくプローズ(正当化コメント)を直接埋め込んでおり、`performance-requirements.md`/`security-requirements.md`/`scalability-requirements.md`/`reliability-requirements.md`/`observability-requirements.md`/`tech-stack-decisions.md`のいずれにも`NFR8`または保守性・テストカバレッジ floor に関する見出しは一つも存在しない(`grep -n "^## "`で全ファイルの見出しを確認済み)。つまり「OK」と主張する成果物本体が実在しない。project.mdの学習済みルール(ビルド・運用系の関心事は"Deferred"、実行時コンポーネントでない関心事は"N/A"かつ正当化コメント明記)という既存の確立パターンに照らしても、NFR8をこのユニット独自のNFR成果物として"OK"扱いする根拠が薄い。 | NFR8を(a) 実際に`tech-stack-decisions.md`等に`## NFR8: 保守性`のような見出しを追加してBR6.2ハードコード方針・テストカバレッジ方針をこのユニット向けに具体化するか、(b) project.mdの既存学習ルールに倣い`status`を"N/A"または"Deferred"にして「team.md/Code Generationが横断的に担保するため本ステージでは個別成果物を作らない」という正当化コメントに書き換える、いずれかで`target`の記載と実態を一致させる。 | New |
| R-02 | Major | `performance-requirements.md` NFR1.1 の `Load condition`、`scalability-requirements.md` NFR3.1、`functional-spec.md` W1 手順4a-4b | `functional-spec.md`のW1(`GET /api/menu`)は、BR6.4が定める「配下リーフからのフォルダ可視性の再帰的導出」だけでなく、リーフ項目ごとに (1) ConfigEngineへのtargetTableConfigId存在確認(BR6.7)、(2) `PermissionEngine.canAccessScreen`呼び出し(BR6.3-(1))という、リーフ数nに比例する最大2n回の同期的なコンポーネント間呼び出しを発生させる。`performance-requirements.md`のNFR1.1は「Load condition」で単に`NFR3.1の想定規模(数十〜百件)を前提とする」とだけ述べ、`scalability-requirements.md`のNFR3.1も「読み取り専用のためロック競合は発生しない」としか触れておらず、両ファイルとも、3秒/p95の目標がこの n×(ConfigEngine呼び出し+PermissionEngine呼び出し)というコスト構造に対して本当に成立するのか(逐次呼び出しか並列化するか、キャッシュの要否等)には一切言及していない。数十〜百件規模なら実用上問題ない可能性は高いが、それを裏付ける記述が成果物になく、「目標値を、実際のアルゴリズム・呼び出し構造と結び付けずに主張している」状態になっている。 | `performance-requirements.md`のNFR1.1(または`scalability-requirements.md`のNFR3.1)に、W1のリーフごとの逐次呼び出し構造(ConfigEngine存在確認+PermissionEngine権限判定)を明記し、想定規模(数十〜百件)においてこの呼び出し構造が3秒/p95に収まる根拠(逐次実行でも許容範囲/キャッシュ不要である理由等)を追記する。 | New |
| R-03 | Major | `reliability-requirements.md` NFR4.3、`functional-spec.md` W4手順5(Open Questions参照) | `functional-spec.md`のW4(`DELETE /api/menu-items/{menuItemId}`)手順5は「子孫MenuItemが存在する場合の挙動(カスケード削除の可否)はcode-generationステージで確定する」と明記された未解決のOpen Questionであり、`tech-stack-decisions.md`は木構造を自己参照外部キー(`parentMenuItemId`)で表現すると決めている。これは削除時に子孫が孤立(orphan)する、または想定外のON DELETE動作が発生し得るというデータ整合性上の既知のリスクである。しかし`reliability-requirements.md`のNFR4.3(`/api/menu-itemsの失敗時挙動`)は400/403/404の即時エラー返却のみを扱い、この既知の未解決カスケード削除問題には一切触れていない。信頼性・データ整合性(NFR4)のスコープにありながら、既に機能設計側で明示的に「未解決」とフラグが立っている論点をNFR成果物側で黙殺している。 | `reliability-requirements.md`に、カスケード削除の可否が未確定である旨と、それがcode-generationステージで確定するまでの暫定リスク(例: 子孫の孤立、または削除自体を子孫存在時は拒否する等)についての記述を追加し、Open Questionへの参照を明記する。 | New |
| R-04 | Minor | `scalability-requirements.md` 既知の制約、`reliability-requirements.md` NFR4.2 | プロジェクト共通のNFR4(信頼性・データ整合性)は「同時更新の競合は楽観ロックで検出する(FR6.3参照)」と定めているが、`MenuItem`のCRUD(`/api/menu-items`)についてはこの楽観ロックを明示的に適用しない方針が`scalability-requirements.md`/`reliability-requirements.md`の両方に直接記載されている。NFR4の引用元FR6.3はrecord-edit-engineの詳細・編集画面(FR6グループ)に限定されたものであり、この除外判断自体は擁護可能だが、`nfr-requirements-questions.md`のQ1〜Q4にはこの除外を問う質問が存在せず、Q1〜Q4以外の論点は「既存のNFR1〜NFR8だけでは本ユニット固有の目標値が決まらない事項のみを問う」という質問ファイル冒頭の方針とも整合しない形で、著者が自己判断で確定させている。 | この楽観ロック除外の根拠(FR6.3のrecord-edit-engine限定スコープ)を`reliability-requirements.md`に明記し、人間の確認を経た判断であることが分かるように出典を補強する(次回インタビューでの確認、または既存回答からの論理的帰結である旨の明記)。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| required-sections(H2 ≥ 2) | FAIL: `observability-requirements.md`はH2見出しが`## NFR5.1: メトリクス・ログ・トレーシング方針`の1つのみ(以降は`### メトリクス`等のH3)。他5ファイルはいずれも2件以上のH2を持ちPASS | 内容自体はH3配下に存在し欠落はないため、これ単独はブロッキングとせず観察事項として記録する(Minorに満たない構造上の指摘としてR一覧には計上しないが、Code Generation段階で参照する場合はH2階層化を推奨) |
| traceability(NFRx.y見出し実在確認) | FAIL: NFR8のエントリのみ`target`が見出しIDではなくプローズであり、対応する見出しが6ファイルいずれにも存在しない。NFR1〜NFR7は全てPASS(見出しの実在を確認済み) | R-01の直接的な根拠。NFR8以外の整合性は良好 |
| linter/type-check | 非適用(Markdown成果物のみ、コード生成前段階のため対象コードなし) | スキップ |

### Summary

STRIDE表のRepudiation行と`reliability-requirements.md` NFR4.2は、AuditLoggingへの新規イベント発行を行わない方針(Q4確定=A)について矛盾なく整合しており、`performance-requirements.md`のNFR1.1/NFR1.2もQ1/Q2の確定回答および`schema-introspector`の先例(30秒緩和目標)と整合する妥当な中間目標として設計されている点は評価できる。しかし、traceability.jsonがNFR8を実体のない"OK"としている点、性能目標がW1の実際の呼び出しコスト構造(リーフごとのConfigEngine/PermissionEngine呼び出し)と結び付けられていない点、機能設計側で明示的に未解決とされたカスケード削除問題が信頼性要件から欠落している点の3つのMajor指摘があり、READY水準には届いていない。
