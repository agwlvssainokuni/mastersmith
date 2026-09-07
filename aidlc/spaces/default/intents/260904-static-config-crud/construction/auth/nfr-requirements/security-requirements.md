# Security Requirements: auth

本Unitはセキュリティ機能そのものが中核であり、requirements.md NFR7を直接担う。

## NFR7.1: パスワードハッシュ化

パスワードはArgon2(Spring SecurityのArgon2PasswordEncoder、デフォルトパラメータ)でハッシュ化して保存する(rules.md BR5.1)。平文・可逆暗号化での保存は行わない。ソルトはライブラリが1ハッシュごとに自動生成しハッシュ文字列へ埋め込むため、別途のペッパー等の秘密鍵管理は行わない。

## NFR-AUTHN.1: アクセストークン(JWT)

ログイン成功時(BR1.1)、HS256署名のJWTアクセストークンを発行する(BR2.2)。claimsはsub(accountId)・isAdmin・roles・標準クレーム(iat, exp)から構成し、有効期限は15分とする。署名鍵はソースコード・Gitリポジトリにコミットせず、環境変数または権限600のローカル設定ファイルで保管する(project.md Forbidden)。

## NFR-AUTHN.2: リフレッシュトークン

アクセストークンと同時にリフレッシュトークンを発行する(BR2.3)。実トークン値はランダムかつ十分なエントロピーを持つ値とし、そのハッシュ値のみを永続化する(平文は保存しない)。有効期限は7日間とする。検証成功のたびに新しいリフレッシュトークンを発行し、古いトークンをrevoked=trueにする(リフレッシュトークンローテーション、BR3.2)ことで、トークン漏洩時の再利用窓を最小化する。

## NFR-AUTHN.3: ログイン試行回数制限

ログイン失敗のたびにconsecutiveFailureCountを加算し、既定n=5回に達するとlockedUntil = 現在時刻 + 既定m=300秒を設定する(BR4.1)。ロック中はパスワード照合自体を行わず403を返す(BR4.2、タイミング攻撃・総当たり攻撃の緩和)。n・mはapplication.ymlで設定変更可能とする。

## NFR-AUTHN.4: 自己サービス用トークン(AccountActionToken)

forgot-password・complete-registration・email-change等で用いるAccountActionTokenは単回使用(consumedAt設定で無効化、BR6.2)とし、有効期限は既定24時間とする(BR6.1)。同一accountId・同一purposeの未消費トークンは新規発行時に無効化する(BR6.1)。

## NFR-AUTHZ.1: isAdminクレームの範囲

isAdminクレームはアクセストークン発行時の判定材料として付与するのみであり、管理系機能へのアクセス制御そのものは各消費Unit(config-management、schema-ingestion、permission管理系、account-management、audit-log)がそれぞれ自身の画面・APIでローカルに判定する(BR7.1、ADR-002、横断的関心事)。本Unit自身はisAdminクレームの発行の正確性にのみ責任を持つ。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T13:37:03Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | construction/auth/nfr-requirements/security-requirements.md > NFR-AUTHN.2(リフレッシュトークン) | NFR-AUTHN.2はリフレッシュトークンの失効条件として「検証成功のたび(ローテーション、BR3.2)」のみを記載している。しかしcontract-summary.md 契約#4(account-management → auth)は「無効化(disable)時、authは該当accountIdの有効なRefreshTokenをすべて即時失効させる」ことを要求しており、これは同Unitのfunctional-design終了ゲートのレビュー(construction/auth/functional-design/functional-spec.md `## Review` R-03、Major、Status: New)で既に「rules.md・functional-spec.mdに未反映」と指摘済みの未解消事項である。rules.mdを確認したが該当BRは依然として存在せず(BR3.3はログアウト時のみを扱う)、この状態でNFR-AUTHN.2を確定させると、アカウント無効化後も最大7日間リフレッシュトークンによるセッション継続が可能という既知のセキュリティギャップがNFR成果物からも見えなくなる。 | rules.mdにR-03の反映(無効化時のRefreshToken一括失効BR)が完了してから、NFR-AUTHN.2にその失効条件(アカウント無効化時の即時失効)を追記する。反映が完了するまでは、traceability.jsonまたは本ファイルに既知の未解消事項として明記する。 | New |
| R-02 | Major | construction/auth/nfr-requirements/observability-requirements.md > NFR3.1(監査ログイベント) | NFR3.1はactionTypeをLOGIN_SUCCESS・LOGIN_FAILED・LOGIN_LOCKED・SELF_SERVICE_PROFILE_CHANGEDの4種のみとしている。しかしcontract-summary.md 契約#4は、account-management経由でAccount.name/emailが変更された場合もauthがAccountInfoChangedEvent(通知イベント契約#10)を発行することを要求しており、これも同Unitのfunctional-design終了ゲートのレビュー(R-04、Major、Status: New)で「rules.md・functional-spec.mdに未反映」と指摘済みの未解消事項である。この経路(管理者による氏名・メールアドレス編集)による監査ログイベントが、rules.md BR8.1にもfunctional-spec.mdのワークフローにも存在せず、NFR3.1のactionType一覧からも欠落しており、運用者が管理者操作による変更を事後追跡できない可観測性ギャップが未記録のまま残っている。 | rules.mdにR-04の反映(管理者経由のname/email変更時のBR8.1拡張)が完了してから、NFR3.1のactionType一覧・説明を更新する。反映が完了するまでは、traceability.jsonまたは本ファイルに既知の未解消事項として明記する。 | New |

### Validation Tool Results

本ステージ定義にvalidation toolの指定はなく、実行していない。

### Summary

7ファイルの数値・挙動(Argon2、JWT15分、リフレッシュトークン7日・ローテーション、ログイン試行制限n=5/m=300秒、AccountActionToken単回使用・24時間)はrules.md BR1.1〜BR8.1と正確に一致しており、ファイル間の矛盾も見つからなかった。traceability.jsonのNFR9 Deferred判定も、account-management Unitのfunctional-design-questions.md Q3で「実装はauth Unit側のstage完了ゲートでの繰延べ事項」と確認済みの内容と整合しており、隠蔽ではなく正確な記録である。一方で、同じfunctional-design終了ゲートで指摘済みの未解消Major事項(R-03: 無効化時のRefreshToken即時失効、R-04: 管理者経由のname/email変更時のAccountInfoChangedEvent発行)がrules.mdに反映されないまま、本ステージのsecurity-requirements.md・observability-requirements.mdからも触れられずに素通りしている。件数はMajor 2件でREADY判定のしきい値(Major 2件以下)の範囲内であり、既存のNFR記述自体に事実誤認はないため READY とするが、上流のR-03・R-04が未解消である事実そのものが、これら2件の観点でNFR成果物の完全性を損なっている点は次工程着手前に解消すべきである。
