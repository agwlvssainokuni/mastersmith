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

# Scalability Requirements — config-engine (U1)

`inception/requirements-analysis/requirements.md`のNFR3、および`nfr-requirements-questions.md`の確定回答（Q5）に基づく、config-engineユニットのスケーラビリティ要件。

## 想定データ量・成長見込み

| Dimension | Current | 想定上限 | Scaling Mechanism |
|---|---|---|---|
| TableConfig件数（1業務プロファイルあたり） | — | 数十〜百程度 | 起動時メモリキャッシュ（NFR1.2）で対応可能な規模 |
| ColumnConfig件数（テーブルあたり） | — | 数個〜数十個 | 同上 |
| TranslationEntry件数 | — | (TableConfig数+ColumnConfig数) × 2言語（日英）程度 | 同上 |

- **根拠**: Q5確定回答（A）。単一業務プロファイル内のテーブル・カラム数は小規模であり、起動時メモリキャッシュ（NFR1.2）を前提としても十分小さい規模である。

## スケーリングアプローチ

## NFR3.1: config-engineのスケーラビリティ

```
NFR3.1: config-engineのスケーラビリティ
Current baseline: 未計測（新規開発）
Target capacity: NFR3のアプリ全体の想定利用規模（数十名程度、前身ツールMasterMeisterの約10名規模からの拡大）に対応
Growth model: 線形（利用者数・業務プロファイル数の緩やかな増加）
Scaling approach: 本ワークフローのスコープでは実デプロイ（Operationフェーズ）が対象外であり、想定利用規模（数十名程度）は単一インスタンスで十分対応可能と判断する。現行設計（NFR1.2の起動時全読み込みメモリキャッシュ、書込時にそのインスタンスのキャッシュのみ更新）は、この単一インスタンス構成を前提とする
Cost constraint: 本ワークフローのスコープでは具体的なインフラコスト制約は対象外（Operationフェーズ未対象）
Degradation policy: 該当なし（起動時全読み込みキャッシュのため、想定データ量の範囲内では性能劣化は生じない設計）
```

### 既知の制約: 複数インスタンス構成への非対応（レビュー指摘R-01対応）

現行のキャッシュ更新方式（`nfr-requirements-questions.md` Q2確定回答A: 起動時全読み込み、書込時にそのインスタンスのキャッシュのみ更新、TTLベース再読込やインスタンス間のキャッシュ無効化通知は持たない）は、**単一インスタンス構成を前提とした設計である**。複数インスタンスを水平配置した場合、あるインスタンスで管理者が設定を変更しても、他インスタンスは古い設定を無期限にキャッシュし続け、反映されない。

これは「単一のアプリ本体を設定の入れ替えだけで複数業務に転用できる」という成功定義そのものを損なうものではない（設定の切り替えは業務プロファイルの再デプロイ・再起動を伴う運用を想定しており、稼働中の複数インスタンスへのリアルタイム反映を要件としていない）が、稼働中の管理画面からの設定変更を複数インスタンスへ反映する運用には対応しない。

将来、複数インスタンスでの水平スケールおよび稼働中の管理画面からの設定変更の即時反映が必要になった場合は、キャッシュ無効化イベントのブロードキャスト（AuditLoggingへの変更イベント発行を活用した通知等）またはTTLベースの定期再読込を追加検討する必要がある。本ワークフローのスコープ（Operationフェーズ未対象）では、この制約を既知の制約として記録するにとどめる。

## スケーリング判断マトリクス

| シグナル | 対応方針 |
|---|---|
| 設定データ量（TableConfig/ColumnConfig/TranslationEntry）の増加 | 想定上限（数十〜百テーブル）を超える見込みが生じた場合、起動時全読み込みキャッシュ方式の見直し（部分キャッシュ・遅延読み込み等）を検討する。現時点では想定内であり対応不要 |
| 同時アクセス数の増加 | 現行スコープでは単一インスタンス構成を前提とするため対応不要（想定規模・数十名は単一インスタンスで十分）。複数インスタンスでの水平拡張が必要になった場合は、上記「既知の制約」節のキャッシュ無効化の仕組みを先に解決する必要がある |

## Scalability Anti-Requirements（除外事項）

- 「無制限のユーザーに対応する」のような境界のない目標は設定しない。NFR3のアプリ全体の想定規模（数十名程度）に基づく。
- 将来的な業務プロファイル追加（設定の入れ替え運用）によるテーブル数百〜千規模への拡張は、Q5確定回答（A、Bは不採用）により本ユニットのスケーラビリティ要件としては対象外とする。想定を超える規模が必要になった場合は、その時点で別途NFR要件を見直す。
