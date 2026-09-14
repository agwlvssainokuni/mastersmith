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
 * <p>{@code (activeRoleId, scopeType, scopeRef)}の3項組。レビュー指摘R-05を踏まえ、roleId成分を明示的にキーへ含める(ロールを跨いだ
 * キャッシュ汚染を避けるため)。
 */
public record PermissionCacheKey(String activeRoleId, ScopeType scopeType, String scopeRef) {}
