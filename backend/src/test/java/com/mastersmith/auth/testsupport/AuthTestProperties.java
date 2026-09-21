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

package com.mastersmith.auth.testsupport;

import com.mastersmith.auth.config.AuthProperties;
import com.mastersmith.auth.config.AuthProperties.CacheSettings;
import com.mastersmith.auth.config.AuthProperties.Lock;
import com.mastersmith.auth.config.AuthProperties.SessionSettings;
import java.time.Duration;

/** {@link AuthProperties}のテスト用の生成(既定値は、NFR Requirements・NFR Designで確定した値)。 */
public final class AuthTestProperties {

  private AuthTestProperties() {}

  public static AuthProperties defaults() {
    return new AuthProperties(
        Duration.ofMinutes(10),
        Duration.ofMinutes(30),
        Duration.ofSeconds(10),
        new Lock(5, Duration.ofMinutes(15)),
        new SessionSettings(
            Duration.ofDays(7), Duration.ofDays(1), Duration.ofMinutes(10), 1000, 100, false),
        new CacheSettings(1000, Duration.ofSeconds(60)));
  }

  public static AuthProperties withLock(int threshold, Duration duration) {
    AuthProperties base = defaults();
    return new AuthProperties(
        base.accessTokenTtl(),
        base.refreshTokenTtl(),
        base.refreshReuseGrace(),
        new Lock(threshold, duration),
        base.session(),
        base.cache());
  }

  public static AuthProperties withGrace(Duration grace) {
    AuthProperties base = defaults();
    return new AuthProperties(
        base.accessTokenTtl(),
        base.refreshTokenTtl(),
        grace,
        base.lock(),
        base.session(),
        base.cache());
  }

  public static AuthProperties withSession(SessionSettings session) {
    AuthProperties base = defaults();
    return new AuthProperties(
        base.accessTokenTtl(),
        base.refreshTokenTtl(),
        base.refreshReuseGrace(),
        base.lock(),
        session,
        base.cache());
  }
}
