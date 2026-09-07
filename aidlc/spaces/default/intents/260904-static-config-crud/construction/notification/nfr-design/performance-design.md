# Performance Design: notification

## 応答速度の一般方針

requirements.md NFR1(厳密な数値目標なし)を踏襲する。

## SMTP送信の非ブロッキング性

イベントリスナーはSpring `@EventListener`(または`@Async`修飾のイベントリスナー)として実装し、イベント発行元(auth・account-management)の主処理をブロックしない(in-process async配送、NFR1.2)。SMTP送信自体の成否は発行元の処理完了後、別スレッドで非同期に行う。
