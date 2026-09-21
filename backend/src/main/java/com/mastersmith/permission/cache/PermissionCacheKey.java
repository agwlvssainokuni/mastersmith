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

import com.mastersmith.permission.entity.ScopeType;

/**
 * 実効権限キャッシュのキー(performance-design.md「キャッシュアーキテクチャ」)。
 *
 * <p>{@code (activeRoleId, scopeType,
 * scopeRef)}の3項組に、キャッシュの<b>世代番号</b>を加える。レビュー指摘R-05を踏まえ、roleId成分を明示的にキーへ含める(ロールを跨いだ
 * キャッシュ汚染を避けるため)。世代番号は、キャッシュの無効化({@link
 * PermissionCacheControl#invalidate()})のたびに進み、読み取りは、常に現在の世代のキーで行う。これにより、無効化の後に、進行中の計算が、
 * 古い世代のキーで、古い値を格納しても、読み取りには使われない(Caffeineの、進行中のロードと{@code
 * invalidateAll()}の競合への対策。config-import-export nfr-design/reliability-design.md NFR4.2)。
 *
 * @param generation キャッシュの世代番号(既存の3引数のコンストラクターでは0)
 */
public record PermissionCacheKey(
    String activeRoleId, ScopeType scopeType, String scopeRef, long generation) {

  /** 世代番号0のキー(既存の呼び出し・テストとの互換)。 */
  public PermissionCacheKey(String activeRoleId, ScopeType scopeType, String scopeRef) {
    this(activeRoleId, scopeType, scopeRef, 0L);
  }
}
