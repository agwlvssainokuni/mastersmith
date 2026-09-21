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
import static org.assertj.core.api.Assertions.tuple;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.permission.cache.PermissionCacheControl;
import com.mastersmith.permission.cache.PermissionCacheKey;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.dto.RbacImportSet.Auxiliary;
import com.mastersmith.permission.dto.RbacImportSet.Group;
import com.mastersmith.permission.dto.RbacImportSet.Primary;
import com.mastersmith.permission.dto.RbacImportSet.Role;
import com.mastersmith.permission.dto.RbacImportSet.Scope;
import com.mastersmith.permission.entity.GroupMembership;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.event.PermissionImportedEvent;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.GroupMembershipRepository;
import com.mastersmith.permission.repository.GroupRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * {@link RbacImporter}(RBAC設定の取り込みの反映)の、実際の組込みH2・実際のJPAでのテスト:
 * 全置換(削除を含む)・自然キー(名前)での内部IDの維持・件数(値が変わらない項目は数えない)・
 * 削除と追加の順序(一意制約)・確定後の動作(キャッシュの世代・取り込み単位のサマリイベント)。業務固有の名前は、連番から機械的に生成する(BR9.20)。
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RbacApplyTest {

  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GroupRoleRepository groupRoleRepository;
  @Autowired private GroupMembershipRepository membershipRepository;
  @Autowired private PrimaryPermissionRepository primaryRepository;
  @Autowired private AuxiliaryPermissionRepository auxiliaryRepository;
  @Autowired private EntityManager entityManager;

  private final List<Object> published = new ArrayList<>();
  private Cache<PermissionCacheKey, EffectivePermission> cache;
  private PermissionCacheControl control;
  private RbacImporter importer;

  @BeforeEach
  void setUp() {
    cache = Caffeine.newBuilder().build();
    control = new PermissionCacheControl(cache);
    importer = importer(published::add);
  }

  private RbacImporter importer(org.springframework.context.ApplicationEventPublisher publisher) {
    RbacImporter created =
        new RbacImporter(
            roleRepository,
            groupRepository,
            groupRoleRepository,
            primaryRepository,
            auxiliaryRepository,
            control,
            publisher,
            TransactionOperations.withoutTransaction());
    created.setEntityManager(entityManager);
    return created;
  }

  private static Scope schema(String name) {
    return new Scope(ScopeType.SCHEMA, name, null, null);
  }

  private static Scope table(String schema, String tableId) {
    return new Scope(ScopeType.TABLE, schema, tableId, null);
  }

  private static RbacImportSet set(
      List<Role> roles, List<Group> groups, List<Primary> primary, List<Auxiliary> auxiliary) {
    return new RbacImportSet(roles, groups, primary, auxiliary);
  }

  private static Primary primary(String role, Scope scope, PermissionLevel level) {
    return new Primary(role, scope, level);
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private SectionCounts counts(ApplyResult result, String section) {
    return result.sections().get(section);
  }

  @Test
  void addsEverythingWhenTheDatabaseIsEmpty() {
    RbacImportSet set =
        set(
            List.of(new Role("role_0001"), new Role("role_0002")),
            List.of(new Group("group_0001", List.of("role_0001", "role_0002"))),
            List.of(
                primary("role_0001", schema("schema_0001"), PermissionLevel.FULL),
                primary("role_0002", table("schema_0001", "table-id-1"), PermissionLevel.READ)),
            List.of(new Auxiliary("role_0001", schema("schema_0001"), true, false)));

    ApplyResult result = importer.apply(set, "actor-role");
    flushAndClear();

    assertThat(roleRepository.count()).isEqualTo(2);
    assertThat(groupRepository.count()).isEqualTo(1);
    assertThat(groupRoleRepository.count()).isEqualTo(2);
    assertThat(primaryRepository.count()).isEqualTo(2);
    assertThat(auxiliaryRepository.count()).isEqualTo(1);
    assertThat(counts(result, ImportSections.ROLES)).isEqualTo(new SectionCounts(2, 0, 0));
    assertThat(counts(result, ImportSections.GROUPS)).isEqualTo(new SectionCounts(1, 0, 0));
    assertThat(counts(result, ImportSections.PRIMARY_PERMISSIONS))
        .isEqualTo(new SectionCounts(2, 0, 0));
    assertThat(counts(result, ImportSections.AUXILIARY_PERMISSIONS))
        .isEqualTo(new SectionCounts(1, 0, 0));
  }

  @Test
  void replacesEverythingKeepingIdsByNameAndDeletingWhatIsNotInTheSet() {
    importer.apply(
        set(
            List.of(new Role("role_keep"), new Role("role_gone")),
            List.of(
                new Group("group_keep", List.of("role_keep", "role_gone")),
                new Group("group_gone", List.of())),
            List.of(
                primary("role_keep", schema("schema_0001"), PermissionLevel.READ),
                primary("role_gone", schema("schema_0001"), PermissionLevel.FULL)),
            List.of(new Auxiliary("role_gone", schema("schema_0001"), true, true))),
        "actor");
    flushAndClear();
    String keptRoleId = roleRepository.findByName("role_keep").orElseThrow().getRoleId();
    String keptGroupId = groupRepository.findByName("group_keep").orElseThrow().getGroupId();
    published.clear();

    ApplyResult result =
        importer.apply(
            set(
                List.of(new Role("role_keep"), new Role("role_new")),
                List.of(
                    new Group("group_keep", List.of("role_new")),
                    new Group("group_new", List.of("role_keep"))),
                List.of(
                    primary("role_keep", schema("schema_0001"), PermissionLevel.FULL),
                    primary("role_new", schema("schema_0002"), PermissionLevel.NONE)),
                List.of()),
            "actor");
    flushAndClear();

    // 名前が一致するロール・グループは、内部IDを維持する。ファイルにないものは、権限ごと削除される。
    assertThat(roleRepository.findByName("role_keep").orElseThrow().getRoleId())
        .isEqualTo(keptRoleId);
    assertThat(groupRepository.findByName("group_keep").orElseThrow().getGroupId())
        .isEqualTo(keptGroupId);
    assertThat(roleRepository.findByName("role_gone")).isEmpty();
    assertThat(groupRepository.findByName("group_gone")).isEmpty();
    assertThat(roleRepository.count()).isEqualTo(2);
    assertThat(auxiliaryRepository.count()).isZero();
    assertThat(primaryRepository.findAll())
        .extracting(PrimaryPermission::getScopeRef, PrimaryPermission::getLevel)
        .containsExactlyInAnyOrder(
            tuple("schema_0001", PermissionLevel.FULL), tuple("schema_0002", PermissionLevel.NONE));
    Map<String, List<String>> roleNamesByGroup =
        groupRoleRepository.findAll().stream()
            .collect(
                Collectors.groupingBy(
                    gr -> groupRepository.findById(gr.getGroupId()).orElseThrow().getName(),
                    Collectors.mapping(
                        gr -> roleRepository.findById(gr.getRoleId()).orElseThrow().getName(),
                        Collectors.toList())));
    assertThat(roleNamesByGroup.get("group_keep")).containsExactly("role_new");
    assertThat(roleNamesByGroup.get("group_new")).containsExactly("role_keep");
    // 件数(値が変わらない項目は、数えない)。
    assertThat(counts(result, ImportSections.ROLES)).isEqualTo(new SectionCounts(1, 0, 1));
    assertThat(counts(result, ImportSections.GROUPS)).isEqualTo(new SectionCounts(1, 1, 1));
    assertThat(counts(result, ImportSections.PRIMARY_PERMISSIONS))
        .isEqualTo(new SectionCounts(1, 1, 1));
    assertThat(counts(result, ImportSections.AUXILIARY_PERMISSIONS))
        .isEqualTo(new SectionCounts(0, 0, 1));
  }

  @Test
  void anIdenticalSetChangesNothingAndCountsNothing() {
    RbacImportSet set =
        set(
            List.of(new Role("role_0001")),
            List.of(new Group("group_0001", List.of("role_0001"))),
            List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.FULL)),
            List.of(new Auxiliary("role_0001", schema("schema_0001"), true, null)));
    importer.apply(set, "actor");
    flushAndClear();

    ApplyResult again = importer.apply(set, "actor");

    for (String section : ImportSections.ALL) {
      if (again.sections().containsKey(section)) {
        assertThat(counts(again, section)).isEqualTo(SectionCounts.ZERO);
      }
    }
  }

  @Test
  void anAuxiliaryChangeIsCountedAsAnUpdate() {
    RbacImportSet before =
        set(
            List.of(new Role("role_0001")),
            List.of(),
            List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.READ)),
            List.of(new Auxiliary("role_0001", schema("schema_0001"), true, null)));
    importer.apply(before, "actor");
    flushAndClear();

    ApplyResult result =
        importer.apply(
            set(
                before.roles(),
                before.groups(),
                before.primaryPermissions(),
                List.of(new Auxiliary("role_0001", schema("schema_0001"), true, true))),
            "actor");
    flushAndClear();

    assertThat(counts(result, ImportSections.AUXILIARY_PERMISSIONS))
        .isEqualTo(new SectionCounts(0, 1, 0));
    assertThat(auxiliaryRepository.findAll().get(0).getDeleteAllowed()).isTrue();
  }

  @Test
  void userGroupMembershipsAreUntouched() {
    importer.apply(
        set(
            List.of(new Role("role_0001")),
            List.of(new Group("group_0001", List.of("role_0001"))),
            List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.READ)),
            List.of()),
        "actor");
    flushAndClear();
    String groupId = groupRepository.findByName("group_0001").orElseThrow().getGroupId();
    membershipRepository.save(new GroupMembership(groupId, "user-1"));
    flushAndClear();

    importer.apply(
        set(
            List.of(new Role("role_0001")),
            List.of(new Group("group_0001", List.of())),
            List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.READ)),
            List.of()),
        "actor");
    flushAndClear();

    assertThat(membershipRepository.count()).isEqualTo(1);
  }

  @Test
  void postCommitInvalidatesTheCacheByAdvancingTheGeneration() {
    cache.put(
        new PermissionCacheKey("r", ScopeType.SCHEMA, "s", control.generation()),
        EffectivePermission.NONE);
    ApplyResult result =
        importer.apply(
            set(
                List.of(new Role("role_0001")),
                List.of(),
                List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.READ)),
                List.of()),
            "actor");
    long generation = control.generation();

    // 反映そのものは、キャッシュに触れない(確定後の動作として、返すだけ)。
    assertThat(cache.asMap()).hasSize(1);

    result.postCommit().invalidateCaches().run();

    assertThat(control.generation()).isEqualTo(generation + 1);
    assertThat(cache.asMap()).isEmpty();
  }

  @Test
  void postCommitPublishesOneSummaryEventWithTheActorAndTheChangeCountOfPermissionsOnly() {
    ApplyResult result =
        importer.apply(
            set(
                List.of(new Role("role_0001")),
                List.of(),
                List.of(
                    primary("role_0001", schema("schema_0001"), PermissionLevel.READ),
                    primary("role_0001", schema("schema_0002"), PermissionLevel.FULL)),
                List.of(new Auxiliary("role_0001", schema("schema_0001"), true, null))),
            "actor-role");
    assertThat(published).isEmpty();

    result.postCommit().publishEvents().run();

    assertThat(published).hasSize(1);
    PermissionImportedEvent event = (PermissionImportedEvent) published.get(0);
    assertThat(event.actor()).isEqualTo("actor-role");
    // 主権限2件+補助権限1件(ロール・グループの変更は、含めない)。
    assertThat(event.changeCount()).isEqualTo(3);
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void noSummaryEventIsPublishedWhenNoPermissionChanged() {
    RbacImportSet set =
        set(
            List.of(new Role("role_0001")),
            List.of(),
            List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.READ)),
            List.of());
    importer.apply(set, "actor");
    flushAndClear();
    published.clear();

    importer.apply(set, "actor").postCommit().publishEvents().run();

    assertThat(published).isEmpty();
  }

  @Test
  void aFailureWhilePublishingTheSummaryEventIsSwallowed() {
    RbacImporter failing =
        importer(
            event -> {
              throw new IllegalStateException("listener down");
            });
    ApplyResult result =
        failing.apply(
            set(
                List.of(new Role("role_0001")),
                List.of(),
                List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.READ)),
                List.of()),
            "actor");

    result.postCommit().publishEvents().run(); // 例外が、伝わらない。
  }

  @Test
  void unresolvedRoleOrScopeReferencesAreInternalErrors() {
    assertThatThrownBy(
            () ->
                importer.apply(
                    set(
                        List.of(),
                        List.of(),
                        List.of(
                            primary("no_such_role", schema("schema_0001"), PermissionLevel.READ)),
                        List.of()),
                    "actor"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                importer.apply(
                    set(
                        List.of(new Role("role_0001")),
                        List.of(),
                        List.of(
                            primary("role_0001", table("schema_0001", null), PermissionLevel.READ)),
                        List.of()),
                    "actor"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anEmptySetDeletesEverything() {
    importer.apply(
        set(
            List.of(new Role("role_0001")),
            List.of(new Group("group_0001", List.of("role_0001"))),
            List.of(primary("role_0001", schema("schema_0001"), PermissionLevel.READ)),
            List.of()),
        "actor");
    flushAndClear();

    importer.apply(set(List.of(), List.of(), List.of(), List.of()), "actor");
    flushAndClear();

    assertThat(roleRepository.count()).isZero();
    assertThat(groupRepository.count()).isZero();
    assertThat(groupRoleRepository.count()).isZero();
    assertThat(primaryRepository.count()).isZero();
  }
}
