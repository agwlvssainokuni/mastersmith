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

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.permission.cache.PermissionCacheControl;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.dto.RbacImportSet.Auxiliary;
import com.mastersmith.permission.dto.RbacImportSet.Primary;
import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.Group;
import com.mastersmith.permission.entity.GroupRole;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.Role;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.event.PermissionImportedEvent;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.GroupRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RBAC設定の取り込みの反映(全置換。config-import-export BR9.9・BR9.10・BR9.14)。呼び出し元のトランザクションの中で(伝播は、呼び出し元の{@code
 * PermissionEngineApiImpl}の{@code MANDATORY})、 次の順に行う。段階の境界で{@code
 * flush()}する(挿入→更新→削除の順にSQLを出すHibernateの性質による、削除と同じキーの追加の一意制約の違反を避ける)。
 *
 * <ol>
 *   <li>削除: 補助権限・主権限(ファイルにないもの)→グループとロールの対応→グループ→ロール。
 *   <li>追加・更新: ロール→グループ→グループとロールの対応→主権限・補助権限。
 * </ol>
 *
 * <p>ロール・グループは、名前(自然キー)で既存の項目と照合し、内部IDを維持する。新規は採番する。権限は、(ロールID, 対象の種別,
 * 対象の識別子)で照合する。値が変わらない項目は、「更新」に数えない。 挿入は{@link EntityManager#persist}でJDBCのバッチにまとめる。1件ごとの{@code
 * assignPermission}(1件ごとの{@code invalidateAll()}・イベント発行。既知のR-04)は、用いない。
 *
 * <p>確定後の動作({@link PostCommit}): キャッシュの無効化(世代番号を進める)と、取り込み単位のサマリイベント({@link
 * PermissionImportedEvent}、BR3.11。主権限・補助権限の変更が1件以上ある場合だけ)を、 独立したトランザクションの中で発行する(例外は握りつぶす)。
 */
@Component
public class RbacImporter {

  private static final Logger LOG = LoggerFactory.getLogger(RbacImporter.class);

  private record PermissionKey(String roleId, ScopeType scopeType, String scopeRef) {}

  private record AuxiliaryValue(Boolean createAllowed, Boolean deleteAllowed) {}

  private static final class Counter {
    int added;
    int updated;
    int deleted;

    SectionCounts toCounts() {
      return new SectionCounts(added, updated, deleted);
    }

    int total() {
      return added + updated + deleted;
    }
  }

  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;
  private final GroupRoleRepository groupRoleRepository;
  private final PrimaryPermissionRepository primaryPermissionRepository;
  private final AuxiliaryPermissionRepository auxiliaryPermissionRepository;
  private final PermissionCacheControl cacheControl;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionOperations eventTransaction;

  @PersistenceContext private EntityManager entityManager;

  /** Springが用いるコンストラクター。 */
  @Autowired
  public RbacImporter(
      RoleRepository roleRepository,
      GroupRepository groupRepository,
      GroupRoleRepository groupRoleRepository,
      PrimaryPermissionRepository primaryPermissionRepository,
      AuxiliaryPermissionRepository auxiliaryPermissionRepository,
      PermissionCacheControl cacheControl,
      ApplicationEventPublisher eventPublisher,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
    this(
        roleRepository,
        groupRepository,
        groupRoleRepository,
        primaryPermissionRepository,
        auxiliaryPermissionRepository,
        cacheControl,
        eventPublisher,
        requiresNew(transactionManager));
  }

  /** 確定後のイベントの発行のトランザクションを指定するコンストラクター(テスト用)。 */
  public RbacImporter(
      RoleRepository roleRepository,
      GroupRepository groupRepository,
      GroupRoleRepository groupRoleRepository,
      PrimaryPermissionRepository primaryPermissionRepository,
      AuxiliaryPermissionRepository auxiliaryPermissionRepository,
      PermissionCacheControl cacheControl,
      ApplicationEventPublisher eventPublisher,
      TransactionOperations eventTransaction) {
    this.roleRepository = roleRepository;
    this.groupRepository = groupRepository;
    this.groupRoleRepository = groupRoleRepository;
    this.primaryPermissionRepository = primaryPermissionRepository;
    this.auxiliaryPermissionRepository = auxiliaryPermissionRepository;
    this.cacheControl = cacheControl;
    this.eventPublisher = eventPublisher;
    this.eventTransaction = eventTransaction;
  }

  /** {@link EntityManager}を差し替える(単体テスト用)。 */
  public void setEntityManager(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  public ApplyResult apply(RbacImportSet set, String actorRoleId) {
    Objects.requireNonNull(entityManager, "entityManager");

    List<Role> existingRoles = roleRepository.findAll();
    List<Group> existingGroups = groupRepository.findAll();
    List<GroupRole> existingGroupRoles = groupRoleRepository.findAll();
    List<PrimaryPermission> existingPrimary = primaryPermissionRepository.findAll();
    List<AuxiliaryPermission> existingAuxiliary = auxiliaryPermissionRepository.findAll();

    // ロール・グループの、名前 → ID(既存は維持、新規は採番)。
    Map<String, String> roleIdByName = new LinkedHashMap<>();
    Map<String, Role> existingRoleByName = new HashMap<>();
    existingRoles.forEach(r -> existingRoleByName.put(r.getName(), r));
    for (RbacImportSet.Role role : set.roles()) {
      Role existing = existingRoleByName.get(role.name());
      roleIdByName.put(role.name(), existing != null ? existing.getRoleId() : newId());
    }
    Map<String, String> groupIdByName = new LinkedHashMap<>();
    Map<String, Group> existingGroupByName = new HashMap<>();
    existingGroups.forEach(g -> existingGroupByName.put(g.getName(), g));
    for (RbacImportSet.Group group : set.groups()) {
      Group existing = existingGroupByName.get(group.name());
      groupIdByName.put(group.name(), existing != null ? existing.getGroupId() : newId());
    }

    // 入力側の、グループとロールの対応(グループID → ロールIDの集合)、権限(キー → 値)。
    Map<String, Set<String>> desiredRoleIdsByGroupId = new HashMap<>();
    for (RbacImportSet.Group group : set.groups()) {
      Set<String> roleIds = new HashSet<>();
      for (String roleName : group.roleNames()) {
        roleIds.add(requireId(roleIdByName, roleName, "role"));
      }
      desiredRoleIdsByGroupId.put(groupIdByName.get(group.name()), roleIds);
    }
    Map<PermissionKey, PermissionLevel> desiredPrimary = new LinkedHashMap<>();
    for (Primary entry : set.primaryPermissions()) {
      desiredPrimary.put(
          new PermissionKey(
              requireId(roleIdByName, entry.roleName(), "role"),
              entry.scope().scopeType(),
              requireScopeRef(entry.scope())),
          entry.level());
    }
    Map<PermissionKey, AuxiliaryValue> desiredAuxiliary = new LinkedHashMap<>();
    for (Auxiliary entry : set.auxiliaryPermissions()) {
      desiredAuxiliary.put(
          new PermissionKey(
              requireId(roleIdByName, entry.roleName(), "role"),
              entry.scope().scopeType(),
              requireScopeRef(entry.scope())),
          new AuxiliaryValue(entry.createAllowed(), entry.deleteAllowed()));
    }

    Counter roles = new Counter();
    Counter groups = new Counter();
    Counter primary = new Counter();
    Counter auxiliary = new Counter();

    // (1) 削除: 補助権限・主権限 → グループとロールの対応 → グループ → ロール。
    for (AuxiliaryPermission row : existingAuxiliary) {
      if (!desiredAuxiliary.containsKey(
          keyOf(row.getRoleId(), row.getScopeType(), row.getScopeRef()))) {
        entityManager.remove(row);
        auxiliary.deleted++;
      }
    }
    for (PrimaryPermission row : existingPrimary) {
      if (!desiredPrimary.containsKey(
          keyOf(row.getRoleId(), row.getScopeType(), row.getScopeRef()))) {
        entityManager.remove(row);
        primary.deleted++;
      }
    }
    Map<String, Set<String>> existingRoleIdsByGroupId = new HashMap<>();
    for (GroupRole row : existingGroupRoles) {
      existingRoleIdsByGroupId
          .computeIfAbsent(row.getGroupId(), k -> new HashSet<>())
          .add(row.getRoleId());
      Set<String> desired = desiredRoleIdsByGroupId.get(row.getGroupId());
      if (desired == null || !desired.contains(row.getRoleId())) {
        entityManager.remove(row);
      }
    }
    for (Group group : existingGroups) {
      if (!groupIdByName.containsKey(group.getName())) {
        entityManager.remove(group);
        groups.deleted++;
      }
    }
    for (Role role : existingRoles) {
      if (!roleIdByName.containsKey(role.getName())) {
        entityManager.remove(role);
        roles.deleted++;
      }
    }
    entityManager.flush();

    // (2) 追加・更新: ロール → グループ → グループとロールの対応。
    for (Map.Entry<String, String> entry : roleIdByName.entrySet()) {
      if (!existingRoleByName.containsKey(entry.getKey())) {
        entityManager.persist(new Role(entry.getValue(), entry.getKey()));
        roles.added++;
      }
    }
    for (Map.Entry<String, String> entry : groupIdByName.entrySet()) {
      if (!existingGroupByName.containsKey(entry.getKey())) {
        entityManager.persist(new Group(entry.getValue(), entry.getKey()));
        groups.added++;
      }
    }
    for (Map.Entry<String, Set<String>> entry : desiredRoleIdsByGroupId.entrySet()) {
      Set<String> before = existingRoleIdsByGroupId.getOrDefault(entry.getKey(), Set.of());
      boolean groupWasThere =
          existingGroupByName.values().stream()
              .anyMatch(g -> g.getGroupId().equals(entry.getKey()));
      for (String roleId : entry.getValue()) {
        if (!before.contains(roleId)) {
          entityManager.persist(new GroupRole(entry.getKey(), roleId));
        }
      }
      // 既存のグループで、対応するロールの集合が変わったものは、「更新」に数える。
      if (groupWasThere && !before.equals(entry.getValue())) {
        groups.updated++;
      }
    }
    entityManager.flush();

    // (3) 追加・更新: 主権限・補助権限。
    Map<PermissionKey, PrimaryPermission> primaryByKey = new HashMap<>();
    existingPrimary.forEach(
        p -> primaryByKey.put(keyOf(p.getRoleId(), p.getScopeType(), p.getScopeRef()), p));
    for (Map.Entry<PermissionKey, PermissionLevel> entry : desiredPrimary.entrySet()) {
      PrimaryPermission existing = primaryByKey.get(entry.getKey());
      if (existing == null) {
        PermissionKey key = entry.getKey();
        entityManager.persist(
            new PrimaryPermission(key.roleId(), key.scopeType(), key.scopeRef(), entry.getValue()));
        primary.added++;
      } else if (existing.getLevel() != entry.getValue()) {
        existing.setLevel(entry.getValue());
        primary.updated++;
      }
    }
    Map<PermissionKey, AuxiliaryPermission> auxiliaryByKey = new HashMap<>();
    existingAuxiliary.forEach(
        a -> auxiliaryByKey.put(keyOf(a.getRoleId(), a.getScopeType(), a.getScopeRef()), a));
    for (Map.Entry<PermissionKey, AuxiliaryValue> entry : desiredAuxiliary.entrySet()) {
      AuxiliaryPermission existing = auxiliaryByKey.get(entry.getKey());
      AuxiliaryValue value = entry.getValue();
      if (existing == null) {
        PermissionKey key = entry.getKey();
        entityManager.persist(
            new AuxiliaryPermission(
                key.roleId(),
                key.scopeType(),
                key.scopeRef(),
                value.createAllowed(),
                value.deleteAllowed()));
        auxiliary.added++;
      } else if (!Objects.equals(existing.getCreateAllowed(), value.createAllowed())
          || !Objects.equals(existing.getDeleteAllowed(), value.deleteAllowed())) {
        existing.setCreateAllowed(value.createAllowed());
        existing.setDeleteAllowed(value.deleteAllowed());
        auxiliary.updated++;
      }
    }
    entityManager.flush();

    Map<String, SectionCounts> sections = new LinkedHashMap<>();
    sections.put(ImportSections.ROLES, roles.toCounts());
    sections.put(ImportSections.GROUPS, groups.toCounts());
    sections.put(ImportSections.PRIMARY_PERMISSIONS, primary.toCounts());
    sections.put(ImportSections.AUXILIARY_PERMISSIONS, auxiliary.toCounts());
    int permissionChanges = primary.total() + auxiliary.total();
    LOG.info(
        "event=permission.import.applied primaryChanges={} auxiliaryChanges={}",
        primary.total(),
        auxiliary.total());
    return new ApplyResult(
        sections,
        new PostCommit(cacheControl::invalidate, summaryPublisher(actorRoleId, permissionChanges)));
  }

  /** 取り込み単位のサマリイベントを、独立した1つのトランザクションの中で発行する。変更がなければ発行しない。例外は握りつぶす。 */
  private Runnable summaryPublisher(String actorRoleId, int changeCount) {
    return () -> {
      if (changeCount <= 0) {
        return;
      }
      try {
        eventTransaction.executeWithoutResult(
            status ->
                eventPublisher.publishEvent(PermissionImportedEvent.of(actorRoleId, changeCount)));
      } catch (RuntimeException e) {
        LOG.error(
            "event=config.import.event-publish-failed unit=permission-engine cause={}",
            e.getClass().getName());
      }
    };
  }

  private static PermissionKey keyOf(String roleId, ScopeType scopeType, String scopeRef) {
    return new PermissionKey(roleId, scopeType, scopeRef);
  }

  private static String newId() {
    return UUID.randomUUID().toString();
  }

  private static String requireId(Map<String, String> idByName, String name, String what) {
    String id = idByName.get(name);
    if (id == null) {
      throw new IllegalStateException("unresolved " + what + " reference in the import set");
    }
    return id;
  }

  private static String requireScopeRef(RbacImportSet.Scope scope) {
    String ref = scope.scopeRef();
    if (ref == null || ref.isBlank()) {
      throw new IllegalStateException("unresolved scope reference in the import set");
    }
    return ref;
  }
}
