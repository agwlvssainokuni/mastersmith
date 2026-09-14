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

package com.mastersmith.permission.entity;

/**
 * 主権限の実効値(entities.md PrimaryPermission.level)。
 *
 * <p>比較可能な順序を持つ({@code FULL > READ > NONE}、rules.md BR3.4)。この順序はBR3.8(権限昇格判定)の比較基準として{@link
 * #rank()}経由で用いる。列挙子の宣言順(ordinal)はこの強さの順序と一致しないため、比較には必ず{@link #rank()}を用いること。
 */
public enum PermissionLevel {
  NONE(0),
  READ(1),
  FULL(2);

  private final int rank;

  PermissionLevel(int rank) {
    this.rank = rank;
  }

  /** 権限の強さを表す数値(大きいほど強い権限)。BR3.8の権限昇格判定の比較基準。 */
  public int rank() {
    return rank;
  }

  /** thatと同等か、それより強い権限かどうか。 */
  public boolean isAtLeast(PermissionLevel that) {
    return this.rank >= that.rank;
  }
}
