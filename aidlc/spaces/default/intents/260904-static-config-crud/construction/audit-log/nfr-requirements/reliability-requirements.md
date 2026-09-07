# Reliability Requirements: audit-log

## 可用性目標

自宅サーバ1台構成(team.md Deployment)であり、マルチAZ・自動フェイルオーバー等の高可用性インフラは対象外とする。SLA/SLO形式の数値目標(例: 99.9%可用性)は設けない。

## NFR-RESILIENCE.1: 記録失敗の非伝播

監査ログの記録処理で発生した例外は、発行元Unit(config-management・dynamic-data-access・auth・account-management)の主処理へ伝播させない(BR1.2)。記録が失敗しても、発行元の本来の操作(設定変更・業務データ操作・アカウント操作)は正常に完了する。これにより、audit-log自身の障害が他Unitの可用性に影響しない設計とする。

## バックアップ・リカバリ

内部H2データストア全体(AuditLogEntry・AuditLogSettingsを含む)のバックアップ方針は、本Unit固有の要件ではなく、システム全体のデプロイ・運用方針(Infrastructure Design/Deployment Pipelineステージ)で確定する。
