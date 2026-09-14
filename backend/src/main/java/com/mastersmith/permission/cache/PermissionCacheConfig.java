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

package com.mastersmith.permission.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mastersmith.permission.dto.EffectivePermission;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 実効権限のインメモリキャッシュ設定(performance-design.md「キャッシュアーキテクチャ」、Caffeine採用、Q1=B)。
 *
 * <p>読み取りはキャッシュ経由で高速化し(NFR1.1/NFR1.2)、{@code assignPermission}成功時は{@link
 * Cache#invalidateAll()}でキャッシュ全体を無効化する(レビュー指摘R-01対応。スコープ階層のフォールバック解決済みエントリを部分無効化で漏らさないため、
 * performance-design.md参照)。
 */
@Configuration
public class PermissionCacheConfig {

  /** 既定TTL(数秒〜数十秒のオーダー、Code Generationで設定可能な値として実装、performance-design.md)。 */
  private static final long DEFAULT_TTL_SECONDS = 30;

  /** 既定サイズ上限(想定同時アクティブユーザー数×想定同時アクセス列数を目安とした数千エントリ、scalability-design.md)。 */
  private static final long DEFAULT_MAX_SIZE = 5000;

  @Bean
  public Cache<PermissionCacheKey, EffectivePermission> permissionCache(
      MeterRegistry meterRegistry,
      @Value("${mastersmith.permission.cache.ttl-seconds:" + DEFAULT_TTL_SECONDS + "}")
          long ttlSeconds,
      @Value("${mastersmith.permission.cache.max-size:" + DEFAULT_MAX_SIZE + "}") long maxSize) {
    Cache<PermissionCacheKey, EffectivePermission> cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .maximumSize(maxSize)
            .recordStats()
            .build();
    // permission_cache_hit_ratio(observability-design.md)。CaffeineのrecordStats()が提供する
    // CacheStatsをMicrometer経由で公開する。
    CaffeineCacheMetrics.monitor(meterRegistry, cache, "permission");
    return cache;
  }
}
