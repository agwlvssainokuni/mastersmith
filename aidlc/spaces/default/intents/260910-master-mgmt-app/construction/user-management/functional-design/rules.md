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

# Business Rules — user-management (U4)

```yaml
rules:
  - id: BR4.1
    statement: >
      管理者によるユーザー招待(POST /api/users)は、status=invitedのUserレコードを作成し、
      無期限に有効な招待トークン(UUIDv4)を発行して招待メールを送信する。この時点では
      passwordHashは設定しない。
    category: policy
    applies_to: [User]
    trigger: "POST /api/users呼び出し時"
    logic: "IF email が既存Userと重複しない THEN status=invitedでUserを作成し招待メールを送信する。ELSE 422バリデーションエラーとする。"
    violation_behaviour: "422 Validation Error(フィールド単位、project.md Mandated)"
    source: "FR2.1, functional-design-questions.md Q2"

  - id: BR4.2
    statement: >
      招待受諾(POST /api/users/invitations/{token}/accept)は、有効な招待トークンに対してのみ
      成功し、パスワード(最小8文字)・氏名を設定してstatusをinvitedからactiveへ遷移させる。
      トークンには有効期限を設けない(Q2確定)。
    category: validation
    applies_to: [User]
    trigger: "POST /api/users/invitations/{token}/accept呼び出し時"
    logic: "IF トークンが実在しstatus=invited THEN password(8文字以上)をArgon2idでハッシュ化しpasswordHash・nameを設定しstatus=activeへ遷移する。ELSE 404(トークン不明)または422(バリデーションエラー)とする。"
    violation_behaviour: "404 Not Found(トークン不明) または 422 Validation Error(パスワード8文字未満等)"
    source: "FR2.1, FR2.5, functional-design-questions.md Q1, Q2"

  - id: BR4.3
    statement: >
      招待受諾リクエストは、theme/fontSize/localeを任意項目として受け付けることができる
      (C5契約追補)。指定された場合はその値で、省略された場合は既定値(light/medium/ja)で
      UserPreferenceレコードを作成する。UserPreferenceの作成は招待受諾の一部として同一トランザクションで
      行う。
    category: policy
    applies_to: [UserPreference]
    trigger: "POST /api/users/invitations/{token}/accept呼び出し時(BR4.2と同時)"
    logic: "IF theme/fontSize/localeが指定されている THEN その値を用いる。ELSE 既定値(light/medium/ja)を用いる。いずれの場合もUserPreferenceレコードを作成する。"
    violation_behaviour: "422 Validation Error(許容値外の場合)"
    source: "FR9.1, FR10.1, functional-design-questions.md Q4"

  - id: BR4.4
    statement: >
      パスワードは平文で保存・ログ出力・監査ログ出力・エラーメッセージ出力してはならない。
      Argon2idでハッシュ化した値のみを保持する。
    category: constraint
    applies_to: [User]
    trigger: "パスワード設定・検証を伴うすべての操作"
    logic: "N/A(不変条件)"
    violation_behaviour: "N/A(実装レベルで平文保持経路を作らないことで担保する)"
    source: "FR2.6, project.md Mandated, functional-design-questions.md Q1"

  - id: BR4.5
    statement: >
      ユーザー情報更新(PUT /api/users/{userId})でroleIdsを変更する場合、指定された各roleIdが
      PermissionEngine側に実在することを検証し、実在しないroleIdが1件でも含まれる場合はfail fastで
      拒否する(Q6確定、C10契約への追補が必要)。
    category: validation
    applies_to: [User]
    trigger: "PUT /api/users/{userId}呼び出し時(roleIds変更を含む場合)"
    logic: "IF roleIdsの全件がPermissionEngineApiの新規存在検証メソッドで実在確認できる THEN 更新を許可する。ELSE 422バリデーションエラーとする。"
    violation_behaviour: "422 Validation Error(実在しないroleIdを明示)"
    source: "FR2.2, functional-design-questions.md Q6"

  - id: BR4.6
    statement: >
      ユーザー無効化(DELETE /api/users/{userId})は、statusをdisabledへ遷移させ、
      UserChangedEvent(operation=DISABLED)を即座に発行する。既発行のアクセストークンは
      その有効期限が切れるまで失効しない(FR2.3で明示された既定動作)。
    category: policy
    applies_to: [User]
    trigger: "DELETE /api/users/{userId}呼び出し時"
    logic: "status=disabledへ遷移し、UserChangedEvent(operation=DISABLED, targetId=userId, before/afterValueにstatus変化を含む)を発行する。"
    violation_behaviour: "N/A"
    source: "FR2.3"

  - id: BR4.7
    statement: >
      アプリ起動時、`application.yml`に設定された初期管理者のメールアドレスが既存Userとして
      存在しない場合に限り、status=activeの管理者アカウントを自動作成する(既に存在する場合は
      何もしない、冪等)。
    category: policy
    applies_to: [User]
    trigger: "アプリケーション起動時"
    logic: "IF `application.yml`のemailに対応するUserが存在しない THEN status=activeでUserを自動作成しApplicationRunner内でパスワードをArgon2idでハッシュ化する。ELSE 何もしない。"
    violation_behaviour: "N/A"
    source: "FR2.4"

  - id: BR4.8
    statement: >
      `/api/me/preferences`(GET/PUT)は、認証済み(有効なアクセストークンを持つ)であれば
      誰でも自分自身の設定を操作できる。canAccessScreenによる画面レベルの権限判定は行わない
      (Q5確定、表示設定はロール権限とは独立した個人設定のため)。
    category: authorization
    applies_to: [UserPreference]
    trigger: "GET/PUT /api/me/preferences呼び出し時"
    logic: "IF ActiveRoleResolverでactiveRoleIdを解決できる(=認証済み) THEN 自分自身のUserPreferenceの取得・更新を許可する。ELSE 401とする。"
    violation_behaviour: "401 Unauthorized(未認証のみ。403は発生しない)"
    source: "functional-design-questions.md Q5"

  - id: BR4.9
    statement: >
      User(登録・更新・無効化)の変更操作は、変更ごとに1件、AuditLogging(U7)へドメインイベント
      (UserChangedEvent)を発行しなければならない。イベントは変更前後の値
      ({name, email, status, roleIds}、passwordHashは含めない)・操作者・発生日時を含む(Q3確定)。
    category: policy
    applies_to: [User]
    trigger: "POST /api/users(招待)・POST /api/users/invitations/{token}/accept(受諾)・PUT /api/users/{userId}(更新)・DELETE /api/users/{userId}(無効化)呼び出し完了時"
    logic: "各操作の成功時に、その操作1回につき1件のUserChangedEventを発行する。招待受諾はUser更新であり別途のイベントとするか、招待作成イベントの延長として扱うかは実装判断とする(functional-spec.md参照)。"
    violation_behaviour: "N/A"
    source: "project.md Mandated, functional-design-questions.md Q3"

  - id: BR4.10
    statement: >
      /api/users系(管理者向けCRUD)の呼び出しは、activeRoleIdがcanAccessScreen(activeRoleId,
      "user-management")でtrueを返す場合のみ許可する。falseの場合は403、activeRoleIdが
      解決できない場合は401とする。
    category: authorization
    applies_to: [User]
    trigger: "/api/users系エンドポイント呼び出し時"
    logic: "IF activeRoleId未解決 THEN 401。ELSE IF canAccessScreen(activeRoleId, \"user-management\")がfalse THEN 403。ELSE 許可する。"
    violation_behaviour: "401 Unauthorized / 403 Forbidden"
    source: "project.md Mandated(サーバー側実効権限の再検証)"
```

## ルール概要

| ID | カテゴリ | 概要 |
|---|---|---|
| BR4.1 | policy | ユーザー招待(status=invited作成、無期限トークン発行) |
| BR4.2 | validation | 招待受諾(パスワード最小8文字、Argon2idハッシュ化、invited→active) |
| BR4.3 | policy | 招待受諾時のUserPreference作成(任意項目、既定値フォールバック) |
| BR4.4 | constraint | パスワード平文出力の禁止 |
| BR4.5 | validation | roleId実在検証(fail fast) |
| BR4.6 | policy | ユーザー無効化(status遷移、イベント発行、既発行トークンは失効期限まで有効) |
| BR4.7 | policy | 初期管理者アカウントの冪等な自動作成 |
| BR4.8 | authorization | /api/me/preferencesは認証済みなら誰でも自分の設定を操作可 |
| BR4.9 | policy | UserChangedEventの発行(変更単位、変更前後の値を含む) |
| BR4.10 | authorization | /api/users系はcanAccessScreen("user-management")による認可 |
