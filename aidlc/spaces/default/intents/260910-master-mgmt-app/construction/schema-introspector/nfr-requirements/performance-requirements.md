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

# Performance Requirements — schema-introspector (U2)

`inception/requirements-analysis/requirements.md`のNFR1、および`nfr-requirements-questions.md`の確定回答に基づく、schema-introspectorユニットの性能要件。

## NFR1.1: スキーマイントロスペクション操作の応答時間目標

- **Metric**: `POST /api/config/schema-introspection`(C8)の応答時間
- **Target**: 30秒以内
- **Percentile**: 該当なし。単一の管理者による低頻度の逐次実行が前提であり、統計的パーセンタイルではなく単一実行あたりの上限値として定義する(Q1確定)
- **Load condition**: 対象スキーマは数十テーブル程度(1テーブルあたり数十カラム)を主な想定とし、同時実行は想定しない(Q2確定)
- **Measurement method**: アプリケーション内メトリクス(リクエスト受付からレスポンス返却までの経過時間、observability-requirements.mdのメトリクス参照)
- **根拠**: NFR1は一覧・詳細画面の応答時間目標(3秒以内、95パーセンタイル)を定めるが、本操作は高頻度の画面応答パスとは性質が異なる管理操作であるため、Q1確定(B)により緩和した個別目標を設定する。

## Performance Anti-Requirements(除外事項)

- 「スキーマ読み込みは高速でなければならない」のような測定不能な目標は設定しない。NFR1.1のとおり、具体的な上限値・負荷条件を明記する。
- 数百テーブル以上の大規模スキーマに対する性能保証は本MVPスコープの対象外とする(Q2確定。scalability-requirements.mdの「既知の制約」参照)。
