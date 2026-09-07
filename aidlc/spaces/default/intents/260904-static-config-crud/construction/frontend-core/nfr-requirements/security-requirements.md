# Security Requirements: frontend-core

## NFR-DATA.1: recordIdの不透明な取り扱い

recordIdは一覧画面で受け取った不透明な文字列をそのまま使い、クライアント側でデコード・解釈は行わない(ワークフロー4手順1)。recordIdの内部構造(Base64エンコードされたJSONオブジェクト)はサーバ側の実装詳細であり、dynamic-data-access BR2.1のサイドチャネル対策(検証失敗理由を404に統一する設計)をクライアント側の実装でも尊重する。

## NFR-DATA.2: アカウント存在有無の非開示

パスワード忘れ申請(ワークフロー7a)は、対象アドレス宛にAccountActionToken付きメールが送信されたかどうかに関わらず、同一の完了メッセージ(「メールを確認してください」)を表示する(auth BR6.2)。応答からアカウントの存在有無を区別できるようにしない。

## NFR-AUTHZ.1: X-Active-Roleによるロール切替の境界

複数ロール保有時のロール切替(ワークフロー8)はクライアント側で完結し、サーバー呼び出しを伴わない。選択したroleIdは以後のリクエストでX-Active-Roleヘッダーとして送信されるが、実際の認可判定はサーバー側(dynamic-data-access BR4.1、config-management BR4.3)で行われる。クライアント側のロール切替UI自体は認可境界ではなく、X-Active-Roleヘッダーの値を偽装しても、サーバー側でアクセストークンのrolesクレームに含まれるかの検証を通過しない限り許可されない。

## NFR-INJECTION.1: 表示内容のエスケープ

業務データ(利用者が入力したカラム値等)を画面に描画する際は、React/TSXの標準エスケープ機構に従う。dangerouslySetInnerHTML等、標準エスケープを迂回する手段は用いない。

## NFR-AUTHN.1(既知の繰延べ事項・Major): トークンリフレッシュ時のローテーション追従漏れ

ワークフロー1手順6(トークンのリフレッシュ)は、`POST /api/auth/refresh`成功時に新しいaccessTokenのみをクライアント側で差し替えるとのみ記述されており、auth Unitのリフレッシュトークンローテーション(BR3.2、リフレッシュ成功のたびに新しいrefreshTokenを発行し古いものをrevoked=trueにする)への追従が明記されていない。このままでは2回目以降のアクセストークン更新時に、既にrevoked済みのrefreshTokenで`POST /api/auth/refresh`を呼び出すことになり、本来7日間有効なはずのセッションが早期に強制ログアウトになる。これはfrontend-core Unitのfunctional-designステージ終了ゲートで既にMajor(R-07)として記録済みの未解消事項であり、本nfr-requirementsステージではこのギャップを隠蔽せず、本ドキュメントに明示的な繰延べ事項として記録する。修正(リフレッシュレスポンスに含まれる新しいrefreshTokenでクライアント側保持値を置き換える処理の追記)は本ステージのスコープ外とし、code-generation段階以降で対応する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T14:19:29Z
**Iteration:** 1

### Findings

(指摘なし)

### Validation Tool Results

本ステージに機械的な検証ツールの指定はないため、artifacts間の照合を手動で実施した。

| 確認項目 | 結果 |
|---|---|
| security-requirements.md NFR-AUTHN.1 と functional-spec.md `## Review` R-07(重大度・状態・技術内容)の一致 | 一致(誇張・過小評価なし) |
| security-requirements.md NFR-DATA.1 と functional-spec.md ワークフロー4手順1(recordIdの不透明な取り扱い)の一致 | 一致 |
| security-requirements.md NFR-DATA.2 と functional-spec.md ワークフロー7a手順2(アカウント存在有無の非開示)の一致 | 一致 |
| security-requirements.md NFR-AUTHZ.1 と functional-spec.md ワークフロー8(X-Active-Roleによるロール切替)の一致 | 一致 |
| performance-requirements.md NFR1.1 と requirements.md NFR1 の一致 | 一致 |
| tech-stack-decisions.md と requirements.md NFR4 の一致 | 一致 |
| traceability.json upstream_ids(NFR1〜NFR9)とcoverageの過不足 | 過不足なし(9件すべて記載) |
| traceability.json NFR3のN/A判定(姉妹Unit frontend-adminのMajor指摘を踏まえた判定見直し)の妥当性 | 妥当。NFR3(Twelve-Factor準拠・OpenTelemetry計装・構造化ログ)はバックエンド側の可観測性の関心事であり、認可エラー時の画面表示切替(NFR-AUTHZ.2相当)とは別の関心事である。target欄でその区別を明示的に述べており、frontend-adminレビューの誤った判定根拠を踏襲していないことを確認した |
| 4ファイル間の矛盾 | なし |

### Summary

security-requirements.md・performance-requirements.md・tech-stack-decisions.md・traceability.jsonの4ファイルは、functional-spec.md(該当ワークフローおよびR-07)・requirements.md(NFR1・NFR3・NFR4)の記述と正確に整合しており、既知の繰延べ事項(R-07/NFR-AUTHN.1)も誇張・過小評価なく記録されている。traceability.jsonのNFR3 N/A判定は、姉妹Unitの誤った判定根拠を排除した論理的に妥当な見直しであり、4ファイル間の矛盾も検出されなかった。READYと判定する。
