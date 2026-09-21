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

# Scalability Design — config-import-export (U9)

`nfr-requirements/scalability-requirements.md`(NFR3.1)に基づく、config-import-exportユニットのスケーラビリティ設計。

## NFR3.1: スケーリングアーキテクチャ

- **単一インスタンス**: 想定規模(数十名、設定は数MB以下、同時のエクスポート・インポートは1〜2件)は、単一インスタンスで十分である。水平スケールの仕組み・負荷分散・データの分割は設けない。
- **ステートレス**: 本ユニットは、リクエストをまたぐ状態を持たない(取り込みの途中の状態は、リクエストの中の、ローカルな変数に限られる)。取り込みどうしの排他は設けない(NFR2.4)ため、ロックなどの共有の状態もない。

## メモリの見積もり

| 項目 | 見積もり(想定規模の上限) |
|---|---|
| リクエスト本体(JSON) | 数MB |
| `JsonNode`の木構造 | 本体の大きさの、10倍程度(数十MB) |
| ConfigDocument(値オブジェクト) | 数十MB以下 |
| 現在の設定の読み込み(3ユニット分、マップ) | 数十MB以下 |
| 1回の取り込みの合計 | 約100MB以内(見積もり) |

- 同時に2件を実行しても、約200MB以内である。JVMのヒープの設定は、本ユニットでは行わない(実行環境の設定。Infrastructure Design・Operationフェーズは対象外)。
- 想定規模を超えるファイル(本体の大きさに上限がない。NFR2.2)では、メモリの使用量が、この見積もりを超える。本体を、全件、メモリに読み込む方式であることを、設計上の前提として明記する。

## 成長への対応

- 業務プロファイルの増加(テーブル・カラムの増加)は、線形にメモリと時間を増やす。想定規模の上限を超える見込みが出た場合は、次の順に見直す。(1)性能の確認(NFR1.1・NFR1.2)で、目標を超えていないかを確認する。(2)本体の大きさの上限(NFR2.2)を設ける。(3)ストリーミングの読み取り・書き込みへ移行する。

## 既知の制約: 複数インスタンスへの非対応

複数インスタンス構成では、取り込みを行ったインスタンス以外の、各ユニットのキャッシュが、無効化されない(無効化は、そのインスタンスの中だけの操作)。この制約は、config-engineの設計と同じであり、本ユニットでは対応しない(NFR3.1)。Q1=C(確定後にキャッシュを無効化し、次の読み取りで、DBから遅延して読み込む)は、この制約を解消しない。ただし、将来、キャッシュの無効化を、インスタンス間で伝える仕組みを導入する場合の、拡張の点になる(無効化のフックが、ユニットごとに、1か所に集約されているため)。
