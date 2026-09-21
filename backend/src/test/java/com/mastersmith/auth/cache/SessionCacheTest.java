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

package com.mastersmith.auth.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.entity.SessionStatus;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.service.AuthExceptionTranslator;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SessionCache}のテスト(NFR3.2・NFR4.3):
 * 存在しないSessionを保持しないこと、TTLで解消すること、ヒット・ミスの統計、内部設定DBの障害で読み込みが失敗しても例外を保持しない
 * こと、更新のコミット後に無効化されること、<b>読み込みと無効化の競合(ストレステスト、実H2)で古い値が残らないこと</b>。
 */
class SessionCacheTest extends AuthIntegrationTestBase {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private SessionCache realCache;
  @Autowired private SessionRepository realRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private static Session session(String userId, String role) {
    return Session.issue(
        AuthTestFactory.newSessionId(),
        userId,
        role,
        AuthTestFactory.newHash(),
        NOW,
        NOW.plus(Duration.ofMinutes(30)));
  }

  private static AuthExceptionTranslator translator(SimpleMeterRegistry registry) {
    return new AuthExceptionTranslator(new AuthMetrics(registry), new AuthEventLogger());
  }

  // ---- 単体(モックのリポジトリ・差し替えたタイマー) ----

  @Test
  void aSessionThatDoesNotExistIsNeverCachedSoALaterCreationIsSeenImmediately() {
    SessionRepository repository = mock(SessionRepository.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SessionCache cache =
        new SessionCache(
            repository,
            10,
            Duration.ofSeconds(60),
            registry,
            translator(registry),
            Ticker.systemTicker());
    Session created = session("u1", "r1");
    when(repository.findById(created.getSessionId())).thenReturn(Optional.empty());

    assertThat(cache.find(created.getSessionId())).isEmpty();
    assertThat(cache.find(created.getSessionId())).isEmpty();
    when(repository.findById(created.getSessionId())).thenReturn(Optional.of(created));

    assertThat(cache.find(created.getSessionId())).isPresent();
    // 否定的な結果を保持しない: 存在しない間は、毎回、DBを引く。
    verify(repository, times(3)).findById(created.getSessionId());
  }

  @Test
  void anEntryExpiresAfterTheTtlAndIsReadAgainFromTheDatabase() {
    SessionRepository repository = mock(SessionRepository.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AtomicLong nanos = new AtomicLong();
    SessionCache cache =
        new SessionCache(
            repository, 10, Duration.ofSeconds(60), registry, translator(registry), nanos::get);
    Session stored = session("u1", "r1");
    when(repository.findById(stored.getSessionId())).thenReturn(Optional.of(stored));

    cache.find(stored.getSessionId());
    nanos.addAndGet(Duration.ofSeconds(59).toNanos());
    cache.find(stored.getSessionId());
    verify(repository, times(1)).findById(stored.getSessionId());

    // TTL(書き込みから60秒)の経過で、古い内容は解消する(安全網)。
    nanos.addAndGet(Duration.ofSeconds(2).toNanos());
    cache.find(stored.getSessionId());
    verify(repository, times(2)).findById(stored.getSessionId());
  }

  @Test
  void hitsAndMissesAreRecordedAsTheStandardCacheMetrics() {
    SessionRepository repository = mock(SessionRepository.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SessionCache cache =
        new SessionCache(
            repository,
            10,
            Duration.ofSeconds(60),
            registry,
            translator(registry),
            Ticker.systemTicker());
    Session stored = session("u1", "r1");
    when(repository.findById(stored.getSessionId())).thenReturn(Optional.of(stored));

    SessionCache.Lookup miss = cache.lookup(stored.getSessionId());
    SessionCache.Lookup hit = cache.lookup(stored.getSessionId());
    cache.lookup(stored.getSessionId());

    assertThat(miss.hit()).isFalse();
    assertThat(hit.hit()).isTrue();
    assertThat(
            registry
                .get("cache.gets")
                .tags("cache", SessionCache.CACHE_NAME, "result", "hit")
                .functionCounter()
                .count())
        .isEqualTo(2.0);
    assertThat(
            registry
                .get("cache.gets")
                .tags("cache", SessionCache.CACHE_NAME, "result", "miss")
                .functionCounter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void aFailedLoadBecauseOfADatabaseFailureIsNotRememberedAndTheNextRequestReadsAgain() {
    SessionRepository repository = mock(SessionRepository.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SessionCache cache =
        new SessionCache(
            repository,
            10,
            Duration.ofSeconds(60),
            registry,
            translator(registry),
            Ticker.systemTicker());
    Session stored = session("u1", "r1");
    when(repository.findById(stored.getSessionId()))
        .thenThrow(new QueryTimeoutException("db down"))
        .thenReturn(Optional.of(stored));

    assertThatThrownBy(() -> cache.find(stored.getSessionId()))
        .isInstanceOf(AuthStorageUnavailableException.class);

    assertThat(cache.find(stored.getSessionId())).isPresent();
  }

  @Test
  void theCacheHoldsOnlyTheSessionStateAndNeverTheHashOrAToken() {
    // SessionStateは、userId・activeRoleId・status・refreshExpiresAtだけを持つ(NFR2.7・NFR3.2)。
    assertThat(SessionState.class.getRecordComponents())
        .extracting(component -> component.getName())
        .containsExactly("userId", "activeRoleId", "status", "refreshExpiresAt");
  }

  @Test
  void theLeastRecentlyUsedEntriesAreEvictedBeyondTheMaximumSizeAndReadAgainOnDemand() {
    SessionRepository repository = mock(SessionRepository.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SessionCache cache =
        new SessionCache(
            repository,
            2,
            Duration.ofSeconds(60),
            registry,
            translator(registry),
            Ticker.systemTicker());
    List<Session> sessions = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      Session stored = session("u" + i, "r1");
      sessions.add(stored);
      when(repository.findById(stored.getSessionId())).thenReturn(Optional.of(stored));
      cache.find(stored.getSessionId());
    }

    // 追い出されたSessionも、次のリクエストで、DBから読み直せる(正しさは保たれる)。
    for (Session stored : sessions) {
      assertThat(cache.find(stored.getSessionId())).isPresent();
    }
  }

  // ---- 実H2: コミット後の無効化・競合 ----

  @Test
  void anUpdateIsSeenByTheNextRequestOnlyAfterTheInvalidationFollowingTheCommit() {
    Session stored = realRepository.saveAndFlush(session(AuthTestFactory.uniqueUserId(), "r1"));
    assertThat(realCache.find(stored.getSessionId()))
        .get()
        .extracting("activeRoleId")
        .isEqualTo("r1");

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> realRepository.updateActiveRole(stored.getSessionId(), "r2"));
    // コミット済みでも、無効化するまでは、キャッシュの値(TTLの安全網の範囲)。
    assertThat(realCache.find(stored.getSessionId()))
        .get()
        .extracting("activeRoleId")
        .isEqualTo("r1");

    realCache.invalidate(stored.getSessionId());
    assertThat(realCache.find(stored.getSessionId()))
        .get()
        .extracting("activeRoleId")
        .isEqualTo("r2");
    assertThat(realCache.find(stored.getSessionId()))
        .get()
        .extracting("status")
        .isEqualTo(SessionStatus.ACTIVE);
  }

  @Test
  void underAStressOfConcurrentLoadsAndInvalidationsAStaleValueNeverSurvivesTheInvalidation()
      throws Exception {
    Session stored = realRepository.saveAndFlush(session(AuthTestFactory.uniqueUserId(), "r0"));
    String sid = stored.getSessionId();
    int readers = 4;
    int updates = 150;
    ExecutorService executor = Executors.newFixedThreadPool(readers);
    AtomicBoolean running = new AtomicBoolean(true);
    List<Future<?>> readerFutures = new ArrayList<>();
    try {
      CountDownLatch started = new CountDownLatch(readers);
      for (int i = 0; i < readers; i++) {
        readerFutures.add(
            executor.submit(
                () -> {
                  started.countDown();
                  // 更新と同時に、読み込みを繰り返す(読み込みの途中に、更新のコミットと無効化が入る)。
                  while (running.get()) {
                    realCache.find(sid);
                    realCache.invalidate("some-other-session");
                  }
                }));
      }
      started.await();

      for (int i = 1; i <= updates; i++) {
        String role = "r" + i;
        new TransactionTemplate(transactionManager)
            .executeWithoutResult(status -> realRepository.updateActiveRole(sid, role));
        realCache.invalidate(sid); // コミットの後に無効化する(NFR4.3)
        // 無効化の後の読み取りは、必ず、新しい値になる(古い値が残らない)。
        assertThat(realCache.find(sid)).get().extracting("activeRoleId").isEqualTo(role);
      }
    } finally {
      running.set(false);
      for (Future<?> future : readerFutures) {
        future.get(30, TimeUnit.SECONDS);
      }
      executor.shutdownNow();
    }
  }
}
