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
**Date:** 2026-09-08T13:50:52Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージに機械的な検証ツールの指定はないため、artifacts間の照合を手動で実施した。

| 確認項目 | 結果 |
|---|---|
| performance-design.md NFR1.1/NFR1.2 と nfr-requirements/performance-requirements.md の一致 | 一致(応答速度の一般方針、バックエンドページネーション利用とも表現・内容が対応) |
| security-design.md NFR-DATA.1/NFR-DATA.2/NFR-AUTHZ.1/NFR-INJECTION.1 と nfr-requirements/security-requirements.md の一致 | 一致(recordId不透明化、アカウント存在有無非開示、X-Active-Role境界、React標準エスケープのいずれも技術内容が対応) |
| security-design.md NFR-AUTHN.1(既知の繰延べ事項)と nfr-requirements/security-requirements.md NFR-AUTHN.1、functional-spec.md `## Review` R-07 の一致 | 一致。重大度(Major)・原因(リフレッシュトークンローテーション追従漏れ)・影響(2回目以降のアクセストークン更新で早期強制ログアウト)・責務帰属(APIクライアントコンポーネント)・対応時期(code-generation段階以降)のいずれも誇張・過小評価なく引き継がれている |
| logical-components.md で定義された3コンポーネント(画面コンポーネント/共通UIコンポーネント/APIクライアント)以外への参照の有無 | なし。performance-design.md・security-design.mdの記述はいずれも「画面コンポーネント」「APIクライアントコンポーネント」の範囲内で閉じている |
| traceability.json upstream_ids と nfr-requirements/traceability.json coverage(NFR1→NFR1.1/NFR1.2に分解された対象含む)の過不足 | 過不足なし。NFR-DATA.1/NFR-DATA.2/NFR-AUTHZ.1/NFR-INJECTION.1/NFR-AUTHN.1はsecurity-requirements.md側の見出しIDと一致し、NFR3のN/A判定もnfr-requirements/traceability.jsonの判定(frontend-adminレビュー指摘を踏まえた見直し)をそのまま継続していることを確認した |
| 4ファイル(performance-design.md/security-design.md/logical-components.md/traceability.json)間の矛盾 | なし |

### Summary

frontend-core Unitのnfr-design成果物4ファイルは、nfr-requirements段階の各要件文書およびfunctional-spec.mdのR-07と正確に整合しており、既知の繰延べ事項(NFR-AUTHN.1)も誇張・過小評価なく記録されている。logical-components.mdで定義された3コンポーネント以外への参照もなく、traceability.jsonのcoverageにも過不足はない。iteration 1でREADY判定を受けた内容から変更はなく、独立した検証でも同一の結論に至った。READYと判定する。
