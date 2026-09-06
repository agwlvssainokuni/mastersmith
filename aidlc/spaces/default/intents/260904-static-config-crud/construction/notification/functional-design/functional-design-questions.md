# Functional Design Questions: notification

notification(U7)は、unit-of-work.mdの定義どおり、アカウント作成通知・アカウント登録完了通知・アカウント情報変更通知・パスワード変更通知・パスワード忘れ対応・メールアドレス変更リクエストの6種のメールフロー[FR6.4]を、自作Mustacheエンジン`java-mustache-processor`によるHTMLテンプレート描画[FR6.5]で実現する。永続エンティティは持たず(domain-design/components.mdで「所有データ: なし」と明記)、auth・account-managementが発行するライフサイクルイベントをSpringのアプリケーション内イベント機構で購読する(直接の呼び出し依存を持たない)。購読する6種のイベント(契約#9〜#10で名称・payloadとも既に確定済み: `AccountCreatedEvent`〈account-management発行〉、`AccountRegistrationCompletedEvent`・`AccountInfoChangedEvent`・`PasswordChangedEvent`・`PasswordResetRequestedEvent`・`EmailChangeRequestedEvent`〈auth発行〉)とSMTP送信失敗時の挙動(ログ記録のみ、リトライなし)は、contract-summary.md側で既に確定しているため、本Unit固有の新規論点は以下1点のみ。

## Q1. AccountCreatedEventのSMTP送信失敗時、管理者による再送手段を今回のMVPスコープに含めるか

contract-summary.md「通知イベント契約(#9〜#10)」の失敗時挙動の節で、`PasswordResetRequestedEvent`・`EmailChangeRequestedEvent`のように利用者本人が再操作で再試行できるフローとは異なり、`AccountCreatedEvent`(管理者起点のアカウント作成通知)は利用者自身が再実行できないため、「必要に応じてFunctional Design以降で確定する再送手段(例: 管理画面からの通知再送操作)に委ねる」と明記されており、本Unitで初めて具体化すべき論点として引き継がれている。

再送手段を追加する場合、frontend-admin(U10、アカウント管理画面13a)に「通知再送」操作を追加する必要があるが、frontend-adminは既にfunctional-designを完了済み(READY)であり、追加するには別途そのUnitの再オープンが必要になる。

- A. 今回のMVPスコープには含めない。SMTP送信失敗時はログ記録のみとし(contract-summary.md記載どおり)、管理者はアカウント管理画面13a(既存の編集機能)から、対象アカウントの状態を確認したうえで必要な対応(例: 手動でのパスワード再設定案内等の運用回避)を行う前提とする。再送UIの追加は将来のスコープ拡張として持ち越す
- B. 今回のMVPスコープに含める。frontend-admin(アカウント管理画面13a)に「通知再送」ボタンを追加し、既存のAccountCreatedEventを同一payload(新しいregistrationTokenの再発行を伴う)で再発行するAPIを新設する
- X. Other (please specify)

[Answer]: A。今回のMVPスコープには含めない。SMTP送信失敗時はログ記録のみとし、frontend-adminの再オープンは行わない。再送UIは将来のスコープ拡張として持ち越す。

## Consolidated Summary Confirmation

notification Unitのfunctional-design成果物を以下の内容で確定します。

**entities.md**: domain-design/components.mdの定義どおり永続エンティティを持たない。イベント受信からメール送信までの間で扱う一時的なデータ形状(EmailDispatch: 宛先・テンプレート識別子・payload)のみを記述する。

**rules.md**: 以下の業務ルールを定義する(BR1.x)。
- BR1.1〜BR1.6: 6種のイベント(AccountCreatedEvent、AccountRegistrationCompletedEvent、AccountInfoChangedEvent、PasswordChangedEvent、PasswordResetRequestedEvent、EmailChangeRequestedEvent)それぞれについて、購読契機・使用テンプレート・宛先(recipientEmail or newEmail)・埋め込みURLの有無を規定する。
- BR2.1: メールテンプレートはjava-mustache-processorによるHTML形式とし、`<title>`要素をSubjectとする[FR6.5]。
- BR3.1: SMTP送信失敗時はログに記録し、リトライは行わない(contract-summary.md「通知イベント契約」の失敗時挙動節どおり)。

**functional-spec.md**: 「1. イベント受信からメール送信までの共通フロー」(購読→テンプレート解決→Mustache描画→SMTP送信→失敗時ログ記録)と、6種のイベントそれぞれの宛先・テンプレート対応表を定義する。

**traceability.json**: upstream_ids = FR6.4, FR6.5(unit-of-work-story-map.mdのU7行と一致)。

[Answer]: Looks correct
