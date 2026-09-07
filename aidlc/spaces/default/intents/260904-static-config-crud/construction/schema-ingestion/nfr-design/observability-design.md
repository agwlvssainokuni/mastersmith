# Observability Design: schema-ingestion

requirements.md NFR3(Twelve-Factor App + OpenTelemetry + 構造化ログ)を踏襲する。

## 構造化ログ

接続テスト・スキーマ走査の失敗時、対象DbConnectionの識別情報(name等、認証情報は含めない)と例外内容(security-design.mdのマスク処理適用後)を含む構造化ログをERRORレベルで出力する。

## メトリクス・分散トレーシング

Spring Boot Actuator + Micrometerの標準メトリクス、およびSpring Boot標準のOpenTelemetry自動計装に委ね、本Unit固有の追加実装は行わない。

## アラート・ダッシュボード

自宅サーバ1台での個人利用のため、専用のアラートルール・ダッシュボードは設計しない。
