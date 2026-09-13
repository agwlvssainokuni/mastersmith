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

# Reliability Requirements — config-engine (U1)

`inception/requirements-analysis/requirements.md`のNFR4、および`nfr-requirements-questions.md`の確定回答（Q3）に基づく、config-engineユニットの信頼性要件。

## 可用性

config-engineは独立してデプロイされるサービスではなく、単一の実行可能WAR内に統合されたモジュールである（`unit-of-work.md`のデプロイモデル: embedded）。そのため、config-engine単体のSLA/SLOは定めず、アプリケーション全体の可用性に従う。本ワークフローではOperationフェーズが現状スコープ外のため、具体的な可用性目標（%）の設定は行わない。

## fail-fastによるデータ整合性

## NFR4.1: 設定定義の整合性担保（fail-fast）

```
NFR4.1: 設定定義の整合性担保（fail-fast）
要件: 設定定義自体に誤り（必須プロパティ欠落等）がある場合、システムは起動時・設定読込時・設定インポート時にこれを検知し、ConfigValidationExceptionをfail fastで送出しなければならない（`rules.md` BR1.1）
効果: 不整合な設定を内部設定DBへ反映したまま起動・稼働することを防ぎ、後続の実行時エラー（list-engine/record-edit-engine等での予期しない障害）を未然に防止する
```

## 内部設定DBの永続化

## NFR4.2: 内部設定DBの永続化モード

```
NFR4.2: 内部設定DBの永続化モード
要件: 内部設定DB（H2）はファイルモード（永続化ファイル）で運用し、アプリケーション再起動後もTableConfig/ColumnConfig/TranslationEntryを含む設定データが失われないようにする
バックアップ: 定期的なファイルバックアップ（例: 日次）を運用手順として想定する。自動バックアップの仕組み自体は本ワークフローのスコープ外（Operationフェーズ未対象）とし、方針の記録のみとする
根拠: Q3確定回答（A）
```

## データ整合性・同時更新

- 管理画面からの設定変更（TableConfig/ColumnConfig/TranslationEntryの更新）における同時編集の競合検出（楽観ロック等）は、単一の管理者による低頻度操作を想定し、本ユニットのNFR要件としては必須としない。将来的に複数管理者による同時編集が想定される場合は、Code Generation以降で改めて検討する。
- schema-introspectorからのドラフト取り込み（`writeTableConfigDraft`）は、既存設定を上書きしない設計（`rules.md` BR1.8）により、データの意図しない喪失を防止する。

## Reliability Anti-Requirements（除外事項）

- 「高可用性を実現する」のような測定不能な目標は設定しない。
- Multi-AZ配置・自動フェイルオーバー等のインフラレベルの信頼性対策は、本ワークフローのスコープ（Operationフェーズ未対象）外であるため、config-engineのNFR要件としては扱わない。
