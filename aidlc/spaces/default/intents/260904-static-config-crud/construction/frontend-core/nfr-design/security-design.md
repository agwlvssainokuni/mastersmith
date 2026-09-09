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
**Date:** 2026-09-08T22:49:48Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージ(nfr-design、frontend-core Unit)に機械的な検証ツールの指定はないため、artifacts間の照合を手動で実施した。

| 確認項目 | 結果 |
|---|---|
| performance-design.md / security-design.md / logical-components.md / traceability.jsonが、frontend-core Unitのnfr-design成果物として規定された4件と一致するか(kind: ui) | 一致(過不足なし) |
| performance-design.md NFR1.1・NFR1.2 と nfr-requirements/performance-requirements.md NFR1.1・NFR1.2 の一致 | 一致(応答速度の一般方針・バックエンドページネーション利用ともに矛盾なし) |
| security-design.md NFR-DATA.1・NFR-DATA.2・NFR-AUTHZ.1・NFR-INJECTION.1 と nfr-requirements/security-requirements.md 該当項目の一致 | 一致 |
| security-design.md NFR-AUTHN.1(既知の繰延べ事項)と nfr-requirements/security-requirements.md NFR-AUTHN.1・functional-spec.md `## Review` R-07 の重大度(Major)・技術内容・「修正はcode-generation段階以降」というスコープ外扱いの一致 | 一致(誇張・過小評価なし) |
| logical-components.mdで定義された3コンポーネント(画面コンポーネント・共通UIコンポーネント・APIクライアント)以外への参照がないか(performance-design.md・security-design.mdを走査) | 該当なし。両ファイルとも「画面コンポーネント」「APIクライアント」の範囲内で記述されている |
| traceability.json upstream_ids(NFR1.1, NFR1.2, NFR-DATA.1, NFR-DATA.2, NFR-AUTHZ.1, NFR-INJECTION.1, NFR-AUTHN.1, NFR3)とcoverageの過不足 | 過不足なし(8件すべて記載、targetもすべて非空) |
| traceability.json NFR3のN/A判定が、nfr-requirements/traceability.jsonのNFR3(N/A、frontend-admin Unitレビュー指摘を踏まえた見直し済み)判定を正しく継続しているか | 継続を確認。判定根拠の混同(認可エラー時の画面表示切替を可観測性の根拠にする誤り)を再導入していない |
| 4ファイル間の矛盾、および前回READY判定時点からの内容変更の有無 | 矛盾なし。内容は前回READY判定時と同一であることを確認した |

### Summary

frontend-core Unitのnfr-design成果物4件(performance-design.md・security-design.md・logical-components.md・traceability.json)は、上流のnfr-requirements成果物およびfunctional-spec.md R-07と正確に整合しており、logical-components.mdで定義された3コンポーネント以外への参照もない。既知の繰延べ事項NFR-AUTHN.1も誇張・過小評価なく記録されている。今回は内容が前回READY判定時から一切変更されていないことを確認したうえでの独立した再検証であり、結論も同じくREADYである。
