# Functional Design Questions — config-engine (U1)

`inception/units-generation/unit-of-work.md`（U1定義）・`inception/domain-design/components.md`（ConfigEngineコンポーネント定義）・`inception/contract-design/contract-summary.md`（C9: ConfigEngineApi）に基づき、config-engineユニットの機能設計（エンティティ・業務ルール・振る舞い仕様）を確定するための質問。

Domain Design・Contract Designで既に確定済みの事項（TableConfig/ColumnConfigエンティティの大枠、ConfigEngineApiのメソッドシグネチャ、embedded組込みDB採用など）は再確認しない。ここでは、それらの確定事項からは読み取れない、機能設計として具体化が必要な論点のみを問う。

## Q1: 権限フィールドとConfigEngineの境界

`requirements.md` FR1.1は「表示名・表示順・書式・編集部品・バリデーション・**権限**・表示可否を定義する設定モデル」とConfigEngineの責務に権限を含めて記述しています。一方、`components.md`のColumnConfig属性一覧（`[tableConfigId, columnName, displayName, displayOrder, format, editorType, validationRule, visibility]`）には権限フィールドがなく、権限（Role/PrimaryPermission/AuxiliaryPermission）はPermissionEngineが`scopeType: schema|table|column`・`scopeRef`という間接参照で保持する設計になっています（C10契約）。

ConfigEngineが保持するTableConfig/ColumnConfigに権限データを一切持たせず、PermissionEngineの`scopeRef`がConfigEngine側の識別子（`schemaName.tableName`や`columnConfigId`等）と対応付く、という理解で機能設計を進めてよいでしょうか。

- A. その理解でよい。ConfigEngineはTableConfig/ColumnConfigに権限データを一切保持しない。PermissionEngineの`scopeRef`は「`schemaName.tableName`」（テーブル階層）「`schemaName.tableName.columnName`」（カラム階層）「`schemaName`」（スキーマ階層）という文字列キーとしてConfigEngine側の識別子と対応付ける
- B. Aと同様だがscopeRefはtableConfigId/columnConfigId（ConfigEngineが発行するID）をそのまま用いる
- C. 上記以外の対応付け方式（自由記述）
- X. Other (please specify)

[Answer]: A. その理解でよい。ConfigEngineはTableConfig/ColumnConfigに権限データを一切保持しない。PermissionEngineのscopeRefは「schemaName.tableName」（テーブル階層）「schemaName.tableName.columnName」（カラム階層）「schemaName」（スキーマ階層）という文字列キーとしてConfigEngine側の識別子と対応付ける

## Q2: 「複数RDBMS方言吸収」の具体的な吸収対象

FR1.2は「PostgreSQL/MySQL/MariaDBの複数RDBMS方言を吸収した上で内部モデルへ変換する」とConfigEngineの責務としていますが、ConfigEngineは業務データへのSQL発行を行わず（一覧・詳細編集のクエリ実行はlist-engine/record-edit-engineが担当）、メタデータ（列の型等）の保持と設定変換が中心です。ConfigEngineが具体的に吸収すべき方言差異の範囲はどれですか。

- A. schema-introspector（U2）が読み取ったDBメタデータの型名（例: PostgreSQLの`varchar`/MySQLの`VARCHAR`、`SERIAL`/`AUTO_INCREMENT`等）を、ConfigEngine内部の論理型（editorTypeの初期値推定等に使う共通型）へ正規化する変換のみ
- B. Aに加え、楽観ロック対象列の自動検出ルール（例: `updated_at`/`version`列の命名規則・型の違い）もRDBMS方言ごとに吸収する
- C. 物理層のSQL生成方言（クォーティング・LIMIT/OFFSET構文等）もConfigEngineが吸収し、list-engine/record-edit-engineへ提供する
- D. 上記の組み合わせ、または別の範囲（自由記述）
- X. Other (please specify)

[Answer]: A + C（Bは不採用）。schema-introspectorが読み取ったDBメタデータの型名をConfigEngine内部の論理型へ正規化する変換に加え、物理層のSQL生成方言（クォーティング・LIMIT/OFFSET構文等）もConfigEngineが吸収し、list-engine/record-edit-engineへ提供する。楽観ロック対象列の自動検出ルールのRDBMS方言別吸収（B）は対象外とし、楽観ロック対象列の有無はTableConfig.optimisticLockColumnとして明示的に設定される値をそのまま用いる（方言ごとの自動検出ロジックは持たない）

## Q3: バリデーションルールの表現形式

ColumnConfigの`validationRule`属性（文字列）の具体的な構造はDomain Design/Contract Designでは未確定です。機能設計として、どのようなバリデーション種別・表現形式を採用しますか。

- A. 構造化データ（JSON等）で、ルール種別（required/minLength/maxLength/min/max/pattern等）ごとにキーを持つ。エラーメッセージはi18nキーで指定する
- B. Aと同様だが、エラーメッセージは固定文字列（i18nキーではなく直接メッセージ）で保持する
- C. 単一の正規表現文字列のみをサポートし、必須・長さ等の基本検証は別途固定ロジックで行う
- X. Other (please specify)

[Answer]: A. 構造化データ（JSON等）で、ルール種別（required/minLength/maxLength/min/max/pattern等）ごとにキーを持つ。エラーメッセージはi18nキーで指定する

## Q4: select/radio編集部品の静的選択肢定義方法

FR1.5は「select/radio編集部品のうちFK参照によるものは、選択肢の名称解決のみ実行時に動的取得する。それ以外の設定は静的設定に従う」としています。FK参照でない静的選択肢（例: ステータス区分のプルダウン）は、ColumnConfigのどこにどのような形で保持しますか。

- A. `validationRule`とは別に、select/radio編集部品専用の選択肢定義（`value`と`displayName`（i18nキー）のペアの配列）をColumnConfigの一部として新設する
- B. FK参照ではない静的選択肢もFK参照と同様の仕組み（参照先テーブルを設定不要なマスタテーブルとして internal に用意）で表現する
- C. 上記以外の方式（自由記述）
- X. Other (please specify)

[Answer]: 基本的にAの方針としたいが、displayNameがリソースファイルに設定済みのi18nキーに限定されるのはイマイチ。i18nのキーと文字列(言語ごと)を管理画面で登録できたりしないだろうか？（新規要件であることは承知している）

## Q4 Follow-up: i18nキー・翻訳テキストの管理画面での登録編集について

Q4の回答は、静的選択肢のdisplayNameに限らず、より広く「i18nキーと言語別テキストを管理画面から登録・編集できるようにしたい」という新規要件の提案です。これはビルド成果物として翻訳リソースファイルを用意するという既存の決定（`inception/units-generation/unit-of-work-story-map.md`のFR10.2の扱い、および`project.md`学習事項「実行時コンポーネントではなくビルド成果物に相当するFRはN/A」）の見直しに関わるため、対象範囲を確認します。

- A. 今回はQ4の静的選択肢のdisplayNameに限定し、それを「管理画面から登録・編集できる翻訳エントリ（i18nキー＋言語別テキストのペア）」としてConfigEngineの内部設定DBに保持する新機能とする。画面ラベル・バリデーションメッセージ等、他のi18nキーは引き続きビルド成果物の翻訳リソースファイルのままとする（対象範囲を静的選択肢のdisplayNameのみに限定した小さな追加スコープ）
- B. Aと同じ管理画面編集の仕組みを、静的選択肢のdisplayNameだけでなく、TableConfig/ColumnConfigのdisplayNameやバリデーションメッセージ等、ConfigEngineが管理するi18nキー全般に適用する（対象範囲を拡大）
- C. 今回のconfig-engine機能設計ではQ4の静的選択肢のdisplayNameも既存の決定（ビルド成果物の翻訳リソース）のままとし、管理画面でのi18nキー・テキスト編集機能は別途の新規要件として後続のインタビュー・スコープ判断に委ねる（今回はスコープに含めない）
- X. Other (please specify)

[Answer]: B. 静的選択肢のdisplayNameに限らず、ConfigEngineが管理するi18nキー全般（TableConfig/ColumnConfigのdisplayName、validationRuleのエラーメッセージ、select/radioの選択肢displayName等、Q6で確定した命名規則のi18nキー）を対象に、管理画面から登録・編集できるようにする（Batch 2の回答でCからBへ変更）

### Q4 Follow-up 確定内容（矛盾確認後の最終合意）

FR10.2（日英翻訳リソース）は、これまで「実行時コンポーネントではなくビルド成果物」としてUnit割当対象外（N/A）と整理されていた（`unit-of-work-story-map.md`・`project.md`学習事項）。今回の合意により、この既存決定を覆すのではなく、**適用範囲を明確化**する形で整理する。

- **基盤（エンジン）層のi18n**（共通ボタンラベル「保存」「キャンセル」、汎用エラーメッセージ、ログイン画面文言など、業務設定プロファイルに依存せず変わらない部分）: 引き続き**ビルド成果物の翻訳リソースファイル**で管理する（FR10.2の既存決定はこの範囲に限定して適用される）。
- **業務設定層のi18n**（TableConfig/ColumnConfigの表示名、select/radioの選択肢displayName、バリデーションメッセージ等、Q6の命名規則に従うi18nキー）: **ConfigEngineの内部設定DBにデータとして保持し、管理画面から登録・編集可能**にする。「単一のアプリ本体を設定の入れ替えだけで複数業務に転用できる」という成功定義上、業務プロファイルごとに異なるこれらの文言をビルド時固定リソースとして持つことはできないため、データとして扱う必要がある。

この結果、config-engineの機能設計には、業務設定層のi18nキー・言語別テキストを保持する新エンティティ（例: TranslationEntry）と、これを管理画面から編集するための操作（内部インタフェースおよび新規REST APIの要否を含む）を含める。

## Q5: 設定定義のfail-fast検証における必須プロパティの範囲

FR1.3は「設定定義自体に誤り（必須プロパティ欠落等）がある場合、起動時・設定読込時にfail fastで検知する」としています。TableConfig/ColumnConfigそれぞれについて、fail fast検証で必須とする最小限のプロパティ（欠落時に`ConfigValidationException`を送出する対象）はどれですか。

- A. TableConfig: schemaName/tableName/displayName。ColumnConfig: tableConfigId/columnName/displayName/editorType。その他（displayOrder/format/validationRule/visibility等）は未設定時に妥当なデフォルト値で補う
- B. Aに加え、editorTypeがselect/radioの場合はQ4で定義した選択肢定義（またはFK参照先情報）の設定も必須とする
- C. 上記以外の必須項目セット（自由記述）
- X. Other (please specify)

[Answer]: B. Aに加え、editorTypeがselect/radioの場合はQ4で定義した選択肢定義（またはFK参照先情報）の設定も必須とする（※displayNameを必須項目に含む前提だったが、下記Q5 Follow-upによりdisplayNameフィールド自体を廃止したため、必須項目からは除外される。最終的な必須項目セットはQ5 Follow-upの回答を参照）

## Q5 Follow-up: displayNameフィールドの意味論とi18nキーの関係

ユーザー指摘: `components.md`のTableConfig/ColumnConfigは`displayName`属性を持つが、FR10.1（表示名はi18nキーによって多言語対応可能な構造とする）・Q6（i18nキー命名規則の確定）を踏まえると、`displayName`は「表示テキストそのもの」ではなく「i18nキー」であるべきであり、そのままでは矛盾しうる。この点を整理する。

- A. `displayName`フィールドの意味論を「テキストそのもの」から「i18nキー」へ変更する。値はQ6の命名規則に従うi18nキー文字列（例: `table.public.products.label`）とし、実際の多言語テキストはTranslationEntry（Q4-followupで新設）から解決する。fail-fast検証（Q5）の対象は変わらず「`displayName`（＝i18nキー）が設定されていること」のままとする（フィールド名はそのまま、意味論のみ変更）
- B. `displayName`フィールド自体を廃止する。i18nキーはQ6の命名規則（`table.{schemaName}.{tableName}.{columnName}.label`等）から`schemaName`/`tableName`/`columnName`を用いて機械的に導出できるため、TableConfig/ColumnConfigに明示的に保持する必要がない。この場合Q5のfail-fast必須項目から`displayName`は除外される
- C. `displayName`フィールドは残すが二重の意味を持たせる。通常はi18nキー参照として使うが、対応するTranslationEntryが未登録の場合のフォールバック表示用の生テキストとしても使える
- X. Other (please specify)

[Answer]: B. displayNameフィールドを廃止する。i18nキーはQ6の命名規則（`table.{schemaName}.{tableName}.label` / `table.{schemaName}.{tableName}.{columnName}.label`）でschemaName/tableName/columnNameから機械的に導出できるため、TableConfig/ColumnConfigに明示的なdisplayNameフィールドを持たない。これによりQ5のfail-fast必須項目セットは最終的に「TableConfig: schemaName/tableName。ColumnConfig: tableConfigId/columnName/editorType（+select/radioの場合は選択肢定義）」となる（displayNameを除外）

## Q6: i18nキー構造の命名規則

`unit-of-work-story-map.md`はFR10.1（i18nキー構造化）をconfig-engineユニットの担当としています。表示名・バリデーションメッセージのi18nキーは、どのような命名規則で構造化しますか。

- A. `table.{tableConfigId}.{columnName}.label` / `table.{tableConfigId}.{columnName}.validation.{ruleType}` のように、テーブル・カラム・ルール種別を階層的に含むキー命名規則とする
- B. Aと似ているが識別子にconfig-engineが発行する内部ID（`tableConfigId`/`columnConfigId`）ではなく`schemaName`/`tableName`/`columnName`をそのまま用いる
- C. 上記以外のキー命名規則（自由記述）
- X. Other (please specify)

[Answer]: B. 識別子にconfig-engineが発行する内部ID（tableConfigId/columnConfigId）ではなくschemaName/tableName/columnNameをそのまま用いる階層キー（例: `table.{schemaName}.{tableName}.{columnName}.label` / `table.{schemaName}.{tableName}.{columnName}.validation.{ruleType}`）

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
