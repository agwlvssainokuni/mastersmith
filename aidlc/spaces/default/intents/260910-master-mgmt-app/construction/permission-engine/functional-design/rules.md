# Business Rules — permission-engine (U3)

```yaml
rules:
  - id: BR3.1
    statement: ロールはユーザーへ直接付与できる(User⇔Roleは多対多)。ロール自体は階層(親子関係)を持たない。
    category: constraint
    applies_to: Role
    trigger: ロール割当の参照・更新時
    logic: >
      IF ロールがUserへ直接付与されている THEN そのUserは当該ロールを選択可能ロール一覧に含める。
    violation_behaviour: N/A(構造上の制約であり違反状態は発生しない)
    source: FR4.1

  - id: BR3.2
    statement: ロールはGroupへ付与でき、Groupに所属するUserは、当該Groupに付与された全ロールを間接的に得る。
    category: constraint
    applies_to: Group, GroupRole, GroupMembership
    trigger: 選択可能ロール一覧の算出時
    logic: >
      IF UserがGroupに所属し(GroupMembership) AND 当該GroupにRoleが付与されている(GroupRole)
      THEN そのRoleをUserの選択可能ロール一覧に含める。
    violation_behaviour: N/A
    source: FR4.1

  - id: BR3.3
    statement: Userの選択可能ロール一覧は、直接付与されたロール集合とGroup経由で得られるロール集合の和集合(重複排除)である。
    category: calculation
    applies_to: Role
    trigger: ロール選択UI(authentication-service)がロール候補を取得する時
    logic: >
      選択可能ロール一覧 = (Userへ直接付与されたRole) UNION (Userが所属する各GroupにGroupRoleで付与されたRole)。
    violation_behaviour: N/A
    source: FR4.1, FR4.2

  - id: BR3.4
    statement: >
      主権限(level)の実効値は、対象ロールのスコープ階層(カラム→テーブル→スキーマの順)を、より詳細な階層から
      たどって最初に見つかった明示設定(PrimaryPermissionの行が存在する階層)を採用して決定する。
      levelは`FULL > READ > NONE`の順序を持つ(FULLが最も強い権限)。この順序はBR3.8の権限昇格判定の
      比較基準として用いる。
    category: calculation
    applies_to: PrimaryPermission
    trigger: resolveEffectivePermission呼び出し時
    logic: >
      IF 対象カラムに対する明示のPrimaryPermission行が存在する THEN その値をlevelとして採用する。
      ELSE IF 対象テーブルに対する明示のPrimaryPermission行が存在する THEN その値を採用する。
      ELSE IF 対象スキーマに対する明示のPrimaryPermission行が存在する THEN その値を採用する。
      ELSE levelはNONEとする(BR3.6)。
      判定はロールを跨いで遡らない(ロール階層は存在しないため、activeRoleId単一ロールの設定のみを参照する)。
    violation_behaviour: N/A(解決アルゴリズムであり違反状態は発生しない)
    source: FR4.3

  - id: BR3.5
    statement: >
      補助権限(createAllowed/deleteAllowed)の実効値は、対象ロールのスコープ階層(テーブル→スキーマの順)を
      たどって最初に見つかった明示設定(nullでないフィールド)を採用して決定する。
    category: calculation
    applies_to: AuxiliaryPermission
    trigger: resolveEffectivePermission呼び出し時
    logic: >
      IF 対象テーブルに対するAuxiliaryPermission行のcreateAllowed(またはdeleteAllowed)がnullでない
        THEN その値を採用する。
      ELSE IF 対象スキーマに対する同フィールドがnullでない THEN その値を採用する。
      ELSE デフォルトはfalse(禁止、BR3.6)とする。createAllowed/deleteAllowedは独立に解決する。
    violation_behaviour: N/A
    source: FR4.4

  - id: BR3.6
    statement: 主権限・補助権限のいずれについても、明示設定がどの階層にも一切見つからない場合、安全側のデフォルト(主権限はNONE、補助権限は禁止)を適用する。
    category: policy
    applies_to: PrimaryPermission, AuxiliaryPermission
    trigger: BR3.4・BR3.5の解決チェーンが最上位スコープまで到達しても明示設定が見つからない場合
    logic: IF 明示設定が全階層で見つからない THEN level=NONE、createAllowed=false、deleteAllowed=falseとする。
    violation_behaviour: N/A
    source: FR4.3, FR4.4

  - id: BR3.7
    statement: 実効権限の判定は、画面表示の出し分け(クライアント側UI非表示)だけに依存せず、必ずサーバー側(resolveEffectivePermission)で再検証しなければならない。
    category: authorization
    applies_to: resolveEffectivePermission
    trigger: 全コンシューマー(user-management, menu-navigation, audit-logging, list-engine, record-edit-engine, config-import-export)からの権限判定要求時
    logic: IF コンシューマーが権限判定を要する操作を実行しようとする THEN 必ずresolveEffectivePermission(またはcanAccessScreen)を呼び出し、その結果に従う。
    violation_behaviour: 本ルールに反する実装(クライアント側判定のみに依存する実装)は許容しない。
    source: FR3.3

  - id: BR3.8
    statement: 権限の昇格(assignPermissionによる権限割当の書き込み)は、操作者自身の実効権限を超えるレベルの付与を許可してはならない(自分自身への昇格を含む)。
    category: authorization
    applies_to: assignPermission
    trigger: assignPermission呼び出し時
    logic: >
      IF 割り当てようとする対象(roleId, scopeType, scopeRef)の新しいlevel(または補助権限の新しい許可/禁止)が、
      操作者(実行中のリクエストのactiveRoleId)が同一(scopeType, scopeRef)について現に持つ実効権限を上回る
      THEN PermissionEscalationExceptionをスローし、割当を拒否する。
    violation_behaviour: PermissionEscalationExceptionをスローし、当該割当エントリの反映を中止する(呼び出し元がconfig-import-exportの場合、当該エントリのみをインポートエラーとして扱うか全体を中止するかはconfig-import-exportユニットの機能設計に委ねる)。
    source: FR3.4

  - id: BR3.9
    statement: assignPermissionの呼び出し経路は、config-import-export(C7 `/api/config/import`)の設定一式JSONインポート処理のみとする。専用のRBAC管理画面・個別割当APIは本MVPスコープでは提供しない。
    category: constraint
    applies_to: assignPermission
    trigger: N/A(設計制約)
    logic: N/A
    violation_behaviour: N/A
    source: unit-of-work.md U9定義

  - id: BR3.10
    statement: >
      canAccessScreenは、管理系画面(user-management, audit-log, config-import-export)・業務メニュー項目
      (config-engineのtableConfigIdに対応する一覧/編集画面)の両方を対象とする汎用的な画面キー体系とする。
    category: authorization
    applies_to: canAccessScreen
    trigger: menu-navigationおよび管理系画面からのアクセス可否問い合わせ時
    logic: >
      IF screenKeyが予約キー("user-management" | "audit-log" | "config-import-export")の場合、
        THEN 内部予約スキーマ(権限管理用の固定スコープ)に対するresolveEffectivePermissionの結果(level != NONE)を返す。
      ELSE screenKeyはtableConfigId(業務メニュー項目)とみなし、
        THEN scopeType=TABLE, scopeRef=tableConfigIdとしてresolveEffectivePermissionを呼び出し、level != NONEを返す。
    violation_behaviour: N/A
    source: FR7.1, FR7.2(menu-navigationからの権限問い合わせ委譲)

  - id: BR3.11
    statement: 権限変更(assignPermissionによる主権限・補助権限の割当変更)は、config-import-exportの1回のインポート実行につき1件のサマリイベント(PermissionChanged、実行者・変更件数・日時)としてaudit-loggingへ発行する。個々の変更前後の値は本イベントには含めない。
    category: policy
    applies_to: assignPermission
    trigger: config-import-exportの1回のインポート実行が完了した時点
    logic: IF インポート実行中に1件以上のassignPermission呼び出しが成功した THEN 実行単位のサマリイベントを1件発行する。
    violation_behaviour: N/A
    source: components.md(PermissionEngine→AuditLoggingのイベント発行定義)

  - id: BR3.12
    statement: 本ユニットは特定業務のテーブル名・カラム名をハードコードしない。権限判定はconfig-engineから取得したスキーマ・テーブル・カラムの識別子(scopeRef)に対する汎用的なロジックとして実装する。
    category: constraint
    applies_to: PrimaryPermission, AuxiliaryPermission
    trigger: N/A(設計制約)
    logic: N/A
    violation_behaviour: N/A
    source: FR1.6

  - id: BR3.13
    statement: >
      RBAC設定(PrimaryPermission行)がシステム全体で1件も存在しないブートストラップ状態に限り、
      (a) canAccessScreen(activeRoleId, "config-import-export")は無条件にtrueを返し、
      (b) assignPermissionはBR3.8の権限昇格チェックを適用しない(どの割当も無条件に許可する)。
      PrimaryPermission行が1件でも存在するようになった時点でブートストラップ状態は永続的に終了し、
      以降のすべての呼び出しに通常のBR3.6・BR3.8・BR3.10のルールを適用する。
    category: policy
    applies_to: canAccessScreen, assignPermission
    trigger: システム起動直後、初回のRBAC設定インポート(config-import-export)が行われる前
    logic: >
      IF PrimaryPermissionの行数が0 THEN canAccessScreenは"config-import-export"に対し常にtrueを返し、
      assignPermissionは昇格チェックをスキップする。
      ELSE 通常のBR3.6・BR3.8・BR3.10を適用する。
    violation_behaviour: N/A
    source: FR3.4, FR2.4(初期管理者アカウントの自動作成)との整合を確保するための設計補完(レビュー指摘R-01対応)

  - id: BR3.14
    statement: >
      config-import-export(C7)が設定一式JSONのRBACセクションを本ユニットへ反映する際、各エントリ
      (roleId, scopeType, scopeRef, level)または(roleId, scopeType, scopeRef, createAllowed,
      deleteAllowed)は、config-engineが認識する実業務スキーマ・テーブル・カラムの一覧に対する
      整合性検証を経ない。scopeRefは不透明な文字列として受け入れ、BR3.15が定める予約スキーマ名
      (管理系画面用)も、config-engine上の実在チェックなしにそのまま許容する。
    category: constraint
    applies_to: PrimaryPermission, AuxiliaryPermission
    trigger: config-import-exportからのRBAC設定インポート時
    logic: N/A
    violation_behaviour: N/A
    source: レビュー指摘R-02対応

  - id: BR3.15
    statement: >
      管理系画面(canAccessScreenの予約screenKey)は、以下の予約スキーマ名をPrimaryPermissionの
      scopeType=SCHEMAのscopeRefとして用いる: `__system__:user-management`、`__system__:audit-log`、
      `__system__:config-import-export`。`__system__:`プレフィックスはconfig-engineが管理する
      実業務スキーマ名との衝突を避けるための予約名前空間であり、config-engine側での定義や検証は
      発生しない(BR3.14参照)。
    category: constraint
    applies_to: PrimaryPermission
    trigger: N/A(識別子の命名規約)
    logic: N/A
    violation_behaviour: N/A
    source: レビュー指摘R-02対応
```

## rules summary

| ID | 概要 | カテゴリ |
|---|---|---|
| BR3.1 | ロールのUserへの直接付与(階層なし) | constraint |
| BR3.2 | Group経由のロール間接付与 | constraint |
| BR3.3 | 選択可能ロール一覧=直接∪Group経由 | calculation |
| BR3.4 | 主権限のスコープ階層解決順序(カラム→テーブル→スキーマ) | calculation |
| BR3.5 | 補助権限のスコープ階層解決順序(テーブル→スキーマ) | calculation |
| BR3.6 | 全階層指定なし時のデフォルト(NONE/禁止) | policy |
| BR3.7 | サーバー側での実効権限再検証の必須化 | authorization |
| BR3.8 | 権限昇格の防止 | authorization |
| BR3.9 | assignPermissionの唯一の呼び出し経路(config-import-export) | constraint |
| BR3.10 | canAccessScreenの汎用画面キー体系 | authorization |
| BR3.11 | PermissionChangedイベント(インポート単位のサマリ) | policy |
| BR3.12 | 業務固有情報のハードコード禁止 | constraint |
| BR3.13 | ブートストラップ例外(初回RBAC投入時の昇格チェック除外) | policy |
| BR3.14 | 予約スコープのconfig-engine非検証(不透明識別子) | constraint |
| BR3.15 | 管理系画面の予約スキーマ名 | constraint |
