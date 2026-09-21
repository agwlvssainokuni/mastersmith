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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.permission.dto.RbacExport;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.Group;
import com.mastersmith.permission.entity.GroupMembership;
import com.mastersmith.permission.entity.GroupRole;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.Role;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.GroupMembershipRepository;
import com.mastersmith.permission.repository.GroupRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link RbacExporter}の、実際の組込みH2でのテスト(RBAC設定の書き出し。内部設定DBから直接読み、他から変更できないスナップショットを返す。ユーザーの所属は含めない)。
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RbacExportTest {

  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupRoleRepository groupRoleRepository;
  @Autowired private GroupMembershipRepository membershipRepository;
  @Autowired private PrimaryPermissionRepository primaryRepository;
  @Autowired private AuxiliaryPermissionRepository auxiliaryRepository;

  private RbacExporter exporter;

  @BeforeEach
  void setUp() {
    exporter =
        new RbacExporter(
            roleRepository,
            groupRepository,
            groupRoleRepository,
            primaryRepository,
            auxiliaryRepository);
  }

  @Test
  void anEmptyDatabaseExportsEmptyLists() {
    RbacExport export = exporter.export();

    assertThat(export.roles()).isEmpty();
    assertThat(export.groups()).isEmpty();
    assertThat(export.primaryPermissions()).isEmpty();
    assertThat(export.auxiliaryPermissions()).isEmpty();
  }

  @Test
  void exportsRolesGroupsWithTheirRolesAndBothKindsOfPermissions() {
    Role r1 = roleRepository.save(new Role("role_0001"));
    Role r2 = roleRepository.save(new Role("role_0002"));
    Group g1 = groupRepository.save(new Group("group_0001"));
    groupRepository.save(new Group("group_0002"));
    groupRoleRepository.save(new GroupRole(g1.getGroupId(), r1.getRoleId()));
    groupRoleRepository.save(new GroupRole(g1.getGroupId(), r2.getRoleId()));
    primaryRepository.save(
        new PrimaryPermission(
            r1.getRoleId(), ScopeType.SCHEMA, "schema_0001", PermissionLevel.FULL));
    primaryRepository.save(
        new PrimaryPermission(r2.getRoleId(), ScopeType.TABLE, "table-id-1", PermissionLevel.NONE));
    auxiliaryRepository.save(
        new AuxiliaryPermission(r1.getRoleId(), ScopeType.SCHEMA, "schema_0001", true, null));

    RbacExport export = exporter.export();

    assertThat(export.roles())
        .extracting(RbacExport.Role::name)
        .containsExactlyInAnyOrder("role_0001", "role_0002");
    assertThat(export.groups()).hasSize(2);
    RbacExport.Group exportedGroup =
        export.groups().stream()
            .filter(g -> g.name().equals("group_0001"))
            .findFirst()
            .orElseThrow();
    assertThat(exportedGroup.roleIds()).containsExactlyInAnyOrder(r1.getRoleId(), r2.getRoleId());
    assertThat(
            export.groups().stream()
                .filter(g -> g.name().equals("group_0002"))
                .findFirst()
                .orElseThrow()
                .roleIds())
        .isEmpty();
    assertThat(export.primaryPermissions())
        .extracting(RbacExport.Primary::scopeRef, RbacExport.Primary::level)
        .containsExactlyInAnyOrder(
            org.assertj.core.api.Assertions.tuple("schema_0001", PermissionLevel.FULL),
            org.assertj.core.api.Assertions.tuple("table-id-1", PermissionLevel.NONE));
    assertThat(export.auxiliaryPermissions()).hasSize(1);
    assertThat(export.auxiliaryPermissions().get(0).createAllowed()).isTrue();
    assertThat(export.auxiliaryPermissions().get(0).deleteAllowed()).isNull();
  }

  @Test
  void userGroupMembershipsAreNotPartOfTheExport() {
    Group group = groupRepository.save(new Group("group_0001"));
    membershipRepository.save(new GroupMembership(group.getGroupId(), "user-1"));

    RbacExport export = exporter.export();

    assertThat(export.groups()).hasSize(1);
    assertThat(export.groups().get(0).roleIds()).isEmpty();
    assertThat(export.toString()).doesNotContain("user-1");
  }

  @Test
  void theSnapshotIsUnmodifiableAndIndependentOfLaterChanges() {
    Role role = roleRepository.save(new Role("role_0001"));

    RbacExport export = exporter.export();
    roleRepository.save(new Role("role_0002"));

    assertThat(export.roles()).hasSize(1);
    assertThatThrownBy(() -> export.roles().add(new RbacExport.Role("x", "y")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(export.roles().get(0).roleId()).isEqualTo(role.getRoleId());
  }

  @Test
  void nullListsBecomeEmpty() {
    RbacExport empty = new RbacExport(null, null, null, null);

    assertThat(empty.roles()).isEmpty();
    assertThat(empty.auxiliaryPermissions()).isEmpty();
    assertThat(new RbacExport.Group("g", "n", null).roleIds()).isEmpty();
  }
}
