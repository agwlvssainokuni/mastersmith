# Functional Design Questions — user-management (U4)

`inception/units-generation/unit-of-work.md`(U4定義)・`inception/domain-design/components.md`(UserManagementコンポーネント定義)・`inception/contract-design/contract-summary.md`(C5: user-management REST API、C11: UserAccountLookupApi)に基づき、user-managementユニットの機能設計(エンティティ・業務ルール・振る舞い仕様)を確定するための質問。

Domain Design・Contract Designで既に確定済みの事項(`User`/`UserPreference`の属性形状、C5の各エンドポイント、C11契約のメソッドシグネチャ、PermissionEngineへの同期依存、AuditLoggingへのイベント発行依存)は再確認しない。ここでは、それらの確定事項からは読み取れない、機能設計として具体化が必要な論点のみを問う。

なお、config-engine(U1)・permission-engine(U3)は実装済みで、audit-logging(U7、実装済み)の機能設計インタビュー(Q5=A)は「user-management実装時に、変更前後の値を含むイベントが発行されるようになれば、その時点で初めてbeforeValue/afterValueが非null値として記録されるようになる」という段階的充足を前提としています。本ユニットの設計はこの前提を踏まえます。

## Q1: パスワードハッシュ化アルゴリズム

FR2.6は「ハッシュ化して保存」を求めていますが、具体的なアルゴリズムは未確定です。

- A. BCrypt(Spring Securityの標準的な`BCryptPasswordEncoder`、実績が豊富で追加依存が不要)
- B. Argon2id(現時点でより推奨される鍵導出関数だが、Spring Securityでの利用に追加設定が必要)
- X. Other (please specify)

[Answer]: B

## Q2: 招待トークンの方式・有効期限

FR2.1は招待メールのリンクからの初回設定を求めていますが、トークンの生成方式・有効期限は未確定です。

- A. UUIDv4をトークンとして`User`テーブルに直接保持し(`invitationToken`カラム、`invited`状態の間のみ有効)、有効期限は設けない(初回ログインが完了する=`active`へ遷移するまで無期限に有効。運用上は管理者が無効化して再招待することで実質的に失効させる)
- B. UUIDv4トークン+発行から72時間の有効期限を設け、期限切れの場合は`POST /api/users/invitations/{token}/accept`が410 Goneを返す(C5契約への追補が必要)
- X. Other (please specify)

[Answer]: A

## Q3: user-managementが発行するドメインイベントの形状(beforeValue/afterValue含む)

audit-logging(U7)は「user-management実装時に変更前後の値を含むイベントが発行される」ことを前提に設計済みです。ユーザーの登録・更新・無効化(FR2.1〜FR2.3)時に発行するドメインイベント(`UserChangedEvent`)の`beforeValue`/`afterValue`はどう構成しますか(パスワードハッシュを含めないことは`project.md` Mandatedの前提)。

- A. `beforeValue`/`afterValue`は`{name, email, status, roleIds}`のスナップショット(`passwordHash`は含めない)とし、config-engineの`ConfigChangedEvent`と同様に変更前後の値を運ぶ構造化イベントとして実装する(audit-loggingが前提としていた段階的充足を今回のBoltで満たす)
- B. config-engine・permission-engineの先例(actorの意味論不一致等、既知のギャップとして許容)にならい、今回は`beforeValue`/`afterValue`を`null`のまま据え置き、変更前後の値の記録は将来のBoltに先送りする
- X. Other (please specify)

[Answer]: A

## Q4: ユーザー単位の表示設定(`UserPreference`)の既定値

新規ユーザー作成時、`theme`/`fontSize`/`locale`の既定値は未確定です(FR9.1, FR10.1)。

- A. `theme=light`, `fontSize=medium`, `locale=ja`をアプリケーション側の固定既定値とし、ユーザーが初回ログイン後に`PUT /api/me/preferences`で変更するまではこの既定値を返す(`UserPreference`レコード自体は招待受諾時に既定値で作成する)
- B. `UserPreference`レコードは作成せず、`GET /api/me/preferences`は未作成の場合にAと同じ固定既定値をレスポンスとして返すのみとし、`PUT`が呼ばれて初めてレコードを作成する(遅延作成)
- X. Other (please specify)

[Answer]: X. 招待受諾API(`POST /api/users/invitations/{token}/accept`)のリクエストボディに任意項目として`theme`/`fontSize`/`locale`を追加し(C5契約への追補)、ユーザーが招待受諾と同時に選択した値でUserPreferenceレコードを作成する。これらの項目が省略された場合は`theme=light`, `fontSize=medium`, `locale=ja`を既定値として使用する。

## Q5: `/api/me/preferences`の認可モデル(自分自身のみ操作可能)

C5契約の`GET/PUT /api/me/preferences`は「ユーザー単位」の設定であり、`/api/users`系(管理者向け、`canAccessScreen(activeRoleId, "user-management")`による認可)とは異なる認可モデルが必要です。

- A. `/api/me/preferences`は認証済み(有効なアクセストークンを持つ)であれば誰でも自分自身の設定を操作でき、`canAccessScreen`による画面レベルの権限判定は行わない(表示設定はロール権限とは独立した個人設定のため)
- B. `/api/me/preferences`も何らかのscreenKeyで`canAccessScreen`による権限判定を行う
- X. Other (please specify)

[Answer]: A

## Q6: ロール割当時の存在検証

`User.roleIds`へのロール割当(招待時・更新時、C5契約の`roleIds`パラメータ)時、指定された`roleId`がPermissionEngine側に実在するかの検証は行いますか。

- A. `PermissionEngineApi`(C10)に該当の検証メソッドが無いため、本Boltでは`roleId`の実在検証は行わない(不透明な文字列参照として受け入れる、他ユニットの`scopeRef`と同じ慣例)。存在しない`roleId`を割り当てた場合の実害は「その `roleId`に対応する権限が実質的に何も無い」だけであり、fail-fastすべき重大な不整合ではないと判断する
- B. `PermissionEngineApi`に新規の存在検証メソッド追加を提案し(Contract Design追補)、招待・更新時にfail fastで検証する
- X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

以下の内容で成果物(entities.md・rules.md・functional-spec.md・traceability.json)を生成しました。

- **entities.md**: User(status: invited/active/disabled、passwordHashはArgon2id、invitationToken無期限)、UserPreference(User 1:1、招待受諾時に作成)、UserChangedEvent(変更前後の値を含む)
- **rules.md**: BR4.1〜BR4.10(招待・受諾・更新・無効化・初期管理者自動作成・roleId実在検証・自分設定の認可・イベント発行・管理画面認可)
- **functional-spec.md**: W1〜W6のワークフロー、User.statusの状態遷移、Assumptions & Open Questions(招待メール送信失敗時の扱い、C11 revokeRefreshTokensOnDisableの呼び出し方向、C10/C5への契約追補が必要な旨)
- **traceability.json**: FR2.1〜FR2.6, FR2.8, FR9.1, FR10.1をBR4.xへ対応付け

[Answer]: Looks correct
