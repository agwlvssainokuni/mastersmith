# Contract Summary: MasterSmith MVP

units-generation/unit-of-work-dependency.mdに列挙された全境界(バックエンド間のプロセス内呼び出し10件、notification/audit-logのイベント購読6件、frontend-core/frontend-adminからバックエンドへのREST呼び出し8件)、およびU5(auth)/U6(account-management)間の共有スキーマ境界を対象に、contract-design-questions.md Q1〜Q13の回答に基づき契約を確定する。外部に公開するAPIは存在しない(Q1)。

## 共通規約

- **認証ヘッダー**: すべてのREST境界で`Authorization: Bearer <アクセストークン>`を必須とする。アクセストークンはJWT(HS256)で、標準クレームに加え`isAdmin`(boolean)と`roles`(割り当てられたロールID配列)を含む。`activeRoleId`はクレームとして持たない(Q5・Q6)。
- **作業中ロールヘッダー**: 複数ロール保有時の作業中ロール(FR5.4)は、リクエストごとに`X-Active-Role: <roleId>`ヘッダーで伝える。バックエンドは指定されたroleIdがアクセストークンの`roles`クレームに含まれるかを毎回検証し、含まれなければ403を返す(Q6・Q9)。このヘッダーが実際に権限判定へ使われるのは、業務データ権限モデル(FR5.1〜FR5.4)に基づく判定を行うdynamic-data-access(#12)のみである。管理系機能(schema-ingestion・config-management・permission管理系エンドポイント・account-management・audit-log)はisAdminクレームによる判定のみを用い、X-Active-Roleを要求しない。
- **トークン有効期限**: アクセストークンは短命(15分)、リフレッシュトークンは長命(7日)。アクセストークン期限切れは401で検知し、フロントエンドが`POST /api/auth/refresh`でリアクティブに再発行する(Q6・Q9)。ロール切替はトークン再発行を伴わない。
- **一覧系クエリパラメータ**: `page`(0始まりページ番号)・`size`・`sort`(`カラム名,asc|desc`)・テーブルごとに設定された検索条件パラメータ、という共通規約に統一する(Q7)。
- **エラー形式**: RFC 7807(Problem Details for HTTP APIs)形式のJSONボディ(`type`/`title`/`status`/`detail`/`errors`配列)。HTTPステータスコードの割り当て: 400=バリデーション/FK制約違反、401=未認証、403=権限不足、404=対象レコードなし、409=設定インポート不整合等の業務的競合、500=予期しないエラー(Q8)。
- **バージョニング**: 明示的なAPIバージョニング(`/v1/`等)は行わない。フロントエンドとバックエンドは常に同一ビルド・同一デプロイ単位のため、破壊的変更はビルド時に両者が同期して更新される(Q11)。
- **タイムアウト・リトライ**: サーキットブレーカー・リトライ等の耐障害性パターンは導入しない。REST境界は標準的なHTTPクライアントのデフォルトタイムアウト、業務DB接続はJDBC標準のタイムアウト設定のみとする(Q12)。
- **契約所有権**: 各契約は提供側(呼び出される側)のUnitが所有する。イベント契約は発行側(publisher)のUnitが所有する(Q10)。

## Contracts

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| 1 | schema-ingestion | config-management | in-process sync (Java method call) | schema-ingestion |
| 2 | config-management | dynamic-data-access | in-process sync (Java method call) | config-management |
| 3 | permission | dynamic-data-access | in-process sync (Java method call) | permission |
| 4 | auth | account-management | in-process sync (shared-schema access via auth's repository/service) | auth |
| 5 | config-management | audit-log | in-process async (Spring ApplicationEvent) | config-management |
| 6 | dynamic-data-access | audit-log | in-process async (Spring ApplicationEvent) | dynamic-data-access |
| 7 | auth | audit-log | in-process async (Spring ApplicationEvent) | auth |
| 8 | account-management | audit-log | in-process async (Spring ApplicationEvent) | account-management |
| 9 | account-management | notification | in-process async (Spring ApplicationEvent) | account-management |
| 10 | auth | notification | in-process async (Spring ApplicationEvent) | auth |
| 11 | auth | frontend-core | REST/HTTP+JSON | auth |
| 12 | dynamic-data-access | frontend-core | REST/HTTP+JSON | dynamic-data-access |
| 13 | permission | frontend-core | REST/HTTP+JSON | permission |
| 14 | schema-ingestion | frontend-admin | REST/HTTP+JSON | schema-ingestion |
| 15 | config-management | frontend-admin | REST/HTTP+JSON | config-management |
| 16 | permission | frontend-admin | REST/HTTP+JSON | permission |
| 17 | account-management | frontend-admin | REST/HTTP+JSON | account-management |
| 18 | audit-log | frontend-admin | REST/HTTP+JSON | audit-log |
| 19 | auth | account-management | shared-schema (Accountテーブル) | auth |

外部公開API(システム外の消費者)は存在しない(Q1)。行19は行4と同じUnitペアだが、境界の性質が異なる(行4はランタイムの呼び出し契約、行19は永続化スキーマの所有契約)ため別行として記載する。

## プロセス内同期呼び出し契約(#1〜#4)

Q2の回答により、同一JVM内のJavaメソッド呼び出しは意味レベルの記述に留め、具体的なメソッドシグネチャ(引数・戻り値の型)の確定はFunctional Design以降に委ねる。

```contract
# 1. config-management → schema-ingestion
Consumer passes: 業務DB接続情報(DbConnectionのconnectionId、またはアドホックな接続パラメータ)
Provider returns: 正規化されたスキーマ情報(テーブルごとのphysicalTableName・isView・主キー有無/構成・カラム名/型・外部キー)
Failure behavior: 接続失敗・認証失敗は例外として呼び出し元に伝播し、config-managementはこれをREST境界で400または500として応答する
```

```contract
# 2. dynamic-data-access → config-management
Consumer passes: テーブル識別子(tableId)
Provider returns: そのテーブルの有効な設定(検索条件・一覧表示項目・編集対象外項目・バリデーションルール・フォーム部品・外部キー関係・代表表示列)。設定キャッシュ(FR2.6)から取得し、DBへは問い合わせない
Failure behavior: 未設定のtableIdは例外(TableConfigNotFound相当)とし、dynamic-data-accessはこれをREST境界で404として応答する
```

```contract
# 3. dynamic-data-access → permission
Consumer passes: テーブル識別子(tableId)、対象アクション(list/view/create/edit/delete)、対象カラム名(カラム単位判定の場合)、判定対象ロールID(X-Active-Roleヘッダ由来)
Provider returns: 許可/不許可の判定結果(カラム単位の場合はaccessLevel: 更新可/表示のみ/非表示)
Failure behavior: 不許可の場合は例外とし、dynamic-data-accessはこれをREST境界で403として応答する
```

```contract
# 4. account-management → auth(共有スキーマ経由のリポジトリ/サービス呼び出し)
Consumer passes: Accountの作成データ(氏名・メールアドレス・初期ロール割り当て)、更新データ、または無効化対象のaccountId
Provider returns: 永続化されたAccountの現在状態(accountId・name・email・status・isAdmin)。account-managementはAccountテーブルへ直接アクセスせず、必ずこのインタフェース経由でアクセスする(#19参照)
Failure behavior: 存在しないaccountIdの操作は例外とし、account-managementはこれをREST境界で404として応答する
```

## 監査ログイベント契約(#5〜#8)

Q3の回答により、監査ログへの記録はSpringの`ApplicationEventPublisher`/`@EventListener`によるイベント連携として実現する。4つの発行元(config-management・dynamic-data-access・auth・account-management)は共通のイベント形状を発行し、audit-logがこれを購読する。「async」という記載は、監査ログの記録失敗が呼び出し元の主処理を止めないことを意味する。

**所有権の例外**: Contract Ownership Rulesの一般原則(イベント契約は発行側Unitが所有)に対し、本イベントは唯一の例外を持つ。共通エンベロープ形状(occurredAt/actorAccountId/actionType/targetDescription)の破壊的変更(フィールドの改名・削除等)は、複数の発行元(4 Unit)を横断する契約であるため、購読側であるaudit-logが唯一の所有者として最終決定権を持つ。各発行元が個別に所有するのは、自身の`actionType`語彙(追加的な列挙値の拡張)のみであり、これは加法的な変更として扱う(既存のenvelope形状を変更しない限り、audit-log側の合意なしに追加できる)。

```yaml
asyncapi: contract-lite
event: AuditableActionOccurredEvent
publishers: [config-management, dynamic-data-access, auth, account-management]
subscriber: audit-log
payload:
  occurredAt: string (ISO-8601 timestamp)
  actorAccountId: string, nullable # システム起因の操作はnull
  actionType: string # 発行元ごとの語彙。例:
    # config-management: CONFIG_TABLE_CREATED | CONFIG_TABLE_UPDATED | CONFIG_TABLE_DELETED | CONFIG_IMPORTED
    # dynamic-data-access: DATA_RECORD_CREATED | DATA_RECORD_UPDATED | DATA_RECORD_DELETED
    # auth: LOGIN_SUCCESS | LOGIN_FAILED | LOGIN_LOCKED | SELF_SERVICE_PROFILE_CHANGED
    # account-management: ACCOUNT_CREATED | ACCOUNT_UPDATED | ACCOUNT_DISABLED
  targetDescription: string # 人間可読な対象の説明(例: "table_config: products", "account: 42")
delivery: エンベロープ形状(occurredAt/actorAccountId/actionType/targetDescription)はaudit-logが唯一の所有者として固定する(所有権の例外、Contract Ownership Rules参照)。各発行元は自身のactionType語彙のみを追加的に所有・拡張できる
```

## 通知イベント契約(#9〜#10)

Q4の回答により、FR6.4の6種のメールフローに対応するイベントを今回確定する。account-managementはアカウント作成イベントのみを発行し、それ以外の5種はauthが発行する。

**失敗時の挙動**: SMTP送信失敗(接続不可・拒否等)はログに記録し、リトライは行わない(Q12の耐障害性方針を踏襲し、専用のリトライ機構は導入しない)。送信失敗によりイベント自体は失われるため、`PasswordResetRequestedEvent`(パスワード忘れ)・`EmailChangeRequestedEvent`(メールアドレス変更)のように利用者本人が再度操作を起点にできるフローは、利用者による再実行に委ねる。`AccountCreatedEvent`(管理者起点のアカウント作成通知)のように利用者自身が再実行できないフローについては、管理者が対象アカウントの状態を確認し、必要に応じてFunctional Design以降で確定する再送手段(例: 管理画面からの通知再送操作)に委ねる。

```yaml
asyncapi: contract-lite
publisher: account-management
subscriber: notification
messages:
  - name: AccountCreatedEvent
    payload:
      accountId: string
      recipientEmail: string
      registrationToken: string # メール本文の登録完了URLに埋め込む
---
asyncapi: contract-lite
publisher: auth
subscriber: notification
messages:
  - name: AccountRegistrationCompletedEvent
    payload:
      accountId: string
      recipientEmail: string
  - name: AccountInfoChangedEvent
    payload:
      accountId: string
      recipientEmail: string
      changedFields: array<string> # "name" | "password" | "email" のいずれか1つ以上
  - name: PasswordChangedEvent
    payload:
      accountId: string
      recipientEmail: string
  - name: PasswordResetRequestedEvent
    payload:
      accountId: string
      recipientEmail: string
      resetToken: string # メール本文のパスワード再設定URLに埋め込む
  - name: EmailChangeRequestedEvent
    payload:
      accountId: string
      newEmail: string # 変更先の新アドレス。メールはこの新アドレス宛に送信する(FR6.4(6))
      changeToken: string # メール本文の変更確定URLに埋め込む
```

## REST API契約(#11〜#18)

### schema-ingestion(#14: frontend-admin向け)

```yaml
openapi: 3.0.3
info:
  title: schema-ingestion API
  version: "1.0"
paths:
  /api/admin/schema-ingestion/preview:
    post:
      summary: 業務DBスキーマの取り込みプレビュー(FR1.1〜FR1.6)
      security: [{ bearerAuth: [] }]
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                connectionId: { type: string }
      responses:
        "200":
          description: 正規化されたスキーマ情報
          content:
            application/json:
              schema:
                type: object
                properties:
                  tables:
                    type: array
                    items:
                      type: object
                      properties:
                        physicalTableName: { type: string }
                        isView: { type: boolean }
                        primaryKeyColumns: { type: array, items: { type: string } }
                        columns:
                          type: array
                          items:
                            type: object
                            properties:
                              name: { type: string }
                              type: { type: string }
                              nullable: { type: boolean }
                        foreignKeys:
                          type: array
                          items:
                            type: object
                            properties:
                              column: { type: string }
                              referencedTable: { type: string }
                              referencedColumn: { type: string }
        "400": { description: "接続情報が不正 (RFC 7807)" }
        "403": { description: "管理者ロールなし (RFC 7807)" }
        "500": { description: "業務DB接続失敗等の予期しないエラー (RFC 7807)" }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### config-management(#15: frontend-admin向け)

```yaml
openapi: 3.0.3
info:
  title: config-management API
  version: "1.0"
paths:
  /api/admin/db-connections:
    get: { summary: "DB接続先設定の一覧 (FR2.1)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    post: { summary: "DB接続先設定の作成", security: [{ bearerAuth: [] }], responses: { "201": { description: Created }, "400": { description: "バリデーションエラー (RFC 7807)" } } }
  /api/admin/db-connections/{id}:
    put: { summary: "DB接続先設定の更新", security: [{ bearerAuth: [] }], responses: { "200": { description: OK }, "404": { description: "対象なし (RFC 7807)" } } }
    delete: { summary: "DB接続先設定の削除", security: [{ bearerAuth: [] }], responses: { "204": { description: "No Content" } } }
  /api/admin/menu-items:
    get: { summary: "メニュー構成の一覧(階層構造, FR2.4)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    post: { summary: "メニュー項目の作成", security: [{ bearerAuth: [] }], responses: { "201": { description: Created } } }
  /api/admin/menu-items/{id}:
    put: { summary: "メニュー項目の更新", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    delete: { summary: "メニュー項目の削除", security: [{ bearerAuth: [] }], responses: { "204": { description: "No Content" } } }
  /api/admin/table-configs:
    get:
      summary: "テーブル設定の一覧 (FR2.1〜FR2.2)"
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: page, in: query, schema: { type: integer } }
        - { name: size, in: query, schema: { type: integer } }
        - { name: sort, in: query, schema: { type: string } }
      responses: { "200": { description: OK } }
  /api/admin/table-configs/{tableId}:
    get: { summary: "テーブル設定の詳細(検索条件・一覧表示項目・編集対象外項目・バリデーション・フォーム部品・論理表示名・外部キー関係・代表表示列)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK }, "404": { description: "対象なし (RFC 7807)" } } }
    put: { summary: "テーブル設定の更新", security: [{ bearerAuth: [] }], responses: { "200": { description: OK }, "400": { description: "バリデーションエラー (RFC 7807)" } } }
  /api/admin/config/export:
    post: { summary: "設定全体のエクスポート (FR2.3)", security: [{ bearerAuth: [] }], responses: { "200": { description: "設定ファイル" } } }
  /api/admin/config/import:
    post:
      summary: "設定ファイルのインポート、不整合時は全体拒否 (FR2.3.1)"
      security: [{ bearerAuth: [] }]
      responses:
        "200": { description: "インポート成功(全項目適用)" }
        "409": { description: "不正形式・スキーマ不一致・必須項目欠落による不整合。設定全体を適用せずerrors配列で内容を返す (RFC 7807)" }
  /api/admin/config/cache/clear:
    post: { summary: "設定キャッシュの明示的クリア (FR2.6)", security: [{ bearerAuth: [] }], responses: { "204": { description: "No Content" } } }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### permission(#13: frontend-core向け, #16: frontend-admin向け — 同一API仕様を両者が消費する)

```yaml
openapi: 3.0.3
info:
  title: permission API
  version: "1.0"
paths:
  /api/me/roles:
    get:
      summary: "自身に割り当てられたロール一覧の取得(FR5.4)。ロールの「切替」自体はサーバー呼び出しを伴わない: この一覧から選んだroleIdを、以後のdynamic-data-access(#12)へのリクエストでX-Active-Roleヘッダーとして送るだけでよく、専用の切替エンドポイントは存在しない(意図的な設計、Q6参照)"
      security: [{ bearerAuth: [] }]
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                type: array
                items: { type: object, properties: { roleId: { type: string }, name: { type: string } } }
  /api/admin/roles:
    get: { summary: "ロール一覧 (FR5.1〜FR5.2)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    post: { summary: "ロールの作成", security: [{ bearerAuth: [] }], responses: { "201": { description: Created } } }
  /api/admin/roles/{roleId}/table-permissions:
    get: { summary: "テーブル単位権限の取得 (FR5.1)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    put: { summary: "テーブル単位権限の更新(list/view/create/edit/delete)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
  /api/admin/roles/{roleId}/column-permissions:
    get: { summary: "カラム単位権限の取得 (FR5.2)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    put: { summary: "カラム単位権限の更新(更新可/表示のみ/非表示)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
  /api/admin/groups:
    get: { summary: "グループ一覧", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    post: { summary: "グループの作成", security: [{ bearerAuth: [] }], responses: { "201": { description: Created } } }
  /api/admin/roles/{roleId}/assignments:
    post: { summary: "ロールのユーザ/グループへの割り当て (FR5.3)", security: [{ bearerAuth: [] }], responses: { "201": { description: Created }, "404": { description: "対象ユーザ/グループ/ロールなし (RFC 7807)" } } }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### dynamic-data-access(#12: frontend-core向け)

このAPIの全エンドポイントは、共通規約の`X-Active-Role`ヘッダー(作業中ロールID)を必須パラメータとして要求する。テーブル単位・カラム単位の権限判定(契約#3参照)はこのヘッダーの値を用いて行われる。

```yaml
openapi: 3.0.3
info:
  title: dynamic-data-access API
  version: "1.0"
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
  parameters:
    XActiveRole:
      name: X-Active-Role
      in: header
      required: true
      description: "作業中ロールID(FR5.4)。アクセストークンのrolesクレームに含まれるroleIdを指定する"
      schema: { type: string }
paths:
  /api/data/{tableId}:
    get:
      summary: "業務データの一覧(検索フォーム+ページング+ソート, FR3.1)。FK値は表示名解決済み (FR4.1)"
      security: [{ bearerAuth: [] }]
      parameters:
        - { $ref: "#/components/parameters/XActiveRole" }
        - { name: page, in: query, schema: { type: integer } }
        - { name: size, in: query, schema: { type: integer } }
        - { name: sort, in: query, schema: { type: string } }
      responses:
        "200": { description: OK }
        "403": { description: "テーブル単位のlist権限なし、またはX-Active-Roleがrolesクレームに含まれない (RFC 7807)" }
    post:
      summary: "業務データの新規作成 (FR3.3)。主キーのないテーブル・ビューは対象外"
      security: [{ bearerAuth: [] }]
      parameters:
        - { $ref: "#/components/parameters/XActiveRole" }
      responses:
        "201": { description: Created }
        "400": { description: "バリデーションエラー (RFC 7807)" }
        "403": { description: "create権限なし、またはX-Active-Roleがrolesクレームに含まれない (RFC 7807)" }
  /api/data/{tableId}/{recordId}:
    get:
      summary: "業務データの詳細(全カラム表示, FR3.2)"
      security: [{ bearerAuth: [] }]
      parameters: [{ $ref: "#/components/parameters/XActiveRole" }]
      responses: { "200": { description: OK }, "403": { description: "view権限なし、またはX-Active-Roleがrolesクレームに含まれない (RFC 7807)" }, "404": { description: "対象レコードなし (RFC 7807)" } }
    put:
      summary: "業務データの更新(後勝ち, FR3.5)"
      security: [{ bearerAuth: [] }]
      parameters:
        - { $ref: "#/components/parameters/XActiveRole" }
      responses:
        "200": { description: OK }
        "400": { description: "バリデーションエラー・FK制約違反 (RFC 7807)" }
        "403": { description: "edit権限なし、またはX-Active-Roleがrolesクレームに含まれない (RFC 7807)" }
        "404": { description: "対象レコードなし (RFC 7807)" }
  /api/data/{tableId}/fk-search/{columnName}:
    get:
      summary: "FK参照先レコードのポップアップ検索 (FR4.2)。カラムごとの絞り込み条件を付与可能"
      security: [{ bearerAuth: [] }]
      parameters: [{ $ref: "#/components/parameters/XActiveRole" }]
      responses: { "200": { description: OK } }
```

### auth(#11: frontend-core向け)

```yaml
openapi: 3.0.3
info:
  title: auth API
  version: "1.0"
paths:
  /api/auth/login:
    post:
      summary: "ID/パスワードによるログイン (FR6.1)。連続失敗n回でm秒ロック (FR6.6)"
      security: []
      responses:
        "200": { description: "accessToken/refreshTokenを返す" }
        "401": { description: "認証失敗 (RFC 7807)" }
        "403": { description: "ログイン試行回数超過によるロック中 (RFC 7807)" }
  /api/auth/refresh:
    post: { summary: "リフレッシュトークンによるアクセストークンの再発行 (FR6.3)", security: [], responses: { "200": { description: "新しいaccessTokenを返す" }, "401": { description: "リフレッシュトークン無効 (RFC 7807)" } } }
  /api/auth/logout:
    post: { summary: "ログアウト", security: [{ bearerAuth: [] }], responses: { "204": { description: "No Content" } } }
  /api/auth/forgot-password:
    post: { summary: "パスワード忘れ対応の開始 (FR6.4(5))", security: [], responses: { "202": { description: "Accepted" } } }
  /api/auth/reset-password:
    post: { summary: "トークンを用いたパスワード再設定の完了", security: [], responses: { "200": { description: OK }, "400": { description: "トークン無効・期限切れ (RFC 7807)" } } }
  /api/auth/complete-registration:
    post: { summary: "アカウント作成通知メールのURLからの登録完了(氏名・パスワード設定, FR6.4(1)→(2))", security: [], responses: { "200": { description: OK }, "400": { description: "トークン無効・期限切れ (RFC 7807)" } } }
  /api/me/profile:
    put: { summary: "自己サービスでの氏名・パスワード変更", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
  /api/me/email-change-request:
    post: { summary: "メールアドレス変更リクエスト(新アドレス宛に確認メール, FR6.4(6))", security: [{ bearerAuth: [] }], responses: { "202": { description: Accepted } } }
  /api/me/email-change-confirm:
    post: { summary: "新アドレス宛メール内URLからの変更確定(現在のパスワード入力)", security: [], responses: { "200": { description: OK }, "400": { description: "トークン無効・パスワード不一致 (RFC 7807)" } } }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### account-management(#17: frontend-admin向け)

```yaml
openapi: 3.0.3
info:
  title: account-management API
  version: "1.0"
paths:
  /api/admin/accounts:
    get: { summary: "利用者アカウントの一覧 (FR6.4.2)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    post: { summary: "利用者アカウントの新規作成(作成すると通知メール送信, FR6.4.1)", security: [{ bearerAuth: [] }], responses: { "201": { description: Created }, "400": { description: "バリデーションエラー (RFC 7807)" } } }
  /api/admin/accounts/{id}:
    get: { summary: "利用者アカウント詳細", security: [{ bearerAuth: [] }], responses: { "200": { description: OK }, "404": { description: "対象なし (RFC 7807)" } } }
    put: { summary: "利用者アカウント情報の編集 (FR6.4.3)", security: [{ bearerAuth: [] }], responses: { "200": { description: OK } } }
    delete: { summary: "利用者アカウントの無効化(論理削除のみ, FR6.4.4)", security: [{ bearerAuth: [] }], responses: { "204": { description: "No Content" } } }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### audit-log(#18: frontend-admin向け)

> 追記(Construction / audit-log Functional Designより): 保持日数の設定値管理(`/settings`)、エクスポート形式パラメータ(`format`)を追加した。いずれも加法的な変更であり、既存の操作を変更しない(Contract Ownership Rules参照)。

```yaml
openapi: 3.0.3
info:
  title: audit-log API
  version: "1.0"
paths:
  /api/admin/audit-log:
    get:
      summary: "監査ログの参照、条件絞り込み (FR7.3)"
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: page, in: query, schema: { type: integer } }
        - { name: size, in: query, schema: { type: integer } }
        - { name: sort, in: query, schema: { type: string } }
      responses: { "200": { description: OK } }
    delete:
      summary: "保持期間超過分の監査ログ削除 (FR7.5)。olderThanDaysはこの呼び出しで実際に使う削除基準日数。呼び出し元(frontend-admin)は/api/admin/audit-log/settingsの現在のretentionDaysをこのパラメータの初期値として画面に表示し、管理者はその場で値を上書きして一時的な基準日数で削除することもできる(Functional Design functional-design-questions.md Q1)"
      security: [{ bearerAuth: [] }]
      parameters: [{ name: olderThanDays, in: query, required: true, schema: { type: integer, minimum: 1 } }]
      responses:
        "204": { description: "No Content" }
        "400": { description: "olderThanDaysが1未満、または整数でない (RFC 7807)" }
  /api/admin/audit-log/export:
    get:
      summary: "監査ログのエクスポート (FR7.4)"
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: format, in: query, required: true, schema: { type: string, enum: [csv, json] } }
      responses:
        "200": { description: "エクスポートファイル(Content-Typeはformatに応じてtext/csvまたはapplication/json)" }
        "400": { description: "formatがcsv/json以外 (RFC 7807)" }
  /api/admin/audit-log/settings:
    get:
      summary: "監査ログ保持日数の設定値を取得(既定365、Functional Design functional-design-questions.md Q1)"
      security: [{ bearerAuth: [] }]
      responses: { "200": { description: "OK。retentionDaysを含む" } }
    put:
      summary: "監査ログ保持日数の設定値を更新"
      security: [{ bearerAuth: [] }]
      requestBody:
        content:
          application/json:
            schema: { type: object, properties: { retentionDays: { type: integer, minimum: 1 } } }
      responses:
        "200": { description: OK }
        "400": { description: "retentionDaysが1未満、または整数でない (RFC 7807)" }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

## 共有スキーマ契約(#19)

Q13の回答により、U5(auth)とU6(account-management)が共有するAccountエンティティの永続化スキーマを以下のとおり記述する。

```shared-schema
table: Account (内部H2データストア、専用接頭辞 ms_ を付与 [NFR5])
owner: auth (U5) — スキーマ定義・マイグレーションの唯一の所有者
consumer: account-management (U6) — Accountテーブルへ直接アクセスせず、authが公開するリポジトリ/サービスインタフェース(契約#4参照)経由でのみアクセスする
columns:
  - accountId: identifier
  - name: string
  - email: string
  - passwordHash: string # 不可逆ハッシュ [NFR7]
  - status: enum (active | disabled) # 論理削除フラグ相当 [FR6.4.4]
  - isAdmin: boolean # アクセストークンのisAdminクレームの発行元データ [FR5.5, FR5.6]
migration policy: スキーマ変更(列追加・型変更等)はauthが起点となり、account-managementはその変更に追従する。account-management側が新しい列を必要とする場合は、authへの変更提案としてauth側で反映する
```

## Contract Ownership Rules

- 各契約は提供側(呼び出される側)のUnitが所有する。イベント契約は発行側(publisher)のUnitが所有する(Q10)。
- 破壊的変更は所有Unitが起点となる。フロントエンドとバックエンドは常に同一ビルド・同一デプロイ単位のため、明示的なAPIバージョニングは行わず、破壊的変更はビルド時に消費側と同期して更新する(Q11)。
- 加法的な変更(新しいオプショナルフィールドの追加等)は、消費側が未知のフィールドを無視する前提で契約変更として扱わない。
- 監査ログイベント(#5〜#8)は「イベント契約は発行側Unitが所有する」という一般原則の例外である。共通エンベロープ形状はaudit-log(購読側)が唯一の所有者として固定し、4つの発行元はそれぞれ自身の`actionType`語彙のみを追加的に所有・拡張する。
- Accountの永続化スキーマ(#19)はauthが唯一の所有者であり、account-managementからの直接のテーブルアクセスは行わない。

## Open Questions

| Contract | Question | Blocks |
|---|---|---|
| #1(schema-ingestion→config-management) | 業務DB接続失敗時、リトライなしで即時500とする設計(Q12)が実運用で許容できるか、実装後の動作確認が必要 | Build and Test |
| #5〜#8(監査ログイベント) | `actionType`の具体的な列挙値の網羅性(各Unitがどの操作を記録対象とするか)は、Functional Designで各Unitの実装詳細と合わせて確定する | Functional Design |
| #4, #19(auth/account-management共有スキーマ) | Accountテーブルの列追加等の将来の変更提案フローの具体的な運用(開発者一人体制のため形式的な承認プロセスは不要と考えられるが、コミットメッセージ等での記録方法)は、CI Pipelineステージで確定する | CI Pipeline |

## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-05T22:55:34Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | contract-summary.md > 監査ログイベント契約(#5〜#8) セクションと Contract Ownership Rules | The conductor added a "所有権の例外" paragraph, restated it in the asyncapi block's delivery line, and added a matching bullet in Contract Ownership Rules, all stating audit-log (subscriber) is the sole owner of the shared envelope shape and each publisher owns only its own actionType vocabulary as an additive extension. All three locations now agree with each other and no longer contradict. | None. | Resolved |
| R-02 | Major | contract-summary.md > audit-log OpenAPI block (#18) | Verified with yaml.safe_load: the block now has a single /api/admin/audit-log path key with get and delete as sibling operations; both resolve correctly, and /api/admin/audit-log/export remains a separate key. No YAML key collision remains. | None. | Resolved |
| R-03 | Major | contract-summary.md > 通知イベント契約(#9〜#10) セクション | Added a "失敗時の挙動" paragraph stating SMTP failures are logged and not retried (consistent with Q12), with user-initiated flows relying on user re-trigger and admin-initiated AccountCreatedEvent explicitly deferring a resend mechanism to Functional Design. This states failure/delivery semantics comparable in specificity to the audit-log event contract. | None. | Resolved |
| R-04 | Minor | contract-summary.md > 共通規約 bullet and dynamic-data-access OpenAPI block (#12) | A reusable components.parameters.XActiveRole header parameter was added and $ref'd on every dynamic-data-access endpoint (GET/POST /api/data/{tableId}, GET/PUT /api/data/{tableId}/{recordId}, GET fk-search), and the common-conventions bullet was narrowed to state X-Active-Role is checked only by dynamic-data-access, not by the isAdmin-gated admin endpoints. Verified consistent with unit-of-work.md U3's note that permission's own model does not use isAdmin, and with U4's contract (#3) which passes the active-role-derived roleId into permission's decision call. No endpoint that lacks the header actually needs it, and no endpoint that needs it lacks the header. | None. | Resolved |
| R-05 | Minor | contract-summary.md > permission OpenAPI block (#13/#16), GET /api/me/roles summary | Added an inline note in the operation summary stating role switching requires no backend call under the reconciled X-Active-Role design (the frontend selects which roleId to send). This is consistent with the common-conventions X-Active-Role description and closes the prior appearance of a gap. | None. | Resolved |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| python3 yaml.safe_load_all over every fenced yaml block | All 9 blocks parse without error; audit-log block resolves both get and delete as sibling keys of one path | Confirms R-02 is fully resolved — no silent operation loss under strict YAML parsing |

### Summary

All 5 prior findings (3 Major, 2 Minor) are verified resolved with no new contradiction introduced: the audit-log ownership exception is now stated consistently in three places, the audit-log OpenAPI YAML is valid and preserves both operations, the notification contract now documents failure semantics, X-Active-Role is both declared where used and correctly scoped away from admin endpoints, and the missing role-switch endpoint is now explained as an intentional design consequence. No new Critical or Major issues were found in this pass.
