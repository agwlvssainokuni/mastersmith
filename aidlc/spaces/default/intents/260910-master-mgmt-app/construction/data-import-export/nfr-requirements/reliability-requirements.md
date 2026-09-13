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

# Reliability Requirements — data-import-export (U8)

`inception/requirements-analysis/requirements.md`のNFR4、`construction/data-import-export/functional-design/rules.md`(BR8.7)、および`nfr-requirements-questions.md`の確定回答に基づく、data-import-exportユニットの信頼性要件。

## NFR4.1: インポートのデータ整合性(全件検証後の一括コミット)

- **要件**: CSVインポートは、BR8.7(全件検証後の一括コミット、1件でもバリデーションエラーがあれば全体をロールバック)により、部分的に不整合な状態(一部の行のみ反映された状態)がDBへ残らないことを保証する
- **障害時の扱い**: バリデーション中またはコミット中にアプリケーション障害(再起動等)が発生した場合、データベーストランザクションの原子性により、コミットが完了していない変更はロールバックされる。副作用は残らない
- **根拠**: `rules.md` BR8.7・NFR4(全体、同時更新の競合は楽観ロックで検出する等のデータ整合性要件)

## NFR4.2: 障害時の復旧手順(再開機能なし、意図的なスコープ判断)

- **要件**: 大規模インポート(10万行規模)の処理中にシステム障害で中断した場合、部分再開機能は提供しない。利用者は同じCSVファイルを使ってインポートを最初からやり直す
- **RTO(Recovery Time Objective)**: 利用者操作によるリトライのみ(自動復旧・自動リトライは行わない)。NFR4.1により未コミットの変更は残らないため、再実行は安全に行える
- **RPO(Recovery Point Objective)**: 0(コミットされていないデータは失われない。コミット済みのデータはトランザクション確定時点で永続化される)
- **根拠**: `nfr-requirements-questions.md` Q9確定(A)。部分再開機能はMVPスコープでは複雑さに見合わないと判断し、対象外とした

## NFR4.3: 楽観ロックの非適用と後勝ちの整合性への影響(既知の限界)

- **要件**: `rules.md` BR8.4により、CSVインポートによる既存行の更新は楽観ロック競合検出を行わず常に後勝ちで上書きする
- **既知の限界**: record-edit-engineの詳細・編集画面で同時に同一行を編集しているユーザーがいる場合、CSVインポートの実行によって当該ユーザーの変更が意図せず上書きされる可能性がある。この限界はFunctional Designで明示的に受容済みであり(Q5確定)、本ユニットのNFR要件としても踏襲する
- **根拠**: `rules.md` BR8.4・`nfr-requirements-questions.md`のFunctional Design時点のQ5確定(BR8.4のsource参照)

## Reliability Anti-Requirements(除外事項)

- 「インポートは絶対に失敗してはならない」のような測定不能な目標は設定しない。BR8.7による安全なロールバックと、NFR4.2の利用者主導リトライで十分とする。
- CSVインポート・エクスポート専用のバックアップ・災害復旧計画は設けない(業務データ用RDBMS全体のバックアップ・復旧計画に含まれるため、本ユニット固有の要件としては追加しない)。
