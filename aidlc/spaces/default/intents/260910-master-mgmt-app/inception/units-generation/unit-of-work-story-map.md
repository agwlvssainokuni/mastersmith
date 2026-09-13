# Unit of Work — ストーリーマップ(MasterSmith)

`user-stories`ステージは本ワークフローのスコープでSKIP対象であるため(`aidlc-state.md`)、`project.md`の学習事項(「user-storiesステージがSKIP対象の場合、Domain Design以降のtraceability.jsonはrequirements.mdの全FRを対象に作成する」)に従い、本ファイルは `US{x}.{y}` ではなく `requirements.md` の全FRを対象に、各FRを実装するUnitへマッピングする。

## FR → Unit マッピング

| FR ID | 概要 | 実装Unit ID | Directory |
|---|---|---|---|
| FR1.1 | テーブル・カラム単位の表示設定モデル | U1 | u1-config-engine |
| FR1.2 | 複数RDBMS方言の吸収 | U1 | u1-config-engine |
| FR1.3 | 設定定義のfail fast検証 | U1 | u1-config-engine |
| FR1.4 | DBメタデータからの初期ドラフト生成 | U2 | u2-schema-introspector |
| FR1.5 | FK参照select/radioの動的名称解決 | U1 | u1-config-engine |
| FR1.6 | 共通エンジン層への業務固有ハードコード禁止(横断) | U10, U11, U3, U7, U4 | u10-list-engine, u11-record-edit-engine, u3-permission-engine, u7-audit-logging, u4-user-management |
| FR2.1 | 招待メールによるユーザー登録・初回ログイン | U4 | u4-user-management |
| FR2.2 | ユーザー情報更新 | U4 | u4-user-management |
| FR2.3 | ユーザー無効化・リフレッシュトークン即時失効 | U4 | u4-user-management |
| FR2.4 | 初期管理者アカウント自動作成 | U4 | u4-user-management |
| FR2.5 | パスワード最小文字数 | U4 | u4-user-management |
| FR2.6 | パスワードのハッシュ化保存 | U4 | u4-user-management |
| FR2.7 | ログイン失敗によるアカウント一時ロック | U5 | u5-authentication-service |
| FR2.8 | 招待メール送信(SMTP、Mailpit) | U4 | u4-user-management |
| FR3.1 | トークンベース認証(アクセス/リフレッシュ) | U5 | u5-authentication-service |
| FR3.2 | 複数デバイス同時ログイン許可 | U5 | u5-authentication-service |
| FR3.3 | サーバー側での実効権限再検証 | U3 | u3-permission-engine |
| FR3.4 | 権限昇格の防止 | U3 | u3-permission-engine |
| FR4.1 | ロール単位の権限割当 | U3 | u3-permission-engine |
| FR4.2 | 複数ロール保持時のロール選択UI・セッション保持 | U5, U3 | u5-authentication-service, u3-permission-engine |
| FR4.3 | 主権限の階層継承 | U3 | u3-permission-engine |
| FR4.4 | 補助権限(CREATE/DELETE) | U3 | u3-permission-engine |
| FR4.5 | READ権限未満カラム/フィールドの非表示 | U10, U11 | u10-list-engine, u11-record-edit-engine |
| FR5.1 | 一覧画面(検索・ページング・ソート) | U10 | u10-list-engine |
| FR5.2 | ページサイズ選択 | U10 | u10-list-engine |
| FR5.3 | CREATE権限なし時の新規作成ボタン非表示 | U10 | u10-list-engine |
| FR5.4 | 該当データなし/エラー表示 | U10 | u10-list-engine |
| FR6.1 | 設定駆動フォーム部品による詳細・編集画面 | U11 | u11-record-edit-engine |
| FR6.2 | フォーカスアウト時インラインバリデーション | U11 | u11-record-edit-engine |
| FR6.3 | 楽観ロック競合検出 | U11 | u11-record-edit-engine |
| FR6.4 | 編集不可/READ未満フィールドの制御 | U11 | u11-record-edit-engine |
| FR7.1 | 業務メニューN階層・管理メニュー構成 | U6 | u6-menu-navigation |
| FR7.2 | トップ画面Card形式表示制御 | U6 | u6-menu-navigation |
| FR7.3 | 空メニュー時の案内表示 | U6 | u6-menu-navigation |
| FR8.1 | 操作の監査記録(操作者・対象・種別・日時・前後値) | U7 | u7-audit-logging |
| FR8.2 | 追記専用(append-only) | U7 | u7-audit-logging |
| FR8.3 | 監査ログ無期限保持 | U7 | u7-audit-logging |
| FR8.4 | 監査ログ閲覧画面 | U7 | u7-audit-logging |
| FR9.1 | ユーザー単位テーマ/フォントサイズ | U4 | u4-user-management |
| FR10.1 | i18nキー構造化 | U1 | u1-config-engine |
| FR10.2 | 日英2言語の翻訳リソース | (N/A、下記参照) | — |
| FR11.1 | 設定一式JSON export/import | U9 | u9-config-import-export |
| FR11.2 | 内部設定DBを正とするハイブリッド方式 | U9 | u9-config-import-export |
| FR12.1 | 業務データCSV export/import | U8 | u8-data-import-export |
| FR13.1 | GitHub Actionsビルド・実行可能WAR生成 | (Deferred、下記参照) | — |
| FR14.1 | OTELエクスポート・構造化ログ | (Deferred、下記参照) | — |
| FR14.2 | OTEL動作確認用ローカルコンテナ環境 | (Deferred、下記参照) | — |

## クロスカッティング・複数Unit横断FRの補足

- **FR1.6**: 「共通エンジン層に業務固有のハードコードをしない」というアーキテクチャ制約であり、単一Unitの機能ではなく`list-engine`・`record-edit-engine`・`permission-engine`・`audit-logging`・`user-management`(いずれもDomain Design段階で共通エンジン層として識別されたコンポーネント)すべてが遵守すべき横断的制約として扱う。`frontend-ui`(U12)も同様に業務固有のハードコードを避けるべきだが、Domain Design側の対象コンポーネントリストにフロントエンドが含まれていないため、Unit側でも本FRの直接ターゲットとはしない(実装時の設計原則として`unit-of-work.md` U12の実装上の注意に明記済み)。
- **FR4.2**: ロール選択UIとセッション保持は`authentication-service`(U5)が担うが、選択されたロールに基づく実際の権限判定は`permission-engine`(U3)が行うため両Unitにまたがる。
- **FR1.4**: 実装(初期ドラフト生成ロジック)は`schema-introspector`(U2)が担うが、FR1.4の「アプリ本体への統合」要件(外部ツール不要)を満たすため、起動経路として`frontend-ui`(U12)の設定管理画面に「スキーマからドラフト生成」操作を設け、そこから`schema-introspector`のAPIを呼び出す(レビュー指摘R-01対応、`unit-of-work-dependency.md`にfrontend-ui→schema-introspector依存を追加済み)。

## FR10.2・FR13.1・FR14.1・FR14.2 の扱い(Deferred/N/A)

Domain Design(`inception/domain-design/traceability.json`)における既存の判断を踏襲する(`project.md`学習事項: 「実行時コンポーネントではなくビルド成果物に相当するFRはN/A」「対象外と判断したFRはDeferredとし後続ステージ名を明記」)。

- **FR10.2**(翻訳リソース): 実行時コンポーネントではなくビルド成果物(言語ファイル)であるため、特定のUnitへの直接マッピング対象としない。配置先は`frontend-ui`(U12)を想定するが、具体化はConstruction(Code Generation)で行う。
- **FR13.1**(CIパイプライン定義そのもの): `packaging`(U13)はWARパッケージング処理を担うが、GitHub Actionsのワークフロー定義自体は後続のCI Pipelineステージ(3.7)で扱う(Domain Design ADR-007を踏襲)。
- **FR14.1・FR14.2**(可観測性/OTEL): 特定のUnitに閉じた機能ではなく、複数serviceユニットに横断的に組み込まれる非機能要件であるため、後続のNFR設計ステージ(3.2/3.3)で具体化する(Domain Design ADR-007を踏襲)。

## Unitごとの担当FR件数(カバレッジ検証)

| Unit ID | 担当FR数 |
|---|---|
| U1 (config-engine) | 6 (FR1.1, FR1.2, FR1.3, FR1.5, FR10.1, FR1.6の一部) |
| U2 (schema-introspector) | 1 (FR1.4) |
| U3 (permission-engine) | 6 (FR3.3, FR3.4, FR4.1, FR4.3, FR4.4, FR4.2の一部, FR1.6の一部) |
| U4 (user-management) | 8 (FR2.1〜FR2.6, FR2.8, FR9.1, FR1.6の一部) |
| U5 (authentication-service) | 3 (FR2.7, FR3.1, FR3.2, FR4.2の一部) |
| U6 (menu-navigation) | 3 (FR7.1, FR7.2, FR7.3) |
| U7 (audit-logging) | 4 (FR8.1〜FR8.4, FR1.6の一部) |
| U8 (data-import-export) | 1 (FR12.1) |
| U9 (config-import-export) | 2 (FR11.1, FR11.2) |
| U10 (list-engine) | 5 (FR5.1〜FR5.4, FR4.5の一部, FR1.6の一部) |
| U11 (record-edit-engine) | 5 (FR6.1〜FR6.4, FR4.5の一部, FR1.6の一部) |
| U12 (frontend-ui) | 0(直接のFR所有なし。全画面の横断的な表示・操作ホストとして、backend各Unitの機能をUIとして具体化する) |
| U13 (packaging) | 1 (FR13.1の一部、WARパッケージング) |

全FR(FR1.1〜FR14.2)は上記のいずれかのUnitに割当済み、またはDeferred/N/Aとして次工程に明示的に引き継がれている(未割当・GAPなし)。
