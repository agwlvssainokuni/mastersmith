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
import com.mastersmith.usermanagement.config.UserManagementProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** {@link PasswordHasher}: ハッシュ・検証・パラメータの比較(NFR1.2・NFR2.2・NFR2.3)。テストの速度のため、軽いパラメータを用いる。 */
class PasswordHasherTest {

  private static final String PASSWORD = "correct horse battery";

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private static UserManagementProperties.Hash params(int memoryKib, int iterations) {
    return new UserManagementProperties.Hash(
        memoryKib, iterations, 1, 16, 32, 4, Duration.ofSeconds(2));
  }

  private PasswordHasher hasher(int memoryKib, int iterations) {
    UserManagementProperties.Hash params = params(memoryKib, iterations);
    return new PasswordHasher(
        params, new HashConcurrencyLimiter(4, params.waitTimeout(), meterRegistry), meterRegistry);
  }

  @Test
  void hashesWithArgon2idAndVerifiesTheCorrectPassword() {
    PasswordHasher hasher = hasher(1024, 1);

    String hash = hasher.hash(PASSWORD);

    assertThat(hash).startsWith("$argon2id$v=19$m=1024,t=1,p=1$");
    assertThat(hasher.verify(PASSWORD, hash)).isTrue();
  }

  @Test
  void rejectsAWrongPassword() {
    PasswordHasher hasher = hasher(1024, 1);
    String hash = hasher.hash(PASSWORD);

    assertThat(hasher.verify("another password", hash)).isFalse();
    assertThat(hasher.verify(PASSWORD + "x", hash)).isFalse();
  }

  @Test
  void hashesTheSamePasswordToDifferentValuesBecauseOfTheSalt() {
    PasswordHasher hasher = hasher(1024, 1);

    assertThat(hasher.hash(PASSWORD)).isNotEqualTo(hasher.hash(PASSWORD));
  }

  @Test
  void theHashDoesNotContainThePlaintext() {
    String hash = hasher(1024, 1).hash(PASSWORD);

    assertThat(hash).doesNotContain(PASSWORD).doesNotContain("correct");
  }

  @Test
  void doesNotComputeForPasswordsLongerThanTheMaximum() {
    PasswordHasher hasher = hasher(1024, 1);
    String hash = hasher.hash("a".repeat(128));
    long timerCountBefore = meterRegistry.get("user.password.hash.duration").timer().count();

    // 129文字以上は、計算せずfalse(許可も取らない、ハッシュ計算のタイマーも増えない)。
    assertThat(hasher.verify("a".repeat(129), hash)).isFalse();
    assertThat(hasher.verifyAndUpgrade("a".repeat(129), hash).matches()).isFalse();

    assertThat(meterRegistry.get("user.password.hash.duration").timer().count())
        .isEqualTo(timerCountBefore);
    assertThat(hasher.verify("a".repeat(128), hash)).isTrue();
  }

  @Test
  void refusesToHashPasswordsOutsideThePolicy() {
    PasswordHasher hasher = hasher(1024, 1);

    assertThatThrownBy(() -> hasher.hash("a".repeat(7)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> hasher.hash("a".repeat(129)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verifyReturnsFalseForMissingOrMalformedInputs() {
    PasswordHasher hasher = hasher(1024, 1);

    assertThat(hasher.verify(null, "$argon2id$v=19$m=1024,t=1,p=1$abc$def")).isFalse();
    assertThat(hasher.verify("", "$argon2id$v=19$m=1024,t=1,p=1$abc$def")).isFalse();
    assertThat(hasher.verify(PASSWORD, null)).isFalse();
    assertThat(hasher.verify(PASSWORD, "")).isFalse();
    assertThat(hasher.verify(PASSWORD, "not-a-hash")).isFalse();
  }

  @Test
  void needsUpgradeComparesTheStoredParametersWithTheCurrentSettings() {
    PasswordHasher current = hasher(2048, 2);
    String sameParams = current.hash(PASSWORD);
    String weakerMemory = hasher(1024, 2).hash(PASSWORD);
    String weakerIterations = hasher(2048, 1).hash(PASSWORD);
    String stronger = hasher(4096, 3).hash(PASSWORD);

    assertThat(current.needsUpgrade(sameParams)).isFalse();
    assertThat(current.needsUpgrade(weakerMemory)).isTrue();
    assertThat(current.needsUpgrade(weakerIterations)).isTrue();
    // 現在の設定より強いパラメータで保存されたハッシュは、更新の対象としない。
    assertThat(current.needsUpgrade(stronger)).isFalse();
  }

  @Test
  void needsUpgradeIsFalseForNullBlankOrMalformedHashes() {
    PasswordHasher hasher = hasher(1024, 1);

    assertThat(hasher.needsUpgrade(null)).isFalse();
    assertThat(hasher.needsUpgrade("")).isFalse();
    assertThat(hasher.needsUpgrade("plain-text")).isFalse();
    assertThat(hasher.needsUpgrade("$argon2id$broken")).isFalse();
    assertThat(hasher.needsUpgrade("$2a$10$abcdefghijklmnopqrstuv")).isFalse();
  }

  @Test
  void verifyAndUpgradeReturnsAnUpgradedHashOnlyForAnOldHashAndACorrectPassword() {
    PasswordHasher current = hasher(2048, 2);
    String oldHash = hasher(1024, 1).hash(PASSWORD);
    String currentHash = current.hash(PASSWORD);

    PasswordHasher.VerifyResult upgraded = current.verifyAndUpgrade(PASSWORD, oldHash);
    PasswordHasher.VerifyResult noUpgrade = current.verifyAndUpgrade(PASSWORD, currentHash);
    PasswordHasher.VerifyResult wrong = current.verifyAndUpgrade("wrong password", oldHash);

    assertThat(upgraded.matches()).isTrue();
    assertThat(upgraded.upgradedHash()).startsWith("$argon2id$v=19$m=2048,t=2,p=1$");
    assertThat(current.verify(PASSWORD, upgraded.upgradedHash())).isTrue();
    assertThat(noUpgrade.matches()).isTrue();
    assertThat(noUpgrade.upgradedHash()).isNull();
    assertThat(wrong.matches()).isFalse();
    assertThat(wrong.upgradedHash()).isNull();
  }

  @Test
  void recordsTheComputationDuration() {
    PasswordHasher hasher = hasher(1024, 1);

    String hash = hasher.hash(PASSWORD);
    hasher.verify(PASSWORD, hash);

    assertThat(meterRegistry.get("user.password.hash.duration").timer().count()).isEqualTo(2);
  }

  @Test
  void propagatesTheCapacityExceptionWhenNoPermitIsAvailable() {
    UserManagementProperties.Hash params = params(1024, 1);
    HashConcurrencyLimiter exhausted =
        new HashConcurrencyLimiter(1, Duration.ofMillis(50), meterRegistry);
    PasswordHasher hasher = new PasswordHasher(params, exhausted, meterRegistry);
    String hash = hasher(1024, 1).hash(PASSWORD);

    // 許可を保持している最中に(同じスレッドの入れ子で)取ろうとして、待機超過にする。
    assertThatThrownBy(() -> exhausted.runWithPermit(() -> hasher.verify(PASSWORD, hash)))
        .isInstanceOf(HashCapacityExceededException.class);
  }
}
