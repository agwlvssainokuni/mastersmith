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

package com.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mastersmith.auth.dto.LoginResponse;
import com.mastersmith.auth.entity.AccountLoginState;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.entity.SessionStatus;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.LoginFailedException;
import com.mastersmith.auth.repository.AccountLoginStateRepository;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.token.AccessTokenClaims;
import com.mastersmith.auth.token.AccessTokenVerifier;
import com.mastersmith.auth.token.RefreshTokenHasher;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link
 * AuthenticationApplicationService}のログイン(W1)のテスト(実H2、C11はモック。BR5.1〜BR5.3・BR5.8・BR5.9・BR5.15、NFR1.3・NFR4.1・NFR4.2):
 * 失敗の応答が原因に かかわらず同一であること、実際の検証を行わない場合に{@code
 * dummyVerify}が呼ばれること、C11をトランザクションの外で呼ぶこと(順番待ちの間に接続プールの使用中の接続が0であること)、
 * ハッシュ計算の上限超過が実際・ダミーのどちらでも503になり枠が返ること、成功の更新とSessionの作成、DB障害で503になり補償が試みられること、予約後の想定外の例外では補償されないこと、
 * 同時の誤った試行でしきい値を超えて検証されないこと。
 */
class AuthenticationApplicationServiceTest extends AuthIntegrationTestBase {

  private static final String PASSWORD = "correct horse battery staple";

  @Autowired private AuthenticationApplicationService service;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private AccountLoginStateRepository stateRepository;
  @Autowired private AccessTokenVerifier accessTokenVerifier;
  @Autowired private RefreshTokenHasher refreshTokenHasher;
  @Autowired private DataSource dataSource;
  @Autowired private MeterRegistry meterRegistry;

  private static String newEmail() {
    return "auth-" + java.util.UUID.randomUUID() + "@example.test";
  }

  private UserAccount stubUser(String email, UserStatus status, String... roleIds) {
    UserAccount account =
        new UserAccount(AuthTestFactory.uniqueUserId(), null, status, List.of(roleIds));
    when(userAccountLookupApi.findByEmail(email.strip().toLowerCase(java.util.Locale.ROOT)))
        .thenReturn(Optional.of(account));
    return account;
  }

  private AccountLoginState state(String userId) {
    return stateRepository.findById(userId).orElseThrow();
  }

  private double counter(String name) {
    return meterRegistry.get(name).counter().count();
  }

  // ---- 成功 ----

  @Test
  void aSuccessfulLoginIssuesTokensAndCreatesAnActiveSessionWithTheHashOnly() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "role-a", "role-b");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD)).thenReturn(true);

    LoginResponse response = service.login(email, PASSWORD);

    AccessTokenClaims claims = accessTokenVerifier.verify(response.accessToken());
    assertThat(claims.sub()).isEqualTo(user.userId());
    Session session = sessionRepository.findById(claims.sid()).orElseThrow();
    assertThat(session.getUserId()).isEqualTo(user.userId());
    assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(session.getRefreshTokenHash())
        .isEqualTo(refreshTokenHasher.hash(response.refreshToken()));
    assertThat(session.getRefreshTokenHash()).isNotEqualTo(response.refreshToken());
    assertThat(session.getRefreshExpiresAt()).isEqualTo(BASE_TIME.plus(Duration.ofMinutes(30)));
    // 選択可能なロール(直接付与分とGroup経由分の和集合)を返す。複数ロールは、未選択(null)。
    assertThat(response.roles()).containsExactly("role-a", "role-b");
    assertThat(response.activeRoleId()).isNull();
    assertThat(session.getActiveRoleId()).isNull();
  }

  @Test
  void aUserWithExactlyOneRoleHasItSelectedAutomatically() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "only-role");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD)).thenReturn(true);

    LoginResponse response = service.login(email, PASSWORD);

    assertThat(response.activeRoleId()).isEqualTo("only-role");
    String sid = accessTokenVerifier.verify(response.accessToken()).sid();
    assertThat(sessionRepository.findById(sid).orElseThrow().getActiveRoleId())
        .isEqualTo("only-role");
  }

  @Test
  void aUserWithNoRoleLogsInWithoutAnActiveRole() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE);
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD)).thenReturn(true);

    LoginResponse response = service.login(email, PASSWORD);

    assertThat(response.roles()).isEmpty();
    assertThat(response.activeRoleId()).isNull();
  }

  @Test
  void theEmailIsTrimmedAndLowercasedBeforeTheLookup() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD)).thenReturn(true);

    service.login("  " + email.toUpperCase(java.util.Locale.ROOT) + "\t", PASSWORD);

    verify(userAccountLookupApi).findByEmail(email);
  }

  @Test
  void eachLoginCreatesAnIndependentSessionSoSeveralDevicesCanBeSignedInAtOnce() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD)).thenReturn(true);

    LoginResponse first = service.login(email, PASSWORD);
    LoginResponse second = service.login(email, PASSWORD);

    String firstSid = accessTokenVerifier.verify(first.accessToken()).sid();
    String secondSid = accessTokenVerifier.verify(second.accessToken()).sid();
    assertThat(firstSid).isNotEqualTo(secondSid);
    assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
    assertThat(sessionRepository.findById(firstSid).orElseThrow().getStatus())
        .isEqualTo(SessionStatus.ACTIVE);
    assertThat(sessionRepository.findById(secondSid).orElseThrow().getStatus())
        .isEqualTo(SessionStatus.ACTIVE);
  }

  @Test
  void aSuccessResetsTheFailureCountAndCreatesTheSessionTogether() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), "wrong")).thenReturn(false);
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD)).thenReturn(true);
    assertThatThrownBy(() -> service.login(email, "wrong"))
        .isInstanceOf(LoginFailedException.class);
    assertThatThrownBy(() -> service.login(email, "wrong"))
        .isInstanceOf(LoginFailedException.class);
    assertThat(state(user.userId()).getConsecutiveFailures()).isEqualTo(2);

    LoginResponse response = service.login(email, PASSWORD);

    assertThat(state(user.userId()).getConsecutiveFailures()).isZero();
    assertThat(sessionRepository.findById(accessTokenVerifier.verify(response.accessToken()).sid()))
        .isPresent();
  }

  // ---- 失敗の応答の一本化(BR5.2) ----

  record FailureCase(String label, boolean verifiesPassword) {
    @Override
    public String toString() {
      return label;
    }
  }

  static Stream<FailureCase> failureCauses() {
    return Stream.of(
        new FailureCase("登録されていないメールアドレス", false),
        new FailureCase("パスワードの誤り", true),
        new FailureCase("招待中(未受諾)", false),
        new FailureCase("無効化済み", false),
        new FailureCase("ロック中", false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("failureCauses")
  void everyCauseOfAFailureGivesTheSameResponseAndTheDummyVerificationRunsWhenNothingWasVerified(
      FailureCase failureCase) {
    String email = newEmail();
    String userId = null;
    switch (failureCase.label()) {
      case "パスワードの誤り" -> {
        userId = stubUser(email, UserStatus.ACTIVE, "r1").userId();
        when(userAccountLookupApi.verifyPasswordHash(userId, PASSWORD)).thenReturn(false);
      }
      case "招待中(未受諾)" -> userId = stubUser(email, UserStatus.INVITED).userId();
      case "無効化済み" -> userId = stubUser(email, UserStatus.DISABLED, "r1").userId();
      case "ロック中" -> {
        userId = stubUser(email, UserStatus.ACTIVE, "r1").userId();
        when(userAccountLookupApi.verifyPasswordHash(userId, PASSWORD)).thenReturn(false);
        for (int i = 0; i < 5; i++) {
          assertThatThrownBy(() -> service.login(email, PASSWORD))
              .isInstanceOf(LoginFailedException.class);
        }
        org.mockito.Mockito.clearInvocations(userAccountLookupApi);
        // ロック中は、正しいパスワードでも、同一の失敗になる。
        when(userAccountLookupApi.verifyPasswordHash(userId, PASSWORD)).thenReturn(true);
      }
      default -> {
        // 登録されていない: findByEmailは、モックの既定(null)を、空へ
        when(userAccountLookupApi.findByEmail(email)).thenReturn(Optional.empty());
      }
    }

    assertThatThrownBy(() -> service.login(email, PASSWORD))
        .isInstanceOf(LoginFailedException.class)
        .hasMessage("Login failed")
        .hasNoCause();

    if (failureCase.verifiesPassword()) {
      verify(userAccountLookupApi).verifyPasswordHash(userId, PASSWORD);
      verify(userAccountLookupApi, never()).dummyVerify(anyString());
    } else {
      // 実際のパスワードの検証を行わない場合でも、ユーザーを指定しないダミーの検証を行ってから、応答する。
      verify(userAccountLookupApi).dummyVerify(PASSWORD);
      verify(userAccountLookupApi, never()).verifyPasswordHash(anyString(), anyString());
    }
  }

  @Test
  void noFailureCountIsRecordedForAnUnregisteredInvitedOrDisabledUser() {
    String invitedEmail = newEmail();
    UserAccount invited = stubUser(invitedEmail, UserStatus.INVITED);
    String disabledEmail = newEmail();
    UserAccount disabled = stubUser(disabledEmail, UserStatus.DISABLED, "r1");
    String unknownEmail = newEmail();
    when(userAccountLookupApi.findByEmail(unknownEmail)).thenReturn(Optional.empty());

    for (String email : List.of(invitedEmail, disabledEmail, unknownEmail)) {
      assertThatThrownBy(() -> service.login(email, PASSWORD))
          .isInstanceOf(LoginFailedException.class);
    }

    // 存在の有無を外部に漏らさないため、また、それらは成功しえないため、記録を残さない。
    assertThat(stateRepository.findById(invited.userId())).isEmpty();
    assertThat(stateRepository.findById(disabled.userId())).isEmpty();
  }

  @Test
  void blankInputsFailWithoutCallingTheUserAccountApi() {
    assertThatThrownBy(() -> service.login(null, PASSWORD))
        .isInstanceOf(LoginFailedException.class);
    assertThatThrownBy(() -> service.login("  ", PASSWORD))
        .isInstanceOf(LoginFailedException.class);
    assertThatThrownBy(() -> service.login("a@example.test", null))
        .isInstanceOf(LoginFailedException.class);
    assertThatThrownBy(() -> service.login("a@example.test", ""))
        .isInstanceOf(LoginFailedException.class);

    verifyNoInteractions(userAccountLookupApi);
  }

  // ---- ロック ----

  @Test
  void afterTheThresholdOfWrongPasswordsTheAccountIsLockedUntilTheLockTimeHasPassed() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), "wrong")).thenReturn(false);
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD)).thenReturn(true);
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> service.login(email, "wrong"))
          .isInstanceOf(LoginFailedException.class);
    }

    // ロック中は、正しいパスワードでも、同一の失敗になる(検証もしない)。
    assertThatThrownBy(() -> service.login(email, PASSWORD))
        .isInstanceOf(LoginFailedException.class);
    verify(userAccountLookupApi, never()).verifyPasswordHash(user.userId(), PASSWORD);

    // 時計を進めると、自動的に解除される(NFR4.6)。
    clock.advance(Duration.ofMinutes(15));
    LoginResponse response = service.login(email, PASSWORD);

    assertThat(response.accessToken()).isNotBlank();
    assertThat(state(user.userId()).getConsecutiveFailures()).isZero();
  }

  // ---- C11をトランザクションの外で呼ぶ・接続を保持しない(NFR1.3・NFR4.1) ----

  @Test
  void theUserAccountApiIsCalledOutsideAnyTransactionAndWithoutHoldingAConnection() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    List<String> violations = new ArrayList<>();
    HikariDataSource hikari = (HikariDataSource) dataSource;
    org.mockito.stubbing.Answer<Object> observe =
        invocation -> {
          if (TransactionSynchronizationManager.isActualTransactionActive()) {
            violations.add(invocation.getMethod().getName() + ": transaction is active");
          }
          // ハッシュ計算の順番待ち(dummyVerify・verifyPasswordHash)の間、接続プールの使用中の接続が0(open-in-viewが無効であることの確認)
          if (hikari.getHikariPoolMXBean().getActiveConnections() != 0) {
            violations.add(invocation.getMethod().getName() + ": a connection is held");
          }
          return invocation.getMethod().getReturnType() == boolean.class ? Boolean.TRUE : null;
        };
    org.mockito.Mockito.doAnswer(observe)
        .when(userAccountLookupApi)
        .verifyPasswordHash(anyString(), anyString());
    org.mockito.Mockito.doAnswer(observe).when(userAccountLookupApi).dummyVerify(anyString());
    String disabledEmail = newEmail();
    stubUser(disabledEmail, UserStatus.DISABLED);

    service.login(email, PASSWORD); // 実際の検証
    assertThatThrownBy(() -> service.login(disabledEmail, PASSWORD)) // ダミーの検証
        .isInstanceOf(LoginFailedException.class);

    assertThat(violations).isEmpty();
    verify(userAccountLookupApi).verifyPasswordHash(user.userId(), PASSWORD);
    verify(userAccountLookupApi).dummyVerify(PASSWORD);
  }

  // ---- ハッシュ計算の上限超過(BR5.15) ----

  @Test
  void
      whenTheHashCapacityIsExceededDuringTheRealVerificationTheSlotIsGivenBackAndTheExceptionPropagates() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD))
        .thenThrow(new HashCapacityExceededException("capacity"));
    double before = counter("auth.login.hash.capacity.exceeded");

    assertThatThrownBy(() -> service.login(email, PASSWORD))
        .isInstanceOf(HashCapacityExceededException.class);

    // 失敗に数えない: 確保した枠を、条件付きの補償の更新で返す。
    assertThat(state(user.userId()).getConsecutiveFailures()).isZero();
    assertThat(counter("auth.login.hash.capacity.exceeded")).isEqualTo(before + 1);
  }

  @Test
  void whenTheHashCapacityIsExceededDuringTheDummyVerificationTheSameExceptionPropagates() {
    // 登録されていない・招待中・無効化済み・ロック中でも、上限を超えたら同じ503になる(存在・ロックの状態を推測されない)。
    String unknownEmail = newEmail();
    when(userAccountLookupApi.findByEmail(unknownEmail)).thenReturn(Optional.empty());
    String invitedEmail = newEmail();
    stubUser(invitedEmail, UserStatus.INVITED);
    org.mockito.Mockito.doThrow(new HashCapacityExceededException("capacity"))
        .when(userAccountLookupApi)
        .dummyVerify(anyString());
    double before = counter("auth.login.hash.capacity.exceeded");

    assertThatThrownBy(() -> service.login(unknownEmail, PASSWORD))
        .isInstanceOf(HashCapacityExceededException.class);
    assertThatThrownBy(() -> service.login(invitedEmail, PASSWORD))
        .isInstanceOf(HashCapacityExceededException.class);

    assertThat(counter("auth.login.hash.capacity.exceeded")).isEqualTo(before + 2);
  }

  @Test
  void aCapacityExceededOfTheAttemptThatReachedTheThresholdAlsoUnlocks() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), "wrong")).thenReturn(false);
    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> service.login(email, "wrong"))
          .isInstanceOf(LoginFailedException.class);
    }
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD))
        .thenThrow(new HashCapacityExceededException("capacity"));

    assertThatThrownBy(() -> service.login(email, PASSWORD))
        .isInstanceOf(HashCapacityExceededException.class);

    AccountLoginState compensated = state(user.userId());
    assertThat(compensated.getConsecutiveFailures()).isEqualTo(4);
    assertThat(compensated.getLockedUntil()).isNull();
  }

  // ---- 内部設定DBの障害(NFR4.2) ----

  @Test
  void aStorageFailureInTheUserAccountLookupBecomesAStorageUnavailableException() {
    String email = newEmail();
    when(userAccountLookupApi.findByEmail(email))
        .thenThrow(new DataAccessResourceFailureException("db down"));
    double before = counter("auth.db.unavailable");

    assertThatThrownBy(() -> service.login(email, PASSWORD))
        .isInstanceOf(AuthStorageUnavailableException.class);

    assertThat(counter("auth.db.unavailable")).isEqualTo(before + 1);
  }

  @Test
  void
      aStorageFailureDuringTheVerificationCompensatesTheSlotAndBecomesAStorageUnavailableException() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD))
        .thenThrow(new QueryTimeoutException("timeout"));

    assertThatThrownBy(() -> service.login(email, PASSWORD))
        .isInstanceOf(AuthStorageUnavailableException.class);

    assertThat(state(user.userId()).getConsecutiveFailures()).isZero();
  }

  @Test
  void anUnexpectedExceptionAfterTheReservationIsNotCompensatedAndTheSlotStaysCounted() {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), PASSWORD))
        .thenThrow(new IllegalStateException("bug"));

    assertThatThrownBy(() -> service.login(email, PASSWORD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("bug");

    // 安全側: 補償せず、枠を失敗として数えたままにする。
    assertThat(state(user.userId()).getConsecutiveFailures()).isEqualTo(1);
  }

  // ---- 同時実行(実H2、CIの必須の合格条件) ----

  @Test
  void
      manyConcurrentWrongAttemptsOnAFreshAccountNeverVerifyMoreThanTheThresholdAndNeverFailWithAnError()
          throws Exception {
    String email = newEmail();
    UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), "wrong")).thenReturn(false);
    int attempts = 12;
    ExecutorService executor = Executors.newFixedThreadPool(attempts);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Throwable>> outcomes = new ArrayList<>();
      for (int i = 0; i < attempts; i++) {
        outcomes.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    service.login(email, "wrong");
                    return null;
                  } catch (Throwable e) {
                    return e;
                  }
                }));
      }
      start.countDown();

      for (Future<Throwable> outcome : outcomes) {
        // 行の初回作成の同時実行による一意制約の違反は、利用者からは観測されない(500にも503にもならない)。
        assertThat(outcome.get(60, TimeUnit.SECONDS)).isInstanceOf(LoginFailedException.class);
      }
    } finally {
      executor.shutdownNow();
    }

    verify(userAccountLookupApi, times(5)).verifyPasswordHash(user.userId(), "wrong");
    AccountLoginState locked = state(user.userId());
    assertThat(locked.getConsecutiveFailures()).isEqualTo(5);
    assertThat(locked.getLockedUntil()).isNotNull();
    // ロック中の試行は、ダミーの検証を行う(12回のうち、検証できた5回を除く7回)。
    verify(userAccountLookupApi, times(7)).dummyVerify("wrong");
  }

  @Test
  void theFirstAttemptsOfDifferentAccountsRunConcurrentlyWithoutInterfering() throws Exception {
    int accounts = 6;
    ExecutorService executor = Executors.newFixedThreadPool(accounts);
    AtomicBoolean unexpected = new AtomicBoolean(false);
    try {
      List<Future<?>> futures = new ArrayList<>();
      CountDownLatch start = new CountDownLatch(1);
      for (int i = 0; i < accounts; i++) {
        String email = newEmail();
        UserAccount user = stubUser(email, UserStatus.ACTIVE, "r1");
        when(userAccountLookupApi.verifyPasswordHash(user.userId(), "wrong")).thenReturn(false);
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    service.login(email, "wrong");
                  } catch (LoginFailedException expected) {
                    assertThat(state(user.userId()).getConsecutiveFailures()).isEqualTo(1);
                    return null;
                  }
                  unexpected.set(true);
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(60, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(unexpected).isFalse();
  }
}
