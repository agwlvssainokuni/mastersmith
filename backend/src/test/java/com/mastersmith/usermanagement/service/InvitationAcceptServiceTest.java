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

package com.mastersmith.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.dto.AcceptInvitationRequest;
import com.mastersmith.usermanagement.dto.UserResponse;
import com.mastersmith.usermanagement.entity.FontSize;
import com.mastersmith.usermanagement.entity.Theme;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserPreference;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.exception.InvitationTokenNotFoundException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserValidationException;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.HashConcurrencyLimiter;
import com.mastersmith.usermanagement.security.PasswordHasher;
import com.mastersmith.usermanagement.testsupport.EventRecorder;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InvitationAcceptService}のテスト(W2、reliability-design.md NFR4.1、NFR8.2):
 * 正常系・既定値へのフォールバック・並行受諾(1件のみ成功)・404の一本化・
 * 未知のトークンでハッシュを計算しないこと・入力の検証(422)・受諾の原子性。コミットを伴う検証のため、テストはトランザクションの外で実行し、作成したUserは 後始末で削除する。
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InvitationAcceptServiceTest {

  private static final String PASSWORD = "correct horse battery";
  private static final long AWAIT_SECONDS = 30;

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final EventRecorder events = new EventRecorder();
  private final ExecutorService executor = Executors.newFixedThreadPool(2);
  private PasswordHasher hasher;
  private InvitationAcceptService service;

  @BeforeEach
  void setUp() {
    UserManagementProperties.Hash params =
        new UserManagementProperties.Hash(1024, 1, 1, 16, 32, 4, Duration.ofSeconds(5));
    hasher =
        new PasswordHasher(
            params,
            new HashConcurrencyLimiter(4, params.waitTimeout(), meterRegistry),
            meterRegistry);
    service = newService(hasher, preferenceRepository);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    preferenceRepository.deleteAll();
    userRepository.deleteAll();
  }

  private InvitationAcceptService newService(
      PasswordHasher passwordHasher, UserPreferenceRepository preferences) {
    return new InvitationAcceptService(
        userRepository,
        preferences,
        transactionManager,
        passwordHasher,
        events.publisher(),
        meterRegistry);
  }

  private User invited() {
    return userRepository.saveAndFlush(
        UserTestFactory.invitedUser(
            UserTestFactory.uniqueEmail(), List.of("r1", "r2"), UserTestFactory.newToken()));
  }

  private double notFoundCount() {
    return meterRegistry.get("user.invitation.accept.not_found").counter().count();
  }

  private static AcceptInvitationRequest request() {
    return new AcceptInvitationRequest(PASSWORD, "  受諾 花子  ", null, null, null);
  }

  // ---- 正常系 ----

  @Test
  void acceptingActivatesTheUserSetsTheHashedPasswordAndCreatesThePreference() {
    User invited = invited();

    UserResponse response =
        service.accept(
            invited.getInvitationToken(),
            new AcceptInvitationRequest(PASSWORD, "  受諾 花子  ", "dark", "large", "en"));

    assertThat(response.userId()).isEqualTo(invited.getUserId());
    assertThat(response.status()).isEqualTo("active");
    assertThat(response.name()).isEqualTo("受諾 花子");
    assertThat(response.roleIds()).containsExactly("r1", "r2");
    User stored = userRepository.findById(invited.getUserId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(stored.getName()).isEqualTo("受諾 花子");
    assertThat(stored.getInvitationToken()).isNull();
    assertThat(stored.getPasswordHash()).startsWith("$argon2id$").doesNotContain(PASSWORD);
    assertThat(hasher.verify(PASSWORD, stored.getPasswordHash())).isTrue();
    assertThat(stored.getEmail()).isEqualTo(invited.getEmail());
    UserPreference preference = preferenceRepository.findById(invited.getUserId()).orElseThrow();
    assertThat(preference.getTheme()).isEqualTo(Theme.DARK);
    assertThat(preference.getFontSize()).isEqualTo(FontSize.LARGE);
    assertThat(preference.getLocale()).isEqualTo(UiLocale.EN);

    UserChangedEvent event = events.single();
    assertThat(event.operation()).isEqualTo(UserChangeOperation.ACTIVATED);
    assertThat(event.actor()).isEqualTo(invited.getUserId());
    assertThat(event.beforeValue().status()).isEqualTo("invited");
    assertThat(event.beforeValue().name()).isEqualTo("招待 太郎");
    assertThat(event.afterValue().status()).isEqualTo("active");
    assertThat(event.afterValue().name()).isEqualTo("受諾 花子");
    assertThat(notFoundCount()).isZero();
  }

  @Test
  void omittedDisplaySettingsFallBackToTheDefaults() {
    User invited = invited();

    service.accept(invited.getInvitationToken(), request());

    UserPreference preference = preferenceRepository.findById(invited.getUserId()).orElseThrow();
    assertThat(preference.getTheme()).isEqualTo(Theme.LIGHT);
    assertThat(preference.getFontSize()).isEqualTo(FontSize.MEDIUM);
    assertThat(preference.getLocale()).isEqualTo(UiLocale.JA);
  }

  // ---- 404の一本化・未知のトークンでハッシュを計算しない ----

  @Test
  void unknownUsedAndCancelledTokensAreIndistinguishable404sAndNeverComputeAHash() {
    PasswordHasher spyHasher = mock(PasswordHasher.class);
    InvitationAcceptService guarded = newService(spyHasher, preferenceRepository);
    User used = invited();
    when(spyHasher.hash(any())).thenReturn("$argon2id$v=19$m=1024,t=1,p=1$c2FsdA$aGFzaA");
    guarded.accept(used.getInvitationToken(), request());
    String usedToken = used.getInvitationToken();
    User cancelled = invited();
    String cancelledToken = cancelled.getInvitationToken();
    cancelled.disable();
    userRepository.saveAndFlush(cancelled);
    org.mockito.Mockito.clearInvocations(spyHasher);
    long eventsBefore = events.events().size();

    List<String> messages = new ArrayList<>();
    for (String token :
        new String[] {UserTestFactory.newToken(), usedToken, cancelledToken, "", "   ", null}) {
      assertThatThrownBy(() -> guarded.accept(token, request()))
          .isInstanceOfSatisfying(
              InvitationTokenNotFoundException.class, e -> messages.add(e.getMessage()));
    }

    assertThat(messages).hasSize(6).containsOnly("Invitation not found");
    verify(spyHasher, never()).hash(any());
    assertThat(notFoundCount()).isEqualTo(6.0);
    assertThat(events.events()).hasSize((int) eventsBefore);
  }

  @Test
  void theNotFoundMessageNeverContainsTheToken() {
    String token = UserTestFactory.newToken();

    assertThatThrownBy(() -> service.accept(token, request())).hasMessageNotContaining(token);
  }

  // ---- 入力の検証(422) ----

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
        Arguments.of(
            "パスワードなし",
            new AcceptInvitationRequest(null, "名前", null, null, null),
            "user.validation.password.required"),
        Arguments.of(
            "パスワード7文字",
            new AcceptInvitationRequest("p".repeat(7), "名前", null, null, null),
            "user.validation.password.length"),
        Arguments.of(
            "パスワード129文字",
            new AcceptInvitationRequest("p".repeat(129), "名前", null, null, null),
            "user.validation.password.length"),
        Arguments.of(
            "氏名が空",
            new AcceptInvitationRequest(PASSWORD, "  ", null, null, null),
            "user.validation.name.required"),
        Arguments.of(
            "氏名に改行",
            new AcceptInvitationRequest(PASSWORD, "a\nb", null, null, null),
            "user.validation.name.controlCharacter"),
        Arguments.of(
            "テーマが許容値外",
            new AcceptInvitationRequest(PASSWORD, "名前", "blue", null, null),
            "user.validation.theme.invalid"),
        Arguments.of(
            "フォントサイズが許容値外",
            new AcceptInvitationRequest(PASSWORD, "名前", null, "huge", null),
            "user.validation.fontSize.invalid"),
        Arguments.of(
            "言語が許容値外",
            new AcceptInvitationRequest(PASSWORD, "名前", null, null, "fr"),
            "user.validation.locale.invalid"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  void invalidInputIs422WithoutComputingAHashOrChangingTheUser(
      String label, AcceptInvitationRequest request, String expectedKey) {
    PasswordHasher spyHasher = mock(PasswordHasher.class);
    InvitationAcceptService guarded = newService(spyHasher, preferenceRepository);
    User invited = invited();

    assertThatThrownBy(() -> guarded.accept(invited.getInvitationToken(), request))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e -> {
              assertThat(e.getErrors())
                  .extracting(UserFieldError::message)
                  .containsExactly(expectedKey);
              // 入力値(パスワードなど)は、メッセージにもエラーにも含めない。
              assertThat(e.getMessage()).doesNotContain("p".repeat(7));
              assertThat(e.getErrors().toString()).doesNotContain(PASSWORD);
            });

    verify(spyHasher, never()).hash(any());
    User stored = userRepository.findById(invited.getUserId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(UserStatus.INVITED);
    assertThat(stored.getInvitationToken()).isEqualTo(invited.getInvitationToken());
    assertThat(preferenceRepository.findById(invited.getUserId())).isEmpty();
    assertThat(events.events()).isEmpty();
    assertThat(notFoundCount()).isZero();
  }

  @Test
  void allValidationErrorsAreReportedTogether() {
    User invited = invited();

    assertThatThrownBy(
            () ->
                service.accept(
                    invited.getInvitationToken(),
                    new AcceptInvitationRequest("short", " ", "blue", "huge", "fr")))
        .isInstanceOfSatisfying(
            UserValidationException.class,
            e ->
                assertThat(e.getErrors())
                    .extracting(UserFieldError::field)
                    .containsExactly("password", "name", "theme", "fontSize", "locale"));
  }

  @Test
  void theRequestToStringDoesNotExposeThePassword() {
    assertThat(new AcceptInvitationRequest(PASSWORD, "氏名", "dark", null, null).toString())
        .doesNotContain(PASSWORD)
        .doesNotContain("氏名");
  }

  // ---- 並行受諾 ----

  @Test
  void concurrentAcceptancesOfTheSameTokenSucceedExactlyOnceAndTheOtherGets404() throws Exception {
    User invited = invited();
    String token = invited.getInvitationToken();
    CountDownLatch start = new CountDownLatch(1);

    Future<UserResponse> first = executor.submit(() -> acceptAfter(start, token));
    Future<UserResponse> second = executor.submit(() -> acceptAfter(start, token));
    start.countDown();

    int successes = 0;
    int notFound = 0;
    for (Future<UserResponse> future : List.of(first, second)) {
      try {
        future.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        successes++;
      } catch (ExecutionException e) {
        assertThat(e.getCause()).isInstanceOf(InvitationTokenNotFoundException.class);
        notFound++;
      }
    }

    assertThat(successes).isEqualTo(1);
    assertThat(notFound).isEqualTo(1);
    assertThat(events.events()).hasSize(1);
    assertThat(preferenceRepository.count()).isEqualTo(1);
    assertThat(userRepository.findById(invited.getUserId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
    assertThat(notFoundCount()).isEqualTo(1.0);
  }

  private UserResponse acceptAfter(CountDownLatch start, String token) throws InterruptedException {
    start.await(AWAIT_SECONDS, TimeUnit.SECONDS);
    return service.accept(token, request());
  }

  // ---- 障害・原子性 ----

  @Test
  void aHashCapacityFailureLeavesTheInvitationUsable() {
    PasswordHasher busy = mock(PasswordHasher.class);
    when(busy.hash(any())).thenThrow(new HashCapacityExceededException("busy"));
    User invited = invited();

    assertThatThrownBy(
            () ->
                newService(busy, preferenceRepository)
                    .accept(invited.getInvitationToken(), request()))
        .isInstanceOf(HashCapacityExceededException.class);

    assertThat(userRepository.findById(invited.getUserId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.INVITED);
    assertThat(notFoundCount()).isZero();
    // 再試行できる。
    service.accept(invited.getInvitationToken(), request());
    assertThat(userRepository.findById(invited.getUserId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void aFailureCreatingThePreferenceRollsBackTheActivation() {
    UserPreferenceRepository failing = mock(UserPreferenceRepository.class);
    when(failing.save(any(UserPreference.class))).thenThrow(new IllegalStateException("disk full"));
    User invited = invited();

    assertThatThrownBy(
            () -> newService(hasher, failing).accept(invited.getInvitationToken(), request()))
        .isInstanceOf(IllegalStateException.class);

    User stored = userRepository.findById(invited.getUserId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(UserStatus.INVITED);
    assertThat(stored.getPasswordHash()).isNull();
    assertThat(stored.getInvitationToken()).isEqualTo(invited.getInvitationToken());
    assertThat(events.events()).isEmpty();
  }
}
