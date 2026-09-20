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

package com.mastersmith.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.mastersmith.MastersmithApplication;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.config.InitialAdminProperties;
import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserChangedEventPublisher;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.HashConcurrencyLimiter;
import com.mastersmith.usermanagement.security.PasswordHasher;
import com.mastersmith.usermanagement.service.InitialAdminBootstrap;
import com.mastersmith.usermanagement.testsupport.RecordingObservationHandler;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 秘密・個人情報が、出力に**現れない**ことの検査(NFR2.2・NFR2.6・NFR2.10、nfr-design NFR8.2、team.md必須テスト種別):
 * 招待・受諾(成功・404・422)・ログイン成功時のハッシュ更新・
 * 初期管理者の作成・メール送信失敗などの、実際の経路を流し、その間に出た**ログ**(DEBUGを含む)・**メトリクスのラベル**・**スパンの属性**・**ProblemDetails**・
 * **UserChangedEventのスナップショット**に、パスワード・招待トークン・passwordHash・メールアドレス・氏名の実値が現れないことを確認する。
 *
 * <p>UserChangedEventのスナップショットは、設計(entities.md・rules.md
 * BR4.9)により、監査ログのために氏名・メールアドレス・状態・ロールを**持つ**(認証情報だけを持たない)。
 * したがって、スナップショットについては、パスワード・招待トークン・passwordHashが現れないことを確認する。
 *
 * <p>共通基盤(観測の規約・リクエストログ)は本Boltの対象外のため、Spring MVCが記録する{@code
 * http.server.requests}の観測(生のURLを高カーディナリティの属性に持ちうる)は、 検査の対象にせず、U4が記録するスパン(名前が{@code
 * user.}で始まるもの)を検査する(notes参照)。
 */
@SpringBootTest(classes = MastersmithApplication.class)
@AutoConfigureMockMvc
@Import(UserManagementNoLeakTest.ObservationConfig.class)
class UserManagementNoLeakTest {

  @TestConfiguration
  static class ObservationConfig {
    @Bean
    RecordingObservationHandler recordingObservationHandler() {
      return new RecordingObservationHandler();
    }

    @Bean
    EventCollector eventCollector() {
      return new EventCollector();
    }
  }

  /** 発行されたUserChangedEventを集める。 */
  static class EventCollector {
    private final List<UserChangedEvent> events = Collections.synchronizedList(new ArrayList<>());

    @EventListener
    public void on(UserChangedEvent event) {
      events.add(event);
    }

    List<UserChangedEvent> events() {
      synchronized (events) {
        return List.copyOf(events);
      }
    }
  }

  // 実値として現れてはならない、特徴的な文字列。
  private static final String PASSWORD_ACCEPT = "NoLeak-Passphrase-Alpha-1";
  private static final String PASSWORD_LOGIN = "NoLeak-Passphrase-Login-3";
  private static final String PASSWORD_ADMIN = "NoLeak-Passphrase-Bootstrap-4";
  private static final String PASSWORD_TOO_SHORT = "NoLeak7";
  private static final String NAME_INVITED_1 = "NoLeakNameInvitedOne";
  private static final String NAME_MAIL_FAILURE = "NoLeakNameMailFailure";
  private static final String NAME_ACCEPTED = "NoLeakNameAccepted";
  private static final String NAME_INVITED_3 = "NoLeakNameInvitedThree";
  private static final String NAME_ACCEPT_422 = "NoLeakNameAcceptInvalid";
  private static final String NAME_LOGIN_USER = "NoLeakNameLoginUser";
  private static final String NAME_SELF_UPDATE = "NoLeakNameSelfUpdate";
  private static final String NAME_FORBIDDEN_UPDATE = "NoLeakNameForbiddenUpdate";
  private static final String BAD_EMAIL = "NoLeakBadEmailValue";
  private static final String UNKNOWN_TOKEN = "00000000-NoLeak-unknown-token-0000";

  private static final List<String> SEVEN_METRICS =
      List.of(
          "user.invitation.mail.failed",
          "user.invitation.accept.not_found",
          "user.role.escalation.denied",
          "user.password.hash.duration",
          "user.password.hash.rejected",
          "user.invitation.subject.rejected",
          "user.event.publish.failed");

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private UserAccountLookupApi userAccountLookupApi;
  @Autowired private PasswordHasher passwordHasher;
  @Autowired private UserChangedEventPublisher eventPublisher;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private RecordingObservationHandler observations;
  @Autowired private EventCollector eventCollector;

  @MockitoBean private PermissionEngineApi permissionEngineApi;
  @MockitoBean private JavaMailSender mailSender;

  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final List<String> problemBodies = new ArrayList<>();
  private final Set<String> secrets = new LinkedHashSet<>();
  private final List<String> createdEmails = new ArrayList<>();
  private Level previousLevel;

  @BeforeEach
  void runTheScenario() throws Exception {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Logger ours = (Logger) LoggerFactory.getLogger("com.mastersmith");
    previousLevel = ours.getLevel();
    ours.setLevel(Level.DEBUG);
    logs.start();
    root.addAppender(logs);
    observations.clear();

    when(permissionEngineApi.canAccessScreen("admin-role", "user-management")).thenReturn(true);
    when(permissionEngineApi.canAccessScreen("viewer-role", "user-management")).thenReturn(false);
    when(permissionEngineApi.roleExists(anyString())).thenReturn(true);
    stubMailSender();

    String email1 = newEmail();
    String email2 = newEmail();
    String email3 = newEmail();
    String emailLogin = newEmail();
    String emailAdmin = newEmail();
    secrets.addAll(
        List.of(
            PASSWORD_ACCEPT,
            PASSWORD_LOGIN,
            PASSWORD_ADMIN,
            PASSWORD_TOO_SHORT,
            NAME_INVITED_1,
            NAME_MAIL_FAILURE,
            NAME_ACCEPTED,
            NAME_INVITED_3,
            NAME_ACCEPT_422,
            NAME_LOGIN_USER,
            NAME_SELF_UPDATE,
            NAME_FORBIDDEN_UPDATE,
            BAD_EMAIL,
            UNKNOWN_TOKEN,
            email1,
            email2,
            email3,
            emailLogin,
            emailAdmin));

    // 1. 招待(成功)
    perform(
        admin(post("/api/users"), "admin-x")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invite(email1, NAME_INVITED_1)),
        201);
    String token1 = userRepository.findByEmail(email1).orElseThrow().getInvitationToken();
    secrets.add(token1);

    // 2. メール送信の失敗(SMTPの例外のメッセージが、宛先・氏名を含む状況): ロールバックして503
    doThrow(new MailSendException("smtp rejected " + email2 + " for " + NAME_MAIL_FAILURE))
        .when(mailSender)
        .send(any(MimeMessage.class));
    problemBodies.add(
        perform(
            admin(post("/api/users"), "admin-x")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invite(email2, NAME_MAIL_FAILURE)),
            503));
    Mockito.reset(mailSender);
    stubMailSender();

    // 3. 招待の受諾(成功): パスワードのハッシュ化
    perform(
        post("/api/users/invitations/" + token1 + "/accept")
            .contentType(MediaType.APPLICATION_JSON)
            .content(accept(PASSWORD_ACCEPT, NAME_ACCEPTED)),
        200);
    User accepted = userRepository.findByEmail(email1).orElseThrow();
    secrets.add(accepted.getPasswordHash());

    // 4. 招待の受諾(404: 未知のトークン)
    problemBodies.add(
        perform(
            post("/api/users/invitations/" + UNKNOWN_TOKEN + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(accept(PASSWORD_ACCEPT, NAME_ACCEPTED)),
            404));

    // 5. 招待の受諾(422: パスワードが短い)
    perform(
        admin(post("/api/users"), "admin-x")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invite(email3, NAME_INVITED_3)),
        201);
    String token3 = userRepository.findByEmail(email3).orElseThrow().getInvitationToken();
    secrets.add(token3);
    problemBodies.add(
        perform(
            post("/api/users/invitations/" + token3 + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(accept(PASSWORD_TOO_SHORT, NAME_ACCEPT_422)),
            422));

    // 6. 招待の入力の不備(422): 入力したメールアドレスを、エラーに含めない
    problemBodies.add(
        perform(
            admin(post("/api/users"), "admin-x")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invite(BAD_EMAIL, NAME_INVITED_1)),
            422));

    // 7. 権限なし(403)・自己のroleIds変更の拒否(422、警告ログ)
    problemBodies.add(
        perform(
            put("/api/users/" + accepted.getUserId())
                .header("X-User-Id", "viewer-x")
                .header("X-Active-Role-Id", "viewer-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + NAME_FORBIDDEN_UPDATE + "\",\"roleIds\":[]}"),
            403));
    problemBodies.add(
        perform(
            put("/api/users/" + accepted.getUserId())
                .header("X-User-Id", accepted.getUserId())
                .header("X-Active-Role-Id", "admin-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + NAME_SELF_UPDATE + "\",\"roleIds\":[\"r-escalated\"]}"),
            422));

    // 8. ログイン成功時のハッシュ更新(古いパラメータのハッシュ → 現在のパラメータ)
    PasswordHasher legacy = legacyHasher();
    String oldHash = legacy.hash(PASSWORD_LOGIN);
    User loginUser =
        userRepository.saveAndFlush(
            User.activeAdmin(NAME_LOGIN_USER, emailLogin, oldHash, List.of()));
    assertThat(userAccountLookupApi.verifyPasswordHash(loginUser.getUserId(), PASSWORD_LOGIN))
        .isTrue();
    String upgradedHash =
        userRepository.findById(loginUser.getUserId()).orElseThrow().getPasswordHash();
    assertThat(upgradedHash).isNotEqualTo(oldHash);
    secrets.addAll(List.of(oldHash, upgradedHash));

    // 9. 初期管理者の作成
    new InitialAdminBootstrap(
            new InitialAdminProperties(emailAdmin, PASSWORD_ADMIN, List.of(), "Administrator"),
            userRepository,
            preferenceRepository,
            transactionManager,
            passwordHasher,
            eventPublisher)
        .run(null);
    secrets.add(userRepository.findByEmail(emailAdmin).orElseThrow().getPasswordHash());
  }

  @AfterEach
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

  // ---- ヘルパー ----

  private String newEmail() {
    String email = UserTestFactory.uniqueEmail();
    createdEmails.add(email);
    return email;
  }

  private void stubMailSender() {
    when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
  }

  private PasswordHasher legacyHasher() {
    SimpleMeterRegistry isolated = new SimpleMeterRegistry();
    UserManagementProperties.Hash params =
        new UserManagementProperties.Hash(1024, 1, 1, 16, 32, 4, Duration.ofSeconds(5));
    return new PasswordHasher(
        params, new HashConcurrencyLimiter(4, params.waitTimeout(), isolated), isolated);
  }

  private static MockHttpServletRequestBuilder admin(
      MockHttpServletRequestBuilder builder, String userId) {
    return builder.header("X-User-Id", userId).header("X-Active-Role-Id", "admin-role");
  }

  private static String invite(String email, String name) {
    return "{\"email\":\""
        + email
        + "\",\"name\":\""
        + name
        + "\",\"roleIds\":[\"r1\"],\"locale\":\"en\"}";
  }

  private static String accept(String password, String name) {
    return "{\"password\":\"" + password + "\",\"name\":\"" + name + "\"}";
  }

  /** リクエストを実行し、期待のステータスを確認して、応答のボディを返す。 */
  private String perform(MockHttpServletRequestBuilder request, int expectedStatus)
      throws Exception {
    return mockMvc
        .perform(request)
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void assertNoSecrets(String label, String text) {
    for (String secret : secrets) {
      assertThat(text).as("%s must not contain a secret value", label).doesNotContain(secret);
    }
  }

  private List<String> logTexts() {
    List<String> texts = new ArrayList<>();
    synchronized (logs.list) {
      for (ILoggingEvent event : List.copyOf(logs.list)) {
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
  void logsAtEveryLevelNeverContainSecretsOrPersonalInformation() {
    List<String> texts = logTexts();

    // 検査が空振りしないよう、期待する(安全な)ログが実際に出ていることを確認する。
    assertThat(texts).anySatisfy(t -> assertThat(t).contains("Initial administrator created"));
    assertThat(texts)
        .anySatisfy(t -> assertThat(t).contains("Invitation mail failed: failure=SEND_FAILED"));
    assertThat(texts).anySatisfy(t -> assertThat(t).contains("Role escalation denied"));
    assertThat(texts).anySatisfy(t -> assertThat(t).contains("User management access denied"));
    assertThat(texts).anySatisfy(t -> assertThat(t).contains("Service unavailable"));
    for (String text : texts) {
      assertNoSecrets("log: " + text, text);
    }
  }

  @Test
  void metricLabelsNeverContainSecretsAndTheSevenUserMetricsHaveNoLabels() {
    for (Meter meter : meterRegistry.getMeters()) {
      StringBuilder id = new StringBuilder(meter.getId().getName());
      meter
          .getId()
          .getTags()
          .forEach(tag -> id.append(' ').append(tag.getKey()).append('=').append(tag.getValue()));
      assertNoSecrets("meter: " + id, id.toString());
    }
    for (String name : SEVEN_METRICS) {
      List<Meter> meters =
          meterRegistry.getMeters().stream().filter(m -> m.getId().getName().equals(name)).toList();
      assertThat(meters).as(name).isNotEmpty();
      assertThat(meters)
          .allSatisfy(m -> assertThat(m.getId().getTags()).as(name + " labels").isEmpty());
    }
    // 経路を流した結果が、メトリクスに現れている。
    assertThat(meterRegistry.get("user.invitation.mail.failed").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(meterRegistry.get("user.invitation.accept.not_found").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(meterRegistry.get("user.role.escalation.denied").counter().count())
        .isGreaterThanOrEqualTo(1.0);
    assertThat(meterRegistry.get("user.password.hash.duration").timer().count())
        .isGreaterThanOrEqualTo(3);
  }

  @Test
  void spanAttributesAreOnlyFixedValuesAndNeverContainSecretsOrPersonalInformation() {
    List<RecordingObservationHandler.Recorded> userSpans = observations.named("user.");

    assertThat(userSpans)
        .extracting(r -> r.name().split("\\|")[0])
        .contains(
            "user.api.invite",
            "user.api.accept",
            "user.api.update",
            "user.mail.send",
            "user.password.compute",
            "user.account.verify_password");
    for (RecordingObservationHandler.Recorded span : userSpans) {
      assertThat(span.keyValues())
          .as("attributes of %s", span.name())
          .allSatisfy(
              kv ->
                  assertThat(kv)
                      .matches("unit=user-management|operation=(hash|verify|verify_and_upgrade)"));
      assertNoSecrets("span: " + span.haystack(), span.haystack());
    }
    // 失敗したスパン(メール送信失敗・404・422)も、エラーとして、型名と固定のメッセージだけを持つ。
    assertThat(userSpans).anySatisfy(r -> assertThat(r.error()).isNotNull());
  }

  @Test
  void problemDetailsNeverContainSecretsOrPersonalInformation() {
    assertThat(problemBodies).hasSize(6);
    for (String body : problemBodies) {
      assertThat(body).contains("\"status\"");
      assertNoSecrets("problem: " + body, body);
      assertThat(body)
          .doesNotContain("passwordHash")
          .doesNotContain("invitationToken")
          .doesNotContain("$argon2");
    }
  }

  @Test
  void eventSnapshotsNeverContainCredentials() {
    List<UserChangedEvent> events = eventCollector.events();

    assertThat(events)
        .extracting(UserChangedEvent::operation)
        .contains(
            UserChangeOperation.INVITED,
            UserChangeOperation.ACTIVATED,
            UserChangeOperation.BOOTSTRAPPED);
    Set<String> credentials = new LinkedHashSet<>(secrets);
    credentials.removeIf(
        s -> s.startsWith("NoLeakName") || s.contains("@example.test") || s.equals(BAD_EMAIL));
    for (UserChangedEvent event : events) {
      String text = event.toString();
      for (String credential : credentials) {
        assertThat(text).as("event: " + event.operation()).doesNotContain(credential);
      }
      assertThat(text)
          .doesNotContain("passwordHash")
          .doesNotContain("invitationToken")
          .doesNotContain("$argon2");
    }
  }

  @Test
  void theDetectionItselfFailsWhenASecretIsPresent() {
    // 検査が空振りしていないことの確認: 秘密の値を含む文字列は、必ず検出される。
    for (String secret : List.of(PASSWORD_ACCEPT, NAME_ACCEPTED, UNKNOWN_TOKEN)) {
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> assertNoSecrets("probe", "x " + secret + " y"))
          .isInstanceOf(AssertionError.class);
    }
    String hash =
        secrets.stream().filter(s -> s.startsWith("$argon2id$")).findFirst().orElseThrow();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> assertNoSecrets("probe", "hash=" + hash))
        .isInstanceOf(AssertionError.class);
  }
}
