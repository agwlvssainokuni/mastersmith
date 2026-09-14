<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Performance Design — schema-introspector (U2)

`nfr-requirements/performance-requirements.md`(NFR1.1)、および`nfr-design-questions.md`の確定回答(Q1)に基づく、schema-introspectorユニットの性能設計。

## NFR1.1: 30秒以内の同期処理

- **実行方式**: `POST /api/config/schema-introspection`のHTTPリクエストスレッド内で同期的に処理する(Q1確定)。ジョブキュー・非同期処理基盤は導入しない。
- **処理の流れ**(単純化のための設計原則):
  1. 認可判定(BR2.8) → 2. RDBMS方言判定(BR2.3) → 3. 対象テーブル一覧の決定(BR2.2) → 4. 各テーブルのメタデータを1パスで走査(JDBC `DatabaseMetaData`) → 5. `TableConfigDraft`への変換 → 6. `writeTableConfigDraft`呼び出し(1回)。
  - 各テーブルのメタデータ取得はJDBC `DatabaseMetaData.getColumns`/`getPrimaryKeys`等の標準APIを1回ずつ呼び出す設計とし、対象RDBMSへの追加のカスタムクエリは行わない(往復回数を最小化する)。
- **キャッシュ**: 導入しない。本操作は1回限りの管理操作であり、結果を再利用する後続リクエストが存在しないため、キャッシュ層を設けるメリットがない。
- **想定処理時間**: NFR3.1(数十テーブル、1テーブルあたり数十カラム)の規模であれば、JDBCメタデータ呼び出しのオーバーヘッドを考慮しても30秒以内に収まる想定(scalability-design.md参照)。

## Performance Anti-Design(採用しない設計)

- ページング・遅延読み込みは適用しない。1回の呼び出しで対象範囲(BR2.2)の全メタデータを読み切る設計とする(部分結果を返す設計はwriteTableConfigDraftの一括書込み方針(BR2.7)と整合しないため)。
- 対象RDBMSへの接続プーリングの新設は行わない。既存のアプリケーション共通のデータソース接続プール(業務データ用RDBMS接続)をそのまま利用する。
