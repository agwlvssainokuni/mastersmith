# Performance Design: account-management

## 応答速度の一般方針

requirements.md NFR1(厳密な数値目標なし)を踏襲する。

## 一覧取得のページネーション

RESTコントローラは、アカウント一覧取得(GET /api/admin/accounts)で既定page=0・size=20のページネーションを受け付け、サービスコンポーネント経由でauth(契約#4)の一覧取得呼び出しへそのまま渡す(NFR1.2)。

## キャッシュ

キャッシュ層は設けない。本Unit自身はデータを保持せず、auth・permissionの応答をそのまま合成するのみであるため、キャッシュを導入すると元データとの不整合リスク(無効化タイミングの複雑化)を招く。
