# Security Design: notification

## トークンのログ非出力

メール本文に埋め込むトークン(registrationToken・resetToken・changeToken)はURLの一部として送信するが、SMTP送信失敗時のログにはサービスコンポーネントがEmailDispatchの内容(宛先・templateId)のみを記録し、これらの機微情報は含めない(NFR-DATA.1)。

## メールテンプレートのHTMLエスケープ

サービスコンポーネントがMustacheテンプレート(java-mustache-processor)を描画する際、テンプレート変数(利用者名、AccountInfoChangedEventのchangedFields等)はHTMLエスケープされた状態で埋め込み、メール本文へのHTML/スクリプト注入を防ぐ(NFR-INJECTION.1)。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T22:55:51Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ定義に紐づく自動検証ツールの明示的な指定は確認できなかったため、成果物7ファイル(performance-design.md、security-design.md、scalability-design.md、reliability-design.md、observability-design.md、logical-components.md、traceability.json)を手動で照合した。

| 検証観点 | 結果 | 解釈 |
|---|---|---|
| traceability.jsonの上流ID解決性 | PASS | `NFR1.1`/`NFR1.2`/`NFR-DATA.1`/`NFR-INJECTION.1`/`NFR2.1`/`NFR-FAILSAFE.1`/`NFR3.1`はいずれも`nfr-requirements/`配下の対応ファイル(performance-requirements.md、security-requirements.md、scalability-requirements.md、reliability-requirements.md、observability-requirements.md)に実在し、IDのずれはない。coverageの各`target`欄が指す節も実在する。 |
| logical-components.mdで定義したコンポーネント(イベントリスナー、サービス)以外への参照有無 | PASS | 7ファイルすべてで「イベントリスナー」「サービス(コンポーネント)」以外のコンポーネント名(リポジトリ等)への言及はない。逆に、性能・信頼性・可観測性・セキュリティの各設計記述は、いずれかのコンポーネントの責務(イベント購読・変換、テンプレート描画・SMTP送信・ログ記録)に紐づいている。 |
| rules.md/entities.mdとの整合性 | PASS | reliability-design.mdのリトライなし方針はBR3.1と、observability-design.mdの送信失敗ログはBR3.1と、security-design.mdのHTMLエスケープはBR2.1(Mustache描画)と、scalability-design.mdの非永続化前提はentities.mdのEmailDispatch(非永続)と、それぞれ矛盾なく対応している。 |
| 旧`## Review`セクションの除去確認 | PASS | security-design.mdに旧レビュー記録は残存していなかった(本レビュー追記前の時点で確認済み)。 |

### Summary

7ファイルはいずれもnfr-requirements配下の対応ID、rules.md/entities.mdの業務ルール・エンティティ定義、logical-components.mdが定義する2コンポーネント(イベントリスナー・サービス、リポジトリなし)と矛盾なく整合しており、前回READY判定時点から内容が変更されていないことも確認した。実装をブロックする指摘はない。
