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

# Performance Requirements — config-engine (U1)

`inception/requirements-analysis/requirements.md`のNFR1、および`nfr-requirements-questions.md`の確定回答に基づく、config-engineユニットの性能要件。

## NFR1.1: 内部API呼び出しレイテンシ

- **Metric**: `getTableConfig`/`getColumnConfigs`/`getOptimisticLockColumn`等、ConfigEngineApi（内部Javaインタフェース、C9契約）の呼び出し応答時間
- **Target**: 50ms以内
- **Percentile**: p95
- **Load condition**: NFR3の想定利用規模（同時アクセス最大50ユーザー）下
- **Measurement method**: アプリケーション内メトリクス（メソッド呼び出しの開始〜終了時間を計測。同一プロセス内呼び出しのためネットワーク往復は含まない）
- **根拠**: Q1確定回答（A）。config-engineはlist-engine/record-edit-engine等ほぼ全ユニットから高頻度に呼び出されるため、自身のレイテンシがアプリ全体のNFR1（一覧・詳細画面3秒以内p95）を圧迫しないよう、個別の目標を設定する

## NFR1.2: 設定データキャッシュによる性能担保

- **Metric**: 内部設定DB（H2）への問い合わせ回数
- **Target**: 通常のリクエスト処理経路（getTableConfig/getColumnConfigs等の読み取り）では、内部設定DBへの都度アクセスを発生させない
- **Load condition**: 常時
- **Measurement method**: アプリケーション内メモリキャッシュのヒット率（実質100%を期待。キャッシュミスは起動直後・キャッシュ更新直後のみ）
- **根拠**: Q2確定回答（A）。起動時に全設定（TableConfig/ColumnConfig/TranslationEntry、Q5の想定データ量なら数百〜数千件規模）をメモリへ読み込みキャッシュし、管理画面からの更新時にキャッシュを更新する。これによりNFR1.1の50ms目標を安定して達成する

## Performance Anti-Requirements（除外事項）

- 「ConfigEngineは高速でなければならない」のような測定不能な目標は設定しない。NFR1.1のとおり、具体的なメトリクス・閾値・パーセンタイル・負荷条件を明記する。
- schema-introspectorからの初期ドラフト生成（`writeTableConfigDraft`）や設定インポート（`importConfigSet`）は、管理者操作に伴う低頻度処理であり、NFR1.1の高頻度読み取りパスの対象外とする。個別の性能目標は設けない。
