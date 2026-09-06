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
5. 絞り込み条件(非表示カラムを除く)で参照先テーブルを検索する。
6. 検索結果の各行について、応答を生成する前に、再度BR4.2のaccessLevelを適用する(BR5.1、R-06フォロー、iteration 2レビューより): 代表表示列(foreignKeyRepresentativeColumns)が非表示の場合はその値を返さず、可視な主キー値(またはBR2.1と同じ可視カラム基準集合)を代わりに返す。recordId生成用の主キー(または全カラム)自体も、可視カラムのみに絞って返す。
7. 代表表示ラベルと(絞り込み後の)recordId生成用カラム値の組を返す。

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

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-06T14:00:50Z
**Iteration:** 2
**Request Challenge:** review:463c46665a7440a86c45c96128c3fcbd

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-06 | Critical | rules.md > BR5.1 statement/logic, entities.md > note (FKポップアップ検索の応答), functional-spec.md > ワークフロー5 steps 6-7 | Previously: the FK popup-search endpoint applied accessLevel=非表示 filtering only to search input, never to the output. The fix adds a genuine output-side re-check: functional-spec.md workflow 5 step 6 explicitly re-applies BR4.2 "応答を生成する前に" (before the response is built) — this is not a documentation-only afterthought, it is sequenced ahead of step 7's return. rules.md BR5.1's `logic` field states the same re-check "検索結果の返却時(BR4.1のview権限確認後、応答生成前)" with the full fallback chain (hidden representative column → visible PK/basis-set value → first visible column → empty string), and explicitly restricts the recordId-generation basis set (primary key or all columns) to visible-only columns, consistent with BR2.1's own visible-columns-only recordId generation. entities.md's note (lines 57-61) carries the same description. All three artifacts agree with each other and with the fix description. Verified: the fallback does not introduce a new per-row side channel, because accessLevel is resolved once per (table, column, role) and is uniform across every row returned by one call — it is not row-dependent, so no row-to-row response-shape difference can leak which specific columns are hidden beyond what BR2.1's existing list/detail design already exposes for the same role. | None — fix verified complete and consistent across rules.md, entities.md, and functional-spec.md. | Resolved |
| R-07 | Minor | rules.md > BR5.1 logic (「可視な主キー値(またはBR2.1と同じ可視カラム基準集合)を代表表示ラベルとして代用する」) | When the representative column is hidden and the referenced table has a composite primary key, the fallback label is described as substituting "a visible PK value (or the same visible basis set as BR2.1)" without saying which single value is used as the display label when the basis set has more than one visible column (concatenated? first column only?). A developer implementing the composite-PK fallback branch has no unambiguous rule to follow. | Add one sentence specifying the exact label construction when the substituted basis set has more than one visible column (e.g. "join with a fixed separator" or "use only the first visible basis-set column, same as the all-hidden fallback"). | New |
| R-08 | Minor | functional-spec.md > ワークフロー5 step 7 | Step 7 says "(絞り込み後の)recordId生成用カラム値の組を返す" — "絞り込み後" (post-filter) is imprecise here; the actual restriction being described is the output-side accessLevel exclusion applied in step 6, not the input-side search filter conditions from step 4/5. The overloaded term could cause a developer to conflate the two distinct accessLevel applications. | Reword step 7 to reference the step-6 output-side exclusion explicitly (e.g. "(ステップ6でaccessLevelを再適用した後の)recordId生成用カラム値の組"). | New |
| R-09 | Minor | inception/contract-design/contract-summary.md > `/api/data/{tableId}/fk-search/{columnName}` GET (upstream, read-only for this Unit) | Every other dynamic-data-access endpoint in this same OpenAPI block documents its 403/404 responses, but the fk-search endpoint's contract lists only `"200"` — it documents no 404 despite BR5.1 (rules.md) and functional-spec.md workflow 5 step 2 both defining a 404 case (unresolved FK reference) for this exact endpoint. This is a pre-existing upstream contract gap, not something introduced by the R-06 fix, and is out of this Unit's read/write scope to correct. | Flag for the contract-design stage / contract owner to add the 404 (and, for consistency with sibling endpoints, 403) response to the fk-search operation in contract-summary.md. Not blocking for this Unit's functional-design. | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| required-sections | passed | functional-spec.md carries all required template sections. |
| upstream-coverage | failed (findings_count: 3) | `unreferenced: ["unit-of-work", "unit-of-work-story-map", "requirements"]` — matches the documented known false-positive (stories.md-skip fallback for out-of-scope FRs); no new items. |
| traceability | failed (findings_count: 39) | `orphans: ["BR2.5"]` (known false-positive: this Unit's own cross-unit citation of config-management's BR2.5) plus a large `missing_from_upstream_ids` list of FR3.4/FR3.6/FR4/FR5.x/FR6.x/FR7.x (known false-positive, out-of-scope FRs for this Unit per the dispatch brief). `gaps: []`, `missing_from_table: []`, `invalid_entries: []`, `invalid_targets: []` — no new structural defects. |

### Summary

The R-06 fix is genuinely and completely applied: the FK popup-search endpoint now re-checks BR4.2 accessLevel on the output side, before the response is built, with an unambiguous (mostly — see R-07) fallback chain, and the recordId-generation columns are correctly restricted to visible columns consistent with BR2.1. R-01 through R-05 remain intact and unchanged in rules.md/entities.md/functional-spec.md. Three Minor findings remain (a composite-PK fallback-label ambiguity, an imprecise cross-reference in workflow step 7, and a pre-existing upstream contract documentation gap) — none block implementation. Verdict: READY.
