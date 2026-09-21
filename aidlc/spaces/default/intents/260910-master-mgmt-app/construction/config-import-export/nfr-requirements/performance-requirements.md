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

# Performance Requirements — config-import-export (U9)

`inception/requirements-analysis/requirements.md`のNFR1、`nfr-requirements-questions.md`の確定回答(Q1・Q6)に基づく、config-import-exportユニットの性能要件。本ユニットが扱うのは設定(テーブル・カラム・翻訳・メニュー・ロール・権限)だけであり、業務データの行数に依存する性能課題はない。設定のエクスポート・インポートは、管理者が低頻度で行う操作である。

## NFR1.1: 設定のエクスポートの応答時間

```
NFR1.1: 設定のエクスポート(GET /api/config/export)の応答時間
Metric: 応答時間(リクエスト受付からレスポンス送出完了まで)
Target: 3秒以内
Percentile: p95
Load condition: NFR1.3の想定規模の上限の設定で、同時に実行されるエクスポート・インポートが合わせて1〜2件のとき
Measurement method: Build and Testの性能確認で、想定規模の上限の設定を用意して計測する(Spring Bootの標準のHTTPサーバーメトリクスで、エンドポイント別のレイテンシを取得できる)
```

- **根拠**: Q1=A。NFR1(画面・APIの応答時間3秒以内、p95)を、エクスポートにもそのまま適用する。
- **範囲**: 3つのユニット(config-engine・menu-navigation・permission-engine)からの読み取りと、自然キーへの変換、JSONの生成を含む。

## NFR1.2: 設定のインポートの応答時間

```
NFR1.2: 設定のインポート(POST /api/config/import)の応答時間
Metric: 応答時間(リクエスト受付からレスポンス送出完了まで。検証と反映の全体)
Target: 10秒以内
Percentile: p95
Load condition: NFR1.3の想定規模の上限の設定で、同時に実行されるエクスポート・インポートが合わせて1〜2件のとき
Measurement method: Build and Testの性能確認で、想定規模の上限の設定ファイルを用意して計測する
```

- **根拠**: Q1=A。インポートは、全件の検証(3つのユニットの検証専用メソッドの呼び出しを含む)と、1つのトランザクションでの全置換を含むため、NFR1の3秒より長い目標とする。低頻度の管理者操作であり、利用者を待たせる度合いが限られる。
- **性格**: この10秒は、確認する性能の目標であり、実行時には強制しない(Q6=A)。目標を超えても、取り込みは完了するまで続く。実行時の上限は置かない(NFR4.3)。目標を満たせない場合は、Build and Testの結果として報告し、検証や反映の方式(一括の書き込み等)の見直しを、Code Generationの修正として扱う。

## NFR1.3: 想定規模(性能目標の前提)

| 項目 | 想定の上限 |
|---|---|
| テーブル(TableConfig) | 100 |
| カラム(ColumnConfig)の合計 | 約3,000 |
| 翻訳(TranslationEntry) | 約6,000 |
| ロール | 50 |
| 主権限(PrimaryPermission) | 約5,000 |
| 設定ファイルの大きさ | 数MB以下 |

- **根拠**: Q1=A。config-engineのスケーラビリティ要件(テーブルは数十〜百程度、カラムはテーブルあたり数個〜数十個、翻訳は(テーブル数+カラム数)×2言語程度)に、メニュー・ロール・グループ・権限を加えた見積もり。NFR1.1・NFR1.2の目標は、この上限の規模で成り立つことを要件とする。

## 性能に関する除外

- 「エクスポート・インポートは高速でなければならない」のような測定できない目標は置かない。上のNFR1.1・NFR1.2のとおり、指標・閾値・パーセンタイル・負荷条件を明記する。
- 想定規模を超える設定ファイルでの性能は、目標の対象外とする。ただし、リクエスト本体の大きさの上限は置かないため、超えた場合も処理は試みられる(NFR2.2)。
- インポートの実行時間の上限(タイムアウト)は置かない(NFR4.3)。
