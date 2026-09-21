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

import com.mastersmith.auth.entity.AccountLoginState;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.repository.AccountLoginStateRepository;
import com.mastersmith.auth.service.LoginAttemptGate.Reservation;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.testsupport.AuthTestProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link LoginAttemptGate}のテスト(実H2、BR5.3・NFR4.1・NFR4.6。<b>同時の予約・補償の並行実行は、CIの必須の合格条件</b>):
 * 同時の誤った試行が何件あっても、検証できる試行が
 * しきい値を超えないこと、しきい値到達と同時にロックが有効になること、ロック期間を延長しないこと、ロックの自動解除後の最初の予約で回数が0に戻ること、正しいパスワードの成功で
 * しきい値到達の試行でもロックが解けること、補償の更新、自己修復、時計を差し替えた境界。トランザクションの境界は、テストが、{@link TransactionTemplate}で、
 * 実際の呼び出し元({@code AuthenticationApplicationService})と同じ形で与える。
 */
class LoginAttemptGateTest extends AuthIntegrationTestBase {

  private static final Duration LOCK = Duration.ofMinutes(15);
  private static final int THRESHOLD = 5;

  @Autowired private LoginAttemptGate gate;
  @Autowired private AccountLoginStateRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;

  private TransactionTemplate tx;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    tx = new TransactionTemplate(transactionManager);
    executor = Executors.newFixedThreadPool(8);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private String newUser(int failures, Instant lockedUntil, long generation) {
    String userId = AuthTestFactory.uniqueUserId();
    jdbcTemplate.update(
        "INSERT INTO account_login_state (user_id, consecutive_failures, locked_until, generation)"
            + " VALUES (?, ?, ?, ?)",
        userId,
        failures,
        lockedUntil == null ? null : Timestamp.from(lockedUntil),
        generation);
    return userId;
  }

  private Reservation reserve(String userId) {
    return tx.execute(status -> gate.reserve(userId));
  }

  private AccountLoginState state(String userId) {
    return repository.findById(userId).orElseThrow();
  }

  // ---- 予約 ----

  @Test
  void theReservationThatReachesTheThresholdActivatesTheLockAtTheSameTime() {
    String userId = newUser(0, null, 0);
    double lockedBefore = counter("auth.account.locked");

    for (int attempt = 1; attempt < THRESHOLD; attempt++) {
      Reservation reservation = reserve(userId);
      assertThat(reservation.acquired()).isTrue();
      assertThat(reservation.lockedUntilSetByThis()).isNull();
    }
    Reservation reaching = reserve(userId);

    assertThat(reaching.acquired()).isTrue();
    assertThat(reaching.lockedUntilSetByThis()).isEqualTo(BASE_TIME.plus(LOCK));
    assertThat(state(userId).getConsecutiveFailures()).isEqualTo(THRESHOLD);
    assertThat(state(userId).getLockedUntil()).isEqualTo(BASE_TIME.plus(LOCK));
    // ロックの発生は、メトリクスで数える(検証の結果や、途中の中断を待たない)。
    assertThat(counter("auth.account.locked")).isEqualTo(lockedBefore + 1);
  }

  @Test
  void whileLockedNothingIsReservedNothingIsCountedAndTheLockIsNotExtended() {
    String userId = newUser(THRESHOLD - 1, null, 0);
    reserve(userId); // ロックが有効になる
    Instant lockedUntil = state(userId).getLockedUntil();

    clock.advance(Duration.ofMinutes(5));
    for (int i = 0; i < 3; i++) {
      assertThat(reserve(userId).acquired()).isFalse();
    }

    AccountLoginState unchanged = state(userId);
    assertThat(unchanged.getConsecutiveFailures()).isEqualTo(THRESHOLD);
    assertThat(unchanged.getLockedUntil()).isEqualTo(lockedUntil);
  }

  @Test
  void afterTheAutomaticUnlockTheFirstReservationResetsTheCountToZeroBeforeCounting() {
    String userId = newUser(THRESHOLD - 1, null, 0);
    reserve(userId);
    Instant lockedUntil = state(userId).getLockedUntil();
    long generation = state(userId).getGeneration();

    // ロックの解除予定日時の直前は、ロック中(NFR4.6: 時計を差し替えた境界)。
    clock.set(lockedUntil.minusMillis(1));
    assertThat(reserve(userId).acquired()).isFalse();
    // ちょうどの時刻は、解除済み。
    clock.set(lockedUntil);
    Reservation first = reserve(userId);

    assertThat(first.acquired()).isTrue();
    assertThat(first.generation()).isEqualTo(generation + 1);
    assertThat(first.lockedUntilSetByThis()).isNull();
    AccountLoginState reset = state(userId);
    assertThat(reset.getConsecutiveFailures()).isEqualTo(1);
    assertThat(reset.getLockedUntil()).isNull();
  }

  @Test
  void aSuccessfulLoginUnlocksEvenWhenItIsTheAttemptThatReachedTheThreshold() {
    String userId = newUser(THRESHOLD - 1, null, 0);
    Reservation reaching = reserve(userId);
    assertThat(reaching.lockedUntilSetByThis()).isNotNull();

    // 検証が成功した(正しいパスワード)。
    tx.executeWithoutResult(status -> gate.succeed(userId));

    AccountLoginState unlocked = state(userId);
    assertThat(unlocked.getConsecutiveFailures()).isZero();
    assertThat(unlocked.getLockedUntil()).isNull();
    assertThat(unlocked.getGeneration()).isEqualTo(reaching.generation() + 1);
    assertThat(reserve(userId).acquired()).isTrue();
  }

  @Test
  void aThresholdOfOneLocksOnTheFirstAttempt() {
    LoginAttemptGate strict =
        new LoginAttemptGate(
            repository,
            AuthTestProperties.withLock(1, LOCK),
            clock,
            new AuthMetrics(new SimpleMeterRegistry()),
            new AuthEventLogger());
    String userId = newUser(0, null, 0);

    Reservation reservation = tx.execute(status -> strict.reserve(userId));

    assertThat(reservation.acquired()).isTrue();
    assertThat(reservation.lockedUntilSetByThis()).isEqualTo(BASE_TIME.plus(LOCK));
    assertThat(tx.execute(status -> strict.reserve(userId)).acquired()).isFalse();
  }

  @Test
  void anUnexpectedStateAtOrAboveTheThresholdWithoutALockIsRepairedSoItNeverStaysLockedForever() {
    String userId = newUser(THRESHOLD, null, 0);

    assertThat(reserve(userId).acquired()).isFalse();
    assertThat(state(userId).getLockedUntil()).isEqualTo(BASE_TIME.plus(LOCK));

    // 自己修復で設定したロックも、時間の経過で、必ず解除される。
    clock.advance(LOCK);
    assertThat(reserve(userId).acquired()).isTrue();
  }

  @Test
  void aRowThatDoesNotExistYetIsCreatedByTheFirstReservation() {
    String userId = AuthTestFactory.uniqueUserId();

    Reservation reservation = reserve(userId);

    assertThat(reservation.acquired()).isTrue();
    assertThat(state(userId).getConsecutiveFailures()).isEqualTo(1);
  }

  // ---- 補償 ----

  @Test
  void compensationGivesTheSlotBackWhenTheGenerationIsTheSame() {
    String userId = newUser(0, null, 0);
    Reservation reservation = reserve(userId);
    assertThat(state(userId).getConsecutiveFailures()).isEqualTo(1);

    tx.executeWithoutResult(status -> gate.compensate(userId, reservation));

    assertThat(state(userId).getConsecutiveFailures()).isZero();
  }

  @Test
  void compensationOfTheReservationThatSetTheLockGivesTheSlotBackAndUnlocks() {
    String userId = newUser(THRESHOLD - 1, null, 0);
    Reservation reaching = reserve(userId);

    tx.executeWithoutResult(status -> gate.compensate(userId, reaching));

    AccountLoginState compensated = state(userId);
    assertThat(compensated.getConsecutiveFailures()).isEqualTo(THRESHOLD - 1);
    assertThat(compensated.getLockedUntil()).isNull();
  }

  @Test
  void compensationAfterAnotherAttemptSucceededDoesNothingAndNeverGoesNegative() {
    String userId = newUser(0, null, 0);
    Reservation reservation = reserve(userId);
    // 別の試行の成功によるリセット(世代+1)
    tx.executeWithoutResult(status -> gate.succeed(userId));

    tx.executeWithoutResult(status -> gate.compensate(userId, reservation));

    assertThat(state(userId).getConsecutiveFailures()).isZero();
  }

  @Test
  void compensationInTheNextGenerationDoesNotEraseTheNewGenerationsFailure() {
    String userId = newUser(THRESHOLD - 1, null, 0);
    Reservation old = reserve(userId); // ロックが有効(世代0)
    clock.advance(LOCK);
    Reservation next = reserve(userId); // ロックの解除後の最初の予約(世代1、回数1)
    assertThat(next.generation()).isEqualTo(old.generation() + 1);

    // 古い世代の予約の補償: 新しい世代の失敗を、数え損ねない。
    tx.executeWithoutResult(status -> gate.compensate(userId, old));

    assertThat(state(userId).getConsecutiveFailures()).isEqualTo(1);
  }

  @Test
  void compensatingAReservationThatWasNotAcquiredDoesNothing() {
    String userId = newUser(THRESHOLD - 1, null, 0);
    reserve(userId);

    tx.executeWithoutResult(status -> gate.compensate(userId, Reservation.NOT_ACQUIRED));

    assertThat(state(userId).getConsecutiveFailures()).isEqualTo(THRESHOLD);
  }

  // ---- 同時実行(実H2、CIの必須の合格条件) ----

  @Test
  void noMatterHowManyConcurrentWrongAttemptsAtMostTheThresholdCanBeVerified() throws Exception {
    String userId = newUser(0, null, 0);
    int attempts = 40;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Reservation>> results = new ArrayList<>();
    for (int i = 0; i < attempts; i++) {
      results.add(
          executor.submit(
              () -> {
                start.await();
                return reserve(userId);
              }));
    }
    start.countDown();

    long acquired = 0;
    for (Future<Reservation> result : results) {
      if (result.get(30, TimeUnit.SECONDS).acquired()) {
        acquired++;
      }
    }

    // 検証できる試行は、しきい値を超えない。ロックは、しきい値に達した予約と同時に有効になっている。
    assertThat(acquired).isEqualTo(THRESHOLD);
    AccountLoginState locked = state(userId);
    assertThat(locked.getConsecutiveFailures()).isEqualTo(THRESHOLD);
    assertThat(locked.getLockedUntil()).isEqualTo(BASE_TIME.plus(LOCK));
  }

  @Test
  void concurrentReservationsAndCompensationsNeverMakeTheCountNegativeNorExceedTheThreshold()
      throws Exception {
    String userId = newUser(0, null, 0);
    int attempts = 24;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    for (int i = 0; i < attempts; i++) {
      results.add(
          executor.submit(
              () -> {
                start.await();
                Reservation reservation = reserve(userId);
                if (reservation.acquired()) {
                  // 半数は、ハッシュ計算の上限超過を想定した補償で、枠を返す。
                  tx.executeWithoutResult(status -> gate.compensate(userId, reservation));
                }
                return reservation.acquired();
              }));
    }
    start.countDown();
    for (Future<Boolean> result : results) {
      result.get(30, TimeUnit.SECONDS);
    }

    AccountLoginState finalState = state(userId);
    assertThat(finalState.getConsecutiveFailures()).isBetween(0, THRESHOLD);
  }

  // ---- トランザクションへの参加 ----

  @Test
  void theGateOnlyJoinsATransactionAndFailsWhenCalledOutsideOne() {
    String userId = newUser(0, null, 0);

    assertThatThrownBy(() -> gate.reserve(userId))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> gate.succeed(userId))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> gate.compensate(userId, Reservation.NOT_ACQUIRED))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  private double counter(String name) {
    return meterRegistry.get(name).counter().count();
  }
}
