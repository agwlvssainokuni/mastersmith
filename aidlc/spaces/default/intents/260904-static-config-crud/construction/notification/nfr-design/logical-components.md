# Logical Components: notification

## コンポーネント構成

notificationはステートレス(entities.md、EmailDispatchは非永続化)であり、以下2つの論理コンポーネントで構成する。リポジトリコンポーネントは持たない。

| コンポーネント | 責務 | 依存 |
|---|---|---|
| イベントリスナー | 6種のライフサイクルイベント(AccountCreatedEvent、AccountRegistrationCompletedEvent、AccountInfoChangedEvent、PasswordChangedEvent、PasswordResetRequestedEvent、EmailChangeRequestedEvent)の購読、EmailDispatchへの変換(BR1.1〜BR1.6) | サービス |
| サービス | Mustacheテンプレート(java-mustache-processor)による本文描画、`<title>`要素のSubject採用(BR2.1)、SMTP送信、送信失敗時のログ記録(BR3.1) | (SMTPサーバ) |

## 障害ドメインとブラストラディウス

本Unitの障害(SMTP送信失敗等)はメール送信機能自体に限定される。イベントリスナーはイベント発行元(auth・account-management)の主処理をブロックしない非同期配送(in-process async)であるため、notification自身の障害がイベント発行元の処理に影響することはない。

## 共有リソース

本Unitは内部H2データストアを使用しない。SMTPサーバへの接続情報(ホスト・ポート・認証情報)は、他Unitとは独立した設定として保持する。
