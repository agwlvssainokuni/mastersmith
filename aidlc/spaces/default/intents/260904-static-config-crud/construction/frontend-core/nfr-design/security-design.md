# Security Design: frontend-core

## recordIdの不透明な取り扱い

画面コンポーネントはrecordIdを一覧画面で受け取った不透明な文字列としてそのまま扱い、デコード・解釈は行わない(NFR-DATA.1)。詳細・編集画面への遷移はAPIクライアント経由でrecordId文字列をそのままパラメータとして渡す。

## アカウント存在有無の非開示

パスワード忘れ申請画面は、APIクライアントの応答(202)を受けて、対象アドレス宛にメールが送信されたかどうかに関わらず同一の完了メッセージを表示する(NFR-DATA.2)。応答からアカウントの存在有無を区別できるようにしない。

## X-Active-Roleによるロール切替の境界

ロール切替画面コンポーネントは選択結果をAPIクライアントの内部状態として保持するのみで、サーバー呼び出しを伴わない(NFR-AUTHZ.1)。APIクライアントは以後のリクエストでX-Active-Roleヘッダーとして送信するが、実際の認可判定はバックエンド側で行われる。

## 表示内容のエスケープ

業務データを画面コンポーネントに描画する際は、React/TSXの標準エスケープ機構に従う(NFR-INJECTION.1)。dangerouslySetInnerHTML等は用いない。

## 既知の繰延べ事項(Major、NFR-AUTHN.1): トークンリフレッシュ時のローテーション追従漏れ

APIクライアントコンポーネントが行う`POST /api/auth/refresh`成功時の処理は、新しいaccessTokenのみを差し替えるとのみ設計されており、authのリフレッシュトークンローテーション(新しいrefreshTokenの発行、古いものの失効)への追従が未定義である。このままでは2回目以降のアクセストークン更新時に、既にrevoked済みのrefreshTokenで`POST /api/auth/refresh`を呼び出すことになり、本来7日間有効なはずのセッションが早期に強制ログアウトになる。これはfrontend-core Unitのfunctional-design・nfr-requirementsの両段階で既にMajorとして記録済みの未解消事項であり、本nfr-designステージではこのギャップを隠蔽せず、明示的に記録する。トークン管理の責務はAPIクライアントコンポーネントに帰属するため、修正時の実装帰属先はここに定まる。修正(リフレッシュレスポンスに含まれる新しいrefreshTokenでクライアント側保持値を置き換える処理の追記)は本ステージのスコープ外とし、code-generation段階以降で対応する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T15:46:52Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージに指定された自動検証ツールはなし。performance-design.md/security-design.md/logical-components.md/traceability.jsonの4ファイル相互、およびnfr-requirements配下の上流4ファイル(performance-requirements.md, security-requirements.md, tech-stack-decisions.md, traceability.json)、functional-spec.md `## Review` R-07、nfr-design-questions.mdとの突き合わせによる手動検証。

| 確認項目 | 結果 |
|---|---|
| security-design.md「既知の繰延べ事項(Major、NFR-AUTHN.1)」とfunctional-spec.md R-07(重大度・技術内容・帰結・スコープ外扱い)の一致 | 一致(誇張・過小評価なし)。`POST /api/auth/refresh`成功時にaccessTokenのみ差し替える設計、リフレッシュトークンローテーション未追従、2回目以降のアクセストークン更新で早期強制ログアウトに至る帰結、修正はcode-generation段階以降というスコープ外扱いまで、R-07およびnfr-requirements/security-requirements.md NFR-AUTHN.1の記述と技術的に整合している |
| security-design.md NFR-AUTHN.1節と、nfr-requirements/security-requirements.md NFR-AUTHN.1節の一致 | 一致。「トークン管理の責務はAPIクライアントコンポーネントに帰属する」という実装帰属先の明記は、上流にはない付加情報だが、上流の記述と矛盾せず、nfr-designステージの責務(論理コンポーネントへの割り当て)に沿った具体化である |
| logical-components.mdで定義された3コンポーネント(画面コンポーネント・共通UIコンポーネント・APIクライアント)以外への参照が他ファイルに存在するか | 存在しない。security-design.md・performance-design.mdはいずれも「画面コンポーネント」「APIクライアント(コンポーネント)」のみを参照している |
| traceability.jsonのNFR3のN/A判定記録(frontend-admin Unitレビュー指摘R-01を踏まえた改善の有効性) | 有効に機能している。upstream_idsにNFR3を明示的に含め、coverageでstatus: "N/A"とし、対象欄に判定理由(UI側では構造化ログ・OpenTelemetry計装を扱わない)と、姉妹Unit frontend-adminのnfr-requirements段階レビューでの指摘(認可エラー時の画面表示切替を可観測性の根拠にすることは論理的誤り)への参照を明記している。加えて、frontend-core自身のnfr-requirements/traceability.jsonでは既にnfr-requirements段階でNFR3=N/A判定済み(同ステージのレビューもREADY・指摘なし)であるため、frontend-adminのケース(上流に未解消のMajorが残存)とは異なり「継続」である旨を明記しており、状況の違いを正確に反映した記録になっている |
| nfr-design-questions.md Consolidated Summary Confirmation(traceability.json節)と実ファイルの一致 | 一致。「NFR3は…N/Aとする(既にnfr-requirements段階でN/A判定済み、継続)」という承認内容どおりの記録になっている |
| 4ファイル間の矛盾 | なし |

### Summary

security-design.md・performance-design.md・logical-components.md・traceability.jsonの4ファイルは、nfr-requirements配下の対応要件およびfunctional-spec.md R-07の記述と正確に整合しており、既知の繰延べ事項(NFR-AUTHN.1)も誇張・過小評価なく記録されている。frontend-admin Unitレビューで指摘されたtraceability.jsonのNFR3無言省略という問題は、本Unitでは発生しておらず、N/A判定・理由・姉妹Unit指摘への参照のいずれも明示的に記録されている点を確認した。指摘事項なし、READYと判定する。
