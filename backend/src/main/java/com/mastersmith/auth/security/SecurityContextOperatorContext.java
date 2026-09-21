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

package com.mastersmith.auth.security;

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * C15の{@link OperatorContext}の実装(BR5.12)。認証フィルタ({@link
 * BearerAuthenticationFilter})が、セキュリティコンテキストに設定した{@link OperatorAuthentication}から、{@link
 * Operator}を読む(読み取り専用)。ヘッダー({@code X-User-Id}・{@code X-Active-Role-Id})は、読まない。
 *
 * <p>認証されていない(認証フィルタを通っていない)リクエストでは、空を返す。アクティブロールがnullでも、{@link Operator}は存在する。
 */
@Component
public class SecurityContextOperatorContext implements OperatorContext {

  @Override
  public Optional<Operator> current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof OperatorAuthentication operatorAuthentication
        && operatorAuthentication.isAuthenticated()) {
      return Optional.of(operatorAuthentication.getPrincipal());
    }
    return Optional.empty();
  }
}
