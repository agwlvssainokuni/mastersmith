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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * {@link CurrentOperatorProvider}の暫定実装。リクエストヘッダー{@value #USER_ID_HEADER}・{@value
 * #ACTIVE_ROLE_HEADER}から、操作者を読む。
 *
 * <p><b>暫定実装</b>: JWTの検証(署名・有効期限)は行わない。authentication-service(U5)の実装時に、検証済みトークンのクレームから読む実装へ
 * 置き換え、このクラスは撤去する(schema-introspectorの{@code HeaderActiveRoleResolver}と同じ流儀)。
 */
@Component
public class HeaderCurrentOperatorProvider implements CurrentOperatorProvider {

  /** 操作者のuserIdを運ぶ暫定ヘッダー名。 */
  public static final String USER_ID_HEADER = "X-User-Id";

  /** 操作者のactiveRoleIdを運ぶ暫定ヘッダー名。 */
  public static final String ACTIVE_ROLE_HEADER = "X-Active-Role-Id";

  @Override
  public Operator resolve(HttpServletRequest request) {
    return new Operator(read(request, USER_ID_HEADER), read(request, ACTIVE_ROLE_HEADER));
  }

  private static String read(HttpServletRequest request, String header) {
    String value = request.getHeader(header);
    return value == null || value.isBlank() ? null : value.trim();
  }
}
