# Domain Entities: account-management

account-managementは、dynamic-data-access(domain-design/components.md参照)と同様に永続エンティティを持たない
Unitである。Accountの永続化スキーマはauth(U5)が唯一の所有者であり(契約#19)、RoleAssignmentの永続化スキーマは
permission(U3)が唯一の所有者である。account-managementはいずれのテーブルへも直接アクセスせず、契約#4
(account-management → auth)・契約#21(account-management → permission)経由でのみ操作する。

## 取り扱うデータ形状(機械可読、契約から導出)

```yaml
data_shapes:
  - name: AccountCreateRequest
    attributes: [name, email, initialRoleIds] # initialRoleIdsは契約#21へ渡す。name/emailは契約#4へ渡す
    source: contract-summary.md #17(POST /api/admin/accounts)

  - name: AccountUpdateRequest
    attributes: [name, email, roleIds] # roleIdsは契約#21へ渡し全置換。name/emailは契約#4へ渡す
    source: contract-summary.md #17(PUT /api/admin/accounts/{id})

  - name: AccountView
    attributes: [accountId, name, email, status, isAdmin, roleIds] # 契約#4(Account本体)と契約#21(ロール一覧)の結果を合成して返す
    source: contract-summary.md #17(GET /api/admin/accounts, GET /api/admin/accounts/{id})

note: >
  AccountView.roleIdsは、permission(契約#21)から取得した直接RoleAssignment一覧のroleIdのみを含む
  (グループ経由の割り当ては含まない。編集画面での直接編集対象は直接割り当てのみであるため)。
```

## エンティティサマリー

| エンティティ | 所有Unit | 備考 |
|---|---|---|
| (永続エンティティなし) | — | account-managementは永続エンティティを一切所有しない。Accountはauth(契約#4)、RoleAssignmentはpermission(契約#21)が所有する |
| AccountView | (DTO、非永続) | 契約#4・#21の結果を合成した表示専用の形状 |
