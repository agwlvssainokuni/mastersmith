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

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link SecurityContextOperatorContext}(C15の{@link OperatorContext}の実装)の契約テスト(BR5.12):
 * 認証済みのリクエストで{@link Operator}を返すこと、アクティブロールがnullでも {@link
 * Operator}が存在すること(未選択は、認証エラーではない)、認証されていないリクエスト(認証フィルタを通っていない・匿名・他の種類の認証)では解決できない(空)こと。
 */
class SecurityContextOperatorContextTest {

  private final OperatorContext context = new SecurityContextOperatorContext();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void anAuthenticatedRequestYieldsTheOperator() {
    Operator operator = new Operator("u-1", "sid-1", "r1");
    SecurityContextHolder.getContext().setAuthentication(new OperatorAuthentication(operator));

    assertThat(context.current()).contains(operator);
  }

  @Test
  void anOperatorExistsEvenWhenTheActiveRoleIsNotSelected() {
    SecurityContextHolder.getContext()
        .setAuthentication(new OperatorAuthentication(new Operator("u-1", "sid-1", null)));

    assertThat(context.current()).isPresent();
    assertThat(context.current().get().activeRoleId()).isNull();
  }

  @Test
  void aRequestThatDidNotPassTheAuthenticationFilterCannotBeResolved() {
    assertThat(context.current()).isEmpty();
  }

  @Test
  void anAnonymousAuthenticationIsNotAnOperator() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThat(context.current()).isEmpty();
  }

  @Test
  void anAuthenticationOfAnotherKindIsNeverTreatedAsAnOperator() {
    // ヘッダー・ユーザー名などから、操作者を作る経路は、ない。
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("someone", "x", List.of()));

    assertThat(context.current()).isEmpty();
  }

  @Test
  void theAuthenticationCarriesNoCredentialsNorAuthorities() {
    OperatorAuthentication authentication =
        new OperatorAuthentication(new Operator("u-1", "sid-1", "r1"));

    assertThat(authentication.getCredentials()).isNull();
    assertThat(authentication.getAuthorities()).isEmpty();
    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getName()).isEqualTo("u-1");
  }
}
