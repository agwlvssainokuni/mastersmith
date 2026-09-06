# Domain Entities: notification

notificationは、domain-design/components.mdの定義どおり永続エンティティを持たない(NotificationComponentの所有データは「なし」と明記されている)。auth・account-managementが発行するライフサイクルイベントを購読し、そのpayloadをそのままメール送信に用いる一時的な処理であり、本Unit自身がデータベースへ書き込む状態は存在しない。

## 取り扱うデータ形状(機械可読)

```yaml
data_shapes:
  - name: EmailDispatch
    description: >
      購読したイベント1件から導出される、1通のメール送信リクエストの内部表現。永続化はされず、
      イベント受信からSMTP送信までの処理内でのみ存在する一時的な値である。
    attributes:
      - recipientEmail   # 契約#9〜#10の各イベントpayloadのrecipientEmailまたはnewEmail(EmailChangeRequestedEventのみ)
      - templateId        # BR1.1〜BR1.6が定めるイベント種別→テンプレート識別子の対応(rules.md参照)
      - templateVariables # イベントpayloadのうち、テンプレート描画に必要な変数(accountId、URLに埋め込むトークン等)
      - subject           # テンプレートの<title>要素から抽出する(BR2.1)

  - name: SubscribedEvent
    description: >
      auth・account-managementがSpringのアプリケーション内イベント機構で発行し、notificationが
      購読する6種のイベント(契約#9〜#10で名称・payloadとも確定済み)。本Unit自身は発行しない。
    attributes: [eventType, payload]
    allowed_values:
      eventType:
        - AccountCreatedEvent            # publisher: account-management
        - AccountRegistrationCompletedEvent # publisher: auth
        - AccountInfoChangedEvent        # publisher: auth
        - PasswordChangedEvent           # publisher: auth
        - PasswordResetRequestedEvent    # publisher: auth
        - EmailChangeRequestedEvent      # publisher: auth

note: >
  SMTP送信失敗時(接続不可・拒否等)は、EmailDispatchの内容をログに記録した時点で処理を終了し、
  リトライは行わない(BR3.1)。イベント自体はこの時点で失われるため、送信失敗の永続的な記録
  (再送キュー等)は本Unitの責務外である(functional-design-questions.md Q1で、管理者による
  再送手段は今回のMVPスコープに含めないことを確認済み)。
```

## データ形状サマリー

| データ形状 | 用途 | 備考 |
|---|---|---|
| EmailDispatch | イベント1件から導出する送信リクエスト | 永続化なし。SMTP送信後は破棄される |
| SubscribedEvent | auth・account-managementから購読する6種のイベント | 契約#9〜#10で名称・payloadとも確定済み。notificationは発行しない |
