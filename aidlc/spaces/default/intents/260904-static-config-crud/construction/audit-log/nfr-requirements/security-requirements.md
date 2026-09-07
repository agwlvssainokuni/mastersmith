# Security Requirements: audit-log

## NFR-AUTHZ.1: 管理者限定アクセス

audit-logの全操作(参照・エクスポート・削除・設定変更)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる(functional-design BR5.1)。isAdminクレームを持たない利用者からの要求は403(RFC 7807)で拒否する。

## NFR-INTEGRITY.1: 記録の完全性(改ざん耐性)

AuditLogEntryは作成後に内容を変更できない(BR6.1、更新操作を提供しない)。削除は保持期間超過分の一括削除(BR4.1)のみに限定され、個別レコードの選択的削除・改変は行わない。この設計自体が、監査ログとしての完全性(誰が・いつ・何をしたかの記録が事後に書き換えられないこと)を担保する主要な制御である。

## NFR-DATA.1: 記録内容の機微性

AuditLogEntryのtargetDescriptionは人間可読な対象の説明(例: "table_config: products")であり、パスワード・トークン等の機微情報そのものは含まない設計とする(発行元Unit側の責務、contract-summary.md監査ログイベント契約参照)。記録失敗時の構造化ログ(BR1.2)についても同様に、機微情報を出力しない。

## NFR-SEC.1: 秘密情報の管理

本Unit自身は認証情報・暗号鍵等の秘密情報を扱わない(project.md Forbidden参照、内部H2の接続情報等はconfig-management/schema-ingestion側の責務)。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T12:08:28Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | security-requirements.md > NFR-INTEGRITY.1 | 「記録の完全性(改ざん耐性)」の節は、記録の不変性(BR6.1)と削除範囲の限定(BR4.1)には言及するが、削除操作そのものの誤爆を防ぐガード(BR4.3: `olderThanDays`は1以上の整数でなければならず、0以下・マイナス値は400エラーで拒否する)には触れていない。BR4.3はrules.md自身が「意図せず監査ログの大部分・全件が削除されうるため事前に拒否する」と明記しており、完全性を損なう経路(過大な一括削除)を塞ぐ制御として、この節の趣旨に直接関係する。 | NFR-INTEGRITY.1にBR4.3(および対になるBR4.2のretentionDays下限)への言及を1文追加し、削除範囲の限定(BR4.1)が入力検証によっても裏付けられていることを明示する。ブロッキングではない。 | New |

### Summary

performance/security/scalability/reliability/observability/tech-stack-decisionsの各NFR文書は、functional-design (rules.md BR1.1〜BR6.1、entities.md) および requirements.md NFR1〜NFR9と矛盾しない。特にBR5.1(isAdminゲーティング・403 RFC7807)とBR6.1(記録不変性・削除は保持期間超過分の一括削除のみ)はsecurity-requirements.mdに正確に反映されている。traceability.jsonのN/A判定(NFR4/NFR6/NFR7/NFR8/NFR9)はいずれも他Unit・横断プロセス方針への切り分けとして妥当であり、根拠も具体的である。「軽量版」前提(数値目標を設けない)自体は要件どおりであり欠陥ではない。BR1.2の記録失敗時ログの具体的挙動(発行元への非伝播、ERRORレベル構造化ログ)もreliability-requirements.mdとobservability-requirements.mdの双方に整合的に記述されており欠落はない。指摘は1件のみでMinor(ブロッキングではない)。
