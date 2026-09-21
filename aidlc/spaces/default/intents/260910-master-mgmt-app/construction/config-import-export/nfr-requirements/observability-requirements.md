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

# Observability Requirements — config-import-export (U9)

`inception/requirements-analysis/requirements.md`のNFR5、`nfr-requirements-questions.md`の確定回答(Q5)に基づく、config-import-exportユニットの可観測性要件。

## NFR5.1: メトリクス

```
NFR5.1: メトリクス
要件: Spring Bootの標準の計装(HTTPサーバーのリクエスト数・レイテンシ・エラー率、エンドポイント別)を、そのまま使う。`GET /api/config/export`と`POST /api/config/import`の、レイテンシ・エラー率は、これで得られる
専用メトリクス: 取り込み・エクスポート固有のメトリクス(結果別のカウンター、所要時間のヒストグラム等)は出さない
根拠: Q5=B
```

- **取り込みの結果**: 成功・失敗の分類・件数は、監査イベント(BR9.16)と、開始・終了のログ(NFR5.2)で確認できる。
- **アラート**: 定義しない(Operationフェーズは、本ワークフローのスコープの対象外)。

## NFR5.2: ログ

```
NFR5.2: 取り込み・エクスポートの開始・終了のログ
要件: 取り込みと、エクスポートについて、開始と終了を、それぞれ1件、INFOの構造化ログ(JSON)で出す。リクエストIDが付く(NFR5)
取り込みの終了のログの項目: 操作者のuserId、結果(成功・失敗)、失敗の分類(failureCategory)、セクションごとの追加・更新・削除の件数(成功時)、誤りの総数(失敗時)、所要時間(ミリ秒)
エクスポートの終了のログの項目: 操作者のuserId、結果、所要時間(ミリ秒)
禁止: 設定ファイルの内容(ロール名・権限・翻訳などの値)、ファイル名、検証の誤りの個々の内容(位置・メッセージのキー)は、ログに出さない。誤りの個々の内容は、応答にだけ含める
根拠: Q5=B、project.md Mandated(認証情報等の非出力)、NFR2.5
```

- **認可の失敗**(401・403)は、取り込み・エクスポートの実行ではないため、この開始・終了のログの対象外とする。認証・認可の失敗は、authentication-serviceの認証フィルタと、permission-engineが、それぞれ扱う。
- **内部設定DBの障害**(503)・想定外の障害(500)は、ERRORのログに、例外の分類と、リクエストIDを出す(スタックトレースは、ログにだけ出し、応答には出さない。NFR2.6)。

## NFR5.3: 分散トレーシング

Spring Bootの標準の計装(トレースの伝播、Micrometer Tracing)に従い、OTELへエクスポートできること(NFR5)。本ユニット固有のスパンは定義しない。

## NFR5.4: ヘルスチェック

本ユニット固有のヘルスチェックは持たない。内部設定DBの疎通は、アプリケーション全体のヘルスチェック(Spring Boot Actuator)が担う。

## NFR5.5: ダッシュボード

本ユニット固有のダッシュボードは定義しない(Operationフェーズは対象外)。
