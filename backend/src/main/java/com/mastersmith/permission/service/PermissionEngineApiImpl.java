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

import com.github.benmanes.caffeine.cache.Cache;
import com.mastersmith.permission.PermissionEngineApi;
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
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.GroupMembershipRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import com.mastersmith.permission.resolver.PermissionResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PermissionEngineApi}(C10契約)の実装。実効権限の解決は{@link
 * PermissionResolver}に委譲し、キャッシュ(NFR1.1/NFR1.2)・ 昇格防止({@link
 * PermissionEscalationChecker}、BR3.8)・ブートストラップ例外({@link BootstrapStateChecker}、BR3.13)を
 * 組み合わせて公開APIとして提供する。
 */
@Service
public class PermissionEngineApiImpl implements PermissionEngineApi {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionEngineApiImpl.class);

  /**
   * rules.md BR3.15: 管理系画面の予約screenKeyと、対応する予約スキーマ名(PrimaryPermissionのscopeType=SCHEMAのscopeRef)。
   */
  private static final Map<String, String> RESERVED_SCREEN_SCHEMAS =
      Map.of(
          "user-management", "__system__:user-management",
          "audit-log", "__system__:audit-log",
          "config-import-export", "__system__:config-import-export");

  /** BR3.13(a): ブートストラップ状態に限り無条件にtrueを返す予約screenKey。 */
  private static final String CONFIG_IMPORT_EXPORT_SCREEN_KEY = "config-import-export";

  /** security-design.md「入力検証・インジェクション対策」: scopeRefの文字列長上限。 */
  private static final int MAX_SCOPE_REF_LENGTH = 255;

  private final RoleRepository roleRepository;
  private final PrimaryPermissionRepository primaryPermissionRepository;
  private final AuxiliaryPermissionRepository auxiliaryPermissionRepository;
  private final GroupMembershipRepository groupMembershipRepository;
  private final GroupRoleRepository groupRoleRepository;
  private final PermissionResolver permissionResolver;
  private final PermissionEscalationChecker escalationChecker;
  private final BootstrapStateChecker bootstrapStateChecker;
  private final Cache<PermissionCacheKey, EffectivePermission> permissionCache;
  private final Timer permissionCheckDurationTimer;

  public PermissionEngineApiImpl(
      RoleRepository roleRepository,
      PrimaryPermissionRepository primaryPermissionRepository,
      AuxiliaryPermissionRepository auxiliaryPermissionRepository,
      GroupMembershipRepository groupMembershipRepository,
      GroupRoleRepository groupRoleRepository,
      PermissionResolver permissionResolver,
      PermissionEscalationChecker escalationChecker,
      BootstrapStateChecker bootstrapStateChecker,
      Cache<PermissionCacheKey, EffectivePermission> permissionCache,
      MeterRegistry meterRegistry) {
    this.roleRepository = roleRepository;
    this.primaryPermissionRepository = primaryPermissionRepository;
    this.auxiliaryPermissionRepository = auxiliaryPermissionRepository;
    this.groupMembershipRepository = groupMembershipRepository;
    this.groupRoleRepository = groupRoleRepository;
    this.permissionResolver = permissionResolver;
    this.escalationChecker = escalationChecker;
    this.bootstrapStateChecker = bootstrapStateChecker;
    this.permissionCache = permissionCache;
    this.permissionCheckDurationTimer =
        Timer.builder("permission_check_duration_seconds")
            .description(
                "resolveEffectivePermission call duration (observability-design.md, NFR1.1 50ms"
                    + " budget)")
            .register(meterRegistry);
  }

  @Override
  public EffectivePermission resolveEffectivePermission(
      String activeRoleId, ScopeType scopeType, String scopeRef) {
    return permissionCheckDurationTimer.record(
        () -> resolveEffectivePermissionUntimed(activeRoleId, scopeType, scopeRef));
  }

  private EffectivePermission resolveEffectivePermissionUntimed(
      String activeRoleId, ScopeType scopeType, String scopeRef) {
    validateScopeRef(scopeRef);
    // security-design.md「多層防御」: activeRoleIdが実在する(削除されていない)Roleであるかどうかの
    // 存在検証のみを入口で行う。存在しなければBR3.6と同じ安全側デフォルトを返す。
    if (!roleRepository.existsById(activeRoleId)) {
      return EffectivePermission.NONE;
    }
    PermissionCacheKey key = new PermissionCacheKey(activeRoleId, scopeType, scopeRef);
    return permissionCache.get(
        key, k -> permissionResolver.resolve(k.activeRoleId(), k.scopeType(), k.scopeRef()));
  }

  @Override
  public boolean canAccessScreen(String activeRoleId, String screenKey) {
    // BR3.13(a): ブートストラップ状態に限り、初期管理者が最初のRBAC設定インポート画面へ
    // 到達できるよう無条件にtrueを返す。
    if (CONFIG_IMPORT_EXPORT_SCREEN_KEY.equals(screenKey)
        && bootstrapStateChecker.isBootstrapState()) {
      return true;
    }
    String reservedSchema = RESERVED_SCREEN_SCHEMAS.get(screenKey);
    EffectivePermission effective =
        reservedSchema != null
            ? resolveEffectivePermission(activeRoleId, ScopeType.SCHEMA, reservedSchema)
            : resolveEffectivePermission(activeRoleId, ScopeType.TABLE, screenKey);
    return effective.level() != PermissionLevel.NONE;
  }

  @Override
  @Transactional
  public void assignPermission(
      String actorRoleId,
      String targetRoleId,
      ScopeType scopeType,
      String scopeRef,
      PermissionLevel level) {
    validateScopeRef(scopeRef);
    escalationChecker.checkPrimaryPermissionAssignment(
        actorRoleId, targetRoleId, scopeType, scopeRef, level);

    Optional<PrimaryPermission> existingRow =
        primaryPermissionRepository.findByRoleIdAndScopeTypeAndScopeRef(
            targetRoleId, scopeType, scopeRef);
    PermissionLevel previousLevel = existingRow.map(PrimaryPermission::getLevel).orElse(null);
    existingRow.ifPresentOrElse(
        existing -> {
          existing.setLevel(level);
          primaryPermissionRepository.save(existing);
        },
        () ->
            primaryPermissionRepository.save(
                new PrimaryPermission(targetRoleId, scopeType, scopeRef, level)));

    // observability-design.md「ログ実装」: assignPermission成功時、変更内容(ロールID・スコープ種別・
    // スコープ参照・変更前後のレベル)を記録する。
    LOG.info(
        "Primary permission assigned: targetRoleId={}, scopeType={}, scopeRef={}, previousLevel={},"
            + " newLevel={}, actor={}",
        targetRoleId,
        scopeType,
        scopeRef,
        previousLevel,
        level,
        actorRoleId);
    afterAssignment();
  }

  @Override
  @Transactional
  public void assignAuxiliaryPermission(
      String actorRoleId,
      String targetRoleId,
      ScopeType scopeType,
      String scopeRef,
      Boolean createAllowed,
      Boolean deleteAllowed) {
    validateScopeRef(scopeRef);
    validateAuxiliaryScopeType(scopeType);
    escalationChecker.checkAuxiliaryPermissionAssignment(
        actorRoleId, targetRoleId, scopeType, scopeRef, createAllowed, deleteAllowed);

    auxiliaryPermissionRepository
        .findByRoleIdAndScopeTypeAndScopeRef(targetRoleId, scopeType, scopeRef)
        .ifPresentOrElse(
            existing -> {
              existing.setCreateAllowed(createAllowed);
              existing.setDeleteAllowed(deleteAllowed);
              auxiliaryPermissionRepository.save(existing);
            },
            () ->
                auxiliaryPermissionRepository.save(
                    new AuxiliaryPermission(
                        targetRoleId, scopeType, scopeRef, createAllowed, deleteAllowed)));

    LOG.info(
        "Auxiliary permission assigned: targetRoleId={}, scopeType={}, scopeRef={},"
            + " createAllowed={}, deleteAllowed={}, actor={}",
        targetRoleId,
        scopeType,
        scopeRef,
        createAllowed,
        deleteAllowed,
        actorRoleId);
    afterAssignment();
  }

  /**
   * 割当成功後の共通処理: キャッシュ無効化(performance-design.md「無効化」レビュー指摘R-01対応、部分無効化ではなく全体無効化)のみを行う。
   *
   * <p>rules.md BR3.11の{@code PermissionChanged}サマリイベント(実行者・変更件数・日時、個々の変更値は含めない)は
   * config-import-exportの1回のインポート実行単位で発行される責務であり、本メソッド(個々の{@code assignPermission}/{@code
   * assignAuxiliaryPermission}呼び出し単位)では発行しない(アーキテクチャレビュー iteration 1, NOT-READY,
   * R-01対応。以前の実装は本メソッド単位でイベントを発行しておりBR3.11の粒度に反していた)。 イベント発行はconfig-import-export自身のCode
   * Generation(未着手)で実装される(functional-spec.md「Assumptions &amp; Open Questions」参照)。
   */
  private void afterAssignment() {
    permissionCache.invalidateAll();
  }

  @Override
  public List<String> getGroupDerivedRoleIds(String userId) {
    List<String> groupIds =
        groupMembershipRepository.findByIdUserId(userId).stream()
            .map(GroupMembership::getGroupId)
            .toList();
    if (groupIds.isEmpty()) {
      return List.of();
    }
    return groupRoleRepository.findByIdGroupIdIn(groupIds).stream()
        .map(GroupRole::getRoleId)
        .distinct()
        .toList();
  }

  @Override
  public boolean roleExists(String roleId) {
    return roleId != null && !roleId.isBlank() && roleRepository.existsById(roleId);
  }

  private void validateScopeRef(String scopeRef) {
    if (scopeRef == null || scopeRef.isBlank()) {
      throw new IllegalArgumentException("scopeRef must not be blank");
    }
    if (scopeRef.length() > MAX_SCOPE_REF_LENGTH) {
      throw new IllegalArgumentException(
          "scopeRef exceeds max length of %d characters".formatted(MAX_SCOPE_REF_LENGTH));
    }
  }

  /**
   * entities.md AuxiliaryPermission.scopeType.allowed_values: 補助権限はSCHEMA/TABLEのみを対象とし、
   * COLUMNは対象外である。fail fastで拒否する(アーキテクチャレビュー iteration 1, NOT-READY, R-05対応)。
   */
  private void validateAuxiliaryScopeType(ScopeType scopeType) {
    if (scopeType == ScopeType.COLUMN) {
      throw new IllegalArgumentException(
          "AuxiliaryPermission does not support ScopeType.COLUMN (entities.md"
              + " AuxiliaryPermission.scopeType.allowed_values: SCHEMA, TABLE only)");
    }
  }
}
