# Observability Design: account-management

requirements.md NFR3を踏襲する。

## 監査ログイベントによる可観測性

アカウント作成・編集・無効化の結果を、契約#5〜#8に基づきAuditableActionOccurredEventとして発行する。actionTypeはACCOUNT_CREATED・ACCOUNT_UPDATED・ACCOUNT_DISABLEDの3種(NFR3.1)。イベント発行失敗は主処理をブロックしない。

## 機微情報のログ非出力

本Unitはパスワード・パスワードハッシュを一切保持しないため、これらがログに出力されることはない(NFR3.2)。AuditableActionOccurredEventのtargetDescriptionはaccountIdの識別情報のみを含む。

## メトリクス・分散トレーシング

Spring Boot Actuator + Micrometerの標準メトリクス、およびSpring Boot標準のOpenTelemetry自動計装に委ね、本Unit固有の追加実装は行わない。
