# Security Design: account-management

## 管理者限定アクセス

RESTコントローラは、account-managementの全操作(アカウント作成・一覧・編集・無効化)についてisAdminクレームを検証する(BR5.1、NFR-AUTHZ.1)。isAdminがfalseの場合は403を返す。

## 認証情報の委譲

サービスコンポーネントは、emailの一意性検証・パスワードハッシュ化・保存をauth(契約#4)へ委譲する(BR1.2、NFR-DATA.1)。本Unit自身はemailの重複チェックを行わず、パスワード・パスワードハッシュを一切保持・参照しない。

## アカウント無効化の委譲

アカウント無効化(DELETE /api/admin/accounts/{id})は、サービスコンポーネントがauth(契約#4)へ無効化を委譲するのみであり、論理削除・リフレッシュトークン失効の実処理はauth側の責務である(BR4.1、NFR-DATA.2)。本Unit自身はトークン失効処理を行わない。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T13:38:18Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージの検証は目視でのクロスリファレンス照合により実施した(自動検証ツールの指定なし)。

| 検証観点 | 結果 |
|---|---|
| nfr-requirements(performance/security/scalability/reliability/observability-requirements.md)のNFR IDと本Unit7ファイルの参照整合性 | 一致(NFR1.1, NFR1.2, NFR-AUTHZ.1, NFR-DATA.1, NFR-DATA.2, NFR2.1, NFR-CONSISTENCY.1, NFR-FAILSAFE.1, NFR-FAILSAFE.2, NFR3.1, NFR3.2 すべて出典文書に定義済み) |
| functional-design/rules.md(BR1.1〜BR5.1)との整合性 | 一致(BR1.2/BR1.3/BR4.1/BR5.1の記述内容がsecurity-design.md・reliability-design.mdの記述と矛盾なし) |
| entities.md・contract-summary.md(契約#4・#21)との整合性 | 一致(account-managementは永続エンティティを持たず、契約#4(auth)・契約#21(permission)経由の委譲のみである旨が全ファイルで一貫) |
| logical-components.mdで定義済みのコンポーネント以外への参照有無 | なし(RESTコントローラ・サービスの2コンポーネントのみを参照、auth/permissionへの依存は契約経由と明記) |
| traceability.jsonの網羅性 | 11件の上流NFR IDすべてがcoverageにOKで記載され、未網羅・過剰記載なし |
| reliability-design.mdの「アカウント無効化とリフレッシュトークン失効の依存関係」節とauth Unit側既知ギャップの現状整合性 | 一致。auth/functional-design/functional-spec.md `## Review` R-03(Status: New)、auth/nfr-design/security-design.md `## Review` R-01・R-02(Status: Unresolved (Accepted as deferred / non-blocking))のいずれとも矛盾なく、「解消されるまで残る既知の依存先ギャップ」という記述は現状のStatusと整合している |

### Summary

前回iteration 1でREADY判定を受けた時点から、account-management Unitのnfr-design成果物7ファイルの内容に変更はない。今回はdynamic-data-access Unitの表記フォーマット不具合修正に伴うstage-level Request Changesによるper-unitレビュー状態リセットを受けての再確認であり、上流のnfr-requirements・functional-design(rules.md/functional-spec.md/entities.md)・contract-summary.md(契約#4・#21)との整合性、logical-components.mdで定義されたコンポーネント以外への参照がないこと、traceability.jsonの網羅性、およびauth Unit側の既知ギャップ(functional-spec.md R-03、security-design.md R-01・R-02)との整合性をいずれも再確認した結果、指摘事項はなく、前回同様READYと判定する。
