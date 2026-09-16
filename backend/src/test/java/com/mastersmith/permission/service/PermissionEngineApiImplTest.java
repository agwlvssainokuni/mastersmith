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

package com.mastersmith.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mastersmith.permission.bootstrap.BootstrapStateChecker;
import com.mastersmith.permission.cache.PermissionCacheKey;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.GroupMembership;
import com.mastersmith.permission.entity.GroupRole;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.escalation.PermissionEscalationChecker;
import com.mastersmith.permission.exception.PermissionEscalationException;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.GroupMembershipRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import com.mastersmith.permission.resolver.PermissionResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PermissionEngineApiImpl}の単体テスト(依存をモック。キャッシュのみ実インスタンスのCaffeineキャッシュを用いる)。
 *
 * <p>認可拒否(negative-authorization)専用テスト(team.md Q8-c必須項目)として、{@link
 * #resolveEffectivePermissionReturnsNoneWhenActiveRoleIdDoesNotExist()}・{@link
 * #canAccessScreenDeniesWhenEffectiveLevelIsNone()}・{@link
 * #assignPermissionThrowsPermissionEscalationExceptionAndPersistsNothing()}を含む。
 */
@ExtendWith(MockitoExtension.class)
class PermissionEngineApiImplTest {

  private static final String ROLE_ID = "role-1";
  private static final ScopeType SCOPE_TYPE = ScopeType.TABLE;
  private static final String SCOPE_REF = "table-1";

  @Mock private RoleRepository roleRepository;
  @Mock private PrimaryPermissionRepository primaryPermissionRepository;
  @Mock private AuxiliaryPermissionRepository auxiliaryPermissionRepository;
  @Mock private GroupMembershipRepository groupMembershipRepository;
  @Mock private GroupRoleRepository groupRoleRepository;
  @Mock private PermissionResolver permissionResolver;
  @Mock private PermissionEscalationChecker escalationChecker;
  @Mock private BootstrapStateChecker bootstrapStateChecker;

  private Cache<PermissionCacheKey, EffectivePermission> permissionCache;
  private PermissionEngineApiImpl service;

  @BeforeEach
  void setUp() {
    permissionCache = Caffeine.newBuilder().build();
    service =
        new PermissionEngineApiImpl(
            roleRepository,
            primaryPermissionRepository,
            auxiliaryPermissionRepository,
            groupMembershipRepository,
            groupRoleRepository,
            permissionResolver,
            escalationChecker,
            bootstrapStateChecker,
            permissionCache,
            new SimpleMeterRegistry());
  }

  // ---- resolveEffectivePermission ----

  @Test
  void resolveEffectivePermissionDelegatesToResolverAndCachesTheResult() {
    when(roleRepository.existsById(ROLE_ID)).thenReturn(true);
    EffectivePermission expected = new EffectivePermission(PermissionLevel.FULL, true, false);
    when(permissionResolver.resolve(ROLE_ID, SCOPE_TYPE, SCOPE_REF)).thenReturn(expected);

    EffectivePermission first = service.resolveEffectivePermission(ROLE_ID, SCOPE_TYPE, SCOPE_REF);
    EffectivePermission second = service.resolveEffectivePermission(ROLE_ID, SCOPE_TYPE, SCOPE_REF);

    assertThat(first).isEqualTo(expected);
    assertThat(second).isEqualTo(expected);
    // 2回目の呼び出しはキャッシュヒットとなり、resolverへは1回しか委譲されない。
    verify(permissionResolver, times(1)).resolve(ROLE_ID, SCOPE_TYPE, SCOPE_REF);
  }

  @Test
  void resolveEffectivePermissionReturnsNoneWhenActiveRoleIdDoesNotExist() {
    // negative-authorization: 実在しないRoleに対しては安全側のデフォルト(NONE/禁止)を返す(security-design.md「多層防御」)。
    when(roleRepository.existsById("unknown-role")).thenReturn(false);

    EffectivePermission result =
        service.resolveEffectivePermission("unknown-role", SCOPE_TYPE, SCOPE_REF);

    assertThat(result).isEqualTo(EffectivePermission.NONE);
  }

  @Test
  void resolveEffectivePermissionRejectsBlankScopeRef() {
    assertThatThrownBy(() -> service.resolveEffectivePermission(ROLE_ID, SCOPE_TYPE, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveEffectivePermissionRejectsScopeRefExceedingMaxLength() {
    String tooLong = "x".repeat(256);
    assertThatThrownBy(() -> service.resolveEffectivePermission(ROLE_ID, SCOPE_TYPE, tooLong))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- canAccessScreen ----

  @Test
  void canAccessScreenAllowsWhenBootstrapStateAndScreenKeyIsConfigImportExport() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(true);

    assertThat(service.canAccessScreen(ROLE_ID, "config-import-export")).isTrue();
  }

  @Test
  void canAccessScreenResolvesReservedScreenKeyAgainstItsReservedSchema() {
    // "user-management"はconfig-import-export専用のBR3.13(a)対象外であり、bootstrapStateCheckerは
    // 呼び出されない(実装の&&短絡評価により、screenKeyが一致しない限りisBootstrapState()は問い合わせない)。
    when(roleRepository.existsById(ROLE_ID)).thenReturn(true);
    when(permissionResolver.resolve(ROLE_ID, ScopeType.SCHEMA, "__system__:user-management"))
        .thenReturn(new EffectivePermission(PermissionLevel.READ, false, false));

    assertThat(service.canAccessScreen(ROLE_ID, "user-management")).isTrue();
  }

  @Test
  void canAccessScreenTreatsUnreservedScreenKeyAsTableConfigId() {
    when(roleRepository.existsById(ROLE_ID)).thenReturn(true);
    when(permissionResolver.resolve(ROLE_ID, ScopeType.TABLE, "products-table"))
        .thenReturn(new EffectivePermission(PermissionLevel.FULL, false, false));

    assertThat(service.canAccessScreen(ROLE_ID, "products-table")).isTrue();
  }

  @Test
  void canAccessScreenDeniesWhenEffectiveLevelIsNone() {
    // negative-authorization: 実効権限がNONEの場合は拒否される。
    when(roleRepository.existsById(ROLE_ID)).thenReturn(true);
    when(permissionResolver.resolve(ROLE_ID, ScopeType.TABLE, "restricted-table"))
        .thenReturn(EffectivePermission.NONE);

    assertThat(service.canAccessScreen(ROLE_ID, "restricted-table")).isFalse();
  }

  @Test
  void canAccessScreenDoesNotBypassBootstrapExceptionForOtherReservedScreens() {
    // BR3.13(a)の無条件許可は"config-import-export"のみが対象であり、他の予約screenKeyには及ばない
    // (bootstrapStateCheckerが仮にtrueを返す状況であっても、screenKeyが一致しないため問い合わせすら発生しない)。
    when(roleRepository.existsById(ROLE_ID)).thenReturn(true);
    when(permissionResolver.resolve(ROLE_ID, ScopeType.SCHEMA, "__system__:audit-log"))
        .thenReturn(EffectivePermission.NONE);

    assertThat(service.canAccessScreen(ROLE_ID, "audit-log")).isFalse();
  }

  // ---- assignPermission ----

  @Test
  void assignPermissionCreatesANewRowWhenNoneExists() {
    when(primaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
            ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .thenReturn(Optional.empty());

    service.assignPermission("actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL);

    verify(escalationChecker)
        .checkPrimaryPermissionAssignment(
            "actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL);
    verify(primaryPermissionRepository).save(any(PrimaryPermission.class));
  }

  @Test
  void assignPermissionUpdatesTheExistingRowWhenOnePresent() {
    PrimaryPermission existing =
        new PrimaryPermission(ROLE_ID, SCOPE_TYPE, SCOPE_REF, PermissionLevel.READ);
    when(primaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
            ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .thenReturn(Optional.of(existing));

    service.assignPermission("actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL);

    assertThat(existing.getLevel()).isEqualTo(PermissionLevel.FULL);
    verify(primaryPermissionRepository).save(existing);
  }

  @Test
  void assignPermissionInvalidatesTheCacheOnSuccess() {
    permissionCache.put(
        new PermissionCacheKey(ROLE_ID, SCOPE_TYPE, SCOPE_REF),
        new EffectivePermission(PermissionLevel.READ, false, false));
    when(primaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
            ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .thenReturn(Optional.empty());

    service.assignPermission("actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL);

    assertThat(permissionCache.asMap()).isEmpty();
  }

  @Test
  void assignPermissionThrowsPermissionEscalationExceptionAndPersistsNothing() {
    // negative-authorization: 権限昇格が試みられた割当は、永続化もキャッシュ無効化も一切行わない。
    org.mockito.Mockito.doThrow(new PermissionEscalationException(ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .when(escalationChecker)
        .checkPrimaryPermissionAssignment(
            eq("actor-role"), eq(ROLE_ID), eq(SCOPE_TYPE), eq(SCOPE_REF), eq(PermissionLevel.FULL));

    assertThatThrownBy(
            () ->
                service.assignPermission(
                    "actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL))
        .isInstanceOf(PermissionEscalationException.class);

    verify(primaryPermissionRepository, never()).save(any());
  }

  @Test
  void assignPermissionRejectsBlankScopeRef() {
    assertThatThrownBy(
            () ->
                service.assignPermission(
                    "actor-role", ROLE_ID, SCOPE_TYPE, "", PermissionLevel.FULL))
        .isInstanceOf(IllegalArgumentException.class);
    verify(escalationChecker, never())
        .checkPrimaryPermissionAssignment(any(), any(), any(), any(), any());
  }

  @Test
  void assignPermissionDoesNotThrowDespiteNoAuditEventBeingPublished() {
    // アーキテクチャレビュー iteration 1, NOT-READY, R-01是正の回帰テスト: rules.md BR3.11の
    // PermissionChangedサマリイベントはconfig-import-exportの1回のインポート実行単位で発行される責務であり、
    // permission-engine自身はassignPermission呼び出し単位ではいかなる監査イベントも発行しない。
    // 発行元(ApplicationEventPublisher)を一切保持しない実装でも、呼び出しは正常に完了する。
    when(primaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
            ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .thenReturn(Optional.empty());

    assertThatCode(
            () ->
                service.assignPermission(
                    "actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL))
        .doesNotThrowAnyException();
  }

  // ---- assignAuxiliaryPermission ----

  @Test
  void assignAuxiliaryPermissionCreatesANewRowWhenNoneExists() {
    when(auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
            ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .thenReturn(Optional.empty());

    service.assignAuxiliaryPermission("actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, true, false);

    verify(auxiliaryPermissionRepository).save(any(AuxiliaryPermission.class));
  }

  @Test
  void assignAuxiliaryPermissionUpdatesTheExistingRowWhenOnePresent() {
    AuxiliaryPermission existing =
        new AuxiliaryPermission(ROLE_ID, SCOPE_TYPE, SCOPE_REF, null, null);
    when(auxiliaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
            ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .thenReturn(Optional.of(existing));

    service.assignAuxiliaryPermission("actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, true, true);

    assertThat(existing.getCreateAllowed()).isTrue();
    assertThat(existing.getDeleteAllowed()).isTrue();
    verify(auxiliaryPermissionRepository).save(existing);
  }

  @Test
  void assignAuxiliaryPermissionThrowsPermissionEscalationExceptionAndPersistsNothing() {
    org.mockito.Mockito.doThrow(new PermissionEscalationException(ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .when(escalationChecker)
        .checkAuxiliaryPermissionAssignment(
            eq("actor-role"), eq(ROLE_ID), eq(SCOPE_TYPE), eq(SCOPE_REF), eq(true), isNull());

    assertThatThrownBy(
            () ->
                service.assignAuxiliaryPermission(
                    "actor-role", ROLE_ID, SCOPE_TYPE, SCOPE_REF, true, null))
        .isInstanceOf(PermissionEscalationException.class);

    verify(auxiliaryPermissionRepository, never()).save(any());
  }

  @Test
  void assignAuxiliaryPermissionRejectsColumnScopeType() {
    // entities.md AuxiliaryPermission.scopeType.allowed_values: SCHEMA/TABLEのみが対象であり、
    // COLUMNはfail fastで拒否する(アーキテクチャレビュー iteration 1, NOT-READY, R-05対応)。
    assertThatThrownBy(
            () ->
                service.assignAuxiliaryPermission(
                    "actor-role", ROLE_ID, ScopeType.COLUMN, SCOPE_REF, true, false))
        .isInstanceOf(IllegalArgumentException.class);

    verify(escalationChecker, never())
        .checkAuxiliaryPermissionAssignment(any(), any(), any(), any(), any(), any());
    verify(auxiliaryPermissionRepository, never()).save(any());
  }

  // ---- getGroupDerivedRoleIds ----

  @Test
  void getGroupDerivedRoleIdsReturnsTheUnionOfRolesFromAllGroups() {
    when(groupMembershipRepository.findByIdUserId("user-1"))
        .thenReturn(
            List.of(
                new GroupMembership("group-1", "user-1"),
                new GroupMembership("group-2", "user-1")));
    when(groupRoleRepository.findByIdGroupIdIn(List.of("group-1", "group-2")))
        .thenReturn(
            List.of(
                new GroupRole("group-1", "role-a"),
                new GroupRole("group-1", "role-b"),
                new GroupRole("group-2", "role-a")));

    List<String> roleIds = service.getGroupDerivedRoleIds("user-1");

    assertThat(roleIds).containsExactlyInAnyOrder("role-a", "role-b");
  }

  @Test
  void getGroupDerivedRoleIdsReturnsEmptyListWhenUserBelongsToNoGroup() {
    when(groupMembershipRepository.findByIdUserId("lone-user")).thenReturn(List.of());

    assertThat(service.getGroupDerivedRoleIds("lone-user")).isEmpty();
    verify(groupRoleRepository, never()).findByIdGroupIdIn(any());
  }
}
