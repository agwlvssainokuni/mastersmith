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
