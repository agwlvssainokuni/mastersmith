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

# Observability Requirements — config-engine (U1)

`inception/requirements-analysis/requirements.md`のNFR5、および`nfr-requirements-questions.md`の確定回答（Q4）に基づく、config-engineユニットの可観測性要件。

## NFR5.1: 可観測性方針（Q4確定回答: A）

config-engineは内部呼び出し（同一プロセス内のJavaメソッド呼び出し）が中心であり、HTTPエンドポイントを直接公開しない（現行のC9契約は内部インタフェースのみ）。そのため、**config-engine固有の追加メトリクス・ログは設けない**。呼び出し元ユニット（list-engine, record-edit-engine等）が発行するHTTPリクエストのレイテンシ・エラー率メトリクス（NFR5、アプリ全体方針）でカバーされるとみなす。

## アプリ全体方針の適用

config-engineは、アプリ全体の可観測性方針（NFR5: HTTPリクエストのレイテンシ・エラー率メトリクス、ヘルスチェック、構造化ログのOTELエクスポート）に、モジュールとして統合される形で従う。具体的なOTELエクスポート実装・ローカル動作確認用コンテナ環境の構築は、Code Generation以降で全ユニット横断のNFR設計（3.3 NFR Design）の対象とする。

## ログ出力方針

- config-engineがConfigValidationException（fail-fast検証エラー）を送出する際、エラーの詳細（フィールド・ルール種別）はアプリケーションの構造化ログ（ERRORレベル）に記録される。パスワード等の認証情報は扱わないため、機微情報のログ出力に関する追加考慮は不要（`security-requirements.md`のデータ分類参照）。
- 将来の管理用REST API（`functional-spec.md`の未解決事項）を通じた設定変更操作は、W6のTranslationEntry登録・編集を含め、通常のHTTPリクエストログ（INFOレベル、操作の成功・失敗）として記録する。個人情報を含まないため、ログ内容の追加マスキングは不要。

## Observability Anti-Requirements（除外事項）

- config-engine固有のダッシュボード・アラートは設けない（Q4確定回答: A）。アプリ全体のダッシュボード・アラート方針に従う。
- 因果ではなく結果（症状）に基づくアラート設計はアプリ全体のNFR設計（3.3）に委ね、本ユニットのNFR要件としては具体化しない。
