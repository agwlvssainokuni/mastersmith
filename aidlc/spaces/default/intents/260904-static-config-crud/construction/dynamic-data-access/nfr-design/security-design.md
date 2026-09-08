# Security Design: dynamic-data-access

本Unitの中核はセキュリティ・アクセス制御そのものである。

## テーブル単位・カラム単位の権限確認

サービスコンポーネントは、一覧・詳細・新規作成・更新・FKポップアップ検索のいずれの操作も、permission Unitへの契約#3プロセス内呼び出しでテーブル単位権限(BR4.1)・カラム単位accessLevel(BR4.2)を確認する。テーブル単位で不許可の場合は403、カラム単位の非表示は静かに除外する。

## 動的SQL識別子の安全性(NFR-INJECTION.1)

動的クエリ実行コンポーネントが組み立てるWHERE句・SELECT列・ORDER BY句のテーブル名・カラム名は、必ずTableConfig(契約#2)が提供する既知の識別子集合のみを使用する。値は常にNamedParameterJdbcTemplateのプレースホルダでバインドし、SQL文字列へ直接埋め込まない。この検証は検索条件(BR1.1)・sortパラメータ(BR1.2)・recordIdデコード後のWHERE句組み立て(BR2.1)のいずれにも同様に適用する(サービスコンポーネントが動的クエリ実行コンポーネントへ渡す前に検証する)。

## recordId検証失敗の応答統一

recordIdのデコード失敗、キー検証失敗、accessLevel再検証失敗、該当行なしのいずれも、サービスコンポーネントは応答からは失敗理由を区別できないよう404として統一する(BR2.1)。

## 既知の繰延べ事項(Critical、NFR-SIDECHANNEL.2): BR5.1(FKポップアップ検索)の識別子検証欠如

BR1.1・BR1.2は動的SQLに使用する識別子(検索対象カラム名・sortカラム名)がTableConfig由来の既知の識別子集合に含まれることを明示的に検証する設計になっている。しかしBR5.1(FKポップアップ検索)の「カラムごとの絞り込み条件」については、この識別子検証がrules.md上まだ明記されていない。これはnfr-requirementsステージから継続してCriticalとして記録済みの未解消事項であり、functional-designステージ終了ゲートでも記録済みである。本nfr-designステージでもこのギャップを隠蔽せず、動的クエリ実行コンポーネントの設計責務(上記NFR-INJECTION.1節)がBR5.1の絞り込み条件には現時点で及んでいないことを明示的に記録する。修正(BR5.1へのTableConfig由来識別子検証の追記、およびサービスコンポーネントでの検証実装)は本ステージのスコープ外とし、code-generation段階以降で対応する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T13:41:33Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

このステージに定義済みの自動検証ツールは実行対象として指定されていない。以下の目視クロスチェックを実施した。

- security-design.mdの内容(テーブル単位・カラム単位権限確認、動的SQL識別子の安全性、recordId検証失敗の応答統一、NFR-SIDECHANNEL.2の既知繰延べ事項)を、nfr-requirements/security-requirements.md(NFR-AUTHZ.1・NFR-AUTHZ.2・NFR-INJECTION.1・NFR-SIDECHANNEL.1・NFR-SIDECHANNEL.2)と突き合わせたところ、内容・粒度ともに整合しており齟齬はない。
- NFR-SIDECHANNEL.2節が参照するBR5.1(rules.md)の現行記述を確認した。参照先テーブルのaccessLevel=非表示カラムの絞り込み条件・応答からの除外は明記されているが、絞り込み条件のカラム名自体がTableConfig由来の既知識別子集合に含まれることを検証する規定は依然として存在しない。これはfunctional-spec.mdの`## Review`(iteration 2)でR-11としてSeverity: Critical、Status: Newのまま記録されている内容と一致しており、security-design.mdの記述はこの既知ギャップを過不足なく正確に反映している(誇張・過小評価・隠蔽のいずれもなし)。このギャップ自体は本ステージのスコープ外(code-generation段階以降で対応)として明示されており、新たな指摘としては計上しない。
- logical-components.mdが定義する3コンポーネント(RESTコントローラ・サービス・動的クエリ実行)以外への参照はsecurity-design.md本文になく、参照先はすべてlogical-components.mdで定義済みのコンポーネントに解決する。
- traceability.jsonのcoverageはNFR-AUTHZ.1・NFR-AUTHZ.2・NFR-INJECTION.1・NFR-SIDECHANNEL.1がsecurity-design.mdの該当節を正しく指しており、NFR-SIDECHANNEL.2もstatus:Deferredとして同様に正確に指している。
- performance-design.md・reliability-design.md・scalability-design.md・observability-design.mdの内容も併せて確認したが、rules.md(BR1.1〜BR6.1)・contract-summary.md(契約#2・#3)との矛盾や、logical-components.md未定義のコンポーネントへの参照は見当たらない。reliability-design.mdの「permission呼び出し失敗時の扱い」節は、nfr-requirements時点でR-01として指摘されていたfail-closed/fail-open未規定のギャップを設計レベルで解消済みである。
- 今回の変更はsecurity-design.mdのFindingsテーブル記法崩れ(テーブル行内の「(指摘なし)」)を、テーブルを使わない単独行の「(指摘なし)」に修正したもののみであり、本文(NFR-SIDECHANNEL.2の記録内容を含む)に変更はない。差分は記法修正に限定されていることを確認した。

### Summary

security-design.mdは前回READY判定時点から本文の内容に変更がなく、今回の修正はFindingsテーブルの記法崩れの是正のみである。nfr-requirements・rules.md・functional-spec.md・contract-summary.mdとの整合性、logical-components.mdへのコンポーネント参照の妥当性、traceability.jsonの網羅性のいずれにも問題はなく、既知のCritical繰延べ事項(NFR-SIDECHANNEL.2/BR5.1の識別子検証欠如)もfunctional-spec.mdのR-11(Critical、Status: New)と正確に整合したまま記録されている。指摘事項はなく、READYとする。
