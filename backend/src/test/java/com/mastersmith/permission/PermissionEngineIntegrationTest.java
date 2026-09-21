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

package com.mastersmith.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.Group;
import com.mastersmith.permission.entity.GroupMembership;
import com.mastersmith.permission.entity.GroupRole;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.Role;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.exception.PermissionEscalationException;
import com.mastersmith.permission.repository.GroupMembershipRepository;
import com.mastersmith.permission.repository.GroupRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * permission-engine(U3)のSpring Boot統合テスト(組込みH2、実際のDB状態を検証、plan Step10)。
 *
 * <p>各テストは{@link Transactional}によりロールバックされ、テスト間で状態を共有しない(Role等はコンストラクタで都度新しいUUIDを採番するため、
 * ロールバックに関わらずキャッシュキーの衝突も生じない)。認可拒否(negative-authorization)専用テスト(team.md Q8-c必須項目)として{@link
 * #deniesAccessWhenNoPermissionIsGranted()}・{@link
 * #assignPermissionThrowsWhenActorAttemptsToExceedTheirOwnEffectivePermission()}を含む。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@Transactional
class PermissionEngineIntegrationTest {

  @Autowired private PermissionEngineApi permissionEngineApi;
  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupMembershipRepository groupMembershipRepository;
  @Autowired private GroupRoleRepository groupRoleRepository;
  @Autowired private ConfigCache configCache;
  @Autowired private TableConfigRepository tableConfigRepository;
  @Autowired private ColumnConfigRepository columnConfigRepository;

  @Autowired
  @Qualifier("transactionManager")
  private PlatformTransactionManager transactionManager;

  @Test
  void resolvesEffectivePermissionThroughColumnToTableToSchemaFallbackAgainstRealDatabase() {
    String schemaName = "schema-" + UUID.randomUUID();
    TableConfig tableConfig = new TableConfig(schemaName, "products");
    ColumnConfig columnConfig =
        new ColumnConfig(tableConfig.getTableConfigId(), "price", EditorType.DECIMAL);
    // 設定は、独立したトランザクションで確定させる(config-engineのキャッシュは、確定済みの内容だけを読み込む。
    // 旧importConfigSet(未確定の内容を、キャッシュへ載せていた)は、検証と反映の分割(C9の追補)で置き換えたため、
    // リポジトリで直接保存し、キャッシュの無効化で読み込ませる)。
    TransactionTemplate committed = new TransactionTemplate(transactionManager);
    committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    committed.executeWithoutResult(
        status -> {
          tableConfigRepository.save(tableConfig);
          columnConfigRepository.save(columnConfig);
        });
    configCache.invalidate();
    try {
      assertColumnResolvesThroughSchemaFallback(schemaName, columnConfig);
    } finally {
      committed.executeWithoutResult(
          status -> {
            columnConfigRepository.deleteById(columnConfig.getColumnConfigId());
            tableConfigRepository.deleteById(tableConfig.getTableConfigId());
          });
      configCache.invalidate();
    }
  }

  private void assertColumnResolvesThroughSchemaFallback(
      String schemaName, ColumnConfig columnConfig) {
    Role role = roleRepository.save(new Role("role-" + UUID.randomUUID()));
    // ブートストラップ状態(PrimaryPermission行が0件)のため、昇格チェックなしで割当できる。
    permissionEngineApi.assignPermission(
        role.getRoleId(), role.getRoleId(), ScopeType.SCHEMA, schemaName, PermissionLevel.FULL);

    // COLUMN・TABLEいずれにも明示設定がないため、SCHEMA階層まで遡って解決される(rules.md BR3.4)。
    EffectivePermission result =
        permissionEngineApi.resolveEffectivePermission(
            role.getRoleId(), ScopeType.COLUMN, columnConfig.getColumnConfigId());

    assertThat(result.level()).isEqualTo(PermissionLevel.FULL);
  }

  @Test
  void deniesAccessWhenNoPermissionIsGranted() {
    // negative-authorization: 権限が一切割り当てられていないロールは、実効権限NONE・画面アクセス不可となる。
    Role role = roleRepository.save(new Role("role-" + UUID.randomUUID()));
    String scopeRef = "schema-" + UUID.randomUUID();

    EffectivePermission result =
        permissionEngineApi.resolveEffectivePermission(
            role.getRoleId(), ScopeType.SCHEMA, scopeRef);

    assertThat(result.level()).isEqualTo(PermissionLevel.NONE);
    assertThat(result.canCreate()).isFalse();
    assertThat(result.canDelete()).isFalse();
    assertThat(permissionEngineApi.canAccessScreen(role.getRoleId(), scopeRef)).isFalse();
  }

  @Test
  void resolveEffectivePermissionDeniesAnActiveRoleIdThatDoesNotExist() {
    // negative-authorization: 実在しないRoleに対しては安全側のデフォルトを返す(security-design.md「多層防御」)。
    EffectivePermission result =
        permissionEngineApi.resolveEffectivePermission(
            "non-existent-role-id", ScopeType.SCHEMA, "public");

    assertThat(result).isEqualTo(EffectivePermission.NONE);
  }

  // ---- activeRoleIdが未選択(null・空)の扱い(authentication-service(U5)の機能設計 BR5.12・追補6番、実H2) ----

  @ParameterizedTest(name = "activeRoleId=''{0}'' × RBAC設定が空 × config-import-export -> 許可")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "ghost-role"})
  void theBootstrapExceptionForConfigImportExportAppliesRegardlessOfTheActiveRole(
      String activeRoleId) {
    // RBAC設定が1件もない間(ブートストラップ状態)は、ロールを持たない初期管理者も、最初のRBAC設定のインポートへ到達できる。
    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "config-import-export")).isTrue();
    // 他の画面には、及ばない。
    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "user-management")).isFalse();
    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "audit-log")).isFalse();
    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "some-table-config-id")).isFalse();
  }

  @ParameterizedTest(name = "activeRoleId=''{0}'' × RBAC設定あり -> 全画面で拒否(fail closed)")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "ghost-role"})
  void anUnselectedOrUnknownActiveRoleIsDeniedEverywhereOnceRbacIsConfigured(String activeRoleId) {
    Role admin = roleRepository.save(new Role("admin-" + UUID.randomUUID()));
    // RBAC設定が1件でもあれば、ブートストラップ状態は終了する。
    permissionEngineApi.assignPermission(
        admin.getRoleId(),
        admin.getRoleId(),
        ScopeType.SCHEMA,
        "__system__:config-import-export",
        PermissionLevel.FULL);

    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "config-import-export")).isFalse();
    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "user-management")).isFalse();
    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "audit-log")).isFalse();
    assertThat(permissionEngineApi.canAccessScreen(activeRoleId, "some-table-config-id")).isFalse();
    for (ScopeType scopeType : ScopeType.values()) {
      assertThat(permissionEngineApi.resolveEffectivePermission(activeRoleId, scopeType, "public"))
          .isEqualTo(EffectivePermission.NONE);
    }
    // 権限のあるロールは、同じ画面へ到達できる(対照)。
    assertThat(permissionEngineApi.canAccessScreen(admin.getRoleId(), "config-import-export"))
        .isTrue();
  }

  @Test
  void bootstrapAllowsFirstAssignmentThenEnforcesEscalationCheckAfterward() {
    Role adminRole = roleRepository.save(new Role("admin-" + UUID.randomUUID()));
    Role limitedRole = roleRepository.save(new Role("limited-" + UUID.randomUUID()));
    String scopeRef = "schema-" + UUID.randomUUID();

    // BR3.13(a): ブートストラップ状態では、初期管理者が最初のRBACインポート画面へ無条件で到達できる。
    assertThat(permissionEngineApi.canAccessScreen(adminRole.getRoleId(), "config-import-export"))
        .isTrue();

    // BR3.13(b): ブートストラップ状態では昇格チェックをスキップし、無条件に割当を許可する。
    permissionEngineApi.assignPermission(
        adminRole.getRoleId(),
        adminRole.getRoleId(),
        ScopeType.SCHEMA,
        scopeRef,
        PermissionLevel.FULL);
    permissionEngineApi.assignPermission(
        adminRole.getRoleId(),
        limitedRole.getRoleId(),
        ScopeType.SCHEMA,
        scopeRef,
        PermissionLevel.READ);

    // ブートストラップ状態は永続的に終了しており、limitedRole(READ)がadminRole相当(FULL)への
    // 昇格を試みると拒否される。
    assertThatThrownBy(
            () ->
                permissionEngineApi.assignPermission(
                    limitedRole.getRoleId(),
                    limitedRole.getRoleId(),
                    ScopeType.SCHEMA,
                    scopeRef,
                    PermissionLevel.FULL))
        .isInstanceOf(PermissionEscalationException.class);

    // 拒否された割当はDBへ反映されない: limitedRoleの実効権限はREADのまま。
    EffectivePermission stillRead =
        permissionEngineApi.resolveEffectivePermission(
            limitedRole.getRoleId(), ScopeType.SCHEMA, scopeRef);
    assertThat(stillRead.level()).isEqualTo(PermissionLevel.READ);
  }

  @Test
  void assignPermissionThrowsWhenActorAttemptsToExceedTheirOwnEffectivePermission() {
    // negative-authorization: 自分自身への昇格(project.md Forbidden)も同じ経路で拒否される。
    Role role = roleRepository.save(new Role("role-" + UUID.randomUUID()));
    String scopeRef = "schema-" + UUID.randomUUID();
    // ブートストラップを終えるため、別スコープへ先に1件割り当てておく。
    permissionEngineApi.assignPermission(
        role.getRoleId(),
        role.getRoleId(),
        ScopeType.SCHEMA,
        "bootstrap-" + UUID.randomUUID(),
        PermissionLevel.READ);

    assertThatThrownBy(
            () ->
                permissionEngineApi.assignPermission(
                    role.getRoleId(),
                    role.getRoleId(),
                    ScopeType.SCHEMA,
                    scopeRef,
                    PermissionLevel.FULL))
        .isInstanceOf(PermissionEscalationException.class);
  }

  @Test
  void cacheIsInvalidatedSoADemotionTakesEffectImmediately() {
    Role adminRole = roleRepository.save(new Role("admin-" + UUID.randomUUID()));
    Role targetRole = roleRepository.save(new Role("target-" + UUID.randomUUID()));
    String scopeRef = "schema-" + UUID.randomUUID();

    permissionEngineApi.assignPermission(
        adminRole.getRoleId(),
        adminRole.getRoleId(),
        ScopeType.SCHEMA,
        scopeRef,
        PermissionLevel.FULL);
    permissionEngineApi.assignPermission(
        adminRole.getRoleId(),
        targetRole.getRoleId(),
        ScopeType.SCHEMA,
        scopeRef,
        PermissionLevel.FULL);

    // 1回目の解決でキャッシュへ載る。
    assertThat(
            permissionEngineApi
                .resolveEffectivePermission(targetRole.getRoleId(), ScopeType.SCHEMA, scopeRef)
                .level())
        .isEqualTo(PermissionLevel.FULL);

    // adminRole自身をREADへ降格(この時点でadminRoleの実効権限はFULLなので許可される)。
    permissionEngineApi.assignPermission(
        adminRole.getRoleId(),
        targetRole.getRoleId(),
        ScopeType.SCHEMA,
        scopeRef,
        PermissionLevel.READ);

    // performance-design.md R-01: invalidateAll()により、キャッシュ済みだったtargetRoleのエントリも
    // 即座に無効化され、降格後の値が返る(TTL経過を待たない)。
    assertThat(
            permissionEngineApi
                .resolveEffectivePermission(targetRole.getRoleId(), ScopeType.SCHEMA, scopeRef)
                .level())
        .isEqualTo(PermissionLevel.READ);
  }

  @Test
  void getGroupDerivedRoleIdsReflectsActualGroupMembershipInTheDatabase() {
    Role directRole = roleRepository.save(new Role("direct-" + UUID.randomUUID()));
    Role groupRole1 = roleRepository.save(new Role("group-role-1-" + UUID.randomUUID()));
    Role groupRole2 = roleRepository.save(new Role("group-role-2-" + UUID.randomUUID()));
    Group group = groupRepository.save(new Group("group-" + UUID.randomUUID()));
    String userId = "user-" + UUID.randomUUID();
    groupMembershipRepository.save(new GroupMembership(group.getGroupId(), userId));
    groupRoleRepository.save(new GroupRole(group.getGroupId(), groupRole1.getRoleId()));
    groupRoleRepository.save(new GroupRole(group.getGroupId(), groupRole2.getRoleId()));

    List<String> derivedRoleIds = permissionEngineApi.getGroupDerivedRoleIds(userId);

    // 直接付与ロール(directRole)は本メソッドの対象外(BR3.2: Group経由のみ)。
    assertThat(derivedRoleIds)
        .containsExactlyInAnyOrder(groupRole1.getRoleId(), groupRole2.getRoleId())
        .doesNotContain(directRole.getRoleId());
  }

  @Test
  void getGroupDerivedRoleIdsReturnsEmptyListForAUserWithNoGroupMembership() {
    assertThat(permissionEngineApi.getGroupDerivedRoleIds("lone-user-" + UUID.randomUUID()))
        .isEmpty();
  }
}
