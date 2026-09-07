# Observability Requirements: auth

requirements.md NFR3(Twelve-Factor App + OpenTelemetry + 構造化ログ)を踏襲する。

## NFR3.1: 監査ログイベントによる可観測性

ログイン・自己サービス操作の結果を、契約#5〜#8(監査ログイベント契約)に基づきAuditableActionOccurredEventとして発行する(BR8.1)。actionTypeはLOGIN_SUCCESS・LOGIN_FAILED・LOGIN_LOCKED・SELF_SERVICE_PROFILE_CHANGEDの4種とする。これがログイン試行状況・アカウントロック発生・自己サービス操作を運用者が事後追跡するための主要な可観測性要件を兼ねる。イベント発行失敗は主処理をブロックしない(BR8.1違反時挙動、契約#5〜#8のasync仕様どおり)。

## NFR3.2: 機微情報のログ非出力

パスワード平文、Argon2ハッシュ値、JWTアクセストークン・リフレッシュトークンの実トークン値は、アプリケーションログ・監査ログのいずれにも出力しない。AuditableActionOccurredEventのtargetDescriptionはaccountIdの識別情報のみを含み、機微情報を含まない(BR8.1)。
