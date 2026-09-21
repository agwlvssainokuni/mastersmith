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

package com.mastersmith.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * {@link AuthProperties}のテスト(BR5.4・NFR4.4、安全失敗のテスト。team.mdの必須テスト種別(a)):
 * 既定値と、設定の不備(0以下の有効期限・しきい値・ロック時間・ アクセストークンの有効期限がリフレッシュトークンの有効期限を超えること・猶予・保持・実行間隔・キャッシュ・{@code
 * revoke-all-on-startup}の不正な値)で、起動が 失敗すること。エラーのメッセージには、設定のキーの名前と理由だけが含まれる。
 */
class AuthPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(AuthProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Test
  void theDefaultsAreTheValuesFixedByTheNfrDesign() {
    runner.run(
        context -> {
          AuthProperties properties = context.getBean(AuthProperties.class);
          assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(10));
          assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofMinutes(30));
          assertThat(properties.refreshReuseGrace()).isEqualTo(Duration.ofSeconds(10));
          assertThat(properties.lock().threshold()).isEqualTo(5);
          assertThat(properties.lock().duration()).isEqualTo(Duration.ofMinutes(15));
          assertThat(properties.session().retention()).isEqualTo(Duration.ofDays(7));
          assertThat(properties.session().cleanupInterval()).isEqualTo(Duration.ofDays(1));
          assertThat(properties.session().cleanupInitialDelay()).isEqualTo(Duration.ofMinutes(10));
          assertThat(properties.session().cleanupBatchSize()).isEqualTo(1000);
          assertThat(properties.session().cleanupMaxBatches()).isEqualTo(100);
          assertThat(properties.session().revokeAllOnStartup()).isFalse();
          assertThat(properties.cache().maxSize()).isEqualTo(1000);
          assertThat(properties.cache().ttl()).isEqualTo(Duration.ofSeconds(60));
        });
  }

  @Test
  void configuredValuesReplaceTheDefaults() {
    runner
        .withPropertyValues(
            "mastersmith.auth.access-token-ttl=5m",
            "mastersmith.auth.refresh-token-ttl=20m",
            "mastersmith.auth.refresh-reuse-grace=0s",
            "mastersmith.auth.lock.threshold=3",
            "mastersmith.auth.lock.duration=1h",
            "mastersmith.auth.session.retention=0d",
            "mastersmith.auth.session.revoke-all-on-startup=true",
            "mastersmith.auth.cache.max-size=10",
            "mastersmith.auth.cache.ttl=5s")
        .run(
            context -> {
              AuthProperties properties = context.getBean(AuthProperties.class);
              assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
              // 猶予0は許す(元の挙動: 再使用は、即、Sessionを失効させる)。保持日数0も許す。
              assertThat(properties.refreshReuseGrace()).isZero();
              assertThat(properties.lock().threshold()).isEqualTo(3);
              assertThat(properties.lock().duration()).isEqualTo(Duration.ofHours(1));
              assertThat(properties.session().retention()).isZero();
              assertThat(properties.session().revokeAllOnStartup()).isTrue();
              assertThat(properties.cache().maxSize()).isEqualTo(10);
            });
  }

  @Test
  void theAccessTokenLifetimeMayEqualTheRefreshTokenLifetime() {
    runner
        .withPropertyValues(
            "mastersmith.auth.access-token-ttl=30m", "mastersmith.auth.refresh-token-ttl=30m")
        .run(context -> assertThat(context).hasNotFailed());
  }

  static Stream<Arguments> invalidSettings() {
    return Stream.of(
        Arguments.of("access-token-ttl", "0s", "access-token-ttl must be positive"),
        Arguments.of("access-token-ttl", "-1m", "access-token-ttl must be positive"),
        Arguments.of("refresh-token-ttl", "0s", "refresh-token-ttl must be positive"),
        Arguments.of("refresh-token-ttl", "-5m", "refresh-token-ttl must be positive"),
        // アクセストークンの有効期限が、リフレッシュトークンの有効期限を超える(既定30分に対して31分)
        Arguments.of("access-token-ttl", "31m", "access-token-ttl must not exceed"),
        Arguments.of("refresh-reuse-grace", "-1s", "refresh-reuse-grace must not be negative"),
        Arguments.of("lock.threshold", "0", "lock.threshold must be at least 1"),
        Arguments.of("lock.threshold", "-3", "lock.threshold must be at least 1"),
        Arguments.of("lock.duration", "0s", "lock.duration must be positive"),
        Arguments.of("lock.duration", "-15m", "lock.duration must be positive"),
        Arguments.of("session.retention", "-1d", "session.retention must not be negative"),
        Arguments.of("session.cleanup-interval", "0s", "session.cleanup-interval must be positive"),
        Arguments.of(
            "session.cleanup-initial-delay",
            "-1s",
            "session.cleanup-initial-delay must not be negative"),
        Arguments.of(
            "session.cleanup-batch-size", "0", "session.cleanup-batch-size must be at least 1"),
        Arguments.of(
            "session.cleanup-max-batches", "0", "session.cleanup-max-batches must be at least 1"),
        Arguments.of("cache.max-size", "0", "cache.max-size must be at least 1"),
        Arguments.of("cache.ttl", "0s", "cache.ttl must be positive"),
        Arguments.of("cache.ttl", "-1s", "cache.ttl must be positive"));
  }

  @ParameterizedTest(name = "{0}={1} -> 起動失敗")
  @MethodSource("invalidSettings")
  void anInvalidSettingFailsTheStartup(String key, String value, String expectedReason) {
    runner
        .withPropertyValues("mastersmith.auth." + key + "=" + value)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(rootMessages(context.getStartupFailure())).contains(expectedReason);
            });
  }

  static Stream<Arguments> unparsableSettings() {
    return Stream.of(
        Arguments.of("session.revoke-all-on-startup", "maybe"),
        Arguments.of("lock.threshold", "five"),
        Arguments.of("access-token-ttl", "soon"),
        Arguments.of("cache.max-size", "many"));
  }

  @ParameterizedTest(name = "{0}={1}(解釈できない値) -> 起動失敗")
  @MethodSource("unparsableSettings")
  void aValueThatCannotBeInterpretedFailsTheStartup(String key, String value) {
    runner
        .withPropertyValues("mastersmith.auth." + key + "=" + value)
        .run(context -> assertThat(context).hasFailed());
  }

  private static String rootMessages(Throwable failure) {
    StringBuilder builder = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      builder.append(cause.getMessage()).append('\n');
    }
    return builder.toString();
  }
}
