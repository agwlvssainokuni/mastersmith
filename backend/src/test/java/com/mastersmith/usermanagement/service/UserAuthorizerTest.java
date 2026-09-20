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

package com.mastersmith.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.UserAccessDeniedException;
import com.mastersmith.usermanagement.security.Operator;
import com.mastersmith.usermanagement.security.UserAuthorizer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link UserAuthorizer}の認可拒否専用テスト(team.md必須テスト種別(c)、rules.md BR4.10・BR4.8):
 * 操作者の未解決(401)・権限なし(403)・許可の組み合わせを、
 * テーブル駆動で確認する。権限判定そのものはpermission-engine(C10)へ委譲し、ここではモックで、許可・拒否を独立に検証する。
 */
class UserAuthorizerTest {

  private final PermissionEngineApi permissionEngineApi = mock(PermissionEngineApi.class);
  private final UserAuthorizer authorizer = new UserAuthorizer(permissionEngineApi);

  static Stream<Arguments> unresolvedOperators() {
    return Stream.of(
        Arguments.of("operatorそのものがnull", null),
        Arguments.of("userIdもactiveRoleIdも未解決", Operator.unresolved()),
        Arguments.of("activeRoleIdが未解決(ロール選択前)", new Operator("user-1", null)),
        Arguments.of("userIdが未解決", new Operator(null, "role-1")));
  }

  @ParameterizedTest(name = "{0}は401")
  @MethodSource("unresolvedOperators")
  void requireUserAdminRejectsAnUnresolvedOperatorWith401AndNeverAsksPermissionEngine(
      String label, Operator operator) {
    assertThatThrownBy(() -> authorizer.requireUserAdmin(operator))
        .isInstanceOf(OperatorUnresolvedException.class);

    verify(permissionEngineApi, never()).canAccessScreen(any(), any());
  }

  @Test
  void requireUserAdminRejectsAnOperatorWithoutTheScreenPermissionWith403() {
    when(permissionEngineApi.canAccessScreen("role-1", "user-management")).thenReturn(false);

    assertThatThrownBy(() -> authorizer.requireUserAdmin(new Operator("user-1", "role-1")))
        .isInstanceOf(UserAccessDeniedException.class);
  }

  @Test
  void requireUserAdminAllowsAnOperatorWithTheScreenPermission() {
    when(permissionEngineApi.canAccessScreen("role-1", "user-management")).thenReturn(true);

    assertThatCode(() -> authorizer.requireUserAdmin(new Operator("user-1", "role-1")))
        .doesNotThrowAnyException();
    verify(permissionEngineApi).canAccessScreen("role-1", "user-management");
  }

  @Test
  void theDecisionIsMadeForTheActiveRoleOnlyNotForTheUser() {
    when(permissionEngineApi.canAccessScreen("admin-role", "user-management")).thenReturn(true);
    when(permissionEngineApi.canAccessScreen("viewer-role", "user-management")).thenReturn(false);

    assertThatCode(() -> authorizer.requireUserAdmin(new Operator("same-user", "admin-role")))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.requireUserAdmin(new Operator("same-user", "viewer-role")))
        .isInstanceOf(UserAccessDeniedException.class);
  }

  @Test
  void requireOperatorUserIdDoesNotNeedAnActiveRoleNorAPermission() {
    assertThat(authorizer.requireOperatorUserId(new Operator("user-1", null))).isEqualTo("user-1");
    verify(permissionEngineApi, never()).canAccessScreen(any(), any());
  }

  @ParameterizedTest(name = "{0}は401")
  @MethodSource("unresolvedUserIds")
  void requireOperatorUserIdRejectsAnOperatorWithoutUserId(String label, Operator operator) {
    assertThatThrownBy(() -> authorizer.requireOperatorUserId(operator))
        .isInstanceOf(OperatorUnresolvedException.class);
  }

  static Stream<Arguments> unresolvedUserIds() {
    return Stream.of(
        Arguments.of("operatorそのものがnull", null),
        Arguments.of("userIdが未解決", new Operator(null, "role-1")),
        Arguments.of("何も未解決", Operator.unresolved()));
  }
}
