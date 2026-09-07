# Observability Requirements: audit-log

## NFR3.1: Twelve-Factor App準拠・構造化ログ

requirements.md NFR3を踏襲する。アプリケーションはTwelve-Factor Appの原則に準拠し、OpenTelemetry対応・構造化ログを備える(プロジェクト全体の横断要件、本Unit固有の追加要件はない)。

## NFR3.2: 記録失敗時のログ

監査ログ記録処理が失敗した場合、actionType・occurredAt・発生した例外の内容を含む構造化ログをERRORレベルで出力する(BR1.2)。これは、audit-log自身の障害を運用者が把握するための最小限の可観測性要件である。

## メトリクス・アラート

audit-log自身についての専用ダッシュボード・アラート設計は、本プロジェクトの運用規模(個人利用中心、team.md)に照らして本ステージでは規定しない。記録失敗の構造化ログ(上記)が、必要に応じた事後調査の基盤となる。
