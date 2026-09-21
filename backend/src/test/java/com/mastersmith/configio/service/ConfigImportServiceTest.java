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

package com.mastersmith.configio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.common.security.Operator;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.event.ConfigImportEventPublisher;
import com.mastersmith.configio.event.ConfigImportExecutedEvent;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.Outcome;
import com.mastersmith.configio.exception.ConfigImportValidationException;
import com.mastersmith.configio.mapper.ConfigDocumentMapper;
import com.mastersmith.configio.parser.ConfigDocumentParser;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.RecordingTransactionManager;
import com.mastersmith.configio.validation.ReferenceValidator;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.permission.PermissionEngineApi;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link ConfigImportService}の単体テスト(3ユニットをモックし、トランザクションは記録用のマネージャーで再現する): 検証→反映の順序・{@code
 * REPEATABLE_READ}・全検証に合格した場合だけ反映・誤りは全件を集めて中止(何も反映しない)・
 * 失敗の分類の優先順位・取り込み1回につき1件の監査イベント(成功は確定後、失敗はトランザクションの外)・反映中の失敗とコミット時の失敗の扱い・確定後の動作の順序。
 */
class ConfigImportServiceTest {

  private static final Operator OPERATOR = new Operator("user-1", "session-1", "role-1");

  private ConfigEngineApi configEngineApi;
  private MenuStructureApi menuStructureApi;
  private PermissionEngineApi permissionEngineApi;
  private RecordingTransactionManager transactionManager;
  private final List<ConfigImportExecutedEvent> events = new ArrayList<>();
  private final List<String> callOrder = new ArrayList<>();
  private ConfigImportService service;
  private ConfigImportEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    configEngineApi = mock(ConfigEngineApi.class);
    menuStructureApi = mock(MenuStructureApi.class);
    permissionEngineApi = mock(PermissionEngineApi.class);
    transactionManager = new RecordingTransactionManager();
    eventPublisher = mock(ConfigImportEventPublisher.class);
    doAnswer(
            invocation -> {
              events.add(invocation.getArgument(0));
              callOrder.add(
                  "event:" + ((ConfigImportExecutedEvent) invocation.getArgument(0)).outcome());
              return null;
            })
        .when(eventPublisher)
        .publish(any());
    when(configEngineApi.getExportableConfigSet())
        .thenReturn(new ConfigExportSet(List.of(), List.of(), List.of()));
    when(configEngineApi.validateConfigSet(any())).thenReturn(List.of());
    when(menuStructureApi.validateMenuStructure(any())).thenReturn(List.of());
    when(permissionEngineApi.validateRbacImport(any(), any(), anyBoolean())).thenReturn(List.of());
    when(permissionEngineApi.isBootstrapState()).thenReturn(false);
    stubApply();
    ImportOrchestrator orchestrator =
        new ImportOrchestrator(
            configEngineApi,
            menuStructureApi,
            permissionEngineApi,
            new ConfigDocumentMapper(),
            new ReferenceValidator());
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    service =
        new ConfigImportService(
            new ConfigDocumentParser(),
            orchestrator,
            permissionEngineApi,
            eventPublisher,
            template,
            Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC));
  }

  private void stubApply() {
    when(configEngineApi.applyConfigSet(any()))
        .thenAnswer(
            i -> {
              callOrder.add("apply:config");
              return result(ImportSections.SCHEMA, new SectionCounts(1, 2, 3), "config");
            });
    when(menuStructureApi.applyMenuStructure(any()))
        .thenAnswer(
            i -> {
              callOrder.add("apply:menu");
              return result(ImportSections.MENU, new SectionCounts(4, 5, 6), "menu");
            });
    when(permissionEngineApi.applyRbacImport(any(), any()))
        .thenAnswer(
            i -> {
              callOrder.add("apply:rbac");
              return result(ImportSections.PRIMARY_PERMISSIONS, new SectionCounts(7, 8, 9), "rbac");
            });
  }

  private ApplyResult result(String section, SectionCounts counts, String unit) {
    return new ApplyResult(
        Map.of(section, counts),
        new PostCommit(
            () -> callOrder.add("invalidate:" + unit), () -> callOrder.add("publish:" + unit)));
  }

  private static JsonNode validBody() {
    return ConfigDocumentFactory.toJson(ConfigDocumentFactory.mechanical(Spec.small()));
  }

  // ---- 成功 ----

  @Test
  void aValidImportValidatesThenAppliesInOneRepeatableReadTransactionAndReturnsTheCounts() {
    ImportResult result = service.importConfig(validBody(), OPERATOR);

    assertThat(result.outcome()).isEqualTo("SUCCESS");
    assertThat(result.sections().keySet())
        .containsExactly(
            ImportSections.SCHEMA, ImportSections.MENU, ImportSections.PRIMARY_PERMISSIONS);
    assertThat(result.sections().get(ImportSections.MENU)).isEqualTo(new SectionCounts(4, 5, 6));
    assertThat(transactionManager.log)
        .containsExactly(
            "begin isolation=%d readOnly=false propagation=0"
                .formatted(TransactionDefinition.ISOLATION_REPEATABLE_READ),
            "commit");
  }

  @Test
  void validationOfAllThreeUnitsRunsBeforeAnyApplyAndApplyOrderIsSchemaMenuRbac() {
    service.importConfig(validBody(), OPERATOR);

    InOrder inOrder = inOrder(permissionEngineApi, configEngineApi, menuStructureApi);
    inOrder.verify(permissionEngineApi).isBootstrapState();
    inOrder.verify(configEngineApi).validateConfigSet(any());
    inOrder.verify(menuStructureApi).validateMenuStructure(any());
    inOrder.verify(permissionEngineApi).validateRbacImport(any(), any(), anyBoolean());
    inOrder.verify(configEngineApi).applyConfigSet(any());
    inOrder.verify(menuStructureApi).applyMenuStructure(any());
    inOrder.verify(permissionEngineApi).applyRbacImport(any(), any());
  }

  @Test
  void bootstrapAtStartIsFixedAtTheStartAndPassedToTheValidationTogetherWithTheActorRole() {
    when(permissionEngineApi.isBootstrapState()).thenReturn(true);

    service.importConfig(validBody(), OPERATOR);

    verify(permissionEngineApi).validateRbacImport(any(), eq("role-1"), eq(true));
    verify(permissionEngineApi).applyRbacImport(any(), eq("role-1"));
  }

  @Test
  void aSuccessEventIsPublishedExactlyOnceAfterTheCommitAndAfterAllPostCommitActions() {
    service.importConfig(validBody(), OPERATOR);

    assertThat(callOrder)
        .containsSubsequence("apply:config", "apply:menu", "apply:rbac")
        .endsWith(
            "invalidate:config",
            "invalidate:menu",
            "invalidate:rbac",
            "publish:config",
            "publish:menu",
            "publish:rbac",
            "event:SUCCESS");
    assertThat(events).hasSize(1);
    ConfigImportExecutedEvent event = events.get(0);
    assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
    assertThat(event.actorUserId()).isEqualTo("user-1");
    assertThat(event.actorRoleId()).isEqualTo("role-1");
    assertThat(event.sections()).containsKey(ImportSections.SCHEMA);
    assertThat(event.failureCategory()).isNull();
  }

  @Test
  void anExceptionInOnePostCommitActionDoesNotPreventTheOthersOrTheSuccessResponse() {
    when(configEngineApi.applyConfigSet(any()))
        .thenReturn(
            new ApplyResult(
                Map.of(),
                new PostCommit(
                    () -> {
                      throw new IllegalStateException("invalidate failed");
                    },
                    () -> callOrder.add("publish:config"))));

    ImportResult result = service.importConfig(validBody(), OPERATOR);

    assertThat(result.outcome()).isEqualTo("SUCCESS");
    assertThat(callOrder)
        .contains("invalidate:menu", "invalidate:rbac", "publish:config", "event:SUCCESS");
  }

  // ---- 検証の誤り(422) ----

  @Test
  void allValidationErrorsFromAllUnitsAreCollectedAndNothingIsApplied() {
    when(configEngineApi.validateConfigSet(any()))
        .thenReturn(
            List.of(
                ImportValidationError.of("tables[0].tableName", ImportMessageKeys.FIELD_REQUIRED)));
    when(menuStructureApi.validateMenuStructure(any()))
        .thenReturn(
            List.of(
                ImportValidationError.of("menu.items[0].label", ImportMessageKeys.FIELD_REQUIRED)));
    when(permissionEngineApi.validateRbacImport(any(), any(), anyBoolean()))
        .thenReturn(
            List.of(
                ImportValidationError.of(
                    "primaryPermissions[2].level", ImportMessageKeys.FIELD_REQUIRED)));

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> {
              assertThat(e.category()).isEqualTo(FailureCategory.VALIDATION_ERROR);
              assertThat(e.errors())
                  .extracting(ImportValidationError::field)
                  .containsExactly(
                      "schema.tables[0].tableName",
                      "menu.items[0].label",
                      "rbac.primaryPermissions[2].level");
              assertThat(e.totalErrorCount()).isEqualTo(3);
              assertThat(e.truncated()).isFalse();
            });

    verify(configEngineApi, never()).applyConfigSet(any());
    verify(menuStructureApi, never()).applyMenuStructure(any());
    verify(permissionEngineApi, never()).applyRbacImport(any(), any());
    assertThat(transactionManager.log).endsWith("rollback").doesNotContain("commit");
  }

  @Test
  void structuralErrorsAndUnitErrorsAreReportedTogether() {
    ObjectNode body = (ObjectNode) validBody().deepCopy();
    ((ObjectNode) body.at("/schema/tables/0")).remove("displayOrder");
    when(menuStructureApi.validateMenuStructure(any()))
        .thenReturn(
            List.of(
                ImportValidationError.of("menu.items[0].label", ImportMessageKeys.FIELD_REQUIRED)));

    assertThatThrownBy(() -> service.importConfig(body, OPERATOR))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e ->
                assertThat(e.errors())
                    .extracting(ImportValidationError::field)
                    .containsExactly("schema.tables[0].displayOrder", "menu.items[0].label"));
  }

  @Test
  void anEscalationAmongOtherErrorsIsClassifiedAsEscalationDeniedFirst() {
    when(configEngineApi.validateConfigSet(any()))
        .thenReturn(
            List.of(ImportValidationError.of("tables[0]", ImportMessageKeys.FIELD_REQUIRED)));
    when(permissionEngineApi.validateRbacImport(any(), any(), anyBoolean()))
        .thenReturn(
            List.of(
                ImportValidationError.of("primaryPermissions", ImportMessageKeys.RBAC_EMPTY),
                ImportValidationError.of(
                    "primaryPermissions[0]", ImportMessageKeys.RBAC_ESCALATION)));

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> assertThat(e.category()).isEqualTo(FailureCategory.ESCALATION_DENIED));
    assertThat(events.get(0).failureCategory()).isEqualTo(FailureCategory.ESCALATION_DENIED);
    assertThat(events.get(0).errorCount()).isEqualTo(3);
  }

  @Test
  void emptyPrimaryPermissionsAmongOtherErrorsIsClassifiedAsRbacEmptyBeforeValidationError() {
    when(configEngineApi.validateConfigSet(any()))
        .thenReturn(
            List.of(ImportValidationError.of("tables[0]", ImportMessageKeys.FIELD_REQUIRED)));
    when(permissionEngineApi.validateRbacImport(any(), any(), anyBoolean()))
        .thenReturn(
            List.of(ImportValidationError.of("primaryPermissions", ImportMessageKeys.RBAC_EMPTY)));

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> assertThat(e.category()).isEqualTo(FailureCategory.RBAC_EMPTY));
  }

  @Test
  void aFailureEventIsPublishedOnceWithTheTotalErrorCountAndNoSuccessEvent() {
    when(configEngineApi.validateConfigSet(any()))
        .thenReturn(
            List.of(ImportValidationError.of("tables[0]", ImportMessageKeys.FIELD_REQUIRED)));

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOf(ConfigImportValidationException.class);

    assertThat(events).hasSize(1);
    assertThat(events.get(0).outcome()).isEqualTo(Outcome.FAILURE);
    assertThat(events.get(0).failureCategory()).isEqualTo(FailureCategory.VALIDATION_ERROR);
    assertThat(events.get(0).errorCount()).isEqualTo(1);
    assertThat(events.get(0).sections()).isNull();
  }

  @Test
  void moreThanAHundredErrorsAreCountedButOnlyAHundredAreReturned() {
    List<ImportValidationError> many =
        java.util.stream.IntStream.range(0, 130)
            .mapToObj(
                i ->
                    ImportValidationError.of("tables[" + i + "]", ImportMessageKeys.FIELD_REQUIRED))
            .toList();
    when(configEngineApi.validateConfigSet(any())).thenReturn(many);

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> {
              assertThat(e.errors()).hasSize(100);
              assertThat(e.totalErrorCount()).isEqualTo(130);
              assertThat(e.truncated()).isTrue();
            });
    assertThat(events.get(0).errorCount()).isEqualTo(130);
  }

  // ---- 構文・形式(トランザクションを開かない) ----

  @Test
  void aMalformedBodyFailsBeforeAnyTransactionWithAMalformedEvent() {
    JsonNode notAnObject = ConfigDocumentFactory.JSON.readTree("[1,2]");

    assertThatThrownBy(() -> service.importConfig(notAnObject, OPERATOR))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> assertThat(e.category()).isEqualTo(FailureCategory.MALFORMED));

    assertThat(transactionManager.log).isEmpty();
    assertThat(events)
        .extracting(ConfigImportExecutedEvent::failureCategory)
        .containsExactly(FailureCategory.MALFORMED);
  }

  @Test
  void anUnsupportedFormatVersionFailsBeforeAnyTransactionWithThatCategory() {
    ObjectNode body = (ObjectNode) validBody().deepCopy();
    body.put("formatVersion", 2);

    assertThatThrownBy(() -> service.importConfig(body, OPERATOR))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> assertThat(e.category()).isEqualTo(FailureCategory.UNSUPPORTED_FORMAT));

    assertThat(transactionManager.log).isEmpty();
    assertThat(events.get(0).failureCategory()).isEqualTo(FailureCategory.UNSUPPORTED_FORMAT);
  }

  @Test
  void recordMalformedPublishesOneMalformedFailureEventForTheOperator() {
    service.recordMalformed(OPERATOR);

    assertThat(events).hasSize(1);
    assertThat(events.get(0).failureCategory()).isEqualTo(FailureCategory.MALFORMED);
    assertThat(events.get(0).actorUserId()).isEqualTo("user-1");
    assertThat(events.get(0).errorCount()).isEqualTo(1);
  }

  // ---- 内部の障害 ----

  @Test
  void aFailureDuringApplyRollsEverythingBackPublishesAnUnexpectedEventAndRethrows() {
    when(menuStructureApi.applyMenuStructure(any())).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    assertThat(transactionManager.log).endsWith("rollback").doesNotContain("commit");
    // 確定していないため、確定後の動作(無効化・個別イベント・成功のイベント)は、呼ばれない。
    assertThat(callOrder).doesNotContain("invalidate:config", "publish:config", "event:SUCCESS");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).failureCategory()).isEqualTo(FailureCategory.UNEXPECTED);
    assertThat(events.get(0).outcome()).isEqualTo(Outcome.FAILURE);
  }

  @Test
  void aFailureAtCommitTimeIsClassifiedAsUnexpectedAndNoSuccessEventIsPublished() {
    transactionManager.failOnCommit = true;

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOf(org.springframework.transaction.TransactionException.class);

    assertThat(callOrder).doesNotContain("invalidate:config", "event:SUCCESS");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).failureCategory()).isEqualTo(FailureCategory.UNEXPECTED);
  }

  @Test
  void theFailureEventIsPublishedOutsideTheImportTransaction() {
    when(permissionEngineApi.applyRbacImport(any(), any()))
        .thenThrow(new IllegalStateException("boom"));
    doAnswer(
            invocation -> {
              // 失敗のイベントの発行の時点で、取り込みのトランザクションは、すでに終了している(ロールバック済み)。
              callOrder.add("log-at-publish:" + String.join(",", transactionManager.log));
              return null;
            })
        .when(eventPublisher)
        .publish(any());

    assertThatThrownBy(() -> service.importConfig(validBody(), OPERATOR))
        .isInstanceOf(IllegalStateException.class);

    assertThat(callOrder.get(callOrder.size() - 1)).endsWith("rollback");
  }

  @Test
  void classifyFollowsThePriorityEscalationThenEmptyThenValidation() {
    com.mastersmith.configio.parser.ImportErrorCollector none =
        new com.mastersmith.configio.parser.ImportErrorCollector();
    none.add("x", ImportMessageKeys.FIELD_REQUIRED);
    assertThat(ConfigImportService.classify(none)).isEqualTo(FailureCategory.VALIDATION_ERROR);
    none.add("y", ImportMessageKeys.RBAC_EMPTY);
    assertThat(ConfigImportService.classify(none)).isEqualTo(FailureCategory.RBAC_EMPTY);
    none.add("z", ImportMessageKeys.RBAC_ESCALATION);
    assertThat(ConfigImportService.classify(none)).isEqualTo(FailureCategory.ESCALATION_DENIED);
  }
}
