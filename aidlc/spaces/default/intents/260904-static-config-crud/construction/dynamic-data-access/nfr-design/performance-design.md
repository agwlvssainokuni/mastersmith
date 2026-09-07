# Performance Design: dynamic-data-access

## 応答速度の一般方針

requirements.md NFR1(厳密な数値目標なし)を踏襲する。

## 一覧取得のページネーション

RESTコントローラは一覧取得(BR1.2)でページネーション(page/size)を受け付け、動的クエリ実行コンポーネントのSQL実行時にOFFSET/LIMITへ変換する(NFR1.2)。

## クエリ実行

動的クエリ実行コンポーネントはNamedParameterJdbcTemplateを用いる。JDBCドライバ標準のプリペアドステートメントキャッシュ以上の独自キャッシュ層は設けない(業務データは頻繁に変更されうるため、キャッシュ導入は不整合リスクを招く)。
