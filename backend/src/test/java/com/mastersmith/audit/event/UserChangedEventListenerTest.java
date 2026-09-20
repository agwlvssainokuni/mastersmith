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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.event.UserSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * {@link UserChangedEventListener}の単体テスト。正常系(リポジトリ保存のみ)と、リポジトリが例外を送出しても発行元へ再送出しないこと(rules.md
 * BR7.7、NFR4.3)、 失敗のログに氏名・メールアドレス(例外のメッセージに含まれうる値)が出ないことを検証する。
 */
class UserChangedEventListenerTest {

  private final AuditLogEventMapper mapper = new AuditLogEventMapper();
  private final AuditLogEntryRepository repository = mock(AuditLogEntryRepository.class);
  private final UserChangedEventListener listener =
      new UserChangedEventListener(mapper, repository);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(UserChangedEventListener.class);

  @BeforeEach
  void attachAppender() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(logs);
  }

  private static UserChangedEvent anEvent(UserChangeOperation operation, String actor) {
    return new UserChangedEvent(
        operation,
        UserChangedEvent.TARGET_TYPE_USER,
        "user-1",
        null,
        new UserSnapshot("秘密の氏名", "secret-mail@example.test", "invited", List.of("r1")),
        actor,
        Instant.parse("2026-09-20T05:00:00Z"));
  }

  @Test
  void savesAMappedAuditLogEntryAndDoesNothingElse() {
    listener.onUserChangedEvent(anEvent(UserChangeOperation.INVITED, "admin-1"));

    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(repository).save(captor.capture());
    verifyNoMoreInteractions(repository);
    AuditLogEntry entry = captor.getValue();
    assertThat(entry.getTargetType()).isEqualTo("User");
    assertThat(entry.getTargetId()).isEqualTo("user-1");
    assertThat(entry.getOperationType()).isEqualTo("INVITED");
    assertThat(entry.getActorUserId()).isEqualTo("admin-1");
    assertThat(entry.getActorRaw()).isNull();
    assertThat(entry.getBeforeValue()).isNull();
    assertThat(entry.getAfterValue()).containsEntry("status", "invited");
  }

  @Test
  void aBootstrappedEventRecordsTheSystemActorAsRawNotAsUserId() {
    listener.onUserChangedEvent(anEvent(UserChangeOperation.BOOTSTRAPPED, "system"));

    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getActorUserId()).isNull();
    assertThat(captor.getValue().getActorRaw()).isEqualTo("system");
  }

  @Test
  void doesNotPropagateAnExceptionWhenTheRepositoryFailsAndDoesNotLogItsMessage() {
    when(repository.save(any(AuditLogEntry.class)))
        .thenThrow(new IllegalStateException("insert failed for 秘密の氏名 secret-mail@example.test"));

    assertThatCode(
            () -> listener.onUserChangedEvent(anEvent(UserChangeOperation.UPDATED, "admin-1")))
        .doesNotThrowAnyException();

    assertThat(logs.list).hasSize(1);
    ILoggingEvent logged = logs.list.get(0);
    assertThat(logged.getLevel()).isEqualTo(Level.ERROR);
    String text = logged.getFormattedMessage();
    assertThat(text)
        .contains("UPDATED")
        .contains("user-1")
        .contains("IllegalStateException")
        .doesNotContain("秘密の氏名")
        .doesNotContain("secret-mail@example.test");
    // 例外そのもの(メッセージ・スタックトレース)は、ログに渡さない。
    assertThat(logged.getThrowableProxy()).isNull();
  }

  @Test
  void aMappingFailureIsAlsoContained() {
    UserChangedEvent broken =
        new UserChangedEvent(null, "User", "user-1", null, null, "a", Instant.now());

    assertThatCode(() -> listener.onUserChangedEvent(broken)).doesNotThrowAnyException();
    assertThat(logs.list).hasSize(1);
  }
}
