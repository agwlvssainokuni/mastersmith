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

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * authentication-serviceの設定の有効化({@code mastersmith.auth.*})と、時刻の唯一の供給元({@link Clock}、UTC)。
 *
 * <p>ロックの判定・Sessionの有効期限・JWTの{@code iat}・{@code exp}・リフレッシュの猶予・定期削除の基準時刻のすべてに、同じ{@link
 * Clock}を用いる(DBの現在時刻は使わない、NFR4.6)。テストでは、{@code @Primary}の{@link Clock}に差し替える。
 *
 * <p>{@link EnableScheduling}は、Sessionの定期削除({@code SessionCleanupJob})のため。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@EnableScheduling
public class AuthConfig {

  @Bean
  public Clock authClock() {
    return Clock.systemUTC();
  }
}
