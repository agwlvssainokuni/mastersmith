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
import com.mastersmith.permission.dto.EffectivePermission;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 実効権限のキャッシュの、世代番号と無効化(config-import-export nfr-design/reliability-design.md NFR4.2)。{@link
 * #invalidate()}は、世代番号を進め、{@code
 * invalidateAll()}を呼ぶ。メモリ上の操作だけで、失敗しえない(取り込みの確定後に呼ばれる)。読み取りは、現在の世代番号を、キーに含める({@link
 * PermissionCacheKey})。
 */
@Component
public class PermissionCacheControl {

  private final Cache<PermissionCacheKey, EffectivePermission> cache;
  private final AtomicLong generation = new AtomicLong();

  public PermissionCacheControl(Cache<PermissionCacheKey, EffectivePermission> cache) {
    this.cache = cache;
  }

  /** 現在の世代番号。 */
  public long generation() {
    return generation.get();
  }

  /** 世代番号を進め、キャッシュの全体を無効にする。 */
  public void invalidate() {
    generation.incrementAndGet();
    cache.invalidateAll();
  }
}
