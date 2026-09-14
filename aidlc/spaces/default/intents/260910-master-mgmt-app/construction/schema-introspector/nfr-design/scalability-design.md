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

# Scalability Design — schema-introspector (U2)

`nfr-requirements/scalability-requirements.md`(NFR3.1)に基づく、schema-introspectorユニットのスケーラビリティ設計。

## NFR3.1: スケーリングアーキテクチャ

- 本ユニットはconfig-engine等の他serviceユニットと同一プロセス(embedded、単一Spring Bootアプリケーション)で動作し、独自の水平/垂直スケール機構は設けない。アプリ全体のスケール方針(単一インスタンス構成)にそのまま従う。
- 対象スキーマ規模は数十テーブル程度(NFR3.1)を主な想定とし、この規模に対して特別なパーティショニング・分割実行の仕組みは導入しない(performance-design.mdの1パス走査設計で十分)。
- 同時実行の排他制御は設けない(scalability-requirements.mdの「既知の制約」を踏襲)。複数管理者が同時に同一スキーマを対象にした場合の競合は、config-engine側の`writeTableConfigDraft`(テーブル単位の既存チェック)に委ねる。

## 将来の拡張余地(採用しない設計、参考記録)

- 数百テーブル以上の大規模スキーマへの対応が将来必要になった場合、Q1で不採用としたジョブID+ポーリング方式(非同期実行)への切り替えを検討する。その場合、テーブル単位のバッチ分割・進捗永続化(例:内部設定DBへの一時ジョブステータステーブル追加)が必要になる。本MVPでは設計・実装のいずれも行わない。
