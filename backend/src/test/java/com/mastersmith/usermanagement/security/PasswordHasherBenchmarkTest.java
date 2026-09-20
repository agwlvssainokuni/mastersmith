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

import com.mastersmith.usermanagement.config.UserManagementProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * ハッシュ計測の単体ベンチマーク(performance-design.md NFR1.2): 既定のArgon2idパラメータ(メモリ19456KiB・反復2・並列度1)で、
 * 並列度1・同時実行なし・十分なウォームアップ後に反復計測し、p95が300ms以下であることを確認する。CPUコア数・メモリ・p95は、テストの出力に含める。
 *
 * <p>実行環境により結果が揺れうるが、目標値は緩めない。満たせない場合は、ギャップとして報告する。
 */
class PasswordHasherBenchmarkTest {

  private static final long P95_LIMIT_MILLIS = 300;
  private static final int WARMUP_ITERATIONS = 10;
  private static final int MEASURED_ITERATIONS = 40;

  @Test
  void hashingP95IsWithinTheBudgetWithDefaultParameters() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    UserManagementProperties.Hash params =
        new UserManagementProperties.Hash(19456, 2, 1, 16, 32, 1, Duration.ofSeconds(60));
    PasswordHasher hasher =
        new PasswordHasher(
            params,
            new HashConcurrencyLimiter(1, params.waitTimeout(), meterRegistry),
            meterRegistry);
    String password = "benchmark-password-value";
    String stored = hasher.hash(password);

    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      hasher.hash(password);
      hasher.verify(password, stored);
    }

    long[] hashNanos = new long[MEASURED_ITERATIONS];
    long[] verifyNanos = new long[MEASURED_ITERATIONS];
    for (int i = 0; i < MEASURED_ITERATIONS; i++) {
      long t0 = System.nanoTime();
      hasher.hash(password);
      long t1 = System.nanoTime();
      hasher.verify(password, stored);
      long t2 = System.nanoTime();
      hashNanos[i] = t1 - t0;
      verifyNanos[i] = t2 - t1;
    }

    double hashP95 = p95Millis(hashNanos);
    double verifyP95 = p95Millis(verifyNanos);
    Runtime runtime = Runtime.getRuntime();
    System.out.printf(
        "[NFR1.2 benchmark] cpuCores=%d maxHeapMiB=%d os=%s/%s jdk=%s argon2id(m=19456KiB,t=2,p=1)"
            + " iterations=%d hash.p95=%.1fms verify.p95=%.1fms (limit %dms)%n",
        runtime.availableProcessors(),
        runtime.maxMemory() / (1024 * 1024),
        System.getProperty("os.name"),
        System.getProperty("os.arch"),
        System.getProperty("java.version"),
        MEASURED_ITERATIONS,
        hashP95,
        verifyP95,
        P95_LIMIT_MILLIS);

    assertThat(hashP95).as("hash p95 (ms)").isLessThanOrEqualTo(P95_LIMIT_MILLIS);
    assertThat(verifyP95).as("verify p95 (ms)").isLessThanOrEqualTo(P95_LIMIT_MILLIS);
  }

  private static double p95Millis(long[] nanos) {
    long[] sorted = nanos.clone();
    Arrays.sort(sorted);
    int index = (int) Math.ceil(0.95 * sorted.length) - 1;
    return sorted[index] / 1_000_000.0;
  }
}
