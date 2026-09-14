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

# Reliability Design — data-import-export (U8)

`construction/data-import-export/nfr-requirements/reliability-requirements.md`(NFR4.1〜NFR4.3)、および`nfr-design-questions.md`の確定回答(Q3)に基づく、data-import-exportユニットの信頼性設計。

## トランザクション設計(NFR4.1対応)

`ImportCommitter`(コミット層)は、Spring の`@Transactional`(伝播レベル`REQUIRED`、単一のデータベーストランザクション)で、`ImportRowBuffer`から取り出した全行のINSERT/UPDATEを一括実行する。トランザクション内で例外が発生した場合(DB制約違反等、`RowValidator`が事前に検出できなかったエラーを含む)は、Spring標準のロールバック機構により自動的にロールバックする。

```java
// ImportCommitterの概念設計
@Transactional
void commit(String tableConfigId, List<ValidatedRow> rows) {
    for (ValidatedRow row : rows) {
        // INSERT または UPDATE(row.operation()に基づく)
    }
}
```

## 耐障害性パターンの不採用(NFR4.2関連、Q3確定)

DataImportExportの依存先は同一プロセス内のConfigEngine(直接メソッド呼び出し)とAuditLogging(アプリケーション内イベント)のみであり、ネットワーク越しの外部呼び出しを持たない。そのため、リトライ・サーキットブレーカー・タイムアウト設定等の耐障害性パターンは設計しない(`contract-summary.md`の前提を踏襲)。

## 障害復旧(NFR4.2対応)

部分再開機能は実装しない。アプリケーション障害(再起動等)によりインポート処理が中断した場合、`@Transactional`のトランザクション境界により未コミットの変更はDBへ反映されない(NFR4.1)。利用者は同じCSVファイルで最初からインポートをやり直す(`reliability-requirements.md` NFR4.2)。

## 楽観ロック非適用の実装への反映(NFR4.3対応)

`ImportCommitter`のUPDATE処理は、record-edit-engineが用いる楽観ロック対象列(`optimisticLockVersion`相当)のチェックを行わない。CSVインポートによる更新は常に対象行を無条件に上書きする(`functional-design/rules.md` BR8.4を実装レベルで踏襲)。
