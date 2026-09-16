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

package com.mastersmith.audit.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.config.event.ConfigChangeOperation;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.event.PermissionChangedEvent;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link AuditLogEventMapper}のテーブル駆動テスト(3種類のイベント全てについてfunctional-design/rules.md
 * BR7.2〜BR7.4のマッピング規則を検証する。team.md確定の追加合格条件「イベント種別ごとのマッピング規則をテーブル駆動テストで網羅する」に対応、unit-test-instructions.md参照)。
 */
class AuditLogEventMapperTest {

  private final AuditLogEventMapper mapper = new AuditLogEventMapper();

  private record Case(
      String name,
      Function<AuditLogEventMapper, AuditLogEntry> map,
      String expectedTargetType,
      String expectedTargetId,
      String expectedOperationType,
      String expectedActorUserId,
      String expectedActorRaw,
      Instant expectedOccurredAt) {}

  private static List<Case> cases() {
    Instant configOccurredAt = Instant.parse("2026-09-16T00:00:00Z");
    Instant permissionOccurredAt = Instant.parse("2026-09-16T01:00:00Z");
    Instant importCommittedOccurredAt = Instant.parse("2026-09-16T02:00:00Z");
    Instant importRolledBackOccurredAt = Instant.parse("2026-09-16T03:00:00Z");

    ConfigChangedEvent configChangedEvent =
        new ConfigChangedEvent(
            ConfigChangeOperation.DRAFT_IMPORTED, "shop.products", "system", configOccurredAt);
    PermissionChangedEvent permissionChangedEvent =
        new PermissionChangedEvent(
            "role-1", ScopeType.SCHEMA, "shop", "role-admin", permissionOccurredAt);
    ImportExecutedEvent importCommittedEvent =
        new ImportExecutedEvent("table-config-1", "user-1", 10, 0, true, importCommittedOccurredAt);
    ImportExecutedEvent importRolledBackEvent =
        new ImportExecutedEvent(
            "table-config-2", "user-2", 3, 2, false, importRolledBackOccurredAt);

    return List.of(
        new Case(
            "ConfigChangedEvent (BR7.2)",
            m -> m.fromConfigChangedEvent(configChangedEvent),
            "ConfigEngine",
            "shop.products",
            "DRAFT_IMPORTED",
            null,
            "system",
            configOccurredAt),
        new Case(
            "PermissionChangedEvent (BR7.3)",
            m -> m.fromPermissionChangedEvent(permissionChangedEvent),
            "PermissionEngine",
            "role-1",
            "PERMISSION_CHANGED",
            null,
            "role-admin",
            permissionOccurredAt),
        new Case(
            "ImportExecutedEvent - committed (BR7.4)",
            m -> m.fromImportExecutedEvent(importCommittedEvent),
            "DataImportExport",
            "table-config-1",
            "IMPORT_COMMITTED",
            "user-1",
            null,
            importCommittedOccurredAt),
        new Case(
            "ImportExecutedEvent - rolled back (BR7.4)",
            m -> m.fromImportExecutedEvent(importRolledBackEvent),
            "DataImportExport",
            "table-config-2",
            "IMPORT_ROLLED_BACK",
            "user-2",
            null,
            importRolledBackOccurredAt));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void mapsEventToAuditLogEntryAccordingToItsMappingRule(Case testCase) {
    AuditLogEntry entry = testCase.map().apply(mapper);

    assertThat(entry.getTargetType()).isEqualTo(testCase.expectedTargetType());
    assertThat(entry.getTargetId()).isEqualTo(testCase.expectedTargetId());
    assertThat(entry.getOperationType()).isEqualTo(testCase.expectedOperationType());
    assertThat(entry.getActorUserId()).isEqualTo(testCase.expectedActorUserId());
    assertThat(entry.getActorRaw()).isEqualTo(testCase.expectedActorRaw());
    assertThat(entry.getOccurredAt()).isEqualTo(testCase.expectedOccurredAt());
    // Q5=A: 現行3イベントはいずれもbeforeValue/afterValueを常にnullとする。
    assertThat(entry.getBeforeValue()).isNull();
    assertThat(entry.getAfterValue()).isNull();
  }
}
