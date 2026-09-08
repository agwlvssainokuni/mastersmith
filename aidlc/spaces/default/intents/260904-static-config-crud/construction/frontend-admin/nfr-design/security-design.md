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
**Date:** 2026-09-08T13:44:06Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | nfr-design/logical-components.md > コンポーネント構成 | security-design.mdの「管理者ゲーティング」節は「AppShellレベルのルートガード」と記述しているが、nfr-design/logical-components.mdの論理コンポーネント表(画面コンポーネント・共通UIコンポーネント・APIクライアントの3件)にはAppShellが独立した項目として明示されていない。AppShell自体はfunctional-design/frontend-components.mdで「全画面共通のシェル」として定義済みの実在コンポーネントであり参照先は妥当だが、nfr-design側の論理コンポーネント表がそれを「画面コンポーネント」に含めているのか独立要素として扱っているのかが本Unit成果物内で明記されていない。 | logical-components.mdの表または注記に、AppShellが「画面コンポーネント」に含まれる旨(または独立コンポーネントである旨)を一言明記する。 | New |

前回iteration 1のREADY判定時のMajor指摘R-01(traceability.jsonがNFR3をupstream_ids/coverageから完全に省略しており、Consolidated Summary Confirmationの内容と食い違っていた点)は、今回の修正により解消を確認した。nfr-design/traceability.jsonのupstream_idsおよびcoverageにNFR3が明示的に追加され、status: "N/A"として、認可エラー時の画面表示切替(NFR-AUTHZ.2)を可観測性の根拠にするのは論理的誤りである旨の判定理由と、上流nfr-requirements/traceability.jsonのNFR3=OK判定に未解消のMajor指摘(status: New)が残っている旨への言及が記載されている。上流nfr-requirements/security-requirements.mdのReviewセクションで実際にR-01がStatus: "New"のまま残存していることも確認済みであり、記述内容は事実と一致している。

### Validation Tool Results

本ステージに指定された自動検証ツールはなし。上記はnfr-requirements配下の要件ファイル(performance-requirements.md/security-requirements.md/traceability.json)、functional-spec.md「管理者ゲーティング」節、frontend-components.mdとの手動突き合わせによる検証。

### Summary

前回iteration 1のMajor指摘(NFR3の無言省略)は今回の修正で解消されており、内容も上流の未解消Major指摘の存在を正確に反映している。NFR-AUTHZ.1/NFR-AUTHZ.2/NFR-INJECTION.1の各設計はfunctional-spec.md「管理者ゲーティング」節・security-requirements.mdの記述と整合し、performance-design.mdもperformance-requirements.mdと整合している。AppShellへの参照がlogical-components.mdの表で明示されていない点(R-01、Minor)を除き、実装を妨げる論点はない。
