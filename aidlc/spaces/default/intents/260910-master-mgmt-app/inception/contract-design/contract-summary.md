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
| C15 | 共通基盤(shared kernel、実装・値の設定はauthentication-service (U5)) | schema-introspector (U2), user-management (U4), menu-navigation (U6), audit-logging (U7)(読み取りのみ)、list-engine, record-edit-engine, config-import-export(読み取りのみ) | Javaインタフェース(shared-schema、同一プロセス内、`com.mastersmith.common.security`) | 共通基盤(Contract Ownership Rulesの例外、変更には、authentication-serviceと、すべての読み取り側のユニットの合意を要する) |

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
      summary: >
        業務データCSVエクスポート(data-import-exportへ内部委譲、FR12.1)。
        一覧画面の現在の検索条件・ソート順を反映する(Contract Design追補Q6=A)。
        列単位の実効READ権限一覧(permittedColumnNames)はlist-engineがサーバー側で
        算出し、本WEB APIには公開しない(C13参照)。
      security: [{ bearerAuth: [] }]
      parameters:
        - name: tableConfigId
          in: path
          required: true
          schema: { type: string }
        - name: filter
          in: query
          description: 一覧画面の現在の検索条件(GET /recordsと同形状)
          schema: { type: object, additionalProperties: true }
        - name: sort
          in: query
          description: 一覧画面の現在のソート順(GET /recordsと同形状)
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
  /api/menu-items:
    post:
      summary: 業務メニュー項目の新規作成(Contract Design追補、Functional Design Q2/Q10対応)
      security: [{ bearerAuth: [] }]
      requestBody:
        content:
          application/json:
            schema: { $ref: "#/components/schemas/MenuItemInput" }
      responses:
        "201": { description: 作成成功, content: { application/json: { schema: { $ref: "#/components/schemas/MenuItem" } } } }
        "400": { $ref: "#/components/responses/BadRequest" }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
  /api/menu-items/{menuItemId}:
    put:
      summary: 業務メニュー項目の更新(Contract Design追補)
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: menuItemId, in: path, required: true, schema: { type: string } }
      requestBody:
        content:
          application/json:
            schema: { $ref: "#/components/schemas/MenuItemInput" }
      responses:
        "200": { description: 更新成功, content: { application/json: { schema: { $ref: "#/components/schemas/MenuItem" } } } }
        "400": { $ref: "#/components/responses/BadRequest" }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { $ref: "#/components/responses/NotFound" }
    delete:
      summary: 業務メニュー項目の削除(Contract Design追補)
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: menuItemId, in: path, required: true, schema: { type: string } }
      responses:
        "204": { description: 削除成功 }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "403": { $ref: "#/components/responses/Forbidden" }
        "404": { $ref: "#/components/responses/NotFound" }
components:
  responses:
    Unauthorized: { description: 認証エラー(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    Forbidden: { description: 権限不足(RFC 9457、canAccessScreen(activeRoleId, "config-import-export")の拒否時。Contract Design追補), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    BadRequest: { description: 入力検証エラー(RFC 9457、targetTableConfigIdがconfig-engine側に存在しない場合を含む。Contract Design追補), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    NotFound: { description: 指定menuItemIdが存在しない(RFC 9457。Contract Design追補), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
  schemas:
    MenuItem:
      type: object
      properties:
        menuItemId: { type: string }
        label: { type: string }
        targetTableConfigId: { type: string, nullable: true }
        children: { type: array, items: { $ref: "#/components/schemas/MenuItem" } }
    MenuItemInput:
      type: object
      properties:
        parentMenuItemId: { type: string, nullable: true }
        label: { type: string }
        order: { type: integer }
        targetTableConfigId: { type: string, nullable: true }
    ProblemDetails:
      type: object
      properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string } }
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
```

**`/api/menu-items` CRUD追加(Contract Design追補、Functional Design Q2/Q10対応)**: 業務メニュー(MenuItem階層)の作成・更新・削除を、業務メニュー設定画面から呼び出すCRUD APIとして本Boltで追加する。認可はscreenKey`"config-import-export"`で`canAccessScreen`をサーバー側で再検証する(Q1でschema-introspectorと共有する既存の予約screenKeyに統一)。`targetTableConfigId`を指定する場合はconfig-engine側の存在確認を行い、存在しなければ400を返す。管理メニュー4項目(業務メニュー設定/ユーザ管理/監査ログ管理/設定管理)はこのCRUD APIの対象外で、アプリケーションコードに固定でハードコードする(Q8)。既存コンシューマー(frontend-ui)への影響がない加法的変更のため、本契約の所有者(menu-navigation)の判断で追加する(Contract Ownership Rules参照)。

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

**C4への追補(Contract Design追補、authentication-service Code Generation着手時、`construction/authentication-service/functional-design/functional-spec.md`の追補一覧2・3・12番と、NFR Design(`nfr-design/security-design.md` NFR2.8・NFR2.9・NFR2.10、`logical-components.md` 保留18番)を反映)**: 既存の記述は書き換えず、次を追補として加える。既存コンシューマー(frontend-ui)は未知の項目・レスポンスコードを一般的な処理で扱う前提のため、加法的変更として本契約の所有者(authentication-service)の判断で追加する(Contract Ownership Rules参照)。

- **ログインのレスポンス(200)**: `activeRoleId`(string、未選択ならnull)を追加する。`roles`は、直接付与分とGroup経由分の和集合(選択可能なロール)である。ロールがちょうど1つのユーザーは、ログイン時に、そのロールが自動的に選択される(`activeRoleId`に入る)。
- **リフレッシュ**: リクエストのリフレッシュトークンを使うたびに、新しいリフレッシュトークンを発行し(ローテーション)、レスポンス(200)を`{ accessToken, refreshToken, roles, activeRoleId }`とする(当初の`{ accessToken }`だけの記述の追補。ページの再読み込みのあとも、ロール選択・ヘッダーの表示ができる、FR4.2)。401は、未知・期限切れ・失効済み・再送の競合(猶予内)・盗用の疑いのいずれも区別しない、同一の応答とする。リフレッシュ時に、選択済みのロールがユーザーの選択可能なロールに含まれなくなっていた場合は、未選択(null)に戻し、残りがちょうど1つならそのロールを自動選択する(応答の`activeRoleId`で知らせる)。
- **アクティブロールの選択(`PUT /api/auth/active-role`)**: 200のレスポンスは`{ activeRoleId }`(選択したロール)。保持していないロール(空・nullを含む)の指定は403で、Sessionは変更しない。
- **エラーの追加**: 全エンドポイントに、503(内部設定DBの障害、ハッシュ計算の待機超過。ログインでは、実際の検証・ダミーの検証のどちらでも同じ503)・413(リクエストボディが64KiBを超えた)・400(JSONの形式が不正)を追加する。認証フィルタが認証を要するすべてのAPIに返す401・503も、同じ形式である(「前提(全契約に共通)」の認証の記述への、加法的な追記。他ユニットのREST契約C1〜C3・C5〜C8のレスポンスには、個別に追加しない)。
- **ProblemDetailsの拡張メンバー`code`(i18nキー)**: `auth.login.failed`(ログイン失敗、全原因)・`auth.token.invalid`(認証フィルタの失敗、全原因。`WWW-Authenticate: Bearer`を付ける)・`auth.refresh.rejected`(リフレッシュの失敗、全原因)・`auth.role.not-held`(403)・`auth.service.unavailable`(503)・`auth.request.too-large`(413)・`auth.request.malformed`(400)。加えて、認証の要否の規則による拒否(403)は`auth.forbidden`、分類できない例外(500)は`auth.internal-error`。`instance`には、生のパスを入れない(ルートのテンプレート、またはフィルタでは省略)。フィールド単位のエラー(`errors[]`)は、C4にはない(user-managementのC5は、`errors[].message`にi18nキーを入れ、`code`を持たない。frontend-uiは、`errors`があればフィールド単位のキー、なければ`code`から、文言を選ぶ)。
- **応答ヘッダー**: `/api/**`のすべての応答(ログイン・リフレッシュを含む)に`Cache-Control: no-store`を付ける。全応答に、`Referrer-Policy: no-referrer`・`X-Content-Type-Options: nosniff`・`Content-Security-Policy`(初期値は、同じオリジンのみ)を付ける。Cookieは使わず、`Set-Cookie`を返さない(ステートレス、CSRF保護は不要)。
- **既知の未解消フォローアップの解消**: 上の「既知の未解消フォローアップ(unit-of-work.md U5引継ぎ事項)」(FR2.7の文言の不一致は未解消)は、**要件定義書の追補(`inception/requirements-analysis/requirements.md`の末尾)で解消済み**である(ロックの閾値・ロック時間は`application.yml`で設定可能。管理画面での編集UIは設けない)。元の記述は、履歴として残す。

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

**C5への追補(Contract Design追補、user-management Code Generation着手時、`construction/user-management/functional-design/functional-spec.md`のOpen Question 2〜4およびNFR Designの保留1〜7・14番を反映)**: 既存の記述は書き換えず、次を追補として加える。既存コンシューマー(frontend-ui)は未知の項目・レスポンスコードを一般的な処理で扱う前提のため、加法的変更として本契約の所有者(user-management)の判断で追加する(Contract Ownership Rules参照)。

- **`POST /api/users`(招待)**: リクエストに任意項目`locale`(`ja`/`en`、省略時`ja`)を追加する(招待メールの言語、NFR7.1)。再招待でも、その時点で指定された言語で再送する。`name`は最大100文字([assumption])で制御文字(CR/LFなど)を含めない。レスポンスコードは、201に加えて、401(操作者を解決できない)、403、413(ボディが64KiBを超える)、422(フィールド単位の検証エラー、重複(status=active/disabled)、roleIdの実在検証の不合格、再招待の競合(招待中でなくなった))、503(招待メールの送信失敗・打ち切り・招待の同時実行数の上限超過・同一emailの排他の待機超過・DBのロック待ちタイムアウト)を宣言する。
- **`GET /api/users`**: 401・403に加えて、内部設定DB利用不可時の応答は共通基盤に従う。
- **`PUT /api/users/{userId}`**: リクエストボディ`{ name: string, roleIds: string[] }`を宣言する。更新可能な項目は`name`と`roleIds`のみで、それ以外の項目(`email`・`status`・`passwordHash`・`invitationToken`など)が指定された場合は422とする(無視しない)。レスポンスコードは、200に加えて、401・403・404(対象不存在)・413・422(自己のroleIds変更、roleIdの実在検証の不合格、nameの不備、更新不可項目の指定)・503(DBのロック待ちタイムアウト)。
- **`DELETE /api/users/{userId}`**: レスポンスコードは、204に加えて、401・403・404(対象不存在)・422(自分自身の無効化)・503(DBのロック待ちタイムアウト)。すでにdisabledの場合は冪等に204。
- **`POST /api/users/invitations/{token}/accept`**: リクエストに任意項目`theme`(`light`/`dark`)・`fontSize`(`large`/`medium`/`small`)・`locale`(`ja`/`en`)を追加する(省略時は`light`/`medium`/`ja`、BR4.3)。`password`は8文字以上128文字以下(Unicodeコードポイント数)とする。レスポンスコードは、200に加えて、404(未知・使用済み・取消済みのトークン。区別しない)、413、422(フィールド単位)、503(ハッシュ計算の待機超過)。ProblemDetailsの`instance`には、トークンを含む生のパスではなく、ルートのテンプレート(`/api/users/invitations/{token}/accept`)を入れる。
- **`/api/me/preferences`**: 401(操作者を解決できない)、`PUT`の413・422を宣言する。
- **`errors[].message`はi18nキー**とする(NFR7.2)。文言への変換(翻訳)はフロントエンド(U12)が行う([assumption])。メッセージにパラメータ(文字数の上限など)が必要な場合に限り、`errors[]`の要素に任意の`params`(例: `{"min":8,"max":128}`)を持たせる。入力値そのもの(パスワードなど)は、エラーに含めない。キーは`user.validation.<field>.<rule>`の形式とする(例: `user.validation.email.invalid`、`user.validation.name.tooLong`、`user.validation.password.length`、`user.validation.roleIds.unknown`)。
- **招待リンクの形式(U12への要求、NFR Design保留8番)**: `<ベースURL>/invitations/accept#token=<トークン>`(トークンはフラグメントに入れる)。受諾画面(U12)は、フラグメントからトークンを読み取ったらただちに`history.replaceState`でURLから消し、トークンを画面の状態・ログ・エラー報告・外部への送信に含めない。認証なしで開けるルートであり、SPAの直接アクセスのフォールバックを備える。

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
        "503": { $ref: "#/components/responses/ServiceUnavailable" }
components:
  responses:
    Forbidden: { description: 権限不足(RFC 9457), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
    ServiceUnavailable: { description: 内部設定DB利用不可時(RFC 9457、専用フォールバックなし。Contract Design追補、NFR Requirementsレビュー指摘R-01対応), content: { application/problem+json: { schema: { $ref: "#/components/schemas/ProblemDetails" } } } }
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

**503レスポンスの追加(Contract Design追補、NFR Requirementsレビュー指摘R-01対応)**: 内部設定DBが利用不可の場合、`503 Service Unavailable`(RFC 9457)を返す(`construction/audit-logging/nfr-requirements/reliability-requirements.md` NFR4.4)。専用のフォールバック(キャッシュ等)は設けない。既存コンシューマー(frontend-ui)は未知のレスポンスコードを一般的なエラー処理で扱う前提のため、加法的変更として本契約の所有者(audit-logging)の判断で追加する(Contract Ownership Rules参照)。

**audit-loggingへの追補(Contract Design追補、config-import-export Code Generation、functional-spec.md 追補一覧6番を反映)**: audit-loggingは、次の2つのドメインイベントを、同期の`@EventListener`(全体をtry-catchで囲み、発行元に例外を伝えない)で購読し、追記専用の監査ログの行として記録する(C6のREST APIは変えない。`targetType`の許容値に、`ConfigImportExport`が加わる)。
- `ConfigImportExecutedEvent`(config-import-export): 取り込み1回につき1件(成功・失敗の両方。エクスポートでは発行しない)。`targetType`=`ConfigImportExport`・`targetId`=`config-import-export`・`operationType`=`CONFIG_IMPORT_SUCCEEDED`|`CONFIG_IMPORT_FAILED`・`actorUserId`=操作者のuserId・`afterValue`={`outcome`, `activeRoleId`, (成功)`sections`の件数, (失敗)`failureCategory`(`MALFORMED`・`UNSUPPORTED_FORMAT`・`VALIDATION_ERROR`・`ESCALATION_DENIED`・`RBAC_EMPTY`・`UNEXPECTED`)・`errorCount`}。ファイルの内容(設定の値・名前)は、記録しない。発行元は、独立したトランザクション(`REQUIRES_NEW`)の中で、同期発行する。
- `PermissionImportedEvent`(permission-engine): 取り込み単位のサマリ(BR3.11)。`targetType`=`PermissionEngine`・`operationType`=`PERMISSION_IMPORTED`・`actorRaw`=操作者のactiveRoleId・`afterValue`={`changeCount`}。

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

**C7への追補(Contract Design追補、config-import-export Code Generation、functional-spec.md 追補一覧1番・nfr-design/security-design.md NFR2.6・reliability-design.md NFR4.3を反映)**: 上のOpenAPIの`200`・`401`・`422`・`500`・`503`と、エクスポートの応答の形式を、次のとおり具体化する(加法的な追補。既存の`403`・`errors[]`の`{ field, message }`は変えない)。

- **エクスポート(`GET /api/config/export`)**: 応答は`application/json`、`Content-Disposition: attachment; filename="mastersmith-config-<書き出し日時>.json"`(日時は、ISO 8601のUTCから、区切りの`-`・`:`を除いた`yyyyMMddTHHmmssZ`)。本体は、`formatVersion`(整数、MVPは1)・`exportedAt`(ISO 8601のUTC)・`appVersion`・`schema`(`tables[]`・`translations[]`)・`menu`(`items[]`。入れ子の`children[]`・遷移先`targetTable{schemaName,tableName}`)・`rbac`(`roles[]`・`groups[]`(`name`・`roleNames[]`)・`primaryPermissions[]`(`roleName`・`scope{scopeType,schemaName,tableName,columnName}`・`level`)・`auxiliaryPermissions[]`(`roleName`・`scope`・`createAllowed`・`deleteAllowed`))。すべての参照は自然キーで、内部IDを含まない。ユーザー・認証情報・監査ログ・業務データ・管理メニューは含まない。取り込みでは、未知のプロパティ(すべての階層)を無視する。
- **インポート(`POST /api/config/import`)**: リクエスト本体は、エクスポートと同じ形式の設定ファイル。`200`の応答本体は`{ "outcome": "SUCCESS", "sections": { "<セクション>": { "added": n, "updated": n, "deleted": n } } }`(セクションは、`schema`・`translations`・`menu`・`roles`・`groups`・`primaryPermissions`・`auxiliaryPermissions`)。値が変わらない項目は、`updated`に数えない。
- **エラー(RFC 9457、`application/problem+json`。いずれも`code`(安定したi18nキー)を持つ)**: `401`(操作者を解決できない。`WWW-Authenticate: Bearer`。`code`=`auth.token.invalid`)・`403`(`config-import-export`の権限なし。`config.import.forbidden`)・`422`(入力の誤り。`errors[]`は`{ field(JSON上の位置。例 schema.tables[3].columns[2].editorType), message(i18nキー), params(任意) }`。最大100件で、`errorCount`(打ち切り前の総数)・`truncated`を持つ。応答全体の`code`は、`config.import.json.malformed`・`config.import.format.unsupported`・`config.import.rbac.escalation`・`config.import.rbac.empty`・`config.import.validation.failed`のいずれか)・`500`(`config.import.internal-error`)・`503`(内部設定DBの障害・更新の競合・ロック待ちのタイムアウト。`config.import.unavailable`)。入力値・スタックトレース・内部の型名・SQLは、応答に含めない。
- **メッセージキー(`errors[].message`)**: `config.import.json.malformed`・`config.import.format.unsupported`・`config.import.field.required`・`config.import.field.type`(`params.expected`)・`config.import.field.value.invalid`(`params.allowed`)・`config.import.field.duplicate`・`config.import.field.too-long`(`params.max`)・`config.import.reference.notFound`・`config.import.column.choiceOrFkExclusive`・`config.import.menu.leafHasChildren`・`config.import.menu.parentInvalid`・`config.import.rbac.auxiliaryColumn`・`config.import.rbac.reservedUnknown`・`config.import.rbac.escalation`・`config.import.rbac.empty`。
- 認可は、サーバー側で、エクスポート・インポートの両方で、必ず、最初に行う(`OperatorContext`→`canAccessScreen(activeRoleId, "config-import-export")`)。リクエスト本体の束縛の失敗(読めないJSON・空の本体・Content-Typeの不一致)も、認可の後に、`422`(`config.import.json.malformed`)として返す。`consumes`は宣言しない。

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
    - name: getTableConfigById
      description: >
        tableConfigIdからTableConfigを解決する(getTableConfigの逆方向解決)。data-import-export(U8)が
        CSVエクスポート・インポート対象の物理テーブル名(schemaName/tableName)を解決するために必要とする
        (Contract Design追補)。
      params: { tableConfigId: string }
      returns: TableConfig
      throws: [TableConfigNotFoundException]
    - name: findColumnConfigById
      description: >
        columnConfigIdからColumnConfigを解決する。permission-engine(U3)がscopeRef(columnConfigId)からの
        階層解決に用いる。存在しない場合は例外ではなくOptional.emptyを返す(呼び出し元がBR3.6のデフォルト
        フォールバックとして扱う通常の制御フロー、Contract Design追補)。
      params: { columnConfigId: string }
      returns: "Optional<ColumnConfig>"
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
    TableConfig: { tableConfigId: string, schemaName: string, tableName: string, displayOrder: int, optimisticLockColumn: "string | null" }
    ColumnConfig: { columnConfigId: string, tableConfigId: string, columnName: string, displayOrder: int, format: string, editorType: string, validationRule: string, visibility: string, isPrimaryKey: boolean, choiceOptions: "List<ChoiceOption> | null", fkReference: "FkReference | null" }
    ChoiceOption: { value: string, i18nKey: string }
    FkReference: { referencedSchemaName: string, referencedTableName: string, referencedValueColumnName: string, referencedLabelColumnName: string }
  exceptions:
    - name: TableConfigNotFoundException
      httpMapping: N/A(内部呼び出し、Java例外)
    - name: ConfigValidationException
      description: 設定定義自体の誤り(必須プロパティ欠落等)。fail fastで起動時・設定読込時・インポート時に検知する(project.md Mandated)
```

**主キー列情報の追加(Contract Design追補Q8=A、レビュー指摘R-05対応)**: `ColumnConfig.isPrimaryKey`は、data-import-export(U8)のCSVインポート時のupsert判定(INSERT/UPDATE、主キー列の値の有無で判定)に用いる。schema-introspector(U2)が対象RDBMSのメタデータ読み取り時に主キー制約を判定し、`writeTableConfigDraft`経由で設定する。単一主キー列を主な想定とし、複合主キーのテーブルへの詳細な対応(CSVに複数の主キー列を含める運用等)は本MVPスコープの主要な対象外とする。

**`displayName`の廃止・`choiceOptions`/`fkReference`の追補(Contract Design追補、config-engine Code Generationレビュー指摘R-05対応)**: `TableConfig`/`ColumnConfig`の`displayName`フィールドは、config-engine Functional Design(Q5 Follow-up)で表示名テキストを保持しない設計(i18nキーの機械的導出、`entities.md`参照)に変更された際に廃止済みだったが、本契約(C9)への反映が漏れていた。本追補はその反映であり、新たな設計変更ではない。`choiceOptions`/`fkReference`(BR1.4、静的選択肢またはFK参照の排他設定)も同様にentities.md確定済みの属性で、本契約への反映が漏れていたため追加する。いずれのコンシューマーユニット(schema-introspector/permission-engine/data-import-export/list-engine/record-edit-engine/config-import-export)の実装コードも`displayName`を参照していないことを確認済みであり(実装済みユニットはentities.mdの現行定義に基づいて構築されている)、破壊的変更ではあるが実質的な影響はない。

**`getTableConfigById`/`findColumnConfigById`の追補(Contract Design追補、permission-engine Code Generationレビュー指摘R-04対応)**: data-import-export(U8)・permission-engine(U3)の各Code Generation時に、それぞれ`getTableConfigById`・`findColumnConfigById`が実装コード上に追加されていたが、いずれも本契約(C9)への反映が漏れていた(データ取得専用の加法的メソッドであり、既存メソッドの変更を伴わない)。本追補はその反映である。

**schema-introspectorのC10コンシューマー追補(Contract Design追補、permission-engine Code Generationレビュー指摘R-03対応)**: schema-introspector(U2、`rules.md` BR2.8)は、設定管理画面(config-import-exportと同一画面)からの`POST /api/config/schema-introspection`呼び出し時に`canAccessScreen(activeRoleId, "config-import-export")`でサーバー側再検証を行う設計であり、既に実装済みだが本契約(C10)のconsumers列挙への反映が漏れていた。本追補はその反映であり、新たな依存の追加ではない。

**C9への追補(Contract Design追補、config-import-export Code Generation、functional-spec.md 追補一覧2番・logical-components.md 追補1〜5を反映)**: `importConfigSet`(引数`ConfigImportSet`・内部IDに依存)を廃止し、取り込みの契約を、検証と反映の2つに分ける(consumersは、config-import-exportだけ)。`getExportableConfigSet`は、他から変更できないスナップショットを返す。

```yaml
methods:
  - name: getExportableConfigSet
    description: キャッシュを介さず、内部設定DBから直接読み、他から変更できないスナップショット(TableConfigSnapshot・ColumnConfigSnapshot・TranslationSnapshot)を返す。伝播MANDATORY(呼び出し元の読み取り専用トランザクションの中で呼ぶ。他のユニットと同じ時点のスナップショットにするため)。
    returns: ConfigExportSet
  - name: validateConfigSet
    description: 何も反映せず、誤りを、位置(入力の中のリストの添え字を含む経路)・i18nキー・パラメータの一覧として返す(全件を集める。例外は投げない)。config-engineの既存の規則(BR1.1〜BR1.4)を再利用する。
    params: { configSet: ConfigNaturalKeySet }
    returns: "List<ImportValidationError>"
  - name: applyConfigSet
    description: 全置換で反映する(自然キー(schemaName・tableName・columnName、翻訳は(i18nKey, locale))で照合し、ファイルにない項目を削除、既存の項目は内部IDを維持して更新、isPrimaryKeyは維持(BR1.14))。伝播MANDATORY。自身ではコミットせず、キャッシュに触れない。削除→追加・更新の順に、段階ごとにflush()する。
    params: { configSet: ConfigNaturalKeySet }
    returns: "ApplyResult { sections(schema・translations)の追加・更新・削除の件数, postCommit }"
types:
  ConfigNaturalKeySet: { tables: "List<Table{schemaName, tableName, displayOrder, optimisticLockColumn, columns}>", translations: "List<{i18nKey, locale, text}>" }
  ApplyResult: { sections: "Map<String, SectionCounts>", postCommit: "PostCommit { invalidateCaches, publishEvents }" }
  ImportValidationError: { field: string, message: "i18nキー", params: "Map" }
```

- **キャッシュの世代管理(NFR4.2)**: `ConfigCache`は、状態(`VALID`・`STALE`)・世代番号・スナップショットを、1つの不変な値にまとめ、compare-and-setで置き換える。`invalidate()`(失敗しえない)、`STALE`の間の読み取りでの再読み込み(待ちの上限つきの排他・二重の確認・独立した読み取り専用トランザクション(`REQUIRES_NEW`・`REPEATABLE_READ`)・終了時の世代の確認)、再読み込みの失敗の共有と抑制の期間(`mastersmith.config.cache.reload-wait-timeout`・`reload-failure-backoff`)を持つ。再読み込みの失敗は、その読み取りの503。個別の更新は、`STALE`のとき・更新の最中に世代が進んだときは、キャッシュを更新せず、`invalidate()`を呼ぶ。
- **確定後の動作**: 反映するメソッドは、`afterCommit`を自身では登録しない。`PostCommit`(`invalidateCaches`・`publishEvents`)を戻り値に含めて返し、config-import-exportの`PostCommitCoordinator`が、固定した順序(無効化 → 個別イベント → 成功の監査イベント)で、独立に実行する。個別の変更イベントは、独立したトランザクション(`REQUIRES_NEW`)の中で発行し、例外を握りつぶす。`actor`は、`"system"`のまま(BR9.15)。

### C10: permission-engine 内部インタフェース契約

```yaml
shared-schema:
  interface: PermissionEngineApi
  package: com.mastersmith.permission
  consumers: [user-management, menu-navigation, audit-logging, list-engine, record-edit-engine, config-import-export, schema-introspector]
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
      description: >
        主権限を割り当てる。権限管理者による明示的操作でのみ呼び出し可能。権限昇格(自分自身への昇格含む)を
        防止する(project.md Forbidden)。actorRoleId(割当操作を実行している操作者のactiveRoleId)は
        BR3.8の昇格防止判定(操作者自身の実効権限との比較)に構造的に必須なため、C10当初のparamsに
        追補する(Contract Design追補、permission-engine Code Generationレビュー指摘R-02対応)。
      params: { actorRoleId: string, roleId: string, scopeType: string, scopeRef: string, level: string }
      throws: [PermissionEscalationException]
    - name: assignAuxiliaryPermission
      description: >
        補助権限(createAllowed/deleteAllowed)を割り当てる。主権限と同じ昇格防止規則(BR3.8)を、
        createAllowed/deleteAllowedそれぞれ独立に適用する。entities.mdがAuxiliaryPermissionを
        独立したエンティティとして定義していることと整合させるため、assignPermission(主権限用)とは
        別メソッドとして追加する(Contract Design追補、permission-engine Code Generationレビュー指摘
        R-02対応)。scopeTypeはSCHEMA/TABLEのみを許容し、COLUMNは拒否する(entities.md AuxiliaryPermission)。
      params: { actorRoleId: string, targetRoleId: string, scopeType: "schema|table", scopeRef: string, createAllowed: "boolean | null", deleteAllowed: "boolean | null" }
      throws: [PermissionEscalationException]
    - name: getGroupDerivedRoleIds
      description: >
        指定UserがGroup所属を通じて間接的に得るロールID一覧を返す。user-managementが自身の
        User.roleIds(直接付与分)と本メソッドの戻り値(Group経由分)を合成し、選択可能ロール一覧を
        構成する(W3、Contract Design追補、permission-engine Code Generationレビュー指摘R-02対応)。
      params: { userId: string }
      returns: "List<string>"
  types:
    EffectivePermission: { level: "FULL|READ|NONE", canCreate: boolean, canDelete: boolean }
  exceptions:
    - name: PermissionEscalationException
      description: 権限管理者の明示的操作を経ない権限昇格の試行(project.md Forbidden)
```

**`roleExists`の追補(Contract Design追補、user-management Code Generation着手時、functional-design-questions.md Q6・rules.md BR4.5対応)**: user-management(U4)が、招待・更新でroleIdsを指定・変更する際に、各roleIdの実在を検証するため、次のメソッドを追加する(加法的変更、既存メソッドは変更しない)。

```yaml
methods:
  - name: roleExists
    description: 指定されたroleIdがpermission-engine側に実在するかを判定する。user-managementが、招待・更新時のroleIds検証(BR4.5)に用いる。実在しない(削除済みを含む)場合はfalse。
    params: { roleId: string }
    returns: boolean
```

**C10への追補(Contract Design追補、authentication-service Code Generation着手時、functional-spec.md 追補一覧6番・rules.md BR5.12を反映)**: `resolveEffectivePermission(activeRoleId, ...)`・`canAccessScreen(activeRoleId, screenKey)`は、`activeRoleId`がnullまたは空(アクティブロールが未選択)のときは、「ロールを持たない」として、fail closed(権限なし=NONE・false)で判定する。ただし、RBAC設定が1件もない間の例外(`canAccessScreen(_, "config-import-export")`、rules.md BR3.13(a))は、`activeRoleId`にかかわらず適用する(ロールを持たない初期管理者も、最初のRBAC設定のインポートへ到達できる)。呼び出し元は、nullを自前で拒否せず(401にせず)、そのままC10へ渡す(操作者そのものが解決できない場合だけ401)。既存のメソッドのシグネチャ・既存の振る舞い(実在しないロールはNONE、など)は変えない(意味の追補であり、加法的変更)。

**C10への追補(Contract Design追補、config-import-export Code Generation、functional-spec.md 追補一覧4番・logical-components.md 追補1a〜3を反映)**: 次のメソッドを追加する(加法的変更、既存のメソッドは変えない。`assignPermission`・`assignAuxiliaryPermission`は、取り込みでは使わない)。

```yaml
methods:
  - name: exportRbac
    description: ロール・グループ・グループとロールの対応・主権限・補助権限を、キャッシュを介さず、内部設定DBから直接読み、他から変更できないスナップショット(内部ID・不透明な対象の識別子のまま)を返す。伝播MANDATORY(読み取り専用)。ユーザー・ユーザーのグループ所属は含めない。
    returns: RbacExport
  - name: isBootstrapState
    description: 主権限が1件もない初期状態(BR3.13)か。config-import-exportが、取り込み開始時点の値(bootstrapAtStart)を固定するために問い合わせる。
    returns: boolean
  - name: isReservedSchemaName
    description: 管理系画面の権限のための予約スキーマ名(`__system__:`で始まる、既知の名前。BR3.15)か。config-import-exportは、名前の意味を解釈せず、この問い合わせに委ねる(BR9.20)。
    params: { schemaName: string }
    returns: boolean
  - name: validateRbacImport
    description: 何も反映せず、誤りを全件集める。(1)権限昇格(BR3.8): すべてのエントリについて、actorRoleIdの、取り込み開始時点の実効権限(REPEATABLE_READのスナップショット。スコープの階層COLUMN→TABLE→SCHEMAの継承、補助権限のTABLE→SCHEMAの継承、取り込みで新規に作るテーブル・カラムはスキーマの設定にフォールバック)を基準に、上回るエントリをすべて集める(bootstrapAtStart=trueなら判定しない)。(2)主権限が0件(BR9.12。ブートストラップ状態でも拒否)。(3)権限の対象の構造(必須・長さ・予約スキーマ名・補助権限のCOLUMNの拒否)。actorRoleIdが未選択・空なら、割当を持たない者として、fail closedで判定する。
    params: { importSet: RbacImportSet, actorRoleId: "string | null", bootstrapAtStart: boolean }
    returns: "List<ImportValidationError>"
  - name: applyRbacImport
    description: 全置換で反映する(ロール・グループは名前で照合して内部IDを維持、権限は(ロール, 対象の種別, 対象の識別子)で照合。ファイルにないものは、削除→追加・更新の順に、段階ごとにflush()する)。伝播MANDATORY。1件ごとのキャッシュの無効化・イベントの発行は行わない(既知のR-04の解消)。
    params: { importSet: RbacImportSet, actorRoleId: "string | null" }
    returns: "ApplyResult { sections(roles・groups・primaryPermissions・auxiliaryPermissions)の件数, postCommit }"
types:
  RbacImportSet: { roles: "List<{name}>", groups: "List<{name, roleNames}>", primaryPermissions: "List<{roleName, scope{scopeType, schemaName, tableConfigId?, columnConfigId?}, level}>", auxiliaryPermissions: "List<{roleName, scope, createAllowed, deleteAllowed}>" }
```

- **キャッシュの世代管理(NFR4.2)**: 実効権限のキャッシュ(Caffeine)は、キーに世代番号を含める。`PermissionCacheControl.invalidate()`が、世代番号を進め、`invalidateAll()`を呼ぶ(失敗しえない)。計算(ロード)は、独立した読み取り専用トランザクション(`REQUIRES_NEW`)で行い、失敗したら、抑制の期間(`mastersmith.permission.cache.load-failure-backoff`)の間、計算を試みず、その読み取りの503。呼び出し元のトランザクションの中では、キャッシュを介さず、そのトランザクションの中で解決する。
- **サマリイベント(BR3.11)**: 取り込み1回につき、主権限・補助権限の変更が1件以上あれば、`PermissionImportedEvent`(`actor`=操作者のactiveRoleId・`changeCount`・`occurredAt`)を、確定後に、1件だけ発行する。

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

**C11への追補(Contract Design追補、user-management Code Generation着手時、functional-spec.md W8・rules.md BR4.13、NFR Designの保留3・9番を反映)**: 既存の記述は書き換えず、次を追補として加える。

- **`UserAccount.roleIds`**: `User.roleIds`(直接付与分)と、`PermissionEngineApi.getGroupDerivedRoleIds(userId)`(Group経由分)の和集合とする。**`UserAccount.passwordHash`は、常にnullとする**(ハッシュ値を呼び出し元へ返さない。検証は`verifyPasswordHash`が行う)。`findByEmail`は、正規化後(trim・小文字)のemailで検索する。
- **`verifyPasswordHash`**: 対象がstatus=activeで`passwordHash`が非nullの場合のみ検証し、それ以外(invited・disabled・不存在)、および129文字以上のパスワードは、ハッシュ計算をせずfalseを返す。**検証に成功し、保存済みのハッシュのパラメータが現在の設定より古い場合は、新しいパラメータのハッシュへ更新する(書き込みを伴う副作用)**。この更新は、呼び出し元のトランザクション属性(読み取り専用を含む)に依存しないよう、`REQUIRES_NEW`の独立したトランザクションで、検証時と同じ`passwordHash`の場合に限って行う。更新に失敗しても、検証結果(ログインの成否)には影響させない。この更新は、UserChangedEventの対象外。
- **`HashCapacityExceededException`**: ハッシュ計算の同時実行数の許可を、待機の上限(既定2秒)内に取れなかった場合に、`verifyPasswordHash`が投げる非チェック例外(`com.mastersmith.usermanagement`パッケージ)。HTTPへの変換(503)は、呼び出し元(authentication-service)が行う。
- **呼び出し元(authentication-service、U5)への要求**: C11の各メソッドを、**トランザクションの外で呼ぶ**こと(ハッシュ計算の許可を保持している間はDB接続を取らない、というuser-managementの資源の取得順序の不変条件を保つため。NFR Design保留9番、レビュー指摘R-11の既定の解決)。
- **`revokeRefreshTokensOnDisable`の扱い(契約からの意図的な差異)**: 呼び出し方向が契約上曖昧で未確定であるため(functional-spec.md Open Questions)、user-management(U4)のCode Generationでは実装しない。`UserAccountLookupApi`は、`findByEmail`・`verifyPasswordHash`・`isDisabled`の3メソッドで定義する。FR2.3の「以後の再認証はできない」は、`isDisabled`が常に最新のstatusを返すこと(BR4.13)で担保する。authentication-service(U5)の機能設計で方向が確定した時点で、本メソッドを追加する。

**C11への追補(Contract Design追補、authentication-service Code Generation着手時、functional-spec.md 追補一覧4番・11番、NFR Designの保留13番を反映)**: 既存の記述は書き換えず、次を追補として加える。

- **`findByUserId(userId: string): Optional<UserAccount>`を追加する**: アクセストークンが`sub`(userId)しか運ばないため、リフレッシュ・ロール選択で、最新の選択可能なロールを得る。`UserAccount.roleIds`は、直接付与分とGroup経由分の和集合、`passwordHash`は常にnull。statusは問わない(不存在の場合だけ空)。nullまたは空のuserIdは、空を返す。
- **`dummyVerify(rawPassword: string): void`を追加する**: ユーザーを指定しないダミーの検証。実際の検証(`verifyPasswordHash`)と同じコストのハッシュ計算を、同じハッシュ計算の同時実行数の上限を共有して行い、結果は返さない。129文字以上のパスワード(およびnull・空)は、ハッシュ計算をしない(`verifyPasswordHash`と同じ扱い)。上限を超えた場合は`HashCapacityExceededException`。呼び出し元が、実際の検証を行わない場合(未登録・招待中・無効化済み・ロック中)に、応答時間と503の有無を、実際の検証と揃えるために用いる。
- **`revokeRefreshTokensOnDisable`を削除する**: 呼び出し方向が確定しない(Open Questions)まま、user-managementでは、当初から実装しなかった。authentication-serviceの機能設計で、引き込み型(リフレッシュ時に`isDisabled`を確認して拒否し、Sessionを失効させる)に確定したため、契約から削除する(FR2.3の「以後の再認証はできない」は、`isDisabled`が常に最新のstatusを返すことと、ログイン・リフレッシュでの拒否で担保する)。
- **`HashCapacityExceededException`の型の公開**: `com.mastersmith.usermanagement`パッケージの非チェック例外として、すでに公開されている(呼び出し元が、補償と503への変換を行う)。
- 上記の追加は、いずれも加法的な変更(既存のメソッド・既存の振る舞いは変えない)であり、実装は、authentication-serviceのCode Generationの範囲で、user-management(U4)のコードに加える(`UserAccountLookupApi`・`UserAccountLookupService`・`PasswordHasher`)。

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

**C12への追補(Contract Design追補、config-import-export Code Generation、functional-spec.md 追補一覧3番を反映)**: `importMenuStructure`を廃止し、検証と反映の2つに分ける。メニューは、キャッシュを持たない(DBから直接読む)ため、確定後の動作は空である。

```yaml
methods:
  - name: getExportableMenuStructure
    description: 内部設定DBから直接読み、フラットな一覧を返す(入れ子への変換は、config-import-exportが行う)。伝播MANDATORY(読み取り専用)。
    returns: "List<MenuStructureEntry>"
  - name: validateMenuStructure
    description: 何も反映せず、構造の規則(表示名・表示順の必須、親の参照・自己参照・循環、遷移先を持つ項目が子を持たないこと)を、全件集めて返す。遷移先のテーブルの実在は、反映の順序(schemaが先)により、反映の段階で満たされるため、検証しない。
    params: { items: "List<MenuImportItem>" }
    returns: "List<ImportValidationError>"
  - name: applyMenuStructure
    description: 全置換で反映する(既存を一括で削除し、入力の項目を、新しいIDで採番して追加する。BR9.14)。伝播MANDATORY。自身ではコミットしない。
    params: { items: "List<MenuImportItem>" }
    returns: "ApplyResult { sections(menu)の件数, postCommit(空) }"
types:
  MenuImportItem: { position: "string(JSON上の位置。一意)", parentPosition: "string | null", label: string, order: "int | null", leaf: boolean, targetTableConfigId: "string | null(検証の段階では、新規のテーブルはnull)" }
```

### C13: data-import-export 内部インタフェース契約

```yaml
shared-schema:
  interface: DataImportExportApi
  package: com.mastersmith.dataio
  consumers: [list-engine, record-edit-engine]
  methods:
    - name: exportCsv
      description: >
        一覧画面の現在の検索条件・ソート順(C1のfilter/sortをlist-engineが中継)、
        および列単位の実効READ権限一覧(permittedColumnNames)を反映してCSVを生成する
        (Contract Design追補Q6=A、レビュー指摘R-01対応)。permittedColumnNamesは
        list-engineが自身のPermissionEngine問い合わせ結果から算出した値であり、
        WEB API(C1)では公開しない内部専用パラメータである。
      params: { tableConfigId: string, filter: "object (nullable)", sort: "string (nullable)", permittedColumnNames: "List<string>" }
      returns: "InputStream (CSV)"
    - name: importCsv
      description: >
        行単位のバリデーションエラーを収集して返す(全体を即時失敗にはしない)。
        actorは監査ログイベント(ImportExecutedEvent)の実行者記録用(Contract Design
        追補Q7=A、レビュー指摘R-02対応)。record-edit-engineが自身のREST層(C2、
        Bearer認証済み)のSpring Security認証済みプリンシパルから取得して渡す
        内部専用パラメータであり、WEB API(C2)のリクエストボディには公開しない。
      params: { tableConfigId: string, file: "InputStream (CSV)", actor: string }
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

**C14への追補(Contract Design追補、authentication-service Code Generation着手時、functional-spec.md 追補一覧5番・rules.md BR5.13を反映)**: `getActiveRoleId(sessionId)`は、アクティブロールが未選択の場合に、**nullを返す**(`returns: string`は、nullを含む)。呼び出し元は、nullをそのままC10へ渡し、権限なしとして判定させる(BR5.12)。Sessionが存在しなければ`SessionNotFoundException`、Sessionが有効でない(revoked、またはリフレッシュの有効期限の経過、BR5.11の定義)なら`SessionExpiredException`を投げる。内部設定DBの障害で、Sessionを確認できない場合は、`AuthStorageUnavailableException`(非チェック例外、`com.mastersmith.auth.exception`)を投げる。3つの例外は、他ユニットのリクエスト処理の中で握りつぶさず、そのまま伝播させる(401・401・503への変換は、authentication-serviceの`AuthCrossCuttingExceptionAdvice`が担う)。

### C15: 共通基盤の操作者の契約(Operator・OperatorContext、新規、authentication-service Code Generation着手時に追加)

```yaml
shared-schema:
  interface: OperatorContext
  package: com.mastersmith.common.security
  consumers: [schema-introspector, user-management, menu-navigation, audit-logging, config-import-export]
  note: >
    認証済みのリクエストの操作者を、他ユニットが読むための、中立の共有契約(共通基盤の契約、shared kernel。
    functional-spec.md 追補一覧9番・rules.md BR5.12)。authentication-serviceの業務ロジックに依存しない型と
    読み取りインタフェースだけで構成する。値を設定する側の実装(SecurityContextOperatorContext)は、
    authentication-serviceの認証フィルタ(BearerAuthenticationFilter)が提供し、他ユニットは、この契約だけを読む
    (authentication-serviceのコンポーネントを呼ばない)。ヘッダー(X-User-Id・X-Active-Role-Id)から
    操作者を導く実装は、どのプロファイルにも置かない(暫定のHeaderCurrentOperatorProvider・HeaderActiveRoleResolverは削除した)。
  methods:
    - name: current
      description: 現在のリクエストの操作者を返す(読み取り専用)。認証されていない(解決できない)場合は空。
      returns: "Optional<Operator>"
  types:
    Operator: { userId: string, sessionId: string, activeRoleId: "string | null" }
  constraints:
    - 操作者が解決できる(認証済み)なら、activeRoleIdがnullでも、Operatorは存在する。activeRoleIdがnullであることは、認証エラー(401)ではなく権限なし(403)を意味する。
    - 読み取り側は、activeRoleIdのnullを、自前で拒否せず(401にせず)、そのままC10へ渡す。操作者そのものが解決できない場合だけ401。
```

**C15への追補(Contract Design追補、config-import-export Code Generation、functional-spec.md 追補一覧5番を反映)**: config-import-export(U9)は、C15の`OperatorContext`の、読み取り側のコンシューマーである(consumersに含める。型・メソッドは変えない。DAGの葉のため、依存の向きも変わらない)。要求ごとに`OperatorContext.current()`を1回だけ読み、解決できなければ401、解決できたら、`activeRoleId`(nullのまま)を、C10の`canAccessScreen`と、`validateRbacImport`・`applyRbacImport`の`actorRoleId`に渡す。authentication-serviceのコンポーネントは呼ばない。

**契約の所有と変更(Contract Ownership Rulesの例外)**: C15は、プロバイダー側ユニットが所有する規則の例外として、共通基盤の契約(shared kernel)とする。変更(型・メソッドの追加・変更・削除)には、**authentication-serviceと、すべての読み取り側のユニットの合意を要する**(加法的変更であっても、単独の判断では行わない)。依存の方向は、読み取り側 → 共通基盤の契約の一方向で、DAGの葉である(`unit-of-work-dependency.md`のDAGは変わらない)。

## Contract Ownership Rules

- 各契約(REST/インタフェースいずれも)は**プロバイダー側ユニットが所有**する。コンシューマー側ユニットは契約の変更を提案できるが、確定はプロバイダー側の合意を要する(`contract-design-questions.md` Q3=A)。
- **破壊的変更**(フィールド削除・型変更・必須化・エンドポイント削除等)は、影響を受ける全コンシューマーユニットとの事前合意を要する。単一WARとして同時ビルド・デプロイされるため、破壊的変更はフロントエンドとバックエンドを同一リリースで反映することで後方互換維持のコストを回避してよい(Q4=A)。
- **加法的変更**(オプションフィールド追加、新規エンドポイント追加等)は、コンシューマーが未知のフィールドを無視することを前提に、プロバイダー単独の判断で行ってよい。
- バージョン番号・URLパスバージョン予約は一切行わない(Q4回答による明示的な簡素化)。契約変更はGitのコミット履歴・本ファイルの更新によって追跡する。
- C5/C11、C3/C12のように同一ユニットが複数の異なる境界向けに契約を持つ場合、境界ごとに独立して変更してよい(REST契約の変更が内部インタフェース契約に自動的に影響することはない)。

- **共通基盤の契約(shared kernel)の例外(C15)**: 業務ロジックに依存しない、中立の型と読み取りインタフェースで、複数のユニットが読む契約(C15の`Operator`・`OperatorContext`)は、プロバイダー側ユニットの単独の判断では変更できない。変更には、値を設定する側のユニット(authentication-service)と、すべての読み取り側のユニットの合意を要する(Code Generation着手時の追補)。

## Open Questions

| Contract | Question | Blocks |
|---|---|---|
| C4 | FR2.7(ログイン失敗ロックアウト)の要件定義書文言(「管理画面から設定可能」)と本契約の前提(`application.yml`方式)の不一致は未解消。要件定義書側の文言修正が必要 | Functional Design(3.1)着手前に要件定義書FR2.7の文言修正を完了させること |
| C4(解消済み) | 上のFR2.7の不一致は、要件定義書の追補(`inception/requirements-analysis/requirements.md`の末尾)で解消済み(authentication-service Code Generation着手時の追補、C4への追補の最後の項目を参照) | — |
| C1/C2 | 検索条件・フィールドの詳細スキーマ(`filter`パラメータ・`fields`オブジェクトの具体的なプロパティ)は、config-engineの動的な設定内容に依存するため、本契約では意図的にフリーフォーム(`additionalProperties: true`)としている。具体的なテーブル別スキーマはFunctional Design(3.1)で確定する | Functional Design(list-engine, record-edit-engine Unit) |
| C4 | パスワードハッシュ化アルゴリズム(bcrypt/argon2等)の具体的選定は未定(`requirements.md` Open Questions継続) | Functional Design(authentication-service Unit)・NFR設計 |
