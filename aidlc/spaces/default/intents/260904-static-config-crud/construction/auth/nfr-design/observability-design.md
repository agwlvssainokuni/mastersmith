# Observability Design: auth

requirements.md NFR3を踏襲する。

## 監査ログイベントによる可観測性

ログイン・自己サービス操作の結果を、契約#5〜#8に基づきAuditableActionOccurredEventとして発行する(BR8.1)。actionTypeはLOGIN_SUCCESS・LOGIN_FAILED・LOGIN_LOCKED・SELF_SERVICE_PROFILE_CHANGEDの4種。イベント発行失敗は主処理をブロックしない。

## 機微情報のログ非出力

パスワード平文、Argon2ハッシュ値、JWTアクセストークン・リフレッシュトークンの実トークン値は、アプリケーションログ・監査ログのいずれにも出力しない。AuditableActionOccurredEventのtargetDescriptionはaccountIdの識別情報のみを含む。

## メトリクス・分散トレーシング

Spring Boot Actuator + Micrometerの標準メトリクス、およびSpring Boot標準のOpenTelemetry自動計装に委ね、本Unit固有の追加実装は行わない。
