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
**Date:** 2026-09-07T14:59:23Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | construction/auth/nfr-design/security-design.md > リフレッシュトークン | 本節は「検証成功のたびに新しいリフレッシュトークンを発行し、古いトークンをrevoked=trueにする(ローテーション、BR3.2)」というリフレッシュトークン失効条件のみを記述している。しかしcontract-summary.md 契約#4(account-management → auth)は「無効化(disable)の場合、authは該当accountIdの有効な(revoked=falseかつ未期限切れの)RefreshTokenをすべて失効させる(即時のセッション無効化)」と明記し、「auth側のrules.md/functional-spec.mdへの反映はauth Unit側のstage完了ゲートで対応する」と本Unitでの反映を名指しで求めている。この事項は既にconstruction/auth/functional-design/functional-spec.mdの`## Review`(R-03、Major、Status: New)およびconstruction/auth/nfr-requirements/security-requirements.mdの`## Review`(R-01、Major、Status: New)で指摘済みだが、rules.mdへの反映は依然未完了であり(rules.md BR3.1〜BR3.3を確認したが無効化契機の一括失効BRは存在しない)、本security-design.mdもこの既知の未解消ギャップに一切触れていない。このままではアカウント無効化後も既存RefreshTokenで最大7日間セッションが継続できてしまうという確認済みのセキュリティギャップが、NFR設計成果物からも見えなくなっている。 | rules.mdにR-03の反映(無効化時のRefreshToken一括失効BR)が完了次第、本節に「アカウント無効化(契約#4)時は対象accountIdの有効なRefreshTokenをすべてrevoked=trueにする」という失効条件を追記する。反映が完了するまでは、本節に既知の未解消事項として明記する。 | New |
| R-02 | Major | construction/auth/nfr-design/traceability.json > NFR-AUTHN.2のcoverageエントリ | traceability.jsonはNFR-AUTHN.2を`"status": "OK"`、target「security-design.md(リフレッシュトークンハッシュ永続化・ローテーション)」とだけ記載しており、R-01で指摘した契約#4由来の既知の未解消ギャップ(無効化時の即時失効未反映)に一切触れていない。上流のnfr-requirements/security-requirements.mdは同じギャップを自身の`## Review`セクションで明示的に記録していたが、本ステージのtraceability.json・security-design.mdのいずれにもその既知ギャップへの言及がなく、上流で行われていた可視化が本ステージで失われている。 | traceability.jsonのNFR-AUTHN.2エントリのtargetに、契約#4の無効化時RefreshToken失効が未反映である旨を注記するか、statusを"OK"から"Partial"等に変更し、rules.md反映後にOKへ更新する運用とする。 | New |

### Validation Tool Results

本ステージ定義にvalidation toolの指定はなく、実行していない。

### Summary

logical-components.mdが定義する3コンポーネント(RESTコントローラ・サービス・リポジトリ)以外への参照は7ファイルのいずれにも存在せず、コンポーネント境界は健全である。Argon2・JWT(HS256、15分)・リフレッシュトークン(7日、ローテーション)・ログイン試行制限(n=5/m=300秒、ロック判定をArgon2照合より先に実施というBR4.2の記述)・AccountActionToken(単回使用、24時間)・isAdminクレーム発行のみという設計は、いずれもnfr-requirements/rules.mdの対応するBR・NFR記述と正確に一致し、7ファイル間の矛盾も見つからなかった。契約#5〜#8の監査ログイベントエンベロープ・所有権例外の記述もcontract-summary.mdと整合している。traceability.jsonのNFR9 Deferred判定も上流のnfr-requirements/traceability.jsonと整合しており、隠蔽ではなく正確な記録である。一方で、contract-summary.md 契約#4が明示的にauth Unit側での反映を求めているリフレッシュトークン即時失効(アカウント無効化時)について、functional-design・nfr-requirementsの両stageで既にMajor指摘(R-03、R-01)として記録されていたにもかかわらず、本nfr-designステージのsecurity-design.md・traceability.jsonのいずれもこの既知ギャップへの言及を欠いており、上流で行われていた可視化がここで失われている(R-01、R-02)。件数はMajor 2件でREADY判定のしきい値(Major 2件以下)の範囲内であり、既存の数値・挙動記述自体に事実誤認や新規の契約違反はないため READY とするが、上流で指摘済みの契約#4未反映事項がここでも可視化されないまま次工程(infrastructure-design等)へ進むと、このセキュリティギャップの追跡が一層困難になる点は速やかに解消すべきである。
