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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestClockConfig;
import com.mastersmith.auth.testsupport.AuthTestEndpoints;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.token.AccessTokenIssuer;
import com.mastersmith.usermanagement.testsupport.ChunkedRequests;
import com.mastersmith.usermanagement.testsupport.JsonBodies;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code AuthSecurityConfig}(アプリケーション全体の{@code
 * SecurityFilterChain})のテスト(実際のフィルタチェーン・H2、NFR2.1・NFR2.8〜NFR2.11): 認証の要否の規則(3つの認証不要のAPI・静的ファイル・
 * {@code /actuator/health}・その他のactuatorの拒否・{@code /api/**}の残りは認証必須)、セキュリティヘッダーの値、{@code
 * Cache-Control: no-store}が{@code /api/**}にだけ付くこと、すべての 応答に{@code Set-Cookie}がないこと、{@code
 * /api/**}・{@code /actuator/**}以外への{@code GET}・{@code HEAD}以外のメソッドが拒否されること、ヘルスの結果が5秒間キャッシュされること、
 * ボディの上限(413)の2つの経路でセキュリティヘッダーが同じであること。
 */
@AutoConfigureMockMvc
@Import({
  AuthTestClockConfig.class,
  AuthTestEndpoints.class,
  AuthSecurityConfigTest.CountingHealthConfig.class
})
class AuthSecurityConfigTest extends AuthIntegrationTestBase {

  /** ヘルスの結果のキャッシュ(5秒)の確認のため、呼ばれた回数を数える。 */
  @TestConfiguration(proxyBeanMethods = false)
  static class CountingHealthConfig {
    static final AtomicInteger CALLS = new AtomicInteger();

    @Bean
    HealthIndicator authTestCountingHealthIndicator() {
      return () -> {
        CALLS.incrementAndGet();
        return Health.up().build();
      };
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private AccessTokenIssuer issuer;

  private final List<MvcResult> results = new ArrayList<>();

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    ResultActions actions = mockMvc.perform(request);
    results.add(actions.andReturn());
    return actions;
  }

  /** 有効なSessionと、そのアクセストークン(Authorizationヘッダーの値)を用意する。 */
  private String bearer(String userId, String activeRoleId) {
    Session session =
        sessionRepository.saveAndFlush(
            Session.issue(
                AuthTestFactory.newSessionId(),
                userId,
                activeRoleId,
                AuthTestFactory.newHash(),
                clock.instant(),
                clock.instant().plus(Duration.ofMinutes(30))));
    return "Bearer " + issuer.issue(userId, session.getSessionId());
  }

  private String expiredBearer() {
    String userId = AuthTestFactory.uniqueUserId();
    String header = bearer(userId, null);
    clock.advance(Duration.ofMinutes(11));
    return header;
  }

  // ---- 認証が必要なAPI ----

  @Test
  void anAuthenticatedRequestReachesTheControllerWithTheOperatorFromTheTokenAndTheSession()
      throws Exception {
    String userId = AuthTestFactory.uniqueUserId();

    perform(get("/api/auth-test/whoami").header("Authorization", bearer(userId, "role-1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.activeRoleId").value("role-1"));
  }

  @Test
  void requestHeadersThatUsedToCarryTheOperatorAreIgnored() throws Exception {
    String userId = AuthTestFactory.uniqueUserId();

    perform(
            get("/api/auth-test/whoami")
                .header("Authorization", bearer(userId, "role-1"))
                .header("X-User-Id", "admin")
                .header("X-Active-Role-Id", "admin-role"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.activeRoleId").value("role-1"));
    // トークンがなければ、ヘッダーだけでは、認証されない。
    perform(get("/api/users").header("X-User-Id", "admin").header("X-Active-Role-Id", "admin-role"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void everyApiPathExceptTheThreePublicOnesRequiresAuthenticationByDefault() throws Exception {
    for (MockHttpServletRequestBuilder request :
        List.of(
            get("/api/users"),
            get("/api/menu"),
            get("/api/audit-log"),
            post("/api/auth/logout"),
            put("/api/auth/active-role").contentType(MediaType.APPLICATION_JSON).content("{}"),
            // 新しく追加されるAPI(まだ存在しないものを含む)も、既定で認証を要する。
            get("/api/brand-new-api"))) {
      perform(request)
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", "Bearer"))
          .andExpect(jsonPath("$.code").value("auth.token.invalid"));
    }
    // 認証すれば、存在しないAPIは404。
    perform(
            get("/api/brand-new-api")
                .header("Authorization", bearer(AuthTestFactory.uniqueUserId(), null)))
        .andExpect(status().isNotFound());
  }

  @Test
  void aPostWithoutACsrfTokenIsAcceptedBecauseTheAuthenticationIsTheBearerHeaderNotACookie()
      throws Exception {
    perform(
            post("/api/auth-test/echo")
                .header("Authorization", bearer(AuthTestFactory.uniqueUserId(), null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length").value(7));
  }

  // ---- 認証不要の3つのAPI ----

  @Test
  void theThreePublicApisAreReachableEvenWithAnExpiredOrInvalidAuthorizationHeader()
      throws Exception {
    String expired = expiredBearer();
    when(userAccountLookupApi.findByEmail(anyString())).thenReturn(java.util.Optional.empty());

    // ログイン: 認証フィルタを通らない(通れば、auth.token.invalidの401になる)。ログインの失敗は、auth.login.failed。
    for (String authorization : new String[] {expired, "Bearer garbage"}) {
      perform(
              post("/api/auth/login")
                  .header("Authorization", authorization)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"nobody@example.test\",\"password\":\"x\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("auth.login.failed"))
          .andExpect(header().doesNotExist("WWW-Authenticate"));
      // リフレッシュ: 期限切れのアクセストークンを付けたままでも、リフレッシュで復旧できる経路が保たれる。
      perform(
              post("/api/auth/refresh")
                  .header("Authorization", authorization)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"refreshToken\":\"unknown\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("auth.refresh.rejected"));
      // 招待受諾(user-managementが提供): 未知のトークンは、404(認証の失敗ではない)。
      perform(
              post("/api/users/invitations/unknown-token/accept")
                  .header("Authorization", authorization)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"a-long-enough-passphrase\",\"name\":\"n\"}"))
          .andExpect(status().isNotFound());
    }
  }

  // ---- 静的ファイル・SPAのルート ----

  @Test
  void staticFilesAndTheSpaRoutesNeedNoAuthentication() throws Exception {
    // 静的ファイルは、フロントエンド(U12)が同梱する。ここでは存在しないため404で、認証(401)・拒否(403)にはならない。
    for (String path :
        new String[] {"/", "/index.html", "/login", "/invitations/accept", "/assets/app.js"}) {
      int status = perform(get(path)).andReturn().getResponse().getStatus();
      assertThat(status).as(path).isNotIn(401, 403);
    }
    int headStatus = perform(head("/index.html")).andReturn().getResponse().getStatus();
    assertThat(headStatus).isNotIn(401, 403);
  }

  @Test
  void aMethodOtherThanGetOrHeadOutsideApiAndActuatorIsDeniedEvenWithoutAnyResource()
      throws Exception {
    for (MockHttpServletRequestBuilder request :
        List.of(
            post("/index.html"),
            put("/somewhere"),
            delete("/some/path"),
            post("/"),
            options("/index.html"))) {
      perform(request)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("auth.forbidden"))
          .andExpect(header().doesNotExist("WWW-Authenticate"));
    }
  }

  // ---- actuator ----

  @Test
  void onlyTheHealthEndpointIsPublicAndItShowsOnlyTheStatus() throws Exception {
    perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
  }

  @Test
  void everyOtherActuatorPathIsDeniedIncludingHealthGroupsAndOtherMethods() throws Exception {
    for (MockHttpServletRequestBuilder request :
        List.of(
            get("/actuator/health/liveness"),
            get("/actuator/health/readiness"),
            get("/actuator/health/db"),
            get("/actuator/health/"),
            get("/actuator/info"),
            get("/actuator/env"),
            get("/actuator/metrics"),
            get("/actuator/prometheus"),
            get("/actuator"),
            post("/actuator/health"),
            post("/actuator/shutdown"))) {
      perform(request)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("auth.forbidden"));
    }
    // 認証済みでも、公開しない。
    perform(
            get("/actuator/env")
                .header("Authorization", bearer(AuthTestFactory.uniqueUserId(), null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void theHealthResultIsCachedForFiveSecondsSoUnauthenticatedCallsDoNotHitTheDatabaseEveryTime()
      throws Exception {
    int before = CountingHealthConfig.CALLS.get();

    for (int i = 0; i < 6; i++) {
      perform(get("/actuator/health")).andExpect(status().isOk());
    }

    assertThat(CountingHealthConfig.CALLS.get() - before).isLessThanOrEqualTo(1);
  }

  // ---- セキュリティヘッダー・Cookie・CORS ----

  @Test
  void theSecurityHeadersAreOnEveryResponseAndNoStoreOnlyUnderApi() throws Exception {
    ResultActions api =
        perform(
            get("/api/auth-test/whoami")
                .header("Authorization", bearer(AuthTestFactory.uniqueUserId(), null)));
    ResultActions unauthenticatedApi = perform(get("/api/users"));
    ResultActions staticFile = perform(get("/index.html"));
    ResultActions health = perform(get("/actuator/health"));

    for (ResultActions actions : List.of(api, unauthenticatedApi, staticFile, health)) {
      actions
          .andExpect(header().string("Referrer-Policy", "no-referrer"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(header().string("X-Frame-Options", "DENY"))
          .andExpect(
              header()
                  .string("Content-Security-Policy", SecurityHeaderValues.CONTENT_SECURITY_POLICY));
    }
    api.andExpect(header().string("Cache-Control", "no-store"));
    unauthenticatedApi.andExpect(header().string("Cache-Control", "no-store"));
    // 静的ファイルのキャッシュを妨げない(Spring Securityの既定のキャッシュ抑止は、無効にしてある)。
    assertThat(staticFile.andReturn().getResponse().getHeader("Cache-Control"))
        .isNotEqualTo("no-store");
    assertThat(health.andReturn().getResponse().getHeader("Cache-Control"))
        .isNotEqualTo("no-store");
  }

  @Test
  void theLoginAndRefreshResponsesAreNotCacheable() throws Exception {
    when(userAccountLookupApi.findByEmail(anyString())).thenReturn(java.util.Optional.empty());

    perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.test\",\"password\":\"x\"}"))
        .andExpect(header().string("Cache-Control", "no-store"));
    perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"x\"}"))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void noResponseEverSetsACookieAndNoHttpSessionIsCreated() throws Exception {
    perform(
        get("/api/auth-test/whoami")
            .header("Authorization", bearer(AuthTestFactory.uniqueUserId(), null)));
    perform(get("/api/users"));
    perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"));
    perform(get("/index.html"));
    perform(get("/actuator/health"));
    perform(post("/index.html"));

    for (MvcResult result : results) {
      assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
      assertThat(result.getResponse().getCookies()).isEmpty();
      assertThat(result.getRequest().getSession(false)).isNull();
    }
  }

  @Test
  void corsIsNotConfiguredSoACrossOriginRequestGetsNoAllowOriginHeader() throws Exception {
    perform(
            get("/api/auth-test/whoami")
                .header("Origin", "https://evil.example")
                .header("Authorization", bearer(AuthTestFactory.uniqueUserId(), null)))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    perform(
            options("/api/auth-test/whoami")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  // ---- ボディの上限(413)の2つの経路 ----

  @Test
  void the413FromTheSizeLimitFilterAndFromTheControllerPathHaveTheSameSecurityHeaders()
      throws Exception {
    // JSONとして正しい大きなボディ(JSONの読み取りが、最後まで読み進める)。
    byte[] tooLarge =
        ("{\"email\":\"a@example.test\",\"password\":\"" + "x".repeat(64 * 1024) + "\"}")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    // (a) Content-Lengthが上限を超える: サイズ制限のフィルタが、フィルタチェーンより前に、直接書く。
    ResultActions declared =
        perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(tooLarge))
            .andExpect(status().isPayloadTooLarge());
    // (b) チャンク転送(Content-Lengthなし): 読み取りの途中で例外になり、コントローラの助言が413にする。
    ResultActions chunked =
        perform(
                post("/api/auth/login")
                    .with(ChunkedRequests.chunked())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(tooLarge))
            .andExpect(status().isPayloadTooLarge());

    for (ResultActions actions : List.of(declared, chunked)) {
      actions
          .andExpect(jsonPath("$.code").value("auth.request.too-large"))
          .andExpect(header().string("Referrer-Policy", "no-referrer"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(header().string("X-Frame-Options", "DENY"))
          .andExpect(
              header()
                  .string("Content-Security-Policy", SecurityHeaderValues.CONTENT_SECURITY_POLICY))
          .andExpect(header().string("Cache-Control", "no-store"));
    }
    // 両方の経路で、セキュリティヘッダーの集合が、同じ。
    assertThat(securityHeaders(declared)).isEqualTo(securityHeaders(chunked));
  }

  private static java.util.Map<String, String> securityHeaders(ResultActions actions) {
    java.util.Map<String, String> headers = new java.util.TreeMap<>();
    for (String name :
        new String[] {
          "Referrer-Policy",
          "X-Content-Type-Options",
          "X-Frame-Options",
          "Content-Security-Policy",
          "Cache-Control"
        }) {
      headers.put(name, actions.andReturn().getResponse().getHeader(name));
    }
    return headers;
  }

  @Test
  void anOversizedBodyIsRejectedBeforeAuthenticationSoNoSessionLookupHappens() throws Exception {
    // 認証前の大きなボディを読み込まない: 認証が必要な/api/auth/logoutでも、Authorizationがなくても、413(401より先)。
    perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.filler(64 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge());
  }
}
