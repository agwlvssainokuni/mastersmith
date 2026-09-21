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

package com.mastersmith.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** {@link ReloadFailureBackoff}の単体テスト(時計を差し替え、時間の経過を待たない)。 */
class ReloadFailureBackoffTest {

  private final AtomicLong nanos = new AtomicLong(1_000);
  private final ReloadFailureBackoff backoff =
      new ReloadFailureBackoff(Duration.ofSeconds(2), nanos::get);

  @Test
  void isNotSuppressingBeforeAnyFailure() {
    assertThat(backoff.isSuppressing()).isFalse();
  }

  @Test
  void suppressesWithinTheBackoffThenAllowsARetry() {
    backoff.recordFailure();
    nanos.addAndGet(Duration.ofSeconds(1).toNanos());
    assertThat(backoff.isSuppressing()).isTrue();

    nanos.addAndGet(Duration.ofSeconds(1).toNanos());
    assertThat(backoff.isSuppressing()).isFalse();
  }

  @Test
  void successClearsTheSuppression() {
    backoff.recordFailure();
    backoff.recordSuccess();

    assertThat(backoff.isSuppressing()).isFalse();
  }

  @Test
  void aNewFailureRestartsThePeriod() {
    backoff.recordFailure();
    nanos.addAndGet(Duration.ofSeconds(3).toNanos());
    backoff.recordFailure();
    nanos.addAndGet(Duration.ofSeconds(1).toNanos());

    assertThat(backoff.isSuppressing()).isTrue();
  }

  @Test
  void systemClockConstructorWorks() {
    ReloadFailureBackoff real = new ReloadFailureBackoff(Duration.ofHours(1));
    real.recordFailure();

    assertThat(real.isSuppressing()).isTrue();
  }
}
