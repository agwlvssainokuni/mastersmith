# NFR Design Questions: notification

軽量版方針で進める。個別の設問は設けず、以下のConsolidated Summary Confirmationのみで確認する。

## Consolidated Summary Confirmation

notification Unitのnfr-design成果物を以下の内容で確定します。

**performance-design.md**: SMTP送信は非同期(Spring ApplicationEventのin-process async配送)で行い、イベント発行元の主処理をブロックしない(NFR1.2)。

**security-design.md**: メール本文に埋め込むトークンはURLの一部として送信するが、SMTP送信失敗時のログには含めない(NFR-DATA.1)。Mustacheテンプレート変数はHTMLエスケープして埋め込む(NFR-INJECTION.1)。

**scalability-design.md**: 単一インスタンス構成(NFR2.1)。EmailDispatchは非永続化のためデータ量起因の課題なし。

**reliability-design.md**: SMTP送信失敗時はログ記録のみでリトライしない(NFR-FAILSAFE.1、明示的トレードオフ)。

**observability-design.md**: 送信失敗のログ記録(機微情報除く)が主要な可観測性要件(NFR3.1)。

**logical-components.md**: notificationは以下2つの論理コンポーネントで構成する(ステートレスのためリポジトリを持たない)。イベントリスナー(6種のライフサイクルイベント〈AccountCreatedEvent等〉の購読、EmailDispatchへの変換)、サービス(Mustacheテンプレート描画〈java-mustache-processor〉、SMTP送信、送信失敗時のログ記録)。

**traceability.json**: nfr-requirementsで確定した各NFR項目を、上記の設計解へマッピングする。

[Answer]: Looks correct
