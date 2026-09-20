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
      無期限に有効な招待トークン(UUIDv4)を発行して招待メールを送信する(BR4.16)。この時点では
      passwordHashは設定しない。emailはBR4.15の検証・正規化を通し、正規化後のemailが
      既存Userと重複する場合は、既存Userがstatus=invitedのときに限り再招待(BR4.11)として扱い、
      それ以外(active/disabled)は重複として拒否する。
    category: policy
    applies_to: [User]
    trigger: "POST /api/users呼び出し時"
    logic: "IF 正規化後emailが既存Userと重複しない THEN status=invitedでUserを作成し招待メールを送信する。ELSE IF 既存Userがstatus=invited THEN BR4.11(再招待)を適用する。ELSE 422バリデーションエラーとする。"
    violation_behaviour: "422 Validation Error(フィールド単位、project.md Mandated)"
    source: "FR2.1, functional-design-questions.md Q2"

  - id: BR4.2
    statement: >
      招待受諾(POST /api/users/invitations/{token}/accept)は、status=invitedのUserに対応する
      招待トークンに対してのみ成功し、パスワード(8文字以上128文字以下、文字数はUnicodeコードポイント数)・
      氏名を設定してstatusをinvitedからactiveへ遷移させる。トークンには有効期限を設けない(Q2確定)。
      status遷移はinvited→activeの条件付き更新として原子的に行い、同一トークンの並行受諾では
      1件のみ成功させる。受諾に成功したUserのinvitationTokenはnullにする(再利用不可)。
      未知・使用済み・取消済みのトークンは区別せず、いずれも同一の応答(404)とする。
    category: validation
    applies_to: [User]
    trigger: "POST /api/users/invitations/{token}/accept呼び出し時"
    logic: "IF トークンが実在しstatus=invited かつ password長が8以上128以下 THEN password をArgon2idでハッシュ化しpasswordHash・nameを設定、status=activeへ原子的に遷移、invitationTokenをnullにする。ELSE IF トークンが未知・使用済み・取消済み THEN 404。ELSE 422。"
    violation_behaviour: "404 Not Found(トークン未知・使用済み・取消済み、区別しない) または 422 Validation Error(パスワード長・氏名の不備、フィールド単位)"
    source: "FR2.1, FR2.5, functional-design-questions.md Q1, Q2"

  - id: BR4.3
    statement: >
      招待受諾リクエストは、theme/fontSize/localeを任意項目として受け付けることができる
      (C5契約追補)。指定された場合はその値で、省略された場合は既定値(light/medium/ja)で
      UserPreferenceレコードを作成する。UserPreferenceの作成は招待受諾の一部として同一トランザクションで
      行う。初期管理者(BR4.7)のUserPreferenceも既定値で作成する。
    category: policy
    applies_to: [UserPreference]
    trigger: "POST /api/users/invitations/{token}/accept呼び出し時(BR4.2と同時)、および初期管理者の自動作成時(BR4.7)"
    logic: "IF theme/fontSize/localeが指定されている THEN その値を用いる。ELSE 既定値(light/medium/ja)を用いる。いずれの場合もUserPreferenceレコードを作成する。"
    violation_behaviour: "422 Validation Error(許容値外の場合)"
    source: "FR9.1, FR10.1, functional-design-questions.md Q4"

  - id: BR4.4
    statement: >
      パスワードは平文で保存・ログ出力・監査ログ出力・エラーメッセージ出力してはならない。
      Argon2idでハッシュ化した値のみを保持する。招待トークンも認証情報として扱い、アプリケーションログ・
      監査ログ・エラーメッセージへ出力せず、アクセスログ等でURLパス中のトークン部分をマスクする。
    category: constraint
    applies_to: [User]
    trigger: "パスワード・招待トークンを伴うすべての操作"
    logic: "N/A(不変条件)"
    violation_behaviour: "N/A(実装レベルで平文保持経路を作らないことで担保する)"
    source: "FR2.6, project.md Mandated, functional-design-questions.md Q1"

  - id: BR4.5
    statement: >
      ユーザー招待(POST /api/users)およびユーザー情報更新(PUT /api/users/{userId})でroleIdsを
      指定・変更する場合、指定された各roleIdがPermissionEngine側に実在することを検証し、
      実在しないroleIdが1件でも含まれる場合はfail fastで拒否する(Q6確定、C10契約への追補が必要)。
      ただし初期管理者の自動作成(BR4.7)には適用しない。
    category: validation
    applies_to: [User]
    trigger: "POST /api/users呼び出し時、またはPUT /api/users/{userId}呼び出し時(roleIds変更を含む場合)"
    logic: "IF roleIdsの全件がPermissionEngineApiの新規存在検証メソッドで実在確認できる THEN 招待・更新を許可する。ELSE 422バリデーションエラーとする。"
    violation_behaviour: "422 Validation Error(実在しないroleIdを明示)"
    source: "FR2.1, FR2.2, functional-design-questions.md Q6"

  - id: BR4.6
    statement: >
      ユーザー無効化(DELETE /api/users/{userId})は、statusをdisabledへ遷移させ、
      UserChangedEvent(operation=DISABLED)を即座に発行する。既発行のアクセストークンは
      その有効期限が切れるまで失効しない(FR2.3で明示された既定動作)。自分自身の無効化は
      禁止する(全管理者ロックアウトの防止)。すでにdisabledのUserへの再実行は冪等に204を返し、
      イベントは重複発行しない。status=invitedのUserへの実行は招待の取消(BR4.11)として扱う。
    category: policy
    applies_to: [User]
    trigger: "DELETE /api/users/{userId}呼び出し時"
    logic: "IF 対象userIdが操作者自身 THEN 422。ELSE IF 対象がdisabled THEN 何もせず204。ELSE status=disabledへ遷移し(invitedの場合はBR4.11に従いinvitationTokenもnullにする)、UserChangedEvent(operation=DISABLED, targetId=userId, before/afterValueにstatus変化を含む)を発行する。"
    violation_behaviour: "422 Validation Error(自分自身の無効化)"
    source: "FR2.3"

  - id: BR4.7
    statement: >
      アプリ起動時、`application.yml`に設定された初期管理者のメールアドレス・パスワードに対応する
      Userが(statusを問わず)存在しない場合に限り、status=activeの管理者アカウントを自動作成する
      (既に存在する場合は何もしない、冪等。disabledの初期管理者の復旧手段は本ユニットの対象外)。
      パスワードはFR2.5の最小8文字(およびBR4.2の最大128文字)を満たさない場合、起動時にfail fastする
      (設定定義自体の誤り、project.md Mandated)。初期管理者のroleIdsは`application.yml`の任意項目
      `initial-admin.role-ids`(未指定なら空)から設定し、BR4.5の実在検証は適用しない
      (ロールは後続のRBAC設定インポートで定義されるため、作成時点では未定義でありうる)。
      作成時にBR4.3に従いUserPreferenceを既定値で作成し、UserChangedEvent(operation=BOOTSTRAPPED)を
      発行する(BR4.9)。
    category: policy
    applies_to: [User, UserPreference]
    trigger: "アプリケーション起動時"
    logic: "IF `application.yml`の初期管理者emailに対応するUserが存在しない THEN パスワード長を検証(不備なら起動失敗)、Argon2idでハッシュ化しstatus=active・roleIds=initial-admin.role-idsでUser、既定値のUserPreferenceを作成し、BOOTSTRAPPEDイベントを発行する。ELSE 何もしない。"
    violation_behaviour: "起動失敗(設定定義の誤り。パスワードは平文でログ・エラーメッセージに出力しない)"
    source: "FR2.4, project.md Mandated(fail fast)"

  - id: BR4.8
    statement: >
      `/api/me/preferences`(GET/PUT)は、認証済み(有効なBearerトークンから操作者のuserIdを取得できる)
      であれば誰でも自分自身の設定を操作できる。canAccessScreenによる画面レベルの権限判定は行わず、
      activeRoleIdの選択有無にも依存しない(Q5確定、表示設定はロール権限とは独立した個人設定のため)。
      対象userIdは常にトークンから得た操作者のuserIdとし、リクエストからは受け取らない。
      GETは、UserPreferenceが存在しない場合(例: データ不整合)は既定値(light/medium/ja)を返すのみで作成はしない。
      PUTは、存在しなければ作成し、存在すれば更新する。
    category: authorization
    applies_to: [UserPreference]
    trigger: "GET/PUT /api/me/preferences呼び出し時"
    logic: "IF Bearerトークンから操作者のuserIdを取得できない THEN 401。ELSE 操作者自身のUserPreferenceを取得・更新する(GETは未作成なら既定値を返す)。"
    violation_behaviour: "401 Unauthorized(未認証のみ。403は発生しない)、422 Validation Error(許容値外)"
    source: "FR9.1, FR10.1, functional-design-questions.md Q5"

  - id: BR4.9
    statement: >
      User(招待・招待受諾・更新・無効化・初期管理者作成)の変更操作は、変更ごとに1件、AuditLogging(U7)へ
      ドメインイベント(UserChangedEvent)を発行しなければならない。operationはINVITED(招待・再招待)・
      ACTIVATED(招待受諾によるパスワード設定・有効化)・UPDATED(管理者による更新)・DISABLED(無効化・招待取消)・
      BOOTSTRAPPED(初期管理者の自動作成)とする。イベントは変更前後の値({name, email, status, roleIds}、
      passwordHashとinvitationTokenは含めない)・操作者・発生日時・対象種別(targetType=User)を含む(Q3確定)。
      actorは、W1・W3・W4では操作した管理者のuserId、W2では受諾したUser自身のuserId、W5ではシステム識別子
      (固定文字列`system`、userIdではない値をuserIdとして偽装しない)とする。
    category: policy
    applies_to: [User]
    trigger: "POST /api/users・POST /api/users/invitations/{token}/accept・PUT /api/users/{userId}・DELETE /api/users/{userId}の成功時、および初期管理者の自動作成時"
    logic: "各操作の成功時に、その操作1回につき1件のUserChangedEventを発行する(招待受諾は招待作成とは別の事象であるためW1のINVITEDとW2のACTIVATEDの2件になる)。冪等な再実行(すでにdisabledへの再DELETE等)では発行しない。"
    violation_behaviour: "N/A"
    source: "project.md Mandated(監査ログ), functional-design-questions.md Q3"

  - id: BR4.10
    statement: >
      /api/users系(管理者向けCRUD)の呼び出しは、activeRoleIdがcanAccessScreen(activeRoleId,
      "user-management")でtrueを返す場合のみ許可する。falseの場合は403、activeRoleIdまたは
      操作者のuserIdを解決できない場合は401とする。"user-management"はpermission-engineが予約する
      screenKeyであり(permission-engineのcanAccessScreenが扱う予約キー)、本ユニットはその値を
      再定義しない。
    category: authorization
    applies_to: [User]
    trigger: "/api/users系エンドポイント呼び出し時"
    logic: "IF activeRoleIdまたは操作者userIdが未解決 THEN 401。ELSE IF canAccessScreen(activeRoleId, \"user-management\")がfalse THEN 403。ELSE 許可する。"
    violation_behaviour: "401 Unauthorized / 403 Forbidden"
    source: "project.md Mandated(サーバー側実効権限の再検証)"

  - id: BR4.11
    statement: >
      招待の再発行と取消を次のとおり扱う。再招待は、status=invitedの既存Userと同一(正規化後)emailで
      POST /api/usersを再実行することで行い、新しいinvitationTokenを発行して旧トークンを無効にし、
      name・roleIdsを新しいリクエストの値で更新し、招待メールを再送する(イベントはoperation=INVITED、
      beforeValueは非null)。取消は、status=invitedのUserへのDELETE /api/users/{userId}で行い、
      statusをdisabledへ遷移させinvitationTokenをnullにする(以後そのトークンは404となる)。
      disabledのUserを同一emailで再招待・復帰させる手段は本ユニットの対象外とする。
    category: policy
    applies_to: [User]
    trigger: "status=invitedの既存Userと同一emailでのPOST /api/users、またはstatus=invitedのUserへのDELETE"
    logic: "IF POST /api/usersの正規化後emailがstatus=invitedのUserと一致 THEN 新トークン発行・旧トークン無効・name/roleIds更新・メール再送・INVITEDイベント発行。IF DELETEの対象がstatus=invited THEN status=disabled・invitationToken=nullとしDISABLEDイベントを発行する。"
    violation_behaviour: "N/A(再招待対象がactive/disabledの場合はBR4.1に従い422)"
    source: "FR2.1, FR2.3, functional-design-questions.md Q2"

  - id: BR4.12
    statement: >
      ロール付与(User.roleIds)による権限昇格を防止する。roleIdsの付与・変更は、
      canAccessScreen(activeRoleId, "user-management")がtrueとなる操作者(権限管理者)による
      明示的な操作(POST /api/users・PUT /api/users/{userId})に限る(BR4.10)。加えて、
      操作者は自分自身のroleIdsを変更してはならない(自分自身への昇格の防止、project.md Forbidden)。
      初期管理者の自動作成(BR4.7)は運用者が`application.yml`で明示した設定によるものであり、本ルールの例外とする。
    category: authorization
    applies_to: [User]
    trigger: "PUT /api/users/{userId}でroleIdsを変更する呼び出し時"
    logic: "IF 対象userIdが操作者自身 かつ roleIdsが現在値と異なる THEN 422。ELSE BR4.10・BR4.5に従う。"
    violation_behaviour: "422 Validation Error(自分自身のroleIds変更)"
    source: "project.md Forbidden(権限の昇格の禁止), FR2.2"

  - id: BR4.13
    statement: >
      C11 UserAccountLookupApiの提供側の振る舞いを次のとおり定める。findByEmailは正規化後(BR4.15)のemailで
      検索し、UserAccountを返す(roleIdsは直接付与分とPermissionEngineApiのgetGroupDerivedRoleIdsで得た
      Group経由分の和集合。passwordHashは返さずnullとする)。verifyPasswordHashは、
      対象がstatus=activeでpasswordHashが非nullの場合のみArgon2idの検証結果を返し、
      invited・disabled・不存在の場合はfalseを返す(検証にはハッシュ関数ライブラリの定数時間比較を用い、
      平文をログに出さない)。isDisabledは常に最新のstatusを参照し(キャッシュしない)、
      status=disabledまたは不存在の場合にtrueを返す(fail closed)。
      これによりFR2.3の「以後の再認証はできない」は、authentication-serviceが再認証(トークン更新)時に
      isDisabledを必ず確認することで満たされる。
    category: policy
    applies_to: [User]
    trigger: "authentication-serviceからのC11メソッド呼び出し時"
    logic: "上記statementのとおり。"
    violation_behaviour: "N/A(不存在は例外にせず、Optional.empty / false / trueで表現する)"
    source: "FR2.3, FR2.6, project.md Mandated"

  - id: BR4.14
    statement: >
      GET /api/users(ユーザー一覧)は、User(userId, name, email, status, roleIds)のみを返し、
      passwordHashとinvitationTokenは返さない(C5のUserスキーマ)。ページングは行わず全件をemail昇順で返す
      (C5は配列を返す)。PUT /api/users/{userId}が更新可能な項目はnameとroleIdsのみとし、
      email・status・passwordHash・invitationTokenは更新できない(statusの変更はDELETEのみ)。
      対象が存在しない場合は404とする。本ユニットが扱うのはUser/UserPreference/UserChangedEventのみであり、
      業務固有のテーブル名・カラム名を持たない。
    category: constraint
    applies_to: [User]
    trigger: "GET /api/users・PUT /api/users/{userId}呼び出し時"
    logic: "GETは全Userを機微項目を除いて返す。PUTはname・roleIds以外の項目を受け付けない(指定されても無視せず422)。"
    violation_behaviour: "404 Not Found(対象不存在)、422 Validation Error(更新不可項目の指定・フィールド単位)"
    source: "FR2.2, FR1.6, project.md Mandated(共通エンジン層に業務固有のハードコードをしない)"

  - id: BR4.15
    statement: >
      emailは、前後の空白を除去し、形式(local@domain)を検証したうえで小文字へ正規化して保持・比較する
      (ログイン識別子であり、大文字小文字違いの重複アカウントを防ぐため)。nameは空でない文字列であることを検証する。
      いずれもフィールド単位のバリデーションエラーとして返す。
    category: validation
    applies_to: [User]
    trigger: "POST /api/users・招待受諾・PUT /api/users/{userId}のemail・name入力時"
    logic: "IF emailが形式不正 THEN 422。ELSE 小文字化・trimした値で一意判定・保存する。IF nameが空 THEN 422。"
    violation_behaviour: "422 Validation Error(フィールド単位)"
    source: "FR2.1, project.md Mandated(フィールド単位のエラー)"

  - id: BR4.16
    statement: >
      招待メール(POST /api/usersおよび再招待)は、`application.yml`に設定されたSMTP接続情報で送信する
      (開発・動作確認環境ではMailpitで受信を確認する)。メール送信に失敗した場合は招待全体を失敗させ、
      Userレコードの作成・更新(再招待の場合は新トークンの発行)も巻き戻し、503 Service Unavailableを返す
      (要件に明示がないため[assumption]として本設計の方針とする)。SMTP設定自体の不備は起動時に検知してfail fastする。
    category: policy
    applies_to: [User]
    trigger: "招待メール送信時"
    logic: "IF 送信成功 THEN 招待を確定しINVITEDイベントを発行する。ELSE Userの作成・更新を巻き戻し503を返す(イベントは発行しない)。"
    violation_behaviour: "503 Service Unavailable(RFC 9457 ProblemDetails)"
    source: "FR2.8"
```

## ルール概要

| ID | カテゴリ | 概要 |
|---|---|---|
| BR4.1 | policy | ユーザー招待(status=invited作成、無期限トークン発行、既存invitedは再招待) |
| BR4.2 | validation | 招待受諾(パスワード8〜128文字、Argon2id、原子的なinvited→active、トークンnull化、404一本化) |
| BR4.3 | policy | 招待受諾時・初期管理者作成時のUserPreference作成(任意項目、既定値フォールバック) |
| BR4.4 | constraint | パスワード・招待トークンの平文出力の禁止 |
| BR4.5 | validation | roleId実在検証(fail fast、初期管理者を除く) |
| BR4.6 | policy | ユーザー無効化(自己無効化の禁止、冪等、イベント発行、既発行トークンは失効期限まで有効) |
| BR4.7 | policy | 初期管理者アカウントの冪等な自動作成(fail fast、roleIdsは設定値) |
| BR4.8 | authorization | /api/me/preferencesは認証済みなら誰でも自分の設定を操作可(activeRoleId不要) |
| BR4.9 | policy | UserChangedEventの発行(operation種別・actor・targetType・変更前後の値) |
| BR4.10 | authorization | /api/users系はcanAccessScreen("user-management")による認可 |
| BR4.11 | policy | 招待の再発行(同一emailへの再POST)と取消(invitedへのDELETE) |
| BR4.12 | authorization | ロール付与による権限昇格の防止(権限管理者のみ、自分自身のroleIds変更禁止) |
| BR4.13 | policy | C11提供側の振る舞い(findByEmail・verifyPasswordHash・isDisabled) |
| BR4.14 | constraint | 一覧の出力項目(機微項目を除外)・PUTの更新可能項目 |
| BR4.15 | validation | email・nameの検証と正規化 |
| BR4.16 | policy | 招待メール送信とSMTP失敗時の扱い |
