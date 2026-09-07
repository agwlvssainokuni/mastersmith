# Performance Requirements: account-management

## NFR1.1: 応答速度の一般方針

requirements.md NFR1を踏襲する。厳密な数値目標は設けない。著しい遅延の兆候(例: 一覧取得に数秒以上かかる)が確認された場合は、別途性能検証を行う。

## NFR1.2: 一覧取得のページネーション

アカウント一覧取得(GET /api/admin/accounts)は既定page=0・size=20のページネーションを行う(BR2.1)。page・size・sortを契約#4の一覧取得呼び出しへそのまま渡すことで、一覧応答のペイロードサイズを制限する。
