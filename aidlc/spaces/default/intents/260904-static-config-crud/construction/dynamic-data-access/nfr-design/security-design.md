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
**Date:** 2026-09-07T15:15:24Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| (指摘なし) | - | - | - | - | - |

### Validation Tool Results

このstageに定義済みの自動検証ツールは実行対象として指定されていない。以下は目視によるクロスチェックの結果である。

- **security-design.md「既知の繰延べ事項(Critical、NFR-SIDECHANNEL.2)」節**: rules.md BR5.1のstatement/logic欄を確認したところ、「accessLevel=非表示のカラムは絞り込み条件として受け付けない」旨は明記されているが、絞り込み条件のカラム名自体がTableConfig由来の既知の識別子集合に含まれることの検証は依然として明記されていない。functional-spec.mdの`## Review`(iteration 2、Verdict: NOT-READY)にはR-11がSeverity: Critical、Status: Newとして同一内容(識別子検証欠如、R-10と同種の脆弱性クラス)で記録されている。security-design.mdの記述は、このギャップの重大度・未解消状態・内容を過不足なく正確に反映しており、誇張・過小評価は見られない。
- **reliability-design.md「permission呼び出し失敗時の扱い」節**: nfr-requirementsステージのレビュー指摘R-01(Major、見出し「permission呼び出し失敗時の扱い」と本文〈BR4.1の正常系403の再掲のみ〉の不一致、呼び出し自体の失敗時のfail-open/fail-closed方針が未規定)を踏まえ、本節は「明示的な拒否判定(403、契約#3のFailure behaviorが規定する範囲)」と「呼び出し自体の失敗(500、契約#3のFailure behaviorが規定する範囲外)」を明確に書き分けている。契約summary.md契約#3のFailure behaviorは「不許可の場合は例外とし…403として応答する」とのみ規定しており、呼び出し自体が失敗するケース(タイムアウト・接続不可等)には言及していないため、「契約#3の範囲外」という記述は根拠のある正確な記述である。500への割り当ても契約summary.mdの共通規約(Q8、500=予期しないエラー)と整合しており、fail-closedである旨の記述にも矛盾はない。これによりR-01の指摘は本ステージの成果物上で解消されている。
- **logical-components.mdとの整合**: logical-components.mdが定義する3コンポーネント(RESTコントローラ・サービス・動的クエリ実行)以外への依存・参照は、performance-design.md・scalability-design.md・reliability-design.md・observability-design.md・security-design.mdのいずれにも見当たらない。逆に、各設計ファイルが言及するコンポーネント責務(識別子検証・プレースホルダバインド・accessLevel確認・監査イベント発行等)は、いずれもlogical-components.mdの「責務」列に対応する記載がある。
- **traceability.jsonの網羅性**: upstream_idsは、nfr-requirements/traceability.jsonで確定した13件のNFR項目(NFR1.1・NFR1.2、NFR-AUTHZ.1・NFR-AUTHZ.2・NFR-INJECTION.1・NFR-SIDECHANNEL.1・NFR-SIDECHANNEL.2、NFR2.1、NFR-CONSISTENCY.1・NFR-CONSISTENCY.2・NFR-FAILSAFE.1、NFR3.1・NFR3.2)と過不足なく一致している。coverageの各targetも実際の各設計ファイルの該当節と一致しており、架空の参照は見当たらない。NFR-SIDECHANNEL.2のstatusは"Deferred"として正直に記録されており、他のOK項目に紛れて隠蔽されていない。

### Summary

本Unitのnfr-design成果物は、NFR-SIDECHANNEL.2の既知のCritical繰延べ事項(BR5.1の識別子検証欠如)を誇張・過小評価なく正確に記録しており、かつnfr-requirementsステージのR-01(permission呼び出し失敗時のfail-open/fail-closed未規定)を明示的な拒否〈403〉と呼び出し失敗〈500〉の書き分けにより根拠を持って解消している。logical-components.mdに定義されないコンポーネントへの参照や、traceability.jsonの過不足も見当たらない。Critical・Major指摘なしのためREADYとする。
