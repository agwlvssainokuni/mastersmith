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

# Business Rules — config-import-export (U9)

```yaml
rules:

  - id: BR9.1
    statement: >
      設定一式のエクスポート(GET /api/config/export)は、次の3つのセクションを、1つのJSONファイルとして書き出す。
      (1)schema: スキーマ定義(テーブル・カラムの表示設定)と翻訳(config-engineのC9`getExportableConfigSet`)。
      (2)menu: 業務メニューの構成(menu-navigationのC12`getExportableMenuStructure`)。(3)rbac: ロール・グループ・
      グループとロールの対応・主権限・補助権限(permission-engineの、本ユニットが要求する書き出しの内部契約、
      functional-spec.mdの追補一覧)。次のものは、含めない。ユーザー・ユーザーのグループ所属(user-managementが所有し、
      環境ごとに異なる)、パスワードなどの認証情報、監査ログ、業務データ(行データ)、管理メニュー4項目(永続化されない)、
      業務データ用RDBMSの接続情報(環境変数で与えられ、設定として保持されない)。
    category: policy
    applies_to: [ConfigDocument, SchemaSection, MenuSection, RbacSection]
    trigger: "GET /api/config/export呼び出し時"
    logic: "IF 認可(BR9.4)に成功 THEN 3つのセクションを、BR9.5の一貫した読み取りで取得し、BR9.3の自然キーに変換して、BR9.2の形式で返す。"
    violation_behaviour: "N/A(範囲の定義)。認可の失敗は、BR9.4"
    source: "FR11.1, FR11.2, functional-design-questions.md Q1"

  - id: BR9.2
    statement: >
      設定ファイルは、UTF-8のJSONで、トップレベルに次の項目を持つ。`formatVersion`(整数。MVPは1)、`exportedAt`(書き出し日時、
      ISO 8601のUTC)、`appVersion`(書き出したアプリケーションのバージョン)、`schema`・`menu`・`rbac`(BR9.1の3つのセクション。
      いずれも必須で、空の場合は空の配列を持つ)。`exportedAt`と`appVersion`は参考情報で、取り込みの検証に使わない。
      エクスポートの応答は、`Content-Type: application/json`とし、ブラウザがファイルとして保存できるよう、
      `Content-Disposition`で保存名(`mastersmith-config-<書き出し日時>.json`)を示す。
    category: constraint
    applies_to: [ConfigDocument]
    trigger: "エクスポート時の書き出し、インポート時の読み取り"
    logic: "IF エクスポート THEN トップレベルの項目を上記のとおり設定して書き出す。IF インポート THEN BR9.8に従い、形式を確認する。"
    violation_behaviour: "N/A(形式の定義)。インポート時の形式の誤りは、BR9.6・BR9.8"
    source: "FR11.1, functional-design-questions.md Q10"
    assumption: "保存名(Content-Disposition)の形式は、機能設計で置いた仮定。契約(C7)への反映は、Code Generation着手時の追補で確認する"

  - id: BR9.3
    statement: >
      設定ファイルの中の参照は、すべて**自然キー**で表し、内部ID(tableConfigId・columnConfigId・menuItemId・roleId・groupId・
      primaryPermissionId)は、ファイルに含めない。自然キーは、次のとおり。テーブル=(schemaName, tableName)、
      カラム=(schemaName, tableName, columnName)、翻訳=(i18nKey, locale)、ロール・グループ=名前。メニュー項目は、
      自然キーを持たず、入れ子の構造で親子を表す(内部IDを外部から参照されないため、BR9.14)。権限の対象(スコープ)は、
      scopeType+自然キー(schemaName・tableName・columnName)で表す。これにより、開発環境で作った設定を、別の環境へ移せる。
    category: constraint
    applies_to: [ConfigDocument, TableRef, PermissionScope, MenuEntry]
    trigger: "エクスポート時(内部ID→自然キー)、インポート時(自然キー→内部ID)"
    logic: "IF エクスポート THEN 各ユニットの内部IDを、自然キーへ変換して出力する。IF インポート THEN 自然キーを、現在の環境の内部IDへ解決する(新規は採番する。BR9.14)。"
    violation_behaviour: "自然キーが解決できない(参照先がファイルにない)場合は、BR9.13の参照整合の誤りとして、422で返す"
    source: "FR11.1, FR1.6, functional-design-questions.md Q2"

  - id: BR9.4
    statement: >
      エクスポート・インポートの両方で、サーバー側で必ず認可を再検証する(project.md Mandated。画面の表示制御だけに依存しない)。
      認証済みの操作者(共有契約C15の`OperatorContext`)を、要求ごとに1回取得し、操作者が解決できない場合は、401を返す。
      操作者が解決できた場合、permission-engineのC10`canAccessScreen(activeRoleId, "config-import-export")`で判定し、
      falseなら403(RFC 9457)を返す。アクティブロールがnull(未選択)の場合は、本ユニットで拒否せず、そのままC10へ渡す
      (authentication-serviceのBR5.12。C10がfail closedで判定する)。RBAC設定が1件もない初期状態の例外(permission-engineのBR3.13)は、
      C10の判定に従う(本ユニットは解釈しない)。
    category: authorization
    applies_to: [ConfigDocument]
    trigger: "GET /api/config/export・POST /api/config/import呼び出しの入口"
    logic: "IF 操作者が解決できない THEN 401。ELSE IF canAccessScreen(activeRoleId, \"config-import-export\")=false THEN 403。ELSE 続行。"
    violation_behaviour: "401 Unauthorized、または403 Forbidden(RFC 9457のProblemDetails)"
    source: "FR3.3, FR11.1, project.md Mandated, contract-summary.md C7・C10・C15, authentication-serviceのBR5.12"

  - id: BR9.5
    statement: >
      エクスポートは、3つのセクションを、**1つの時点の内容として整合するように**取得する(同じ読み取りの範囲で、
      schema→menu→rbacの順に読む)。書き出しの途中で別の管理操作(取り込み・メニューの変更など)が確定しても、
      出力したファイルの中で、セクション間の参照(メニューの遷移先・権限の対象・FK参照)が食い違わないようにする。
      エクスポートは、内部設定DBを変更せず、監査イベントを発行しない(BR9.16)。
    category: constraint
    applies_to: [ConfigDocument]
    trigger: "エクスポートのデータ取得時"
    logic: "IF エクスポート THEN 読み取り専用の、1つの一貫した範囲で、3つのセクションを取得する。"
    violation_behaviour: "N/A(不変条件)。内部設定DBの障害は、503を返す"
    source: "FR11.2, functional-design-questions.md Q9(エクスポートは記録しない)"
    assumption: "『1つの読み取りの範囲』の実現方法(読み取り専用トランザクションの分離レベル)は、NFR設計・Code Generationで確定する"

  - id: BR9.6
    statement: >
      インポート(POST /api/config/import)は、認可(BR9.4)に成功したあと、次の順に検証し、**取り込みの反映の前に、すべての検証を終える**。
      (1)構文: リクエスト本体が、JSONとして読めること。(2)形式: `formatVersion`が対応する値であること(BR9.8)。
      (3)構造: 必須項目・型・許容値・一意性(entities.mdの制約)。(4)参照整合: セクションをまたぐ参照(BR9.13)。
      (5)各ユニットの検証: config-engine(BR1.1〜BR1.4)・menu-navigation・permission-engineの、反映しない検証(BR9.10)。
      (6)権限昇格の判定(BR9.11)。(7)取り込み後のRBACが空にならないこと(BR9.12)。検証の誤りは、**1件目で止めず、すべて集めて**返す(BR9.7)。
      構文の誤り(1)と、形式の誤り(2)は、それ以降の検証が意味を持たないため、その時点で、その誤りだけを返す。
    category: validation
    applies_to: [ConfigDocument, ImportError]
    trigger: "POST /api/config/importの入口(認可の後)"
    logic: "IF (1)または(2)に失敗 THEN その誤りを返して終了。ELSE (3)〜(7)を順に行い、誤りを集める。誤りが1件以上なら、何も反映せずに、422で返す。"
    violation_behaviour: "422 Unprocessable Entity(BR9.7の形式)。何も反映しない"
    source: "FR1.3, FR11.1, project.md Mandated(設定不備のfail fast), functional-design-questions.md Q4・Q6"

  - id: BR9.7
    statement: >
      検証の誤りは、RFC 9457のProblemDetailsの`errors[]`に、**全件を集めて**返す。ただし、1回の応答に含める誤りは、最大**100件**とし、
      超えた場合は、打ち切ったこと(総件数と、返した件数)を、応答に含める。各誤りは、`field`(JSON上の位置。例
      `schema.tables[3].columns[2].editorType`)と、`message`(翻訳用のメッセージキー(i18nキー)。必要な場合だけ`params`に値を持つ)からなる。
      入力値そのもの(ファイルの内容)・スタックトレース・内部の型名は、応答に含めない。開発者向けの詳細ではなく、
      フィールド単位のエラーとして返す(project.md Mandated)。
    category: validation
    applies_to: [ImportError]
    trigger: "検証の誤りを返すとき"
    logic: "IF 誤りの総数 > 100 THEN 先頭の100件と、打ち切ったこと(総件数)を返す。ELSE 全件を返す。"
    violation_behaviour: "N/A(応答の規約)"
    source: "FR1.3, NFR7(i18nキー), project.md Mandated, functional-design-questions.md Q6"

  - id: BR9.8
    statement: >
      `formatVersion`が、本ユニットが対応する値(MVPは1)でない場合、または、欠落している場合は、ファイル全体を、422で拒否する
      (以降の検証は行わない。BR9.6)。ファイルの中の**未知のプロパティ**(すべての階層)は、エラーにせず、**無視**する
      (将来のバージョンで項目が増えても、読める前方互換のため。Q10=A)。ただし、必須の項目の欠落は、構造の誤り(BR9.6の(3))である。
    category: validation
    applies_to: [ConfigDocument]
    trigger: "インポートの形式の確認時"
    logic: "IF formatVersionが欠落、または対応しない値 THEN 422(メッセージキー config.import.format.unsupported)。ELSE 続行(未知のプロパティは無視)。"
    violation_behaviour: "422 Unprocessable Entity"
    source: "FR1.3, functional-design-questions.md Q10"

  - id: BR9.9
    statement: >
      インポートは、**全置換**である(Q3=A)。ファイルにない項目は削除し、取り込み後の設定を、ファイルの内容と完全に一致させる。
      対象は、テーブルの設定・カラムの設定・翻訳・メニュー項目・ロール・グループ・グループとロールの対応・主権限・補助権限である。
      既存の項目との照合は、自然キー(BR9.3)で行い、既存の項目は、内部IDを維持して更新し、ファイルにない項目は削除し、
      ファイルだけにある項目は、新規に採番して追加する(BR9.14)。ただし、カラムの`isPrimaryKey`は、ファイルの値にかかわらず、
      **既存のカラムでは現在の値を維持し、新規のカラムではfalseにする**(config-engineのBR1.14。主キー列情報は、スキーマ探索でのみ設定される)。
      値が変わらない項目は、「更新」に数えない(BR9.17の件数)。
    category: policy
    applies_to: [SchemaSection, MenuSection, RbacSection]
    trigger: "取り込みの反映時(BR9.10)"
    logic: "IF 自然キーが既存の項目と一致 THEN 内部IDを維持して値を更新する(isPrimaryKeyは維持)。ELSE IF ファイルだけにある THEN 新規に追加する。既存にあり、ファイルにない項目は、削除する。"
    violation_behaviour: "N/A(意味の定義)"
    source: "FR11.1, FR11.2, config-engineのBR1.14, functional-design-questions.md Q3"

  - id: BR9.10
    statement: >
      1回の取り込み**全体を、1つの単位**として扱う(Q4=A)。まず、3つの内容(schema・menu・rbac)のすべてを、**何も反映せずに検証**し
      (BR9.6の(5)。各ユニットに、検証だけを行う内部契約を要求する。functional-spec.mdの追補一覧)、すべてに合格した場合に限り、
      **1つのトランザクションで反映する**。反映の順序は、schema(テーブル・カラム・翻訳)→ロール・グループ→メニュー→権限
      (メニューはテーブルを、権限はロールとテーブル・カラムを参照するため)。反映の途中で失敗した場合は、すべてのセクションの
      反映を元に戻す(部分的な反映を残さない)。反映の確定後にだけ、成功の監査イベント(BR9.16)と、各ユニットの
      変更イベントを発行する。
    category: constraint
    applies_to: [ConfigDocument, ImportContext]
    trigger: "検証のすべてに合格したとき"
    logic: "IF 検証に1件でも誤りがある THEN 反映しない。ELSE 1つのトランザクションで、schema→ロール・グループ→メニュー→権限の順に反映し、いずれかで失敗したら全体をロールバックする。"
    violation_behaviour: "反映中の失敗は、全体をロールバックし、内部の障害として、500または503を返す(BR9.21)。失敗の監査イベント(UNEXPECTED)を発行する"
    source: "FR1.3, FR11.1, project.md Mandated, functional-design-questions.md Q4"

  - id: BR9.11
    statement: >
      RBAC設定の、権限昇格の判定(permission-engineのBR3.8)は、**取り込みを始めた時点の設定を基準に**、検証の段階で、すべてのエントリについて
      先に行う(Q13=A)。操作者の実効権限は、取り込み開始時点の値で固定し、反映の途中で権限が削除・追加されても、判定に影響しない。
      ブートストラップ状態(permission-engineのBR3.13、主権限が0件の初期状態)であったかも、取り込み開始時点の値で固定し
      (`ImportContext.bootstrapAtStart`)、開始時点で初期状態なら、昇格の判定を行わない(初回のRBAC投入を許すため)。
      昇格が**1件でも**検出された場合は、取り込み**全体を中止**し(Q5=A)、何も反映せず、422で、該当のエントリをすべて一覧で返す
      (BR9.7の形式。メッセージキー config.import.rbac.escalation)。
    category: authorization
    applies_to: [PrimaryPermissionEntry, AuxiliaryPermissionEntry, ImportContext]
    trigger: "取り込みの検証段階(BR9.6の(6))"
    logic: "IF bootstrapAtStart=true THEN 判定しない。ELSE 各エントリについて、操作者(operatorActiveRoleId)の取り込み開始時点の実効権限を基準に、permission-engineの昇格判定を行い、上回るエントリを、すべて集める。1件以上なら、422で全体を中止する。"
    violation_behaviour: "422 Unprocessable Entity(全体を中止。権限昇格の防止、project.md Forbidden)"
    source: "FR3.4, project.md Forbidden・Mandated, permission-engineのBR3.8・BR3.9・BR3.13, functional-design-questions.md Q5・Q13"

  - id: BR9.12
    statement: >
      取り込みの結果、**主権限(primaryPermissions)が0件になる**ファイルは、422で拒否する(Q14b=A)。RBAC設定が一度も投入されていない
      初回の取り込みを含め、取り込み後の主権限は、1件以上でなければならない。理由: permission-engineの初期状態の判定
      (BR3.13、実装は主権限の行が0件かどうか)が、主権限を空にする取り込みで再び有効になると、認証済みの誰でも設定管理画面へ到達し、
      昇格のチェックなしで権限を付与できる。これは、project.md Forbidden(権限管理者による明示的な操作を経ない権限昇格)を破る。
    category: constraint
    applies_to: [RbacSection]
    trigger: "取り込みの検証段階(BR9.6の(7))"
    logic: "IF ファイルのprimaryPermissionsが0件 THEN 422(メッセージキー config.import.rbac.empty)で全体を拒否する。"
    violation_behaviour: "422 Unprocessable Entity"
    source: "FR3.4, project.md Forbidden, permission-engineのBR3.13, functional-design-questions.md Q14・Q14b"

  - id: BR9.13
    statement: >
      セクションをまたぐ参照は、すべて、**ファイルの中で**解決できなければならない(取り込み後の設定の中に、参照先が存在すること)。
      (1)メニューの`targetTable`は、schemaセクションのテーブルであること。(2)権限のTABLE・COLUMNの対象は、schemaセクションの
      テーブル・カラムであること。SCHEMAの対象は、schemaセクションのスキーマ名、またはpermission-engineが管理する予約スキーマ名
      (`__system__:`で始まる名前。本ユニットは解釈せず、permission-engineの検証に委ねる。BR9.20)であること。
      (3)権限の`roleName`・グループの`roleNames`は、rbacセクションのロールであること。(4)`fkReference`の参照先の
      スキーマ・テーブル・値の列・名称の列は、schemaセクションに存在すること。(5)`optimisticLockColumn`は、そのテーブルの
      columnsに含まれること。参照先が見つからない場合は、その参照を持つ要素の位置を`field`として、誤りに加える。
    category: validation
    applies_to: [MenuEntry, PermissionScope, PrimaryPermissionEntry, AuxiliaryPermissionEntry, GroupEntry, FkReference, TableEntry]
    trigger: "取り込みの検証段階(BR9.6の(4))"
    logic: "IF 参照先が、ファイルの中に存在しない THEN 誤りに加える(メッセージキー config.import.reference.notFound)。"
    violation_behaviour: "422 Unprocessable Entity(誤りとして集める。BR9.7)"
    source: "FR1.3, FR11.1, functional-design-questions.md Q2・Q3"

  - id: BR9.14
    statement: >
      取り込み後の内部IDは、次のとおりとする。既存の項目(テーブル・カラム・ロール・グループ)は、自然キーが一致する限り、
      **内部IDを維持する**(他のユニットが、内部IDで参照している設定・権限・監査ログの対象を、壊さないため)。ファイルだけにある項目は、
      新規に採番する。メニュー項目は、自然キーを持たず、全置換のため、取り込みのたびに**再採番される**(メニュー項目のIDは、権限の判定
      (メニュー項目の権限は、遷移先のテーブルの権限で判定される)や、他のユニットから参照されないため)。
      ファイルにないため削除された項目を、あとで同じ自然キーで再度取り込んだ場合は、新規として採番される(以前のIDは復元されない)。
    category: policy
    applies_to: [TableEntry, ColumnEntry, RoleEntry, GroupEntry, MenuEntry]
    trigger: "取り込みの反映時"
    logic: "IF 自然キーが既存の項目と一致 THEN 内部IDを維持する。ELSE 新規に採番する。メニュー項目は、常に再採番する。"
    violation_behaviour: "N/A(不変条件)"
    source: "FR11.2, functional-design-questions.md Q2・Q3, menu-navigationのBR6.x(メニュー項目のIDは外部から参照されない)"
    assumption: "『メニュー項目のIDは、外部から参照されない』は、現時点の実装(権限の判定にtableConfigIdを使う)からの確認。他ユニットがmenuItemIdを参照する設計になった場合は、見直す"

  - id: BR9.15
    statement: >
      取り込みの実行者は、共有契約C15の`OperatorContext`から得た、認証済みの操作者(userIdとactiveRoleId)とする(Q8=A)。
      本ユニットは、(a)permission-engineの`actorRoleId`(権限昇格の判定・権限の割当の基準)に、操作者のactiveRoleIdを渡し、
      (b)自身が発行する、取り込みのサマリ監査イベント(BR9.16)に、操作者のuserIdとactiveRoleIdを記録する。
      config-engine・menu-navigationの取り込みの契約(C9の`importConfigSet`・C12の`importMenuStructure`)には、操作者の引数を
      追加しない。そのため、それらのユニットが発行する個別の変更イベントの`actor`は、`"system"`のままになる(既知の制約。
      config-engineの機能設計に記録された未解決事項の、部分的な解消にとどまる)。
    category: policy
    applies_to: [ImportContext, ConfigImportExecutedEvent]
    trigger: "取り込みの開始時(ImportContextの作成)"
    logic: "IF 操作者が解決できる THEN ImportContextに、userIdとactiveRoleIdを保持し、以降の呼び出しと、監査イベントに用いる。"
    violation_behaviour: "操作者が解決できない場合は、BR9.4の401"
    source: "FR8.1, contract-summary.md C15, config-engineのfunctional-spec.md(W5・W6の未解決事項), functional-design-questions.md Q8"

  - id: BR9.16
    statement: >
      取り込みは、**成功・失敗の両方について、1回につき1件**、`ConfigImportExecutedEvent`を、audit-loggingへ発行する(Q9=A、FR8.1)。
      内容は、操作者(userId・activeRoleId)・日時・結果(SUCCESS/FAILURE)・成功時のセクションごとの件数(追加・更新・削除)・
      失敗時の失敗の理由の分類・検出した誤りの総数に限り、ファイルの内容(設定の値・名前)は、含めない(監査ログは、無期限に保持される。FR8.3)。
      失敗の理由の分類は、MALFORMED(構文)・UNSUPPORTED_FORMAT(形式の版)・VALIDATION_ERROR(構造・参照整合・各ユニットの検証の誤り)・
      ESCALATION_DENIED(権限昇格)・RBAC_EMPTY(主権限が0件)・UNEXPECTED(反映中の想定外の失敗)のいずれか。
      成功のイベントは、反映が確定したあとに発行し、失敗のイベントは、反映を行っていないため、判定の時点で発行する。
      イベントの発行が失敗しても、取り込みの結果(すでに確定した反映、または返した応答)には影響させない(失敗は、警告のログだけに残す)。
      **エクスポートでは、イベントを発行しない**。権限の変更については、permission-engineが、取り込み1回につき1件のサマリイベントを、別に発行する
      (permission-engineのBR3.11)。
    category: policy
    applies_to: [ConfigImportExecutedEvent]
    trigger: "取り込みの結果が決まったとき(成功の確定後、または失敗の判定時)"
    logic: "IF 取り込みが成功(反映を確定) THEN SUCCESSのイベントを1件発行する。IF 取り込みが失敗(検証の誤り・中止・反映中の失敗) THEN FAILUREのイベントを、失敗の分類つきで1件発行する。"
    violation_behaviour: "イベントの発行の失敗は、警告のログに残し、取り込みの結果に影響させない"
    source: "FR8.1, FR8.2, FR8.3, components.md(ConfigImportExport→AuditLogging: event), functional-design-questions.md Q9"

  - id: BR9.17
    statement: >
      取り込みが成功した場合、200を返し、応答本体に、セクション(schema・translations・menu・roles・groups・primaryPermissions・
      auxiliaryPermissions)ごとの、追加・更新・削除の件数を含める(ImportResult)。応答のステータスと本体は、C7の`200`(インポート成功)を、
      件数で補うものである。失敗の応答は、次のとおり。401(操作者を解決できない)・403(権限不足)・422(構文・形式・構造・参照整合・
      各ユニットの検証・権限昇格・主権限が0件の誤り)・500または503(内部の障害。BR9.21)。いずれも、RFC 9457のProblemDetailsとする。
    category: policy
    applies_to: [ImportResult]
    trigger: "取り込みの応答を返すとき"
    logic: "IF 取り込みが成功 THEN 200とImportResultを返す。ELSE 失敗の種類に応じたステータスのProblemDetailsを返す。"
    violation_behaviour: "N/A(応答の規約)"
    source: "FR11.1, contract-summary.md C7"
    assumption: "200の応答本体に件数を含めることは、C7に定義がなく、機能設計で置いた仮定。契約(C7)への追補で確認する"

  - id: BR9.18
    statement: >
      サーバーは、**検証だけを行うモード(dryRun)を提供しない**(Q7=A)。取り込みの確認モーダルは、フロントエンドが手元で得られる情報
      (ファイル名・大きさ・セクションごとの項目数)に加えて、フロントエンドが、現在の設定(`GET /api/config/export`)を取得して、
      ファイルの内容と**比較して**計算する追加・更新・削除の件数を表示する(全置換で、意図せず削除される項目を、利用者が事前に確認できるようにするため)。
      サーバー側には、確認のための追加のAPIを設けない。確認のあとの取り込み要求は、確認の時点のファイルと同一の内容でなければならないが、
      確認の時点の設定が、取り込みの時点まで変わらないことは、保証しない(BR9.19)。
    category: policy
    applies_to: [ConfigDocument]
    trigger: "取り込み前の確認(フロントエンド)"
    logic: "IF 利用者がファイルを選択 THEN フロントエンドが、現在のエクスポートとの比較を行い、確認モーダルに表示する。承認後に、POST /api/config/importを呼ぶ。"
    violation_behaviour: "N/A(責務の分担)"
    source: "FR11.1, refined-mockups(確認モーダル), functional-design-questions.md Q7"

  - id: BR9.19
    statement: >
      次の3点は、MVPでは、**対策を設けず、リスクとして受け入れる**(いずれも、利用者が明示的に選択した。Q11=C・Q12=C)。
      (1)取り込みの同時実行の排他は、設けない。2件の取り込みが同時に実行された場合は、データベースのトランザクションの結果に従う
      (BR9.10により、それぞれは1つのトランザクションで反映されるが、結果は、後から確定した方が勝つ。デッドロックなどで失敗した場合は、
      BR9.21の内部の障害になる)。(2)リクエスト本体の大きさの上限は、設けない(想定規模は、テーブル数十個・カラム数百個・メニュー数百項目)。
      (3)取り込みによって、操作者自身が設定管理画面に到達できなくなること(自己の締め出し)を、防がない。締め出された場合の回復は、
      内部設定DBの修復による(運用上の手順。MVPのスコープ外)。なお、主権限が0件になる取り込みは、初期状態の例外の再有効化(権限昇格)を
      防ぐため、拒否する(BR9.12)。
    category: policy
    applies_to: [ConfigDocument, ImportContext]
    trigger: "取り込みの実行時"
    logic: "IF 同時に2件以上の取り込み THEN 排他せず、DBのトランザクションに任せる。IF 大きさが大きい THEN 制限しない。IF 取り込み後に操作者が到達できなくなる THEN 拒否も警告もしない。"
    violation_behaviour: "N/A(受け入れたリスク)。functional-spec.mdの「残余リスク」に記録する"
    source: "functional-design-questions.md Q11・Q12"

  - id: BR9.20
    statement: >
      本ユニットは、共通エンジン層の一部として、特定業務固有のテーブル名・カラム名・ロール名・業務ルールを、コードに持たない
      (FR1.6・NFR8)。設定ファイルの中の名前は、すべて、データとして扱い、意味を解釈しない。permission-engineが管理する
      予約スキーマ名(管理系画面の権限のための`__system__:`で始まる名前)も、本ユニットは解釈せず、権限の対象として、そのまま
      permission-engineの検証に委ねる。設定の値の検証(editorTypeの許容値・validationRuleの形式・choiceOptionsとfkReferenceの排他など)は、
      config-engineの規則に委ね、本ユニットは、重複して持たない。
    category: constraint
    applies_to: [ConfigDocument]
    trigger: "実装・検証の全般"
    logic: "IF 名前・値の意味の検証が必要 THEN それを所有するユニット(config-engine・menu-navigation・permission-engine)の検証を呼び出す。"
    violation_behaviour: "N/A(不変条件)"
    source: "FR1.6, NFR8, project.md Mandated"

  - id: BR9.21
    statement: >
      エラーは、次の2種を区別して扱う(project.md Mandated)。(1)**入力の誤り**(設定ファイルの内容の誤り)は、422で、フィールド単位のエラー
      (BR9.7)として返す。(2)**内部の障害**(内部設定DBの障害・反映中の想定外の失敗・他ユニットの想定外の例外)は、開発者向けの詳細
      (スタックトレース・内部の型名・SQL)を応答に含めず、汎用のメッセージで、503(内部設定DBの障害・接続の取得の失敗)または
      500(それ以外)を返す。内部の障害の場合は、反映を全体ロールバックし、失敗の監査イベント(UNEXPECTED、BR9.16)を発行する。
    category: constraint
    applies_to: [ImportError, ConfigImportExecutedEvent]
    trigger: "エラーが発生したとき"
    logic: "IF 入力の誤り THEN 422+errors[]。ELSE IF 内部設定DBの障害 THEN 503。ELSE 500。"
    violation_behaviour: "N/A(規約)"
    source: "FR1.3, project.md Mandated, authentication-serviceのNFR4.2(内部設定DBの障害の503)"
```

## ルール概要

| ID | カテゴリ | 概要 |
|---|---|---|
| BR9.1 | policy | エクスポートの範囲(schema・menu・rbac。ユーザー・認証情報・監査ログなどは含めない) |
| BR9.2 | constraint | 設定ファイルの形式(UTF-8のJSON、formatVersion・exportedAt・appVersion、3つのセクション、保存名) |
| BR9.3 | constraint | 参照はすべて自然キー(内部IDを含めない)。別の環境へ移せる |
| BR9.4 | authorization | 操作者の解決(401)と`canAccessScreen`(403)による、サーバー側の認可の再検証 |
| BR9.5 | constraint | エクスポートは、1つの時点として整合する読み取り。DBを変更せず、監査イベントを発行しない |
| BR9.6 | validation | インポートの検証の順序(構文→形式→構造→参照整合→各ユニットの検証→昇格→RBAC空)。反映の前にすべて終える |
| BR9.7 | validation | 検証の誤りは、全件を集めて返す(最大100件、打ち切りの表示、位置+i18nキー、入力値を含めない) |
| BR9.8 | validation | formatVersionの確認(未対応は422)と、未知のプロパティの無視 |
| BR9.9 | policy | 全置換(ファイルにない項目は削除)、自然キーでの照合、isPrimaryKeyの維持 |
| BR9.10 | constraint | 取り込み全体を1つの単位: 全検証→全合格のみ1つのトランザクションで反映(schema→ロール・グループ→メニュー→権限)。失敗は全ロールバック |
| BR9.11 | authorization | 権限昇格の判定は、取り込み開始時点の設定を基準に、検証段階で。1件でも検出したら全体を中止 |
| BR9.12 | constraint | 取り込み後に主権限が0件になるファイルは、422で拒否(初期状態の例外の再有効化による昇格の防止) |
| BR9.13 | validation | セクションをまたぐ参照は、ファイルの中で解決できること |
| BR9.14 | policy | 内部IDの維持(既存)・新規の採番。メニュー項目は再採番 |
| BR9.15 | policy | 操作者はOperatorContextから取得し、permission-engineのactorRoleIdと、サマリ監査イベントに記録。C9・C12は変更しない |
| BR9.16 | policy | 取り込み1回につき1件の監査イベント(成功・失敗の両方、失敗の分類、件数)。エクスポートは記録しない |
| BR9.17 | policy | 成功は200+セクション別の件数。失敗は401・403・422・500/503(RFC 9457) |
| BR9.18 | policy | サーバーにdryRunはない。確認モーダルは、フロントエンドが現在のエクスポートと比較して、件数を表示する |
| BR9.19 | policy | 受け入れたリスク: 排他なし・大きさの上限なし・自己の締め出しの防止なし(MVP) |
| BR9.20 | constraint | 業務固有情報のハードコード禁止。名前・値の意味の検証は、所有するユニットに委ねる |
| BR9.21 | constraint | 入力の誤り(422、フィールド単位)と内部の障害(500/503、汎用メッセージ)の区別 |
