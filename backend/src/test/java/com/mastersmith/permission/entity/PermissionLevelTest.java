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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PermissionLevelの強さの順序(rules.md BR3.4: FULL > READ > NONE)のテスト。BR3.8の権限昇格判定が依拠する。 */
class PermissionLevelTest {

  @Test
  void fullIsStrongerThanReadAndNone() {
    assertThat(PermissionLevel.FULL.rank()).isGreaterThan(PermissionLevel.READ.rank());
    assertThat(PermissionLevel.FULL.rank()).isGreaterThan(PermissionLevel.NONE.rank());
  }

  @Test
  void readIsStrongerThanNone() {
    assertThat(PermissionLevel.READ.rank()).isGreaterThan(PermissionLevel.NONE.rank());
  }

  @Test
  void isAtLeastIsReflexive() {
    assertThat(PermissionLevel.READ.isAtLeast(PermissionLevel.READ)).isTrue();
  }

  @Test
  void isAtLeastIsTrueForAStrongerLevel() {
    assertThat(PermissionLevel.FULL.isAtLeast(PermissionLevel.READ)).isTrue();
  }

  @Test
  void isAtLeastIsFalseForAWeakerLevel() {
    assertThat(PermissionLevel.READ.isAtLeast(PermissionLevel.FULL)).isFalse();
    assertThat(PermissionLevel.NONE.isAtLeast(PermissionLevel.READ)).isFalse();
  }
}
