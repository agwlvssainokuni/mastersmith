# Business Rules: permission

## ルール一覧(機械可読)

```yaml
rules:
  - id: BR1.1
    statement: RoleおよびGroupのnameは、それぞれの種別内で一意でなければならない
    category: validation
    applies_to: Role, Group
    trigger: "管理者がRoleまたはGroupを作成・名称変更しようとしたとき"
    logic: "IF 指定されたnameが同種別内で既に使用されている THEN 400エラー(RFC 7807)を返す。ELSE 作成・変更を受け付ける"
    violation_behaviour: "作成・変更は拒否され、既存のレコードは変更されない"
    source: FR5.1, FR5.2, FR5.3

  - id: BR1.2
    statement: 既にGroupに所属しているAccountを同じGroupへ追加しようとした場合、既存の所属を維持し何もしない(冪等)
    category: policy
    applies_to: GroupMembership
    trigger: "管理者が`POST /api/admin/groups/{groupId}/members`で、既に所属済みのAccountを追加しようとしたとき"
    logic: "IF 指定された(accountId, groupId)の組が既に存在する THEN 何もせず現在の状態を200/201いずれかで返す(エラーにしない)。ELSE 新規にGroupMembershipを作成する"
    violation_behaviour: "該当なし(エラーとして扱わない)"
    source: functional-design-questions.md Q1のレビューで発見された未決定事項(R-03フォロー)

  - id: BR1.3
    statement: PUT /api/admin/roles/{roleId}(ロール名称変更)・PUT /api/admin/groups/{groupId}(グループ名称変更)は、対象のname属性のみを更新する。一意性検証はBR1.1に従う
    category: business
    applies_to: Role, Group
    trigger: "管理者がPUT /api/admin/roles/{roleId}またはPUT /api/admin/groups/{groupId}を呼び出したとき(frontend-admin Unit Functional Designレビューより新設)"
    logic: "IF 対象roleId/groupIdが存在しない THEN 404を返す。ELSE BR1.1の一意性検証を適用し、通過すればname属性のみを更新する(roleId/groupId自体、およびその他の属性は変更しない)"
    violation_behaviour: "404エラー(対象なし)、または400エラー(BR1.1違反、RFC 7807)"
    source: FR5.1, FR5.2, FR5.3(frontend-admin Unit Functional Designレビューより新設)

  - id: BR1.4
    statement: DELETE /api/admin/roles/{roleId}は、対象roleIdを参照するRoleAssignmentが1件以上存在する場合は削除を拒否する。参照が存在しない場合、対象RoleのTablePermission・ColumnPermission(そのRole自身に従属するデータ)を道連れに削除したうえでRole自体を削除する
    category: business
    applies_to: Role, RoleAssignment, TablePermission, ColumnPermission
    trigger: "管理者がDELETE /api/admin/roles/{roleId}を呼び出したとき(frontend-admin Unit Functional Designレビューより新設)"
    logic: "IF 対象roleIdが存在しない THEN 404を返す。IF RoleAssignment(roleId=対象)が1件以上存在する THEN 409を返す(config-managementのDbConnection削除と同様、参照が残っている限り削除不可とする方針)。ELSE 対象RoleのTablePermission・ColumnPermissionをすべて削除したうえでRole自体を削除する"
    violation_behaviour: "404エラー(対象なし)、または409エラー(RFC 7807、割り当てが残っている旨を示す)"
    source: FR5.1, FR5.2, FR5.3(frontend-admin Unit Functional Designレビューより新設)

  - id: BR1.5
    statement: DELETE /api/admin/groups/{groupId}は、対象groupIdを参照するRoleAssignment(assigneeType=group)が1件以上存在する場合は削除を拒否する。参照が存在しない場合、対象GroupのGroupMembership(そのGroup自身に従属するデータ)を道連れに削除したうえでGroup自体を削除する
    category: business
    applies_to: Group, RoleAssignment, GroupMembership
    trigger: "管理者がDELETE /api/admin/groups/{groupId}を呼び出したとき(frontend-admin Unit Functional Designレビューより新設)"
    logic: "IF 対象groupIdが存在しない THEN 404を返す。IF RoleAssignment(assigneeType=group, groupId=対象)が1件以上存在する THEN 409を返す。ELSE 対象GroupのGroupMembershipをすべて削除したうえでGroup自体を削除する"
    violation_behaviour: "404エラー(対象なし)、または409エラー(RFC 7807、ロールが割り当てられている旨を示す)"
    source: FR5.1, FR5.2, FR5.3(frontend-admin Unit Functional Designレビューより新設)

  - id: BR2.1
    statement: TablePermissionは、一覧(canList)・詳細(canView)・作成(canCreate)・編集(canEdit)・削除(canDelete)の5つの操作権限を、ロール・テーブルの組み合わせごとに個別に設定できる
    category: authorization
    applies_to: TablePermission
    trigger: "管理者がロールのテーブル単位権限を設定したとき"
    logic: "各操作(list/view/create/edit/delete)ごとに真偽値を独立して保存する"
    violation_behaviour: "該当なし"
    source: FR5.1

  - id: BR2.2
    statement: ColumnPermissionのaccessLevelは、更新可(editable)・表示のみ(readonly)・非表示(hidden)のいずれかである
    category: authorization
    applies_to: ColumnPermission
    trigger: "管理者がロールのカラム単位権限を設定したとき"
    logic: "IF accessLevelがeditable/readonly/hiddenのいずれか THEN 設定を受け付ける。ELSE 400エラー(RFC 7807)を返す"
    violation_behaviour: "設定は拒否される"
    source: FR5.2

  - id: BR3.1
    statement: RoleAssignmentのaccountId・groupIdは排他であり、assigneeType=userのときはaccountIdのみ、assigneeType=groupのときはgroupIdのみが値を持つ。この排他性は書き込み時に検証する。読み取り時はassigneeTypeを正とし、値が入っているフィールドとの不一致が見つかった場合はデータ不整合としてログに記録する(書き込み時検証がある限り通常は発生しない)
    category: validation
    applies_to: RoleAssignment
    trigger: "管理者がロールをAccountまたはGroupへ割り当てようとしたとき"
    logic: "IF assigneeType=user AND accountIdが指定されている AND groupIdが未指定 THEN 割り当てを受け付ける。IF assigneeType=group AND groupIdが指定されている AND accountIdが未指定 THEN 割り当てを受け付ける。ELSE 400エラー(RFC 7807)を返す"
    violation_behaviour: "割り当ては拒否される"
    source: FR5.3

  - id: BR3.2
    statement: 契約#21(account-management → permission)を受けたとき、渡されたroleId配列がすべて存在するロールであることを検証したうえで、対象accountIdの直接RoleAssignment(assigneeType=user)を渡された配列で全置換する(既存の直接割当をすべて削除してから、配列内の重複roleIdを去重(dedup)した上で、各roleIdについて新規に作成する)。グループ経由の割当(GroupMembership)には一切触れない
    category: business
    applies_to: RoleAssignment
    trigger: "契約#21の呼び出しを受けたとき(account-managementによるアカウント新規作成時の初期ロール割り当て、または編集時の割り当てロール変更)"
    logic: "IF roleId配列に存在しないroleIdが含まれる THEN 例外を送出する(account-management側で400として応答、契約#21 Failure behavior)。ELSE 配列内の重複roleIdを去重してから、対象accountIdのassigneeType=user・RoleAssignmentを全件削除し、去重後の各roleIdについて新規RoleAssignment(assigneeType=user, accountId=対象, roleId=該当)を作成する(去重により、RoleAssignmentの(roleId, accountId)一意制約違反を防ぐ。iteration 2レビューR-01フォロー)。処理成功後、更新後の直接RoleAssignment一覧(去重済みroleId配列)を返す"
    violation_behaviour: "該当なし(例外は呼び出し元であるaccount-managementがREST境界で400として応答する)"
    source: contract-summary.md #21(R-01フォロー、permission Unit Functional Designレビュー iteration 1より。配列内重複のdedupはiteration 2レビューR-01フォロー)

  - id: BR3.3
    statement: 管理画面からの個別ロール割り当て(`POST /api/admin/roles/{roleId}/assignments`)で、既に存在する(roleId, accountId)または(roleId, groupId)の組を再度割り当てようとした場合、既存の割当を維持し何もしない(冪等。BR1.2のGroupMembershipと同じ方針)
    category: policy
    applies_to: RoleAssignment
    trigger: "管理者が既に割り当て済みの(roleId, accountId)または(roleId, groupId)の組を`POST /api/admin/roles/{roleId}/assignments`で再度割り当てようとしたとき"
    logic: "IF 指定された組み合わせのRoleAssignmentが既に存在する THEN 何もせず現在の状態を200/201いずれかで返す(エラーにしない)。ELSE 新規にRoleAssignmentを作成する"
    violation_behaviour: "該当なし(エラーとして扱わない)"
    source: contract-summary.md #13(R-02フォロー、permission Unit Functional Designレビュー iteration 1より)

  - id: BR4.1
    statement: 複数ロール保有時の作業中ロール切替(`GET /api/me/roles`)の候補には、自身のAccountに直接割り当てられたロールに加え、自身が所属するGroupに割り当てられたロールも含める
    category: policy
    applies_to: RoleAssignment, GroupMembership
    trigger: "利用者が自身の切替可能ロール一覧を取得しようとしたとき"
    logic: "対象ロール集合 = {r | RoleAssignment(roleId=r, assigneeType=user, accountId=自身)が存在する} ∪ {r | GroupMembership(accountId=自身, groupId=g)かつRoleAssignment(roleId=r, assigneeType=group, groupId=g)が存在する}"
    violation_behaviour: "該当なし"
    source: FR5.4, functional-design-questions.md Q1

  - id: BR5.1
    statement: あるロール・テーブルの組み合わせに対してTablePermissionレコードが1件も存在しない場合、そのロールはそのテーブルに対する一覧・詳細・作成・編集・削除のすべての操作を拒否される(デフォルト拒否)
    category: authorization
    applies_to: TablePermission
    trigger: "dynamic-data-access Unitがテーブル単位の操作権限を確認したとき(contract-summary.md #3)"
    logic: "IF TablePermission(roleId=対象ロール, tableId=対象テーブル)が存在する THEN 該当操作のcanXxx値に従う。ELSE すべての操作を拒否する"
    violation_behaviour: "dynamic-data-access側で403エラー(RFC 7807)として扱われる"
    source: FR5.1, functional-design-questions.md Q2

  - id: BR5.2
    statement: あるロール・テーブル・カラムの組み合わせに対してColumnPermissionレコードが1件も存在しない場合、そのカラムのaccessLevelはeditable(更新可)として扱う(デフォルト許可)。TablePermission(BR5.1)のデフォルト拒否とは異なる既定方針である
    category: authorization
    applies_to: ColumnPermission
    trigger: "dynamic-data-access Unitがカラム単位のアクセスレベルを確認したとき(contract-summary.md #3)"
    logic: "IF ColumnPermission(roleId=対象ロール, tableId=対象テーブル, columnName=対象カラム)が存在する THEN そのaccessLevelに従う。ELSE editableとして扱う"
    violation_behaviour: "該当なし"
    source: FR5.2(「更新可(デフォルト)」という要件文言そのものが既定値を明示している。TablePermissionのBR5.1とは異なりQ2の確認を要さない)

  - id: BR5.3
    statement: 契約#22(config-management → permission)を受けたとき、指定されたroleIdについて、TablePermission.canList=trueを持つtableIdの集合を返す。BR5.1のデフォルト拒否により、TablePermission未設定のテーブルはこの集合に含まれない
    category: business
    applies_to: TablePermission
    trigger: "config-managementが契約#22を呼び出したとき(GET /api/menuの処理中、frontend-core Unit Functional Designより新設)"
    logic: "対象tableId集合 = {t | TablePermission(roleId=対象roleId, tableId=t, canList=true)が存在する}"
    violation_behaviour: "該当なし(該当tableIdが0件の場合は空集合を返す。エラー条件ではない)"
    source: contract-summary.md #22(frontend-core Unit Functional Designより新設)

  - id: BR6.1
    statement: permission Unitが管理する業務データ権限モデル(Role/Group/RoleAssignment/TablePermission/ColumnPermission)は、管理者専用機能へのアクセス制御(isAdminクレーム、FR5.5/FR5.6)とは別のモデルであり、本Unitはこのアクセストークンのクレームに基づくアクセス制御には関与しない
    category: policy
    applies_to: Role, Group, RoleAssignment, TablePermission, ColumnPermission
    trigger: "該当なし(設計上の恒常的な制約)"
    logic: "該当なし"
    violation_behaviour: "該当なし"
    source: domain-design/components.md PermissionComponent.behaviour
```

## ルールサマリー

| ID | カテゴリ | 概要 |
|---|---|---|
| BR1.1 | validation | Role・Groupのname一意性 |
| BR1.2 | policy | 重複するグループ所属追加は冪等(エラーにしない) |
| BR1.3 | business | ロール・グループの名称変更(PUT) |
| BR1.4 | business | ロール削除(DELETE)。割当参照が残っていれば409 |
| BR1.5 | business | グループ削除(DELETE)。割当参照が残っていれば409 |
| BR3.2 | business | 契約#21(初期ロール割り当て・変更)の全置換ロジック |
| BR3.3 | policy | 重複するロール割り当ては冪等(エラーにしない) |
| BR2.1 | authorization | TablePermissionの5操作を個別設定 |
| BR2.2 | authorization | ColumnPermissionのaccessLevel(editable/readonly/hidden) |
| BR3.1 | validation | RoleAssignmentのaccountId/groupIdは排他 |
| BR4.1 | policy | ロール切替候補にグループ経由のロールを含める |
| BR5.1 | authorization | TablePermission未設定はデフォルト拒否 |
| BR5.2 | authorization | ColumnPermission未設定はデフォルトeditable |
| BR5.3 | business | 契約#22: 指定ロールがcanList権限を持つtableId集合を返す |
| BR6.1 | policy | 業務データ権限モデルはisAdminとは別モデル |
