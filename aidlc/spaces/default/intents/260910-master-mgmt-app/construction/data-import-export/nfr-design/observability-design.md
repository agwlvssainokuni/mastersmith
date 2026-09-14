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

# Observability Design — data-import-export (U8)

`construction/data-import-export/nfr-requirements/observability-requirements.md`(NFR5.1〜NFR5.3)、および`nfr-design-questions.md` Q5確定に基づく、data-import-exportユニットの可観測性設計。

## メトリクス実装(NFR5.1対応)

Micrometer(Spring Boot標準)を用いて以下のメトリクスを実装する:

```java
// 概念設計(実装はCode Generationで確定)
Counter.builder("data_import_export.export.count").tag("table", tableConfigId).register(meterRegistry);
Timer.builder("data_import_export.export.duration").register(meterRegistry);
Counter.builder("data_import_export.import.count").tag("table", tableConfigId).register(meterRegistry);
Timer.builder("data_import_export.import.duration").register(meterRegistry);
Counter.builder("data_import_export.import.success_count").register(meterRegistry);
Counter.builder("data_import_export.import.error_count").register(meterRegistry);
```

これらのメトリクスは、アプリ全体のOTELエクスポート基盤(FR14/NFR5全体、後続のNFR設計で他ユニットと共通に具体化)経由でエクスポートされる前提とする。本ユニットはMicrometerのAPIに対して計装するのみで、エクスポート先(OTLP Collector等)の具体的な設定は本ユニットの設計対象外とする。

## ログ設計(NFR5.2対応)

`performance-design.md`のコンポーネント分割に従い、ログ出力は`ImportCommitter`(コミット層)が担う:

- 全体ロールバック時(BR8.7): `WARN`レベルで対象テーブル・実行者・エラー件数・処理時間をログ出力
- 成功時: `INFO`レベルで対象テーブル・実行者・処理行数・処理時間をログ出力
- 予期しない例外: `ERROR`レベルでスタックトレースを含めてログ出力(利用者向けレスポンスとは別)

CSVの行データ・セル値そのものはいずれのログレベルにも含めない(`security-design.md`の入力検証設計と合わせて、機密性の観点からも徹底する)。

## アラート(NFR5.3、対象外)

自動アラートは設けない(`observability-requirements.md` NFR5.3確定)。将来的にNFR1.1/NFR1.2の性能目標を継続的に下回るケースが観測された場合、Micrometerメトリクス(`data_import_export.import.duration`等)を用いたアラートルールの追加を検討する。
