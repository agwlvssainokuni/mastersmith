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
**Date:** 2026-09-08T22:43:13Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | logical-components.md > コンポーネント構成 | security-design.md「管理者ゲーティング」節はAppShellレベルのルートガードに言及しているが、logical-components.mdのコンポーネント表(画面コンポーネント/共通UIコンポーネント/APIクライアントの3件)にAppShell自体が明示的な行として記載されていない。ルートガードの実装主体がどの論理コンポーネントに属するのか、表からは一意に読み取れない。 | logical-components.mdの表にAppShell(またはルートガードの帰属先)を明示するか、既存いずれかのコンポーネントの責務説明にAppShell/ルートガードを含む旨を追記する。 | Unresolved |

### Validation Tool Results

本ステージに指定された自動検証ツールはなし。手動検証: (1) traceability.jsonのNFR3判定が前回修正(N/A判定への変更、nfr-requirements側の未解消Major R-01への言及)のまま維持されていることを確認。(2) nfr-requirements/security-requirements.md・performance-requirements.mdの内容(NFR-AUTHZ.1/NFR-AUTHZ.2/NFR-INJECTION.1/NFR1.1/NFR1.2)とperformance-design.md/security-design.mdの記述を突き合わせ、文言・対象画面・エラー処理方針とも整合していることを確認。(3) functional-spec.md「管理者ゲーティング」節(FR5.5・FR5.6)とsecurity-design.mdの記述(AppShellルートガード、403応答時のエラー表示切替、isAdminクレームの発行元・検証はauth Unit/バックエンド側の責務)が矛盾しないことを確認。(4) 4成果物内でlogical-components.mdに定義された3コンポーネント(画面コンポーネント・共通UIコンポーネント・APIクライアント)以外への参照がないことを確認(AppShellはlogical-components.mdの表に明示されていないためR-01として再記録)。

### Summary

今回のリクエストは内容の変更を伴わない再レビュー依頼であり、前回イテレーションで修正済みのMajor指摘(traceability.jsonのNFR3無言省略)は維持されている。既知の繰延べMinor指摘(R-01、AppShellがlogical-components.mdの表に明示されていない)は非ブロッキング事項として同一内容で再記録した。上流のnfr-requirements/functional-spec.mdとの整合性、および3論理コンポーネント以外への参照がないことも改めて確認しており、Critical/新規Major指摘はない。
