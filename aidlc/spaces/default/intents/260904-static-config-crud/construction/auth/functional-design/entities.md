# Domain Entities: auth

domain-design/components.mdのAccountComponentが持つエンティティ(Account、AccountActionToken)を出発点とし、
本Unit(auth)の責務([FR5.5、FR5.6、FR6.1〜FR6.3、FR6.6、NFR7]、および契約#11・#19・#20)に必要な属性・新規エンティティを追加する。

## エンティティ一覧(機械可読)

```yaml
entities:
  - name: Account
    identifier: accountId
    attributes:
      - name: string
      - email: string
      - passwordHash: string # Argon2による不可逆ハッシュ。complete-registration未実行の間はnull(ログイン不可) [NFR7, functional-design-questions.md Q4]
      - status: enum(active, disabled) # disabledは論理削除相当。値そのものは契約#19で固定済み [FR6.4.4]
      - isAdmin: boolean # アクセストークンisAdminクレームの発行元データ [FR5.5, FR5.6]
      - consecutiveFailureCount: integer # 連続ログイン失敗回数(auth固有の追加属性) [FR6.6]
      - lockedUntil: datetime, nullable # このタイムスタンプを過ぎるまでログイン禁止(auth固有の追加属性) [FR6.6]
    ownership: >
      auth(U5)が永続化スキーマの唯一の所有者(契約#19)。account-management(U6)はAccountテーブルへ
      直接アクセスせず、契約#4(account-management → auth)のリポジトリ/サービスインタフェース経由でのみ
      name/email/status/isAdminを参照・更新する。passwordHash/consecutiveFailureCount/lockedUntilの
      3列は契約#4のインタフェースに含まれず、auth自身のログイン処理内でのみ読み書きする。

  - name: AccountActionToken
    identifier: tokenId
    attributes:
      - accountId: identifier # Accountへの参照。IDのみ保持し外部キー制約は設けない(データ参照であり機能呼び出しではない)
      - purpose: enum(registration_completion, password_forgot, email_change)
      - createdAt: datetime
      - expiresAt: datetime # application.yml設定値、既定24時間 [functional-design-questions.md Q2]
      - consumedAt: datetime, nullable # 使用済みマーカー。設定済みなら再利用不可(単回使用) [functional-design-questions.md Q2]
      - pendingNewEmail: string, nullable # purpose=email_changeの場合のみ使用。確定操作までの新アドレスを保持する
    ownership: >
      auth(U5)が唯一の所有者・利用者。account-managementはこのテーブルを参照しない。
      ただしpurpose=registration_completionの実トークン値は、account-managementからの契約#4呼び出し
      (アカウント新規作成)への戻り値として、発行のたびに1回だけaccount-managementへ渡される
      (account-managementはこの値をAccountCreatedEvent(契約#9)のpayloadへ転記するのみで、
      テーブル自体へはアクセスしない。追記: 契約#4、auth Unit Functional Designより)。

  - name: RefreshToken
    identifier: tokenId
    attributes:
      - accountId: identifier # Accountへの参照。IDのみ
      - tokenHash: string # 実トークン値のハッシュ。平文は保存しない
      - issuedAt: datetime
      - expiresAt: datetime # 7日間 [contract-summary.md 共通規約「トークン有効期限」]
      - revoked: boolean # ログアウト、またはリフレッシュ時のローテーションにより失効する [functional-design-questions.md Q3]
    ownership: auth(U5)が唯一の所有者・利用者(新規エンティティ)。

note: >
  アクセストークン(JWT)自体はステートレスであり、いかなるエンティティとしても永続化しない。
  そのclaims構成(sub, isAdmin, roles[], iat, exp)はfunctional-spec.mdで記述する。
```

## エンティティサマリー

| エンティティ | 所有Unit | 識別子 | 主な属性 | 備考 |
|---|---|---|---|---|
| Account | auth(共有スキーマ、契約#19) | accountId | name, email, passwordHash, status, isAdmin, consecutiveFailureCount, lockedUntil | account-managementは契約#4経由でのみアクセス |
| AccountActionToken | auth | tokenId | accountId, purpose, expiresAt, consumedAt, pendingNewEmail | 自己サービス系トークン(登録完了・パスワード忘れ・メール変更) |
| RefreshToken | auth(新規) | tokenId | accountId, tokenHash, expiresAt, revoked | ローテーション方式(functional-design-questions.md Q3) |
