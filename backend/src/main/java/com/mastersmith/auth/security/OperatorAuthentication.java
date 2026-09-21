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
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * 認証フィルタ({@link BearerAuthenticationFilter})が、セキュリティコンテキストに設定する認証済みの操作者({@link
 * Operator})。認証情報(credentials)は
 * 持たない。権限(authorities)は持たない(権限判定は、各ユニットが、permission-engine(C10)で行う。認証と認可の分担)。
 */
public final class OperatorAuthentication extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final Operator operator;

  public OperatorAuthentication(Operator operator) {
    super(List.of());
    this.operator = operator;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Operator getPrincipal() {
    return operator;
  }

  @Override
  public String getName() {
    return operator.userId();
  }
}
