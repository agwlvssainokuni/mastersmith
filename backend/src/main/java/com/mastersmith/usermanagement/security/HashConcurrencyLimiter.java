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

import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.config.UserManagementProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ハッシュ計算の同時実行数を制御する(performance-design.md NFR1.3、scalability-design.md NFR3.3)。
 *
 * <p>公平(fair)なセマフォで、許可数の既定はCPUコア数。許可の取得は最大待機時間(既定2秒)まで待ち、取れなければ{@link
 * HashCapacityExceededException}を投げる。許可は、例外の場合も必ず返す。
 *
 * <p><b>資源の取得順序(reliability-design.md)</b>: ハッシュ計算の許可を保持している間は、DB接続(トランザクション)を取らない。
 * 呼び出し側は、トランザクションの外側で{@link #runWithPermit(Supplier)}を使うこと。許可を保持したまま別の許可を取る(入れ子)ことは 行わない。
 */
@Component
public class HashConcurrencyLimiter {

  private static final Logger LOG = LoggerFactory.getLogger(HashConcurrencyLimiter.class);

  private final Semaphore semaphore;
  private final Duration waitTimeout;
  private final Counter rejectedCounter;

  @Autowired
  public HashConcurrencyLimiter(UserManagementProperties properties, MeterRegistry meterRegistry) {
    this(
        properties.hash().effectiveMaxConcurrent(), properties.hash().waitTimeout(), meterRegistry);
  }

  public HashConcurrencyLimiter(int permits, Duration waitTimeout, MeterRegistry meterRegistry) {
    this.semaphore = new Semaphore(permits, true);
    this.waitTimeout = waitTimeout;
    this.rejectedCounter =
        Counter.builder("user.password.hash.rejected")
            .description("同時計算の待機超過でハッシュ計算を拒否した回数(NFR1.3)")
            .register(meterRegistry);
  }

  /**
   * 許可を取得して処理を実行し、許可を必ず返す。
   *
   * @throws HashCapacityExceededException 待機の上限内に許可を取れなかった場合(または待機が中断された場合)
   */
  public <T> T runWithPermit(Supplier<T> action) {
    acquire();
    try {
      return action.get();
    } finally {
      semaphore.release();
    }
  }

  private void acquire() {
    try {
      if (semaphore.tryAcquire(waitTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
        return;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HashCapacityExceededException("Interrupted while waiting for a password hash slot");
    }
    rejectedCounter.increment();
    LOG.warn("Password hash capacity exceeded: waited {} ms", waitTimeout.toMillis());
    throw new HashCapacityExceededException("Password hash capacity exceeded");
  }

  /** 現在取得できる許可数(テスト・診断用)。 */
  public int availablePermits() {
    return semaphore.availablePermits();
  }
}
