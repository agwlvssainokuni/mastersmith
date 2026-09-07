# Security Design: frontend-admin

## 管理者ゲーティング

AppShellレベルのルートガードで、isAdminクレームの有無に応じてサイドナビ項目・ルーティングを出し分ける(NFR-AUTHZ.1)。isAdminクレームを持たない利用者には、frontend-adminの画面への導線自体を表示しない。

## 認可判定のバックエンド一元化

frontend-admin自身は認可判定ロジックを持たない。URLを直接指定してアクセスした場合は、画面コンポーネントの初回データ取得APIが返す403に応じて、APIクライアントが共通のエラー表示状態へ切り替える(NFR-AUTHZ.2)。isAdminクレームの発行元・署名検証はバックエンド側の責務であり、frontend-admin側でのクレーム偽装対策は行わない。

## 表示内容のエスケープ

業務DB由来の識別子文字列(テーブル名・カラム名・論理表示名等)や利用者入力値(ロール名・グループ名等)を画面コンポーネントに描画する際は、React/TSXの標準エスケープ機構に従う(NFR-INJECTION.1)。dangerouslySetInnerHTML等、標準エスケープを迂回する手段は用いない。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T15:21:29Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | construction/frontend-admin/nfr-design/traceability.json > upstream_ids / coverage | traceability.jsonはupstream_idsからNFR3を完全に省略しており、coverage配列にもNFR3のエントリが存在しない。しかしnfr-design-questions.mdのConsolidated Summary Confirmation(ユーザー承認済み)は「NFR3は…本UnitではN/Aとして判定し直す(nfr-requirements/traceability.jsonのOK判定は上流の既存Major指摘として残存するが、nfr-design段階では正確な判定を採用する)」と明記しており、NFR3をupstream_idsに含めたうえでstatus: "N/A"として明示的に記録し、かつ上流(nfr-requirements/traceability.json)に残る未解消のMajor指摘(R-01, Status: New — NFR-AUTHZ.2を可観測性の根拠にする判定を「論理的誤り」と指摘)への参照を残すことを約束している。実際のtraceability.jsonはこの約束と異なり、NFR3を無言で除外しているため、(a)承認済みConsolidated Summary Confirmationとの不一致、(b)上流nfr-requirements/traceability.jsonに残存する未解消Major指摘が本stageの成果物からは一切見えなくなる、という2点の問題がある。第三者がnfr-design/traceability.jsonのみを読んだ場合、NFR3が意図的にN/A判定されたのか、単なる記載漏れなのか区別できない。 | traceability.jsonのupstream_idsにNFR3を追加し、coverageに `{ "id": "NFR3", "status": "N/A", "target": "…UI側では構造化ログ・OpenTelemetry計装を扱わないためN/A。nfr-requirements/traceability.jsonのNFR3=OK判定は未解消のMajor指摘(R-01, Status: New)が残存している旨を明記" }` の形でエントリを追加し、nfr-design-questions.mdの承認内容と実ファイルを一致させる。 | New |

### Validation Tool Results

本ステージに指定された自動検証ツールはなし。performance-design.md/security-design.md/logical-components.md/traceability.jsonの4ファイル相互、およびnfr-requirements配下の上流4ファイル(performance-requirements.md, security-requirements.md, tech-stack-decisions.md, traceability.json)、functional-spec.md「管理者ゲーティング」節、nfr-design-questions.mdとの突き合わせによる手動検証。

### Summary

security-design.md/performance-design.md/logical-components.mdの記述はnfr-requirements配下の対応要件(NFR1.1・NFR1.2・NFR-AUTHZ.1・NFR-AUTHZ.2・NFR-INJECTION.1)およびfunctional-spec.md「管理者ゲーティング」節と文言・対象画面とも正確に一致し、logical-components.mdで定義された3コンポーネント(画面コンポーネント・共通UIコンポーネント・APIクライアント)以外への参照も他ファイルに存在しない。4ファイル間の矛盾もない。唯一の指摘(R-01, Major)は、姉妹Unit(frontend-core)のレビュー指摘を踏まえてNFR3をN/A扱いに修正するという判断自体は妥当である一方、その修正がtraceability.json上で明示的な記録(status: N/A + 上流の未解消Major指摘への参照)としてではなく、単純な省略という形で行われており、承認済みのnfr-design-questions.mdの記載と実際の成果物が食い違っている点にある。Majorが1件のみでCriticalは無いため、READY判定とするが、次回反映時にtraceability.jsonの記載を承認内容と一致させることを求める。
