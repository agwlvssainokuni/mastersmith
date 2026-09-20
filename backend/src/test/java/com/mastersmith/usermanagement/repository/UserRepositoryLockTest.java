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

package com.mastersmith.usermanagement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
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
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link UserRepository#findByIdForUpdate(String)}の行ロックの動作(reliability-design.md
 * NFR4.1)。コミットを伴う複数スレッドの検証のため、 テスト自体はトランザクションの外で実行し、作成したUserは後始末で削除する。ロック待ちの上限は設定値で短縮する。
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "mastersmith.users.db-lock-timeout=1s")
class UserRepositoryLockTest {

  private static final long AWAIT_SECONDS = 20;

  @Autowired private UserRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  private TransactionTemplate template;
  private ExecutorService executor;
  private String userId;

  @BeforeEach
  void setUp() {
    template = new TransactionTemplate(transactionManager);
    executor = Executors.newCachedThreadPool();
    User user =
        template.execute(
            status ->
                repository.save(
                    UserTestFactory.activeUser(UserTestFactory.uniqueEmail(), List.of("r1"))));
    userId = user.getUserId();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    template.executeWithoutResult(status -> repository.deleteById(userId));
  }

  @Test
  void aSecondLockerFailsAfterTheConfiguredTimeoutWhileTheRowIsLocked() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Future<?> holder =
        executor.submit(
            () ->
                template.executeWithoutResult(
                    status -> {
                      repository.findByIdForUpdate(userId);
                      locked.countDown();
                      awaitUninterruptibly(release);
                    }));
    assertThat(locked.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

    long start = System.nanoTime();
    assertThatThrownBy(
            () -> template.executeWithoutResult(status -> repository.findByIdForUpdate(userId)))
        .isInstanceOf(PessimisticLockingFailureException.class);
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    // 設定値(1秒)が効いていること: 既定の15秒まで待たずに失敗する。
    assertThat(elapsedMillis).isLessThan(10_000);
    release.countDown();
    holder.get(AWAIT_SECONDS, TimeUnit.SECONDS);
  }

  @Test
  void aSecondLockerSeesTheCommittedValueOfTheFirstAfterItReleasesTheLock() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    Future<?> first =
        executor.submit(
            () ->
                template.executeWithoutResult(
                    status -> {
                      User user = repository.findByIdForUpdate(userId).orElseThrow();
                      locked.countDown();
                      awaitUninterruptibly(secondStarted);
                      user.setName("先行の更新");
                      repository.save(user);
                    }));
    assertThat(locked.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

    Future<String> second =
        executor.submit(
            () ->
                template.execute(
                    status -> {
                      secondStarted.countDown();
                      return repository.findByIdForUpdate(userId).orElseThrow().getName();
                    }));

    first.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    assertThat(second.get(AWAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("先行の更新");
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await(AWAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
