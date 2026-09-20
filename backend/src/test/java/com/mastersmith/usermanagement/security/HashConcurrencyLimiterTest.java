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

import com.mastersmith.usermanagement.HashCapacityExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link HashConcurrencyLimiter}: 許可数の上限・待機超過の例外・許可の返却・超過カウンタ(NFR1.3)。 */
class HashConcurrencyLimiterTest {

  private static final long AWAIT_SECONDS = 20;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ExecutorService executor = Executors.newCachedThreadPool();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private double rejectedCount() {
    return meterRegistry.get("user.password.hash.rejected").counter().count();
  }

  /** 許可を1つ取得したまま、releaseされるまで保持するタスクを起動し、取得済みになるまで待つ。 */
  private Future<String> holdPermit(
      HashConcurrencyLimiter limiter, CountDownLatch release, CountDownLatch acquired) {
    Future<String> holder =
        executor.submit(
            () ->
                limiter.runWithPermit(
                    () -> {
                      acquired.countDown();
                      try {
                        release.await(AWAIT_SECONDS, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return "done";
                    }));
    return holder;
  }

  @Test
  void runsTheActionAndReturnsItsResultAndTheImmediatePermit() {
    HashConcurrencyLimiter limiter =
        new HashConcurrencyLimiter(2, Duration.ofMillis(200), meterRegistry);

    String result = limiter.runWithPermit(() -> "result");

    assertThat(result).isEqualTo("result");
    assertThat(limiter.availablePermits()).isEqualTo(2);
    assertThat(rejectedCount()).isZero();
  }

  @Test
  void rejectsWhenAllPermitsAreHeldAndCountsTheRejection() throws Exception {
    HashConcurrencyLimiter limiter =
        new HashConcurrencyLimiter(2, Duration.ofMillis(200), meterRegistry);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch acquired = new CountDownLatch(2);
    Future<String> first = holdPermit(limiter, release, acquired);
    Future<String> second = holdPermit(limiter, release, acquired);
    assertThat(acquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> limiter.runWithPermit(() -> "never"))
        .isInstanceOf(HashCapacityExceededException.class);

    assertThat(rejectedCount()).isEqualTo(1.0);
    release.countDown();
    assertThat(first.get(AWAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("done");
    assertThat(second.get(AWAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("done");
    assertThat(limiter.availablePermits()).isEqualTo(2);
  }

  @Test
  void neverRunsMoreActionsConcurrentlyThanThePermits() throws Exception {
    int permits = 3;
    HashConcurrencyLimiter limiter =
        new HashConcurrencyLimiter(permits, Duration.ofSeconds(20), meterRegistry);
    java.util.concurrent.atomic.AtomicInteger running =
        new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicInteger maxObserved =
        new java.util.concurrent.atomic.AtomicInteger();

    java.util.List<Future<Integer>> futures = new java.util.ArrayList<>();
    for (int i = 0; i < 12; i++) {
      futures.add(
          executor.submit(
              () ->
                  limiter.runWithPermit(
                      () -> {
                        int now = running.incrementAndGet();
                        maxObserved.accumulateAndGet(now, Math::max);
                        Thread.yield();
                        running.decrementAndGet();
                        return now;
                      })));
    }
    for (Future<Integer> f : futures) {
      f.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    }

    assertThat(maxObserved.get()).isLessThanOrEqualTo(permits);
    assertThat(limiter.availablePermits()).isEqualTo(permits);
  }

  @Test
  void returnsThePermitWhenTheActionThrows() {
    HashConcurrencyLimiter limiter =
        new HashConcurrencyLimiter(1, Duration.ofMillis(200), meterRegistry);

    assertThatThrownBy(
            () ->
                limiter.runWithPermit(
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(limiter.availablePermits()).isEqualTo(1);
    assertThat(limiter.runWithPermit(() -> "ok")).isEqualTo("ok");
  }

  @Test
  void treatsAnInterruptedWaitAsCapacityExceededAndKeepsTheInterruptFlag() throws Exception {
    HashConcurrencyLimiter limiter =
        new HashConcurrencyLimiter(1, Duration.ofSeconds(20), meterRegistry);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch acquired = new CountDownLatch(1);
    Future<String> holder = holdPermit(limiter, release, acquired);
    assertThat(acquired.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> limiter.runWithPermit(() -> "never"))
          .isInstanceOf(HashCapacityExceededException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted(); // 後続のテストのために割り込みフラグを消す。
    }
    release.countDown();
    holder.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    assertThat(limiter.availablePermits()).isEqualTo(1);
  }
}
