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

package com.mastersmith.permission.escalation;

import com.mastersmith.permission.bootstrap.BootstrapStateChecker;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.exception.PermissionEscalationException;
import com.mastersmith.permission.resolver.PermissionResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 権限昇格の防止(rules.md BR3.8、project.md Forbidden「権限の昇格(自分自身への昇格を含む)を...明示的な操作を経ずに許可しない」)。
 *
 * <p>割り当てようとする新しい権限が、操作者(actorRoleId)が同一スコープについて現に持つ実効権限を上回る場合、{@link
 * PermissionEscalationException}をスローする。ブートストラップ状態(rules.md BR3.13、PrimaryPermission行数=0)では
 * このチェックを適用しない。拒否時は{@code
 * permission_escalation_denied_total}カウンタをインクリメントし(observability-design.md)、
 * 対象ロールID・スコープ種別を構造化ログへ記録する。
 */
@Component
public class PermissionEscalationChecker {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionEscalationChecker.class);

  private final PermissionResolver permissionResolver;
  private final BootstrapStateChecker bootstrapStateChecker;
  private final Counter escalationDeniedCounter;

  public PermissionEscalationChecker(
      PermissionResolver permissionResolver,
      BootstrapStateChecker bootstrapStateChecker,
      MeterRegistry meterRegistry) {
    this.permissionResolver = permissionResolver;
    this.bootstrapStateChecker = bootstrapStateChecker;
    this.escalationDeniedCounter =
        Counter.builder("permission_escalation_denied_total")
            .description(
                "Number of assignment calls denied for escalating beyond the actor's own effective permission (BR3.8)")
            .register(meterRegistry);
  }

  /**
   * 主権限の割当が権限昇格に当たらないか検証する(BR3.8、BR3.4のFULL&gt;READ&gt;NONE順序で比較)。
   *
   * @param actorRoleId 割当操作を実行している操作者のactiveRoleId
   * @param targetRoleId 割当先のロール(自分自身への割当を含みうる)
   * @param newLevel 割り当てようとする新しい主権限
   * @throws PermissionEscalationException 操作者の実効権限を上回る割当が試みられた場合
   */
  public void checkPrimaryPermissionAssignment(
      String actorRoleId,
      String targetRoleId,
      ScopeType scopeType,
      String scopeRef,
      PermissionLevel newLevel) {
    if (bootstrapStateChecker.isBootstrapState()) {
      return;
    }
    PermissionLevel actorLevel =
        permissionResolver.resolve(actorRoleId, scopeType, scopeRef).level();
    if (newLevel.rank() > actorLevel.rank()) {
      denyEscalation(targetRoleId, scopeType, scopeRef, newLevel.name());
    }
  }

  /**
   * 補助権限の割当が権限昇格に当たらないか検証する(BR3.8)。createAllowed/deleteAllowedは独立に判定する。
   *
   * @param actorRoleId 割当操作を実行している操作者のactiveRoleId
   * @param targetRoleId 割当先のロール(自分自身への割当を含みうる)
   * @param createAllowed 割り当てようとするcreateAllowed(nullは「指定なし」で昇格判定の対象外)
   * @param deleteAllowed 割り当てようとするdeleteAllowed(nullは「指定なし」で昇格判定の対象外)
   * @throws PermissionEscalationException 操作者の実効権限を上回る割当が試みられた場合
   */
  public void checkAuxiliaryPermissionAssignment(
      String actorRoleId,
      String targetRoleId,
      ScopeType scopeType,
      String scopeRef,
      Boolean createAllowed,
      Boolean deleteAllowed) {
    if (bootstrapStateChecker.isBootstrapState()) {
      return;
    }
    EffectivePermission actorEffective =
        permissionResolver.resolve(actorRoleId, scopeType, scopeRef);
    if (Boolean.TRUE.equals(createAllowed) && !actorEffective.canCreate()) {
      denyEscalation(targetRoleId, scopeType, scopeRef, "createAllowed=true");
    }
    if (Boolean.TRUE.equals(deleteAllowed) && !actorEffective.canDelete()) {
      denyEscalation(targetRoleId, scopeType, scopeRef, "deleteAllowed=true");
    }
  }

  private void denyEscalation(
      String targetRoleId, ScopeType scopeType, String scopeRef, String requestedLevel) {
    escalationDeniedCounter.increment();
    LOG.warn(
        "Permission escalation denied: targetRoleId={}, scopeType={}, scopeRef={}, requestedLevel={}",
        targetRoleId,
        scopeType,
        scopeRef,
        requestedLevel);
    throw new PermissionEscalationException(targetRoleId, scopeType, scopeRef);
  }
}
