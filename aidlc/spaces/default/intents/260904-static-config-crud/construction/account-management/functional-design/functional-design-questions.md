# Functional Design Questions: account-management

requirements.md・contract-summary.mdだけでは確定しきれない、account-management固有の業務ルールを2点確定する。

## Q1. アカウント無効化(FR6.4.4)時の既存セッションの扱い

FR6.4.4は「無効化されたアカウントはログインできなくなること」としているが、無効化の時点で既に発行済みのアクセストークン・リフレッシュトークンをどう扱うかは未確定である。

- A. アカウント無効化時、auth(契約#4経由の追加操作)へ「当該accountIdの有効なリフレッシュトークンをすべて失効させる」ことを依頼する。アクセストークンはステートレスなJWTのため即時失効はできないが、有効期限が15分と短いため実用上のリスクは限定的とする(推奨: 無効化操作の実効性を高める)
- B. リフレッシュトークンの失効は行わず、次回のアクセストークン期限切れ時のリフレッシュ試行が失敗する(通常のトークン有効期限切れに委ねる)ことのみで対応する
- X. Other (please specify)

[Answer]: A

## Q2. 管理者によるメールアドレス編集(FR6.4.3)が自己サービスの確認フローを経由するか

FR6.4.3は「既存の利用者アカウント情報(氏名・メールアドレス・割り当てロール等)を編集できる」としている。auth Unitの自己サービスでのメールアドレス変更(`/api/me/email-change-request`→`/api/me/email-change-confirm`)は新アドレス宛の確認メールと現在パスワードの入力を要するが、管理者による編集がこの確認フローを経由するかを確定する。

- A. 管理者編集は確認フローを経由せず、直接Account.emailを上書きする(契約#4のupdate呼び出しで氏名と同様に扱う)。管理者はisAdminによる高い権限を持つため、利用者本人の確認を介さない直接変更を許容する(推奨: 管理者操作の単純さを優先)
- B. 管理者によるメールアドレス変更も、自己サービスと同様の確認フローを経由させる
- X. Other (please specify)

[Answer]: A

## Q3. 初期管理者アカウントのブートストラップ(Summary Confirmation中にユーザーが提起)

account-managementのアカウント作成フロー(BR1.1〜BR1.5)は管理者権限(isAdmin)を前提とするため、システム稼働開始時点で最初の管理者アカウントをどう用意するかが未規定だった。ユーザーの提起により、application.yml(環境変数)経由でのブートストラップ方針を確定する。

- A. 起動時、isAdminクレームを持つAccountが1件も存在しない場合、application.ymlで指定されたメールアドレス・パスワードを用いて初期管理者アカウントを自動作成する(auth Unitの起動時処理として実装、passwordHashは作成時点でArgon2ハッシュ化済み、complete-registration不要で即時ログイン可能)。パスワードの同期は初回作成時のみとし、以後application.ymlの値を変更しても既存アカウントには反映しない(通常のアカウントとして自己サービスに委ねる)(採用)
- B. 異なるブートストラップ方式を採用する
- X. Other (please specify)

[Answer]: A(この決定はrequirements.md NFR9として追記済み。実装はauth Unitの責務であり、account-management自身のワークフローには含まれない。auth Unitのfunctional-designは既にREADY判定・凍結済みのため、auth側のrules.md/functional-spec.md/traceability.jsonへの反映は、functional-designステージ完了ゲートで他の繰延べ事項(トークン失効、AccountInfoChangedEvent一元化)とあわせて対応する)

## Consolidated Summary Confirmation

Q1〜Q2の回答、および機能設計中に発見したアーキテクチャ上のギャップ(初期ロール割り当て・割り当てロール変更が実際にはpermission Unitへの書き込みを要すること)への対応を踏まえ、account-management Unitのfunctional-design成果物を以下の内容で確定します。

**アーキテクチャ上の対応(既に承認済み)**: 新契約#21(account-management → permission)を新設し、unit-of-work-dependency.mdにaccount-management→permissionの依存エッジを追加(循環なしを確認済み)。契約#4(account-management → auth)から「初期ロール割り当て」を削除し、authはAccountフィールドのみを扱う。また、契約#4に「無効化時のリフレッシュトークン即時失効」(Q1)と「name/email変更時のAccountInfoChangedEvent発行はauthが一元的に行う」を追記(auth側artifactへの反映はauth Unitのstage完了ゲートで対応する、繰延べ事項として記録済み)。加えて、Q3(初期管理者アカウントのブートストラップ)をrequirements.md NFR9として新規追記し、unit-of-work.mdのauth責務にも反映した(実装自体もauth Unit側のstage完了ゲートでの繰延べ事項)。

**エンティティ(entities.md)**: account-managementは永続エンティティを持たない(dynamic-data-accessと同様)。契約#4・#21から合成するAccountView(DTO)のみを文書化。

**業務ルール(rules.md)**: BR1.1〜BR1.5(作成: 必須項目検証→契約#4呼び出し→契約#21での初期ロール割り当て→AccountCreatedEvent発行→監査ログ)、BR2.1(一覧、ページネーション)、BR3.1〜BR3.2(編集: 契約#4/#21への振り分け、emailは直接上書き)、BR4.1〜BR4.2(無効化: 契約#4経由、authがトークン失効)、BR5.1(isAdminゲーティング)。

**ワークフロー(functional-spec.md)**: アカウントの新規作成/一覧参照/編集/無効化、の4つ。

**トレーサビリティ(traceability.json)**: upstream_ids = FR6.4.1〜FR6.4.4。

[Answer]: Looks correct

