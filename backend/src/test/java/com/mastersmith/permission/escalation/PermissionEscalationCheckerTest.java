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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.permission.bootstrap.BootstrapStateChecker;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.exception.PermissionEscalationException;
import com.mastersmith.permission.resolver.PermissionResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PermissionEscalationChecker}の単体テスト(rules.md BR3.8、project.md
 * Forbidden)。PermissionResolverと
 * BootstrapStateCheckerをモックし、MeterRegistryは実インスタンス(SimpleMeterRegistry)を用いて{@code
 * permission_escalation_denied_total}カウンタ(observability-design.md)の計上を検証する。
 */
@ExtendWith(MockitoExtension.class)
class PermissionEscalationCheckerTest {

  private static final ScopeType SCOPE_TYPE = ScopeType.TABLE;
  private static final String SCOPE_REF = "table-1";

  @Mock private PermissionResolver permissionResolver;
  @Mock private BootstrapStateChecker bootstrapStateChecker;
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private PermissionEscalationChecker checker;

  private PermissionEscalationChecker checker() {
    if (checker == null) {
      checker =
          new PermissionEscalationChecker(permissionResolver, bootstrapStateChecker, meterRegistry);
    }
    return checker;
  }

  // ---- 主権限(BR3.4のFULL>READ>NONE順序による比較) ----

  @Test
  void allowsPrimaryAssignmentAtOrBelowTheActorsEffectiveLevel() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.FULL, false, false));

    assertThatCode(
            () ->
                checker()
                    .checkPrimaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, PermissionLevel.READ))
        .doesNotThrowAnyException();
  }

  @Test
  void deniesPrimaryAssignmentAboveTheActorsEffectiveLevel() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.READ, false, false));

    assertThatThrownBy(
            () ->
                checker()
                    .checkPrimaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL))
        .isInstanceOf(PermissionEscalationException.class);
  }

  @Test
  void incrementsTheEscalationDeniedCounterOnDenial() {
    // observability-design.md: permission_escalation_denied_totalはPermissionEscalationException
    // 発生時にインクリメントする。
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.READ, false, false));

    assertThatThrownBy(
        () ->
            checker()
                .checkPrimaryPermissionAssignment(
                    "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL));

    assertThat(meterRegistry.get("permission_escalation_denied_total").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void doesNotIncrementTheEscalationDeniedCounterWhenAssignmentIsAllowed() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.FULL, false, false));

    checker()
        .checkPrimaryPermissionAssignment(
            "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, PermissionLevel.READ);

    assertThat(meterRegistry.get("permission_escalation_denied_total").counter().count()).isZero();
  }

  @Test
  void deniesSelfEscalationEvenWhenActorAndTargetAreTheSameRole() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("role-1", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.READ, false, false));

    assertThatThrownBy(
            () ->
                checker()
                    .checkPrimaryPermissionAssignment(
                        "role-1", "role-1", SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL))
        .isInstanceOf(PermissionEscalationException.class);
  }

  @Test
  void allowsAssignmentEqualToTheActorsOwnCurrentLevel() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("role-1", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.READ, false, false));

    assertThatCode(
            () ->
                checker()
                    .checkPrimaryPermissionAssignment(
                        "role-1", "role-1", SCOPE_TYPE, SCOPE_REF, PermissionLevel.READ))
        .doesNotThrowAnyException();
  }

  @Test
  void skipsEscalationCheckDuringBootstrapState() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(true);

    assertThatCode(
            () ->
                checker()
                    .checkPrimaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, PermissionLevel.FULL))
        .doesNotThrowAnyException();
    verify(permissionResolver, never()).resolve(anyString(), any(), anyString());
  }

  // ---- 補助権限(createAllowed/deleteAllowedを独立に判定) ----

  @Test
  void deniesAuxiliaryCreateEscalationWhenActorCannotCreate() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.FULL, false, true));

    assertThatThrownBy(
            () ->
                checker()
                    .checkAuxiliaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, true, null))
        .isInstanceOf(PermissionEscalationException.class);
  }

  @Test
  void deniesAuxiliaryDeleteEscalationWhenActorCannotDelete() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.FULL, true, false));

    assertThatThrownBy(
            () ->
                checker()
                    .checkAuxiliaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, null, true))
        .isInstanceOf(PermissionEscalationException.class);
  }

  @Test
  void allowsAuxiliaryAssignmentWithinTheActorsEffectivePermission() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.FULL, true, true));

    assertThatCode(
            () ->
                checker()
                    .checkAuxiliaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, true, true))
        .doesNotThrowAnyException();
  }

  @Test
  void allowsAuxiliaryAssignmentWithUnspecifiedFieldsRegardlessOfActorsPermission() {
    // createAllowed/deleteAllowedがnull(指定なし)の場合、その軸は昇格判定の対象外(BR3.5の「指定なし」)。
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(false);
    lenient()
        .when(permissionResolver.resolve("actor-role", SCOPE_TYPE, SCOPE_REF))
        .thenReturn(new EffectivePermission(PermissionLevel.NONE, false, false));

    assertThatCode(
            () ->
                checker()
                    .checkAuxiliaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, null, null))
        .doesNotThrowAnyException();
  }

  @Test
  void skipsAuxiliaryEscalationCheckDuringBootstrapState() {
    when(bootstrapStateChecker.isBootstrapState()).thenReturn(true);

    assertThatCode(
            () ->
                checker()
                    .checkAuxiliaryPermissionAssignment(
                        "actor-role", "target-role", SCOPE_TYPE, SCOPE_REF, true, true))
        .doesNotThrowAnyException();
  }
}
