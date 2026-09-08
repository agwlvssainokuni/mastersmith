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
**Date:** 2026-09-08T20:37:30Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | security-design.md 全体 | 契約#4(account-management→auth)経由でAccountがstatus=disabledに変更された際、既発行のリフレッシュトークンが即座には失効しない既知のギャップが、本ドキュメントに明記されていない。functional-spec.mdのBR1.1・BR1.2(ログイン時のstatus=disabled判定)は新規ログインをブロックするのみで、既存のリフレッシュトークンのローテーション処理(BR3.2)にstatus再判定のロジックがなく、無効化後もアクセストークンの再発行が継続し得る。この既知ギャップはauth Unit自身のfunctional-design・nfr-requirements両段階で既にMajorとして記録済みであり、繰延べ事項として扱う。 | security-design.mdの「リフレッシュトークン」節に、この既知の未対応ギャップと、対応方針(将来のリフレッシュ時status再検証の追加、またはアカウント無効化時の一括revoke処理の追加)をリスクとして明記する。実装判断はcode-generation段階以降で確定してよいが、設計文書に不可視のまま残さない。 | Accepted risk |
| R-02 | Major | traceability.json > NFR-AUTHN.2行 | traceability.jsonのNFR-AUTHN.2カバレッジ記述(リフレッシュトークンハッシュ永続化・ローテーション)が、R-01と同じ既知ギャップ(アカウント無効化時の即時失効未反映)への言及を欠いており、トレーサビリティ上もこのリスクが不可視になっている。 | traceability.jsonのNFR-AUTHN.2エントリのtargetに、当該ギャップが繰延べ事項として認識済みである旨の注記を追加する。 | Accepted risk |

### Validation Tool Results

本イテレーションはR-01・R-02のStatus値の表記修正のみを目的とし、security-design.md・performance-design.md・scalability-design.md・reliability-design.md・observability-design.md・logical-components.md・traceability.jsonの本文はすべて前回レビュー時点から変更されていないことを確認した(diffなし)。上流のnfr-requirements/security-requirements.md(R-01・R-02、Status: New)、functional-design/rules.md(BR1.1・BR1.2・BR3.2)を再照合し、R-01・R-02の指摘内容が上流記録と矛盾しないことを確認した。ツール実行結果に変化はない。

### Summary

今回の再レビューはStatus値の表記修正(不正な自由記述文からフレームワーク正規値`Accepted risk`への訂正)のみが目的であり、指摘内容・対応方針・本文はすべて前回と同一である。既知のMajor 2件(R-01・R-02)はいずれも上流段階で既に記録済みの繰延べ事項であり、Acceptedとして扱ってREADY判定を維持する。
