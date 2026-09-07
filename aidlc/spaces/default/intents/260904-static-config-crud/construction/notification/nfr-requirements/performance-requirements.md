# Performance Requirements: notification

## NFR1.1: 応答速度の一般方針

requirements.md NFR1を踏襲する。厳密な数値目標は設けない。

## NFR1.2: SMTP送信の非ブロッキング性

SMTP送信の成否は、イベント発行元(auth・account-management)の主処理をブロックしない(BR3.1)。送信失敗時もイベント発行元の処理には影響しない。
