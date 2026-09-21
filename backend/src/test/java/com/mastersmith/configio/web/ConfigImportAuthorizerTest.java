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

package com.mastersmith.configio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import com.mastersmith.configio.exception.ConfigImportForbiddenException;
import com.mastersmith.configio.exception.ConfigImportUnauthorizedException;
import com.mastersmith.permission.PermissionEngineApi;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigImportAuthorizer}のテスト(BR9.4): 操作者を解決できなければ401、{@code
 * canAccessScreen}がfalseなら403。アクティブロールのnullは、自前で拒否せず、そのままC10へ渡す。
 */
class ConfigImportAuthorizerTest {

  private final OperatorContext operatorContext = mock(OperatorContext.class);
  private final PermissionEngineApi permissionEngineApi = mock(PermissionEngineApi.class);
  private final ConfigImportAuthorizer authorizer =
      new ConfigImportAuthorizer(operatorContext, permissionEngineApi);

  @Test
  void anUnresolvableOperatorIsUnauthorizedAndPermissionIsNeverAsked() {
    when(operatorContext.current()).thenReturn(Optional.empty());

    assertThatThrownBy(authorizer::authorize).isInstanceOf(ConfigImportUnauthorizedException.class);

    verifyNoInteractions(permissionEngineApi);
  }

  @Test
  void anOperatorWithoutTheScreenPermissionIsForbidden() {
    when(operatorContext.current()).thenReturn(Optional.of(new Operator("u", "s", "role-1")));
    when(permissionEngineApi.canAccessScreen("role-1", "config-import-export")).thenReturn(false);

    assertThatThrownBy(authorizer::authorize).isInstanceOf(ConfigImportForbiddenException.class);
  }

  @Test
  void anAuthorizedOperatorIsReturnedAndTheReservedScreenKeyIsUsed() {
    Operator operator = new Operator("u", "s", "role-1");
    when(operatorContext.current()).thenReturn(Optional.of(operator));
    when(permissionEngineApi.canAccessScreen("role-1", "config-import-export")).thenReturn(true);

    assertThat(authorizer.authorize()).isSameAs(operator);
    assertThat(ConfigImportAuthorizer.SCREEN_KEY).isEqualTo("config-import-export");
  }

  @Test
  void aNullActiveRoleIsPassedToPermissionEngineAsIsSoTheBootstrapExceptionStaysThere() {
    Operator operator = new Operator("u", "s", null);
    when(operatorContext.current()).thenReturn(Optional.of(operator));
    when(permissionEngineApi.canAccessScreen(null, "config-import-export")).thenReturn(true);

    assertThat(authorizer.authorize()).isSameAs(operator);
    verify(permissionEngineApi).canAccessScreen(null, "config-import-export");
  }

  @Test
  void aNullActiveRoleThatPermissionEngineDeniesIsForbiddenNotUnauthorized() {
    when(operatorContext.current()).thenReturn(Optional.of(new Operator("u", "s", null)));
    when(permissionEngineApi.canAccessScreen(null, "config-import-export")).thenReturn(false);

    assertThatThrownBy(authorizer::authorize).isInstanceOf(ConfigImportForbiddenException.class);
  }
}
