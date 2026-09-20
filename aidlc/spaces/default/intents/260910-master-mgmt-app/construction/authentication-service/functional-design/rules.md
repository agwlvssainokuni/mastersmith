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
      user-managementのC11(`findByEmail`・`verifyPasswordHash`、失敗時は`dummyVerify`)で認証する。認証に成功する条件は、対象のUserが存在し、
      statusがactiveで、ロック中でなく、パスワードの検証が成功することの、すべてを満たすことである。成功したら、
      Session(BR5.8)・アクセストークン・リフレッシュトークン(BR5.5)を発行し、アクセストークン・リフレッシュトークン・
      選択可能なロール(C11の`UserAccount.roleIds`、直接付与分とGroup経由分の和集合)・アクティブロール(BR5.9。未選択ならnull)を返す。C11の呼び出しは、
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
      アカウントの存在を、応答の内容から推測できないようにする。加えて、実際のパスワードの検証を行わない場合
      (登録されていない・招待中・無効化済み・ロック中)でも、ユーザーを指定しないダミーの検証を、C11に追補する
      `dummyVerify(rawPassword)`で行ってから応答する(実際の検証と同じコストのハッシュ計算を、user-managementの、
      ハッシュ計算の同時実行数の上限を共有して行う)。これにより、(a)応答時間が、実際の検証を行った場合に近づき、
      (b)ハッシュ計算の上限を超えた場合の503(BR5.15)が、アカウントの存在・状態にかかわらず、すべての経路に等しく
      返り、503と401の違いから、存在やロックの状態を推測されることがない。
    category: policy
    applies_to: [Session, AccountLoginState]
    trigger: "ログイン失敗時"
    logic: "IF 認証に失敗(BR5.1の条件のいずれかを満たさない) THEN 実際の検証を行わなかった場合は、C11のdummyVerifyを行ったうえで、同一の401を返す。dummyVerifyが上限超過ならBR5.15の503を返す。"
    violation_behaviour: "N/A(不変条件)"
    source: "contract-summary.md C4(ロック中の試行も同一の汎用メッセージ), unit-of-work.md U5, functional-design-questions.md Q8, レビュー指摘R-03"

  - id: BR5.3
    statement: >
      アカウントのロックを、「先に試行の枠を確保してから検証する(予約型)」で扱い、しきい値を超える同時の試行や、
      ロックの解除後の再ロックの取りこぼしがないようにする。activeなユーザーのログイン試行では、パスワードの検証の前に、
      `AccountLoginState`に対して、次の1つの原子的な更新(なければ作成する、いわゆるupsert相当を含む)を行い、試行の枠を確保する。
      (1)`lockedUntil`が未来なら、確保しない(ロック中)。(2)`lockedUntil`が過去(ロックの解除済み)なら、
      `consecutiveFailures`を0に、`lockedUntil`を空に戻し、`generation`を1増やしたうえで、(3)へ進む。
      (3)`consecutiveFailures`がしきい値(既定5回)未満なら、1増やす。増やした結果がしきい値に達した場合は、
      **同じ更新の中で、`lockedUntil`も、現在時刻に設定値(既定15分)を加えた日時に設定する**(ロックを、失敗の確定を待たずに、
      枠の確保と原子的に有効にする)。(4)`consecutiveFailures`がしきい値以上で`lockedUntil`が空(通常は起こらない、
      想定外の状態)なら、`lockedUntil`を、現在時刻に設定値を加えた日時に設定して、確保しない(自己修復。
      永続的にロックされたままにならない)。確保できなかった場合は、検証せず(BR5.2の`dummyVerify`を行って)失敗の応答を返し、
      失敗回数を数えず、ロック期間も延長しない。確保できた場合は、確保した時点の`generation`と、この確保がロックを
      設定した場合の`lockedUntil`の値を、この試行の予約(reservation)として保持し、C11で検証する。検証が成功したら、
      `consecutiveFailures`を0に戻し、`lockedUntil`を空にし、`generation`を1増やす(正しいパスワードでの成功は、
      しきい値に達した試行であっても、ロックを解く)。検証が失敗したら、枠は、確保の時点で失敗として数えられている
      ため、追加の更新は要らない(ロックは、確保の時点で有効になっている)。ハッシュ計算の上限超過(503、BR5.15)の
      場合は、確保した枠を、次の**条件付きの補償の更新**で返す: `generation`が予約の時点と同じで、かつ
      `consecutiveFailures`が0より大きい場合に限り、`consecutiveFailures`を1戻し、`lockedUntil`が、この予約が設定した値と
      一致する場合に限り、`lockedUntil`を空に戻す。条件を満たさない場合(別の試行の成功によるリセット、ロックの
      解除後の新しい世代の予約など)は、何もしない(回数が負にならず、新しい世代の失敗を数え損ねない)。補償の更新が
      失敗した場合や、検証の途中の中断の場合は、確保した枠を失敗として数えたままにするが(安全側)、ロックは
      `lockedUntil`の経過で必ず自動的に解除されるため、状態が固定されて、永続的にロックされることはない。この方式により、
      同時の試行が何件あっても、1回のロック期間(またはロックの解除から次のロックまで)に検証できる試行は、しきい値の
      回数を超えない。登録されていないメールアドレス・招待中・無効化済みのユーザーへの試行は、失敗回数を記録しない
      (存在の有無を外部に漏らさないため、また、それらは成功しえないため)。予約の更新・成功の更新・補償の更新は、
      それぞれ短いトランザクションで行い、C11の呼び出しの間は、トランザクションを保持しない(資源の取得順序の不変条件)。
    category: policy
    applies_to: [AccountLoginState]
    trigger: "activeなユーザーへのログイン試行時"
    logic: "IF 枠を確保できない THEN 検証せずBR5.2の失敗応答。ELSE 検証する: 成功なら回数を0に戻し、ロックを解き、世代を進める。失敗なら追加の更新は無く(確保時点で失敗として数え、しきい値到達ならロックも有効)、応答はBR5.2の失敗応答。上限超過なら条件付きの補償の更新で枠を返して503。"
    violation_behaviour: "401 Unauthorized(BR5.2)、503(BR5.15)"
    source: "FR2.7, functional-design-questions.md Q2, レビュー指摘R-04・R-12・R-13"

  - id: BR5.4
    statement: >
      ロックの閾値(既定5回)とロック時間(既定15分)は、`application.yml`で設定する。管理画面からの編集UIは設けない。
      アクセストークン・リフレッシュトークンの有効期限(FR3.1)、および、更新済みのリフレッシュトークンの再送を許す猶予
      (BR5.6、既定10秒)も`application.yml`で設定する。要件定義書のFR2.7の文言(「管理画面から設定可能」)は、
      「`application.yml`で設定可能(管理画面での編集UIは設けない)」に修正する追補として、要件定義書
      (`inception/requirements-analysis/requirements.md`の末尾の追補節)に記録済みである(元の記述は残す)。設定値が不正な
      場合は、起動時にfail fastする(project.md Mandated)。不正とは、閾値・ロック時間・有効期限・猶予が0以下(猶予は
      0を許す)、または、アクセストークンの有効期限がリフレッシュトークンの有効期限を超えることである(超えると、
      アクセストークンが、Sessionの有効性(BR5.11)より長く有効になり、認証フィルタとC14の結果が食い違うため)。
    category: constraint
    applies_to: [AccountLoginState, Session]
    trigger: "アプリケーションの起動時、および設定値の参照時"
    logic: "IF 閾値・ロック時間・有効期限が0以下、または、アクセストークンの有効期限 > リフレッシュトークンの有効期限 THEN 起動を失敗させる。ELSE 設定値をログインと更新で用いる。"
    violation_behaviour: "起動失敗(設定定義の誤り)"
    source: "FR2.7, FR3.1, project.md Mandated(fail fast), functional-design-questions.md Q1, レビュー指摘R-08・R-11"

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
      (ローテーション)。手順は、提出されたリフレッシュトークンのハッシュで、有効な(BR5.11の定義)Sessionを見つけ、
      BR5.7・BR5.10の確認を通したうえで、新しいアクセストークンと新しいリフレッシュトークンを発行し、選択可能なロールと
      アクティブロールとあわせて返す(C4の追補)。Sessionの`previousRefreshTokenHash`に、それまでの`refreshTokenHash`を
      移し、`refreshTokenHash`・`lastRefreshedAt`・`refreshExpiresAt`(発行から設定値後)を、1つの条件付きの更新で
      更新する(同一のリフレッシュトークンの同時の更新では、1件だけが成功する)。すでに更新で無効にしたトークン
      (`previousRefreshTokenHash`と一致)が提出された場合は、経過時間で扱いを分ける。`lastRefreshedAt`から猶予(既定10秒)
      以内なら、正当な再送・複数タブ・同時実行による競合とみなし、401を返すだけで、Sessionは失効させない(手順5の
      条件付きの更新に負けた側も、この場合と同じ結果になる=到着の順序によらず、同じ状況では同じ挙動)。猶予を超えて
      いれば、無効なトークンの再使用(盗用の疑い)とみなし、Sessionを失効(revoked)させて401を返す。未知・期限切れ・失効済み
      のトークンは、いずれも同一の401を返す(区別しない)。401の応答の意味は、いずれの場合も「このリフレッシュトークンでは
      更新できない」であり、クライアントは、共有された最新のトークンで1回だけ再試行し、それでも401なら、セッションの
      終了として扱う(frontend-uiへの要求、追補一覧)。猶予内の401では、Sessionは生きているため、最新のトークンを
      失ったクライアントは、リフレッシュトークンの有効期限(最大30分)まで、再ログインするまで更新できない
      (トークンの再発行の応答の損失は、再ログインで解消する。[assumption]としてAssumptions & Open Questionsに記録)。
    category: policy
    applies_to: [Session]
    trigger: "POST /api/auth/refresh呼び出し時"
    logic: "IF ハッシュがrefreshTokenHashと一致し有効なSession THEN BR5.7・BR5.10の確認のあと、条件付きの更新でトークンを更新して200を返す(更新に負けたら401、Sessionは失効させない)。ELSE IF ハッシュがpreviousRefreshTokenHashと一致 THEN 猶予内なら401(Sessionはそのまま)、猶予を超えていればSessionをrevokedにして401。ELSE 401。"
    violation_behaviour: "401 Unauthorized(同一の応答)"
    source: "FR3.1, functional-design-questions.md Q5, レビュー指摘R-02"

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
      ログアウト(POST /api/auth/logout)は、有効なアクセストークン(BR5.11)を要し、そのsidに対応するSessionだけを
      失効(revoked)させ、同一ユーザーの他のSessionには影響しない。すでに失効済みのSessionへのログアウトは、認証フィルタ
      (BR5.11)で401になる(Sessionがrevokedのため)。アクセストークンの有効期限が切れている場合は、ログアウトは
      401になるため、フロントエンドは、先にリフレッシュしてからログアウトする(frontend-uiへの要求、追補一覧)。
      利用者が、期限切れのまま、ログアウトできなかった場合は、リフレッシュトークンが、有効期限(最大30分)まで有効な
      ままになる(残余リスクとして記録する)。
    category: policy
    applies_to: [Session]
    trigger: "ログイン成功時、およびPOST /api/auth/logout呼び出し時"
    logic: "ログイン成功: Session(active)を1件作る。ログアウト: 認証フィルタを通ったsidのSessionをrevokedにする。"
    violation_behaviour: "N/A(認証されていないログアウトは、BR5.11に従い401)"
    source: "FR3.2, contract-summary.md C4, レビュー指摘R-09"

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
      Sessionに保持されたアクティブロールが、ユーザーの現在の選択可能なロールと食い違ったまま残らないようにする
      (ロールの付け外し・削除の反映)。リフレッシュのたびに、C11から最新の選択可能なロールを取得して、アクティブロールを
      次のとおり再確認する。(1)アクティブロールが選択済みで、なお含まれるなら、そのまま。(2)選択済みで、含まれなくなって
      いたら、未選択(null)に戻し、残りのロールがちょうど1つなら、そのロールを自動選択する。(3)未選択で、ロールが
      ちょうど1つになっていたら(0個から1個、または複数から1個)、そのロールを自動選択する。(4)未選択で、0個または
      2個以上なら、未選択のまま。変更があった場合は、リフレッシュのレスポンスの`activeRoleId`で、クライアントに知らせる
      (BR5.6・C4の追補)。したがって、ロールを外されたユーザーが、旧ロールの権限で操作できる期間は、リフレッシュの間隔
      (アクセストークンの有効期限、最大10分)を超えない。
    category: policy
    applies_to: [Session]
    trigger: "POST /api/auth/refresh呼び出し時"
    logic: "最新の選択可能なロールに対して、Session.activeRoleIdを上記(1)〜(4)で更新する。"
    violation_behaviour: "N/A"
    source: "FR3.3, FR3.4, FR4.2, user-management nfr-design/security-design.md 残余リスク1, functional-design-questions.md Q4, レビュー指摘R-05・R-11"

  - id: BR5.11
    statement: >
      認証フィルタが、認証を必要とするすべてのリクエストで、次のとおりBearerトークンを検証し、操作者(`Operator`)を決める。
      検証の内容は、アクセストークンの署名と有効期限が正しく、`sid`のSessionが、有効(下記)であること。
      **Sessionが有効**であるとは、statusがactiveで、かつ`refreshExpiresAt`が経過していないことである。この定義は、
      認証フィルタ(本ルール)とC14の`getActiveRoleId`(BR5.13)で共通に用いる。いずれかを満たさない場合は、401を返す。
      認証を必要としないのは、ログイン(`/api/auth/login`)・リフレッシュ(`/api/auth/refresh`)・招待受諾
      (`/api/users/invitations/{token}/accept`)だけである。フィルタは、`/api/**`のすべてに適用され、認証を必要とするAPIには、
      この検証を経ない経路が無い。決めた操作者(userId・sessionId・アクティブロール)は、リクエストの処理中、各ユニットが
      読める(BR5.12)。アクティブロールは、リクエストごとにSessionから解決する(インメモリでキャッシュし、ロールの選択・
      更新・失効で無効化する)。
    category: authorization
    applies_to: [Session, Operator, AccessTokenClaims]
    trigger: "認証を必要とするAPIの呼び出し時"
    logic: "IF Bearerトークンが無い・署名不正・期限切れ・Sessionが存在しない・Sessionが無効(revokedまたはリフレッシュの有効期限の経過) THEN 401。ELSE Operator(userId=sub, sessionId=sid, activeRoleId=Sessionの値)を決めて処理を続ける。"
    violation_behaviour: "401 Unauthorized"
    source: "FR3.1, FR3.3, contract-summary.md 前提(認証), functional-design-questions.md Q4・Q9, レビュー指摘R-11"

  - id: BR5.12
    statement: >
      操作者(`Operator`)の受け渡しを、次のとおり、ユニットの依存を増やさない形で定める。`Operator`と、それを読む
      インタフェース(`OperatorContext`。リクエストの処理中の操作者を返す、読み取り専用)は、authentication-serviceの
      業務ロジックに依存しない、中立の共有契約(shared-schema、新しい契約C15、共通基盤の所有)として置く。
      authentication-serviceの認証フィルタは、この契約の値を設定する側(提供側の実装)で、他ユニット(schema-introspector・
      menu-navigation・audit-logging・user-management)は、この契約(型と読み取りインタフェース)だけを読む。したがって、
      user-managementは、authentication-serviceのコンポーネントを呼ばず、authentication-service→user-managementの
      依存(C11)と循環しない。アクティブロールが未選択(null)のまま、アクティブロールを要する操作が行われた場合は、
      認証エラー(401)ではなく、権限なし(403)として扱う(操作者が解決できない場合だけが401)。これは、ロールを持たない
      ユーザー(初期管理者を含む)が、ログインでき、permission-engineの判定の結果として拒否される、または(RBAC設定が
      空の間の例外である)設定インポート画面へ到達できるようにするためである。この判定の統一のため、
      permission-engineのC10を追補する: `canAccessScreen(activeRoleId, screenKey)`は、`activeRoleId`が空または
      null(未選択)のときは、「ロールを持たない」として扱い、fail closed(権限なし=NONE)で判定する。ただし、
      RBAC設定が1件もない間の例外(`config-import-export`)は、`activeRoleId`にかかわらず適用する(従来と同じ)。
      `resolveEffectivePermission`も、`activeRoleId`がnullなら、NONEを返す。各呼び出し元は、nullを自前で拒否せず
      (401にせず)、そのままC10へ渡す。あわせて、他ユニットの暫定の操作者取得(schema-introspectorの
      `ActiveRoleResolver`・`HeaderActiveRoleResolver`、user-managementの`CurrentOperatorProvider`・
      `HeaderCurrentOperatorProvider`、menu-navigation・audit-loggingが用いる同様のヘッダー方式)を、すべて削除し、
      `OperatorContext`を読む実装に置き換える。コントローラ・サービスの入口(`canAccessScreen`による認可の再検証)は
      変えない。ヘッダー(`X-User-Id`・`X-Active-Role-Id`)を信頼する経路は、どのプロファイルにも残さない。
    category: authorization
    applies_to: [Operator, Session]
    trigger: "アクティブロールを要する操作、および認証フィルタの導入時"
    logic: "IF Operator.activeRoleId=null THEN 操作者の解決自体は成功とし、C10へnullを渡し、権限なしとして判定する(拒否は403、RBAC空の間の例外は許可)。IF 操作者が解決できない THEN 401。"
    violation_behaviour: "403 Forbidden(アクティブロール未選択による権限なし)、401(操作者が解決できない)"
    source: "FR3.3, FR4.2, project.md Mandated(サーバー側の再検証), functional-design-questions.md Q7・Q9, レビュー指摘R-01・R-06"

  - id: BR5.13
    statement: >
      C14の`SessionContextApi.getActiveRoleId(sessionId)`は、Sessionのアクティブロールを返す(list-engine・
      record-edit-engineが、権限判定の引数として渡すため)。Sessionが存在しない場合は`SessionNotFoundException`、
      Sessionが有効でない場合(BR5.11の定義: revoked、またはリフレッシュの有効期限の経過)は`SessionExpiredException`を
      投げる。アクティブロールが未選択(null)の場合は、nullを返す(契約の追補。呼び出し元は、nullをそのままC10へ渡し、
      権限なしとして判定させる、BR5.12)。
    category: policy
    applies_to: [Session]
    trigger: "C14のgetActiveRoleId呼び出し時"
    logic: "IF Sessionが不存在 THEN SessionNotFoundException。ELSE IF Sessionが有効でない THEN SessionExpiredException。ELSE activeRoleId(nullを含む)を返す。"
    violation_behaviour: "N/A(Java例外。frontend-ui向けにはBearer認証失敗の401に変換される)"
    source: "FR4.2, contract-summary.md C14, functional-design-questions.md Q4・Q7, レビュー指摘R-06・R-11"

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
      C11の`verifyPasswordHash`と、ダミーの検証(C11に追補する`dummyVerify`、BR5.2)は、ハッシュ計算の同時実行数の上限を
      超えた場合に`HashCapacityExceededException`を投げる(user-managementのC11追補)。ログインは、この例外を握りつぶさず、
      原因を隠さず503(RFC 9457のProblemDetails)に変換する(リトライを促す)。実際の検証・ダミーの検証のどちらで
      あっても、上限を超えたら同じ503になるため、503の有無から、アカウントの存在やロックの状態を推測できない(BR5.2)。
      この場合は、失敗回数に数えず、確保した枠を、BR5.3の条件付きの補償の更新で返す。C11のメソッド(`findByEmail`・`findByUserId`・
      `verifyPasswordHash`・`dummyVerify`・`isDisabled`)は、トランザクションの外で呼ぶ(C11の追補。`verifyPasswordHash`の
      ログイン成功時のハッシュ更新は、C11の側が独立したトランザクションで行う)。
    category: policy
    applies_to: [AccountLoginState]
    trigger: "ログイン時にHashCapacityExceededExceptionが発生したとき"
    logic: "IF HashCapacityExceededException THEN 503を返し、確保した枠を返して、AccountLoginStateの失敗回数を増やさない。"
    violation_behaviour: "503 Service Unavailable"
    source: "user-management nfr-design/performance-design.md NFR1.3, contract-summary.md C11(追補), functional-design-questions.md Q8, レビュー指摘R-03"

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
| BR5.2 | policy | 失敗の応答の統一(同一の401)と、C11のdummyVerifyによる応答時間・503の均一化 |
| BR5.3 | policy | アカウントロック(予約型の原子的な更新、しきい値到達と同時にロックを有効化、条件付きの補償、5回・15分・自動解除) |
| BR5.4 | constraint | ロックの閾値・時間・トークンの有効期限・再送の猶予は`application.yml`で設定、設定値の検証、要件FR2.7の文言の追補 |
| BR5.5 | constraint | トークンの発行(JWTはsub/sid/iat/exp、リフレッシュは不透明なランダム値でハッシュのみ保存、鍵の扱い) |
| BR5.6 | policy | リフレッシュのローテーション、再送の猶予、無効になったトークンの再使用の検知、ロール情報の返却 |
| BR5.7 | policy | リフレッシュ時のisDisabledの確認(無効化の反映、引き込み型) |
| BR5.8 | policy | ログインごとのSession(複数端末の同時ログイン)、ログアウトは該当のSessionだけを失効 |
| BR5.9 | policy | アクティブロール(単一ロールは自動選択、複数は選択、保持していないロールは403) |
| BR5.10 | policy | リフレッシュ時のアクティブロールの再確認(外された・1つになった場合を含む) |
| BR5.11 | authorization | 認証フィルタ(Bearerの検証、Sessionの有効の定義、認証不要なAPIは3つだけ、Operatorの決定) |
| BR5.12 | authorization | Operatorの共有契約(C15)、アクティブロール未選択は403、C10のnullの扱い、暫定の操作者取得の置き換え |
| BR5.13 | policy | C14のgetActiveRoleId(例外とnullの扱い) |
| BR5.14 | constraint | パスワード・トークン・鍵の非出力、失敗の原因の区別を出さない |
| BR5.15 | policy | ハッシュ計算の上限超過は、実際・ダミーの検証のどちらでも同じ503(失敗に数えない)、C11はトランザクションの外 |
| BR5.16 | constraint | 無効化されたユーザーの扱いの分担(ログイン・リフレッシュで拒否、押し出し・ロック表示・復帰はMVP対象外) |
