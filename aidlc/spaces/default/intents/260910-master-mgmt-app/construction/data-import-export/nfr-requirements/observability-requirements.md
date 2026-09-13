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

# Observability Requirements — data-import-export (U8)

`inception/requirements-analysis/requirements.md`のNFR5、および`nfr-requirements-questions.md`の確定回答に基づく、data-import-exportユニットの可観測性要件。詳細なOTELエクスポート方式・構造化ログの全体基盤はFR14/NFR5(全体)に基づき後続のNFR設計(3.3)で具体化する。

## NFR5.1: メトリクス要件

- **Application metrics**:
  - `data_import_export.export.count`(カウンタ、対象テーブル別)
  - `data_import_export.export.duration`(ヒストグラム、`performance-requirements.md` NFR1.2の目標値との比較に用いる)
  - `data_import_export.import.count`(カウンタ、対象テーブル別)
  - `data_import_export.import.duration`(ヒストグラム、NFR1.1の目標値との比較に用いる)
  - `data_import_export.import.success_count` / `data_import_export.import.error_count`(カウンタ)
- **Retention**: アプリ全体のOTEL基盤の既定方針(NFR5全体、後続のNFR設計で確定)に従う
- **根拠**: `nfr-requirements-questions.md` Q6確定(A)

## NFR5.2: ログ要件

| Log Level | 用途 | 内容 |
|---|---|---|
| WARN | CSVインポートが全体ロールバックとなった場合(BR8.7) | 対象テーブル・実行者・エラー件数・処理時間。個別の行データ・CSVの内容そのものは含めない |
| INFO | CSVエクスポート・インポートの実行完了(成功時) | 対象テーブル・実行者・処理行数・処理時間 |
| ERROR | 予期しない例外(ファイルI/Oエラー等、BR8.5の通常のバリデーションエラーではないもの) | スタックトレースを含む(開発者向け診断用。利用者へは`rules.md` BR8.6のとおりフィールド単位のメッセージのみ返す) |

- **禁止事項**: CSVの行データ・セル値そのものをログへ出力しない(業務データの機密性は業務プロファイル依存であり、ログへの漏えいを避ける、`security-requirements.md`参照)
- **根拠**: `nfr-requirements-questions.md` Q6確定(A)。詳細なアラート(Q6選択肢B)は今回のMVPスコープでは設けない

## NFR5.3: アラート要件(今回のMVPスコープでは対象外)

- **方針**: `nfr-requirements-questions.md` Q6確定(A、Bは不採用)により、処理時間閾値超過等の自動アラートは設けない。WARNレベルのログ出力(NFR5.2)による事後確認のみとする
- **将来の拡張**: 運用開始後、NFR1.1/NFR1.2の性能目標を継続的に下回るケースが観測された場合、アラート追加を検討する

## Observability Anti-Patterns(避けるべき事項の確認)

- CSVの行データ・セル値をログ・トレースへ出力しない(NFR5.2参照)。
- 実行単位のメトリクス(NFR5.1)と監査ログ(`rules.md` BR8.9のImportExecutedEvent)は目的が異なる別の仕組みであり、混同しない。メトリクスは運用監視用、監査ログは操作の証跡用。
