# Performance Design: permission

## インデックス設計

権限確認(内部呼び出しAPI、契約#3・#22)は高頻度に呼ばれるため、以下のインデックスを設ける。

- `TablePermission`: `(roleId, tableId)`複合インデックス(ユニーク制約も兼ねる)
- `ColumnPermission`: `(roleId, tableId, columnName)`複合インデックス(ユニーク制約も兼ねる)
- `RoleAssignment`: `(assigneeType, accountId)`および`(assigneeType, groupId)`の複合インデックス(有効ロール集合の計算、BR4.1)
- `GroupMembership`: `accountId`への単一インデックス(所属グループ検索用)

## キャッシュ

キャッシュ層は設けない(軽量版方針)。権限変更(TablePermission・ColumnPermissionの更新)の即時反映を優先し、キャッシュ無効化ロジックの複雑さを避ける。想定運用規模(自宅サーバ1台)ではインデックスによるDBアクセスで十分な応答速度が得られると判断する。

## 応答速度の一般方針

requirements.md NFR1(厳密な数値目標なし)を踏襲する。
