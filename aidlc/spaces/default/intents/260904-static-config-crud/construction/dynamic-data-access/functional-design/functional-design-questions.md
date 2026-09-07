# Functional Design Questions: dynamic-data-access

requirements.md・contract-summary.mdだけでは確定しきれない、dynamic-data-access固有の業務ルールを2点確定する。

## Q1. recordIdのエンコーディング(複合主キー・主キーなしテーブルの詳細画面)

契約#12は `/api/data/{tableId}/{recordId}` という単一パスセグメントでレコードを特定する。config-managementのTableConfig.primaryKeyColumnsは単一列・複合列の両方に対応するため、複合主キーの場合の`recordId`表現、および主キーを持たないテーブル(FR1.3、詳細画面はサポートするが編集は対象外)の`recordId`表現を確定する。

- A. 複合主キーの場合、`recordId`は各主キー列の値をURLセーフなBase64でJSON配列エンコードした単一文字列とする(例: `["42","2024-01"]` をBase64化)。主キーを持たないテーブルは、詳細画面(GET)は提供せず一覧画面(検索フォーム+一覧表示、FR3.1)のみを提供する(一覧の各行に「詳細」への遷移リンクを設けない)。ビュー(主キーなし)も同様に一覧のみとする(推奨: 主キーなし行を一意に再特定する安全な方法がないため、詳細画面自体を提供しない方が誤動作を避けられる)
- B. 異なるエンコーディング方式・主キーなしテーブルの扱いを採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: X(自己訂正、下記参照)。A案の複合主キーのrecordIdエンコーディング(Base64化)自体は採用するが、主キーなしテーブル・ビューについて「詳細画面を提供しない」としたA案の後半はrequirements.md FR1.3(主キーなしテーブルは「一覧・詳細」をサポート)・FR1.4(ビューは「参照(一覧・詳細)」のみ)と矛盾するため撤回する。代わりに、主キーなしテーブル・ビューのrecordIdは「主キー列の代わりに全カラムの値」を対象とし、詳細画面(GET)は全カラム一致のWHERE句で検索する(重複行があれば最初の1件を返す。読み取り専用画面(編集は対象外)であるためリスクは限定的)。これによりFR1.3/FR1.4の「一覧・詳細」の約束を維持する。なお、エンコーディングの具体的な形式(JSON配列かJSONオブジェクトか)は、後続のレビューでのカラム順序依存性の指摘(iteration 2レビューR-03/entities.md参照)を受けて、カラム名→値のJSON**オブジェクト**方式に確定した(複合主キー・全カラムいずれの場合も同様)。

## Q2. 動的SQL実行の実現方式

dynamic-data-accessは実行時に発見された任意のスキーマ(テーブル名・カラム名・型は設定driven)に対しSQLを構築・実行する。コンパイル時に固定されたエンティティクラスを前提とするJPA/Hibernateは使えないため、実現方式を確定する。

- A. Spring JDBCの`NamedParameterJdbcTemplate`を用い、テーブル名・カラム名はTableConfigから取得した既知の識別子のみを使って動的にSQL文字列を組み立てる(値は必ずプレースホルダ経由でバインドし、SQLインジェクションを防ぐ。識別子自体はconfig-management(契約#2)がキャッシュ経由で提供する既知の値のみを使うため、利用者入力が直接SQL識別子として使われることはない)(推奨)
- B. 異なる実現方式を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Q1(自己訂正込み)・Q2の回答、および機能設計中に発見したrequirements.mdの矛盾(FR3.1の文言修正)への対応を踏まえ、dynamic-data-access Unitのfunctional-design成果物を以下の内容で確定します。

**requirements.mdの修正(既に承認済み)**: FR3.1から「(主キーを持つもの)」という誤った限定を削除し、主キーなしテーブル・ビューも一覧画面の対象に含まれることをFR1.3/FR1.4と整合させた。

**エンティティ(entities.md)**: dynamic-data-accessは永続エンティティを持たない。RecordId(主キー/全カラムのうちaccessLevel≠非表示のものをカラム名→値のJSON**オブジェクト**としてBase64エンコード。レビュー iteration 2 R-01/R-04フォローで確定)、SearchCondition、RecordViewの3データ形状を文書化。主キーなしテーブル・ビューの詳細画面は全カラム一致のWHERE句で実現し(自己訂正後の方針)、FR1.3/FR1.4の「一覧・詳細」の約束を維持する。

**業務ルール(rules.md)**: BR1.1〜BR1.3(一覧: 動的検索条件・表示列・ページネーション・FK表示名解決。BR1.1はaccessLevel=非表示のカラムを検索条件として受け付けない、iteration 2レビューR-01フォロー)、BR2.1(詳細: recordIdの生成(非表示カラム除外)・デコード後の識別子/accessLevel再検証・WHERE句組み立て。可視カラムが0件の場合は404、iteration 2レビューR-04フォロー。デコードしたキー集合の識別子検証・accessLevel再検証を検索実行前に行う、iteration 1(redo jump後)レビューR-05フォロー)、BR3.1〜BR3.4(作成・更新: 主キーなし編集不可・バリデーション・後勝ち・FK制約違反400)、BR4.1〜BR4.2(契約#3経由のテーブル単位・カラム単位権限制御)、BR5.1(FKポップアップ検索。参照先テーブルについてもaccessLevel=非表示のカラムを絞り込み条件として受け付けない、iteration 2レビューR-01フォロー)、BR6.1(監査ログ発行、DATA_RECORD_CREATED/UPDATEDのみ)。

**ワークフロー(functional-spec.md)**: 一覧画面/詳細画面/新規作成/更新/FKポップアップ検索、の5つ。詳細画面(ワークフロー2)は、recordIdデコード後にBR2.1の識別子/accessLevel検証を行ってからWHERE句検索を実行する順序に修正した(R-05フォロー)。

**トレーサビリティ(traceability.json)**: upstream_ids = FR3.1〜FR3.3, FR3.5, FR4.1〜FR4.2(unit-of-work-story-map.mdの正式な割当と一致。unit-of-work.mdの範囲表記「FR3.1〜FR3.6」はFR3.4・FR3.6を誤って含む大まかな記法であり、story-mapの個別割当表が正)。

**レビュー結果**: iteration 1(redo jump前)でCritical 1件(recordId経由の非表示カラム値漏えい)・Major 1件(未使用のDATA_RECORD_DELETED)・Minor 3件を検出、いずれも修正済み。iteration 2(redo jump前)でCritical 1件(一覧画面・FKポップアップ検索の検索条件経由の同種のサイドチャネル漏えい、R-01)・Minor 3件(R-02〜R-04)を新規検出した。同一stageのredo jump(dynamic-data-accessの当時のiteration上限到達を受けたエスカレーション)によりレビュー履歴がリセットされ、redo jump後のiteration 1でR-01〜R-04をすべて実際に修正したことを確認したうえで、独立レビューにより詳細画面のrecordIdデコード経路に同種のサイドチャネル(R-05、Critical)を新規発見した。R-05もentities.md/rules.md/functional-spec.mdへ実際に修正を反映済みである(Q4参照)。redo jump後のiteration 2レビューではさらに新規のCritical(R-06、FKポップアップ検索の応答(代表表示列・recordId生成用主キー)にaccessLevelフィルタがかかっていない同種のサイドチャネル)を発見したが、当時はiteration上限のため未修正のまま確定した。今回、stage-level Request Changesによりレビュー履歴が再度リセットされたことを受け、R-06もentities.md/rules.mdへ実際に修正を反映した(Q5参照)。さらに今回(2度目のredo jump後)のiteration 1レビューで、一覧画面の`sort`パラメータにBR1.1・BR5.1と同水準の識別子検証・非表示カラム除外が課されていない新規のCritical(R-10)を検出し、rules.md/functional-spec.mdへ実際に修正を反映した(Q6参照)。

[Answer]: Looks correct

## Q4. レビュー結果への対応記録(R-01〜R-05)

redo jump後のiteration 1レビューで検出された以下の5件は、すべてentities.md/rules.md/functional-spec.md/traceability.jsonへ実際に反映済みである:

- **R-01(Critical、redo jump前のiteration 2で検出、iteration 1で修正確認済み)**: 一覧画面の検索条件受付(BR1.1)・FKポップアップ検索の絞り込み条件受付(BR5.1)が、契約#3のカラム単位accessLevel=非表示を考慮しておらず、検索条件を通じて非表示カラムの値をサイドチャネルで推測できる問題。BR1.1・BR5.1に「呼び出しロールについてaccessLevelが非表示のカラムは検索条件・絞り込み条件として受け付けない(送信されても無視する)」を追加し、functional-spec.mdワークフロー1・5の手順順序も、検索条件受付より前にBR4.2相当の可視性判定を適用する順序へ修正した。
- **R-02(Minor、修正確認済み)**: traceability.jsonのFR3.1エントリのtargetにBR2.1を追加し、rules.md(BR2.1.source)・functional-spec.md(ルールサマリー表)と整合させた。
- **R-03(Minor、修正確認済み)**: Q1のConsolidated Summary Confirmation本文を、JSON配列方式からJSONオブジェクト方式(entities.mdの現行仕様に一致)の記述に更新した。
- **R-04(Minor、修正確認済み)**: recordId生成対象カラムの基準集合が完全に空になる場合(主キー列自体が非表示等)、WHERE句が0件条件になり任意の行にマッチしてしまう問題。BR2.1・entities.mdのnoteに、この場合は404を返す(空のWHERE句で検索を実行しない)ことを明記した。
- **R-05(Critical、redo jump後のiteration 1レビューで新規検出)**: recordIdはクライアントが直接送信するURLパスセグメントであり、Base64はエンコーディングであって署名・暗号化ではないため、改変されたrecordId(accessLevel=非表示のカラムを含むキー、またはTableConfigに存在しない未知のキー)が渡され得る。詳細画面のrecordIdデコード経路(BR2.1・ワークフロー2)は、この改変を検証せずにそのままWHERE句として検索を実行しており、200/404の応答差から非表示カラムの値を推測できるサイドチャネル(R-01と同種)、および未検証の識別子がSQL識別子位置に渡るリスクがあった。修正: BR2.1に、デコードしたキー集合について(a)TableConfig由来の正しい基準集合(既知の識別子)に含まれるか、(b)呼び出しロールについてaccessLevel=非表示でないか、の両方を検索実行前に検証し、いずれかに違反すれば404を返す規定を追加した。functional-spec.mdワークフロー2の手順順序も、検証を検索実行より前に置くよう修正した。

[Answer]: 上記5件を反映済み。Looks correct

## Q5. レビュー結果への対応記録(R-06)

redo jump後のiteration 2レビューで新規検出されたR-06(Critical)は、今回entities.md/rules.md/functional-spec.mdへ実際に修正を反映済みである:

- **R-06(Critical)**: FKポップアップ検索(BR5.1・ワークフロー5)は、検索条件(入力側)についてはR-01でaccessLevelフィルタを適用済みだったが、検索結果の応答(出力側、代表表示列・recordId生成用の主キー値)にはaccessLevelフィルタが一切かかっておらず、view権限さえあれば非表示カラムの値をそのまま取得できてしまうサイドチャネルがあった。修正: BR5.1に、応答生成前に再度BR4.2のaccessLevelを適用する規定を追加した。代表表示列が非表示の場合はその値を返さず、可視な主キー値(または可視カラム基準集合)を代表表示ラベルとして代用する(主キーも非表示なら可視な最初のカラムを代用、全カラム非表示なら空文字列)。recordId生成用の主キー(または全カラム)自体も可視カラムのみに絞る。functional-spec.mdワークフロー5に手順6(応答前のaccessLevel再適用)を追加した。

[Answer]: 上記を反映済み。Looks correct

## Q6. レビュー結果への対応記録(R-10、redo jump後のiteration 1レビューで新規検出)

redo jump後のiteration 1レビューで新規検出されたR-10(Critical)は、今回rules.md/functional-spec.mdへ実際に修正を反映済みである:

- **R-10(Critical)**: 一覧画面の`sort`パラメータ(契約#12)が、対象カラム名をTableConfig由来の既知の識別子集合に限定する検証を持たず、かつaccessLevel=非表示のカラムをソート対象から除外する規定も持っていなかった。BR1.1(検索条件)・BR5.1(FKポップアップ検索)が同種の検証を既に備えているのと非対称であり、動的SQL識別子インジェクション、および非表示カラムでソートした際の行順序を介した値推測(サイドチャネル)の両方のリスクがあった。修正: BR1.2に、sort対象カラム名がTableConfig.columns[]に実在する既知の識別子であること、かつaccessLevel=非表示でないことの両方を検証し、いずれかに違反する場合はそのsort指定を無視してlistOrder順にフォールバックする規定を追加した(BR1.1と同様、明示的なエラーとはしない)。functional-spec.mdワークフロー1手順6にも同様の検証順序を反映した。

[Answer]: Looks correct


