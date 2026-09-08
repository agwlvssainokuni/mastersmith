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
**Date:** 2026-09-08T22:34:23Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

このstageに定義済みの自動検証ツールは実行対象として指定されていない。手動で以下を確認した。

- traceability.jsonの`upstream_ids`(13件)は、nfr-requirements配下の各要件ファイル(performance/scalability/reliability/observability/security-requirements.md)に定義されたNFR ID(13件)と過不足なく一致しており、未カバー・余剰のいずれも見当たらない。
- logical-components.mdが定義する3コンポーネント(RESTコントローラ・サービス・動的クエリ実行)以外への参照は、performance/scalability/reliability/observability/security-design.mdのいずれにも見当たらない。他Unit(permission、config-management、audit-log)への言及は、いずれも既存契約(契約#2・#3・#5〜#8)への参照として一貫している。
- security-design.mdのNFR-SIDECHANNEL.2(既知の繰延べ事項)の記述を、rules.md BR5.1の現行記述(識別子検証の付記なし、accessLevel=非表示のカラム除外のみ明記)、functional-spec.mdの`## Review`セクション(R-11、Severity: Critical、Status: New)、nfr-requirements/security-requirements.mdのNFR-SIDECHANNEL.2記述と突き合わせた。3者の内容は一致しており、security-design.mdの記述に誇張・過小評価は見当たらない。既知ギャップの隠蔽もない。
- reliability-design.mdの「permission呼び出し失敗時の扱い」節は、nfr-requirements時点で指摘されていたNFR-FAILSAFE.1の見出し・本文不一致(Major、R-01)を、明示的拒否(403)と呼び出し失敗(500、fail-closed)の書き分けとして設計レベルで補完しており、矛盾は見当たらない。

### Summary

本Unitのnfr-design成果物7ファイルは、上流のnfr-requirements・functional-design(rules.md、functional-spec.mdのReviewセクション)と整合しており、コンポーネント参照もlogical-components.mdで定義された3コンポーネントの範囲に収まっている。既知のCritical繰延べ事項(NFR-SIDECHANNEL.2、BR5.1の識別子検証欠如)は、誇張・過小評価なく正確に記録されており、修正がcode-generation段階以降であることも明記されている。内容は前回READY判定時点から変更されておらず、独立した再検証の結果も同じくREADYとする。
