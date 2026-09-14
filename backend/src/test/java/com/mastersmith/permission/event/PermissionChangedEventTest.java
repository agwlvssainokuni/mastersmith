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

package com.mastersmith.permission.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.permission.entity.ScopeType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** {@link PermissionChangedEvent}の単体テスト(rules.md BR3.11)。 */
class PermissionChangedEventTest {

  @Test
  void ofPopulatesAllFieldsAndStampsOccurredAtToNow() {
    Instant before = Instant.now();

    PermissionChangedEvent event =
        PermissionChangedEvent.of("role-1", ScopeType.TABLE, "table-1", "actor-role");

    Instant after = Instant.now();
    assertThat(event.targetRoleId()).isEqualTo("role-1");
    assertThat(event.scopeType()).isEqualTo(ScopeType.TABLE);
    assertThat(event.scopeRef()).isEqualTo("table-1");
    assertThat(event.actor()).isEqualTo("actor-role");
    assertThat(event.occurredAt()).isBetween(before, after);
  }

  @Test
  void doesNotCarryBeforeAfterValues() {
    // rules.md BR3.11: 個々の変更前後の値は本イベントには含めない。PermissionChangedEventのコンポーネントは
    // targetRoleId/scopeType/scopeRef/actor/occurredAtの5件のみであることを、レコードの構造そのもので示す。
    PermissionChangedEvent event =
        PermissionChangedEvent.of("role-1", ScopeType.SCHEMA, "public", "actor-role");

    assertThat(event.getClass().getRecordComponents()).hasSize(5);
  }
}
