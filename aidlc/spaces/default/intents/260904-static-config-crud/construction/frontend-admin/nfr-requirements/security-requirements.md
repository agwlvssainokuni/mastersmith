# Security Requirements: frontend-admin

## NFR-AUTHZ.1: 管理者ゲーティング

全画面(ロール一覧・編集、ロール割り当て、グループ管理、監査ログ、アカウント管理、設定管理、スキーマ取り込み、エクスポート/インポート、メニュー管理)、およびAppShellのサイドナビにおけるこれらの画面への導線は、アクセストークンのisAdminクレームを持つ利用者にのみ表示・アクセス可能とする(functional-spec.md「管理者ゲーティング」節)。isAdminクレームを持たない利用者には、サイドナビの該当項目自体が表示されない。

## NFR-AUTHZ.2: 認可判定のバックエンド一元化

frontend-admin自身は認可判定ロジックを持たない。URLを直接指定してアクセスした場合は、各画面の初回データ取得APIが返す403に応じて「この操作を行う権限がありません」というエラー状態に切り替えるのみである。isAdminクレームの発行元・検証はauth Unitおよび各バックエンドUnitの責務であり、frontend-admin側でのクレーム偽装対策(改ざん検出等)は行わない(JWT自体の署名検証はバックエンド側の責務)。

## NFR-INJECTION.1: 表示内容のエスケープ

業務DB由来の識別子文字列(テーブル名・カラム名・論理表示名等)や利用者入力値(ロール名・グループ名等)を画面に描画する際は、React/TSXの標準エスケープ機構に従う。dangerouslySetInnerHTML等、標準エスケープを迂回する手段は用いない。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T14:17:30Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | traceability.json > coverage[NFR3] | NFR3(requirements.md: Twelve-Factor App準拠・OpenTelemetry対応・構造化ログ)のstatusが"OK"とされ、根拠としてNFR-AUTHZ.2(security-requirements.mdの403応答に基づくエラー表示切替)が「UI側の可観測性の主要な具体化」と説明されている。しかしNFR-AUTHZ.2は認可エラー時の画面表示切替であり、観測可能性(構造化ログ・分散トレーシング)とは異なる関心事であって、両者を結びつける論理的根拠が本Unit成果物のどこにも存在しない。frontend-adminは4ファイルのみを成果物とし、ログ出力形式やトレース計装に関する記述はperformance-requirements.md/security-requirements.md/tech-stack-decisions.mdのいずれにも無い。実質的にはNFR2・NFR5〜NFR9と同様「UI Unit固有のNFR成果物には具体化しない」ためN/Aとすべき対象を、無関係な認可項目を根拠にOKと誤判定している。 | traceability.jsonのNFR3のstatusを、他のバックエンド関心事(NFR2/NFR5/NFR6/NFR7/NFR8/NFR9)と同様の理由でN/Aに修正するか、UI側で実際に構造化ログ・トレース計装に該当する記述を追加したうえでOKの根拠を差し替える。 | New |
| R-02 | Minor | performance-requirements.md > NFR1.2 | 「監査ログ画面(ワークフロー5)・アカウント一覧(ワークフロー6)・テーブル一覧(ワークフロー7)はいずれもバックエンド側のページネーション(page/size)を利用」との記述のうち、ワークフロー6(アカウント管理画面群)については、frontend-adminのfunctional-spec.md該当ステップ(`GET /api/admin/accounts`)にpage/sizeパラメータの記載がなく、本Unit内の一次情報からは裏付けられない(監査ログ・テーブル一覧の2画面は該当ステップにpage/size明記あり)。account-management Unitのfunctional-spec.md/rules.md(BR2.1)を確認した限り、バックエンド側はpage/sizeを受け付けるため記述自体は事実として妥当だが、frontend-admin自身のfunctional-spec.mdにその利用が明記されていない点で、参照すべき一次ソースとの対応関係が本Unit内で閉じていない。 | frontend-adminのfunctional-spec.mdワークフロー6のステップに、`GET /api/admin/accounts`がpage/size/sortを受け付ける旨を追記するか、performance-requirements.md側でaccount-management契約(#17)への参照を明記する。 | New |

### Validation Tool Results

本ステージに指定された自動検証ツールはなし。上記はfunctional-spec.md・requirements.md・(スポットチェックとして)account-management Unitのfunctional-spec.md/rules.mdとの突き合わせによる手動検証。

### Summary

NFR-AUTHZ.1/NFR-AUTHZ.2はfunctional-spec.mdの「管理者ゲーティング」節と文言・対象画面リストとも正確に一致しており、NFR-INJECTION.1も本UnitがdangerouslySetInnerHTML等を用いない設計であることと整合している。tech-stack-decisions.mdもrequirements.md NFR4・team.md Code Styleと矛盾しない。traceability.jsonのNFR3の判定根拠に論理的な誤りがある(R-01、Major)ほか、performance-requirements.mdの一部記述が本Unit内の一次情報のみでは裏付けられない(R-02、Minor)が、いずれもCriticalではなくMajorも1件のみのため、実装を進める上でのブロッカーとはならない。
