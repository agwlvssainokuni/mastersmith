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

# Security Requirements — data-import-export (U8)

`inception/requirements-analysis/requirements.md`のNFR2、`project.md`のMandated/Forbidden、`construction/data-import-export/functional-design/`の確定内容、および`nfr-requirements-questions.md`の確定回答に基づく、data-import-exportユニットのセキュリティ要件。

## データ分類

| データ | 分類 | 理由 |
|---|---|---|
| CSVファイル(インポート入力/エクスポート出力) | 業務プロファイル依存(Internal〜Confidential) | 業務データ本体(行データ)の内容は、config-engineが保持する設定に応じて業務プロファイルごとに異なる(ECショップの商品マスタ、蔵書マスタ等)。本ユニット単体では列の意味を解釈しないため、一律の機密性分類は行わず、対象テーブルの実際の業務データ分類に従う前提とする |
| CsvExportRequest/CsvImportRequest(`entities.md`) | Internal | 検索条件・ソート順・列名一覧・実行者IDのみを保持し、業務データそのものは含まない |
| ImportExecutedEvent(`entities.md`) | Internal | 実行単位のサマリ(成功/エラー件数、実行者、日時)のみを保持し、行単位の業務データ値は含まない(BR8.9) |

## STRIDE脅威分析

| 脅威 | 該当性 | 対策 |
|---|---|---|
| Spoofing(なりすまし) | list-engine/record-edit-engineからの内部呼び出しであり、外部から直接呼び出されるAPIではない。実行者(actor)の真正性はrecord-edit-engineが認証済みセッションから取得したユーザーIDに依存する | DataImportExport自身は認証を行わない(内部委譲、BR8.8)。actorの出所はrecord-edit-engineの認証境界に委ねる(`functional-spec.md` Assumptions & Open Questions参照) |
| Tampering(改ざん) | アップロードされたCSVファイルの内容が、意図しない業務データへ書き込まれるリスク | BR8.5(validationRule全適用+型変換エラー検出)、BR8.7(全件検証後の一括コミット、1件でもエラーがあれば全体ロールバック)により、不正な形式のデータが業務データへ反映されることを防ぐ |
| Repudiation(否認) | インポート実行の実施有無に関する否認 | ImportExecutedEvent(BR8.9)により実行者・実行日時・成功/失敗件数を監査ログへ記録する。行単位の変更前後の値は含まない(NFR2.1参照、意図的なスコープ判断) |
| Information Disclosure(情報漏えい) | (a) CSVエクスポート時、READ権限未満の列が意図せず出力される、(b) インポートのバリデーションエラーメッセージに内部実装詳細が露出する、(c) CSVインジェクション(数式インジェクション)によるクライアント側でのデータ露出・改ざん | (a) BR8.2(permittedColumnNamesによる列除外、NFR2.2参照)。(b) BR8.6(行番号・フィールド単位のエラーメッセージのみ、スタックトレースを含めない)。(c) 対策なし(NFR2.3参照、意図的なリスク受容) |
| Denial of Service(サービス妨害) | 大量行数のCSVインポート・エクスポートによるリソース枯渇(メモリ・DB接続) | BR8.10(ストリーミング処理、全件を一括でメモリへ読み込まない)。ファイルサイズ上限チェックは設けない(NFR2.4参照、意図的なスコープ判断) |
| Elevation of Privilege(権限昇格) | CSVインポートを通じて、実行ユーザーが本来持たないCREATE/FULL権限相当の操作(他ユーザーが読み書きできない行の作成・更新)を行うリスク | BR8.8により、DataImportExport自身は権限を再検証しないが、呼び出し元record-edit-engineが呼び出し前にCREATE/FULL権限を検証済みであることを前提とする(NFR2.2参照)。この前提が崩れる呼び出し経路(record-edit-engine以外からの直接呼び出し)は想定しない |

## NFR2.1: 監査ログの範囲と限界(意図的なスコープ判断)

```
NFR-AUTHZ-1: インポート実行の監査記録範囲
Model: 実行単位のサマリイベント(ImportExecutedEvent、BR8.9)
Audit: 実行者(actor)・対象テーブル・成功件数・エラー件数・コミット有無・実行日時を記録する
限界: project.mdのMandated「監査ログは... 変更前後の値を記録する」が要求する行単位の変更前後値は、
      CSVインポートについては意図的に記録しない(functional-design-questions.md Q8・Q8 Follow-up
      で明示的に確認済み)。個々の行の変更内容の追跡が必要な場合、本ユニットの監査ログでは
      提供できない
```

- **根拠**: Functional Design(`rules.md` BR8.9)で確定済みの意図的なスコープ判断を、セキュリティ要件としても明示的に引き継ぐ。将来この制約が問題になる場合は、Functional Designの見直し(行単位イベント発行への変更)が必要になる。

## NFR2.2: 権限検証の責務分界

```
NFR-AUTHZ-2: CSVエクスポート・インポートの権限検証
Model: RBAC(PermissionEngine、C10契約に委譲。ただしDataImportExportは直接PermissionEngineを呼び出さない)
Roles: エクスポート実行にはREAD権限、インポート実行にはCREATE権限(更新を伴う場合はFULL権限)が必要
Resource granularity: テーブル・カラム単位(list-engine/record-edit-engineが呼び出し前に検証)
Delegation: DataImportExportは権限の再検証を行わない(`rules.md` BR8.8)。エクスポート対象列の絞り込みは
            list-engineが渡す`CsvExportRequest.permittedColumnNames`(レビュー指摘R-01対応)に依存する
Audit: 権限判定そのものはPermissionEngine(U3)側で監査対象となる(本ユニットの対象外)
```

- **根拠**: `project.md`のMandated「画面表示の出し分けだけに依存せず、必ずサーバー側で実効権限を再検証する」は、list-engine/record-edit-engineがサーバー側(バックエンド)で実効権限を検証する時点で満たされる。DataImportExportは同一プロセス内の内部委譲先であり、外部から直接呼び出し可能なエンドポイントを持たないため、二重の権限検証を要求しない(`functional-design-questions.md` Q7確定)。

## NFR2.3: CSVインジェクション(数式インジェクション)への対応方針(意図的なリスク受容)

```
NFR-DATA-1: CSVインジェクション対策
方針: 対策を実装しない(nfr-requirements-questions.md Q3確定)
理由: 本アプリの利用者は社内の業務担当者に限定され、エクスポートしたCSVを外部へ配布する運用は
      想定しない。数式インジェクション(セル値が=/+/-/@で始まる場合にExcel等で数式として実行
      される既知の脆弱性クラス、OWASP)のリスクは、この利用シナリオの範囲では許容する
再評価条件: 将来、エクスポートしたCSVを外部の取引先等へ配布する運用が追加される場合、または
      利用者が信頼できない外部ソースからのCSVをインポートする運用が追加される場合は、
      本方針を再評価し、セル値エスケープ(先頭にシングルクォート付与等)の実装を検討する
```

## NFR2.4: アップロードファイルの検証範囲(意図的なスコープ判断)

```
NFR-DATA-2: CSVアップロードファイルの検証
方針: ファイルサイズ上限・Content-Type・拡張子の明示的なチェックは追加しない
      (nfr-requirements-questions.md Q4確定)
根拠: `rules.md` BR8.1(CSV形式: UTF-8 BOM付き・カンマ区切り・ヘッダー行・CRLF)で読み取れない
      ファイルは、ファイル単位のエラーとして扱われる(BR8.1 violation_behaviour)。この検証で
      不正な形式のファイルは実質的に排除されるため、追加のサイズ・MIME検証は設けない
限界: 巨大なファイル(例: 数GB)がアップロードされた場合の一時的なリソース消費リスクは、
      BR8.10のストリーミング処理(NFR1.3参照)が緩和するが、明示的な上限は設けないため
      完全には排除されない(NFR3のスケーラビリティ要件・DoS脅威の項参照)
```

## Security Anti-Requirements(除外事項)

- 「CSVエクスポート・インポートは安全でなければならない」のような測定不能な目標は設定しない。
- 依存関係の脆弱性スキャンは、`team.md`の確定事項により本プロジェクトでは意図的に対象外とする(本ユニット固有の要件としても追加しない)。
- 本ユニットが扱う業務データ本体の機密性分類・暗号化要件は、対象テーブルの実際の業務プロファイル(config-engineの設定)に依存するため、本ユニットのセキュリティ要件としては一律の暗号化要件を課さない。対象RDBMS(業務データ用)自体の保護は、当該テーブルを所有する業務システムの責務範囲とする。
