# Unit of Work — 依存関係(MasterSmith)

本ステージは Unit 間の依存トポロジーのみを扱う。着手順序(どのUnitを最初に着手するか)は経済的判断であり、Stage 2.9 Delivery Planning が本DAGを入力として決定する。

## 依存関係の設計判断(重要)

Domain Designのコンポーネントカタログ(`inception/domain-design/components.md`)には、同期呼び出し(`style: sync`)とドメインイベント発行(`style: event`、`audit-logging`への疎結合連携)が混在した意図的な循環依存が記録されている(`PermissionEngine`↔`AuditLogging`、および`ConfigEngine`→`AuditLogging`→`PermissionEngine`→`ConfigEngine`)。

`units-generation-questions.md` Q4の回答(A: 直接メソッド呼び出しを基本とし、監査ログ記録への連携のみドメインイベント発行とする)に基づき、本ステージのUnit依存DAGは **`style: sync`の依存のみをエッジとして採用し、`style: event`の依存(各ユニットからaudit-loggingへのイベント発行)はDAGのエッジに含めない**。

この扱いの理由:
- イベント発行はfire-and-forgetであり、発行元ユニットの処理完了はaudit-loggingの購読処理を待たない(呼び出し元がaudit-loggingの存在を意識しない疎結合、Domain Design ADR-005)。
- ビルド順序・並行開発可否の観点では、発行元ユニット(config-engine、record-edit-engine、permission-engine、user-management、config-import-export、data-import-export)はaudit-loggingの実装完了を待たずに開発・テストできる(イベントの契約=ペイロード形状さえ合意されていればよい)。
- 一方、audit-loggingが監査ログ閲覧画面のアクセス権限判定のためpermission-engineを**同期呼び出し**する依存(`audit-logging → permission-engine`)は、そのままDAGのエッジとして採用する。

この結果、Domain Designで確認された循環依存はUnit DAGでは解消され、非巡回(cycle-free)なグラフになる。

## 依存関係(プローズ)

- `config-engine`: 依存なし(内部設定DBへの読み書きのみ)。全ユニットの基盤。
- `schema-introspector`: `config-engine`に依存(生成した初期ドラフトを書き込む)。
- `permission-engine`: `config-engine`に依存(権限判定対象のスキーマ・テーブル・カラム階層構造を取得)。
- `user-management`: `permission-engine`に依存(ユーザ管理画面へのアクセス権限判定)。
- `menu-navigation`: `permission-engine`に依存(メニュー項目の表示可否判定)。
- `audit-logging`: `permission-engine`に依存(監査ログ閲覧画面へのアクセス権限判定)。
- `authentication-service`: `user-management`に依存(ユーザー情報・パスワードハッシュ・ロック状態の参照/更新)。
- `config-import-export`: `config-engine`・`menu-navigation`・`permission-engine`に依存(設定一式=スキーマ設定・メニュー構成・RBAC設定を集約)。
- `data-import-export`: `config-engine`に依存(対象テーブルのカラム定義・バリデーションルール取得)。
- `list-engine`: `config-engine`・`permission-engine`・`data-import-export`・`authentication-service`に依存(表示設定取得、権限判定、CSVエクスポート委譲、アクティブロール取得)。
- `record-edit-engine`: `config-engine`・`permission-engine`・`data-import-export`・`authentication-service`に依存(フォーム/楽観ロック設定取得、権限判定、CSVインポート委譲、アクティブロール取得)。
- `frontend-ui`: `list-engine`・`record-edit-engine`・`menu-navigation`・`authentication-service`・`user-management`・`audit-logging`・`config-import-export`・`schema-introspector`に依存(これらのAPIをブラウザから直接呼び出す)。`schema-introspector`への依存は、設定管理画面の「スキーマからドラフト生成」操作がFR1.4の「アプリ本体への統合」要件(外部ツール不要)を満たすために必要(レビュー指摘R-01対応、`unit-of-work.md` U2/U12参照)。`data-import-export`へは`list-engine`/`record-edit-engine`経由の委譲であり直接依存しない。
- `packaging`: 全12ユニット(`config-engine`〜`frontend-ui`)に依存(単一実行可能WARへの最終統合)。

## 統合ポイント(APIs / 共有データ / イベント)

| From | To | 種別 | 内容 |
|---|---|---|---|
| schema-introspector | config-engine | sync(書込) | 初期ドラフトの書き込み |
| permission-engine | config-engine | sync(照会) | スキーマ・テーブル・カラム階層構造の取得 |
| user-management | permission-engine | sync(照会) | ユーザ管理画面アクセス権限判定 |
| menu-navigation | permission-engine | sync(照会) | メニュー項目表示可否判定 |
| audit-logging | permission-engine | sync(照会) | 監査ログ閲覧画面アクセス権限判定 |
| authentication-service | user-management | sync(照会/更新) | ユーザー情報・パスワードハッシュ・ロック状態 |
| config-import-export | config-engine, menu-navigation, permission-engine | sync(集約) | 設定一式のexport対象取得・import結果書戻し |
| data-import-export | config-engine | sync(照会) | カラム定義・バリデーションルール取得 |
| list-engine | config-engine, permission-engine, authentication-service | sync(照会) | 表示設定・権限判定・アクティブロール取得 |
| list-engine | data-import-export | sync(委譲) | 業務データCSVエクスポート |
| record-edit-engine | config-engine, permission-engine, authentication-service | sync(照会) | フォーム/楽観ロック設定・権限判定・アクティブロール取得 |
| record-edit-engine | data-import-export | sync(委譲) | 業務データCSVインポート |
| frontend-ui | list-engine, record-edit-engine, menu-navigation, authentication-service, user-management, audit-logging, config-import-export | REST API(内部) | 画面表示・操作のための各ユニットAPI呼び出し |
| frontend-ui | schema-introspector | REST API(内部) | 設定管理画面の「スキーマからドラフト生成」操作(FR1.4のアプリ本体統合要件を満たす起動経路、レビュー指摘R-01対応) |
| config-engine, record-edit-engine, permission-engine, user-management, config-import-export, data-import-export | audit-logging | event(疎結合、DAGエッジ対象外) | ドメインイベント発行(RecordUpdated, UserInvited, PermissionChanged等) |
| packaging | 全ユニット | ビルド統合 | frontend-uiビルド成果物をバックエンドの静的リソースとして同梱し単一WARを生成 |

## 並行開発可能な層(Parallel Development Opportunities)

依存のないユニット群は並行して開発してよい(`units-generation-questions.md` Q3=A)。以下は本DAGにおける層構造(同一層内は相互に依存せず並行開発可能):

1. **Layer 0**: `config-engine`
2. **Layer 1**: `schema-introspector`, `permission-engine`, `data-import-export`
3. **Layer 2**: `user-management`, `menu-navigation`, `audit-logging`
4. **Layer 3**: `authentication-service`, `config-import-export`
5. **Layer 4**: `list-engine`, `record-edit-engine`
6. **Layer 5**: `frontend-ui`
7. **Layer 6**: `packaging`

NOTE: 上記の層構造はDAGのトポロジーが示す並行開発の余地を示すものであり、実際にどのユニットから着手するか(経済的な着手順序・Bolt構成)はStage 2.9 Delivery Planningが決定する。本ステージはクリティカルパスの推奨や着手順序の推奨を行わない。

## 依存関係グラフ(機械可読)

```yaml
units:
  - name: config-engine
    kind: service
    depends_on: []
  - name: schema-introspector
    kind: service
    depends_on: [config-engine]
  - name: permission-engine
    kind: service
    depends_on: [config-engine]
  - name: user-management
    kind: service
    depends_on: [permission-engine]
  - name: menu-navigation
    kind: service
    depends_on: [permission-engine]
  - name: audit-logging
    kind: service
    depends_on: [permission-engine]
  - name: authentication-service
    kind: service
    depends_on: [user-management]
  - name: config-import-export
    kind: service
    depends_on: [config-engine, menu-navigation, permission-engine]
  - name: data-import-export
    kind: service
    depends_on: [config-engine]
  - name: list-engine
    kind: service
    depends_on: [config-engine, permission-engine, data-import-export, authentication-service]
  - name: record-edit-engine
    kind: service
    depends_on: [config-engine, permission-engine, data-import-export, authentication-service]
  - name: frontend-ui
    kind: ui
    depends_on: [list-engine, record-edit-engine, menu-navigation, authentication-service, user-management, audit-logging, config-import-export, schema-introspector]
  - name: packaging
    kind: packaging
    depends_on: [config-engine, schema-introspector, permission-engine, user-management, menu-navigation, audit-logging, authentication-service, config-import-export, data-import-export, list-engine, record-edit-engine, frontend-ui]
```
