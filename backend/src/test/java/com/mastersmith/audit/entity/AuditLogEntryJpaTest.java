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

package com.mastersmith.audit.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** AuditLogEntryのJPAマッピングテスト(INSERT後の読み取り往復、beforeValue/afterValueのnull永続化、plan Step4)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuditLogEntryJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsAnImportExecutedEventDerivedEntry() {
    // BR7.4: ImportExecutedEvent由来のエントリのみactorUserIdが非null、actorRawはnull。
    Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    AuditLogEntry entry =
        new AuditLogEntry(
            "user-1",
            null,
            "DataImportExport",
            "table-config-1",
            "IMPORT_COMMITTED",
            occurredAt,
            null,
            null);

    entityManager.persist(entry);
    entityManager.flush();
    entityManager.clear();

    AuditLogEntry reloaded = entityManager.find(AuditLogEntry.class, entry.getAuditLogEntryId());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getActorUserId()).isEqualTo("user-1");
    assertThat(reloaded.getActorRaw()).isNull();
    assertThat(reloaded.getTargetType()).isEqualTo("DataImportExport");
    assertThat(reloaded.getTargetId()).isEqualTo("table-config-1");
    assertThat(reloaded.getOperationType()).isEqualTo("IMPORT_COMMITTED");
    assertThat(reloaded.getOccurredAt()).isEqualTo(occurredAt);
  }

  @Test
  void savesAndReloadsAConfigChangedEventDerivedEntryWithActorRaw() {
    // BR7.2: ConfigChangedEvent由来のエントリはactorUserIdがnull、actorRawへ元の値(system等)を保持する。
    AuditLogEntry entry =
        new AuditLogEntry(
            null,
            "system",
            "ConfigEngine",
            "schema.table",
            "DRAFT_IMPORTED",
            Instant.now(),
            null,
            null);

    entityManager.persist(entry);
    entityManager.flush();
    entityManager.clear();

    AuditLogEntry reloaded = entityManager.find(AuditLogEntry.class, entry.getAuditLogEntryId());
    assertThat(reloaded.getActorUserId()).isNull();
    assertThat(reloaded.getActorRaw()).isEqualTo("system");
  }

  @Test
  void beforeValueAndAfterValueArePersistedAsNull() {
    // entities.md Q5=A: 現行3イベントはいずれも構造化された変更前後の値を運ばないため常にnull。
    AuditLogEntry entry =
        new AuditLogEntry(
            null,
            "role-1",
            "PermissionEngine",
            "role-2",
            "PERMISSION_CHANGED",
            Instant.now(),
            null,
            null);

    entityManager.persist(entry);
    entityManager.flush();
    entityManager.clear();

    AuditLogEntry reloaded = entityManager.find(AuditLogEntry.class, entry.getAuditLogEntryId());
    assertThat(reloaded.getBeforeValue()).isNull();
    assertThat(reloaded.getAfterValue()).isNull();
  }

  @Test
  void generatesAUniqueAuditLogEntryIdWhenNotExplicitlyProvided() {
    AuditLogEntry first =
        new AuditLogEntry(
            null,
            "system",
            "ConfigEngine",
            "schema.a",
            "DRAFT_IMPORTED",
            Instant.now(),
            null,
            null);
    AuditLogEntry second =
        new AuditLogEntry(
            null,
            "system",
            "ConfigEngine",
            "schema.b",
            "DRAFT_IMPORTED",
            Instant.now(),
            null,
            null);

    assertThat(first.getAuditLogEntryId()).isNotBlank();
    assertThat(second.getAuditLogEntryId()).isNotBlank();
    assertThat(first.getAuditLogEntryId()).isNotEqualTo(second.getAuditLogEntryId());
  }
}
