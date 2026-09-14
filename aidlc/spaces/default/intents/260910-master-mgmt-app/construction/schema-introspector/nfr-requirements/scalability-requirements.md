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

# Scalability Requirements — schema-introspector (U2)

`inception/requirements-analysis/requirements.md`のNFR3、および`nfr-requirements-questions.md`の確定回答に基づく、schema-introspectorユニットのスケーラビリティ要件。

## NFR3.1: 対象スキーマ規模

- **Load projection**: 1回のイントロスペクション実行が対象とするのは、数十テーブル程度(1テーブルあたり数十カラム)を主な想定とする(Q2確定)。
- **Concurrency**: 単一管理者による低頻度・逐次実行を前提とし、高頻度・多数同時アクセスは想定しない。
- **Growth model**: 該当なし。本ユニットは永続データを持たず、データ量の増大に伴うスケール設計対象外。
- **Scaling approach**: embedded(他serviceユニットと同一プロセス内で動作)であり、本ユニット単体での水平/垂直スケール設計は行わない。アプリ全体のスケールに従属する。

## 既知の制約

- 数百テーブル以上の大規模スキーマに対する明示的な性能検証・分割実行(バッチ処理化、進捗表示付き非同期実行等)は、本MVPスコープの主要な対象外とする(Q2確定、`performance-requirements.md`のPerformance Anti-Requirements参照)。将来、より大規模なスキーマへの対応が必要になった場合は、非同期実行・進捗通知の設計を追加検討する。
- 複数管理者が同時に同一スキーマへイントロスペクションを実行した場合の明示的な排他制御は行わない。config-engine側の`writeTableConfigDraft`(テーブル単位の既存設定有無チェック、`construction/config-engine/functional-design/rules.md` BR1.8)に委ねており、極めて低頻度の管理操作であるため、稀な競合(同時に同じ新規テーブルを対象にした場合の重複試行等)は許容されるリスクとして受け入れる。
