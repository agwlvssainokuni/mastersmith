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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.auth.config.AuthProperties.SessionSettings;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthObservations;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.testsupport.AuthTestProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SessionCleanupJob}のテスト(実H2、NFR4.5):
 * 有効期限から保持日数を過ぎたSession(revokedを含む)だけが削除され、有効なSessionは削除されないこと、1バッチの行数の上限のもとで
 * 複数回に分けて削除されること、1回の実行のバッチ数の上限、失敗が認証に影響しない(例外を外へ出さない)こと、実行中は次を始めないこと。
 */
class SessionCleanupJobTest extends AuthIntegrationTestBase {

  private static final Duration RETENTION = Duration.ofDays(7);

  @Autowired private SessionCleanupJob job;
  @Autowired private SessionRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  /** リフレッシュの有効期限が{@code expiresAt}のSession。 */
  private Session saved(Instant expiresAt, boolean revoked) {
    Session session =
        repository.saveAndFlush(
            Session.issue(
                AuthTestFactory.newSessionId(),
                AuthTestFactory.uniqueUserId(),
                null,
                AuthTestFactory.newHash(),
                expiresAt.minus(Duration.ofMinutes(30)),
                expiresAt));
    if (revoked) {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(status -> repository.revoke(session.getSessionId()));
    }
    return session;
  }

  private SessionCleanupJob smallBatchJob(int batchSize, int maxBatches, SessionRepository repo) {
    return new SessionCleanupJob(
        repo,
        AuthTestProperties.withSession(
            new SessionSettings(
                RETENTION,
                Duration.ofDays(1),
                Duration.ofMinutes(10),
                batchSize,
                maxBatches,
                false)),
        clock,
        new AuthEventLogger(),
        AuthObservations.NOOP,
        transactionManager);
  }

  @Test
  void onlySessionsPastTheRetentionAreDeletedIncludingRevokedOnesAndActiveOnesAreKept() {
    Instant now = clock.instant().plus(Duration.ofDays(30));
    clock.set(now);
    Session longExpired = saved(now.minus(RETENTION).minusSeconds(1), false);
    Session longExpiredRevoked = saved(now.minus(RETENTION).minus(Duration.ofDays(3)), true);
    // 保持日数の境界: 有効期限が、ちょうどcutoffのSessionは、削除しない(cutoffより前だけ)。
    Session atBoundary = saved(now.minus(RETENTION), false);
    Session expiredWithinRetention = saved(now.minus(Duration.ofDays(1)), false);
    Session revokedWithinRetention = saved(now.plus(Duration.ofMinutes(10)), true);
    Session stillValid = saved(now.plus(Duration.ofMinutes(20)), false);

    long deleted = job.runOnce();

    assertThat(deleted).isGreaterThanOrEqualTo(2);
    assertThat(repository.findById(longExpired.getSessionId())).isEmpty();
    assertThat(repository.findById(longExpiredRevoked.getSessionId())).isEmpty();
    assertThat(repository.findById(atBoundary.getSessionId())).isPresent();
    assertThat(repository.findById(expiredWithinRetention.getSessionId())).isPresent();
    assertThat(repository.findById(revokedWithinRetention.getSessionId())).isPresent();
    // 有効なSessionは、削除されない。
    assertThat(repository.findById(stillValid.getSessionId())).isPresent();
  }

  @Test
  void manyExpiredSessionsAreDeletedInSeveralBatchesOfTheConfiguredSize() {
    Instant now = clock.instant().plus(Duration.ofDays(60));
    clock.set(now);
    // 他のテストが残した行を、先に片付ける(このテストの件数の検証を、正確にするため)。
    smallBatchJob(1000, 100, repository).runOnce();
    List<Session> expired = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      expired.add(saved(now.minus(RETENTION).minus(Duration.ofHours(i + 1)), false));
    }
    Session valid = saved(now.plus(Duration.ofMinutes(5)), false);

    long deleted = smallBatchJob(3, 100, repository).runOnce();

    assertThat(deleted).isEqualTo(10);
    for (Session session : expired) {
      assertThat(repository.findById(session.getSessionId())).isEmpty();
    }
    assertThat(repository.findById(valid.getSessionId())).isPresent();
  }

  @Test
  void aRunDeletesAtMostTheMaximumNumberOfBatchesAndTheRestIsLeftForTheNextRun() {
    Instant now = clock.instant().plus(Duration.ofDays(90));
    clock.set(now);
    smallBatchJob(1000, 100, repository).runOnce();
    for (int i = 0; i < 7; i++) {
      saved(now.minus(RETENTION).minus(Duration.ofHours(i + 1)), false);
    }
    SessionCleanupJob limited = smallBatchJob(2, 2, repository);

    assertThat(limited.runOnce()).isEqualTo(4); // 2バッチ × 2行(残り3行は、次の実行へ)
    assertThat(limited.runOnce()).isEqualTo(3); // 残りの3行(2行 + 1行)
    assertThat(limited.runOnce()).isZero();
  }

  @Test
  void aFailureDoesNotEscapeTheJobSoAuthenticationIsNeverAffectedAndTheNextRunRetries() {
    SessionRepository failing = mock(SessionRepository.class);
    when(failing.findExpiredIds(any(), any())).thenThrow(new QueryTimeoutException("db down"));
    SessionCleanupJob failingJob = smallBatchJob(1000, 100, failing);

    long deleted = failingJob.runOnce();

    assertThat(deleted).isZero();
    // 次の実行で、再試行できる(実行中のフラグが残らない)。
    assertThat(failingJob.runOnce()).isZero();
  }

  @Test
  void aSecondRunDoesNotStartWhileTheFirstIsStillRunning() throws Exception {
    SessionRepository blocking = mock(SessionRepository.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(blocking.findExpiredIds(any(), any()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              release.await(30, TimeUnit.SECONDS);
              return List.of();
            });
    SessionCleanupJob blockingJob = smallBatchJob(1000, 100, blocking);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Long> first = executor.submit(blockingJob::runOnce);
      assertThat(entered.await(30, TimeUnit.SECONDS)).isTrue();

      // 実行中は、次を始めない(何もせずに戻る)。
      assertThat(blockingJob.runOnce()).isZero();
      org.mockito.Mockito.verify(blocking, org.mockito.Mockito.times(1))
          .findExpiredIds(any(), any());

      release.countDown();
      first.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void theScheduledEntryPointRunsTheSameCleanupWithoutThrowing() {
    Instant now = clock.instant().plus(Duration.ofDays(120));
    clock.set(now);
    Session expired = saved(now.minus(RETENTION).minus(Duration.ofDays(1)), false);

    job.scheduledRun();

    assertThat(repository.findById(expired.getSessionId())).isEmpty();
  }
}
