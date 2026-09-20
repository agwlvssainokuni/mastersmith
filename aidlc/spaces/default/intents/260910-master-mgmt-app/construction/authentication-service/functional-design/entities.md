<!--
Copyright 2026 agwlvssainokuni

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Entities — authentication-service (U5)

`inception/domain-design/components.md`のAuthenticationServiceコンポーネント定義(`LoginAttempt`・`Session`の属性大枠)を、機能設計として具体化したものである。`User`と`Role`は、それぞれuser-management(U4)とpermission-engine(U3)が所有する。本ユニットは、それらを、不透明な識別子(`userId`・`roleId`)としてだけ参照する。

```yaml
entities:
  - name: Session
    description: >
      1回のログインに対して1つ発行される、認証の単位(端末ごとのログイン)。リフレッシュトークンの状態と、
      その端末で選択されたアクティブロールを保持する。同一ユーザーが複数のSessionを同時に持てる(FR3.2)。
      アクセストークン(JWT)は、userIdとsessionIdだけを運び(Q4=A)、アクティブロールはこのエンティティが持つ。
      リフレッシュトークンの平文は保持せず、ハッシュだけを保持する。
    attributes:
      - name: sessionId
        type: string
        required: true
        unique: true
        description: 推測不能なランダムな識別子(主キー)。アクセストークンの`sid`クレームに入る
      - name: userId
        type: string
        required: true
        references: User
        description: ログインしたユーザー(user-managementが所有するUserの不透明な参照)
      - name: activeRoleId
        type: string
        required: false
        references: Role
        description: >
          このSessionで選択されているアクティブロール(permission-engineが所有するRoleの不透明な参照)。
          ロールが1つだけのユーザーではログイン時に自動選択される。ロールが0個、または2個以上でまだ選択していない間は
          null(未選択)。未選択のまま操作したときは、認証エラー(401)ではなく権限なし(403)として扱う(BR5.12)
      - name: refreshTokenHash
        type: string
        required: true
        unique: true
        description: 現在有効なリフレッシュトークンのハッシュ。平文は保持しない(BR5.5)
      - name: previousRefreshTokenHash
        type: string
        required: false
        description: >
          1つ前(更新で無効にした)リフレッシュトークンのハッシュ。無効になったトークンの再使用を検知するために、
          直前の1世代だけを保持する。`lastRefreshedAt`から猶予(既定10秒)以内の提出は、再送・同時実行による競合とみなして
          Sessionを失効させず、猶予を超えた提出は、盗用の疑いとしてSessionを失効させる(BR5.6)
      - name: issuedAt
        type: string
        required: true
        description: Sessionの発行日時(ISO 8601)。ログインした時刻
      - name: lastRefreshedAt
        type: string
        required: true
        description: 最後にリフレッシュトークンを発行(ログインまたは更新)した日時(ISO 8601)
      - name: refreshExpiresAt
        type: string
        required: true
        description: 現在のリフレッシュトークンの有効期限(ISO 8601)。最後の発行から、設定値(既定30分、FR3.1)後
      - name: status
        type: string
        allowed_values: [active, revoked]
        required: true
        defaults: "active(ログイン時)"
        description: >
          activeは有効、revokedは失効済み(ログアウト・無効になったトークンの再使用・ユーザーの無効化の検知)。
          有効期限の経過(refreshExpiresAt)は、statusを変えず、時刻の比較で判定する
    entity_constraints:
      - "1回のログインにつき1つのSessionを作る。同一ユーザーのSession数に上限は設けない(FR3.2)"
      - "refreshTokenHashは、全Sessionを通じて一意"
      - "statusがrevokedのSessionは、認証(アクセストークンの検証・リフレッシュ・アクティブロールの取得)に使えず、再びactiveに戻らない"
    relationships:
      - target: User
        cardinality: "0..*"
        direction: "Session * -> 1 User"
        description: 各Sessionは1人のUserに対して発行される。Userは0個以上のSessionを持つ
      - target: Role
        cardinality: "0..1"
        direction: "Session 1 -> 0..1 Role"
        description: 各Sessionは、選択済みの場合に限り、1個のアクティブロールを参照する

  - name: AccountLoginState
    description: >
      登録済みでactiveなユーザーごとの、連続ログイン失敗の回数とロックの状態(FR2.7)。Domain Designの`LoginAttempt`
      (userId・attemptedAt・succeeded)が担う「失敗回数の追跡」の概念を、同時実行でも回数を正しく数えられる形
      (ユーザーごとに1件のカウンタ)に具体化したもの。試行ごとの履歴は保持しない(履歴の永続化を求める要件が無いため、
      [assumption]。詳細はfunctional-spec.mdのAssumptions & Open Questions)。
    attributes:
      - name: userId
        type: string
        required: true
        unique: true
        references: User
        description: 対象ユーザー(主キー)。Userと1対0または1
      - name: consecutiveFailures
        type: integer
        required: true
        defaults: 0
        min: 0
        description: >
          連続したログイン失敗の回数(検証の前に確保した試行の枠の数を含む。予約型、BR5.3)。ログイン成功で0に戻す。
          ロック中の試行は数えない。ロックが自動解除された後の最初の試行の予約では、0に戻してから数える(BR5.3)
      - name: lockedUntil
        type: string
        required: false
        description: ロックの解除予定日時(ISO 8601)。ロックされていなければnull。現在時刻がこの日時より前ならロック中。回数がしきい値に達する予約の更新と、原子的に設定される(BR5.3)
      - name: generation
        type: integer
        required: true
        defaults: 0
        min: 0
        description: >
          失敗回数のリセット(ログイン成功・ロックの自動解除後の最初の予約)のたびに1増える世代番号。予約した試行が、
          補償の更新(枠を返す)を行うとき、リセットをまたいで、別の世代の回数を誤って戻さないための条件に用いる(BR5.3)
    entity_constraints:
      - "レコードは、activeなユーザーへの最初のログイン試行の、試行の枠の確保(予約)で作る(登録されていないメールアドレス・招待中・無効化済みへの試行では作らない、BR5.3)"
      - "consecutiveFailuresの更新は、予約型の原子的な更新(なければ作成する場合を含む)で行い、同時に行われる複数の試行でも回数が失われず、しきい値を超えて検証されない(BR5.3)"
      - "consecutiveFailuresがしきい値以上になる予約の更新は、同じ更新の中で、lockedUntilを設定する。したがって、consecutiveFailuresがしきい値以上でlockedUntilが空の状態は、通常は存在しない(存在した場合は、次の予約で自己修復する。BR5.3)"
      - "lockedUntilが非nullで、現在時刻がその日時以降の場合は、ロックは解除済みとして扱う(明示的な解除処理を要しない)"
    relationships:
      - target: User
        cardinality: "0..1"
        direction: "AccountLoginState 1 -> 1 User"
        description: 各AccountLoginStateは、activeなUser1人に対するものである。Userは、0個または1個を持つ

  - name: AccessTokenClaims
    description: >
      アクセストークン(JWT)が運ぶ値(値オブジェクト。永続化しない)。署名で改ざんを検知する。
      アクティブロールは含めない(Q4=A)。
    attributes:
      - name: sub
        type: string
        required: true
        description: userId
      - name: sid
        type: string
        required: true
        description: sessionId
      - name: iat
        type: string
        required: true
        description: 発行日時
      - name: exp
        type: string
        required: true
        description: 有効期限(発行から、設定値。既定10分、FR3.1)
    entity_constraints:
      - "パスワード・メールアドレス・氏名・ロールなどの、その他の値は含めない"
    relationships: []

  - name: Operator
    description: >
      認証済みのリクエストの操作者(値オブジェクト。永続化しない)。認証フィルタが、アクセストークンのuserIdと
      sessionIdと、Sessionのアクティブロールから決め、各ユニットが読む。`Operator`と、それを読む読み取り専用の
      インタフェース(`OperatorContext`)は、authentication-serviceの業務ロジックに依存しない、中立の共有契約
      (新しい契約C15、共通基盤の所有)として置く。認証フィルタ(authentication-service)が値を設定し、他ユニット
      (U2・U4・U6・U7)は読むだけである。他ユニット(U2・U4・U6・U7)の暫定の操作者取得(ヘッダー方式)に置き換わる
      (BR5.11・BR5.12、Q9=A)。
    attributes:
      - name: userId
        type: string
        required: true
        description: アクセストークンのsub
      - name: sessionId
        type: string
        required: true
        description: アクセストークンのsid
      - name: activeRoleId
        type: string
        required: false
        description: Sessionのアクティブロール。未選択ならnull
    entity_constraints:
      - "操作者が解決できる(認証済み)なら、activeRoleIdがnullでも、Operatorは存在する。activeRoleIdがnullであることは、認証エラー(401)ではなく権限なし(403)を意味する(BR5.12)"
    relationships: []
```

## エンティティ概要

- **Session**: 1回のログイン(1端末)。リフレッシュトークンのハッシュ(現在の世代と1つ前の世代)・アクティブロール・有効期限・状態を持つ。アクセストークンは、userIdとsessionIdだけを運び、アクティブロールはSessionが持つ。
- **AccountLoginState**: activeなユーザーごとの、連続ログイン失敗の回数とロックの解除予定日時。Domain Designの`LoginAttempt`を、原子的に更新できる1件のカウンタとして具体化した。
- **AccessTokenClaims**: アクセストークンが運ぶ値(`sub`・`sid`・`iat`・`exp`)。永続化しない。
- **Operator**: 認証フィルタが決める、リクエストの操作者(userId・sessionId・activeRoleId)。永続化しない。
