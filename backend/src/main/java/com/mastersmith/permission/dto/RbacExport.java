/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastersmith.permission.dto;

import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.List;

/**
 * permission-engineが、設定の書き出し(config-import-export)に返す、RBAC設定一式の、他から変更できないスナップショット(C10の追補)。内部設定DBから、直接読んだ値で、内部ID・不透明な対象の識別子
 * ({@code scopeRef})のままである。自然キーへの変換は、呼び出し元が行う。ユーザー・ユーザーのグループ所属は含めない。
 *
 * @param roles ロール
 * @param groups グループと、対応するロールのID
 * @param primaryPermissions 主権限
 * @param auxiliaryPermissions 補助権限
 */
public record RbacExport(
    List<Role> roles,
    List<Group> groups,
    List<Primary> primaryPermissions,
    List<Auxiliary> auxiliaryPermissions) {

  public RbacExport {
    roles = roles == null ? List.of() : List.copyOf(roles);
    groups = groups == null ? List.of() : List.copyOf(groups);
    primaryPermissions = primaryPermissions == null ? List.of() : List.copyOf(primaryPermissions);
    auxiliaryPermissions =
        auxiliaryPermissions == null ? List.of() : List.copyOf(auxiliaryPermissions);
  }

  /** ロール1件。 */
  public record Role(String roleId, String name) {}

  /** グループ1件と、対応するロールのID。 */
  public record Group(String groupId, String name, List<String> roleIds) {
    public Group {
      roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }
  }

  /** 主権限1件。 */
  public record Primary(
      String roleId, ScopeType scopeType, String scopeRef, PermissionLevel level) {}

  /** 補助権限1件。 */
  public record Auxiliary(
      String roleId,
      ScopeType scopeType,
      String scopeRef,
      Boolean createAllowed,
      Boolean deleteAllowed) {}
}
