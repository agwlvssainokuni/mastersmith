# Security Requirements: notification

## NFR-DATA.1: トークンのログ非出力

メール本文に埋め込むトークン(registrationToken・resetToken・changeToken)はURLの一部として送信するが、SMTP送信失敗時のログにはEmailDispatchの内容(宛先・templateId)のみを記録し、これらの機微情報は含めない(BR3.1)。

## NFR-INJECTION.1: メールテンプレートのHTMLエスケープ

Mustacheテンプレート(java-mustache-processor)描画時、テンプレート変数(利用者名、AccountInfoChangedEventのchangedFields等)はHTMLエスケープされた状態で埋め込み、メール本文へのHTML/スクリプト注入を防ぐ(BR2.1、BR1.3)。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T14:23:42Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | performance-requirements.md > NFR1.2 | NFR1.2は「SMTP送信の成否はイベント発行元の主処理をブロックしない」と主張し、出典として`(BR3.1)`のみを付している。しかしrules.md BR3.1はSMTP送信失敗時のログ記録・リトライなし方針を定めるのみで、発行元がブロックされない(非同期配送である)ことには一切言及していない。この非同期配送の実際の根拠はinception/contract-design/contract-summary.mdの「通知イベント契約(#9〜#10)」がdeliveryを`in-process async (Spring ApplicationEvent)`と定めている点であり(監査ログイベント契約の節ではこの"async"の定義が「呼び出し元の主処理を止めないことを意味する」と明記されている)、rules.md/entities.mdにはこの根拠が存在しない。 | NFR1.2の出典表記を、rules.md BR3.1単独ではなく、inception/contract-design/contract-summary.mdの通知イベント契約(#9〜#10)のdelivery定義(in-process async)を明記する形に修正する。 | New |
| R-02 | Minor | reliability-requirements.md > 冒頭文 | 「自宅サーバ1台構成のため、SLA/SLO数値目標(稼働率%等)は設けない」の出典として`requirements.md NFR1`を挙げているが、NFR1は応答速度・想定同時接続数に関する性能要件であり、可用性(稼働率)の数値目標を扱っていない。可用性目標を設けない根拠として性能要件を引用するのはミスリーディングである。 | 出典表記から`requirements.md NFR1`を外し、`team.md Deployment`(自宅サーバ1台・ステージング/本番分離なしの運用前提)のみを根拠として引用する、または可用性目標を設けない旨の記述をrequirements.mdの該当箇所(Constraints等)に正しく対応付ける。 | New |

### Validation Tool Results

本ステージ定義に紐付く自動検証ツールの指定は確認できなかったため、成果物本文とアップストリーム文書(rules.md、entities.md、functional-design-questions.md、contract-summary.md、requirements.md)との突き合わせによる手動検証のみを実施した。

| Tool | Result | Interpretation |
|---|---|---|
| (該当なし) | — | 本ステージにvalidationツールの指定なし。手動クロスチェックで代替。 |

### Summary

7ファイルの記述はいずれもrules.md(BR1.1〜BR3.1)・entities.md(EmailDispatch非永続化)・functional-design-questions.md(Q1: 管理者起点再送手段はMVPスコープ外)と正確に一致しており、ファイル間の矛盾もない。traceability.jsonのNFR1〜NFR9の各status(OK/N/A)判定はいずれも妥当な根拠を持つ。security-requirements.mdのNFR-INJECTION.1(Mustacheテンプレート変数のHTMLエスケープ)は、java-mustache-processorが未実装の自作ライブラリであることを踏まえた実装要件としての記述であり、rules.md/entities.mdの記述と矛盾しない。指摘した2件はいずれも出典citationの精度に関するMinorな指摘であり、実装をブロックするものではない。
