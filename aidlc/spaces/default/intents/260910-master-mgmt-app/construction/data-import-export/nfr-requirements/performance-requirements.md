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

# Performance Requirements — data-import-export (U8)

`inception/requirements-analysis/requirements.md`のNFR1、`construction/data-import-export/functional-design/rules.md`(BR8.10)、および`nfr-requirements-questions.md`の確定回答に基づく、data-import-exportユニットの性能要件。

## NFR1.1: CSVインポート処理時間

- **Metric**: `importCsv`呼び出し1回あたりの処理時間(ファイル読み取り開始〜`ImportResult`返却まで。全行のストリーミング読み取り・型変換・validationRule適用・確定後の一括DBコミットを含む)
- **Target**: 10万行を5分以内
- **Percentile**: p95
- **Load condition**: 対象テーブルが1テーブルあたり最大10万行程度(NFR1想定規模)、CSVファイルの列数は対象テーブルのColumnConfig列数相当
- **Measurement method**: アプリケーション内メトリクス(処理開始〜終了のタイマー計測、行数とあわせて記録)
- **根拠**: Q1確定回答(A)。一覧・詳細画面の応答時間目標(NFR1本体、3秒以内p95)とは別に、バッチ的な一括処理としての目標値を設定する

## NFR1.2: CSVエクスポート処理時間

- **Metric**: `exportCsv`呼び出し1回あたりの処理時間(対象データのカーソル読み取り開始〜CSVストリーム出力完了まで)
- **Target**: 10万行を2分以内
- **Percentile**: p95
- **Load condition**: NFR1.1と同様
- **Measurement method**: アプリケーション内メトリクス
- **根拠**: Q2確定回答(A)。インポートより単純な処理(検証・型変換なし)であるため、より短い目標時間とする

## NFR1.3: ストリーミング処理によるメモリ使用量の制約

- **Metric**: CSVエクスポート・インポート処理中のアプリケーションヒープ使用量
- **Target**: 対象データ全件(最大10万行)を一括でメモリへ読み込まない(`rules.md` BR8.10)。エクスポートはDBカーソルからの逐次読み取り、インポートはCSVファイルの1回のストリーミング走査+変換後の軽量な行データ(列数×最大10万行のプリミティブ値)のみをメモリ上バッファに保持する(`nfr-requirements-questions.md` Q8確定: 一時テーブル方式は採用せず常にメモリ上配列)
- **Load condition**: 常時
- **Measurement method**: 負荷試験時のヒープ使用量モニタリング(JVMメトリクス)
- **根拠**: BR8.10・Q8確定。生のCSVファイル全体やHTTPリクエスト全体を複製保持しないことで、NFR1想定規模でのメモリ影響を限定的に抑える

## Performance Anti-Requirements(除外事項)

- 「CSVエクスポート・インポートは高速でなければならない」のような測定不能な目標は設定しない。NFR1.1・NFR1.2のとおり、具体的な行数・時間・パーセンタイルを明記する。
- 「無制限の行数に対応する」という目標は設定しない。NFR1想定規模(1テーブル最大10万行程度)を前提とし、それを超える規模への対応は本要件の対象外とする(スケーラビリティ要件側の`scalability-requirements.md`参照)。
