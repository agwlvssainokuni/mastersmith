# Security Requirements: account-management

## NFR-AUTHZ.1: 管理者限定アクセス

account-managementの全操作(アカウント作成・一覧・編集・無効化)は、アクセストークンのisAdminクレームを持つ利用者のみが実行できる(BR5.1)。isAdminクレームがfalseの場合は403を返す。

## NFR-DATA.1: 認証情報の委譲

emailの一意性検証・パスワードハッシュ化・保存は、Accountの唯一の所有者であるauthに委譲する(BR1.2)。本Unit自身はemailの重複チェックを行わず、パスワード・パスワードハッシュを一切保持・参照しない。アカウント一覧・詳細(AccountView)はauth(契約#4)・permission(契約#21)から取得したデータを合成して構成するのみである。

## NFR-DATA.2: アカウント無効化の委譲

アカウント無効化(DELETE /api/admin/accounts/{id})は論理削除のみとし、物理削除は行わない(BR4.1)。無効化に伴う既存リフレッシュトークンの即時失効はauthの責務であり、本Unit自身はトークン失効処理を行わない。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T14:02:25Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | construction/account-management/nfr-requirements/reliability-requirements.md > NFR-FAILSAFE.1 | 「この即時失効の実装はauth Unit側でまだ反映されておらず…既に繰延べ事項として記録済みの未解消ギャップである」との記述は、参照先であるauth Unitのfunctional-design/functional-spec.md `## Review`(R-03)およびnfr-requirements/security-requirements.md `## Review`(R-01)の実際のStatusがいずれも「New」(READY判定の許容範囲内で残った未解消のMajor指摘であり、正式に「繰延べ」と分類された記録ではない)である点とはやや語感がずれる。内容の実質(無効化後も最大7日間セッションが継続し得るという既知のギャップの存在自体)は両レビューの記述と正確に一致しており、実害はない。 | 「繰延べ事項として記録済み」を「未解消のレビュー指摘(Status: New)として記録済み」等、Statusの実態に即した表現に軽微に修正するとより正確になる。ブロッキングではない。 | New |

### Validation Tool Results

本ステージ定義にvalidation toolの指定はなく、実行していない。

### Summary

performance/security/scalability/reliability/observability-requirements.md、tech-stack-decisions.md、traceability.jsonの7ファイルを、functional-design/rules.md(BR1.1〜BR5.1)・functional-spec.md、requirements.md(NFR1〜NFR3)、およびauth Unitのfunctional-design/functional-spec.md `## Review`(R-03)・nfr-requirements/security-requirements.md `## Review`(R-01)と突き合わせた。ページネーション既定値(page=0/size=20)、isAdmin必須の403応答、契約#4/#21への委譲範囲、BR1.3の部分失敗許容(400応答へのaccountId同梱)、監査ログのactionType語彙(ACCOUNT_CREATED/UPDATED/DISABLED)は、いずれもrules.mdの記述と正確に一致し、7ファイル間の矛盾も見つからなかった。traceability.jsonのNFR1〜NFR3のOK判定、NFR4〜NFR9のN/A判定(理由: パッケージング・内部H2エンティティなし・スキーマ読み込み層固有・パスワードハッシュ化委譲・HTTPS終端は横断関心事・初期管理者アカウントはauth側)はいずれも根拠が妥当で過不足がない。NFR-FAILSAFE.1のauth Unit側ギャップへの言及も、参照先レビューの実質的内容と正確に一致している(R-01は語感の軽微な指摘のみ)。Critical/Majorな欠陥は検出されなかった。
