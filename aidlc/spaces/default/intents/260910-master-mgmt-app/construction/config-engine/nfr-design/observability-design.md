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

# Observability Design — config-engine (U1)

`construction/config-engine/nfr-requirements/observability-requirements.md`（NFR5.1: config-engine固有の追加メトリクス・ログなし）を踏まえた設計。

## メトリクス

- config-engine固有のメトリクスは追加しない（NFR5.1、Q4確定）。アプリ全体のOTELエクスポート（HTTPリクエストのレイテンシ・エラー率）が、呼び出し元（list-engine等）の計測を通じて間接的にconfig-engineの遅延影響をカバーする。

## ログ

- fail-fast検証エラー（`ConfigValidationException`）は、ERRORレベルの構造化ログ（JSON形式、フィールド・ルール種別を含む）として出力する（アプリ全体の構造化ログ方針、NFR5に整合）。パスワード等の機微情報は扱わないため追加のマスキングは不要。
- 管理画面からの設定変更操作は、INFOレベルの構造化ログ（操作種別・対象・結果）として出力する。

## トレーシング

- config-engineの呼び出しは同一プロセス内のJavaメソッド呼び出しであり、サービス境界を跨がないため、分散トレーシング（X-Ray/Jaeger相当）の計装対象外とする。呼び出し元が発行するHTTPリクエストの相関ID（correlation ID / trace ID）は、`ConfigModelStore`のログ出力にMDC（Mapped Diagnostic Context）経由で伝播させ、ログの追跡可能性のみ確保する。

## SLI/SLO

- config-engine単体のSLI/SLOは定義しない（NFR5.1）。アプリ全体のSLI/SLO（`nfr-requirements`で個別定義されていない場合はOperationフェーズで扱う）に統合される。

## アラート

- config-engine固有のアラートルールは設けない（NFR5.1）。fail-fast起動失敗は、アプリケーションプロセスの起動失敗そのものとしてインフラ監視（Operationフェーズ、現状スコープ外）で検知される想定。
