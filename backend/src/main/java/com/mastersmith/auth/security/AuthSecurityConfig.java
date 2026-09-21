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

import com.mastersmith.auth.cache.SessionCache;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.observation.AuthObservations;
import com.mastersmith.auth.token.AccessTokenVerifier;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * アプリケーション全体の{@code SecurityFilterChain}(Q2=A、security-design.md
 * NFR2.1・NFR2.9)。認証の要否の規則・セキュリティヘッダー・ステートレス・CSRF無効・CORSなしを
 * 設定する。認証フィルタは、認証(操作者が誰か、失敗は401)だけを担う。権限判定(失敗は403)は、従来どおり、各ユニットが、入口で、permission-engine(C10)を呼んで行う。
 *
 * <p>規則(上から順に評価し、最初に一致したものを適用する。deny by default):
 *
 * <ol>
 *   <li>エラーのディスパッチ({@code /error})は、素通し(元の応答の状態を、規則で置き換えない)。
 *   <li>{@code POST /api/auth/login}・{@code POST /api/auth/refresh}・{@code POST
 *       /api/users/invitations/*}{@code /accept}: 認証を要しない(BR5.11の3つだけ)。
 *   <li>{@code /api/**}の残りすべて: 認証を要する(Bearerトークン、{@link BearerAuthenticationFilter})。
 *   <li>{@code GET /actuator/health}(完全一致のみ): 認証を要しない(状態だけを返す)。
 *   <li>{@code /actuator/**}の残り: 拒否する。
 *   <li>{@code /api/**}・{@code /actuator/**}以外への{@code GET}・{@code HEAD}(フロントエンドの静的ファイル・SPAのルート):
 *       認証を要しない(業務データ・個人情報を含まない 静的な成果物のため)。
 *   <li>上記のいずれにも一致しないすべてのリクエスト: 拒否する。
 * </ol>
 *
 * <p>認証は{@code Authorization: Bearer}ヘッダーで行い、Cookieを使わない。CSRFの保護は無効にし、HTTPセッションを作らず({@link
 * SessionCreationPolicy#STATELESS})、{@code Set-Cookie}を返さない。CORSは設定しない(単一の実行可能WARからフロントエンドを配信するため)。
 */
@Configuration
public class AuthSecurityConfig {

  /** サイズ制限のフィルタを、Spring Securityのフィルタチェーンより前に置くための、順序の差(小さいほど先)。 */
  private static final int BEFORE_SECURITY_CHAIN = -10;

  @Bean
  public SecurityFilterChain authSecurityFilterChain(
      HttpSecurity http,
      AccessTokenVerifier verifier,
      SessionCache cache,
      Clock clock,
      AuthMetrics metrics,
      AuthEventLogger logger,
      AuthObservations observations,
      ProblemDetailsWriter problemWriter)
      throws Exception {
    BearerAuthenticationFilter bearerFilter =
        new BearerAuthenticationFilter(
            verifier, cache, clock, metrics, logger, observations, problemWriter);
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers
                    .contentTypeOptions(Customizer.withDefaults())
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives(SecurityHeaderValues.CONTENT_SECURITY_POLICY))
                    // 既定のキャッシュ抑止は、静的ファイルのキャッシュを妨げるため、無効にし、/api/**にだけno-storeを付ける。
                    .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                    .addHeaderWriter(
                        new DelegatingRequestMatcherHeaderWriter(
                            AuthRequestRules.API,
                            new StaticHeadersWriter(
                                "Cache-Control", SecurityHeaderValues.API_CACHE_CONTROL)))
                    .httpStrictTransportSecurity(Customizer.withDefaults()))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, e) ->
                            problemWriter.write(
                                request,
                                response,
                                AuthRequestRules.API.matches(request)
                                    ? AuthProblem.TOKEN_INVALID
                                    : AuthProblem.FORBIDDEN))
                    .accessDeniedHandler(
                        (request, response, e) ->
                            problemWriter.write(request, response, AuthProblem.FORBIDDEN)))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(AuthRequestRules.PUBLIC_API)
                    .permitAll()
                    .requestMatchers(AuthRequestRules.API)
                    .authenticated()
                    .requestMatchers(AuthRequestRules.HEALTH)
                    .permitAll()
                    .requestMatchers(AuthRequestRules.ACTUATOR)
                    .denyAll()
                    .requestMatchers(AuthRequestRules.STATIC_RESOURCES)
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(bearerFilter, AuthorizationFilter.class);
    return http.build();
  }

  /**
   * 認証情報(ユーザー名・パスワード)による認証は、行わない(認証は、Bearerトークンだけ)。{@code AuthenticationManager}を定義することで、Spring
   * Bootが、 既定のユーザー(起動のたびに生成される、ランダムなパスワードのユーザー)を作らないようにする。
   */
  @Bean
  public AuthenticationManager authenticationManager() {
    return authentication -> {
      throw new AuthenticationServiceException("Credential authentication is not supported");
    };
  }

  /** 認証のAPIのボディの上限(64KiB)を、Spring Securityのフィルタチェーンより前に置く(認証前の大きなボディを読み込ませないため)。 */
  @Bean
  public FilterRegistrationBean<AuthRequestSizeLimitFilter> authRequestSizeLimitFilter(
      ProblemDetailsWriter problemWriter) {
    FilterRegistrationBean<AuthRequestSizeLimitFilter> registration =
        new FilterRegistrationBean<>(new AuthRequestSizeLimitFilter(problemWriter));
    registration.setName("authRequestSizeLimitFilter");
    registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + BEFORE_SECURITY_CHAIN);
    return registration;
  }
}
