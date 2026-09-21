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
import static org.mockito.Mockito.lenient;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  // ---- activeRoleIdが未選択(null・空)の扱い(authentication-service(U5)の機能設計 BR5.12・追補6番) ----
  //
  // team.md「権限判定ロジックの追加合格条件」: activeRoleId(null・空・空白のみ・実在・実在しない) × RBAC設定の有無
  // (ブートストラップ状態) × 画面(config-import-exportを含む)の組み合わせを網羅するテーブル駆動テスト。
  // 実装に先立って、組み合わせと期待値(下の表)を洗い出した(team.mdのTesting Posture: 権限判定ロジックの例外)。
  //
  // | activeRoleId | RBAC設定 | 画面 | 期待 |
  // | null・空・空白のみ | 空(ブートストラップ) | config-import-export | 許可(activeRoleIdにかかわらず適用) |
  // | null・空・空白のみ | 空(ブートストラップ) | それ以外(予約・業務) | 拒否(fail closed) |
  // | null・空・空白のみ | あり | すべて | 拒否(fail closed) |
  // | 実在しない | 空 | config-import-export | 許可(activeRoleIdにかかわらず適用) |
  // | 実在しない | 空 | それ以外 | 拒否 |
  // | 実在しない | あり | すべて | 拒否 |
  // | 実在(権限NONE) | あり | すべて | 拒否 |
  // | 実在(権限READ以上) | あり | 権限のある画面 | 許可 |

  private record ScreenCase(
      String label,
      String activeRoleId,
      boolean roleExists,
      PermissionLevel level,
      boolean bootstrap,
      String screenKey,
      boolean expected) {
    @Override
    public String toString() {
      return label;
    }
  }

  private static ScreenCase screen(
      String label,
      String activeRoleId,
      boolean roleExists,
      PermissionLevel level,
      boolean bootstrap,
      String screenKey,
      boolean expected) {
    return new ScreenCase(label, activeRoleId, roleExists, level, bootstrap, screenKey, expected);
  }

  static Stream<ScreenCase> screenAccessMatrix() {
    Stream.Builder<ScreenCase> cases = Stream.builder();
    String[] unselected = {null, "", "   "};
    String[] screenKeys = {
      "config-import-export", "user-management", "audit-log", "products-table"
    };
    for (String roleId : unselected) {
      String shown = roleId == null ? "null" : "'" + roleId + "'";
      for (boolean bootstrap : new boolean[] {true, false}) {
        for (String screenKey : screenKeys) {
          // RBAC設定が空の間の例外(config-import-export)だけが、activeRoleIdにかかわらず許可される。
          boolean expected = bootstrap && screenKey.equals("config-import-export");
          cases.add(
              screen(
                  "未選択(%s) × %s × %s".formatted(shown, bootstrap ? "RBAC空" : "RBACあり", screenKey),
                  roleId,
                  false,
                  PermissionLevel.NONE,
                  bootstrap,
                  screenKey,
                  expected));
        }
      }
    }
    for (boolean bootstrap : new boolean[] {true, false}) {
      for (String screenKey : screenKeys) {
        boolean bypass = bootstrap && screenKey.equals("config-import-export");
        cases.add(
            screen(
                "実在しないロール × %s × %s".formatted(bootstrap ? "RBAC空" : "RBACあり", screenKey),
                "ghost-role",
                false,
                PermissionLevel.NONE,
                bootstrap,
                screenKey,
                bypass));
        cases.add(
            screen(
                "実在・権限NONE × %s × %s".formatted(bootstrap ? "RBAC空" : "RBACあり", screenKey),
                ROLE_ID,
                true,
                PermissionLevel.NONE,
                bootstrap,
                screenKey,
                bypass));
        cases.add(
            screen(
                "実在・権限READ × %s × %s".formatted(bootstrap ? "RBAC空" : "RBACあり", screenKey),
                ROLE_ID,
                true,
                PermissionLevel.READ,
                bootstrap,
                screenKey,
                true));
        cases.add(
            screen(
                "実在・権限FULL × %s × %s".formatted(bootstrap ? "RBAC空" : "RBACあり", screenKey),
                ROLE_ID,
                true,
                PermissionLevel.FULL,
                bootstrap,
                screenKey,
                true));
      }
    }
    return cases.build();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("screenAccessMatrix")
  void canAccessScreenFollowsTheMatrixOfActiveRoleRbacStateAndScreen(ScreenCase c) {
    lenient().when(bootstrapStateChecker.isBootstrapState()).thenReturn(c.bootstrap());
    if (c.activeRoleId() != null) {
      lenient().when(roleRepository.existsById(c.activeRoleId())).thenReturn(c.roleExists());
    }
    lenient()
        .when(permissionResolver.resolve(any(), any(), any()))
        .thenReturn(new EffectivePermission(c.level(), false, false));

    assertThat(service.canAccessScreen(c.activeRoleId(), c.screenKey())).isEqualTo(c.expected());
  }

  @ParameterizedTest(name = "activeRoleId={0} -> NONE")
  @MethodSource("unselectedRoles")
  void
      resolveEffectivePermissionReturnsNoneForAnUnselectedActiveRoleWithoutTouchingTheRepositoryOrTheResolver(
          String activeRoleId) {
    for (ScopeType scopeType : ScopeType.values()) {
      assertThat(service.resolveEffectivePermission(activeRoleId, scopeType, SCOPE_REF))
          .isEqualTo(EffectivePermission.NONE);
    }

    verify(roleRepository, never()).existsById(any());
    verify(permissionResolver, never()).resolve(any(), any(), any());
  }

  static Stream<Arguments> unselectedRoles() {
    return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of("   "));
  }

  @Test
  void anUnselectedActiveRoleIsNotCachedAsAResultSoALaterSelectedRoleIsResolvedFresh() {
    assertThat(service.resolveEffectivePermission(null, SCOPE_TYPE, SCOPE_REF))
        .isEqualTo(EffectivePermission.NONE);

    when(roleRepository.existsById(ROLE_ID)).thenReturn(true);
    when(permissionResolver.resolve(ROLE_ID, SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.FULL, true, true));

    assertThat(service.resolveEffectivePermission(ROLE_ID, SCOPE_TYPE, SCOPE_REF).level())
        .isEqualTo(PermissionLevel.FULL);
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

  // ---- roleExists(user-management(U4)のBR4.5・C10追補) ----

  @Test
  void roleExistsReturnsTrueWhenTheRoleIsPersisted() {
    when(roleRepository.existsById("role-a")).thenReturn(true);

    assertThat(service.roleExists("role-a")).isTrue();
  }

  @Test
  void roleExistsReturnsFalseWhenTheRoleDoesNotExist() {
    when(roleRepository.existsById("no-such-role")).thenReturn(false);

    assertThat(service.roleExists("no-such-role")).isFalse();
  }

  @Test
  void roleExistsReturnsFalseForNullOrBlankRoleIdsWithoutQueryingTheRepository() {
    assertThat(service.roleExists(null)).isFalse();
    assertThat(service.roleExists("")).isFalse();
    assertThat(service.roleExists("   ")).isFalse();
    verify(roleRepository, never()).existsById(any());
  }
}
