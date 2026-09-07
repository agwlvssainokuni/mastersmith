# Performance Design: frontend-core

## 応答速度の一般方針

requirements.md NFR1(厳密な数値目標なし)を踏襲する。

## 一覧取得のページネーション

一覧画面コンポーネントは、バックエンドのページネーション(page/size)をそのまま利用し、フロントエンド側で全件取得後にクライアント側ページングする実装は行わない(NFR1.2)。
