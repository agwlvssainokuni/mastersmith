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

package com.mastersmith.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.mastersmith.MastersmithApplication;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthTestClockConfig;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.testsupport.MutableClock;
import com.mastersmith.auth.token.AccessTokenVerifier;
import com.mastersmith.auth.token.JwtKeyProvider;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.UserAccountLookupApi;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.PasswordHasher;
import com.mastersmith.usermanagement.testsupport.RecordingObservationHandler;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 認証情報・トークン・鍵の非出力の確認(BR5.14・NFR2.7、code-generation-plan.md Step
 * 14。認証の各経路を、実際のフィルタチェーン・実H2・実際のC11で流す): ログイン(成功・失敗・ロック中・503)・
 * リフレッシュ(成功・猶予内の再使用・猶予を超えた再使用)・ログアウト・ロール選択・認証フィルタ(401)の各経路で、U5のログ出力({@code
 * ListAppender}で捕捉)・メトリクスのラベル・スパンの属性・
 * ProblemDetails・応答に、パスワード・トークン(平文・ハッシュ)・鍵・メールアドレスの実値が現れないこと。起動時の鍵の検証の失敗は、{@code
 * JwtKeyProviderTest}が、起動失敗の出力全体で確認する。
 *
 * <p>ログイン・リフレッシュの成功の応答は、正当なトークンを運ぶ(その応答自体は検査の対象外。失敗・他の経路の応答に、現れないことを確認する)。検査が空振りしないよう、期待する(安全な)ログ・
 * メトリクス・スパンが出ていることと、検出そのものが、秘密の値を検出できることも確認する。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@AutoConfigureMockMvc
@Import({AuthTestClockConfig.class, AuthenticationNoLeakTest.ObservationConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationNoLeakTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class ObservationConfig {
    @Bean
    RecordingObservationHandler authRecordingObservationHandler() {
      return new RecordingObservationHandler();
    }
  }

  // 実値として現れてはならない、特徴的な文字列。
  private static final String PASSWORD = "NoLeak-Auth-Passphrase-Correct-1";
  private static final String WRONG_PASSWORD = "NoLeak-Auth-Passphrase-Wrong-2";
  private static final String UNKNOWN_PASSWORD = "NoLeak-Auth-Passphrase-Unknown-3";
  private static final String CAPACITY_PASSWORD = "NoLeak-Auth-Passphrase-Capacity-4";
  private static final String CAPACITY_DUMMY_PASSWORD = "NoLeak-Auth-Passphrase-DummyCapacity-5";
  private static final String DB_DOWN_EMAIL = "noleak-db-down@example.test";
  private static final String UNKNOWN_EMAIL = "noleak-unknown@example.test";
  private static final String DB_DETAIL = "SECRET-DB-DETAIL jdbc:h2:file:./data/secret";
  private static final String GARBAGE_TOKEN = "NoLeakGarbageToken.NoLeakPayload.NoLeakSignature";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private PasswordHasher passwordHasher;
  @Autowired private AccessTokenVerifier accessTokenVerifier;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private RecordingObservationHandler observations;
  @Autowired private MutableClock clock;
  @Autowired private Environment environment;

  @MockitoSpyBean private UserAccountLookupApi userAccountLookupApi;

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final List<String> problemBodies = new ArrayList<>();
  private final List<String> otherResponseBodies = new ArrayList<>();
  private final Set<String> credentials = new LinkedHashSet<>();
  private final Set<String> emails = new LinkedHashSet<>();
  private final Set<String> identifiers = new LinkedHashSet<>();
  private final List<String> createdEmails = new ArrayList<>();
  private Level previousLevel;

  @BeforeAll
  void runTheScenario() throws Exception {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Logger ours = (Logger) LoggerFactory.getLogger("com.mastersmith");
    previousLevel = ours.getLevel();
    ours.setLevel(Level.DEBUG);
    logs.start();
    root.addAppender(logs);
    observations.clear();
    clock.set(java.time.Instant.parse("2026-01-01T00:00:00Z"));

    credentials.addAll(
        List.of(
            PASSWORD,
            WRONG_PASSWORD,
            UNKNOWN_PASSWORD,
            CAPACITY_PASSWORD,
            CAPACITY_DUMMY_PASSWORD,
            DB_DETAIL,
            GARBAGE_TOKEN));
    // JWTの署名の鍵(Base64の文字列と、デコードした文字列)。
    String key = environment.getProperty(JwtKeyProvider.KEY_PROPERTY);
    credentials.add(key);
    credentials.add(
        new String(
            java.util.Base64.getDecoder().decode(key), java.nio.charset.StandardCharsets.UTF_8));
    emails.addAll(List.of(DB_DOWN_EMAIL, UNKNOWN_EMAIL));

    // ---- ユーザーの用意 ----
    User user = newUser("noleak-main", List.of("role-a", "role-b"));
    User lockedUser = newUser("noleak-locked", List.of("role-a"));
    User capacityUser = newUser("noleak-capacity", List.of("role-a"));
    identifiers.addAll(List.of(user.getUserId(), lockedUser.getUserId(), capacityUser.getUserId()));

    // ---- 1. ログイン成功(複数ロール) ----
    String loginBody = perform(login(user.getEmail(), PASSWORD), 200, false);
    String access1 = JsonPath.read(loginBody, "$.accessToken");
    String refresh1 = JsonPath.read(loginBody, "$.refreshToken");
    collectTokens(access1, refresh1);

    // ---- 2. ログイン失敗(パスワードの誤り・未登録・503(上限超過・DB障害)) ----
    perform(login(user.getEmail(), WRONG_PASSWORD), 401, true);
    perform(login(UNKNOWN_EMAIL, UNKNOWN_PASSWORD), 401, true);
    doThrow(new HashCapacityExceededException("Password hash capacity exceeded"))
        .when(userAccountLookupApi)
        .verifyPasswordHash(eq(capacityUser.getUserId()), eq(CAPACITY_PASSWORD));
    perform(login(capacityUser.getEmail(), CAPACITY_PASSWORD), 503, true);
    doThrow(new HashCapacityExceededException("Password hash capacity exceeded"))
        .when(userAccountLookupApi)
        .dummyVerify(eq(CAPACITY_DUMMY_PASSWORD));
    perform(login(UNKNOWN_EMAIL, CAPACITY_DUMMY_PASSWORD), 503, true);
    doThrow(new DataAccessResourceFailureException(DB_DETAIL))
        .when(userAccountLookupApi)
        .findByEmail(eq(DB_DOWN_EMAIL));
    perform(login(DB_DOWN_EMAIL, PASSWORD), 503, true);

    // ---- 3. ロック(しきい値の誤り → ロック中は正しいパスワードでも失敗 → 解除後に成功) ----
    for (int i = 0; i < 5; i++) {
      perform(login(lockedUser.getEmail(), WRONG_PASSWORD), 401, true);
    }
    perform(login(lockedUser.getEmail(), PASSWORD), 401, true);
    clock.advance(Duration.ofMinutes(16));
    String unlocked = perform(login(lockedUser.getEmail(), PASSWORD), 200, false);
    collectTokens(
        JsonPath.read(unlocked, "$.accessToken"), JsonPath.read(unlocked, "$.refreshToken"));

    // ---- 4. ロール選択(成功・保持しないロール) ----
    String access = accessOf(loggedIn(user));
    perform(
        authorized(put("/api/auth/active-role"), access, "{\"roleId\":\"role-b\"}"), 200, false);
    perform(
        authorized(put("/api/auth/active-role"), access, "{\"roleId\":\"someone-elses-role\"}"),
        403,
        true);

    // ---- 5. リフレッシュ(成功 → 猶予内の再使用 → 猶予を超えた再使用) ----
    String refreshed = perform(refresh(refresh1), 200, false);
    String access2 = JsonPath.read(refreshed, "$.accessToken");
    String refresh2 = JsonPath.read(refreshed, "$.refreshToken");
    collectTokens(access2, refresh2);
    perform(refresh(refresh1), 401, true); // 猶予内の再使用(401のみ)
    clock.advance(Duration.ofSeconds(11));
    perform(refresh(refresh1), 401, true); // 猶予を超えた再使用(盗用の疑い、Sessionを失効)
    perform(refresh(refresh2), 401, true); // 失効済み
    perform(refresh(GARBAGE_TOKEN), 401, true);

    // ---- 6. 認証フィルタの401(失効・トークンなし・不正・期限切れ) ----
    perform(authorized(post("/api/auth/logout"), access2, null), 401, true);
    perform(post("/api/auth/logout"), 401, true);
    perform(authorized(post("/api/auth/logout"), GARBAGE_TOKEN, null), 401, true);
    String shortLived = accessOf(loggedIn(user));
    clock.advance(Duration.ofMinutes(11));
    perform(authorized(post("/api/auth/logout"), shortLived, null), 401, true);

    // ---- 7. ログアウト(成功) ----
    clock.set(java.time.Instant.parse("2026-01-01T02:00:00Z")); // 新しい時刻(以前のSessionとは無関係)
    String toLogout = accessOf(loggedIn(user));
    perform(authorized(post("/api/auth/logout"), toLogout, null), 204, false);
  }

  @AfterAll
  void cleanUp() {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAppender(logs);
    ((Logger) LoggerFactory.getLogger("com.mastersmith")).setLevel(previousLevel);
    for (String email : createdEmails) {
      userRepository
          .findByEmail(email)
          .ifPresent(
              user -> {
                preferenceRepository.deleteById(user.getUserId());
                userRepository.delete(user);
              });
    }
  }

  // ---- 部品 ----

  private User newUser(String prefix, List<String> roles) {
    String email = prefix + "-" + java.util.UUID.randomUUID() + "@example.test";
    createdEmails.add(email);
    emails.add(email);
    return userRepository.saveAndFlush(
        User.activeAdmin("NoLeak Name " + prefix, email, passwordHasher.hash(PASSWORD), roles));
  }

  private static MockHttpServletRequestBuilder login(String email, String password) {
    return post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
  }

  private static MockHttpServletRequestBuilder refresh(String refreshToken) {
    return post("/api/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"refreshToken\":\"" + refreshToken + "\"}");
  }

  private static MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder builder, String accessToken, String body) {
    builder.header("Authorization", "Bearer " + accessToken);
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return builder;
  }

  /** ログイン成功の応答の本文(以降のステップで、アクセストークンを使う)。 */
  private String loggedIn(User user) throws Exception {
    String body = perform(login(user.getEmail(), PASSWORD), 200, false);
    collectTokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    return body;
  }

  private static String accessOf(String loginBody) {
    return JsonPath.read(loginBody, "$.accessToken");
  }

  /** 発行されたトークン(平文)・ハッシュ・userId・sessionIdを、検査の対象の値に加える。 */
  private void collectTokens(String accessToken, String refreshToken) {
    credentials.add(accessToken);
    credentials.add(refreshToken);
    // 署名部・ペイロード部の断片(トークンの一部が現れる場合も検出する)
    String[] parts = accessToken.split("\\.");
    credentials.add(parts[1]);
    credentials.add(parts[2]);
    credentials.add(AuthTestFactory.hashOf(refreshToken));
    try {
      var claims = accessTokenVerifier.verify(accessToken);
      identifiers.add(claims.sid());
      sessionRepository
          .findById(claims.sid())
          .map(Session::getPreviousRefreshTokenHash)
          .ifPresent(credentials::add);
    } catch (RuntimeException e) {
      // 期限切れなどで検証できないトークンは、sidを取り出せない(ハッシュは、上で加えた)。
    }
  }

  /** リクエストを実行し、ステータスを確認して、本文を返す。失敗の応答(ProblemDetails)は、検査の対象に加える。 */
  private String perform(
      MockHttpServletRequestBuilder request, int expectedStatus, boolean isProblem)
      throws Exception {
    var response = mockMvc.perform(request).andReturn().getResponse();
    String body = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(response.getStatus()).as("status of %s", body).isEqualTo(expectedStatus);
    if (isProblem) {
      problemBodies.add(body);
    } else if (expectedStatus >= 400) {
      problemBodies.add(body);
    } else if (expectedStatus != 200 || !body.contains("accessToken")) {
      // 正当なトークンを運ぶ成功の応答(ログイン・リフレッシュ)以外の応答。
      otherResponseBodies.add(body);
    }
    return body;
  }

  private void assertNoCredentials(String label, String text) {
    for (String credential : credentials) {
      assertThat(text).as("%s must not contain a credential", label).doesNotContain(credential);
    }
  }

  private List<String> logTexts(boolean onlyAuthLoggers) {
    List<String> texts = new ArrayList<>();
    synchronized (logs.list) {
      for (ILoggingEvent event : List.copyOf(logs.list)) {
        if (onlyAuthLoggers && !event.getLoggerName().startsWith("com.mastersmith.auth")) {
          continue;
        }
        StringBuilder text =
            new StringBuilder(event.getLoggerName())
                .append(' ')
                .append(event.getFormattedMessage());
        event.getMDCPropertyMap().values().forEach(v -> text.append(' ').append(v));
        if (event.getThrowableProxy() != null) {
          text.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }
        texts.add(text.toString());
      }
    }
    return texts;
  }

  // ---- 検査 ----

  @Test
  void logsNeverContainPasswordsTokensHashesOrTheKey() {
    List<String> all = logTexts(false);
    List<String> auth = logTexts(true);

    // 検査が空振りしないよう、期待する(安全な)ログが実際に出ていることを確認する。
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Login succeeded"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Login failed"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Account locked"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Refresh token reuse within grace"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Refresh token reuse detected"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Logged out"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Active role changed"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("password hash capacity exceeded"));
    assertThat(auth).anySatisfy(t -> assertThat(t).contains("Authentication storage unavailable"));
    assertThat(auth)
        .anySatisfy(t -> assertThat(t).contains("Authentication filter rejected the request"));
    // パスワード・トークン・ハッシュ・鍵は、どのロガーのログにも現れない。
    for (String text : all) {
      assertNoCredentials("log: " + text, text);
    }
  }

  @Test
  void authenticationLogsNeverContainEmailAddressesAndTheFailureReasonIsNeverDistinguished() {
    for (String text : logTexts(true)) {
      for (String email : emails) {
        assertThat(text).as("log: " + text).doesNotContain(email);
      }
    }
    // ログイン失敗の理由(原因)を、区別しない: 失敗のログは、事実だけ。
    assertThat(logTexts(true).stream().filter(t -> t.contains("Login failed")))
        .isNotEmpty()
        .allSatisfy(
            t ->
                assertThat(t)
                    .isEqualTo("com.mastersmith.auth.observation.AuthEventLogger Login failed"));
  }

  @Test
  void metricLabelsNeverContainPersonalInformationIdentifiersTokensOrTheKey() {
    List<Meter> authMeters =
        meterRegistry.getMeters().stream()
            .filter(m -> m.getId().getName().startsWith("auth."))
            .toList();
    assertThat(authMeters).isNotEmpty();
    for (Meter meter : meterRegistry.getMeters()) {
      StringBuilder id = new StringBuilder(meter.getId().getName());
      meter
          .getId()
          .getTags()
          .forEach(tag -> id.append(' ').append(tag.getKey()).append('=').append(tag.getValue()));
      assertNoCredentials("meter: " + id, id.toString());
      for (String email : emails) {
        assertThat(id.toString()).doesNotContain(email);
      }
      for (String identifier : identifiers) {
        assertThat(id.toString()).as("meter: " + id).doesNotContain(identifier);
      }
    }
    // ラベルは、値が固定された分類だけ(reason・result・cache、観測が自動で付ける固定の値のunit・例外の型名のerror)。
    for (Meter meter : authMeters) {
      assertThat(meter.getId().getTags())
          .as(meter.getId().getName())
          .allSatisfy(
              tag -> assertThat(tag.getKey()).isIn("reason", "result", "cache", "unit", "error"));
    }
    // 経路を流した結果が、メトリクスに現れている。
    assertThat(meterRegistry.get("auth.login.failed").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(meterRegistry.get("auth.account.locked").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(meterRegistry.get("auth.refresh.token.reuse.detected").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(meterRegistry.get("auth.refresh.reuse.within.grace").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(meterRegistry.get("auth.login.hash.capacity.exceeded").counter().count())
        .isGreaterThanOrEqualTo(2.0);
    assertThat(meterRegistry.get("auth.db.unavailable").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    for (String reason : List.of("missing", "invalid", "expired", "session_inactive")) {
      assertThat(
              meterRegistry.get("auth.filter.unauthorized").tag("reason", reason).counter().count())
          .as(reason)
          .isGreaterThanOrEqualTo(1.0);
    }
  }

  @Test
  void spanAttributesAreOnlyFixedClassificationsAndNeverContainSecretsOrIdentifiers() {
    List<RecordingObservationHandler.Recorded> authSpans = observations.named("auth.");

    assertThat(authSpans)
        .extracting(r -> r.name().split("\\|")[0])
        .contains("auth.login", "auth.refresh", "auth.logout", "auth.active-role", "auth.filter");
    for (RecordingObservationHandler.Recorded span : authSpans) {
      assertThat(span.keyValues())
          .as("attributes of %s", span.name())
          .allSatisfy(
              kv ->
                  assertThat(kv)
                      .matches(
                          "unit=authentication-service|reason=(missing|invalid|expired|session_inactive)|cache=(hit|miss)"));
      assertNoCredentials("span: " + span.haystack(), span.haystack());
      for (String email : emails) {
        assertThat(span.haystack()).doesNotContain(email);
      }
      for (String identifier : identifiers) {
        assertThat(span.haystack()).doesNotContain(identifier);
      }
    }
  }

  @Test
  void problemDetailsAndOtherFailureResponsesNeverContainSecretsOrPersonalInformation() {
    assertThat(problemBodies).isNotEmpty();
    for (String body : problemBodies) {
      assertThat(body).contains("\"status\"").contains("\"code\"");
      assertNoCredentials("problem: " + body, body);
      for (String email : emails) {
        assertThat(body).doesNotContain(email);
      }
      assertThat(body)
          .doesNotContain("passwordHash")
          .doesNotContain("refreshTokenHash")
          .doesNotContain("$argon2")
          .doesNotContain("Exception")
          .doesNotContain("jdbc:");
    }
    for (String body : otherResponseBodies) {
      assertNoCredentials("response: " + body, body);
    }
  }

  @Test
  void theDetectionItselfFailsWhenACredentialIsPresent() {
    // 検査が空振りしていないことの確認: 秘密の値を含む文字列は、必ず検出される。
    for (String secret : List.of(PASSWORD, WRONG_PASSWORD, GARBAGE_TOKEN, DB_DETAIL)) {
      assertThatThrownBy(() -> assertNoCredentials("probe", "x " + secret + " y"))
          .isInstanceOf(AssertionError.class);
    }
    String someToken = credentials.stream().filter(c -> c.length() == 43).findFirst().orElseThrow();
    assertThatThrownBy(() -> assertNoCredentials("probe", "prefix " + someToken))
        .isInstanceOf(AssertionError.class);
    assertThat(credentials).hasSizeGreaterThan(20);
  }
}
