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
**Date:** 2026-09-07T15:12:00Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ定義にvalidation toolの指定はなく、実行していない。

### Summary

performance/security/scalability/reliability/observability-design.md、logical-components.md、traceability.jsonの7ファイルを、functional-design/rules.md(BR1.1〜BR5.1)・functional-spec.md・entities.md、上流のnfr-requirements/7ファイル(および同ステージの`## Review`)、inception/contract-design/contract-summary.md(契約#4・#21)、および auth Unitのfunctional-design/functional-spec.md `## Review`(R-03)・nfr-design/security-design.md `## Review`(R-01)と突き合わせた。

- **コンポーネント境界の健全性**: logical-components.mdはRESTコントローラ・サービスの2コンポーネントのみを定義し(リポジトリコンポーネントなし、account-managementが永続エンティティを持たないことと整合)、他6ファイルのいずれもこの2コンポーネント以外への参照を含まない。過去Unitのレビューで見つかったパターン(1)は本Unitには該当しない。
- **上流NFR要件との一致**: 設計側traceability.jsonのupstream_ids(NFR1.1・NFR1.2・NFR-AUTHZ.1・NFR-DATA.1・NFR-DATA.2・NFR2.1・NFR-CONSISTENCY.1・NFR-FAILSAFE.1・NFR-FAILSAFE.2・NFR3.1・NFR3.2)は、nfr-requirements配下の各ファイルで定義された同名IDと過不足なく一致し、coverageの記述内容(page=0/size=20ページネーション、isAdmin 403、契約#4/#21への委譲範囲、BR1.3の部分失敗許容、監査ログ3種等)もいずれも要件側の記述と正確に対応している。
- **functional-design(rules.md)との整合**: BR1.1〜BR1.5・BR2.1・BR2.2・BR3.1・BR3.2・BR4.1・BR4.2・BR5.1のいずれについても、対応する設計ファイルの記述(RESTコントローラのisAdmin検証、サービスの契約#4/#21委譲、BR1.3の部分失敗許容ロジック、ACCOUNT_CREATED/UPDATED/DISABLEDの監査ログ)がBRの内容と矛盾なく一致している。
- **契約(#4・#21)との整合**: security-design.mdの「emailの一意性検証・パスワードハッシュ化・保存をauthへ委譲」「無効化の論理削除・トークン失効はauth側の責務」、reliability-design.mdの「初期ロール割当失敗時のAccountロールバックなし」は、いずれもcontract-summary.md 契約#4・#21のConsumer passes/Provider returns/Failure behaviorの記述と正確に一致する。過去パターン(2)に該当する矛盾は見つからなかった。
- **繰延べギャップの可視性**: アカウント無効化時のRefreshToken即時失効(契約#4がauth側での反映を求めている事項)は、auth Unitのfunctional-spec.md `## Review`(R-03、Major、Status: New)およびnfr-design/security-design.md `## Review`(R-01、Major、Status: New)で未解消のまま記録されている。本Unitのreliability-design.mdはこの依存先ギャップの存在(「auth Unit側で未反映のままであり…既に記録済みの未解消事項である」)とその影響(NFR-FAILSAFE.1)を正確に記述しており、既存の繰延べギャップが本stageの成果物から見えなくなるという過去パターン(3)には該当しない。文言はnfr-requirements/reliability-requirements.mdの`## Review`(R-01、Minor)で指摘済みの「繰延べ事項」という語感のずれをそのまま引き継いでいるが、実害のないMinor事項であり本stageで新たに追加された問題ではないため、本レビューでは新規指摘としては起票しない。

Critical・Majorな欠陥は検出されなかった。7ファイル間の矛盾も見つからず、開発者はこの設計から追加の確認なしに実装に着手できる。
