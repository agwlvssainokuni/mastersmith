# Security Design: notification

## トークンのログ非出力

メール本文に埋め込むトークン(registrationToken・resetToken・changeToken)はURLの一部として送信するが、SMTP送信失敗時のログにはサービスコンポーネントがEmailDispatchの内容(宛先・templateId)のみを記録し、これらの機微情報は含めない(NFR-DATA.1)。

## メールテンプレートのHTMLエスケープ

サービスコンポーネントがMustacheテンプレート(java-mustache-processor)を描画する際、テンプレート変数(利用者名、AccountInfoChangedEventのchangedFields等)はHTMLエスケープされた状態で埋め込み、メール本文へのHTML/スクリプト注入を防ぐ(NFR-INJECTION.1)。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T16:00:31Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ定義に紐付く自動検証ツールの指定は確認できなかったため、成果物7ファイル(logical-components.md、performance-design.md、security-design.md、scalability-design.md、reliability-design.md、observability-design.md、traceability.json)と上流文書(nfr-requirements配下の全ファイル、functional-design/rules.md、entities.md)との突き合わせによる手動検証のみを実施した。

| Tool | Result | Interpretation |
|---|---|---|
| (該当なし) | — | 本ステージにvalidationツールの指定なし。手動クロスチェックで代替。 |

### Summary

観点1(NFR要件との一致): performance/security/scalability/reliability/observability の各design.mdは、対応するnfr-requirements配下ファイルのNFR1.1・NFR1.2・NFR-DATA.1・NFR-INJECTION.1・NFR2.1・NFR-FAILSAFE.1・NFR3.1の記述内容(非同期配送、トークン非ログ出力、HTMLエスケープ、単一インスタンス、リトライなし、送信失敗ログ)と正確に一致している。

観点2(BRとの整合): 各design.mdの記述はrules.md BR1.1〜BR3.1(6種イベント購読、Mustache描画、SMTP送信失敗時ログのみ・リトライなし)と矛盾しない。security-design.mdが列挙するトークン(registrationToken・resetToken・changeToken)もBR1.1・BR1.5・BR1.6の記述と一致し、トークンを持たないBR1.2〜BR1.4を誤って含めていない。

観点3(コンポーネント参照の整合): logical-components.mdは「イベントリスナー」「サービス」の2コンポーネントのみを定義し、リポジトリコンポーネントを持たないと明記している。他5ファイル(performance/security/scalability/reliability/observability-design.md)を確認したところ、この2コンポーネント以外への参照(未定義コンポーネント、リポジトリ層、他Unit固有のコンポーネント名)は存在しない。永続エンティティを持たない設計(entities.md、EmailDispatch非永続化)とリポジトリコンポーネント不在の記述も整合している。

観点4(traceability網羅性): nfr-design/traceability.jsonのupstream_ids(NFR1.1、NFR1.2、NFR-DATA.1、NFR-INJECTION.1、NFR2.1、NFR-FAILSAFE.1、NFR3.1)は、nfr-requirements/traceability.jsonでstatus:"OK"と判定された全7項目と過不足なく一致する。status:"N/A"と判定されたNFR4〜NFR9(packaging責務、内部H2非該当、schema-ingestion固有、auth責務等)は設計対象外として適切に除外されており、設計成果物からの参照漏れもない。

観点5(ファイル間矛盾): 7ファイル間で送信失敗時のログ記録内容(宛先・templateId、機微情報除く)、リトライなし方針、単一インスタンス構成、コンポーネント構成についての記述はすべて一致しており、矛盾は見当たらない。

過去Unitで見られた3パターン(未定義コンポーネントへの依存、確定済み契約との矛盾、繰延べギャップの不可視化)のいずれも本Unitの成果物には確認されなかった。READY と判定する。
