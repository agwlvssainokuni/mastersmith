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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mastersmith.common.security.Operator;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.TableConfigSnapshot;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.mapper.ConfigDocumentMapper;
import com.mastersmith.configio.testsupport.RecordingTransactionManager;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.menu.dto.MenuStructureEntry;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.permission.dto.RbacExport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ConfigExportService}のテスト(BR9.5・NFR4.4): 読み取り専用・{@code
 * REPEATABLE_READ}の1つのトランザクションの中で、schema→menu→rbacの順に読むこと。監査イベントを発行しないこと。
 */
class ConfigExportServiceTest {

  private static final Operator OPERATOR = new Operator("user-1", "session-1", "role-1");

  private ConfigEngineApi configEngineApi;
  private MenuStructureApi menuStructureApi;
  private PermissionEngineApi permissionEngineApi;
  private RecordingTransactionManager transactionManager;
  private ConfigExportService service;
  private final List<String> order = new ArrayList<>();

  @BeforeEach
  void setUp() {
    configEngineApi = mock(ConfigEngineApi.class);
    menuStructureApi = mock(MenuStructureApi.class);
    permissionEngineApi = mock(PermissionEngineApi.class);
    transactionManager = new RecordingTransactionManager();
    when(configEngineApi.getExportableConfigSet())
        .thenAnswer(
            i -> {
              order.add("schema@" + transactionManager.log.size());
              return new ConfigExportSet(
                  List.of(new TableConfigSnapshot("id-1", "s_1", "t_1", 1, null)),
                  List.of(),
                  List.of());
            });
    when(menuStructureApi.getExportableMenuStructure())
        .thenAnswer(
            i -> {
              order.add("menu");
              return List.<MenuStructureEntry>of();
            });
    when(permissionEngineApi.exportRbac())
        .thenAnswer(
            i -> {
              order.add("rbac");
              return new RbacExport(List.of(), List.of(), List.of(), List.of());
            });
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setReadOnly(true);
    service =
        new ConfigExportService(
            configEngineApi,
            menuStructureApi,
            permissionEngineApi,
            new ConfigDocumentMapper(),
            template,
            Clock.fixed(Instant.parse("2026-09-21T01:02:03Z"), ZoneOffset.UTC),
            "1.2.3");
  }

  @Test
  void readsThreeSectionsInOrderInsideOneReadOnlyRepeatableReadTransaction() {
    ConfigDocument document = service.export(OPERATOR);

    assertThat(order).containsExactly("schema@1", "menu", "rbac");
    assertThat(transactionManager.log)
        .containsExactly(
            "begin isolation=%d readOnly=true propagation=0"
                .formatted(TransactionDefinition.ISOLATION_REPEATABLE_READ),
            "commit");
    assertThat(document.formatVersion()).isEqualTo(1);
    assertThat(document.exportedAt()).isEqualTo("2026-09-21T01:02:03Z");
    assertThat(document.appVersion()).isEqualTo("1.2.3");
    assertThat(document.schema().tables()).hasSize(1);
  }

  @Test
  void aFailureWhileReadingPropagatesAndRollsBackWithoutPublishingAnything() {
    doAnswer(
            i -> {
              throw new org.springframework.dao.DataAccessResourceFailureException("db down");
            })
        .when(permissionEngineApi)
        .exportRbac();

    assertThatThrownBy(() -> service.export(OPERATOR))
        .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);

    assertThat(transactionManager.log).endsWith("rollback");
  }

  @Test
  void aMissingAppVersionFallsBackToAnUnknownMarker() {
    ConfigExportService withoutVersion =
        new ConfigExportService(
            configEngineApi,
            menuStructureApi,
            permissionEngineApi,
            new ConfigDocumentMapper(),
            new TransactionTemplate(transactionManager),
            Clock.systemUTC(),
            null);

    assertThat(withoutVersion.export(OPERATOR).appVersion()).isEqualTo("unknown");
  }

  @Test
  void exportNeverTouchesTheEventMachineryBecauseItHasNoDependencyOnIt() {
    // エクスポートは、監査イベントを発行しない(BR9.16)。サービスは、イベントの発行部品を、持たない。
    verifyNoInteractions(permissionEngineApi);
    assertThat(ConfigExportService.class.getDeclaredFields())
        .noneMatch(f -> f.getType().getSimpleName().contains("EventPublisher"));
  }
}
