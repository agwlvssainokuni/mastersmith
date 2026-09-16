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

# Reliability Requirements — menu-navigation (U6)

`inception/requirements-analysis/requirements.md`のNFR4、および`functional-design/rules.md` BR6.7に基づく、menu-navigationユニットの信頼性要件。

## NFR4.1: 参照整合性欠損時の挙動(fail fastにしない)

- `GET /api/menu`で、MenuItem.targetTableConfigIdが指すTableConfig(ConfigEngine管理)が存在しない場合、当該MenuItemを実行時に結果から除外する(BR6.7)。アプリ全体のfail fast(起動時検証)は行わない。設定投入時の整合性検証はconfig-import-export側のインポート時検証に委ねる。
- 可用性: 本ユニットはembedded(他serviceユニットと同一プロセス内)で動作し、独立したSLA/SLOは設定しない。アプリ全体の可用性に従属する。
- グレースフルデグラデーション: 該当なし。参照欠損は例外的なエラーではなく、想定内の状態として実行時に除外する設計とする(BR6.7)。

## NFR4.2: データ整合性・バックアップ

- NFR4が定める監査ログの改ざん・削除不可(append-only)、および楽観ロックによる同時更新競合検出は、本ユニット自身のデータ(`MenuItem`)には適用されない。楽観ロック要件はFR6.3(業務データの詳細・編集画面、record-edit-engineの責務)に限定されており、設定情報である`MenuItem`は元々その適用範囲に含まれない(scalability-requirements.md参照)。`MenuItem`は業務担当者による低頻度な設定操作の対象であり、監査ログの記録対象イベントとしては扱わない方針とした(Q4確定=A、security-requirements.md参照)。
- バックアップ・リカバリ: `MenuItem`は内部設定DB(組込みDB)に永続化される。バックアップ・リカバリは内部設定DB全体の運用方針に従い、本ユニット固有の追加対応は不要。

## NFR4.3: `/api/menu-items`の失敗時挙動

- リクエストボディ検証エラー(400)・認可拒否(403)・対象不存在(404)は、いずれもfail fastで即座にエラーを返す(BR6.8)。自動リトライは行わない(低頻度の管理操作であり、失敗時は管理者による再実行に委ねる)。

## NFR4.4: DELETE時の子孫MenuItem整合性(既知のリスク、code-generationで最終確定)

`tech-stack-decisions.md`のとおり、MenuItemの木構造は自己参照外部キー(`parentMenuItemId`)で表現する。`DELETE /api/menu-items/{menuItemId}`が子孫を持つMenuItemに対して呼び出された場合の挙動は`functional-spec.md`のOpen Questionsで未確定のまま残されており、データ整合性上のリスクである(親を削除すると子が孤立する、または外部キー制約違反でエラーになる)。信頼性の観点から、以下を既定方針としてcode-generationステージへ引き継ぐ。

- **既定方針**: 子孫MenuItemを持つ項目の削除は409 Conflict(RFC 9457)で拒否し、カスケード削除は行わない。管理者は先に子を削除・付け替えてから親を削除する運用とする(データの意図しない一括消失を防ぐ、fail-safeな既定挙動)。
- この既定方針をcode-generationステージで採用しない場合(例: 明示的なカスケード削除オプションを追加する場合)は、その判断根拠をcode-generationの`code-summary.md`に記録すること。
