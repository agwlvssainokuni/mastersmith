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
**Date:** 2026-09-06T22:40:53Z
**Iteration:** 1

### 検証結果

1. **契約#9〜#10の6イベントカバレッジ**: rules.md BR1.1〜BR1.6は、AccountCreatedEvent・AccountRegistrationCompletedEvent・AccountInfoChangedEvent・PasswordChangedEvent・PasswordResetRequestedEvent・EmailChangeRequestedEventの6件を過不足なくカバーしている。EmailChangeRequestedEventの宛先がpayload.newEmail(他5件のrecipientEmailとは異なる)である点は、BR1.6・functional-spec.mdのワークフロー対応表(#6行)の両方で明示的に反映されている。問題なし。

2. **payload解釈の整合性**: account-management側のBR1.4(`construction/account-management/functional-design/rules.md`)は、AccountCreatedEventのpayloadを`accountId・recipientEmail・registrationToken`と定義しており、これはcontract-summary.md「通知イベント契約(#9〜#10)」のAccountCreatedEventスキーマ、およびnotification側BR1.1の解釈(宛先=recipientEmail、本文にregistrationTokenを埋め込んだURL)と完全に一致する。矛盾なし。

3. **functional-spec.mdの対応表とrules.mdの一致**: ワークフロー対応表の6行(templateId・宛先・埋め込みURLの有無)は、BR1.1〜BR1.6の各`logic`フィールドと一致している。問題なし。

4. **entities.mdの「永続エンティティなし」との整合性**: EmailDispatch・SubscribedEventはいずれも「永続化されず処理内でのみ存在する一時的な値」と明記されており、domain-design/components.mdの「所有データ: なし」という設計方針と矛盾しない。問題なし。

5. **BR3.1と契約#9〜#10の失敗時挙動の整合性**: BR3.1(SMTP送信失敗時はログ記録のみ・リトライなし)は、contract-summary.md「通知イベント契約(#9〜#10)」の「失敗時の挙動」節と文言レベルで整合している。AccountCreatedEventの再送手段をFunctional Design以降で確定する旨が契約側にあるが、これはfunctional-design-questions.md Q1で人間が明示的にMVPスコープ外と承認済みであり、指摘不要という指示に従う。

6. **traceability.jsonのcoverage/reverse**: FR6.4→BR1.1〜1.6、FR6.5→BR2.1のcoverageは要件文言(6種のメールフロー、Mustacheテンプレート+`<title>`→Subject)と一致している。reverseのBR3.1(N/A、契約起源でFR個別化されていない)も妥当な扱い。

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | entities.md「取り扱うデータ形状」EmailDispatch.templateVariables と functional-spec.mdワークフロー手順3 | entities.mdはtemplateVariablesを「URLに埋め込むトークン等」(トークン素の値)と記述する一方、functional-spec.mdは手順3で「埋め込みURLの場合はトークンを含む完全なURL」をテンプレート変数として用いると記述しており、URL構築(ベースURL+トークン)がどちらの層の責務か、ベースURLの出所(application.yml等)がどちらの文書からも読み取れない。実装者がURL組み立てロジックの置き場所を推測する必要がある。 | entities.mdまたはfunctional-spec.mdのいずれかに統一し、テンプレート変数として渡すのが生トークンか完成済みURLかを明記する。完成済みURLとする場合、ベースURLの設定源(application.yml等)を一言追記する。 | New |

### Summary

契約#9〜#10の6イベントすべてがrules.md・functional-spec.mdで過不足なく一貫してカバーされており、account-management側BR1.4とのpayload解釈にも矛盾はない。永続エンティティなしの設計方針、BR3.1の失敗時挙動、traceabilityの整合性もいずれも問題なし。Minor指摘1件(テンプレート変数がトークンか完成URLかの記述不一致)のみで、実装を妨げるものではないためREADYとする。
