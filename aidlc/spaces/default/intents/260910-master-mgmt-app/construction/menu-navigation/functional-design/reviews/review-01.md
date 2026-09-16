## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-16T13:42:27Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | `construction/menu-navigation/functional-design/rules.md` BR6.8(削除)、`functional-spec.md` W4 手順5・「Assumptions & Open Questions」、`inception/contract-design/contract-summary.md` C3 `DELETE /api/menu-items/{menuItemId}` | BR6.8の`logic`ブロックは削除系分岐を持たず「ELSE 作成・更新・削除を実行する」で無条件に204を返すと定めている一方、`functional-spec.md`の「Assumptions & Open Questions」は子孫MenuItemが存在する場合の挙動(カスケード削除か拒否か)を未確定としたままcode-generationへ先送りしており、既定案として「409で拒否」を示唆している。しかしC3契約の`DELETE`レスポンスは401/403/404のみで409が定義されていない。functional-design-questions.mdの10問(Q1〜Q10、フォローアップ含む)のいずれもこの論点を扱っておらず、正本であるはずのrules.md(BR6.8)自身がこの未決事項を反映していない(「無条件に削除実行」と書かれているため、rules.mdだけを読む実装者は409分岐の存在を知り得ない)。code-generation側で409を採用した場合、C3のもう一段の追補(レスポンス定義追加)が必要になり、手戻りが発生する。 | (a) rules.md BR6.8にカスケード削除方針の分岐を明記するか、少なくとも「未確定」であることをlogic/violation_behaviourに反映して`functional-spec.md`の記述と整合させる。(b) 方針が「409で拒否」になる可能性がある以上、Contract Design追補(C3)に409レスポンスを今のうちに追加しておくか、少なくとも本ステージのAssumptionsに「C3への追加追補が必要になる可能性がある」ことを明記する。 | New |
| R-02 | Minor | `construction/menu-navigation/functional-design/rules.md` | required-sectionsセンサーの基準(最低2つのH2見出し)に対し、本ファイルのH2見出しは末尾の「## ルールサマリー」1つのみ(YAMLブロックの前にH2見出しがない)。`entities.md`・`functional-spec.md`は2つ以上のH2を持ち基準を満たすが、`rules.md`のみ満たしていない。 | `rules.md`にYAMLルール定義ブロックの前に見出し(例: 「## ルール定義」)を追加し、H2見出しを2つ以上にする。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| required-sections | entities.md: PASS(H2 2件)/ functional-spec.md: PASS(H2 5件以上)/ rules.md: FAIL相当(H2 1件のみ、「## ルールサマリー」のみ) | R-02の根拠。内容自体は充実しているが構造要件を満たさない |
| traceability | PASS | `traceability.json`のcoverage(FR7.1→BR6.1,BR6.2 / FR7.2→BR6.3,BR6.4,BR6.9 / FR7.3→BR6.5)とreverse(BR6.6, BR6.7, BR6.8)を合わせるとrules.mdの9件(BR6.1〜BR6.9)すべてが説明されており孤立ルールはない。`BRx.y`参照もすべてrules.md内に実在する |
| linter/type-check | 該当なし(コード生成物なし、スキップ) | — |

### Summary

BR6.3のscreenKey共有規則はschema-introspector側のBR2.8(予約screenKey `"config-import-export"`の共有)およびpermission-engine側のBR3.10(予約キー以外はtableConfigIdとして扱う汎用画面キー体系)と正確に整合しており、Q1のフォローアップ訂正(A案)も両ファイルへ齟齬なく反映されている。Contract Design追補(C3)の`/api/menu-items` CRUD定義もBR6.8・W2〜W4のステータスコード(401/403/400/404)・screenKeyと一致し、entities.mdとfunctional-spec.mdのER図も自己整合的である。唯一の実質的な懸念は削除時のカスケード方針が未決のままrules.mdの正本記述と矛盾した形で放置されている点(R-01)で、これはcode-generation段階での契約再追補という手戻りリスクを内包するがCritical/Major超過には至らないため、READY判定とする。
