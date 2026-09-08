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
**Date:** 2026-09-07T20:45:49Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 手動突合(自動検証ツールの指定なし) | 差分なし | permission Unitのnfr-design成果物7ファイル(performance-design.md、security-design.md、scalability-design.md、reliability-design.md、observability-design.md、logical-components.md、traceability.json)の内容は、iteration 2でREADY判定を受けた時点から変更されていないことを確認した。今回のレビュー依頼は、別Unit(dynamic-data-access)のFindingsテーブル記法不具合の是正に伴うnfr-designステージ全体のstage-level Request Changesによって、per-unit reviewステータスがフレームワーク側の状態管理上リセットされたことによる再確認である。 |

### Summary

以下の観点で再検証し、いずれも整合していることを確認した。

1. **観測性設計と共有契約の整合**: observability-design.mdの「監査ログ対象範囲」節は、contract-summary.md「監査ログイベント契約(#5〜#8)」のpublishers一覧 `[config-management, dynamic-data-access, auth, account-management]` に照らして再確認した。permissionはこの一覧に含まれておらず、`actionType`語彙も定義されていないため、AuditableActionOccurredEventの発行対象外とする記述は引き続き契約summary.mdの実際の記述と一致している。functional-design/rules.mdにもpermission自身の監査ログ発行に相当するBR(BR8.1相当)は存在せず、整合している。
2. **信頼性設計とnfr-requirements時点の繰延べ事項(R-01)**: reliability-design.mdは、nfr-requirements/security-requirements.mdのReviewで指摘されたMajor R-01(NFR-FAILSAFE.1がBR4.1・契約#3のFailure behaviorを「permission自身の障害」の根拠として誤引用していた問題)を踏まえ、(a)明示的な不許可判定(dynamic-data-access BR4.1・config-management BR4.3)によるfail-closedの経路と、(b)permission自身の未捕捉例外・応答不能の経路(契約summary.md共通規約Q8により500)とを明確に分離して記述しており、traceability.jsonのカバレッジ記載(「5xx応答の帰属先を契約summary共通規約〈Q8〉に正しく再帰属させた」)と実際の内容が一致している。契約#22のFailure behaviorが「該当なし(エラー条件ではない)」とだけ規定し、permission自身の内部例外時の挙動を明示的に規定していない点も、contract-summary.mdの契約#22本文と一致する形で正直に記述されている。
3. **論理コンポーネントと参照整合**: logical-components.mdが挙げる4コンポーネント(RESTコントローラ・内部呼び出しAPI・サービス・リポジトリ)以外への参照は、7ファイル中に見当たらない。内部呼び出しAPIコンポーネントが受け付ける契約#3・#20・#21・#22は、いずれもcontract-summary.mdの「プロセス内同期呼び出し契約」節に実在し、Consumer/Provider/Failure behaviorの記述内容も設計側の記述と矛盾しない。
4. **traceability.jsonの網羅性**: upstream_idsに列挙された11件のNFR ID(NFR1.1・NFR1.2・NFR-AUTHZ.1〜.3・NFR-FAILSAFE.1×2ファイル・NFR2.1・NFR2.2・NFR3.1・NFR5)はすべてcoverage配列でstatus: OKとして個別にターゲットへ紐付けられており、抜け・重複はない。NFR5(内部H2の`ms_`接頭辞)はtech-stack-decisions.md由来の全Unit共通NFRであり、logical-components.mdの記述(6エンティティは本Unit専有テーブル)とも矛盾しない。

以上より、permission Unitのnfr-design成果物はiteration 2時点の内容から変更されておらず、上流のnfr-requirements・functional-design(rules.md/functional-spec.md)・契約summary.mdとの整合も引き続き確認できたため、READY判定を維持する。
