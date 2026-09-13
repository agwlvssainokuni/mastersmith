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

# Scalability Requirements — data-import-export (U8)

`inception/requirements-analysis/requirements.md`のNFR3、および`nfr-requirements-questions.md`の確定回答に基づく、data-import-exportユニットのスケーラビリティ要件。

## Capacity Planning

| Dimension | Current(想定) | 備考 |
|---|---|---|
| 1テーブルあたりの行数 | 最大10万行程度 | NFR1(全体)のデータ規模想定を踏襲。`performance-requirements.md` NFR1.1/NFR1.2の性能目標はこの規模を前提とする |
| 同時実行ユーザー数 | 最大50ユーザー | NFR3(全体)の想定利用規模(前身ツールMasterMeisterの約10名規模からの拡大) |
| 同一テーブルへの同時インポート実行数 | 制限なし | `nfr-requirements-questions.md` Q5確定。特別な排他制御は設けない |

## NFR3.1: 水平スケーリングへの対応

- **要件**: DataImportExportは状態を持たないサービスとして実装し(CSVエクスポート・インポート処理は呼び出しごとに完結し、リクエスト間で共有される可変状態を持たない)、アプリケーションインスタンスを水平に追加することで同時実行数の増加に対応できる設計とする
- **根拠**: NFR3(全体、同時接続数が想定を超えて拡大しても線形にリソースを追加することで対応できる設計)を踏襲。BR8.10の一時バッファ(メモリ上配列)はリクエストローカルであり、インスタンス間で共有しないため、この前提を妨げない

## NFR3.2: 同時実行制御を設けないことによるスケーラビリティ上の限界(意図的なスコープ判断)

- **要件**: `nfr-requirements-questions.md` Q5確定により、同一テーブルへの同時インポート実行数に上限を設けない。DBレベルの通常のトランザクション分離レベル・行ロックに競合解決を委ねる
- **既知の限界**: 同一テーブルへ大量行(10万行規模)のインポートが複数同時に実行された場合、DB接続プールの枯渇やトランザクションのロック待ちにより、NFR1.1(5分以内)の性能目標を満たせない可能性がある。この限界はMVPスコープでは許容し、将来必要になった場合にQ5の方針(排他制御の導入)を再検討する
- **根拠**: `nfr-requirements-questions.md` Q5確定(A)

## NFR3.3: 大量データ処理時のメモリスケーラビリティ

- **要件**: `performance-requirements.md` NFR1.3(ストリーミング処理)により、行数の増加に対してメモリ使用量が線形以下で増加する設計とする。NFR1想定規模(10万行)を超える将来のデータ量増加に備え、一時テーブル方式への切り替えを拡張余地として残す(`rules.md` BR8.10のnotes、`nfr-requirements-questions.md` Q8確定でMVPスコープでは常にメモリ上配列を採用)
- **Growth model**: 線形(業務データの行数増加に比例)
- **Scaling approach**: 現時点(MVP)はアプリケーションメモリ上のバッファリングのみ。将来、NFR1想定規模を大きく超える場合は一時テーブル方式への切り替えを検討する
- **根拠**: `nfr-requirements-questions.md` Q8確定(A)
