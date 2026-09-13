# Contract Summary — MasterSmith(マスタ管理アプリ)

`inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`・`inception/domain-design/components.md`・`inception/requirements-analysis/requirements.md`と`contract-design-questions.md`の確定回答に基づき、Unit間・外部との境界を正式な契約(Contract)として確定する。

## 前提(全契約に共通)

- **外部公開APIなし**: `requirements.md`の制約(既存システム連携なし、SSO/LDAP等の社内認証基盤との連携は対象外)により、本システムがシステム外部(他チーム・パートナー・パブリックインターネット)に公開するAPIは存在しない。すべての契約はシステム内部(ブラウザ↔バックエンド間、バックエンドユニット間)のみ(Q1=A)。ただし内部向けであっても簡易な取り決めで済ませず、標準のOpenAPI形式で正式に仕様化する(Q1回答の明示的な要望)。
- **連携方式**: frontend-ui↔各バックエンドユニットの境界はREST/HTTP(OpenAPI形式で契約化)、バックエンドユニット間(同一実行可能WAR内、同一プロセス)は直接メソッド呼び出し(Javaインタフェース契約、shared-schema形式で契約化)とする(Q2=A、Units Generationの決定を踏襲)。
- **契約所有権**: 各契約はプロバイダー側(提供側)ユニットが所有する。破壊的変更はコンシューマー側ユニットとの合意を要し、加法的変更(フィールド追加等)はコンシューマーが未知のフィールドを無視することで後方互換を保つ(Q3=A)。
- **バージョニング**: 内部・REST問わず一切のバージョン番号・URLパス予約を行わない(`/api/...`、`/api/v1/...`のような予約もしない)。単一の実行可能WARとして全ユニットが同時にビルド・デプロイされるため、破壊的変更が必要な場合はフロントエンドとバックエンドを同時にリリースすることで後方互換維持のコストを回避する(Q4=A、ユーザーの明示的な指定によりURLパス予約も省略)。
- **エラー形式**: frontend-ui向けREST APIのエラーは**RFC 9457**(Problem Details for HTTP APIs — 2023年7月発行、RFC 7807を正式にobsoleteした現行規格)形式で統一する。フィールド単位のバリデーションエラーは`errors`配列(`{ field, message }`)で返却する(project.mdの`## Mandated`に整合)。認証エラーは401、権限不足は403、リソース不在は404、バリデーションエラーは422とする。バックエンドユニット間(同一プロセス内呼び出し)はJava例外で表現し、タイムアウト・リトライの概念自体を適用しない。frontend-uiからのHTTP呼び出しはブラウザ標準のfetchタイムアウトに委ね、アプリケーション層での自動リトライは行わない(Q5=A、MVPスコープ)。
- **認証**: すべてのREST APIエンドポイント(ログインエンドポイント自体を除く)は、Unit U5(authentication-service)が発行するアクセストークンによるBearer認証を要求する。実効権限の再検証は各プロバイダーユニット(permission-engine呼び出し経由)がサーバー側で行う(project.mdの`## Mandated`「画面表示の出し分けだけに依存せず、必ずサーバー側で実効権限を再検証する」に整合)。
- **対象外**: `packaging`(U13)は実行時に稼働するAPIを持たないビルド成果物であるため、本ステージの契約対象外とする。

## Contracts Table

| # | Provider Unit | Consumer | Mechanism | Owner |
|---|---|---|---|---|
| C1 | list-engine (U10) | frontend-ui (U12) | REST/HTTP (OpenAPI) | list-engine |
| C2 | record-edit-engine (U11) | frontend-ui (U12) | REST/HTTP (OpenAPI) | record-edit-engine |
| C3 | menu-navigation (U6) | frontend-ui (U12) | REST/HTTP (OpenAPI) | menu-navigation |
| C4 | authentication-service (U5) | frontend-ui (U12) | REST/HTTP (OpenAPI) | authentication-service |
| C5 | user-management (U4) | frontend-ui (U12) | REST/HTTP (OpenAPI) | user-management |
| C6 | audit-logging (U7) | frontend-ui (U12) | REST/HTTP (OpenAPI) | audit-logging |
| C7 | config-import-export (U9) | frontend-ui (U12) | REST/HTTP (OpenAPI) | config-import-export |
| C8 | schema-introspector (U2) | frontend-ui (U12) | REST/HTTP (OpenAPI) | schema-introspector |
| C9 | config-engine (U1) | schema-introspector, permission-engine, data-import-export, list-engine, record-edit-engine, config-import-export | Javaインタフェース(shared-schema、同一プロセス内) | config-engine |
| C10 | permission-engine (U3) | user-management, menu-navigation, audit-logging, list-engine, record-edit-engine, config-import-export | Javaインタフェース(shared-schema、同一プロセス内) | permission-engine |
| C11 | user-management (U4) | authentication-service (U5) | Javaインタフェース(shared-schema、同一プロセス内) | user-management |
| C12 | menu-navigation (U6) | config-import-export (U9) | Javaインタフェース(shared-schema、同一プロセス内) | menu-navigation |
| C13 | data-import-export (U8) | list-engine, record-edit-engine | Javaインタフェース(shared-schema、同一プロセス内) | data-import-export |
| C14 | authentication-service (U5) | list-engine, record-edit-engine | Javaインタフェース(shared-schema、同一プロセス内) | authentication-service |

C14は、レビュー指摘R-01により追加した契約である。`unit-of-work-dependency.md`はlist-engine/record-edit-engineが「セッションのアクティブロール取得」のためauthentication-serviceへ同期依存すると明記しているが、当初のcontract-summary.mdにはC4(authentication-serviceのfrontend-ui向けREST契約)しか存在せず、この同一プロセス内境界が契約化されていなかった。C14はこの欠落を埋める。

C5とC11はいずれもuser-managementが提供するが、C5はfrontend-uiへの外部(ブラウザ)向けREST契約、C11はauthentication-serviceへの内部(同一プロセス)向けインタフェース契約であり、境界の性質が異なるため別契約として管理する。C3とC12も同様の関係(menu-navigation)。

## Per-Contract Spec

### C1: list-engine REST API(FR5)

```yaml
openapi: 3.0.3
info:
  title: List Engine API
  version: unversioned
paths:
  /api/tables/{tableConfigId}/records:
    get:
      summary: 一覧取得(検索・ページング・ソート)
      security: [{ bearerAuth: [] }]
      parameters:
        - name: tableConfigId
          in: path
          required: true
          schema: { type: string }
        - name: page
          in: query
          schema: { type: integer, default: 1 }
        - name: pageSize
          in: query
          schema: { type: integer, enum: [10, 20, 50, 100], default: 20 }
        - name: sort
          in: query
          schema: { type: string }
        - name: filter
          in: query
          description: 検索条件(主要条件+詳細検索)。実際のフィールド構成はconfig-engineの設定に依存する動的スキーマ
          schema: { type: object, additionalProperties: true }
      responses:
        "200":
          description: 一覧結果
          content:
            application/json:
              schema:
                type: object
                properties:
                  items: { type: array, items: { type: object, additionalProperties: true } }
                  totalCount: { type: integer }
                  page: { type: integer }
                  pageSize: { type: integer }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "422": { $ref: "#/components/responses/ValidationError" }
  /api/tables/{tableConfigId}/records/export:
    get:
      summary: 業務データCSVエクスポート(data-import-exportへ内部委譲、FR12.1)
      security: [{ bearerAuth: [] }]
      parameters:
        - name: tableConfigId
          in: path
          required: true
          schema: { type: string }
      responses:
        "200":
          description: CSVファイル
          content:
            text/csv: { schema: { type: string, format: binary } }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
  /api/tables/{tableConfigId}/records/bulk-delete:
    post:
      summary: 一覧チェックボックスによる一括削除
      security: [{ bearerAuth: [] }]
      requestBody:
        content:
          application/json:
            schema: { type: object, properties: { ids: { type: array, items: { type: string } } } }
      responses:
        "204": { description: 削除成功 }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
components:
  responses:
    Unauthorized:
      description: 認証エラー(RFC 9457)
      content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } }
    Forbidden:
      description: 権限不足(RFC 9457)
      content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } }
    ValidationError:
      description: バリデーションエラー(RFC 9457、フィールド単位)
      content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } }
  schemas:
    ProblemDetails:
      type: object
      description: RFC 9457準拠
      properties:
        type: { type: string }
        title: { type: string }
        status: { type: integer }
        detail: { type: string }
        instance: { type: string }
        errors:
          type: array
          items:
            type: object
            properties:
              field: { type: string }
              message: { type: string }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

**エラー・境界動作**: 該当データがない場合は`items: []`かつ200(FR5.4)。検索・一覧取得に失敗した場合は500系だがフロントエンドはインラインエラー表示に変換する。CREATE権限がない場合、C2(record-edit-engine)が所有する新規作成エンドポイント(`POST /api/tables/{tableConfigId}/records`)は403を返す(フロントエンド側はFR5.3により本Unit(list-engine)の一覧画面で「+ 新規作成」ボタン自体を表示しない。新規作成エンドポイント自体の定義はC2を参照)。

### C2: record-edit-engine REST API(FR6)

```yaml
openapi: 3.0.3
info:
  title: Record Edit Engine API
  version: unversioned
paths:
  /api/tables/{tableConfigId}/records/{recordId}:
    get:
      summary: 詳細取得
      security: [{ bearerAuth: [] }]
      responses:
        "200": { description: 詳細データ, content: { application/json: { schema: { type: object, additionalProperties: true } } } }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { $ref: "#/components/responses/NotFound" }
    put:
      summary: 更新(楽観ロック競合検出、FR6.3)
      security: [{ bearerAuth: [] }]
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                fields: { type: object, additionalProperties: true }
                optimisticLockVersion:
                  description: 楽観ロック対象列が存在するテーブルのみ必須。存在しない場合は省略可(後勝ち)
                  type: string
                  nullable: true
      responses:
        "200": { description: 更新成功 }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "409":
          description: 楽観ロック競合(FR6.3、画面上部にインラインバナー表示・一覧へは遷移しない)
          content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } }
        "422": { $ref: "#/components/responses/ValidationError" }
    delete:
      summary: 削除
      security: [{ bearerAuth: [] }]
      responses:
        "204": { description: 削除成功 }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
  /api/tables/{tableConfigId}/records:
    post:
      summary: 新規作成
      security: [{ bearerAuth: [] }]
      requestBody:
        content: { application/json: { schema: { type: object, properties: { fields: { type: object, additionalProperties: true } } } } }
      responses:
        "201": { description: 作成成功 }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "422": { $ref: "#/components/responses/ValidationError" }
  /api/tables/{tableConfigId}/records/import:
    post:
      summary: 業務データCSVインポート(data-import-exportへ内部委譲、FR12.1、行単位バリデーション)
      security: [{ bearerAuth: [] }]
      requestBody:
        content: { multipart/form-data: { schema: { type: object, properties: { file: { type: string, format: binary } } } } }
      responses:
        "200":
          description: インポート結果(行単位のバリデーションエラーを含む)
          content:
            application/json:
              schema:
                type: object
                properties:
                  successCount: { type: integer }
                  errors:
                    type: array
                    items: { type: object, properties: { row: { type: integer }, message: { type: string } } }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
components:
  responses:
    Unauthorized: { description: 認証エラー(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    Forbidden: { description: 権限不足(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    NotFound: { description: リソース不在(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    ValidationError: { description: バリデーションエラー(RFC 9457、フィールド単位), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    ProblemDetails:
      type: object
      properties:
        type: { type: string }
        title: { type: string }
        status: { type: integer }
        detail: { type: string }
        errors: { type: array, items: { type: object, properties: { field: { type: string }, message: { type: string } } } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

**楽観ロック契約の要点(レビュー指摘R-02対応済みの依存方向を踏まえる)**: record-edit-engineはconfig-engine(C9経由)から対象テーブルの楽観ロック対象列(更新日時/バージョン列)の有無を取得し、存在する場合のみ`optimisticLockVersion`の不一致を409として検出する。存在しない場合は`optimisticLockVersion`を無視し後勝ちで保存する。

### C3: menu-navigation REST API(FR7)

```yaml
openapi: 3.0.3
info:
  title: Menu Navigation API
  version: unversioned
paths:
  /api/menu:
    get:
      summary: 業務メニュー・管理メニューの階層構成取得(権限フィルタ済み、FR7.1〜FR7.3)
      security: [{ bearerAuth: [] }]
      responses:
        "200":
          description: メニュー階層(権限のあるメニューが1件もない場合はitems空配列、FR7.3はフロントエンド側の案内表示)
          content:
            application/json:
              schema:
                type: object
                properties:
                  businessMenu: { type: array, items: { $ref: "#/components/schemas/MenuItem" } }
                  adminMenu: { type: array, items: { $ref: "#/components/schemas/MenuItem" } }
        "401": { $ref: "#/components/responses/Unauthorized" }
components:
  responses:
    Unauthorized: { description: 認証エラー(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    MenuItem:
      type: object
      properties:
        menuItemId: { type: string }
        label: { type: string }
        targetTableConfigId: { type: string, nullable: true }
        children: { type: array, items: { $ref: "#/components/schemas/MenuItem" } }
    ProblemDetails:
      type: object
      properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### C4: authentication-service REST API(FR2.7, FR3, FR4.2)

```yaml
openapi: 3.0.3
info:
  title: Authentication Service API
  version: unversioned
paths:
  /api/auth/login:
    post:
      summary: ログイン(ID/パスワード認証、失敗時ロックアウト判定)
      security: []
      requestBody:
        content: { application/json: { schema: { type: object, properties: { email: { type: string }, password: { type: string } } } } }
      responses:
        "200":
          description: 認証成功(アクセストークン10分・リフレッシュトークン30分、application.ymlで設定可能)
          content:
            application/json:
              schema:
                type: object
                properties:
                  accessToken: { type: string }
                  refreshToken: { type: string }
                  roles: { type: array, items: { type: string } }
        "401":
          description: 認証失敗(ロック中の試行も同一の汎用メッセージを返しロック状態を外部に漏らさない)
          content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } }
  /api/auth/refresh:
    post:
      summary: アクセストークンの再発行
      security: []
      requestBody:
        content: { application/json: { schema: { type: object, properties: { refreshToken: { type: string } } } } }
      responses:
        "200": { description: 再発行成功, content: { application/json: { schema: { type: object, properties: { accessToken: { type: string } } } } } }
        "401": { $ref: "#/components/responses/Unauthorized" }
  /api/auth/logout:
    post:
      summary: ログアウト(リフレッシュトークン失効)
      security: [{ bearerAuth: [] }]
      responses:
        "204": { description: ログアウト成功 }
  /api/auth/active-role:
    put:
      summary: 複数ロールを持つユーザーのアクティブロール選択(FR4.2、単一ロールの場合は本APIは呼び出されない)
      security: [{ bearerAuth: [] }]
      requestBody:
        content: { application/json: { schema: { type: object, properties: { roleId: { type: string } } } } }
      responses:
        "200": { description: 選択成功(以降のセッションで保持) }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { description: 保持していないロールの指定, content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
components:
  responses:
    Unauthorized: { description: 認証エラー(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    ProblemDetails:
      type: object
      properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

**既知の未解消フォローアップ(unit-of-work.md U5引継ぎ事項)**: ログイン失敗ロックアウトの閾値・ロック時間は要件定義書FR2.7では「管理画面から設定可能」とされているが、本契約はDomain Design/Units Generationで確定した`application.yml`方式を前提とする。この不一致は要件定義書側の文言修正待ちであり、Construction(機能設計)で必ず解消すること。

### C5: user-management REST API(FR2, FR9, FR10.1)

```yaml
openapi: 3.0.3
info:
  title: User Management API
  version: unversioned
paths:
  /api/users:
    get:
      summary: ユーザー一覧取得(管理者向け)
      security: [{ bearerAuth: [] }]
      responses:
        "200": { description: ユーザー一覧, content: { application/json: { schema: { type: array, items: { $ref: "#/components/schemas/User" } } } } }
        "403": { $ref: "#/components/responses/Forbidden" }
    post:
      summary: ユーザー招待(FR2.1、招待メール送信)
      security: [{ bearerAuth: [] }]
      requestBody:
        content: { application/json: { schema: { type: object, properties: { email: { type: string }, name: { type: string }, roleIds: { type: array, items: { type: string } } } } } }
      responses:
        "201": { description: 招待成功 }
        "403": { $ref: "#/components/responses/Forbidden" }
        "422": { $ref: "#/components/responses/ValidationError" }
  /api/users/{userId}:
    put:
      summary: ユーザー情報更新(FR2.2)
      security: [{ bearerAuth: [] }]
      responses:
        "200": { description: 更新成功 }
        "403": { $ref: "#/components/responses/Forbidden" }
    delete:
      summary: ユーザー無効化(FR2.3、リフレッシュトークン即時失効)
      security: [{ bearerAuth: [] }]
      responses:
        "204": { description: 無効化成功 }
        "403": { $ref: "#/components/responses/Forbidden" }
  /api/users/invitations/{token}/accept:
    post:
      summary: 招待メールリンクからの初回パスワード・氏名設定(FR2.1)
      security: []
      requestBody:
        content: { application/json: { schema: { type: object, properties: { password: { type: string, minLength: 8 }, name: { type: string } } } } }
      responses:
        "200": { description: 初回ログイン設定成功 }
        "422": { $ref: "#/components/responses/ValidationError" }
  /api/me/preferences:
    get:
      summary: ユーザー単位の表示設定取得(テーマ/フォントサイズ/言語、FR9.1, FR10.1)
      security: [{ bearerAuth: [] }]
      responses:
        "200": { description: 表示設定, content: { application/json: { schema: { $ref: "#/components/schemas/UserPreference" } } } }
    put:
      summary: ユーザー単位の表示設定更新
      security: [{ bearerAuth: [] }]
      requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/UserPreference" } } } }
      responses:
        "200": { description: 更新成功 }
        "422": { $ref: "#/components/responses/ValidationError" }
components:
  responses:
    Forbidden: { description: 権限不足(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    ValidationError: { description: バリデーションエラー(RFC 9457、フィールド単位)、パスワードは平文でエラーメッセージに出力しない, content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    User:
      type: object
      properties: { userId: { type: string }, name: { type: string }, email: { type: string }, status: { type: string, enum: [active, invited, disabled] }, roleIds: { type: array, items: { type: string } } }
    UserPreference:
      type: object
      properties: { theme: { type: string, enum: [light, dark] }, fontSize: { type: string, enum: [large, medium, small] }, locale: { type: string, enum: [ja, en] } }
    ProblemDetails:
      type: object
      properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string }, errors: { type: array, items: { type: object, properties: { field: { type: string }, message: { type: string } } } } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### C6: audit-logging REST API(FR8.4)

```yaml
openapi: 3.0.3
info:
  title: Audit Logging API
  version: unversioned
paths:
  /api/audit-log:
    get:
      summary: 監査ログ閲覧(管理メニュー、権限判定はpermission-engineへ委譲)
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: page, in: query, schema: { type: integer, default: 1 } }
        - { name: pageSize, in: query, schema: { type: integer, default: 20 } }
        - { name: targetType, in: query, schema: { type: string } }
      responses:
        "200":
          description: 監査ログ一覧(追記専用ストア、更新・削除APIは存在しない)
          content:
            application/json:
              schema:
                type: object
                properties:
                  items: { type: array, items: { $ref: "#/components/schemas/AuditLogEntry" } }
                  totalCount: { type: integer }
        "403": { $ref: "#/components/responses/Forbidden" }
components:
  responses:
    Forbidden: { description: 権限不足(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    AuditLogEntry:
      type: object
      properties:
        auditLogEntryId: { type: string }
        actorUserId: { type: string }
        targetType: { type: string }
        targetId: { type: string }
        operationType: { type: string }
        occurredAt: { type: string, format: date-time }
        beforeValue: { type: object, additionalProperties: true, nullable: true }
        afterValue: { type: object, additionalProperties: true, nullable: true }
    ProblemDetails:
      type: object
      properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

**不変条件**: 本APIはGET(閲覧)のみを公開する。PUT/PATCH/DELETEエンドポイントは意図的に実装しない(FR8.2「アプリケーションからのUPDATE/DELETE経路を持たない追記専用」を契約レベルで強制する)。

### C7: config-import-export REST API(FR11)

```yaml
openapi: 3.0.3
info:
  title: Config Import/Export API
  version: unversioned
paths:
  /api/config/export:
    get:
      summary: 設定一式(スキーマ定義・メニュー構成・RBAC設定)のJSONエクスポート(FR11.1)
      security: [{ bearerAuth: [] }]
      responses:
        "200": { description: 設定一式JSON, content: { application/json: { schema: { type: object, additionalProperties: true } } } }
        "403": { $ref: "#/components/responses/Forbidden" }
  /api/config/import:
    post:
      summary: 設定一式のJSONインポート(確認モーダル経由、fail fast検証、FR1.3・FR11.1)
      security: [{ bearerAuth: [] }]
      requestBody: { content: { application/json: { schema: { type: object, additionalProperties: true } } } }
      responses:
        "200": { description: インポート成功(内部設定DBへ反映) }
        "403": { $ref: "#/components/responses/Forbidden" }
        "422":
          description: 設定定義の誤り(fail fast、必須プロパティ欠落等)。開発者向けスタックトレースではなくフィールド単位のエラーで返す
          content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } }
components:
  responses:
    Forbidden: { description: 権限不足(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    ProblemDetails:
      type: object
      properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string }, errors: { type: array, items: { type: object, properties: { field: { type: string }, message: { type: string } } } } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

### C8: schema-introspector REST API(FR1.4、レビュー指摘R-01対応)

```yaml
openapi: 3.0.3
info:
  title: Schema Introspector API
  version: unversioned
paths:
  /api/config/schema-introspection:
    post:
      summary: >
        対象RDBMSのメタデータ(テーブル/カラム/型/NULL可否/主キー/外部キー等)を読み取り、
        config-engineへ設定の初期ドラフトを書き込む(FR1.4)。設定管理画面の「スキーマからドラフト生成」操作から呼び出される起動経路(Unit Generationレビュー指摘R-01で追加確定)。
      security: [{ bearerAuth: [] }]
      requestBody:
        content: { application/json: { schema: { type: object, properties: { schemaName: { type: string }, tableNames: { type: array, items: { type: string }, nullable: true, description: "省略時は対象スキーマ内の全テーブル" } } } } }
      responses:
        "200":
          description: 生成された初期ドラフト(config-engineへの書き込み結果)
          content: { application/json: { schema: { type: object, properties: { generatedTableConfigIds: { type: array, items: { type: string } } } } } }
        "403": { $ref: "#/components/responses/Forbidden" }
        "422": { $ref: "#/components/responses/ValidationError" }
components:
  responses:
    Forbidden: { description: 権限不足(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    ValidationError: { description: 対象DB接続エラー等(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    ProblemDetails:
      type: object
      properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

**外部ツール不使用の制約**: FR1.4・`components.md`により、本APIはアプリ本体に統合された機能としてのみ提供され、CLIや外部ツールとしては提供しない。

### C9: config-engine 内部インタフェース契約

```yaml
shared-schema:
  interface: ConfigEngineApi
  package: com.mastersmith.config
  consumers: [schema-introspector, permission-engine, data-import-export, list-engine, record-edit-engine, config-import-export]
  methods:
    - name: getTableConfig
      params: { schemaName: string, tableName: string }
      returns: TableConfig
      throws: [TableConfigNotFoundException]
    - name: getColumnConfigs
      params: { tableConfigId: string }
      returns: "List<ColumnConfig>"
    - name: getOptimisticLockColumn
      description: 対象テーブルの楽観ロック対象列(更新日時/バージョン列)の有無を返す
      params: { tableConfigId: string }
      returns: "Optional<String>"
    - name: writeTableConfigDraft
      description: schema-introspector専用。既存設定がある場合は上書きしない(fail fastではなくスキップ)
      params: { draft: TableConfigDraft }
      returns: "List<String> (generated tableConfigIds)"
    - name: getExportableConfigSet
      description: config-import-export専用。スキーマ・カラム設定一式を返す
      returns: ConfigExportSet
    - name: importConfigSet
      description: >
        config-import-export専用。設定定義に誤りがある場合はConfigValidationExceptionをfail fastで送出する(FR1.3)。
      params: { configSet: ConfigImportSet }
      throws: [ConfigValidationException]
  types:
    TableConfig: { tableConfigId: string, schemaName: string, tableName: string, displayName: string, displayOrder: int, optimisticLockColumn: "string | null" }
    ColumnConfig: { columnConfigId: string, tableConfigId: string, columnName: string, displayName: string, displayOrder: int, format: string, editorType: string, validationRule: string, visibility: string }
  exceptions:
    - name: TableConfigNotFoundException
      httpMapping: N/A(内部呼び出し、Java例外)
    - name: ConfigValidationException
      description: 設定定義自体の誤り(必須プロパティ欠落等)。fail fastで起動時・設定読込時・インポート時に検知する(project.md Mandated)
```

### C10: permission-engine 内部インタフェース契約

```yaml
shared-schema:
  interface: PermissionEngineApi
  package: com.mastersmith.permission
  consumers: [user-management, menu-navigation, audit-logging, list-engine, record-edit-engine, config-import-export]
  methods:
    - name: resolveEffectivePermission
      description: >
        ロール階層継承後の実効権限を再検証する。画面表示の出し分けだけに依存してはならず、
        全コンシューマーは必ず本メソッド経由でサーバー側検証を行う(project.md Mandated)。
      params: { activeRoleId: string, scopeType: "schema|table|column", scopeRef: string }
      returns: "EffectivePermission { level: FULL|READ|NONE, canCreate: boolean, canDelete: boolean }"
    - name: canAccessScreen
      description: 画面(ユーザ管理・監査ログ閲覧・メニュー項目)へのアクセス可否判定
      params: { activeRoleId: string, screenKey: string }
      returns: boolean
    - name: assignPermission
      description: 権限管理者による明示的操作でのみ呼び出し可能。権限昇格(自分自身への昇格含む)を防止する(project.md Forbidden)
      params: { roleId: string, scopeType: string, scopeRef: string, level: string }
      throws: [PermissionEscalationException]
  types:
    EffectivePermission: { level: "FULL|READ|NONE", canCreate: boolean, canDelete: boolean }
  exceptions:
    - name: PermissionEscalationException
      description: 権限管理者の明示的操作を経ない権限昇格の試行(project.md Forbidden)
```

### C11: user-management → authentication-service 内部インタフェース契約

```yaml
shared-schema:
  interface: UserAccountLookupApi
  package: com.mastersmith.usermanagement
  consumers: [authentication-service]
  note: >
    C5(user-management REST API)とは異なる境界。frontend-uiからの直接呼び出しではなく、
    authentication-serviceがログイン処理・ロック判定のために同一プロセス内で呼び出す内部契約。
  methods:
    - name: findByEmail
      params: { email: string }
      returns: "Optional<UserAccount>"
    - name: verifyPasswordHash
      params: { userId: string, rawPassword: string }
      returns: boolean
      note: パスワードは平文でログ・監査ログ・エラーメッセージに出力しない(project.md Mandated)
    - name: isDisabled
      params: { userId: string }
      returns: boolean
    - name: revokeRefreshTokensOnDisable
      description: ユーザー無効化と同時にリフレッシュトークンを即時失効させるためのフック(FR2.3)
      params: { userId: string }
      returns: void
  types:
    UserAccount: { userId: string, passwordHash: string, status: "active|invited|disabled", roleIds: "List<string>" }
```

### C12: menu-navigation → config-import-export 内部インタフェース契約

```yaml
shared-schema:
  interface: MenuStructureApi
  package: com.mastersmith.menu
  consumers: [config-import-export]
  note: C3(menu-navigation REST API)とは異なる境界。config-import-exportが設定一式のexport/import対象にメニュー構成を含めるための内部契約。
  methods:
    - name: getExportableMenuStructure
      returns: "List<MenuItem>"
    - name: importMenuStructure
      params: { items: "List<MenuItem>" }
      throws: [ConfigValidationException]
  types:
    MenuItem: { menuItemId: string, parentMenuItemId: "string | null", label: string, order: int, targetTableConfigId: "string | null" }
```

### C13: data-import-export 内部インタフェース契約

```yaml
shared-schema:
  interface: DataImportExportApi
  package: com.mastersmith.dataio
  consumers: [list-engine, record-edit-engine]
  methods:
    - name: exportCsv
      params: { tableConfigId: string }
      returns: "InputStream (CSV)"
    - name: importCsv
      description: 行単位のバリデーションエラーを収集して返す(全体を即時失敗にはしない)
      params: { tableConfigId: string, file: "InputStream (CSV)" }
      returns: "ImportResult { successCount: int, errors: List<RowError> }"
  types:
    RowError: { row: int, message: string }
```

### C14: authentication-service → list-engine, record-edit-engine 内部インタフェース契約(レビュー指摘R-01対応)

```yaml
shared-schema:
  interface: SessionContextApi
  package: com.mastersmith.auth
  consumers: [list-engine, record-edit-engine]
  note: >
    C4(authentication-service REST API)とは異なる境界。frontend-uiからの直接呼び出しではなく、
    list-engine・record-edit-engineがリクエスト処理時にセッションのアクティブロールを読み取り、
    permission-engine(C10)呼び出しの引数として渡すために同一プロセス内で呼び出す内部契約
    (`unit-of-work-dependency.md`統合ポイント表・`components.md`のAuthenticationServiceコンポーネント定義を根拠とする)。
  methods:
    - name: getActiveRoleId
      description: >
        現在のリクエストに紐づくセッションのアクティブロールIDを返す(FR4.2)。
        authentication-serviceはPermissionEngineを直接呼び出さず、あくまで呼び出し元
        (list-engine/record-edit-engine)がこのメソッドで取得したロールIDをpermission-engineへの
        引数として渡す(components.mdのAuthenticationServiceの振る舞い記述を踏襲)。
      params: { sessionId: string }
      returns: string
      throws: [SessionNotFoundException, SessionExpiredException]
  exceptions:
    - name: SessionNotFoundException
      httpMapping: N/A(内部呼び出し、Java例外。frontend-ui向けにはC4のBearer認証失敗として401に変換される)
    - name: SessionExpiredException
      httpMapping: N/A(内部呼び出し、Java例外)
```

## Contract Ownership Rules

- 各契約(REST/インタフェースいずれも)は**プロバイダー側ユニットが所有**する。コンシューマー側ユニットは契約の変更を提案できるが、確定はプロバイダー側の合意を要する(`contract-design-questions.md` Q3=A)。
- **破壊的変更**(フィールド削除・型変更・必須化・エンドポイント削除等)は、影響を受ける全コンシューマーユニットとの事前合意を要する。単一WARとして同時ビルド・デプロイされるため、破壊的変更はフロントエンドとバックエンドを同一リリースで反映することで後方互換維持のコストを回避してよい(Q4=A)。
- **加法的変更**(オプションフィールド追加、新規エンドポイント追加等)は、コンシューマーが未知のフィールドを無視することを前提に、プロバイダー単独の判断で行ってよい。
- バージョン番号・URLパスバージョン予約は一切行わない(Q4回答による明示的な簡素化)。契約変更はGitのコミット履歴・本ファイルの更新によって追跡する。
- C5/C11、C3/C12のように同一ユニットが複数の異なる境界向けに契約を持つ場合、境界ごとに独立して変更してよい(REST契約の変更が内部インタフェース契約に自動的に影響することはない)。

## Open Questions

| Contract | Question | Blocks |
|---|---|---|
| C4 | FR2.7(ログイン失敗ロックアウト)の要件定義書文言(「管理画面から設定可能」)と本契約の前提(`application.yml`方式)の不一致は未解消。要件定義書側の文言修正が必要 | Functional Design(3.1)着手前に要件定義書FR2.7の文言修正を完了させること |
| C1/C2 | 検索条件・フィールドの詳細スキーマ(`filter`パラメータ・`fields`オブジェクトの具体的なプロパティ)は、config-engineの動的な設定内容に依存するため、本契約では意図的にフリーフォーム(`additionalProperties: true`)としている。具体的なテーブル別スキーマはFunctional Design(3.1)で確定する | Functional Design(list-engine, record-edit-engine Unit) |
| C4 | パスワードハッシュ化アルゴリズム(bcrypt/argon2等)の具体的選定は未定(`requirements.md` Open Questions継続) | Functional Design(authentication-service Unit)・NFR設計 |
