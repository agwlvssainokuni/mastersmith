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

# Scalability Design — config-engine (U1)

`construction/config-engine/nfr-requirements/scalability-requirements.md`（NFR3.1、GAPとして記録済み）を踏まえた具体設計。

## スケーリングアーキテクチャ

- **前提**: 単一インスタンス構成（`nfr-requirements-questions.md` Q2 Follow-upで人間が明示的に受入れ済み）。`ConfigCache`はインスタンスローカルのメモリキャッシュであり、インスタンス間の共有・同期機構を持たない。
- 垂直方向（インスタンスのCPU/メモリ増強）でのスケールアップは可能（想定データ量・数十〜百テーブルのキャッシュはメモリ消費が小さいため通常不要）。

## データパーティショニング

- 該当なし。TableConfig/ColumnConfig/TranslationEntryは想定データ量が小規模（`nfr-requirements/scalability-requirements.md`参照）であり、シャーディング・パーティショニングは設計しない。

## 将来の水平スケール対応（参考、本Boltのスコープ外）

複数インスタンス化が必要になった場合の設計オプションを記録として残す（実装しない）。

1. **キャッシュ無効化ブロードキャスト**: AuditLoggingが発行する`ConfigChanged`イベントを、インスタンス間メッセージング（未導入）経由で購読し、各インスタンスの`ConfigCache`を再構築する。
2. **TTLベース再読込**: `ConfigCache`が一定間隔（例: 30秒〜数分）で内部設定DBから再読込する。実装は単純だが反映までの遅延が生じる。

いずれも本ワークフローのスコープ（Operationフェーズ未対象）では設計・実装しない。

## キャパシティしきい値

| 指標 | しきい値 | 対応 |
|---|---|---|
| TableConfig件数 | 数百件を超えた場合 | `ConfigCache`の全件読み込み方式を見直す（部分キャッシュ化を検討） |
| 同時アクセス数 | NFR3のアプリ全体想定（数十名）を超えた場合 | インスタンス垂直スケールで対応（水平スケールは上記「将来の水平スケール対応」が前提条件） |
