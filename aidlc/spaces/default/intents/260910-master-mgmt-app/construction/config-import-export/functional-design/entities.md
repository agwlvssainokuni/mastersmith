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

# Entities — config-import-export (U9)

`inception/domain-design/components.md`のConfigImportExportコンポーネント定義(`entities: []`)のとおり、本ユニットは、**永続化するエンティティを持たない**。設定の実体は、config-engine(スキーマ定義と翻訳)・menu-navigation(業務メニュー)・permission-engine(RBAC)が、内部設定DBに保持している(FR11.2: 内部設定DBを正とする)。本ユニットが扱うのは、次の値オブジェクトである。

- 設定ファイル(`ConfigDocument`)と、その構成要素。他のユニットが持つエンティティを、**環境に依存しない形(自然キー)で写した表現**であり、取り込み時に、他のユニットの内部IDへ解決される。
- 取り込みの実行の文脈・結果・エラー・監査イベント。いずれも、1回の呼び出しの間だけ存在し、永続化しない(監査イベントだけは、audit-loggingが記録する)。

```yaml
entities:
  - name: ConfigDocument
    description: >
      設定一式のJSONファイル全体(値オブジェクト。永続化しない)。エクスポートが作り、インポートが読む。
      ファイルの先頭に、形式を識別する情報(formatVersion・書き出し日時・出力元のバージョン)を持ち(Q10=A)、
      本体は、3つのセクション(スキーマ定義・メニュー構成・RBAC設定)からなる。すべての参照は、自然キーで表す(Q2=A)。
      内部ID(UUID)は、ファイルに含めない。
    attributes:
      - name: formatVersion
        type: integer
        required: true
        allowed_values: [1]
        description: ファイルの形式の版。MVPは1。取り込み時に未対応の値なら、ファイル全体を拒否する(BR9.8)
      - name: exportedAt
        type: string
        required: false
        description: 書き出した日時(ISO 8601、UTC)。参考情報で、取り込みの検証には使わない
      - name: appVersion
        type: string
        required: false
        description: 書き出したアプリケーションのバージョン。参考情報で、取り込みの検証には使わない
      - name: schema
        type: SchemaSection
        required: true
        description: スキーマ定義(テーブル・カラムの表示設定)と翻訳
      - name: menu
        type: MenuSection
        required: true
        description: 業務メニューの構成
      - name: rbac
        type: RbacSection
        required: true
        description: RBAC設定(ロール・グループ・権限)
    entity_constraints:
      - "取り込み時、未知のプロパティ(ファイル全体・各セクション・各要素のすべての階層)は、無視する(Q10=A、前方互換)"
      - "3つのセクションは、いずれも必須。空の場合でも、空の配列として持つ(セクションの欠落は、構造のエラーである)"
      - "内部ID(tableConfigId・columnConfigId・menuItemId・roleId・groupId・primaryPermissionIdなど)は、ファイルに含めない(BR9.3)"
    relationships:
      - target: SchemaSection
        cardinality: "1..1"
        direction: "ConfigDocument 1 -> 1 SchemaSection"
        description: スキーマ定義のセクション
      - target: MenuSection
        cardinality: "1..1"
        direction: "ConfigDocument 1 -> 1 MenuSection"
        description: メニュー構成のセクション
      - target: RbacSection
        cardinality: "1..1"
        direction: "ConfigDocument 1 -> 1 RbacSection"
        description: RBAC設定のセクション

  - name: SchemaSection
    description: >
      config-engineが保持するスキーマ定義(TableConfig・ColumnConfig)と、翻訳(TranslationEntry)を、自然キーで表した
      セクション(値オブジェクト)。C9の`getExportableConfigSet`が返す内容に対応する。
    attributes:
      - name: tables
        type: "list of TableEntry"
        required: true
        description: テーブルの設定
      - name: translations
        type: "list of TranslationItem"
        required: true
        description: 翻訳(i18nキー・言語・テキスト)
    entity_constraints:
      - "tablesの中で、(schemaName, tableName)の組は、一意でなければならない"
      - "translationsの中で、(i18nKey, locale)の組は、一意でなければならない"
    relationships:
      - target: TableEntry
        cardinality: "0..*"
        direction: "SchemaSection 1 -> * TableEntry"
        description: 0個以上のテーブルの設定を持つ
      - target: TranslationItem
        cardinality: "0..*"
        direction: "SchemaSection 1 -> * TranslationItem"
        description: 0個以上の翻訳を持つ

  - name: TableEntry
    description: >
      1つのテーブルの設定(config-engineのTableConfigに対応する、自然キー表現)。テーブルの識別は、
      (schemaName, tableName)による(内部のtableConfigIdは含めない)。
    attributes:
      - name: schemaName
        type: string
        required: true
        description: 対象RDBMSのスキーマ名。自然キーの一部
      - name: tableName
        type: string
        required: true
        description: 対象RDBMSのテーブル名。自然キーの一部
      - name: displayOrder
        type: integer
        required: true
        description: テーブルの表示順
      - name: optimisticLockColumn
        type: string
        required: false
        description: 楽観ロック対象列の名前。なければnull。columnsに含まれる列でなければならない(BR9.13)
      - name: columns
        type: "list of ColumnEntry"
        required: true
        description: このテーブルのカラムの設定
    entity_constraints:
      - "columnsの中で、columnNameは、一意でなければならない"
      - "schemaName・tableNameの値は、config-engineの検証(BR1.1〜BR1.4)に従う。本ユニットは、値の意味(業務固有の名前)を解釈しない"
    relationships:
      - target: ColumnEntry
        cardinality: "0..*"
        direction: "TableEntry 1 -> * ColumnEntry"
        description: 0個以上のカラムの設定を持つ

  - name: ColumnEntry
    description: >
      1つのカラムの設定(config-engineのColumnConfigに対応する、自然キー表現)。カラムの識別は、
      (schemaName, tableName, columnName)による(所属するTableEntryの自然キー+columnName)。
      内部のcolumnConfigId・tableConfigIdは含めない。
    attributes:
      - name: columnName
        type: string
        required: true
        description: カラム名。自然キーの一部
      - name: displayOrder
        type: integer
        required: true
        description: カラムの表示順
      - name: format
        type: string
        required: true
        description: 書式(config-engineの定義に従う。本ユニットは中身を解釈しない)
      - name: editorType
        type: string
        required: true
        allowed_values: [text, textarea, integer, decimal, date, datetime, select, radio, switch, checkbox]
        description: 編集部品(FR1.1)。許容値の検証は、config-engineが行う
      - name: validationRule
        type: object
        required: false
        description: バリデーション定義(required・minLength・maxLength・min・max・patternなど)。config-engineが検証する
      - name: visibility
        type: string
        required: true
        allowed_values: [visible, hidden]
        description: 表示可否
      - name: isPrimaryKey
        type: boolean
        required: false
        description: >
          主キー列かどうか。書き出しでは、現在の値を出力する(参考情報)。取り込みでは、ファイルの値を**無視する**
          (config-engineのBR1.14: 主キー列情報は、スキーマ探索(schema-introspector)でのみ設定される)。
      - name: choiceOptions
        type: "list of ChoiceOption"
        required: false
        description: 静的な選択肢(value・i18nKey)。fkReferenceと同時には持てない(config-engineのBR1.4)
      - name: fkReference
        type: FkReference
        required: false
        description: FK参照(参照先のスキーマ・テーブル・値の列・名称の列)。choiceOptionsと同時には持てない
    entity_constraints:
      - "fkReferenceの参照先(referencedSchemaName・referencedTableName)と、その値の列・名称の列は、ファイルのschemaセクションに存在しなければならない(BR9.13)"
      - "取り込み後のisPrimaryKeyは、既存のカラムではその値を維持し、新規のカラムではfalseになる(BR9.9)"
    relationships:
      - target: FkReference
        cardinality: "0..1"
        direction: "ColumnEntry 1 -> 0..1 FkReference"
        description: FK参照を持つ場合に限り、1個の参照先を持つ

  - name: FkReference
    description: FK参照の参照先を、自然キーで表した値オブジェクト(config-engineのFkReferenceに対応)
    attributes:
      - name: referencedSchemaName
        type: string
        required: true
        description: 参照先のスキーマ名
      - name: referencedTableName
        type: string
        required: true
        description: 参照先のテーブル名
      - name: referencedValueColumnName
        type: string
        required: true
        description: 参照先の、値にあたる列の名前
      - name: referencedLabelColumnName
        type: string
        required: true
        description: 参照先の、表示する名称にあたる列の名前
    entity_constraints: []
    relationships: []

  - name: ChoiceOption
    description: 静的な選択肢の1件(config-engineのChoiceOptionに対応)
    attributes:
      - name: value
        type: string
        required: true
        description: 選択肢の値
      - name: i18nKey
        type: string
        required: true
        description: 選択肢の表示名の、翻訳キー
    entity_constraints: []
    relationships: []

  - name: TranslationItem
    description: >
      1件の翻訳(config-engineのTranslationEntryに対応)。i18nキーは、表示名・バリデーションメッセージ・選択肢の
      表示名の、機械的に導出されたキーである。
    attributes:
      - name: i18nKey
        type: string
        required: true
        description: 翻訳キー
      - name: locale
        type: string
        required: true
        allowed_values: [ja, en]
        description: 言語(MVPは日本語・英語)
      - name: text
        type: string
        required: true
        description: 翻訳されたテキスト
    entity_constraints:
      - "(i18nKey, locale)の組は、一意"
    relationships: []

  - name: MenuSection
    description: >
      menu-navigationが保持する業務メニュー(MenuItem)を、**入れ子の木構造**で表したセクション(値オブジェクト)。
      C12の`getExportableMenuStructure`が返す、フラットな一覧(menuItemId・parentMenuItemId・label・order・
      targetTableConfigId)を、親子関係で入れ子にして表す。管理メニュー(ユーザー管理・監査ログ・設定管理・
      スキーマ探索の4項目)は、DBに永続化されないため、対象外(menu-navigationのBR6.2)。
    attributes:
      - name: items
        type: "list of MenuEntry"
        required: true
        description: ルート直下のメニュー項目(表示順の順に並べる)
    entity_constraints:
      - "メニュー項目の内部ID(menuItemId・parentMenuItemId)は、ファイルに含めない。親子関係は、入れ子で表す(BR9.3)"
    relationships:
      - target: MenuEntry
        cardinality: "0..*"
        direction: "MenuSection 1 -> * MenuEntry"
        description: 0個以上のルート直下のメニュー項目を持つ

  - name: MenuEntry
    description: >
      1つの業務メニュー項目。遷移先のテーブルを持つ項目(リーフ)と、子を持つフォルダ項目がある。
    attributes:
      - name: label
        type: string
        required: true
        description: 表示名
      - name: order
        type: integer
        required: true
        description: 同一階層内での表示順
      - name: targetTable
        type: TableRef
        required: false
        description: 遷移先のテーブル(自然キー)。フォルダ項目ではnull。ファイルのschemaセクションのテーブルでなければならない(BR9.13)
      - name: children
        type: "list of MenuEntry"
        required: false
        description: 子のメニュー項目。子を持つのは、targetTableを持たない項目(フォルダ)だけ
    entity_constraints:
      - "targetTableを持つ項目は、childrenを持たない(menu-navigationの階層の規則に従う)"
      - "同一の親の下で、orderは、重複しない(重複の検証は、menu-navigationの規則に従う)"
    relationships:
      - target: TableRef
        cardinality: "0..1"
        direction: "MenuEntry 1 -> 0..1 TableRef"
        description: リーフ項目に限り、遷移先のテーブルを参照する

  - name: TableRef
    description: テーブルを、自然キーで指す値オブジェクト(メニューの遷移先・権限の対象で用いる)
    attributes:
      - name: schemaName
        type: string
        required: true
        description: スキーマ名
      - name: tableName
        type: string
        required: true
        description: テーブル名
    entity_constraints: []
    relationships: []

  - name: RbacSection
    description: >
      permission-engineが保持するRBAC設定を、自然キーで表したセクション(値オブジェクト、Q1=A)。ロール・グループ・
      グループとロールの対応・主権限・補助権限を含む。**ユーザー(user-managementが所有)と、ユーザーのグループ所属は
      含めない**(ユーザーは環境ごとに異なるため)。permission-engineが提供する書き出し・投入の内部契約
      (C10の追補、本ユニットの機能設計で要求する。functional-spec.mdの追補一覧)を通して、値を得る・渡す。
    attributes:
      - name: roles
        type: "list of RoleEntry"
        required: true
        description: ロール
      - name: groups
        type: "list of GroupEntry"
        required: true
        description: グループと、それに対応するロール
      - name: primaryPermissions
        type: "list of PrimaryPermissionEntry"
        required: true
        description: 主権限(FULL・READ・NONE)
      - name: auxiliaryPermissions
        type: "list of AuxiliaryPermissionEntry"
        required: true
        description: 補助権限(作成・削除)
    entity_constraints:
      - "roles・groupsの中で、それぞれ名前は、一意でなければならない"
      - "primaryPermissionsは、取り込み後に、1件以上でなければならない(BR9.12。0件のファイルは、拒否する)"
      - "primaryPermissionsの中で、(roleName, scope)の組は、一意でなければならない。auxiliaryPermissionsも同様"
    relationships:
      - target: RoleEntry
        cardinality: "0..*"
        direction: "RbacSection 1 -> * RoleEntry"
        description: 0個以上のロールを持つ
      - target: GroupEntry
        cardinality: "0..*"
        direction: "RbacSection 1 -> * GroupEntry"
        description: 0個以上のグループを持つ
      - target: PrimaryPermissionEntry
        cardinality: "0..*"
        direction: "RbacSection 1 -> * PrimaryPermissionEntry"
        description: 主権限の割当を持つ
      - target: AuxiliaryPermissionEntry
        cardinality: "0..*"
        direction: "RbacSection 1 -> * AuxiliaryPermissionEntry"
        description: 補助権限の割当を持つ

  - name: RoleEntry
    description: ロール1件(permission-engineのRoleに対応)。名前が自然キーで、内部のroleIdは含めない
    attributes:
      - name: name
        type: string
        required: true
        unique: true
        description: ロール名。自然キー
    entity_constraints: []
    relationships: []

  - name: GroupEntry
    description: グループ1件と、そのグループに対応するロール(permission-engineのGroup・GroupRoleに対応)
    attributes:
      - name: name
        type: string
        required: true
        unique: true
        description: グループ名。自然キー
      - name: roleNames
        type: "list of string"
        required: true
        description: このグループに対応するロールの名前。すべて、ファイルのroleに存在しなければならない(BR9.13)
    entity_constraints:
      - "roleNamesの中で、同じ名前を重複して持たない"
    relationships: []

  - name: PermissionScope
    description: >
      権限の対象(スコープ)を、自然キーで表した値オブジェクト。permission-engineのscopeType・scopeRefに対応する。
      scopeRefは、permission-engineの内部では、スキーマ名・tableConfigId・columnConfigIdの不透明な文字列だが、
      ファイルでは、自然キーで表す。
    attributes:
      - name: scopeType
        type: string
        required: true
        allowed_values: [SCHEMA, TABLE, COLUMN]
        description: 対象の階層
      - name: schemaName
        type: string
        required: true
        description: >
          SCHEMA: スキーマ名、またはpermission-engineが管理する予約スキーマ名(管理系画面用の`__system__:`で始まる名前、
          permission-engineのBR3.14・BR3.15)。TABLE・COLUMN: テーブルのスキーマ名
      - name: tableName
        type: string
        required: false
        description: TABLE・COLUMNで必須。SCHEMAではnull
      - name: columnName
        type: string
        required: false
        description: COLUMNで必須。SCHEMA・TABLEではnull
    entity_constraints:
      - "TABLE・COLUMNの対象は、ファイルのschemaセクションのテーブル・カラムでなければならない(BR9.13)"
      - "SCHEMAの対象は、ファイルのschemaセクションのスキーマ名、または予約スキーマ名でなければならない。予約スキーマ名は、本ユニットが解釈せず、permission-engineの検証に委ねる(BR9.20)"
    relationships: []

  - name: PrimaryPermissionEntry
    description: ロールに対する主権限の割当1件(permission-engineのPrimaryPermissionに対応)
    attributes:
      - name: roleName
        type: string
        required: true
        description: 対象のロール名。ファイルのroleに存在しなければならない
      - name: scope
        type: PermissionScope
        required: true
        description: 権限の対象
      - name: level
        type: string
        required: true
        allowed_values: [FULL, READ, NONE]
        description: 主権限のレベル(「指定なし」は、エントリを持たないことで表す)
    entity_constraints:
      - "(roleName, scope)の組は、一意"
    relationships: []

  - name: AuxiliaryPermissionEntry
    description: ロールに対する補助権限の割当1件(permission-engineのAuxiliaryPermissionに対応)
    attributes:
      - name: roleName
        type: string
        required: true
        description: 対象のロール名。ファイルのroleに存在しなければならない
      - name: scope
        type: PermissionScope
        required: true
        description: 権限の対象。scopeTypeは、SCHEMA・TABLEだけ(COLUMNは、補助権限の対象外)
      - name: createAllowed
        type: boolean
        required: false
        description: 作成の可否。null(指定なし)は、上位の設定を継承する
      - name: deleteAllowed
        type: boolean
        required: false
        description: 削除の可否。null(指定なし)は、上位の設定を継承する
    entity_constraints:
      - "scopeTypeがCOLUMNのエントリは、構造のエラーとする"
    relationships: []

  - name: ImportContext
    description: >
      1回の取り込みの実行の文脈(値オブジェクト。永続化しない)。取り込みの開始時に、1回だけ決める。
      権限昇格の判定は、この文脈が保持する「取り込み開始時点の設定」を基準に行う(Q13=A、BR9.11)。
    attributes:
      - name: operatorUserId
        type: string
        required: true
        description: 操作者のuserId(共有契約C15のOperator)
      - name: operatorActiveRoleId
        type: string
        required: false
        description: 操作者のアクティブロール(未選択ならnull)。permission-engineのactorRoleIdに渡す
      - name: startedAt
        type: string
        required: true
        description: 取り込みを始めた日時
      - name: bootstrapAtStart
        type: boolean
        required: true
        description: 取り込みの開始時点で、permission-engineが初期状態(主権限が0件)だったか。昇格チェックの除外の判断は、この値で固定する
    entity_constraints:
      - "operatorUserIdは、必須。操作者が解決できない要求は、取り込みを始める前に、401で拒否する(BR9.15)"
    relationships: []

  - name: ImportError
    description: >
      検証で見つかった誤り1件(値オブジェクト)。RFC 9457のProblemDetailsの`errors[]`の要素になる。
      入力値そのもの(ファイルの内容)は、含めない。
    attributes:
      - name: field
        type: string
        required: true
        description: JSON上の位置(例 `schema.tables[3].columns[2].editorType`)
      - name: message
        type: string
        required: true
        description: 翻訳用のメッセージキー(i18nキー)
      - name: params
        type: object
        required: false
        description: メッセージに埋め込む値(必要なものだけ。例 上限の値)
    entity_constraints:
      - "1回の応答に含めるImportErrorは、最大100件(BR9.7)。超えた場合は、打ち切ったことを示す"
    relationships: []

  - name: ImportResult
    description: 取り込みが成功したときの結果(値オブジェクト)。200の応答本体と、監査イベントの件数に用いる
    attributes:
      - name: sections
        type: "map of section name to SectionCounts"
        required: true
        description: セクション(schema・translations・menu・roles・groups・primaryPermissions・auxiliaryPermissions)ごとの件数
      - name: outcome
        type: string
        required: true
        allowed_values: [SUCCESS]
        description: 応答は成功のときだけ返る(失敗は、422などの誤りの応答になる)
    entity_constraints: []
    relationships: []

  - name: SectionCounts
    description: 1つのセクションの、追加・更新・削除の件数
    attributes:
      - name: added
        type: integer
        required: true
        min: 0
        description: 追加した件数
      - name: updated
        type: integer
        required: true
        min: 0
        description: 更新した件数(値が変わったもの。値が同じ項目は、数えない)
      - name: deleted
        type: integer
        required: true
        min: 0
        description: 削除した件数(全置換のため、ファイルにない項目)
    entity_constraints: []
    relationships: []

  - name: ConfigImportExecutedEvent
    description: >
      取り込みの実行を、audit-loggingへ知らせるドメインイベント(値オブジェクト、Q9=A)。取り込み1回につき1件で、
      成功・失敗の両方で発行する。エクスポートでは、発行しない。監査ログは追記専用で、無期限に保持される
      (FR8.2・FR8.3)ため、内容は、操作者・日時・結果・件数・失敗の理由の分類に限る(ファイルの内容そのものは、含めない)。
    attributes:
      - name: actorUserId
        type: string
        required: true
        description: 操作者のuserId
      - name: actorRoleId
        type: string
        required: false
        description: 操作者のアクティブロール(未選択ならnull)
      - name: outcome
        type: string
        required: true
        allowed_values: [SUCCESS, FAILURE]
        description: 取り込みの結果
      - name: failureCategory
        type: string
        required: false
        allowed_values: [MALFORMED, UNSUPPORTED_FORMAT, VALIDATION_ERROR, ESCALATION_DENIED, RBAC_EMPTY, UNEXPECTED]
        description: FAILUREのときの、失敗の理由の分類(BR9.16)。SUCCESSではnull
      - name: sections
        type: "map of section name to SectionCounts"
        required: false
        description: SUCCESSのときの、セクションごとの件数。FAILUREではnull(何も反映していない)
      - name: errorCount
        type: integer
        required: false
        min: 0
        description: FAILUREのときの、検出した誤りの件数(打ち切り前の総数)
      - name: occurredAt
        type: string
        required: true
        description: 発生日時
    entity_constraints:
      - "ファイルの内容(設定の値・名前)は、含めない(監査ログは、無期限に保持されるため)"
    relationships: []
```

## エンティティ概要

- **ConfigDocument**: 設定一式のJSONファイル全体。`formatVersion`(MVPは1)・書き出し日時・出力元のバージョンと、3つのセクション(schema・menu・rbac)を持つ。すべての参照は自然キーで、内部IDを含まない。
- **SchemaSection**(TableEntry・ColumnEntry・FkReference・ChoiceOption・TranslationItem): config-engineのスキーマ定義と翻訳を、(schemaName, tableName, columnName)を自然キーとして表す。
- **MenuSection**(MenuEntry・TableRef): 業務メニューを、入れ子の木構造で表す。遷移先のテーブルは、自然キーで指す。
- **RbacSection**(RoleEntry・GroupEntry・PermissionScope・PrimaryPermissionEntry・AuxiliaryPermissionEntry): ロール・グループ・グループとロールの対応・主権限・補助権限を、名前と自然キーで表す。ユーザーとユーザーのグループ所属は、含めない。
- **ImportContext・ImportError・ImportResult・SectionCounts**: 1回の取り込みの、文脈・誤り・結果。永続化しない。
- **ConfigImportExecutedEvent**: 取り込み1回につき1件の、監査用のイベント。成功・失敗の両方で発行し、内容は、操作者・日時・結果・件数・失敗の分類に限る。
