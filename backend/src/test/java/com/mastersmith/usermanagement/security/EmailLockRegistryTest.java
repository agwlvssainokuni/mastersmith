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

package com.mastersmith.usermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.exception.EmailLockTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link EmailLockRegistry}: 同一emailの直列化・別emailの非干渉・エントリの除去・待機超過(reliability-design.md NFR4.2)。 */
class EmailLockRegistryTest {

  private static final long AWAIT_SECONDS = 20;

  private final ExecutorService executor = Executors.newCachedThreadPool();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void serializesTheSameEmail() throws Exception {
    EmailLockRegistry registry = new EmailLockRegistry(Duration.ofSeconds(AWAIT_SECONDS));
    List<String> events = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch firstHolds = new CountDownLatch(1);
    CountDownLatch secondAttempting = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);

    Future<?> first =
        executor.submit(
            () -> {
              try (EmailLockRegistry.Held held = registry.acquire("a@example.test")) {
                events.add("first-acquired");
                firstHolds.countDown();
                secondAttempting.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                releaseFirst.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                events.add("first-releasing");
              }
              return null;
            });
    assertThat(firstHolds.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    Future<?> second =
        executor.submit(
            () -> {
              secondAttempting.countDown();
              try (EmailLockRegistry.Held held = registry.acquire("a@example.test")) {
                events.add("second-acquired");
              }
              return null;
            });
    releaseFirst.countDown();

    first.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    second.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    assertThat(events).containsExactly("first-acquired", "first-releasing", "second-acquired");
    assertThat(registry.size()).isZero();
  }

  @Test
  void doesNotSerializeDifferentEmails() {
    EmailLockRegistry registry = new EmailLockRegistry(Duration.ofMillis(100));

    try (EmailLockRegistry.Held a = registry.acquire("a@example.test");
        EmailLockRegistry.Held b = registry.acquire("b@example.test")) {
      // 同じスレッドで、別のemailを同時に保持できる(ストライプ方式では衝突しうる)。
      assertThat(registry.size()).isEqualTo(2);
    }
    assertThat(registry.size()).isZero();
  }

  @Test
  void removesTheEntryWhenNobodyUsesTheEmailAnymore() {
    EmailLockRegistry registry = new EmailLockRegistry(Duration.ofMillis(100));

    for (int i = 0; i < 100; i++) {
      registry.acquire("user-" + i + "@example.test").close();
    }

    assertThat(registry.size()).isZero();
  }

  @Test
  void failsAfterTheWaitTimeoutAndKeepsTheHoldersEntryUntilItReleases() throws Exception {
    EmailLockRegistry registry = new EmailLockRegistry(Duration.ofMillis(150));
    EmailLockRegistry.Held holder = registry.acquire("a@example.test");

    Future<Throwable> waiter =
        executor.submit(
            () -> {
              try {
                registry.acquire("a@example.test");
                return null;
              } catch (EmailLockTimeoutException e) {
                return e;
              }
            });

    assertThat(waiter.get(AWAIT_SECONDS, TimeUnit.SECONDS))
        .isInstanceOf(EmailLockTimeoutException.class);
    // 待機に失敗した側の参照は返され、保持者のエントリだけが残る。
    assertThat(registry.size()).isEqualTo(1);
    holder.close();
    assertThat(registry.size()).isZero();
  }

  @Test
  void closeIsIdempotent() {
    EmailLockRegistry registry = new EmailLockRegistry(Duration.ofMillis(100));
    EmailLockRegistry.Held held = registry.acquire("a@example.test");

    held.close();
    held.close();

    // 二重のcloseが、他の保持者の参照を減らさない。
    try (EmailLockRegistry.Held again = registry.acquire("a@example.test")) {
      assertThat(registry.size()).isEqualTo(1);
    }
    assertThat(registry.size()).isZero();
  }

  @Test
  void theLockIsReusableAfterAWaiterTimedOut() throws Exception {
    EmailLockRegistry registry = new EmailLockRegistry(Duration.ofMillis(100));
    EmailLockRegistry.Held holder = registry.acquire("a@example.test");
    Future<?> failing =
        executor.submit(
            () ->
                assertThatThrownBy(() -> registry.acquire("a@example.test"))
                    .isInstanceOf(EmailLockTimeoutException.class));
    failing.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    holder.close();

    try (EmailLockRegistry.Held reused = registry.acquire("a@example.test")) {
      assertThat(registry.size()).isEqualTo(1);
    }
    assertThat(registry.size()).isZero();
  }

  @Test
  void manyThreadsOnTheSameEmailNeverOverlapAndLeaveNoEntries() throws Exception {
    EmailLockRegistry registry = new EmailLockRegistry(Duration.ofSeconds(AWAIT_SECONDS));
    java.util.concurrent.atomic.AtomicInteger inside =
        new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicInteger maxInside =
        new java.util.concurrent.atomic.AtomicInteger();
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      futures.add(
          executor.submit(
              () -> {
                try (EmailLockRegistry.Held held = registry.acquire("hot@example.test")) {
                  int now = inside.incrementAndGet();
                  maxInside.accumulateAndGet(now, Math::max);
                  Thread.yield();
                  inside.decrementAndGet();
                }
              }));
    }
    for (Future<?> f : futures) {
      f.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    }

    assertThat(maxInside.get()).isEqualTo(1);
    assertThat(registry.size()).isZero();
  }
}
