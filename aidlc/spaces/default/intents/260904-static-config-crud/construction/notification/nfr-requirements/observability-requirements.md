# Observability Requirements: notification

requirements.md NFR3(Twelve-Factor App + OpenTelemetry + 構造化ログ)を踏襲する。

## NFR3.1: 送信失敗ログによる可観測性

SMTP送信失敗はEmailDispatchの内容(宛先・templateId、機微情報を除く)をログに記録する(BR3.1)。これが本Unitの主要な可観測性要件である。送信失敗の永続的な記録・追跡は行わない(entities.md、EmailDispatchは非永続化)。
