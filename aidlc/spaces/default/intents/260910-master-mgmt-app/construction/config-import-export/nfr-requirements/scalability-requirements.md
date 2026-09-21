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

# Scalability Requirements — config-import-export (U9)

`inception/requirements-analysis/requirements.md`のNFR3、`nfr-requirements-questions.md`の確定回答(Q1)に基づく、config-import-exportユニットのスケーラビリティ要件。

## 想定データ量・成長見込み

| Dimension | Current | 想定上限 | Scaling Mechanism |
|---|---|---|---|
| 設定ファイルに含まれるテーブル数 | — | 100 | 設定全体をメモリ上で処理する(NFR1.3) |
| カラム数の合計 | — | 約3,000 | 同上 |
| 翻訳数 | — | 約6,000 | 同上 |
| ロール数 | — | 50 | 同上 |
| 主権限数 | — | 約5,000 | 同上 |
| 同時に実行されるエクスポート・インポート | — | 合わせて1〜2件 | 排他は設けない(NFR2.4) |

- **根拠**: Q1=A。config-engineのスケーラビリティ要件の想定規模に、メニュー・ロール・グループ・権限を加えた見積もり。設定は、管理者が低頻度で扱う小規模なデータである。

## NFR3.1: config-import-exportのスケーラビリティ

```
NFR3.1: config-import-exportのスケーラビリティ
Current baseline: 未計測(新規開発)
Target capacity: NFR3のアプリ全体の想定利用規模(数十名程度)に対応する。設定のエクスポート・インポートは、管理者の低頻度の操作であり、同時に1〜2件を想定する
Growth model: 線形(業務プロファイル数・設定の項目数の緩やかな増加)
Scaling approach: 単一インスタンスで十分に対応できる規模であり、水平スケールの仕組みは持たない。設定ファイルの全体をメモリ上で処理する(ストリーミングは行わない)
Cost constraint: 本ワークフローのスコープでは具体的なインフラコストの制約は対象外(Operationフェーズは対象外)
Degradation policy: 想定規模の上限を超えると、応答時間とメモリの使用量が増える(リクエスト本体の大きさの上限は設けない。NFR2.2)。性能目標(NFR1.1・NFR1.2)の対象外
```

## 既知の制約: 複数インスタンス構成への非対応

取り込みのトランザクションは内部設定DB(H2)に対して行い、確定後に、各ユニットのインメモリのキャッシュを、そのインスタンスの中で再構築する(config-engineのNFR1.2・NFR3.1と同じ前提)。複数インスタンスを水平配置した場合、取り込みを行ったインスタンス以外は、古い設定のキャッシュを保持し続ける。この制約は、config-engineのスケーラビリティ要件の「既知の制約」と同じであり、本ユニットでは、対応しない。取り込みどうしの排他も、インスタンスの中に限られる(そもそも排他は設けない。NFR2.4)。
