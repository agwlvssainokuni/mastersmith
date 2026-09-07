# Security Design: permission

## セキュアバイデフォルト(テーブル単位)

サービスコンポーネントのテーブル単位権限判定ロジック(BR5.1)は、対象ロール・テーブルのTablePermissionレコードが存在しない場合、すべての操作(list/view/create/edit/delete)を拒否と判定する。これはコード上、「明示的な許可レコードが1件でも見つかった場合のみ許可を検討する」という否定形の実装(ホワイトリスト方式)とし、「拒否レコードがなければ許可」という肯定形の実装(ブラックリスト方式)にしない。

## デフォルト許可(カラム単位)

カラム単位権限判定ロジック(BR5.2)は、対象ロール・テーブル・カラムのColumnPermissionレコードが存在しない場合、editable(更新可)を返す。これは、既存テーブルへの新規カラム追加時に、権限設定を明示的に行うまでは全カラムが非表示になってしまう(業務データが見えなくなる)ことを避けるための設計判断であり、テーブル単位のデフォルト拒否とは意図的に非対称である。

## 管理系エンドポイントの認可

RESTコントローラが受け付ける管理系エンドポイント(ロール・グループのCRUD、メンバー管理、権限設定、ロール割当)は、Spring SecurityによりisAdminクレームを検証する。`GET /api/me/roles`(BR4.1、自身の切替可能ロール一覧取得)のみ、isAdminの有無を問わず全利用者がアクセス可能とする(認証済みであることのみを要求する)。

## 内部呼び出しAPIの認可境界

内部呼び出しAPI(契約#3・#20・#21・#22)は、呼び出し元Unit(dynamic-data-access・auth・account-management・config-management)自身が別途の認可コンテキスト(それぞれのUnitでのisAdmin検証・X-Active-Role検証等)で動作するプロセス内呼び出しであり、permission自身はこの経路にisAdmin検証を適用しない(NFR-AUTHZ.3)。プロセス内呼び出しであるため、ネットワーク境界を越える認証(APIキー等)も不要とする。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T14:52:19Z
**Iteration:** 2

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-04 | Minor | reliability-design.md > 「フェイルセーフな権限判定」節 | 「明示的な『拒否』判定を返すBR4.1(dynamic-data-access)/BR4.3(config-management)のロジック」という並列表現は、両者を同質のものとして扱っているが、実際の挙動は異なる。BR4.1(dynamic-data-access、契約#3)は不許可時に例外を送出し403として応答する明示的な拒否判定である一方、BR4.3(config-management、契約#22経由)はcanList=falseのtableIdを結果集合から静かに除外するフィルタリングであり、エラー応答や例外を伴わない(config-management/functional-design/rules.mdのBR4.3定義を確認。同BRのviolation_behaviourの403は、X-Active-Roleがrolesクレームに含まれない場合のものであり、契約#22の判定結果とは無関係)。本document全体の結論(fail-closed、config-management経路は推定に留まる旨)には影響しないが、この一文の記述精度は改善の余地がある。 | 「明示的な『拒否』判定を返す」という表現をBR4.1のみに限定するか、BR4.3については「(結果集合からの)フィルタリングによる黙示的な除外」等、実際の挙動に即した表現に修正する。 | New |

### Validation Tool Results

自動検証ツールの指定なし(手動でcontract-summary.md #3・#5〜#8・#22、functional-design/rules.md(permission/dynamic-data-access/config-management)との突き合わせを実施)。

| 検証観点 | 結果 |
|---|---|
| R-01(監査ログpublishers一覧との整合) | 解消を確認。contract-summary.md #5〜#8のpublishers `[config-management, dynamic-data-access, auth, account-management]` にpermissionが含まれないという記述はcontract-summary.mdの記述と一致し、observability-design.mdの新記述もこれと矛盾しない。traceability.jsonのNFR3.1行にも同修正が反映されている。 |
| R-02(契約#22経路のフェイルセーフ推定の明記) | 解消を確認。reliability-design.mdは契約#3経路(共通規約Q8の裏付けあり、500)と契約#22経路(契約#22自身のFailure behaviorに明示的裏付けなし、推定に留まる旨を明記)を書き分けており、契約summary.mdの契約#22記述(「該当なし。エラー条件ではない」)と矛盾しない。 |
| R-03(権限判定の可観測性の反映) | 解消を確認。observability-design.mdの「構造化ログ」節に拒否理由(デフォルト拒否/明示的権限不足)を区別するDEBUGレベルの構造化ログ設計が追加されており、nfr-requirements/observability-requirements.md「権限判定の可観測性」節(レスポンスには理由を含めない旨)とも整合する。 |
| 7ファイル間の突合 | performance-design.md・scalability-design.md・logical-components.mdは前回から変更なく、修正後のobservability-design.md・reliability-design.mdの内容と矛盾しない。logical-components.mdのブラストラディウス記述(呼び出し元4Unitへの例外伝播)はreliability-design.mdの記述と整合する。security-design.mdはReview追記の除去のみで本文に変更なし。 |

### Summary

iteration 1で指摘したR-01(監査ログpublishers一覧との齟齬)・R-02(契約#22経路の断定的記述)・R-03(権限判定可観測性の未反映)はいずれも解消されており、上流のcontract-summary.md・functional-design/rules.mdとの矛盾も見当たらない。新たに検出したR-04はreliability-design.md内の一文の表現精度に関するMinor指摘であり、設計全体の実装可能性やfail-closedという結論には影響しない。Critical・Majorのfindingはなく、READYと判定する。
