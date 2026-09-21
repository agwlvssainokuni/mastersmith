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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.mastersmith.auth.cache.SessionCache;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.observation.AuthObservations;
import com.mastersmith.auth.observation.UnauthorizedReason;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.service.AuthExceptionTranslator;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.testsupport.AuthTestProperties;
import com.mastersmith.auth.testsupport.MutableClock;
import com.mastersmith.auth.token.AccessTokenIssuer;
import com.mastersmith.auth.token.AccessTokenVerifier;
import com.mastersmith.auth.token.JwtKeyProvider;
import com.mastersmith.common.security.Operator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link
 * BearerAuthenticationFilter}のテスト(BR5.11・NFR2.1・NFR2.2・NFR1.2、テーブル駆動、認可拒否専用テスト。team.mdの必須テスト種別(c)):
 * トークンなし・{@code alg: none}・HS256以外・ 署名の不正・必須の値の欠落・期限切れ・{@code sub}とSessionの{@code
 * userId}の不一致・失効・期限切れのSessionが、<b>いずれも同一の401</b>になること、キャッシュミスでDB障害のとき503・
 * キャッシュヒットのとき通ること、リクエストヘッダー({@code X-User-Id}・{@code
 * X-Active-Role-Id})が無視されること、<b>認証不要のパスで、期限切れ・不正な{@code Authorization}ヘッダーを付けても、
 * フィルタで401にならないこと</b>、{@code Bearer}のスキーム名の大文字小文字を区別しないこと。
 */
class BearerAuthenticationFilterTest {

  private static final String USER_ID = "user-1";
  private static final String ROLE_ID = "role-1";

  private MutableClock clock;
  private byte[] keyBytes;
  private AccessTokenIssuer issuer;
  private SessionRepository repository;
  private SimpleMeterRegistry registry;
  private BearerAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    clock = new MutableClock();
    keyBytes = new byte[32];
    new SecureRandom().nextBytes(keyBytes);
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty(
        JwtKeyProvider.KEY_PROPERTY, Base64.getEncoder().encodeToString(keyBytes));
    JwtKeyProvider keyProvider = new JwtKeyProvider(environment);
    issuer = new AccessTokenIssuer(keyProvider, AuthTestProperties.defaults(), clock);
    repository = mock(SessionRepository.class);
    registry = new SimpleMeterRegistry();
    AuthMetrics metrics = new AuthMetrics(registry);
    AuthEventLogger logger = new AuthEventLogger();
    SessionCache cache =
        new SessionCache(
            repository,
            100,
            Duration.ofSeconds(60),
            registry,
            new AuthExceptionTranslator(metrics, logger),
            Ticker.systemTicker());
    filter =
        new BearerAuthenticationFilter(
            new AccessTokenVerifier(keyProvider, clock),
            cache,
            clock,
            metrics,
            logger,
            AuthObservations.NOOP,
            new ProblemDetailsWriter());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  private Session activeSession(String sessionId, String userId, String roleId) {
    Session session =
        Session.issue(
            sessionId,
            userId,
            roleId,
            AuthTestFactory.newHash(),
            clock.instant(),
            clock.instant().plus(Duration.ofMinutes(30)));
    when(repository.findById(sessionId)).thenReturn(Optional.of(session));
    return session;
  }

  private record Result(
      MockHttpServletResponse response,
      MockFilterChain chain,
      Authentication authenticationInChain,
      String mdcUserId) {

    boolean chainInvoked() {
      return chain.getRequest() != null;
    }
  }

  private Result run(String method, String path, String... authorizationHeaders) throws Exception {
    return run(method, path, null, authorizationHeaders);
  }

  private Result run(
      String method,
      String path,
      java.util.function.Consumer<MockHttpServletRequest> customizer,
      String... authorizationHeaders)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    for (String header : authorizationHeaders) {
      request.addHeader("Authorization", header);
    }
    if (customizer != null) {
      customizer.accept(request);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Authentication> seen = new AtomicReference<>();
    AtomicReference<String> mdc = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
              throws java.io.IOException, jakarta.servlet.ServletException {
            seen.set(SecurityContextHolder.getContext().getAuthentication());
            mdc.set(MDC.get("userId") + "/" + MDC.get("sessionId"));
            super.doFilter(req, res);
          }
        };
    filter.doFilter(request, response, chain);
    return new Result(response, chain, seen.get(), mdc.get());
  }

  private double unauthorized(UnauthorizedReason reason) {
    return registry.get("auth.filter.unauthorized").tag("reason", reason.tag()).counter().count();
  }

  // ---- 成功 ----

  @Test
  void aValidTokenOfAnActiveSessionAuthenticatesTheOperatorWithTheSessionsActiveRole()
      throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);

    Result result = run("GET", "/api/users", "Bearer " + issuer.issue(USER_ID, "sid-1"));

    assertThat(result.chainInvoked()).isTrue();
    assertThat(result.response().getStatus()).isEqualTo(200);
    assertThat(result.authenticationInChain()).isInstanceOf(OperatorAuthentication.class);
    assertThat(result.authenticationInChain().getPrincipal())
        .isEqualTo(new Operator(USER_ID, "sid-1", ROLE_ID));
    // MDCには、リクエストの間だけ、userIdとsessionIdが入る。
    assertThat(result.mdcUserId()).isEqualTo(USER_ID + "/sid-1");
    assertThat(MDC.get("userId")).isNull();
    assertThat(MDC.get("sessionId")).isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void anUnselectedActiveRoleStillAuthenticatesWithANullRoleNotAnError() throws Exception {
    activeSession("sid-1", USER_ID, null);

    Result result = run("GET", "/api/users", "Bearer " + issuer.issue(USER_ID, "sid-1"));

    assertThat(result.chainInvoked()).isTrue();
    assertThat(((Operator) result.authenticationInChain().getPrincipal()).activeRoleId()).isNull();
  }

  @Test
  void theOperatorComesOnlyFromTheTokenAndTheSessionNeverFromTheRequestHeaders() throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);

    Result result =
        run(
            "GET",
            "/api/users",
            request -> {
              request.addHeader("X-User-Id", "attacker");
              request.addHeader("X-Active-Role-Id", "admin-role");
            },
            "Bearer " + issuer.issue(USER_ID, "sid-1"));

    assertThat(result.authenticationInChain().getPrincipal())
        .isEqualTo(new Operator(USER_ID, "sid-1", ROLE_ID));
  }

  @ParameterizedTest(name = "スキーム名: {0}")
  @MethodSource("schemeNames")
  void theBearerSchemeNameIsCaseInsensitive(String scheme) throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);

    Result result = run("GET", "/api/users", scheme + " " + issuer.issue(USER_ID, "sid-1"));

    assertThat(result.chainInvoked()).isTrue();
  }

  static Stream<String> schemeNames() {
    return Stream.of("Bearer", "bearer", "BEARER", "BeArEr");
  }

  // ---- 失敗(同一の401) ----

  private String forged(JWSAlgorithm algorithm, byte[] key, JWTClaimsSet claims) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
      byte[] signingKey =
          algorithm.equals(JWSAlgorithm.HS256) ? key : java.util.Arrays.copyOf(repeat(key, 64), 64);
      jwt.sign(new MACSigner(signingKey));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] repeat(byte[] key, int length) {
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = key[i % key.length];
    }
    return result;
  }

  private JWTClaimsSet.Builder claims(String sub, String sid) {
    return new JWTClaimsSet.Builder()
        .subject(sub)
        .claim("sid", sid)
        .issueTime(Date.from(clock.instant()))
        .expirationTime(Date.from(clock.instant().plus(Duration.ofMinutes(10))));
  }

  record Failure(
      String label,
      Function<BearerAuthenticationFilterTest, String[]> headers,
      UnauthorizedReason reason) {
    @Override
    public String toString() {
      return label;
    }
  }

  static Stream<Failure> failures() {
    return Stream.of(
        new Failure("Authorizationヘッダーなし", t -> new String[0], UnauthorizedReason.MISSING),
        new Failure(
            "Bearerではない(Basic)",
            t -> new String[] {"Basic dXNlcjpwYXNz"},
            UnauthorizedReason.MISSING),
        new Failure("Bearerの後ろが空", t -> new String[] {"Bearer "}, UnauthorizedReason.MISSING),
        new Failure(
            "Authorizationヘッダーが複数",
            t -> new String[] {"Bearer a", "Bearer b"},
            UnauthorizedReason.MISSING),
        new Failure(
            "JWTではない文字列", t -> new String[] {"Bearer not-a-jwt"}, UnauthorizedReason.INVALID),
        new Failure(
            "alg: none",
            t ->
                new String[] {
                  "Bearer "
                      + new PlainJWT(new PlainHeader(), t.claims(USER_ID, "sid-1").build())
                          .serialize()
                },
            UnauthorizedReason.INVALID),
        new Failure(
            "alg: HS384",
            t ->
                new String[] {
                  "Bearer "
                      + t.forged(JWSAlgorithm.HS384, t.keyBytes, t.claims(USER_ID, "sid-1").build())
                },
            UnauthorizedReason.INVALID),
        new Failure(
            "署名の不正(別の鍵)",
            t ->
                new String[] {
                  "Bearer "
                      + t.forged(
                          JWSAlgorithm.HS256, new byte[32], t.claims(USER_ID, "sid-1").build())
                },
            UnauthorizedReason.INVALID),
        new Failure(
            "必須の値の欠落(sid)",
            t ->
                new String[] {
                  "Bearer "
                      + t.forged(
                          JWSAlgorithm.HS256,
                          t.keyBytes,
                          new JWTClaimsSet.Builder()
                              .subject(USER_ID)
                              .issueTime(new Date())
                              .expirationTime(
                                  Date.from(t.clock.instant().plus(Duration.ofMinutes(10))))
                              .build())
                },
            UnauthorizedReason.INVALID),
        new Failure(
            "期限切れ",
            t -> {
              String token = t.issuer.issue(USER_ID, "sid-1");
              t.clock.advance(Duration.ofMinutes(10));
              return new String[] {"Bearer " + token};
            },
            UnauthorizedReason.EXPIRED),
        new Failure(
            "subがSessionのuserIdと一致しない",
            t -> new String[] {"Bearer " + t.issuer.issue("someone-else", "sid-1")},
            UnauthorizedReason.INVALID),
        new Failure(
            "Sessionが存在しない",
            t -> new String[] {"Bearer " + t.issuer.issue(USER_ID, "no-such-session")},
            UnauthorizedReason.SESSION_INACTIVE),
        new Failure(
            "Sessionが失効している",
            t -> {
              Session revoked = t.activeSession("sid-revoked", USER_ID, ROLE_ID);
              org.springframework.test.util.ReflectionTestUtils.setField(
                  revoked, "status", com.mastersmith.auth.entity.SessionStatus.REVOKED);
              return new String[] {"Bearer " + t.issuer.issue(USER_ID, "sid-revoked")};
            },
            UnauthorizedReason.SESSION_INACTIVE),
        new Failure(
            "Sessionのリフレッシュの有効期限が経過している",
            t -> {
              Session expiring =
                  Session.issue(
                      "sid-expired",
                      USER_ID,
                      ROLE_ID,
                      AuthTestFactory.newHash(),
                      t.clock.instant(),
                      t.clock.instant().plus(Duration.ofMinutes(5)));
              when(t.repository.findById("sid-expired")).thenReturn(Optional.of(expiring));
              String token = t.issuer.issue(USER_ID, "sid-expired");
              t.clock.advance(Duration.ofMinutes(5));
              return new String[] {"Bearer " + token};
            },
            UnauthorizedReason.SESSION_INACTIVE));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("failures")
  void everyFailureGivesTheSame401WithoutRevealingTheCause(Failure failure) throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);
    // 比較の基準: トークンなしの応答。
    Result reference = run("GET", "/api/users");

    Result result = run("GET", "/api/users", failure.headers().apply(this));

    assertThat(result.chainInvoked()).isFalse();
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.response().getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(result.response().getContentAsString()).contains("\"code\":\"auth.token.invalid\"");
    // 原因を区別しない: 本文・ヘッダーとも、同一。
    assertThat(result.response().getContentAsString())
        .isEqualTo(reference.response().getContentAsString());
    assertThat(headers(result.response())).isEqualTo(headers(reference.response()));
    // 原因は、メトリクスのタグにだけ現れる。
    assertThat(unauthorized(failure.reason())).isGreaterThanOrEqualTo(1);
  }

  private static java.util.Map<String, List<Object>> headers(MockHttpServletResponse response) {
    java.util.Map<String, List<Object>> all = new java.util.TreeMap<>();
    for (String name : response.getHeaderNames()) {
      all.put(name, response.getHeaderValues(name));
    }
    return all;
  }

  @Test
  void theReasonIsCountedInTheMetricByClassification() throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);

    run("GET", "/api/users");
    run("GET", "/api/users", "Bearer garbage");
    String token = issuer.issue(USER_ID, "sid-1");
    clock.advance(Duration.ofMinutes(11));
    run("GET", "/api/users", "Bearer " + token);
    run("GET", "/api/users", "Bearer " + issuer.issue(USER_ID, "unknown-sid"));

    assertThat(unauthorized(UnauthorizedReason.MISSING)).isEqualTo(1);
    assertThat(unauthorized(UnauthorizedReason.INVALID)).isEqualTo(1);
    assertThat(unauthorized(UnauthorizedReason.EXPIRED)).isEqualTo(1);
    assertThat(unauthorized(UnauthorizedReason.SESSION_INACTIVE)).isEqualTo(1);
  }

  // ---- キャッシュ・内部設定DBの障害 ----

  @Test
  void theFirstRequestReadsTheSessionAndTheFollowingOnesAreServedFromTheCache() throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);
    String token = issuer.issue(USER_ID, "sid-1");

    run("GET", "/api/users", "Bearer " + token);
    run("GET", "/api/users", "Bearer " + token);
    run("GET", "/api/users", "Bearer " + token);

    verify(repository, times(1)).findById("sid-1");
    assertThat(registry.get("auth.filter.duration").tag("result", "miss").timer().count())
        .isEqualTo(1);
    assertThat(registry.get("auth.filter.duration").tag("result", "hit").timer().count())
        .isEqualTo(2);
  }

  @Test
  void aDatabaseFailureOnACacheMissGives503NotA401AndDoesNotLetTheRequestThrough()
      throws Exception {
    when(repository.findById(anyString())).thenThrow(new QueryTimeoutException("db down"));

    Result result = run("GET", "/api/users", "Bearer " + issuer.issue(USER_ID, "sid-1"));

    assertThat(result.chainInvoked()).isFalse();
    assertThat(result.response().getStatus()).isEqualTo(503);
    assertThat(result.response().getContentAsString())
        .contains("\"code\":\"auth.service.unavailable\"");
    assertThat(result.response().getHeader("WWW-Authenticate")).isNull();
    // 障害は、認証の失敗(401)として数えない。
    assertThat(unauthorized(UnauthorizedReason.SESSION_INACTIVE)).isZero();
    assertThat(registry.get("auth.db.unavailable").counter().count()).isEqualTo(1);
  }

  @Test
  void aCachedActiveSessionKeepsAuthenticatingWhileTheDatabaseIsDown() throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);
    String token = issuer.issue(USER_ID, "sid-1");
    run("GET", "/api/users", "Bearer " + token);
    when(repository.findById(anyString())).thenThrow(new QueryTimeoutException("db down"));

    Result result = run("GET", "/api/users", "Bearer " + token);

    assertThat(result.chainInvoked()).isTrue();
  }

  // ---- 適用範囲(認証不要のパスでは実行しない) ----

  static Stream<Arguments> publicPaths() {
    return Stream.of(
        Arguments.of("POST", "/api/auth/login"),
        Arguments.of("POST", "/api/auth/refresh"),
        Arguments.of("POST", "/api/users/invitations/some-token/accept"),
        Arguments.of("GET", "/"),
        Arguments.of("GET", "/index.html"),
        Arguments.of("GET", "/login"),
        Arguments.of("GET", "/invitations/accept"),
        Arguments.of("GET", "/assets/app.js"),
        Arguments.of("HEAD", "/index.html"),
        Arguments.of("GET", "/actuator/health"));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("publicPaths")
  void onAPathThatNeedsNoAuthenticationAnExpiredOrInvalidAuthorizationHeaderNeverCausesA401(
      String method, String path) throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);
    String expired = issuer.issue(USER_ID, "sid-1");
    clock.advance(Duration.ofMinutes(20));

    // 期限切れのアクセストークン・不正な値・ヘッダーの重複のいずれでも、フィルタは何も検証せず、素通しにする。
    for (String[] headers :
        new String[][] {
          {"Bearer " + expired}, {"Bearer garbage"}, {"Basic x"}, {"Bearer a", "Bearer b"}, {}
        }) {
      Result result = run(method, path, headers);

      assertThat(result.chainInvoked()).as(method + " " + path + " " + List.of(headers)).isTrue();
      assertThat(result.response().getStatus()).isEqualTo(200);
      // 認証は行わない(セキュリティコンテキストは、設定しない)。
      assertThat(result.authenticationInChain()).isNull();
    }
    assertThat(registry.get("auth.filter.unauthorized").tag("reason", "expired").counter().count())
        .isZero();
  }

  static Stream<Arguments> authenticatedPaths() {
    return Stream.of(
        Arguments.of("GET", "/api/users"),
        Arguments.of("POST", "/api/users"),
        Arguments.of("GET", "/api/menu"),
        Arguments.of("POST", "/api/auth/logout"),
        Arguments.of("PUT", "/api/auth/active-role"),
        // 認証を要しない3つのAPIは、メソッド・パスが完全に一致する場合だけ(それ以外は、認証を要する: deny by default)
        Arguments.of("GET", "/api/auth/login"),
        Arguments.of("PUT", "/api/auth/refresh"),
        Arguments.of("POST", "/api/auth/login/"),
        Arguments.of("GET", "/api/users/invitations/some-token/accept"),
        Arguments.of("POST", "/api/users/invitations/a/b/accept"),
        Arguments.of("GET", "/api"),
        Arguments.of("GET", "/api/anything/new"));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("authenticatedPaths")
  void everyOtherApiPathRequiresAuthentication(String method, String path) throws Exception {
    Result result = run(method, path);

    assertThat(result.chainInvoked()).isFalse();
    assertThat(result.response().getStatus()).isEqualTo(401);
  }

  @Test
  void nothingIsVerifiedForAnApiPathOnceTheTokenIsValidEvenWithDifferentMethods() throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);
    String token = "Bearer " + issuer.issue(USER_ID, "sid-1");

    for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
      assertThat(run(method, "/api/menu-items", token).chainInvoked()).isTrue();
    }
  }

  @Test
  void aTokenIssuedByADifferentKeyAfterAKeyRotationIsRejectedAndNothingElseChanges()
      throws Exception {
    activeSession("sid-1", USER_ID, ROLE_ID);
    byte[] otherKey = new byte[32];
    new SecureRandom().nextBytes(otherKey);
    String oldToken = forged(JWSAlgorithm.HS256, otherKey, claims(USER_ID, "sid-1").build());

    Result result = run("GET", "/api/users", "Bearer " + oldToken);

    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chainInvoked()).isFalse();
    // Sessionは失効しない: 新しい鍵のトークンで、そのまま認証できる(リフレッシュで復旧できる)。
    assertThat(run("GET", "/api/users", "Bearer " + issuer.issue(USER_ID, "sid-1")).chainInvoked())
        .isTrue();
  }
}
