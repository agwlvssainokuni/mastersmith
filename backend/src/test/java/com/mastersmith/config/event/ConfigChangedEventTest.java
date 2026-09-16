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

package com.mastersmith.config.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigChangedEvent}の単体テスト。BR1.13/entities.mdが要求するフィールド形状(targetType, targetId,
 * beforeValue, afterValue)が{@link ConfigChangedEvent#of}経由で正しく設定されること、および
 * AuditLogging(U7)の既存呼び出し互換のため残す4引数コンストラクタが引き続き機能することを検証する。
 */
class ConfigChangedEventTest {

  @Test
  void ofPopulatesAllFieldsAndDerivesTheLegacyTargetFromTargetTypeAndTargetId() {
    Map<String, Object> after = Map.of("tableConfigId", "t1");

    ConfigChangedEvent event =
        ConfigChangedEvent.of(
            ConfigChangeOperation.DRAFT_IMPORTED,
            ConfigChangedEvent.TARGET_TYPE_TABLE_CONFIG,
            "t1",
            null,
            after,
            "system");

    assertThat(event.operation()).isEqualTo(ConfigChangeOperation.DRAFT_IMPORTED);
    assertThat(event.targetType()).isEqualTo(ConfigChangedEvent.TARGET_TYPE_TABLE_CONFIG);
    assertThat(event.targetId()).isEqualTo("t1");
    assertThat(event.beforeValue()).isNull();
    assertThat(event.afterValue()).isEqualTo(after);
    assertThat(event.actor()).isEqualTo("system");
    assertThat(event.occurredAt()).isNotNull();
    assertThat(event.target()).isEqualTo("TableConfig:t1");
  }

  @Test
  void legacyFourArgConstructorLeavesTheNewFieldsUnset() {
    Instant occurredAt = Instant.parse("2026-09-16T00:00:00Z");

    ConfigChangedEvent event =
        new ConfigChangedEvent(
            ConfigChangeOperation.DRAFT_IMPORTED, "shop.products", "system", occurredAt);

    assertThat(event.target()).isEqualTo("shop.products");
    assertThat(event.actor()).isEqualTo("system");
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
    assertThat(event.targetType()).isNull();
    assertThat(event.targetId()).isNull();
    assertThat(event.beforeValue()).isNull();
    assertThat(event.afterValue()).isNull();
  }
}
