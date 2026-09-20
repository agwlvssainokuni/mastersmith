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

# Business Rules — authentication-service (U5)

```yaml
rules:
  - id: BR5.1
    statement: >
      ログイン(POST /api/auth/login)は、リクエストのemailをtrimし小文字へ正規化したうえで(user-managementのBR4.15と同じ規則)、
      user-managementのC11(`findByEmail`・`verifyPasswordHash`)で認証する。認証に成功する条件は、対象のUserが存在し、
      statusがactiveで、ロック中でなく、パスワードの検証が成功することの、すべてを満たすことである。成功したら、
      Session(BR5.8)・アクセストークン・リフレッシュトークン(BR5.5)を発行し、アクセストークン・リフレッシュトークン・
      選択可能なロール(C11の`UserAccount.roleIds`、直接付与分とGroup経由分の和集合)を返す。C11の呼び出しは、
      トランザクションの外で行う(C11追補、NFR Designの資源の取得順序の不変条件)。
    category: policy
    applies_to: [Session, AccountLoginState]
    trigger: "POST /api/auth/login呼び出し時"
    logic: "IF 正規化後emailに対応するUserが存在し status=active かつ ロック中でない かつ verifyPasswordHash=true THEN Sessionを作成しトークンを発行して200を返す。ELSE BR5.2の失敗応答を返す。"
    violation_behaviour: "401 Unauthorized(BR5.2の統一された応答)。ハッシュ計算の同時実行数の上限超過は503(BR5.15)"
    source: "FR3.1, FR2.7, contract-summary.md C4・C11, functional-design-questions.md Q3(受諾後のログインはフロントエンドがこのAPIを呼ぶ)"

  - id: BR5.2
    statement: >
      ログインの失敗は、原因(登録されていないメールアドレス・パスワードの誤り・ロック中・無効化済み・招待中で未受諾)を
      区別せず、同一の応答(401、同じステータス・タイトル・詳細、RFC 9457のProblemDetails)を返す。ロック状態や
      アカウントの存在を、応答の内容から推測できないようにする。加えて、登録されていない・招待中・無効化済み・ロック中の
      場合でも、ダミーのハッシュを検証してから応答し、パスワードの検証が行われた場合と応答時間を近づける(メールアドレスの
      存在の推測を難しくする)。ただし、ハッシュ計算の同時実行数の上限を超えた場合(C11の`HashCapacityExceededException`)は、
      原因を隠さず503を返す(BR5.15)。
    category: policy
    applies_to: [Session, AccountLoginState]
    trigger: "ログイン失敗時"
    logic: "IF 認証に失敗(BR5.1の条件のいずれかを満たさない) THEN 実際に検証を行わなかった場合はダミーのハッシュを検証したうえで、同一の401を返す。"
    violation_behaviour: "N/A(不変条件)"
    source: "contract-summary.md C4(ロック中の試行も同一の汎用メッセージ), unit-of-work.md U5, functional-design-questions.md Q8"

  - id: BR5.3
    statement: >
      アカウントのロックを次のとおり扱う。activeなユーザーのログインに失敗するたびに、`AccountLoginState.consecutiveFailures`を
      1増やし、設定値(既定5回)に達したら、`lockedUntil`を、現在時刻に設定値(既定15分)を加えた日時にする。ログインに
      成功したら、`consecutiveFailures`を0に戻し、`lockedUntil`を消す。ロック中(現在時刻が`lockedUntil`より前)の試行は、
      失敗回数に数えず、ロック期間も延長しない(その試行は、パスワードの検証が正しくても、BR5.2の失敗として拒否する)。
      ロックは、`lockedUntil`の経過で自動的に解除され、解除後の最初の試行は、`consecutiveFailures`を0として扱う。
      登録されていないメールアドレス・招待中・無効化済みのユーザーへの試行は、失敗回数を記録しない(存在の有無を外部に
      漏らさないため、また、それらは成功しえないため)。`consecutiveFailures`の更新は原子的に行い、同時に行われる複数の
      試行で回数が失われて、しきい値を回避されることがないようにする。
    category: policy
    applies_to: [AccountLoginState]
    trigger: "activeなユーザーへのログイン試行時"
    logic: "IF ロック中 THEN 数えずにBR5.2の失敗応答。ELSE IF 検証成功 THEN 回数を0に戻す。ELSE 回数を1増やし、しきい値に達したらlockedUntilを設定する(応答はBR5.2の失敗応答)。"
    violation_behaviour: "401 Unauthorized(BR5.2)"
    source: "FR2.7, functional-design-questions.md Q2"

  - id: BR5.4
    statement: >
      ロックの閾値(既定5回)とロック時間(既定15分)は、`application.yml`で設定する。管理画面からの編集UIは設けない。
      アクセストークン・リフレッシュトークンの有効期限(FR3.1)も`application.yml`で設定する。要件定義書のFR2.7の
      文言(「管理画面から設定可能」)は、「`application.yml`で設定可能(管理画面での編集UIは設けない)」に修正する
      (追補として記録し、元の記述は残す)。設定値が不正(0以下など)な場合は、起動時にfail fastする(project.md Mandated)。
    category: constraint
    applies_to: [AccountLoginState, Session]
    trigger: "アプリケーションの起動時、および設定値の参照時"
    logic: "IF 閾値・ロック時間・有効期限のいずれかが0以下または未設定(既定値がある項目を除く) THEN 起動を失敗させる。ELSE 設定値をログインと更新で用いる。"
    violation_behaviour: "起動失敗(設定定義の誤り)"
    source: "FR2.7, FR3.1, project.md Mandated(fail fast), functional-design-questions.md Q1"

  - id: BR5.5
    statement: >
      トークンを次のとおり発行する。アクセストークンはJWTで、署名し、`sub`(userId)・`sid`(sessionId)・`iat`・`exp`
      だけを運ぶ(アクティブロールは含めない、Q4=A)。有効期限は設定値(既定10分)。リフレッシュトークンは、推測不能な
      ランダムな値(不透明で、内容を持たない)で、有効期限は設定値(既定30分)。リフレッシュトークンの平文は、応答でだけ
      返し、保存はハッシュのみとする(平文をログ・監査ログ・エラーメッセージに出力しない、project.md Mandated)。JWTの
      署名の鍵は、設定(環境変数から注入)で与え、リポジトリに置かない。鍵が未設定または短すぎる場合は、起動時にfail fastする。
    category: constraint
    applies_to: [Session, AccessTokenClaims]
    trigger: "ログイン成功時・リフレッシュ成功時"
    logic: "JWTを署名して発行する。リフレッシュトークンをランダムに生成し、ハッシュをSessionへ保存する。"
    violation_behaviour: "起動失敗(鍵の不備)"
    source: "FR3.1, project.md Mandated(認証情報・秘密の扱い), functional-design-questions.md Q4・Q5"

  - id: BR5.6
    statement: >
      リフレッシュ(POST /api/auth/refresh)は、更新のたびに新しいリフレッシュトークンを発行して有効期限を延長する
      (ローテーション)。手順は、提出されたリフレッシュトークンのハッシュで、statusがactiveでrefreshExpiresAtが未経過の
      Sessionを見つけ、BR5.7の確認を通したうえで、新しいアクセストークンと新しいリフレッシュトークンを発行する。
      Sessionの`previousRefreshTokenHash`に、それまでの`refreshTokenHash`を移し、`refreshTokenHash`・`lastRefreshedAt`・
      `refreshExpiresAt`(発行から設定値後)を更新する。提出されたリフレッシュトークンが、`previousRefreshTokenHash`と
      一致する(すでに無効になったトークンの再使用)場合は、そのSessionを失効(revoked)させ、401を返す。未知・期限切れ・
      失効済みのトークンは、いずれも同一の401を返す(区別しない)。同一のリフレッシュトークンの同時の更新では、1件だけが
      成功する(条件付きの更新)。
    category: policy
    applies_to: [Session]
    trigger: "POST /api/auth/refresh呼び出し時"
    logic: "IF ハッシュがrefreshTokenHashと一致しactive・未期限切れ THEN BR5.7の確認のあと、トークンを更新して200を返す。ELSE IF ハッシュがpreviousRefreshTokenHashと一致 THEN Sessionをrevokedにして401。ELSE 401。"
    violation_behaviour: "401 Unauthorized(同一の応答)"
    source: "FR3.1, functional-design-questions.md Q5"

  - id: BR5.7
    statement: >
      リフレッシュのたびに、user-managementのC11の`isDisabled(userId)`(常に最新のstatusを返す)を確認し、無効化されている
      (disabledまたは不存在)場合は、更新を拒否して、そのSessionを失効させ、401を返す(引き込み型。FR2.3の「以後の再認証
      (トークン更新)はできない」を、更新時の確認で担保する)。user-managementから本ユニットへの失効の呼び出しは設けない
      (C11の`revokeRefreshTokensOnDisable`は削除する追補を行う)。無効化の前に発行済みのアクセストークンは、要件どおり、
      有効期限(最大10分)まで有効なままである。
    category: policy
    applies_to: [Session]
    trigger: "POST /api/auth/refresh呼び出し時"
    logic: "IF isDisabled(userId)=true THEN Sessionをrevokedにして401。"
    violation_behaviour: "401 Unauthorized(BR5.6と同一の応答)"
    source: "FR2.3, FR3.1, functional-design-questions.md Q6, functional-design/functional-spec.md(user-management)Open Questions"

  - id: BR5.8
    statement: >
      ログインの成功のたびに、新しいSessionを1つ作る。同一のユーザーが、複数の端末から同時にログインでき、それぞれが
      独立したSession(独立したリフレッシュトークン)を持つ(FR3.2)。同一ユーザーのSession数に上限は設けない。
      ログアウト(POST /api/auth/logout)は、リクエストのアクセストークンのsidに対応するSessionだけを失効(revoked)させ、
      同一ユーザーの他のSessionには影響しない。すでに失効済みのSessionへのログアウトは、冪等に204を返す。
    category: policy
    applies_to: [Session]
    trigger: "ログイン成功時、およびPOST /api/auth/logout呼び出し時"
    logic: "ログイン成功: Session(active)を1件作る。ログアウト: sidのSessionをrevokedにする。"
    violation_behaviour: "N/A(認証されていないログアウトは、BR5.11に従い401)"
    source: "FR3.2, contract-summary.md C4"

  - id: BR5.9
    statement: >
      アクティブロールを次のとおり扱う。選択できるロールは、ログイン時にC11の`UserAccount.roleIds`(直接付与分とGroup経由分の
      和集合)から得る。ロールがちょうど1つのユーザーは、ログイン時に、そのロールを、自動的にアクティブロールとして
      Sessionへ保存する。ロールが0個、または2個以上のユーザーは、アクティブロールが未選択(null)のままSessionを作る。
      複数ロールのユーザーは、PUT /api/auth/active-roleでロールを選択する。指定されたroleIdが、そのユーザーの
      その時点の選択可能なロールに含まれない場合は403を返し、Sessionは変更しない。選択したロールは、そのSessionで保持し、
      ロールを切り替えると、以降のリクエストへ即時に反映する(別のSession(端末)には影響しない)。単一ロールのユーザーは、
      ロール選択そのものを表示しない(FR4.2。フロントエンドの責務)。
    category: policy
    applies_to: [Session]
    trigger: "ログイン成功時、およびPUT /api/auth/active-role呼び出し時"
    logic: "ログイン: roles = C11のroleIds。roles.size=1ならactiveRoleId=その値。それ以外はnull。選択: roleIdがrolesに含まれるならSession.activeRoleIdを更新して200。含まれなければ403。"
    violation_behaviour: "403 Forbidden(保持していないロールの指定)"
    source: "FR4.2, contract-summary.md C4・C14, functional-design-questions.md Q4・Q7"

  - id: BR5.10
    statement: >
      Sessionに保持されたアクティブロールが、その後、ユーザーの選択可能なロールから外れた場合(ロールの削除・付け外し)の
      権限が、無期限に残らないようにする。リフレッシュのたびに、C11から最新の選択可能なロールを取得して、Sessionの
      アクティブロールが、なお含まれることを確認する。含まれなくなっていた場合は、アクティブロールを未選択(null)に
      戻す(残りのロールがちょうど1つになった場合は、そのロールを自動選択する)。したがって、ロールを外されたユーザーが、
      旧ロールの権限で操作できる期間は、リフレッシュの間隔(アクセストークンの有効期限、最大10分)を超えない。
    category: policy
    applies_to: [Session]
    trigger: "POST /api/auth/refresh呼び出し時"
    logic: "IF Session.activeRoleIdが最新の選択可能なロールに含まれない THEN activeRoleIdをnull(または残りが1つならその値)にする。"
    violation_behaviour: "N/A"
    source: "FR3.3, FR3.4, FR4.2, user-management nfr-design/security-design.md 残余リスク1, functional-design-questions.md Q4"

  - id: BR5.11
    statement: >
      認証フィルタが、認証を必要とするすべてのリクエストで、次のとおりBearerトークンを検証し、操作者(`Operator`)を決める。
      検証の内容は、アクセストークンの署名と有効期限が正しく、`sid`のSessionが存在してstatus=activeであること。
      いずれかを満たさない場合は、401を返す。認証を必要としないのは、ログイン(`/api/auth/login`)・リフレッシュ
      (`/api/auth/refresh`)・招待受諾(`/api/users/invitations/{token}/accept`)だけである。フィルタは、`/api/**`のすべてに
      適用され、認証を必要とするAPIには、この検証を経ない経路が無い。決めた操作者(userId・sessionId・アクティブロール)は、
      リクエストの処理中、各ユニットが読める(BR5.12)。アクティブロールは、リクエストごとにSessionから解決する
      (インメモリでキャッシュし、ロールの選択・更新・失効で無効化する)。
    category: authorization
    applies_to: [Session, Operator, AccessTokenClaims]
    trigger: "認証を必要とするAPIの呼び出し時"
    logic: "IF Bearerトークンが無い・署名不正・期限切れ・Sessionが存在しない・Sessionがrevoked THEN 401。ELSE Operator(userId=sub, sessionId=sid, activeRoleId=Sessionの値)を決めて処理を続ける。"
    violation_behaviour: "401 Unauthorized"
    source: "FR3.1, FR3.3, contract-summary.md 前提(認証), functional-design-questions.md Q4・Q9"

  - id: BR5.12
    statement: >
      アクティブロールが未選択(null)のまま、アクティブロールを要する操作が行われた場合は、認証エラー(401)ではなく、
      権限なし(403)として扱う。これは、ロールを持たないユーザー(初期管理者を含む)が、ログインでき、permission-engineの
      判定の結果として拒否される、または(RBAC設定が空の間の例外である)設定インポート画面へ到達できるようにするためである。
      あわせて、他ユニットの暫定の操作者取得(schema-introspectorの`ActiveRoleResolver`・`HeaderActiveRoleResolver`、
      user-managementの`CurrentOperatorProvider`・`HeaderCurrentOperatorProvider`、menu-navigation・audit-loggingが用いる
      同様のヘッダー方式)を、すべて削除し、BR5.11で決めた`Operator`を読む実装に置き換える。コントローラ・サービスの入口
      (`canAccessScreen`による認可の再検証)は変えない。ヘッダー(`X-User-Id`・`X-Active-Role-Id`)を信頼する経路は、
      どのプロファイルにも残さない。
    category: authorization
    applies_to: [Operator, Session]
    trigger: "アクティブロールを要する操作、および認証フィルタの導入時"
    logic: "IF Operator.activeRoleId=null THEN 操作者の解決自体は成功とし、認可の判定はactiveRoleId未選択(権限なし)として行い、拒否は403とする。"
    violation_behaviour: "403 Forbidden(アクティブロール未選択による権限なし)"
    source: "FR3.3, FR4.2, project.md Mandated(サーバー側の再検証), functional-design-questions.md Q7・Q9"

  - id: BR5.13
    statement: >
      C14の`SessionContextApi.getActiveRoleId(sessionId)`は、Sessionのアクティブロールを返す(list-engine・
      record-edit-engineが、権限判定の引数として渡すため)。Sessionが存在しない場合は`SessionNotFoundException`、
      失効済みまたはリフレッシュの有効期限が経過している場合は`SessionExpiredException`を投げる。アクティブロールが
      未選択(null)の場合は、nullを返す(契約の追補。呼び出し元は、nullを権限なしとして扱う、BR5.12)。
    category: policy
    applies_to: [Session]
    trigger: "C14のgetActiveRoleId呼び出し時"
    logic: "IF Sessionが不存在 THEN SessionNotFoundException。ELSE IF revoked または 期限切れ THEN SessionExpiredException。ELSE activeRoleId(nullを含む)を返す。"
    violation_behaviour: "N/A(Java例外。frontend-ui向けにはBearer認証失敗の401に変換される)"
    source: "FR4.2, contract-summary.md C14, functional-design-questions.md Q4・Q7"

  - id: BR5.14
    statement: >
      パスワード・アクセストークン・リフレッシュトークン(平文とハッシュ)・JWTの署名の鍵を、ログ・監査ログ・エラー
      メッセージ・メトリクスのラベルに出力しない。ログには、userIdとsessionIdを用いる。ログイン失敗の理由(BR5.2の
      原因)は、応答にも外部向けのログにも、原因ごとの区別を出さない(運用上の集計が必要な場合は、原因を区別しない
      カウンタで数える)。
    category: constraint
    applies_to: [Session, AccountLoginState]
    trigger: "認証情報・トークンを伴うすべての操作"
    logic: "N/A(不変条件)"
    violation_behaviour: "N/A(実装レベルで平文・ハッシュの出力経路を作らないことで担保する)"
    source: "project.md Mandated(認証情報の非出力), FR2.6の趣旨(認証情報の保護)"

  - id: BR5.15
    statement: >
      C11の`verifyPasswordHash`は、ハッシュ計算の同時実行数の上限を超えた場合に`HashCapacityExceededException`を投げる
      (user-managementのC11追補)。ログインは、この例外を握りつぶさず、原因を隠さず503(RFC 9457のProblemDetails)
      に変換する(リトライを促す)。この場合は、失敗回数に数えず、ロックの状態を変えない。C11のメソッド
      (`findByEmail`・`verifyPasswordHash`・`isDisabled`)は、トランザクションの外で呼ぶ(C11の追補。`verifyPasswordHash`の
      ログイン成功時のハッシュ更新は、C11の側が独立したトランザクションで行う)。
    category: policy
    applies_to: [AccountLoginState]
    trigger: "ログイン時にHashCapacityExceededExceptionが発生したとき"
    logic: "IF HashCapacityExceededException THEN 503を返し、AccountLoginStateを更新しない。"
    violation_behaviour: "503 Service Unavailable"
    source: "user-management nfr-design/performance-design.md NFR1.3, contract-summary.md C11(追補), functional-design-questions.md Q8"

  - id: BR5.16
    statement: >
      FR2.3の即時失効に関する、無効化されたユーザーの認証の扱いを、user-managementと次のとおり分担する。ユーザーが
      無効化された後も、user-managementはC11の`isDisabled`を常に最新の値で返し、本ユニットは、ログイン(BR5.1、statusが
      activeでない)とリフレッシュ(BR5.7)の両方で、無効化されたユーザーを拒否する。失効のための、user-managementから
      本ユニットへの呼び出し・ロック状態の表示・無効化したユーザーの復帰(「有効化」の操作)は、MVPでは設けない
      (Q10=A)。
    category: constraint
    applies_to: [Session, AccountLoginState]
    trigger: "無効化されたユーザーのログイン・リフレッシュ時"
    logic: "N/A(BR5.1とBR5.7の組み合わせ)"
    violation_behaviour: "401 Unauthorized(BR5.2・BR5.6)"
    source: "FR2.3, functional-design-questions.md Q6・Q10"
```

## ルール概要

| ID | カテゴリ | 概要 |
|---|---|---|
| BR5.1 | policy | ログイン(C11で認証、成功条件、トークンとSessionの発行、選択可能なロールを返す) |
| BR5.2 | policy | 失敗の応答の統一(同一の401)とダミーのハッシュ検証による応答時間の均一化 |
| BR5.3 | policy | アカウントロック(5回・15分、成功でリセット、ロック中は数えず延長しない、自動解除) |
| BR5.4 | constraint | ロックの閾値・時間・トークンの有効期限は`application.yml`で設定、要件FR2.7の文言の修正 |
| BR5.5 | constraint | トークンの発行(JWTはsub/sid/iat/exp、リフレッシュは不透明なランダム値でハッシュのみ保存、鍵の扱い) |
| BR5.6 | policy | リフレッシュのローテーションと、無効になったトークンの再使用の検知 |
| BR5.7 | policy | リフレッシュ時のisDisabledの確認(無効化の反映、引き込み型) |
| BR5.8 | policy | ログインごとのSession(複数端末の同時ログイン)、ログアウトは該当のSessionだけを失効 |
| BR5.9 | policy | アクティブロール(単一ロールは自動選択、複数は選択、保持していないロールは403) |
| BR5.10 | policy | リフレッシュ時のアクティブロールの再確認(ロールを外された場合の権限の残存を防ぐ) |
| BR5.11 | authorization | 認証フィルタ(Bearerの検証、認証不要なAPIは3つだけ、Operatorの決定) |
| BR5.12 | authorization | アクティブロール未選択は403、他ユニットの暫定の操作者取得(ヘッダー方式)の削除と置き換え |
| BR5.13 | policy | C14のgetActiveRoleId(例外とnullの扱い) |
| BR5.14 | constraint | パスワード・トークン・鍵の非出力、失敗の原因の区別を出さない |
| BR5.15 | policy | ハッシュ計算の上限超過は503(失敗に数えない)、C11の呼び出しはトランザクションの外 |
| BR5.16 | constraint | 無効化されたユーザーの扱いの分担(ログイン・リフレッシュで拒否、押し出し・ロック表示・復帰はMVP対象外) |
