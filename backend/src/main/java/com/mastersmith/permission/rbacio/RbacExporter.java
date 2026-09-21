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

package com.mastersmith.permission.rbacio;

import com.mastersmith.permission.dto.RbacExport;
import com.mastersmith.permission.entity.GroupRole;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.GroupRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * RBAC設定(ロール・グループ・グループとロールの対応・主権限・補助権限)を、キャッシュを介さず、内部設定DBから直接読み、他から変更できないスナップショットとして返す(config-import-export
 * BR9.1・BR9.5、NFR4.4)。 ユーザー・ユーザーのグループ所属は、含めない。呼び出し元の(読み取り専用の)トランザクションの中で呼ぶこと。
 */
@Component
public class RbacExporter {

  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;
  private final GroupRoleRepository groupRoleRepository;
  private final PrimaryPermissionRepository primaryPermissionRepository;
  private final AuxiliaryPermissionRepository auxiliaryPermissionRepository;

  public RbacExporter(
      RoleRepository roleRepository,
      GroupRepository groupRepository,
      GroupRoleRepository groupRoleRepository,
      PrimaryPermissionRepository primaryPermissionRepository,
      AuxiliaryPermissionRepository auxiliaryPermissionRepository) {
    this.roleRepository = roleRepository;
    this.groupRepository = groupRepository;
    this.groupRoleRepository = groupRoleRepository;
    this.primaryPermissionRepository = primaryPermissionRepository;
    this.auxiliaryPermissionRepository = auxiliaryPermissionRepository;
  }

  public RbacExport export() {
    Map<String, List<String>> roleIdsByGroupId = new HashMap<>();
    for (GroupRole groupRole : groupRoleRepository.findAll()) {
      roleIdsByGroupId
          .computeIfAbsent(groupRole.getGroupId(), k -> new ArrayList<>())
          .add(groupRole.getRoleId());
    }
    return new RbacExport(
        roleRepository.findAll().stream()
            .map(r -> new RbacExport.Role(r.getRoleId(), r.getName()))
            .toList(),
        groupRepository.findAll().stream()
            .map(
                g ->
                    new RbacExport.Group(
                        g.getGroupId(),
                        g.getName(),
                        roleIdsByGroupId.getOrDefault(g.getGroupId(), List.of())))
            .toList(),
        primaryPermissionRepository.findAll().stream()
            .map(
                p ->
                    new RbacExport.Primary(
                        p.getRoleId(), p.getScopeType(), p.getScopeRef(), p.getLevel()))
            .toList(),
        auxiliaryPermissionRepository.findAll().stream()
            .map(
                a ->
                    new RbacExport.Auxiliary(
                        a.getRoleId(),
                        a.getScopeType(),
                        a.getScopeRef(),
                        a.getCreateAllowed(),
                        a.getDeleteAllowed()))
            .toList());
  }
}
