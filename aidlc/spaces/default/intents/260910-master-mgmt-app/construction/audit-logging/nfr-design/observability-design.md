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

# Observability Design — audit-logging (U7)

`construction/audit-logging/nfr-requirements/observability-requirements.md`(NFR5.1〜NFR5.4)、および`nfr-design-questions.md` Q5確定に基づく、audit-loggingユニットの可観測性設計。

## メトリクス実装(NFR5.1対応)

他の実装済みユニット(config-engine・permission-engine・data-import-export)と同水準で、`GET /api/audit-log`のHTTPリクエストレイテンシ・エラー率をMicrometer(Spring Boot標準)経由でOTELメトリクスとして計装する(FR14.1)。

```java
// 概念設計(実装はCode Generationで確定)
Timer.builder("audit_logging.get_audit_log.duration").register(meterRegistry);
Counter.builder("audit_logging.get_audit_log.error_count").register(meterRegistry);
```

これらのメトリクスは、アプリ全体のOTELエクスポート基盤(具体的なライブラリ選定はCI Pipelineで確定)経由でエクスポートされる。イベント購読からAuditLogEntry書き込み完了までの内部処理時間は計装対象外とする(NFR5.1)。

## 相関ID伝播(Q5確定)

`GET /api/audit-log`呼び出しおよびBR7.7の失敗時ログにおける相関ID(トレースID)は、呼び出し元・OTEL計装基盤(具体的なライブラリ選定はCode Generation/CI Pipelineで確定)が管理するトレースコンテキストにそのまま乗せる。audit-logging自身が独自の相関ID生成・伝播の仕組みを持つ必要はない(他ユニットと同一方針)。

## 構造化ログ(NFR5.2対応)

- BR7.7(記録失敗時)のログはERRORレベルの構造化ログ(JSON形式)とし、購読したイベントの内容(target・actor・occurredAt等)をそのまま含める(`security-design.md`で機微情報を含まないことを確認済み)。
- 通常の記録成功時は、業務運用上の観点からINFOレベルでの個別ログ出力は必須としない(記録自体がAuditLogEntryとして永続化されるため、二重のログ出力は不要と判断する)。

## 専用メトリクス・アラートを設けない領域(NFR5.3対応、Q3=B)

記録失敗(BR7.7)自体の発生頻度を追跡する専用メトリクス・アラートは、本MVPスコープでは設けない。通常の構造化ログ記録のみとする(`reliability-design.md`の残存リスク記載も参照)。

## ヘルスチェック(NFR5.4対応)

他の実装済みユニットと同様、アプリケーション全体のヘルスチェックエンドポイントに相乗りする。本ユニット固有の追加ヘルスチェックエンドポイントは設けない。

## Observability Anti-Requirements(除外事項、`observability-requirements.md`から継承)

- イベント購読からAuditLogEntry書き込みまでの内部処理時間の計装は行わない(NFR5.1)。
- 記録失敗の発生頻度に対する専用ダッシュボード・自動アラートは設けない(NFR5.3)。
