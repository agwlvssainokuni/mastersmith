# Functional Specification: dynamic-data-access

## ワークフロー

### 1. 一覧画面

1. 利用者が `GET /api/data/{tableId}` を、X-Active-Roleヘッダー・検索条件・page/size/sortとともに呼び出す。
2. 契約#3でテーブル単位のlist権限を確認する(BR4.1)。
3. 契約#2でtableIdの有効なTableConfigを取得する(キャッシュ経由)。
4. 契約#3で呼び出しロールのカラム単位accessLevelを取得し、非表示カラムを確定する(BR4.2)。
5. 検索条件をisSearchable=trueかつaccessLevel≠非表示のカラムのみ受け付け、動的にWHERE句を組み立てて検索する(BR1.1。非表示カラムの検索条件は送信されても無視する、iteration 2レビューR-01フォロー)。
6. listOrderに基づく表示列・ページネーションを適用する。sortパラメータで指定されたカラム名が、TableConfig.columns[]に実在する既知の識別子でない場合、またはaccessLevel=非表示と判定される場合は、そのsort指定を無視しlistOrder順にフォールバックする(BR1.2、iteration 1レビューR-10フォロー)。検証を通過したカラム名のみを動的ORDER BY句の識別子として使用する。
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

**Verdict:** NOT-READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T03:30:48Z
**Iteration:** 2

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-10 | Critical | rules.md > BR1.2 | 一覧画面sortパラメータの対象カラム名がTableConfig由来の既知の識別子集合に限定されておらず、かつaccessLevel=非表示のカラムをソート対象から除外する規定もなかった(動的SQL識別子インジェクション、行順序を介した非表示値の推測) | BR1.1の検索条件と同様に、識別子検証とaccessLevel=非表示除外の両方をsortにも課し、違反時はlistOrder順にフォールバックする規定を追加する | Resolved |
| R-11 | Critical | rules.md > BR5.1「絞り込み条件付きで検索を行い」/ logic欄、functional-spec.md > ワークフロー5 手順4〜5 | R-10で修正されたBR1.1/BR1.2は「動的SQL識別子として使用するカラム名はTableConfig.columns[]に実在する既知の識別子に限定する」ことを明文で要求しているが、BR5.1(FKポップアップ検索の絞り込み条件)には同等の識別子検証規定がない。BR5.1のstatement/logicは「参照先テーブルについてもBR1.1と同様」と述べる範囲を明示的にaccessLevel=非表示の除外のみに限定しており(「accessLevel=非表示のカラムは絞り込み条件として受け付けない」)、絞り込み条件のカラム名がTableConfig.columns[]に実在する既知の識別子かどうかの検証には触れていない。契約側(contract-summary.md #12 fk-searchエンドポイント定義)もクエリパラメータのスキーマを定義しておらず(「カラムごとの絞り込み条件を付与可能」という自由記述のみ)、この検証を上流契約が肩代わりしてもいない。functional-spec.mdワークフロー5手順4「参照先テーブルの列単位accessLevelを確認し…非表示カラムの絞り込み条件は無視する」、手順5「絞り込み条件(非表示カラムを除く)で参照先テーブルを検索する」も、accessLevelのみを検証対象とし識別子の実在検証には触れていない。この結果、任意のcolumnNameキー(参照先テーブルに実在しないカラム名を含む)が絞り込み条件として渡された場合の扱いが未規定であり、実装者がBR1.1/BR1.2と同水準の防御(未知の識別子を動的WHERE句に使う前に除外する)を入れずに実装してしまう余地が残る。これはR-10が塞いだのと同一クラスのリスク(動的SQL識別子インジェクション)であり、レビュー観点2(BR1.1・BR5.1の検証水準の一貫性)が指摘する不整合そのものである | BR5.1のstatement/logicに、絞り込み条件のカラム名が参照先テーブルのTableConfig.columns[]に実在する既知の識別子であることの検証を明記し、違反する場合(未知の識別子)はBR1.1と同様にその絞り込み条件を無視する規定を追加する。functional-spec.mdワークフロー5手順4〜5にも同じ検証順序(識別子検証→accessLevel検証→絞り込み実行)を反映する | New |

### Validation Tool Results

このstageに定義済みの自動検証ツールは実行対象として指定されていない(スキーマ検証はrules.mdのYAMLブロックを目視で確認し、フィールド構造の破損は検出されなかった)。

### Summary

R-10自体(BR1.2のsort識別子検証・非表示カラム除外の欠落)は修正により解消され、BR1.1と同水準の検証ロジックになっている。functional-spec.mdワークフロー1手順6もBR1.2のロジックと一致している。entities.md・traceability.jsonにも矛盾は見当たらない。しかし、レビュー観点2で要求された「BR1.1・BR5.1と同水準か」という一貫性チェックの結果、BR5.1(FKポップアップ検索の絞り込み条件)にR-10と同一クラスの識別子検証の欠落(R-11)が新たに見つかったため、iteration 2はNOT-READYとする。
