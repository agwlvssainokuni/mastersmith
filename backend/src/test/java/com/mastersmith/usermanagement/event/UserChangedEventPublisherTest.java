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

package com.mastersmith.usermanagement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.usermanagement.entity.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * {@link UserChangedEventPublisher}と{@link UserSnapshot}:
 * 独立したトランザクション(REQUIRES_NEW)での発行、発行の例外がHTTPの結果に影響しないこと、
 * スナップショットに認証情報が含まれないこと(NFR2.2・NFR4.3)。コミット後の発行で監査ログの行が永続化されることは、統合テスト
 * (UserChangedEventAuditIntegrationTest)で確認する。
 */
class UserChangedEventPublisherTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private final UserChangedEventPublisher publisher =
      new UserChangedEventPublisher(eventPublisher, transactionManager, meterRegistry);

  private static UserChangedEvent anEvent() {
    return UserChangedEvent.of(
        UserChangeOperation.INVITED,
        "user-1",
        null,
        new UserSnapshot("氏名", "a@example.test", "invited", List.of("r1")),
        "admin-1");
  }

  private double failedCount() {
    return meterRegistry.get("user.event.publish.failed").counter().count();
  }

  @Test
  void publishesTheEventInsideANewIndependentTransactionAndCommits() {
    TransactionStatus status = new SimpleTransactionStatus(true);
    when(transactionManager.getTransaction(any())).thenReturn(status);

    publisher.publish(anEvent());

    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertThat(definition.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    verify(eventPublisher).publishEvent(any(UserChangedEvent.class));
    verify(transactionManager).commit(status);
    assertThat(failedCount()).isZero();
  }

  @Test
  void aListenerFailureRollsBackAndDoesNotPropagate() {
    TransactionStatus status = new SimpleTransactionStatus(true);
    when(transactionManager.getTransaction(any())).thenReturn(status);
    doThrow(new IllegalStateException("audit table missing"))
        .when(eventPublisher)
        .publishEvent(any(UserChangedEvent.class));

    assertThatCode(() -> publisher.publish(anEvent())).doesNotThrowAnyException();

    verify(transactionManager).rollback(status);
    verify(transactionManager, never()).commit(any());
    assertThat(failedCount()).isEqualTo(1.0);
  }

  @Test
  void aFailureToBeginTheTransactionDoesNotPropagate() {
    when(transactionManager.getTransaction(any()))
        .thenThrow(new TransactionException("no connection") {});

    assertThatCode(() -> publisher.publish(anEvent())).doesNotThrowAnyException();

    verify(eventPublisher, never()).publishEvent(any(UserChangedEvent.class));
    assertThat(failedCount()).isEqualTo(1.0);
  }

  @Test
  void aFailureAtCommitDoesNotPropagate() {
    TransactionStatus status = new SimpleTransactionStatus(true);
    when(transactionManager.getTransaction(any())).thenReturn(status);
    doThrow(new TransactionException("commit failed") {}).when(transactionManager).commit(status);

    assertThatCode(() -> publisher.publish(anEvent())).doesNotThrowAnyException();

    assertThat(failedCount()).isEqualTo(1.0);
  }

  @Test
  void theSnapshotHasNoCredentialComponents() {
    assertThat(Arrays.stream(UserSnapshot.class.getRecordComponents()).map(c -> c.getName()))
        .containsExactly("name", "email", "status", "roleIds");
    assertThat(Arrays.stream(UserChangedEvent.class.getRecordComponents()).map(c -> c.getName()))
        .doesNotContain("passwordHash", "invitationToken");
  }

  @Test
  void theSnapshotIsBuiltFromAUserWithLowercaseStatusAndSortedRoles() {
    User user = User.invited("氏名", "a@example.test", List.of("role-z", "role-a"), "token-1");

    UserSnapshot snapshot = UserSnapshot.from(user);

    assertThat(snapshot.name()).isEqualTo("氏名");
    assertThat(snapshot.email()).isEqualTo("a@example.test");
    assertThat(snapshot.status()).isEqualTo("invited");
    assertThat(snapshot.roleIds()).containsExactly("role-a", "role-z");
    assertThat(snapshot.toString()).doesNotContain("token-1");
  }

  @Test
  void theEventIsTypedAsAUserChangeWithAFixedTargetType() {
    UserChangedEvent event = anEvent();

    assertThat(event.targetType()).isEqualTo("User");
    assertThat(event.occurredAt()).isNotNull();
    assertThat(UserChangedEvent.SYSTEM_ACTOR).isEqualTo("system");
  }
}
