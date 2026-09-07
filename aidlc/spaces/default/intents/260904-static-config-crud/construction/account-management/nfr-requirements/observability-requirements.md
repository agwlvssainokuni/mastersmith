# Observability Requirements: account-management

requirements.md NFR3(Twelve-Factor App + OpenTelemetry + 構造化ログ)を踏襲する。

## NFR3.1: 監査ログイベントによる可観測性

アカウント作成・編集・無効化の結果を、契約#5〜#8(監査ログイベント契約)に基づきAuditableActionOccurredEventとして発行する。actionTypeはACCOUNT_CREATED(BR1.5)・ACCOUNT_UPDATED(BR3.2)・ACCOUNT_DISABLED(BR4.2)の3種とする。イベント発行失敗は主処理をブロックしない。

## NFR3.2: 機微情報のログ非出力

本Unitはパスワード・パスワードハッシュを一切保持しないため、これらがログに出力されることはない。AuditableActionOccurredEventのtargetDescriptionはaccountIdの識別情報のみを含む(BR1.5/BR3.2/BR4.2)。
