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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import org.junit.jupiter.api.Test;

/**
 * {@link
 * ImportExecutedEventListener}の単体テスト。正常系(リポジトリ保存)と、リポジトリが例外を送出しても発行元へ再送出しないこと(NFR4.2・NFR4.5、rules.md
 * BR7.7)を検証する。
 */
class ImportExecutedEventListenerTest {

  private final AuditLogEventMapper mapper = new AuditLogEventMapper();
  private final AuditLogEntryRepository repository = mock(AuditLogEntryRepository.class);
  private final ImportExecutedEventListener listener =
      new ImportExecutedEventListener(mapper, repository);

  private static ImportExecutedEvent anEvent() {
    return ImportExecutedEvent.of("table-config-1", "user-1", 10, 0, true);
  }

  @Test
  void savesAMappedAuditLogEntryWhenTheRepositorySucceeds() {
    listener.onImportExecutedEvent(anEvent());

    verify(repository).save(any(AuditLogEntry.class));
  }

  @Test
  void doesNotPropagateAnExceptionWhenTheRepositoryFails() {
    when(repository.save(any(AuditLogEntry.class))).thenThrow(new RuntimeException("db down"));

    // BR7.7: 例外は構造化ログへ記録するのみとし、発行元(data-import-export)へは再送出しない。
    assertThatCode(() -> listener.onImportExecutedEvent(anEvent())).doesNotThrowAnyException();
  }
}
