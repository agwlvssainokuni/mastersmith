# Domain Design — 確認事項(MasterSmith)

要件定義書(`inception/requirements-analysis/requirements.md`)を踏まえ、システムを構成する論理的な building block(コンポーネント)を洗い出し、各エンティティの所有コンポーネント・コンポーネント間の依存関係を確定する前に、以下を確認します。

## Q1. 共通エンジン層(一覧/詳細編集)の分割粒度

FR5(一覧画面)・FR6(詳細・編集画面)は「共通エンジン層」として、設定駆動で任意の業務テーブルに対応する中核機能です。この共通エンジン層をどう分割しますか?

- A. 一覧と詳細編集をまとめて1つのコンポーネント(`RecordEngine`)とする。検索・ページング・フォームレンダリング・バリデーション・楽観ロックをすべて内包する
- B. 一覧(`ListEngine`)と詳細編集(`RecordEditEngine`)を別コンポーネントに分割する。検索・ページング・ソートはListEngine、フォームレンダリング・バリデーション・保存・楽観ロックはRecordEditEngineが担当する
- C. さらに細分化し、バリデーション処理を独立した`ValidationEngine`コンポーネントに切り出す(ListEngine/RecordEditEngine双方から呼び出される)
- X. Other (please specify)

[Answer]: B

## Q2. 設定基盤(Configuration Engine)の分割粒度

FR1(設定基盤)は、テーブル・カラム単位の表示設定・書式・編集部品・バリデーション・権限・表示可否を定義する設定モデルの読込・検証・DB方言吸収を担います。この設定基盤をどう分割しますか?

- A. 単一の`ConfigEngine`コンポーネントとし、設定読込・DB方言吸収・fail fast検証・メタデータからの初期ドラフト生成をすべて内包する
- B. 設定の読込・保持を担う`ConfigEngine`と、DBメタデータ読み取り・初期ドラフト生成(FR1.4)を担う`SchemaIntrospector`を別コンポーネントに分割する
- X. Other (please specify)

[Answer]: B

## Q3. 権限判定(RBAC)コンポーネントの独立性

FR4(権限制御)は、ロール・主権限(FULL/READ/NONE/指定なし・階層継承)・補助権限(CREATE/DELETE)の判定を行い、FR3.3により一覧/編集エンジンを含むすべてのAPI/ドメイン層で実効権限を再検証する必要があります。この権限判定ロジックはどう配置しますか?

- A. 独立した`PermissionEngine`コンポーネントとし、ListEngine/RecordEditEngine/ユーザ管理/監査ログ閲覧など、権限判定が必要な全コンポーネントから呼び出される共通コンポーネントとする
- B. 権限判定ロジックはユーザ管理コンポーネント(`UserManagement`)の一部として実装し、他コンポーネントはUserManagement経由で権限を問い合わせる
- X. Other (please specify)

[Answer]: A

## Q4. 認証・セッション管理とユーザ管理の分割

FR2(ユーザ管理: 招待・無効化・ロックアウト設定)とFR3(認証・セッション: トークン発行・複数デバイスログイン)は、それぞれ別のコンポーネントとしますか、それとも1つにまとめますか?

- A. 別コンポーネントとする。`UserManagement`(ユーザCRUD・招待・無効化)と`AuthenticationService`(ログイン・トークン発行検証・アカウントロック判定)に分割する
- B. 1つの`UserManagement`コンポーネントにまとめ、認証機能も内包する
- X. Other (please specify)

[Answer]: A

## Q5. 監査ログの横断的関与

FR8(監査ログ)は、ユーザー操作について操作者・操作対象・操作種別・日時・変更前後の値を記録する、追記専用(append-only)の機能です。他の多くのコンポーネント(RecordEditEngine、UserManagement、ConfigEngine等)から呼び出される横断的コンポーネントとなりますが、この依存関係の表現方法はどうしますか?

- A. `AuditLogging`を独立コンポーネントとし、記録が必要な各コンポーネント(RecordEditEngine、UserManagement、ConfigEngine等)がdepends_onとして明示的に依存する
- B. 監査ログ記録はイベント発行(ドメインイベント)によって疎結合に行い、`AuditLogging`は各コンポーネントが発行するイベントを購読して記録する(将来的なイベント駆動化を見据えた構成)
- X. Other (please specify)

[Answer]: B

## Q6. メニュー・ナビゲーション設定の所属

FR7(メニュー・ナビゲーション: 業務メニューのN階層構成、トップ画面のCard表示)を管理する機能は、どのコンポーネントが所有しますか?

- A. `ConfigEngine`の一部として扱う(メニュー構成も広義の設定データであるため)
- B. 独立した`MenuNavigation`コンポーネントとする
- X. Other (please specify)

[Answer]: B

## Q7. 表示設定(テーマ・フォントサイズ・言語)の所属

FR9(テーマ・フォントサイズ)・FR10(多言語i18n)のユーザー単位の表示設定は、どのコンポーネントが所有しますか?

- A. `UserManagement`(ユーザに紐づく設定として)の一部とする
- B. 独立した`UserPreferences`コンポーネントとする
- X. Other (please specify)

[Answer]: A

## Q8. 設定ファイル(JSON)IOと業務データ(CSV)IOの分割

FR11(設定ファイルのexport/import)とFR12(業務データのCSV export/import)は、対象データも形式も異なります。これらは同一コンポーネントで扱いますか、別コンポーネントに分割しますか?

- A. 別コンポーネントとする。`ConfigImportExport`(FR11、ConfigEngineが保持する設定一式が対象)と`DataImportExport`(FR12、RecordEditEngineが扱う業務データ行が対象)に分割する
- B. 1つの`ImportExportService`コンポーネントにまとめ、対象(設定/業務データ)をパラメータで切り替える
- X. Other (please specify)

[Answer]: A

## Q9. 楽観ロック判定ロジックの所在

`refined-mockups-questions.md`のQ6-follow-up・レビュー指摘R-01対応により、業務データの楽観ロック(更新日時/バージョン列が存在するテーブルのみ)とアカウントロックアウト設定(`application.yml`方式)が確定しています。楽観ロックの競合検出ロジックは、どのコンポーネントが担いますか?

- A. `RecordEditEngine`(またはQ1で選択した詳細編集を担うコンポーネント)が、`ConfigEngine`から該当テーブルの楽観ロック対象列の有無を取得した上で判定する
- B. 独立した`ConcurrencyControl`コンポーネントとして切り出す
- X. Other (please specify)

[Answer]: A

## Q10. FR13(CIパイプライン)・FR14(可観測性/OTEL)の扱い

FR13(GitHub ActionsによるビルドとWAR生成)・FR14(OTELエクスポート・構造化ログ)は、アプリケーションが実行時に呼び出す「業務ロジックを持つコンポーネント」ではなく、ビルド・運用系の関心事です。Domain Designのコンポーネントカタログにこれらを含めますか?

- A. 含めない。FR13・FR14はコンポーネントカタログ(components.md)の対象外とし、後続のCI Pipelineステージ・NFR設計ステージで扱う
- B. FR14(可観測性)のみ、横断的関心事として`Observability`コンポーネント(構造化ログ出力・OTELエクスポートの共通処理)をカタログに含める
- X. Other (please specify)

[Answer]: A

## Q11. 内部設定DBと業務データDBの接続分離とコンポーネント境界の関係

`team-practices.md`により、内部設定DB(表示設定・RBAC・ユーザ管理・監査ログ)と業務データ用RDBMS(PostgreSQL/MySQL/MariaDB)は別接続(内部設定DBは埋め込みDB)と確定しています。この物理的なDB分離を、コンポーネントの`external_dependencies`にどう反映しますか?

- A. 内部設定DB(H2等)を利用するコンポーネント(ConfigEngine、UserManagement、PermissionEngine、AuditLogging、UserPreferences等)と、業務データ用RDBMSを利用するコンポーネント(RecordEditEngine、ListEngine)とで、`external_dependencies`に明確に異なる接続先を記載する
- X. Other (please specify)

[Answer]: A

## Assumptions & Open Questions

None.

## Consolidated Summary Confirmation

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
