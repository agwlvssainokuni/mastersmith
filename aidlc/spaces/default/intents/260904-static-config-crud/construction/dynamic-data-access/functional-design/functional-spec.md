# Functional Specification: dynamic-data-access

## ワークフロー

### 1. 一覧画面

1. 利用者が `GET /api/data/{tableId}` を、X-Active-Roleヘッダー・検索条件・page/size/sortとともに呼び出す。
2. 契約#3でテーブル単位のlist権限を確認する(BR4.1)。
3. 契約#2でtableIdの有効なTableConfigを取得する(キャッシュ経由)。
4. 契約#3で呼び出しロールのカラム単位accessLevelを取得し、非表示カラムを確定する(BR4.2)。
5. 検索条件をisSearchable=trueかつaccessLevel≠非表示のカラムのみ受け付け、動的にWHERE句を組み立てて検索する(BR1.1。非表示カラムの検索条件は送信されても無視する、iteration 2レビューR-01フォロー)。
6. listOrderに基づく表示列・ページネーション・ソートを適用する(BR1.2)。
7. カラム単位権限(BR4.2)で非表示カラムをレスポンスから除外する。
8. 各行のrecordIdを、BR4.2で非表示と判定されたカラムを除いた基準集合(主キーまたは全カラム)から生成する(BR2.1)。
9. FK列の表示名を解決する(BR1.3)。
10. 一覧結果を返す。

### 2. 詳細画面

1. 利用者が `GET /api/data/{tableId}/{recordId}` をX-Active-Roleヘッダーとともに呼び出す。
2. 契約#3でテーブル単位のview権限を確認する(BR4.1)。
3. 契約#2でtableIdの有効なTableConfigを取得する(キャッシュ経由)。
4. recordIdをデコードする。デコード失敗時は404。
5. デコードしたカラム名→値のマップの各キーを検証する: (a)対象テーブルの正しい基準集合(TableConfig由来の既知の識別子)に含まれるか、(b)契約#3で取得した呼び出しロールのカラム単位accessLevel(BR4.2)がいずれも非表示でないか。いずれかに違反するキーが1件でもあれば、SQLを実行せず404を返す(recordIdはクライアントが直接送信する値であり改変され得るため、検索実行より前に検証する。BR2.1、R-05フォロー)。
6. 検証を通過したカラム名→値のマップをそのままWHERE句の一致条件として該当行を検索する(BR2.1)。該当なしの場合は404。
7. FK列の表示名を解決する(BR1.3)。
8. 全カラム(表示可能なもの)を返す。

### 3. 新規作成

1. 利用者が `POST /api/data/{tableId}` をX-Active-Roleヘッダー・入力値とともに呼び出す。
2. 対象テーブルが主キーを持つことを確認する(BR3.1)。主キーなしテーブル・ビューは403。
3. 契約#3でテーブル単位のcreate権限を確認する(BR4.1)。
4. カラム単位権限(BR4.2)により、accessLevel=更新可のカラムのみ入力を受け付ける。
5. TableConfigのバリデーション設定に基づき入力値を検証する(BR3.2)。違反時は400。
6. INSERT文を実行する。外部キー制約違反時は400(BR3.4)。
7. 監査ログイベント(actionType=DATA_RECORD_CREATED、BR6.1)を発行する。
8. 作成されたレコードのrecordIdとともに201を返す。

### 4. 更新

1. 利用者が `PUT /api/data/{tableId}/{recordId}` をX-Active-Roleヘッダー・入力値とともに呼び出す。
2. 対象テーブルが主キーを持つことを確認する(BR3.1)。
3. 契約#3でテーブル単位のedit権限を確認する(BR4.1)。
4. カラム単位権限(BR4.2)により、accessLevel=更新可のカラムのみ更新を受け付ける(表示のみのカラムは送信されても無視する)。
5. TableConfigのバリデーション設定に基づき入力値を検証する(BR3.2)。違反時は400。
6. 主キー一致のみをWHERE句としてUPDATE文を実行する(BR3.3、後勝ち)。該当行が0件の場合は404。外部キー制約違反時は400(BR3.4)。
7. 監査ログイベント(actionType=DATA_RECORD_UPDATED、BR6.1)を発行する。
8. 更新後の内容を返す。

### 5. FKポップアップ検索

1. 利用者が `GET /api/data/{tableId}/fk-search/{columnName}` をX-Active-Roleヘッダー・絞り込み条件とともに呼び出す。
2. columnNameが指すFK参照先テーブルを、TableConfig.foreignKeysから解決する。未解決(referencedTableId=null)の場合は404(BR5.1)。
3. 参照先テーブルに対するview権限を確認する(BR4.1、BR5.1)。
4. 参照先テーブルの列単位accessLevelを確認し(BR4.2)、非表示カラムの絞り込み条件は無視する(BR5.1、iteration 2レビューR-01フォロー)。
5. 絞り込み条件(非表示カラムを除く)で参照先テーブルを検索し、代表表示列(foreignKeyRepresentativeColumns)と主キー(recordId生成用)の組を返す。

## 状態遷移

dynamic-data-accessは永続エンティティを持たず、業務データの行自体は対象RDBMSの任意のスキーマに属するため、本Unit自身が管理する状態遷移は存在しない。

## エンティティ関連図

dynamic-data-accessは永続エンティティを持たないため、ER図は存在しない(entities.md参照)。

## ルールサマリー(rules.mdから導出)

| ワークフロー | 適用ルール |
|---|---|
| 1. 一覧画面 | BR1.1, BR1.2, BR1.3, BR2.1, BR4.1, BR4.2 |
| 2. 詳細画面 | BR2.1, BR1.3, BR4.1, BR4.2 |
| 3. 新規作成 | BR3.1, BR3.2, BR3.4, BR4.1, BR4.2, BR6.1 |
| 4. 更新 | BR3.1, BR3.2, BR3.3, BR3.4, BR4.1, BR4.2, BR6.1 |
| 5. FKポップアップ検索 | BR5.1, BR4.1, BR4.2 |

## Review

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-06T13:04:01Z
**Iteration:** 2
**Request Challenge:** review:7b5e3dedf745c86025c56a7f8e873c29

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Critical | rules.md BR1.1 / functional-spec.md workflow 1 step 5 | Search-condition side-channel for `accessLevel=非表示` columns (silently ignore hidden-column conditions). | None. | Resolved (re-verified iteration 2) |
| R-02 | Minor | rules.md BR6.1 | `DATA_RECORD_DELETED` explicitly documented as unused vocabulary since this Unit has no delete endpoint. | None. | Resolved (re-verified iteration 2) |
| R-03 | Minor | (carried; scope unchanged since iteration 1) | — | None. | Resolved (re-verified iteration 2) |
| R-04 | Minor | entities.md RecordId shape / rules.md BR2.1 | RecordId is a JSON object (not array) keyed by column name, avoiding ordering-dependence; all-hidden-basis case returns 404 rather than an unconditioned WHERE clause. | None. | Resolved (re-verified iteration 2) |
| R-05 | Critical | rules.md BR2.1, entities.md RecordId note, functional-spec.md workflow 2 | Detail-screen recordId decode path executed the client-supplied, unsigned Base64-decoded column→value map directly as a WHERE clause with no validation of key membership or accessLevel, and unvalidated strings could reach a SQL-identifier position. **Verified fixed**: BR2.1 now specifies, in order, (1) decoded-key membership against the table's correct known-identifier basis set (primaryKeyColumns, or all columns for PK-less tables/views) — reject with 404 if any key is not a known identifier, so only TableConfig-sourced identifiers ever reach the WHERE clause's identifier position; then (2) accessLevel re-check on the keys that passed (1) — reject with 404 if any is currently `非表示` for the calling role. functional-spec.md workflow 2 is correctly reordered (step 3: TableConfig fetch → step 4: decode → step 5: validate (a)+(b) → step 6: WHERE-clause search), and both entities.md's RecordId note and rules.md's `violation_behaviour` state the 404 is returned uniformly across decode failure, key-validation failure, accessLevel failure, and zero-rows-found, so the failure reason cannot be distinguished from the response — no new side-channel is introduced by the fix itself. | None. | Resolved |
| R-06 | Critical | rules.md BR5.1 / BR4.2 / functional-spec.md workflow 5 (FKポップアップ検索, `GET /api/data/{tableId}/fk-search/{columnName}`) | BR4.2's own statement text scopes column-level output suppression (`accessLevel=非表示`のカラムはレスポンスに含めない) to "一覧・詳細・新規作成・更新" only — the FK popup-search screen is not in that list. BR5.1 and functional-spec.md workflow 5 step 4 apply `accessLevel`/BR4.2 filtering **only to the search/filter input conditions** ("非表示カラムの絞り込み条件は無視する"), never to the **output**: workflow step 5 returns "代表表示列と主キー(または全カラム、recordId生成に使う)" for every matched row with no accessLevel check on those returned columns. `foreignKeyRepresentativeColumns` and `primaryKeyColumns` are admin-configured per TableConfig (contract-summary.md #2) independently of any accessLevel guarantee, and FR5.2 (requirements.md) states the column-level access model (更新可/表示のみ/非表示) applies to "業務データ" generally, not to a subset of screens. Concretely: if an admin sets a referenced table's representative column (or a PK column) to `accessLevel=非表示` for a role, a caller in that role who has only `view` permission on the referenced table (checked in workflow step 3) can still invoke fk-search and receive that hidden column's literal value in the 200 response — the exact class of disclosure R-01/R-05 close for the list/detail screens, reopened here on a different endpoint. This is a genuinely new defect not covered by the R-01/R-05 fixes verified above. | Add an output-filtering step to BR5.1/workflow 5 (after step 3's view-permission check, before step 5's response) that excludes from the response any representative-column or primary-key value whose `accessLevel` (BR4.2, referenced table, calling role) is `非表示`, and specify the resulting behaviour when the representative column itself is hidden (e.g., fall back to another visible column, or omit the field) so a developer does not have to guess. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| `required-sections` (functional-spec.md) | passed | All required H2 sections present. |
| `upstream-coverage` (traceability.json) | failed — `unreferenced: [unit-of-work, unit-of-work-story-map, requirements]` | Matches the dispatch's documented false-positive (stories.md-skip fallback for out-of-scope contract files); no new mismatch. |
| `traceability` (traceability.json) | failed — `orphans: [BR2.5]`, `missing_from_upstream_ids: [FR1…FR7 out-of-scope IDs]` | Both match the dispatch's documented known false-positives (BR2.5 is dynamic-data-access's own citation of config-management's BR2.5; the FR list is out-of-Unit-scope FRs under the stories.md-skip fallback). No new mismatch found. |

### Summary

R-05 is correctly and completely fixed: the identifier-membership check and the accessLevel re-check both run before the WHERE-clause search executes, and the 404 response is uniform across every failure reason (decode, key-validation, accessLevel, not-found), so the fix closes the side-channel without opening a new one. R-01 through R-04 remain intact and unchanged. However, this pass found a new Critical issue (R-06): the FK popup-search endpoint filters `accessLevel=非表示` only on the search/filter *input*, never on the representative-column/primary-key *output* it returns, reopening the same class of hidden-column disclosure that R-01 and R-05 closed elsewhere, on a different endpoint. Verdict is NOT-READY pending that fix.
