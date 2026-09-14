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

package com.mastersmith.schema.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * {@link ActiveRoleResolver}の最小実装(code-generation-plan.md「前提事項」参照)。リクエストヘッダー{@value
 * #ACTIVE_ROLE_HEADER}からactiveRoleIdを直接読み取る。
 *
 * <p><b>暫定実装</b>: JWT検証(署名・有効期限)は行わない。authentication-service(U5、Bolt 7相当)の実装時に、Bearer
 * JWTのクレームからactiveRoleIdを取り出す実装へ置き換える想定である。認可判定自体({@code
 * PermissionEngineApi.canAccessScreen})はコントローラ層で必ずサーバー側再検証を行うため、この暫定実装であっても 「実効権限の再検証」(project.md
 * Mandated)は満たされる。
 */
@Component
public class HeaderActiveRoleResolver implements ActiveRoleResolver {

  /** activeRoleIdを直接運ぶ暫定ヘッダー名。authentication-service実装後は撤去し、JWTクレームからの解決に置き換える。 */
  public static final String ACTIVE_ROLE_HEADER = "X-Active-Role-Id";

  @Override
  public String resolveActiveRoleId(HttpServletRequest request) {
    String value = request.getHeader(ACTIVE_ROLE_HEADER);
    return value == null || value.isBlank() ? null : value.trim();
  }
}
