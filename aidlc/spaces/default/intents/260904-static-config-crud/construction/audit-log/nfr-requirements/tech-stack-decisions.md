# Tech Stack Decisions: audit-log

audit-log固有の追加の技術選定はなく、プロジェクト全体で確定済みの技術スタックをそのまま用いる。

| 項目 | 選定 | 根拠 |
|---|---|---|
| 言語・フレームワーク | Java 25 / Spring Boot | プロジェクト全体の標準(scope-document.md) |
| ビルドツール | Gradle | プロジェクト全体の標準 |
| 永続化先 | 内部H2データストア(`ms_`接頭辞、NFR5) | AuditLogEntry・AuditLogSettingsを保持 |
| イベント連携 | Spring `ApplicationEventPublisher` / `@EventListener` | 4つの発行元Unit(config-management・dynamic-data-access・auth・account-management)からのAuditableActionOccurredEventを疎結合に購読するため(contract-summary.md #5〜#8) |
