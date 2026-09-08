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
**Date:** 2026-09-08T22:01:27Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ定義にvalidation toolの指定はなく、実行していない。performance-design.md・security-design.md・scalability-design.md・reliability-design.md・observability-design.md・logical-components.md・traceability.jsonの7ファイルは、auth Unitのsecurity-design.mdへのstage-level Request Changes(Status値の表記修正)による全Unitリセットの影響を受けたのみで、本Unit自身の本文は前回READY判定時点から一切変更されていないことを確認した(旧`## Review`セクションの除去のみ)。

### Summary

nfr-requirements(performance/security/scalability/reliability/observability-requirements.md)、functional-design(rules.md BR1.1〜BR5.1、functional-spec.md、entities.md)、inception/contract-design/contract-summary.md契約#4・#21と突き合わせ、独立に再検証した。logical-components.mdで定義された2コンポーネント(RESTコントローラ、サービス)以外への参照は7ファイル中に見られず、リポジトリコンポーネントを持たないという設計とも整合している。isAdmin検証による403応答、email一意性検証・パスワードハッシュ化のauthへの全面委譲、無効化(disable)の論理削除限定とトークン失効のauth委譲、page=0/size=20の既定ページネーション、単一インスタンス構成、BR1.3の部分失敗許容(400応答へのaccountId同梱)、監査ログのactionType語彙(ACCOUNT_CREATED/UPDATED/DISABLED)は、いずれも上流のnfr-requirements・functional-designの記述と正確に一致している。

reliability-design.md「アカウント無効化とリフレッシュトークン失効の依存関係」節が依存を明記するauth Unit側の既知ギャップについては、現時点のStatus値を実際に照合した。auth/functional-design/functional-spec.md R-03は**Status: New**(無効化時のRefreshToken即時失効ロジックが未実装のまま)であり、auth/nfr-design/security-design.md R-01・R-02は**Status: Accepted risk**(同一ギャップをリスクとして受容した表記修正済み)である。account-management側の記述「auth Unit自身のfunctional-design・nfr-design双方で既に記録済みの未解消事項である」は、ギャップの実体(無効化後も最大7日間セッションが継続し得ること)がいずれの段階でも未解消のまま残っている点では正確である。ただし「未解消事項」という表現は、nfr-design側がStatus: Accepted risk(リスクとして正式に受容・記録済み)であるのに対し、functional-design側のStatus: New(受容の判断すらまだ行われていない未対応)とは状態の性質が異なる。この語感のずれは前回サイクルのnfr-requirements/security-requirements.mdレビュー(R-01、Minor、Status: New)で既に指摘済みの同種の軽微な問題であり、実害はなく(ギャップの存在自体・影響範囲の記述はいずれも正確)、READY判定をブロックするものではないため、今回は新規Findingとして起票せずSummaryへの記録に留める。

Critical/Majorな欠陥は検出されず、前回READY判定と同じ結論に至った。
