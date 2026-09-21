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

# Observability Design — config-import-export (U9)

`nfr-requirements/observability-requirements.md`(NFR5.1〜NFR5.5)に基づく、config-import-exportユニットの可観測性設計。確定回答(Q5=B)により、取り込み・エクスポート固有のメトリクスは設けない。

## NFR5.1: メトリクスの設計

- Spring Boot ActuatorのMicrometer標準の計装(`http.server.requests`)を使う。`uri`タグ(`/api/config/export`・`/api/config/import`)・`status`タグ・`outcome`タグで、エンドポイント別のレイテンシ・エラー率が得られる。
- 取り込み・エクスポート固有のカスタムメトリクスは定義しない。
- 取り込みの直後の読み取り(キャッシュの再読み込み。Q1=C)の所要時間は、各ユニットの読み取りの、既存の計装(config-engine・permission-engineの、既存のメトリクスの設計)に含まれる。本ユニットでは、追加しない。

## NFR5.2: ログの設計

構造化ログ(JSON)。既存のログの設定に従い、リクエストID(MDC)が付く。

| イベント | レベル | 項目 |
|---|---|---|
| 取り込みの開始 | INFO | `event`=`config.import.start`、`operatorUserId` |
| 取り込みの終了(成功) | INFO | `event`=`config.import.end`、`operatorUserId`、`outcome`=`SUCCESS`、セクションごとの追加・更新・削除の件数、`durationMs` |
| 取り込みの終了(失敗) | INFO | `event`=`config.import.end`、`operatorUserId`、`outcome`=`FAILURE`、`failureCategory`、`errorCount`(誤りの総数)、`durationMs` |
| エクスポートの開始・終了 | INFO | `event`=`config.export.start`・`config.export.end`、`operatorUserId`、`outcome`、`durationMs` |
| 想定外の障害(500・503) | ERROR | `event`=`config.import.error`(または`config.export.error`)、例外の分類(クラス名)、リクエストID。スタックトレースは、ログにだけ出す |
| 確定後のイベント発行の失敗 | ERROR | `event`=`config.import.event-publish-failed`、例外の分類 |

- **出さないもの**: 設定ファイルの内容・ファイル名・検証の誤りの個々の内容(位置・メッセージのキー)・ロール名・権限の値。`operatorUserId`は、内部のID(認証情報ではない)。
- 認可の失敗(401・403)は、この開始・終了のログの対象外(認証フィルタとpermission-engineが扱う)。

## NFR5.3: 分散トレーシング

Spring Bootの標準(Micrometer Tracing)に従う。本ユニット固有のスパンは定義しない。

## NFR5.4: ヘルスチェック

本ユニット固有のヘルスチェックは持たない。Q1=Cにより、確定後のキャッシュの食い違いを、ヘルスチェックで示す設計(Q1のAの案)は採らない。内部設定DBの疎通は、アプリケーション全体のヘルスチェック(Actuator)が担う。

## NFR5.5: ダッシュボード・アラート

定義しない(Operationフェーズは対象外)。
