# Functional Design Questions: auth

requirements.md・contract-summary.mdだけでは確定しきれない、auth固有の業務ルールを3点確定する。

## Q1. ログイン試行回数制限のパラメータ(FR6.6)

FR6.6は「連続n回ログインに失敗した場合、最後の失敗からm秒間ログインを禁止する」としており、具体的なn・mは後続段階で確定するとしている。audit-logの保持日数(functional-design-questions.md)と同様、固定値か設定値かも含めて確定する。

- A. 固定値とする。n=5回、m=300秒(5分)とする。管理者が変更できる設定項目にはしない(推奨: MVPの単純化。値の妥当性は運用しながら見直す)
- B. 設定値とする(管理者が変更可能)。具体的な既定値を教えてください
- X. Other (please specify)

[Answer]: X. application.yml(環境変数経由)で指定する値とする。内部H2の管理者設定にはしない。デフォルトはn=5回・m=300秒(A案の数値をデフォルト値として採用)。

## Q2. 自己サービス用トークン(AccountActionToken)の有効期限と単回使用

domain-design/components.mdのAccountActionToken(パスワード忘れ・アカウント登録完了・メールアドレス変更確認で使用)について、有効期限と使用後の扱いを確定する。

- A. 有効期限は24時間、使用後(パスワード再設定・登録完了・メールアドレス変更確認の完了時)は即座に無効化し再利用不可とする(単回使用)。期限切れ・使用済みトークンでのアクセスは400エラー(RFC 7807)とする(推奨)
- B. 異なる有効期限・再利用可否を採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A(ただし有効期限の24時間はapplication.ymlで設定可能な値とし、コード決め打ちにはしない。デフォルト24時間)

## Q3. リフレッシュトークンの実現方式(FR6.3)

FR6.3は「単純な固定有効期限の使い切り型を超える、より本格的な仕組みを持つこと」としている。具体的な仕組みを確定する。

- A. リフレッシュトークンをAccountに紐づけてハッシュ化して内部H2に永続化し、使用のたびに新しいリフレッシュトークンを発行して古いものを無効化する(リフレッシュトークンローテーション)。これにより、ログアウト時の明示的な無効化(失効)や、漏えいが疑われるトークンの無効化が可能になる(推奨: 「単純な使い切り型を超える」という要件文言に最も忠実)
- B. 異なる仕組みを採用する(具体的に教えてください)
- X. Other (please specify)

[Answer]: A

## Q4. パスワードハッシュアルゴリズム(NFR7)

NFR7は「不可逆ハッシュ(例: bcrypt、Argon2等。具体的なアルゴリズムは後続段階で確定)」としている。Java 25/Spring Bootスタックで確定する。

- A. bcrypt(Spring Securityの`BCryptPasswordEncoder`を使用。実績が豊富で追加依存なし)(推奨)
- B. Argon2(Spring Securityの`Argon2PasswordEncoder`を使用。より新しくGPU攻撃耐性が高いが、計算コストのチューニングが必要)
- X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

Q1〜Q4の回答を踏まえ、auth Unitのfunctional-design成果物を以下の内容で確定します。

**エンティティ(entities.md)**
- `Account`(共有スキーマ、契約#19、auth所有): 既存属性(name/email/passwordHash/status/isAdmin)に加え、ログインロック用の`consecutiveFailureCount`・`lockedUntil`を追加。account-managementは契約#4経由でのみアクセスし、passwordHash等3列には触れない。
- `AccountActionToken`(auth専有): purpose(registration_completion/password_forgot/email_change)、expiresAt(application.yml設定値、既定24時間)、consumedAt(単回使用マーカー)、pendingNewEmail(メール変更用)。
- `RefreshToken`(新規、auth専有): tokenHash(ハッシュ化永続化)、expiresAt(7日、契約サマリー既定)、revoked(ローテーション/ログアウトで失効)。

**業務ルール(rules.md)**: BR1.1(ログイン認証)、BR2.1〜BR2.3(契約#20経由のロール取得・アクセストークン発行・リフレッシュトークン発行)、BR3.1〜BR3.3(リフレッシュ検証・ローテーション・ログアウト時一括失効)、BR4.1〜BR4.3(ログイン失敗カウント・ロック。n=5回・m=300秒をapplication.yml既定値とする)、BR5.1(Argon2ハッシュ化)、BR6.1〜BR6.4(自己サービス系トークンの発行・検証・登録完了・メールアドレス変更確定)、BR7.1(isAdminクレーム発行、判定は各消費Unit)。

**ワークフロー(functional-spec.md)**: ログイン/リフレッシュ/ログアウト/アカウント登録完了/パスワード忘れ対応/自己サービス氏名・パスワード変更/自己サービスメールアドレス変更の7つ。各所でNotificationComponentへのイベント発行(FR6.4系)を明記。

**トレーサビリティ(traceability.json)**: upstream_ids = FR5.5, FR5.6, FR6.1〜FR6.3, FR6.6, NFR7。自己サービス系ルール(BR6.1〜BR6.4)はFR6.4系(notification/account-management所有)由来だが契約#11(auth API)によりauthが実装する旨をreverseで明記。

[Answer]: Looks correct

