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

package com.mastersmith.common.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証フィルタを通さずに、すべてのリクエストを通す{@code SecurityFilterChain}(テスト用)。{@code @SpringBootTest}で、実物の認証フィルタ
 * ({@code BearerAuthenticationFilter})を経由せず、{@link
 * TestOperatorContext}で供給した操作者で、コントローラ・サービスの振る舞いを確認するために用いる。 本番の設定({@code
 * AuthSecurityConfig})より優先する。
 *
 * <p>認証フィルタを通した確認(認証の要否の規則・401)は、{@code AuthSecurityConfigTest}・{@code
 * AuthenticationFlowIntegrationTest}が行う。
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestPermitAllSecurityConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain testPermitAllSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/**")
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
