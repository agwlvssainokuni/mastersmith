# Observability Design: notification

requirements.md NFR3を踏襲する。

## 送信失敗ログによる可観測性

SMTP送信失敗はEmailDispatchの内容(宛先・templateId、機微情報を除く)をログに記録する(BR3.1、NFR3.1)。これが本Unitの主要な可観測性要件である。送信失敗の永続的な記録・追跡は行わない(entities.md、EmailDispatchは非永続化)。

## メトリクス・分散トレーシング

Spring Boot Actuator + Micrometerの標準メトリクス、およびSpring Boot標準のOpenTelemetry自動計装に委ね、本Unit固有の追加実装は行わない。
