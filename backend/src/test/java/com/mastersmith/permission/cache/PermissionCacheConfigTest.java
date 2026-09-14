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

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * {@link PermissionCacheConfig}の単体テスト(実インスタンスのCaffeineキャッシュを用いる。TTL・invalidateAll()の実際の挙動を
 * 検証する、unit-test-instructions.md「モック・スタブ方針」)。TTLの実際の経過待ちは行わず、設定された有効期限ポリシーの値そのものを検証する。
 */
class PermissionCacheConfigTest {

  private final PermissionCacheConfig config = new PermissionCacheConfig();

  @Test
  void putAndGetRoundTripsThroughTheCache() {
    Cache<PermissionCacheKey, EffectivePermission> cache =
        config.permissionCache(new SimpleMeterRegistry(), 30, 5000);
    PermissionCacheKey key = new PermissionCacheKey("role-1", ScopeType.TABLE, "table-1");
    EffectivePermission value = new EffectivePermission(PermissionLevel.FULL, true, true);

    cache.put(key, value);

    assertThat(cache.getIfPresent(key)).isEqualTo(value);
  }

  @Test
  void getIfPresentReturnsNullForAnUncachedKey() {
    Cache<PermissionCacheKey, EffectivePermission> cache =
        config.permissionCache(new SimpleMeterRegistry(), 30, 5000);

    assertThat(cache.getIfPresent(new PermissionCacheKey("role-1", ScopeType.SCHEMA, "public")))
        .isNull();
  }

  @Test
  void invalidateAllClearsEveryEntry() {
    Cache<PermissionCacheKey, EffectivePermission> cache =
        config.permissionCache(new SimpleMeterRegistry(), 30, 5000);
    cache.put(
        new PermissionCacheKey("role-1", ScopeType.TABLE, "table-1"),
        new EffectivePermission(PermissionLevel.FULL, true, true));
    cache.put(
        new PermissionCacheKey("role-2", ScopeType.SCHEMA, "public"),
        new EffectivePermission(PermissionLevel.READ, false, false));

    cache.invalidateAll();

    assertThat(cache.asMap()).isEmpty();
  }

  @Test
  void expireAfterWriteIsConfiguredWithTheGivenTtl() {
    Cache<PermissionCacheKey, EffectivePermission> cache =
        config.permissionCache(new SimpleMeterRegistry(), 45, 5000);

    assertThat(cache.policy().expireAfterWrite()).isPresent();
    assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.SECONDS))
        .isEqualTo(45);
  }

  @Test
  void maximumSizeIsConfiguredWithTheGivenLimit() {
    Cache<PermissionCacheKey, EffectivePermission> cache =
        config.permissionCache(new SimpleMeterRegistry(), 30, 3);

    assertThat(cache.policy().eviction()).isPresent();
    assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(3);
  }

  @Test
  void statsAreRecordedForHitRatioObservability() {
    // observability-design.md: permission_cache_hit_ratioはCaffeineのrecordStats()(CacheStats)
    // をMicrometer経由で公開する。recordStats()が有効化されていることをヒット/ミス計上で検証する。
    Cache<PermissionCacheKey, EffectivePermission> cache =
        config.permissionCache(new SimpleMeterRegistry(), 30, 5000);
    PermissionCacheKey key = new PermissionCacheKey("role-1", ScopeType.TABLE, "table-1");

    cache.getIfPresent(key); // ミス
    cache.put(key, new EffectivePermission(PermissionLevel.FULL, true, true));
    cache.getIfPresent(key); // ヒット

    assertThat(cache.stats().missCount()).isEqualTo(1);
    assertThat(cache.stats().hitCount()).isEqualTo(1);
  }
}
