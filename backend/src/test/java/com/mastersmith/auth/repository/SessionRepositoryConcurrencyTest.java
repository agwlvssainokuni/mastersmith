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

import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.testsupport.AuthTestFactory;
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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 同時のローテーションで、1件だけが成功すること(reliability-design.md NFR4.1、CIの必須の合格条件。実H2)。コミットを伴う複数スレッドの検証のため、テスト自体は
 * トランザクションの外で実行し、作成したSessionは後始末で削除する。開始は{@link CountDownLatch}で揃える(固定の{@code sleep}に依存しない)。
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SessionRepositoryConcurrencyTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final int THREADS = 8;

  @Autowired private SessionRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  private TransactionTemplate template;
  private ExecutorService executor;
  private String sessionId;

  @BeforeEach
  void setUp() {
    template = new TransactionTemplate(transactionManager);
    executor = Executors.newFixedThreadPool(THREADS);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    if (sessionId != null) {
      template.executeWithoutResult(status -> repository.deleteById(sessionId));
    }
  }

  @Test
  void onlyOneOfManyConcurrentRotationsOfTheSameTokenSucceeds() throws Exception {
    String oldHash = AuthTestFactory.newHash();
    Session session =
        template.execute(
            status ->
                repository.save(
                    AuthTestFactory.session(
                        AuthTestFactory.uniqueUserId(),
                        "r1",
                        oldHash,
                        NOW,
                        Duration.ofMinutes(30))));
    sessionId = session.getSessionId();
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      String newHash = AuthTestFactory.newHash();
      results.add(
          executor.submit(
              () -> {
                start.await();
                return template.execute(
                    status ->
                        repository.rotateWithRole(
                            sessionId,
                            oldHash,
                            newHash,
                            NOW.plusSeconds(1),
                            NOW.plus(Duration.ofMinutes(30)),
                            "r1",
                            "r1"));
              }));
    }
    start.countDown();

    int succeeded = 0;
    for (Future<Integer> result : results) {
      succeeded += result.get(20, TimeUnit.SECONDS);
    }

    assertThat(succeeded).isEqualTo(1);
    Session loaded = repository.findById(sessionId).orElseThrow();
    assertThat(loaded.getPreviousRefreshTokenHash()).isEqualTo(oldHash);
  }
}
