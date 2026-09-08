# Security Design: auth

## パスワードハッシュ化

サービスコンポーネントはSpring SecurityのArgon2PasswordEncoder(デフォルトパラメータ)でパスワードをハッシュ化する(BR5.1、NFR7.1)。平文・可逆暗号化は行わない。ソルトはライブラリがハッシュごとに自動生成する。

## アクセストークン(JWT)

ログイン成功時、サービスコンポーネントがHS256署名のJWTアクセストークンを発行する(BR2.2)。claimsはsub(accountId)・isAdmin・roles・標準クレーム(iat, exp)、有効期限15分。署名鍵(HMAC秘密鍵)は環境変数経由で外部化し、ソースコード・リポジトリにコミットしない(project.md Forbidden)。

## リフレッシュトークン

アクセストークンと同時にリフレッシュトークンを発行する(BR2.3)。実トークン値はランダムかつ十分なエントロピーを持つ値とし、そのハッシュ値のみをRefreshTokenテーブルへ永続化する(平文非保存)。有効期限7日間。検証成功のたびに新しいリフレッシュトークンを発行し、古いトークンをrevoked=trueにする(リフレッシュトークンローテーション、BR3.2)。

## ログイン試行回数制限

Account.consecutiveFailureCountとlockedUntilで実装する(BR4.1〜BR4.3)。既定n=5回・m=300秒はapplication.ymlで設定変更可能とする。ロック判定はパスワード照合(Argon2)より先に行い、ロック中はArgon2照合自体を実行しない(BR4.2、計算コストの無駄な消費を避けると同時にタイミング攻撃を緩和)。

## 自己サービス用トークン(AccountActionToken)

forgot-password・complete-registration・email-change等で用いるAccountActionTokenは単回使用(consumedAt設定で無効化)とし、有効期限は既定24時間とする(BR6.1〜BR6.2)。同一accountId・同一purposeの未消費トークンは新規発行時に無効化する。

## isAdminクレームの範囲

isAdminクレームはアクセストークン発行時の判定材料として付与するのみであり、管理系機能へのアクセス制御そのものは各消費Unitがそれぞれ自身の画面・APIでローカルに判定する(BR7.1、ADR-002)。auth自身はisAdminクレームの発行の正確性にのみ責任を持つ。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-08T13:30:42Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | security-design.md 全体 | 契約#4(account-management→auth)経由でAccountがstatus=disabledに変更された際、既発行のリフレッシュトークンが即座には失効しない既知のギャップが、本ドキュメントに明記されていない。functional-spec.mdのBR1.1・BR1.2(ログイン時のstatus=disabled判定)は新規ログインをブロックするのみで、既存のリフレッシュトークンのローテーション処理(BR3.2)にstatus再判定のロジックがなく、無効化後もアクセストークンの再発行が継続し得る。この既知ギャップはauth Unit自身のfunctional-design・nfr-requirements両段階で既にMajorとして記録済みであり、繰延べ事項として扱う。 | security-design.mdの「リフレッシュトークン」節に、この既知の未対応ギャップと、対応方針(将来のリフレッシュ時status再検証の追加、またはアカウント無効化時の一括revoke処理の追加)をリスクとして明記する。実装判断はcode-generation段階以降で確定してよいが、設計文書に不可視のまま残さない。 | Unresolved (Accepted as deferred / non-blocking) |
| R-02 | Major | traceability.json > NFR-AUTHN.2行 | traceability.jsonのNFR-AUTHN.2カバレッジ記述(リフレッシュトークンハッシュ永続化・ローテーション)が、R-01と同じ既知ギャップ(アカウント無効化時の即時失効未反映)への言及を欠いており、トレーサビリティ上もこのリスクが不可視になっている。 | traceability.jsonのNFR-AUTHN.2エントリのtargetに、当該ギャップが繰延べ事項として認識済みである旨の注記を追加する。 | Unresolved (Accepted as deferred / non-blocking) |

### Validation Tool Results

本レビューサイクルでは、他Unit(dynamic-data-access)のnfr-designステージ内1ファイルの表記フォーマット不具合(Findingsテーブル記法崩れ)を修正するためのstage-level Request Changesにより、auth Unitを含む全11Unitのper-unit reviewステータスがエンジンの状態管理上リセットされたことに伴う再検証である。auth Unit自身の7成果物ファイル(performance-design.md、security-design.md、scalability-design.md、reliability-design.md、observability-design.md、logical-components.md、traceability.json)はいずれも前回iteration 1のREADY判定時点から内容変更なし(git履歴上も単一コミットのみで、以降の変更なし)であることを確認した。

| Tool | Result | Interpretation |
|---|---|---|
| 上流整合性確認(手動) | PASS | nfr-requirements配下の全NFR(NFR1.1〜NFR3.2、NFR-AUTHN.1〜4、NFR-AUTHZ.1、NFR-FAILSAFE.1〜2、NFR9)がtraceability.jsonでOK/Deferredのいずれかにマッピングされ、functional-design/rules.md(BR1.1〜BR8.1)・functional-spec.mdとの整合が取れている |
| logical-components.md参照整合性(手動) | PASS | performance/security/scalability/reliability/observability-design.mdのいずれも、logical-components.mdで定義されたRESTコントローラ・サービス・リポジトリ以外のコンポーネントへの参照はない |
| traceability.json網羅性(手動) | PASS | upstream_idsとcoverage配列の項目数・ID一致を確認、抜け漏れなし |
| NFR9 Deferred判定(手動) | PASS | 初期管理者アカウント自動作成の繰延べは、functional-designステージ終了ゲートで既に確認済みとtraceability.jsonに明記されており、本ステージでの再設計対象外という判定は妥当 |
| 契約#4整合性(手動、R-01根拠) | 既知ギャップ確認 | account-management→auth契約#4(contract-summary.md L48-52)にはアカウント無効化時のリフレッシュトークン即時失効に関する取り決めがなく、rules.md BR1.1/BR1.2・BR3.2にも該当ロジックがないことを確認。前回iteration 1の指摘内容と一致し、新規指摘ではなく既知の繰延べ事項として扱う |

### Summary

auth Unitのnfr-design成果物7ファイルは前回iteration 1のREADY判定時点から内容の変更がなく、上流のnfr-requirements・functional-design(rules.md/functional-spec.md)・契約サマリとの整合、logical-components.mdへの参照の妥当性、traceability.jsonの網羅性、NFR9のDeferred判定のいずれも再検証の結果問題は見つからなかった。既知のMajor指摘2件(契約#4由来のアカウント無効化時リフレッシュトークン即時失効ギャップの不可視化)は前回から継続する繰延べ事項であり、新規指摘として重複計上していない。今回のレビューはdynamic-data-access Unitの表記不具合修正に伴うstage-levelリセットに起因する再確認であり、auth Unit自身の設計変更は一切ない。よってREADY(Major 2件、非ブロッキング)を維持する。
