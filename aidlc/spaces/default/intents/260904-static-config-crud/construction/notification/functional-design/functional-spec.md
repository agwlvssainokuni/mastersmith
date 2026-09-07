# Functional Specification: notification

notificationは、domain-design/components.mdの定義どおり、auth・account-managementが発行するアカウントライフサイクルイベントをSpringのアプリケーション内イベント機構で購読し、対応するHTMLメールを送信する。auth・account-managementへの直接の呼び出し依存は持たず(unit-of-work.md実装メモ)、REST APIによる外部からの呼び出しも受けない。永続エンティティを持たないため、entities.md/rules.mdと合わせて本ファイルが単独で完結する仕様とはならず、rules.md(BR1.1〜BR3.1)で定義した判断ロジックを、以下のワークフローとしてどの順序で適用するかを規定する。

## 障害時の挙動(横断的関心事)

6種のワークフローすべてに共通する障害時の挙動を先に定義する。SMTP送信に失敗した場合(接続不可・拒否等)、EmailDispatchの内容をログに記録した時点で処理を終了し、リトライは行わない(BR3.1)。イベント自体はこの時点で失われるため、`PasswordResetRequestedEvent`(ワークフロー5)・`EmailChangeRequestedEvent`(ワークフロー6)のように利用者本人が再度操作を起点にできるフローは、利用者による再実行に委ねる。`AccountCreatedEvent`(ワークフロー1、管理者起点)の再送手段は、今回のMVPスコープには含めない(functional-design-questions.md Q1)。管理者はアカウント管理画面(frontend-admin 13a)から対象アカウントの状態を確認し、必要な運用回避を行う前提とする。

## ワークフロー

### 1〜6共通: イベント受信からメール送信までの流れ

1. Springのアプリケーション内イベント機構が、auth・account-managementが発行したイベントをnotificationへ配送する。
2. イベント種別(payloadのクラス)に応じて、以下の対応表からtemplateId・宛先・埋め込みURLの有無を決定する(BR1.1〜BR1.6)。
3. テンプレート変数(payloadの内容、埋め込みURLの場合はトークンを含む完全なURL)を用いて、java-mustache-processorでHTMLテンプレートを描画する(BR2.1)。描画結果の`<title>`要素をSubjectとして採用する。
4. SMTP経由でメールを送信する。失敗した場合は「障害時の挙動」節の規定に従う。

| # | イベント(publisher) | 宛先 | テンプレート(templateId) | 埋め込みURL | 対応FR |
|---|---|---|---|---|---|
| 1 | AccountCreatedEvent(account-management) | payload.recipientEmail | account-created | 登録完了URL(registrationToken) | FR6.4(1) |
| 2 | AccountRegistrationCompletedEvent(auth) | payload.recipientEmail | account-registration-completed | なし | FR6.4(2) |
| 3 | AccountInfoChangedEvent(auth) | payload.recipientEmail | account-info-changed | なし(changedFieldsをテンプレート変数として表示) | FR6.4(3) |
| 4 | PasswordChangedEvent(auth) | payload.recipientEmail | password-changed | なし | FR6.4(4) |
| 5 | PasswordResetRequestedEvent(auth) | payload.recipientEmail | password-reset-requested | パスワード再設定URL(resetToken) | FR6.4(5) |
| 6 | EmailChangeRequestedEvent(auth) | payload.newEmail(他5件のrecipientEmailとは異なる) | email-change-requested | 変更確定URL(changeToken) | FR6.4(6) |

上記の各行がBR1.1〜BR1.6にそれぞれ対応する。

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-07T02:25:04Z
**Iteration:** 1

### Findings

指摘なし(既知の繰延べ事項R-01を除く)

### Summary

entities.md・rules.md・functional-spec.md・traceability.jsonの4ファイルは相互に整合している。BR1.1〜BR1.6が定義する6種のイベント(AccountCreatedEvent、AccountRegistrationCompletedEvent、AccountInfoChangedEvent、PasswordChangedEvent、PasswordResetRequestedEvent、EmailChangeRequestedEvent)の名称・payload・publisher・宛先(recipientEmail/newEmail)は、contract-summary.md「通知イベント契約(#9〜#10)」の記載と完全に一致し、BR2.1(Mustacheテンプレート・`<title>`→Subject)はFR6.5と、BR1.1〜BR1.6の6フローはFR6.4(1)〜(6)とそれぞれ一致する。SMTP送信失敗時の挙動(BR3.1)もcontract-summary.mdの「失敗時の挙動」節と一致し、AccountCreatedEventの管理者向け再送手段を今回のMVPスコープに含めない判断はfunctional-design-questions.md Q1で人間の承認を得て確定済みであり、矛盾はない。traceability.jsonのupstream_ids(FR6.4, FR6.5)およびcoverageの対応も正確。新規のCritical/Major欠陥は見つからなかった。
