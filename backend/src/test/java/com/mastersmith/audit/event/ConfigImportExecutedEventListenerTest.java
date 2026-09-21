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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.configio.event.ConfigImportExecutedEvent;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.permission.event.PermissionImportedEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * audit-loggingの、設定の取り込みの監査イベント({@link ConfigImportExecutedEvent}・{@link
 * PermissionImportedEvent})の購読と、{@link AuditLogEntry}への対応付け(config-import-export BR9.16、
 * permission-engine BR3.11)のテスト。マッピングは、テーブル駆動(成功・失敗の分類ごと)。ファイルの内容を記録しないこと、リスナーが例外を遮断すること、発行元が{@code
 * REQUIRES_NEW}で発行した場合に、書き込みが、 実際に永続化される(別のスレッド・別のトランザクションから読める)こと(レビュー指摘R-15)を確認する。
 */
class ConfigImportExecutedEventListenerTest {

  private final AuditLogEventMapper mapper = new AuditLogEventMapper();
  private final AuditLogEntryRepository repository = mock(AuditLogEntryRepository.class);
  private final ConfigImportExecutedEventListener listener =
      new ConfigImportExecutedEventListener(mapper, repository);

  private static Map<String, SectionCounts> counts() {
    Map<String, SectionCounts> sections = new LinkedHashMap<>();
    sections.put(ImportSections.SCHEMA, new SectionCounts(1, 2, 3));
    sections.put(ImportSections.MENU, new SectionCounts(4, 5, 6));
    return sections;
  }

  // ---- マッピング(テーブル駆動) ----

  static Stream<Arguments> failureCategories() {
    return Stream.of(FailureCategory.values()).map(Arguments::of);
  }

  @Test
  void successIsMappedWithSectionCountsAndTheActorAsARealUserId() {
    ConfigImportExecutedEvent event =
        ConfigImportExecutedEvent.success("user-1", "role-1", counts());

    AuditLogEntry entry = mapper.fromConfigImportExecutedEvent(event);

    assertThat(entry.getActorUserId()).isEqualTo("user-1");
    assertThat(entry.getActorRaw()).isNull();
    assertThat(entry.getTargetType()).isEqualTo("ConfigImportExport");
    assertThat(entry.getTargetId()).isEqualTo("config-import-export");
    assertThat(entry.getOperationType()).isEqualTo("CONFIG_IMPORT_SUCCEEDED");
    assertThat(entry.getOccurredAt()).isEqualTo(event.occurredAt());
    assertThat(entry.getBeforeValue()).isNull();
    assertThat(entry.getAfterValue())
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("activeRoleId", "role-1")
        .doesNotContainKeys("failureCategory", "errorCount");
    @SuppressWarnings("unchecked")
    Map<String, Object> sections = (Map<String, Object>) entry.getAfterValue().get("sections");
    assertThat(sections).containsOnlyKeys("schema", "menu");
    assertThat(sections.get("schema")).isEqualTo(Map.of("added", 1, "updated", 2, "deleted", 3));
  }

  @ParameterizedTest(name = "失敗の分類 {0}")
  @MethodSource("failureCategories")
  void failureIsMappedWithTheCategoryAndTheTotalErrorCountAndNoSections(FailureCategory category) {
    ConfigImportExecutedEvent event =
        ConfigImportExecutedEvent.failure("user-1", null, category, 250);

    AuditLogEntry entry = mapper.fromConfigImportExecutedEvent(event);

    assertThat(entry.getOperationType()).isEqualTo("CONFIG_IMPORT_FAILED");
    assertThat(entry.getActorUserId()).isEqualTo("user-1");
    assertThat(entry.getAfterValue())
        .containsEntry("outcome", "FAILURE")
        .containsEntry("failureCategory", category.name())
        .containsEntry("errorCount", 250)
        .doesNotContainKeys("sections", "activeRoleId");
  }

  @Test
  void theMappedEntryCarriesOnlyTheAgreedKeysSoNoFileContentCanLeak() {
    AuditLogEntry success =
        mapper.fromConfigImportExecutedEvent(ConfigImportExecutedEvent.success("u", "r", counts()));
    AuditLogEntry failure =
        mapper.fromConfigImportExecutedEvent(
            ConfigImportExecutedEvent.failure("u", "r", FailureCategory.VALIDATION_ERROR, 1));

    assertThat(success.getAfterValue().keySet())
        .containsExactlyInAnyOrder("outcome", "activeRoleId", "sections");
    assertThat(failure.getAfterValue().keySet())
        .containsExactlyInAnyOrder("outcome", "activeRoleId", "failureCategory", "errorCount");
  }

  @Test
  void permissionImportedSummaryIsMappedWithTheRoleAsAnUnverifiedActorAndOnlyTheChangeCount() {
    Instant at = Instant.parse("2026-09-21T00:00:00Z");

    AuditLogEntry entry =
        mapper.fromPermissionImportedEvent(new PermissionImportedEvent("role-1", 7, at));

    assertThat(entry.getActorUserId()).isNull();
    assertThat(entry.getActorRaw()).isEqualTo("role-1");
    assertThat(entry.getTargetType()).isEqualTo("PermissionEngine");
    assertThat(entry.getOperationType()).isEqualTo("PERMISSION_IMPORTED");
    assertThat(entry.getOccurredAt()).isEqualTo(at);
    assertThat(entry.getAfterValue())
        .containsOnlyKeys("changeCount")
        .containsEntry("changeCount", 7);
  }

  @Test
  void theEventRequiresTheActorAndTheOutcome() {
    assertThatThrownBy(() -> ConfigImportExecutedEvent.success(null, "r", counts()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ConfigImportExecutedEvent.failure("u", "r", null, 1))
        .isInstanceOf(NullPointerException.class);
  }

  // ---- リスナー ----

  @Test
  void savesTheMappedEntry() {
    listener.onConfigImportExecutedEvent(ConfigImportExecutedEvent.success("u", null, counts()));

    org.mockito.ArgumentCaptor<AuditLogEntry> captor = forClass(AuditLogEntry.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getOperationType()).isEqualTo("CONFIG_IMPORT_SUCCEEDED");
  }

  @Test
  void doesNotPropagateAnExceptionWhenTheRepositoryFails() {
    when(repository.save(any(AuditLogEntry.class))).thenThrow(new RuntimeException("db down"));

    assertThatCode(
            () ->
                listener.onConfigImportExecutedEvent(
                    ConfigImportExecutedEvent.failure("u", "r", FailureCategory.UNEXPECTED, 0)))
        .doesNotThrowAnyException();
  }

  @Test
  void thePermissionSummaryListenerAlsoSwallowsFailures() {
    when(repository.save(any(AuditLogEntry.class))).thenThrow(new RuntimeException("db down"));
    PermissionImportedEventListener permissionListener =
        new PermissionImportedEventListener(mapper, repository);

    assertThatCode(
            () -> permissionListener.onPermissionImportedEvent(PermissionImportedEvent.of("r", 1)))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(ConfigImportExecutedEvent.Outcome.class)
  void outcomesAreTwo(ConfigImportExecutedEvent.Outcome outcome) {
    assertThat(outcome.name()).isIn("SUCCESS", "FAILURE");
  }

  // ---- 永続化(実際のH2・実際のトランザクション。R-15) ----

  /** 発行元が、{@code REQUIRES_NEW}のトランザクションの中で、同期発行した書き込みが、別のスレッドから読める(永続化された)こと。 */
  @Nested
  @SpringBootTest(classes = MastersmithApplication.class)
  class Persistence {

    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private AuditLogEntryRepository auditRepository;

    @Autowired
    @Qualifier("transactionManager")
    private PlatformTransactionManager transactionManager;

    private final ExecutorService otherThread = Executors.newSingleThreadExecutor();

    private TransactionTemplate template(int propagation) {
      TransactionTemplate template = new TransactionTemplate(transactionManager);
      template.setPropagationBehavior(propagation);
      return template;
    }

    private List<AuditLogEntry> entriesOf(String userId) throws Exception {
      return otherThread
          .submit(
              () ->
                  auditRepository
                      .findAll(PageRequest.of(0, 2000, Sort.by(Sort.Direction.DESC, "occurredAt")))
                      .stream()
                      .filter(e -> userId.equals(e.getActorUserId()))
                      .toList())
          .get(10, TimeUnit.SECONDS);
    }

    @Test
    void anEventPublishedInsideARequiresNewTransactionIsPersistedAndReadableFromAnotherThread()
        throws Exception {
      String userId = "audit-cfg-" + UUID.randomUUID();

      template(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
          .executeWithoutResult(
              status ->
                  publisher.publishEvent(ConfigImportExecutedEvent.success(userId, "r", counts())));

      List<AuditLogEntry> persisted = entriesOf(userId);
      assertThat(persisted).hasSize(1);
      assertThat(persisted.get(0).getOperationType()).isEqualTo("CONFIG_IMPORT_SUCCEEDED");
      assertThat(persisted.get(0).getAfterValue()).containsEntry("outcome", "SUCCESS");
      otherThread.shutdown();
    }

    @Test
    void aFailureEventIsPersistedTheSameWayAndAnEventInARolledBackTransactionIsNot()
        throws Exception {
      String failedUser = "audit-cfg-" + UUID.randomUUID();
      String rolledBackUser = "audit-cfg-" + UUID.randomUUID();

      template(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
          .executeWithoutResult(
              status ->
                  publisher.publishEvent(
                      ConfigImportExecutedEvent.failure(
                          failedUser, null, FailureCategory.ESCALATION_DENIED, 3)));
      // 対比: 取り込みのトランザクションの中で(ロールバックされる)発行すると、記録も一緒に消える。
      // 失敗の監査イベントを、トランザクションの外の、独立したトランザクションで発行する理由。
      assertThatCode(
              () ->
                  template(TransactionDefinition.PROPAGATION_REQUIRED)
                      .executeWithoutResult(
                          status -> {
                            publisher.publishEvent(
                                ConfigImportExecutedEvent.failure(
                                    rolledBackUser, null, FailureCategory.UNEXPECTED, 0));
                            status.setRollbackOnly();
                          }))
          .doesNotThrowAnyException();

      assertThat(entriesOf(failedUser)).hasSize(1);
      assertThat(entriesOf(rolledBackUser)).isEmpty();
      otherThread.shutdown();
    }

    @Test
    void thePermissionSummaryEventIsPersistedToo() throws Exception {
      String role = "audit-perm-" + UUID.randomUUID();

      template(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
          .executeWithoutResult(
              status -> publisher.publishEvent(PermissionImportedEvent.of(role, 5)));

      List<AuditLogEntry> all =
          otherThread
              .submit(
                  () ->
                      auditRepository
                          .findAll(
                              PageRequest.of(0, 2000, Sort.by(Sort.Direction.DESC, "occurredAt")))
                          .stream()
                          .filter(e -> role.equals(e.getActorRaw()))
                          .toList())
              .get(10, TimeUnit.SECONDS);
      assertThat(all).hasSize(1);
      assertThat(all.get(0).getOperationType()).isEqualTo("PERMISSION_IMPORTED");
      otherThread.shutdown();
    }
  }
}
