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

# Reliability Requirements — schema-introspector (U2)

`inception/requirements-analysis/requirements.md`のNFR4、および`functional-design/rules.md` BR2.9に基づく、schema-introspectorユニットの信頼性要件。

## NFR4.1: fail-fast障害時挙動

- 対象RDBMS(業務データ用)への接続失敗・メタデータ読み取り失敗が発生した場合、fail fastとし部分書込みを行わずに処理全体を中断する(BR2.9)。422のエラーレスポンスを呼び出し元へ返す。
- 自動リトライは行わない。単純さを優先し、失敗時は管理者による再実行に委ねる(低頻度の管理操作であるため、自動リトライの複雑さに見合うメリットが乏しいと判断)。
- 可用性: 本ユニットはembedded(他serviceユニットと同一プロセス内)で動作し、独立したSLA/SLOは設定しない。アプリ全体の可用性に従属する。
- グレースフルデグラデーション: 該当なし。オプション機能ではなく、失敗時は明確なエラー(403/422)を返す設計とする。

## NFR4.2: データ整合性・バックアップ(本ユニット非該当)

- NFR4が定める監査ログの改ざん・削除不可(append-only)、および楽観ロックによる同時更新競合検出は、本ユニット自身のデータには適用されない(本ユニットは永続データを持たず、楽観ロック対象にもならない)。
- schema-introspectorの書き込み(`writeTableConfigDraft`)によって生成・変更されるconfig-engineの`TableConfig`/`ColumnConfig`の変更監査は、config-engine側の`ConfigChangedEvent`発行(`construction/config-engine/functional-design/rules.md` BR1.13)に委ねる。
- バックアップ・リカバリ: 本ユニットは永続データを持たないため、本ユニット自体のバックアップ・リカバリ対象は存在しない。読み取り元の業務データ用RDBMSのバックアップ・リカバリは各RDBMSの運用方針に従い(本ユニットのスコープ外)、書き込み先であるconfig-engineの内部設定DBのバックアップ・リカバリはconfig-engineユニットの責務である。
