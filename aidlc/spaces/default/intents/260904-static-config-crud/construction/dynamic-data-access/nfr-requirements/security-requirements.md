# Security Requirements: dynamic-data-access

本Unitの中核はセキュリティ・アクセス制御そのものである。

## NFR-AUTHZ.1: テーブル単位の権限確認

一覧・詳細・新規作成・更新・FKポップアップ検索のいずれの操作も、契約#3(dynamic-data-access → permission)を呼び出し、X-Active-Roleヘッダーのroleidに対するテーブル単位の権限(list/view/create/edit)を確認する(BR4.1)。不許可の場合は403を返す。

## NFR-AUTHZ.2: カラム単位の権限制御

各カラムについて、契約#3から取得したカラム単位のaccessLevel(更新可/表示のみ/非表示)に基づき、レスポンス・入力受付を制御する(BR4.2)。accessLevel=非表示のカラムはレスポンスに含めず、表示のみのカラムは更新入力を無視する。

## NFR-INJECTION.1: 動的SQL識別子の安全性

動的に組み立てるWHERE句・SELECT列・ORDER BY句のテーブル名・カラム名は、必ずTableConfig(契約#2)が提供する既知の識別子集合のみを使用する。値は常にNamedParameterJdbcTemplateのプレースホルダでバインドし、SQL文字列へ直接埋め込まない。この検証は検索条件(BR1.1)だけでなく、sortパラメータ(BR1.2、R-10フォロー: 当初は検索条件にのみ課されていた識別子検証・非表示カラム除外が、sortパラメータには課されていなかったため動的SQL識別子インジェクションと行順序を介した非表示値の推測が可能だった)、およびrecordIdデコード後のWHERE句組み立て(BR2.1)にも同様に適用する。

## NFR-SIDECHANNEL.1: recordId検証失敗の応答統一

recordIdのデコード失敗、キー検証失敗(TableConfig由来の既知の識別子集合に含まれないキーの存在)、accessLevel再検証失敗(非表示カラムを含むrecordId)、該当行なしのいずれも、応答からは失敗理由を区別できないよう404として統一する(BR2.1)。区別できてしまうこと自体がサイドチャネルになるため。

## NFR-SIDECHANNEL.2(既知の繰延べ事項・Critical): BR5.1(FKポップアップ検索)の識別子検証欠如

BR1.1(一覧検索)・BR1.2(ソート)は、動的SQLに使用する識別子(検索対象カラム名・sortカラム名)がTableConfig由来の既知の識別子集合に含まれることを明示的に検証し、かつaccessLevel=非表示のカラムを条件として受け付けない設計になっている(NFR-INJECTION.1)。しかしBR5.1(FKポップアップ検索、GET /api/data/{tableId}/fk-search/{columnName})の「カラムごとの絞り込み条件」については、accessLevel=非表示のカラムを条件として受け付けない旨は明記されているものの、絞り込み条件のカラム名自体がTableConfig由来の既知の識別子集合に含まれることを検証する旨がBR5.1の記述に明記されていない。これはBR1.2で修正されたR-10と同種の脆弱性クラス(動的SQL識別子インジェクション、および行の有無を介した非表示値の推測)であり、dynamic-data-access Unitのfunctional-designステージ終了ゲートで既にCritical(R-11)として記録済みの未解消事項である。本nfr-requirementsステージではこのギャップを隠蔽せず、本ドキュメントに明示的な繰延べ事項として記録する。修正(BR5.1へのTableConfig由来識別子検証の追記)は本ステージのスコープ外とし、code-generation段階以降で対応する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T14:10:22Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | reliability-requirements.md > NFR-FAILSAFE.1 | 見出しは「permission呼び出し失敗時の扱い」(契約#3 permissionの呼び出しが失敗した場合の挙動)を謳っているが、本文の記述は「X-Active-Roleがアクセストークンのrolesクレームに含まれない場合は403を返す」というBR4.1の正常系ロジックの再掲にとどまっており、permissionサービス自体の呼び出し失敗(タイムアウト・接続不可・5xx応答等)が発生した場合にfail-closed(403/5xx等でアクセス拒否)とfail-open(権限確認をスキップして処理続行)のいずれで扱うかが、rules.md(BR4.1)・functional-spec.mdのいずれにも明記されていない。本Unitはセキュリティ上最もクリティカルなUnitであり、権限確認の唯一の経路である契約#3呼び出し自体が失敗した際の挙動が未定義のままだと、実装者がfail-openな実装(権限確認をスキップしてリクエストを通す)を選んでしまう余地が残る。数値SLA目標の話ではなく、見出しが予告する内容と本文の内容が一致していない、およびセキュリティ上重要な障害モードが未規定という指摘である | NFR-FAILSAFE.1に、permissionサービスへの呼び出し自体が失敗(タイムアウト・接続不可・エラー応答)した場合はfail-closed(例: 403または5xxを返し、当該操作を許可しない)とする旨を明記する。可能であればrules.md BR4.1のlogic欄にも同じ規定を反映する | New |

### Validation Tool Results

このステージに定義済みの自動検証ツールは実行対象として指定されていない。目視でのクロスチェックのみ実施した。

- rules.md BR5.1の現在の記述を確認したところ、accessLevel=非表示のカラムを絞り込み条件・応答から除外する規定は明記されているが、絞り込み条件のカラム名自体がTableConfig由来の既知の識別子集合に含まれることを検証する規定は依然として記載されていない。NFR-SIDECHANNEL.2の記述(識別子検証欠如)は現時点のrules.mdの内容と一致しており、既に修正済みの内容を未解消と誤記してはいない。
- functional-spec.mdの`## Review`セクション(iteration 2、Verdict: NOT-READY)を確認したところ、R-11がSeverity: Critical、Status: Newとして記録されており、rules.md BR5.1「絞り込み条件付きで検索を行い」/logic欄の識別子検証欠如を指摘する内容である。security-requirements.mdのNFR-SIDECHANNEL.2セクションは、このR-11の重大度(Critical)・未解消状態(New = 未解消)・指摘内容(識別子検証欠如、R-10と同種の脆弱性クラス)を過不足なく正確に反映しており、誇張・過小評価のいずれも見られない。
- NFR-INJECTION.1・NFR-SIDECHANNEL.1の記述をrules.md BR1.1・BR1.2・BR2.1と突き合わせたところ、いずれも正確に一致している(動的SQL識別子はTableConfig由来の既知集合に限定・値はプレースホルダバインド・recordId検証失敗の4パターンすべてを404に統一、という記述に齟齬なし)。
- traceability.jsonのupstream_ids(NFR1〜NFR9)とcoverageは、requirements.mdのNFR1〜NFR9の内容および他Unit(schema-ingestion、account-management/auth、packaging)への責務分担と整合しており、OK/N/Aの判定根拠(理由文)もrequirements.md・constraint-register.mdの記載と矛盾しない。7ファイル間(performance/security/scalability/reliability/observability/tech-stack-decisions/traceability)でBR番号・NFR番号の相互参照に矛盾は見当たらない。

### Summary

本Unitのnfr-requirements成果物は、rules.mdの該当BR(特にBR1.1・BR1.2・BR2.1・BR5.1)およびfunctional-spec.mdのReviewセクション(R-10・R-11)の内容を正確に反映しており、R-11の既知ギャップ(BR5.1の識別子検証欠如)を隠蔽せず明示的に記録している点は方針どおりである。唯一、reliability-requirements.mdのNFR-FAILSAFE.1が見出しの予告(permission呼び出し失敗時の扱い)と本文の内容(役割クレーム未包含時の403)が食い違っており、permissionサービス呼び出し自体の失敗時のfail-closed/fail-open方針が未規定という指摘(R-01、Major)がある。Critical指摘はなくMajorが1件のみのため、検証ルール(Critical 0件・Major 2件以下)によりREADYとする。
