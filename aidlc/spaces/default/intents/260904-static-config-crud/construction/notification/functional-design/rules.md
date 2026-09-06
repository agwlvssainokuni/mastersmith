# Business Rules: notification

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: AccountCreatedEventを受信すると、アカウント作成通知テンプレートを用いてメールを送信する。宛先はpayloadのrecipientEmail、本文にはpayloadのregistrationTokenを埋め込んだ登録完了URLを含める
    category: business
    applies_to: EmailDispatch
    trigger: "account-managementがAccountCreatedEvent(契約#9)を発行したとき"
    logic: "IF AccountCreatedEventを受信 THEN templateId=account-created を用いてEmailDispatchを生成し送信する(FR6.4(1))"
    violation_behaviour: "該当なし(送信失敗時の挙動はBR3.1に従う)"
    source: FR6.4

  - id: BR1.2
    statement: AccountRegistrationCompletedEventを受信すると、アカウント登録完了通知テンプレートを用いてメールを送信する。宛先はpayloadのrecipientEmail
    category: business
    applies_to: EmailDispatch
    trigger: "authがAccountRegistrationCompletedEvent(契約#10)を発行したとき"
    logic: "IF AccountRegistrationCompletedEventを受信 THEN templateId=account-registration-completed を用いてEmailDispatchを生成し送信する(FR6.4(2))"
    violation_behaviour: "該当なし(送信失敗時の挙動はBR3.1に従う)"
    source: FR6.4

  - id: BR1.3
    statement: AccountInfoChangedEventを受信すると、アカウント情報変更通知テンプレートを用いてメールを送信する。宛先はpayloadのrecipientEmail。changedFieldsの値(name/password/email)はテンプレート変数として渡し、変更内容の表示に用いる
    category: business
    applies_to: EmailDispatch
    trigger: "authがAccountInfoChangedEvent(契約#10)を発行したとき"
    logic: "IF AccountInfoChangedEventを受信 THEN templateId=account-info-changed を用い、changedFieldsをテンプレート変数として渡したEmailDispatchを生成し送信する(FR6.4(3))"
    violation_behaviour: "該当なし(送信失敗時の挙動はBR3.1に従う)"
    source: FR6.4

  - id: BR1.4
    statement: PasswordChangedEventを受信すると、パスワード変更通知テンプレートを用いてメールを送信する。宛先はpayloadのrecipientEmail
    category: business
    applies_to: EmailDispatch
    trigger: "authがPasswordChangedEvent(契約#10)を発行したとき"
    logic: "IF PasswordChangedEventを受信 THEN templateId=password-changed を用いてEmailDispatchを生成し送信する(FR6.4(4))"
    violation_behaviour: "該当なし(送信失敗時の挙動はBR3.1に従う)"
    source: FR6.4

  - id: BR1.5
    statement: PasswordResetRequestedEventを受信すると、パスワード忘れ対応通知テンプレートを用いてメールを送信する。宛先はpayloadのrecipientEmail、本文にはpayloadのresetTokenを埋め込んだパスワード再設定URLを含める
    category: business
    applies_to: EmailDispatch
    trigger: "authがPasswordResetRequestedEvent(契約#10)を発行したとき"
    logic: "IF PasswordResetRequestedEventを受信 THEN templateId=password-reset-requested を用いてEmailDispatchを生成し送信する(FR6.4(5))"
    violation_behaviour: "該当なし(送信失敗時の挙動はBR3.1に従う)"
    source: FR6.4

  - id: BR1.6
    statement: EmailChangeRequestedEventを受信すると、メールアドレス変更リクエスト通知テンプレートを用いてメールを送信する。宛先は変更先の新アドレス(payloadのnewEmail、他の5イベントのrecipientEmailとは異なる)。本文にはpayloadのchangeTokenを埋め込んだ変更確定URLを含める
    category: business
    applies_to: EmailDispatch
    trigger: "authがEmailChangeRequestedEvent(契約#10)を発行したとき"
    logic: "IF EmailChangeRequestedEventを受信 THEN templateId=email-change-requested を用い、宛先をpayload.newEmailとしたEmailDispatchを生成し送信する(FR6.4(6))"
    violation_behaviour: "該当なし(送信失敗時の挙動はBR3.1に従う)"
    source: FR6.4

  - id: BR2.1
    statement: メール本文は自作Mustacheエンジンjava-mustache-processorによるHTML形式のテンプレートを用い、テンプレート内の<title>要素をメールのSubjectとする
    category: business
    applies_to: EmailDispatch
    trigger: "BR1.1〜BR1.6のいずれかによりEmailDispatchが生成されたとき"
    logic: "IF EmailDispatchを送信する THEN 対応するtemplateIdのHTMLテンプレートをテンプレート変数で描画し、描画結果の<title>要素の内容をSubjectとして採用する"
    violation_behaviour: "該当なし(テンプレート自体の欠落・構文エラーはコード生成段階の実装上の異常系として扱う)"
    source: FR6.5

  - id: BR3.1
    statement: SMTP送信失敗(接続不可・拒否等)はログに記録し、リトライは行わない
    category: policy
    applies_to: EmailDispatch
    trigger: "BR1.1〜BR1.6のいずれかで生成したEmailDispatchのSMTP送信が失敗したとき"
    logic: "IF SMTP送信が失敗する THEN EmailDispatchの内容(宛先・templateId、payloadに含まれる機微情報を除く)をログに記録し、以降の再送は行わない。イベント自体はこの時点で失われる"
    violation_behaviour: "該当なし(利用者本人が再操作で再試行できるフロー〈BR1.5、BR1.6〉は利用者の再実行に委ねる。BR1.1〈管理者起点〉の再送手段は今回のMVPスコープに含めない〈functional-design-questions.md Q1〉)"
    source: contract-summary.md「通知イベント契約(#9〜#10)」失敗時の挙動節
```

## ルールサマリー

| ID | カテゴリ | 対象イベント | 概要 |
|---|---|---|---|
| BR1.1 | business | AccountCreatedEvent | アカウント作成通知(登録完了URL付き) |
| BR1.2 | business | AccountRegistrationCompletedEvent | アカウント登録完了通知 |
| BR1.3 | business | AccountInfoChangedEvent | アカウント情報変更通知 |
| BR1.4 | business | PasswordChangedEvent | パスワード変更通知 |
| BR1.5 | business | PasswordResetRequestedEvent | パスワード忘れ対応通知(再設定URL付き) |
| BR1.6 | business | EmailChangeRequestedEvent | メールアドレス変更リクエスト通知(新アドレス宛、変更確定URL付き) |
| BR2.1 | business | (全イベント共通) | Mustacheテンプレート描画、`<title>`をSubjectに採用 |
| BR3.1 | policy | (全イベント共通) | SMTP送信失敗時はログ記録のみ、リトライなし |
