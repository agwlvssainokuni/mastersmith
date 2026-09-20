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

package com.mastersmith.usermanagement.security;

import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.UserAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code /api/users}系の認可(rules.md BR4.10)と、{@code
 * /api/me/preferences}の操作者の確認(BR4.8)。権限判定そのものは自前で持たず、 {@link
 * PermissionEngineApi}(C10)へ委譲する。画面表示の出し分けだけに依存せず、サービスの入口で、必ずサーバー側で実効権限を再検証する (project.md
 * Mandated、NFR2.1)。
 */
@Component
public class UserAuthorizer {

  private static final Logger LOG = LoggerFactory.getLogger(UserAuthorizer.class);

  /** permission-engineが予約するscreenKey(user-managementは再定義しない、BR4.10)。 */
  public static final String USER_MANAGEMENT_SCREEN_KEY = "user-management";

  private final PermissionEngineApi permissionEngineApi;

  public UserAuthorizer(PermissionEngineApi permissionEngineApi) {
    this.permissionEngineApi = permissionEngineApi;
  }

  /**
   * 管理者向け操作の認可。操作者のuserIdまたはactiveRoleIdを解決できなければ401、{@code canAccessScreen(activeRoleId,
   * "user-management")}がfalseなら403。
   */
  public void requireUserAdmin(Operator operator) {
    if (operator == null || operator.userId() == null || operator.activeRoleId() == null) {
      throw new OperatorUnresolvedException();
    }
    if (!permissionEngineApi.canAccessScreen(operator.activeRoleId(), USER_MANAGEMENT_SCREEN_KEY)) {
      LOG.warn("User management access denied: operatorUserId={}", operator.userId());
      throw new UserAccessDeniedException();
    }
  }

  /** 自分自身の設定の操作(BR4.8)の認可。操作者のuserIdを解決できれば許可する(canAccessScreenとactiveRoleIdは不要)。 */
  public String requireOperatorUserId(Operator operator) {
    if (operator == null || operator.userId() == null) {
      throw new OperatorUnresolvedException();
    }
    return operator.userId();
  }
}
