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

# Performance Design — config-engine (U1)

`construction/config-engine/nfr-requirements/performance-requirements.md`（NFR1.1: 50ms以内p95、NFR1.2: 都度DBアクセスを避ける）を実現する具体設計。

## キャッシュアーキテクチャ

- **パターン**: 起動時全読み込み（Read-Through不要、Write-Aroundでもない、単純な「Load-All-At-Startup」パターン）
- **格納**: `ConfigCache`（Q2で論理分割を確定）がアプリケーション内メモリに保持する。`Map<TableConfigKey, TableConfig>`と`Map<ColumnConfigKey, ColumnConfig>`、`Map<TranslationKey, String>`の3つの読み取り専用スナップショットとして保持する（`TableConfigKey = (schemaName, tableName)`、`ColumnConfigKey = (schemaName, tableName, columnName)`、`TranslationKey = (i18nKey, locale)`）
- **読み取り**: `ConfigModelStore`（Q2で論理分割を確定）が`ConfigCache`から参照を取得して返す。ロックフリー（不変スナップショットの参照差し替えのみ）
- **書き込み反映**: 管理画面からの変更操作（W6、Contract Design追補待ち）は、`ConfigModelStore`が内部設定DBへ書き込んだ後、`ConfigCache`のスナップショット全体を再構築して原子的に差し替える（部分更新ではなく全体差し替えにより整合性を保証する。想定データ量（数十〜百テーブル）では全体再構築のコストは無視できる）

## レイテンシ予算

| 操作 | 目標（p95） | 内訳 |
|---|---|---|
| `getTableConfig` / `getColumnConfigs` | 50ms以内（NFR1.1） | キャッシュ参照（メモリアクセスのみ）: 1ms未満。呼び出し元（list-engine/record-edit-engine）でのオブジェクト変換等は呼び出し元の予算に含める |
| `getOptimisticLockColumn` | 50ms以内（NFR1.1） | 同上 |
| `writeTableConfigDraft`（schema-introspectorからの取り込み） | 予算対象外（低頻度の管理操作） | 内部設定DBへの書き込み + キャッシュ再構築 |

## 非同期処理・CDN等

- config-engineは非同期処理・CDN・ページネーションの対象外（内部呼び出しのみ、HTTPレスポンスを直接返さない）。

## パフォーマンス予算の検証方法

- Build and Testステージ（3.6）で、`getTableConfig`/`getColumnConfigs`呼び出しに対するマイクロベンチマーク（またはユニットテストでの実行時間アサーション）を用意し、50ms以内（p95相当、テスト環境ではより厳しい閾値でも代替可）を確認する。
