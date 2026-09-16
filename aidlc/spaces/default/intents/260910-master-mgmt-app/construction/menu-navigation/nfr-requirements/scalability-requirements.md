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

# Scalability Requirements — menu-navigation (U6)

`inception/requirements-analysis/requirements.md`のNFR3、および`nfr-requirements-questions.md`の確定回答に基づく、menu-navigationユニットのスケーラビリティ要件。

## NFR3.1: MenuItem件数・階層深さの想定規模

- **Load projection**: MenuItem総数は数十〜百件程度、階層深さは3〜4階層程度を主な想定とする(Q3確定=A)。NFR3(想定利用規模: 数十名程度の業務担当者向け)に対応する規模感。
- **Concurrency**: `GET /api/menu`は複数利用者からの同時アクセスを想定する(トップ画面・サイドバーの初期表示のたびに呼び出される)。読み取り専用のためロック競合は発生しない。
- **Growth model**: 業務メニューの追加は業務担当者の設定操作(`/api/menu-items`)による緩やかな増加を想定し、急激な増大は想定しない。
- **Scaling approach**: embedded(他serviceユニットと同一プロセス内で動作)であり、本ユニット単体での水平/垂直スケール設計は行わない。アプリ全体のスケールに従属する。

## 既知の制約

- 数千件規模のMenuItem・5階層以上の深い階層に対する明示的なキャッシュ・非再帰アルゴリズムの設計は、本MVPスコープの対象外とする(Q3確定=A、`performance-requirements.md`のPerformance Anti-Requirements参照)。将来、より大規模なメニュー構成への対応が必要になった場合は、キャッシュ層・非再帰アルゴリズムの設計を追加検討する。
- `POST/PUT/DELETE /api/menu-items`は低頻度の管理操作であるため、複数管理者が同時に同一MenuItemを変更した場合の明示的な排他制御(楽観ロック等)は行わない。稀な競合は許容されるリスクとして受け入れる。NFR4が定める楽観ロックによる同時更新競合検出はFR6.3(業務データの編集画面、record-edit-engineの責務)に限定されたものであり、`MenuItem`(設定情報)には適用範囲外であるため、この適用除外はNFR4自体の要求範囲から導かれる帰結であり新たな緩和判断ではない。
