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

# Observability Requirements — schema-introspector (U2)

`inception/requirements-analysis/requirements.md`のNFR5、および`nfr-requirements-questions.md`の確定回答(Q3)に基づく、schema-introspectorユニットの可観測性要件。

## NFR5.1: メトリクス・ログ・トレーシング方針(Q3確定回答: A)

OTEL基盤へエクスポートする(NFR5)。

### メトリクス

| メトリクス名 | 種別 | 説明 |
|---|---|---|
| `schema_introspection_duration_seconds` | Histogram | 1回の`POST /api/config/schema-introspection`実行にかかった時間 |
| `schema_introspection_tables_total` | Counter | 読み取り対象としたテーブル数(累積) |
| `schema_introspection_generated_total` | Counter | 新規生成された`TableConfig`件数(累積、既存スキップ分は含まない) |
| `schema_introspection_failures_total` | Counter | 失敗(403/422)件数(累積) |

### ログ

構造化ログ(JSON、リクエストID付与、NFR5)として以下を記録する(Q3確定)。

| タイミング | レベル | 内容 |
|---|---|---|
| 実行開始 | INFO | schemaName、tableNames(指定時) |
| 実行完了(成功) | INFO | 対象テーブル数、生成件数、所要時間 |
| 実行失敗 | ERROR | 失敗理由(権限不足/接続失敗/読み取り失敗等)、リクエストID |

パスワード・接続文字列等の機微情報はログに含めない(security-requirements.md NFR2.2)。

### トレーシング

他ユニットと同様、W3C Trace Context によるトレースコンテキスト伝搬に参加する。`POST /api/config/schema-introspection`のHTTPスパンから、config-engineの`writeTableConfigDraft`呼び出しまでを1つのトレースとして追跡できるようにする。

### アラート・ダッシュボード

- 本操作は低頻度の管理操作であり、エラー率ベースの常時監視アラートは設定しない(実行頻度が低く、閾値判定が意味をなさないため)。失敗ログ(ERROR)は、アプリケーション全体のエラーログ監視の一部として捕捉される。
- 専用ダッシュボードは設ける必要性が低いと判断し設けない。上記メトリクスはアプリケーション全体の監視ダッシュボードに含める。
