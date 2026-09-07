# Performance Requirements: dynamic-data-access

## NFR1.1: 応答速度の一般方針

requirements.md NFR1を踏襲する。厳密な数値目標は設けない。著しい遅延の兆候(例: 一覧取得に数秒以上かかる)が確認された場合は、別途性能検証を行う。

## NFR1.2: 一覧取得のページネーション

一覧画面はページネーション(page・size、BR1.2)を受け付け、一覧応答のペイロードサイズを制限する。表示列はTableConfig.columns[].listOrderに基づき決定する。
