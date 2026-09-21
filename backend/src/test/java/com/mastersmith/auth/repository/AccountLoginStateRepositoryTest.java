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

package com.mastersmith.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.auth.entity.AccountLoginState;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link AccountLoginStateRepository}の、予約型の原子的な更新のSQLのテスト(実H2、BR5.3・reliability-design.md NFR4.1):
 * 予約(しきい値未満・到達・ロック中は確保できない・
 * ロックの自動解除後の最初の予約で回数が0に戻り世代が進む)、SET句の各式が更新前の値で評価されること(H2の動作への依存、CIで確認)、補償の更新(世代が同じ場合だけ枠を
 * 返す・この予約が設定したロックだけを解く)、自己修復、行がない状態での初回の予約(一意制約違反)。
 */
@DataJpaTest
class AccountLoginStateRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Duration LOCK = Duration.ofMinutes(15);
  private static final int THRESHOLD = 5;

  @Autowired private AccountLoginStateRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private int reserve(String userId, Instant now) {
    return repository.reserve(userId, now, now.plus(LOCK), THRESHOLD);
  }

  private AccountLoginState state(String userId) {
    return repository.findById(userId).orElseThrow();
  }

  private String newRow(int failures, Instant lockedUntil, long generation) {
    String userId = AuthTestFactory.uniqueUserId();
    jdbcTemplate.update(
        "INSERT INTO account_login_state (user_id, consecutive_failures, locked_until, generation)"
            + " VALUES (?, ?, ?, ?)",
        userId,
        failures,
        lockedUntil == null ? null : java.sql.Timestamp.from(lockedUntil),
        generation);
    return userId;
  }

  @Test
  void reserveDoesNothingWhenTheRowDoesNotExist() {
    assertThat(reserve(AuthTestFactory.uniqueUserId(), NOW)).isZero();
  }

  @Test
  void theFirstAttemptCreatesTheRowWithOneFailureAndNoLock() {
    String userId = AuthTestFactory.uniqueUserId();

    repository.insertCounted(userId);

    AccountLoginState created = state(userId);
    assertThat(created.getConsecutiveFailures()).isEqualTo(1);
    assertThat(created.getLockedUntil()).isNull();
    assertThat(created.getGeneration()).isZero();
  }

  @Test
  void aSecondCreationOfTheSameRowViolatesThePrimaryKey() {
    String userId = AuthTestFactory.uniqueUserId();
    repository.insertCounted(userId);

    assertThatThrownBy(() -> repository.insertCounted(userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void eachReservationBelowTheThresholdCountsOneFailureWithoutLocking() {
    String userId = newRow(1, null, 0);

    for (int expected = 2; expected < THRESHOLD; expected++) {
      assertThat(reserve(userId, NOW)).isEqualTo(1);
      assertThat(state(userId).getConsecutiveFailures()).isEqualTo(expected);
      assertThat(state(userId).getLockedUntil()).isNull();
    }
  }

  @Test
  void theReservationThatReachesTheThresholdLocksInTheSameUpdate() {
    String userId = newRow(THRESHOLD - 1, null, 3);

    assertThat(reserve(userId, NOW)).isEqualTo(1);

    AccountLoginState locked = state(userId);
    assertThat(locked.getConsecutiveFailures()).isEqualTo(THRESHOLD);
    assertThat(locked.getLockedUntil()).isEqualTo(NOW.plus(LOCK));
    assertThat(locked.getGeneration()).isEqualTo(3);
  }

  @Test
  void whileLockedNoSlotIsGrantedTheCountIsNotIncrementedAndTheLockIsNotExtended() {
    Instant lockedUntil = NOW.plus(LOCK);
    String userId = newRow(THRESHOLD, lockedUntil, 7);

    assertThat(reserve(userId, NOW.plusSeconds(1))).isZero();
    assertThat(reserve(userId, lockedUntil.minusMillis(1))).isZero();

    AccountLoginState unchanged = state(userId);
    assertThat(unchanged.getConsecutiveFailures()).isEqualTo(THRESHOLD);
    assertThat(unchanged.getLockedUntil()).isEqualTo(lockedUntil);
    assertThat(unchanged.getGeneration()).isEqualTo(7);
  }

  @Test
  void afterTheAutomaticUnlockTheFirstReservationResetsTheCountToOneAndAdvancesTheGeneration() {
    Instant lockedUntil = NOW.plus(LOCK);
    String userId = newRow(THRESHOLD, lockedUntil, 7);

    // lockedUntilちょうどの時刻は、ロックの解除済み。
    assertThat(reserve(userId, lockedUntil)).isEqualTo(1);

    AccountLoginState reset = state(userId);
    assertThat(reset.getConsecutiveFailures()).isEqualTo(1);
    assertThat(reset.getLockedUntil()).isNull();
    assertThat(reset.getGeneration()).isEqualTo(8);
  }

  @Test
  void aThresholdOfOneLocksOnTheVeryFirstReservationAfterAnUnlock() {
    Instant lockedUntil = NOW.plus(LOCK);
    String userId = newRow(1, lockedUntil, 0);
    Instant later = lockedUntil.plusSeconds(1);

    assertThat(repository.reserve(userId, later, later.plus(LOCK), 1)).isEqualTo(1);

    AccountLoginState relocked = state(userId);
    assertThat(relocked.getConsecutiveFailures()).isEqualTo(1);
    assertThat(relocked.getLockedUntil()).isEqualTo(later.plus(LOCK));
    assertThat(relocked.getGeneration()).isEqualTo(1);
  }

  @Test
  void anUnexpectedStateWithoutALockAtOrAboveTheThresholdIsNotReservedAndIsRepaired() {
    String userId = newRow(THRESHOLD, null, 2);

    assertThat(reserve(userId, NOW)).isZero();
    assertThat(repository.repair(userId, NOW.plus(LOCK), THRESHOLD)).isEqualTo(1);

    assertThat(state(userId).getLockedUntil()).isEqualTo(NOW.plus(LOCK));
    // 修復済みの行は、対象にならない。
    assertThat(repository.repair(userId, NOW.plus(LOCK), THRESHOLD)).isZero();
  }

  @Test
  void compensationWithoutALockOnlyGivesBackTheCountWhenTheGenerationMatches() {
    String userId = newRow(3, null, 4);

    assertThat(repository.compensateCount(userId, 99)).isZero();
    assertThat(state(userId).getConsecutiveFailures()).isEqualTo(3);

    assertThat(repository.compensateCount(userId, 4)).isEqualTo(1);
    assertThat(state(userId).getConsecutiveFailures()).isEqualTo(2);
  }

  @Test
  void compensationNeverMakesTheCountNegative() {
    String userId = newRow(0, null, 4);

    assertThat(repository.compensateCount(userId, 4)).isZero();
    assertThat(repository.compensateWithLock(userId, 4, NOW.plus(LOCK))).isZero();

    assertThat(state(userId).getConsecutiveFailures()).isZero();
  }

  @Test
  void compensationWithALockUnlocksOnlyTheLockThisReservationSet() {
    Instant mine = NOW.plus(LOCK);
    String userId = newRow(THRESHOLD, mine, 4);

    // 別の予約が設定したロック(値が異なる)は、解かない(回数だけを戻す)。
    assertThat(repository.compensateWithLock(userId, 4, mine.plusSeconds(1))).isEqualTo(1);
    AccountLoginState other = state(userId);
    assertThat(other.getConsecutiveFailures()).isEqualTo(THRESHOLD - 1);
    assertThat(other.getLockedUntil()).isEqualTo(mine);

    // この予約が設定したロックは、解く。
    String userId2 = newRow(THRESHOLD, mine, 4);
    assertThat(repository.compensateWithLock(userId2, 4, mine)).isEqualTo(1);
    AccountLoginState unlocked = state(userId2);
    assertThat(unlocked.getConsecutiveFailures()).isEqualTo(THRESHOLD - 1);
    assertThat(unlocked.getLockedUntil()).isNull();
  }

  @Test
  void compensationAfterAResetOfTheGenerationDoesNothing() {
    // 別の試行の成功によるリセット(世代+1)をまたいで、別の世代の回数を、誤って戻さない。
    String userId = newRow(THRESHOLD, NOW.plus(LOCK), 4);
    repository.reset(userId);

    assertThat(repository.compensateWithLock(userId, 4, NOW.plus(LOCK))).isZero();
    assertThat(repository.compensateCount(userId, 4)).isZero();
    assertThat(state(userId).getConsecutiveFailures()).isZero();
  }

  @Test
  void aSuccessResetsTheCountUnlocksAndAdvancesTheGeneration() {
    String userId = newRow(THRESHOLD, NOW.plus(LOCK), 4);

    assertThat(repository.reset(userId)).isEqualTo(1);

    AccountLoginState reset = state(userId);
    assertThat(reset.getConsecutiveFailures()).isZero();
    assertThat(reset.getLockedUntil()).isNull();
    assertThat(reset.getGeneration()).isEqualTo(5);
    assertThat(repository.reset("no-such-user")).isZero();
  }
}
