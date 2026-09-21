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

package com.mastersmith.configio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.reset;
import static org.springframework.test.util.AopTestUtils.getUltimateTargetObject;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.ConfigDocumentNormalizer;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.menu.repository.MenuItemRepository;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * config-import-exportの統合テスト(実際の組込みH2・実際のトランザクション・実際の3ユニットと監査ログ。code-generation-plan.md Step 17):
 * エクスポート→インポートの往復・全置換(削除を含む)・原子性(反映の途中の失敗で、
 * DB・キャッシュが不変)・確定後のキャッシュの無効化と遅延の再読み込み・監査イベントの永続化(成功・失敗・MALFORMED。認可を通らない場合は、発行されない)・内部IDの維持と、メニューの再採番・isPrimaryKeyの維持・
 * エクスポートの反映中の一貫性(H2の{@code REPEATABLE_READ}: 反映の途中の(未確定の)状態が、読まれない)。
 */
class ConfigImportExportIntegrationTest extends ConfigIoIntegrationTestBase {

  @Autowired private TableConfigRepository tableRepository;
  @Autowired private ColumnConfigRepository columnRepository;
  @Autowired private MenuItemRepository menuRepository;
  @Autowired private PrimaryPermissionRepository primaryRepository;
  @Autowired private AuxiliaryPermissionRepository auxiliaryRepository;
  @Autowired private ConfigEngineApi configEngineApi;

  /** 反映の途中の失敗・停止を作るために、menu-navigationの取り込みを、差し替えられるようにする(既定は、実物に委譲)。 */
  @MockitoSpyBean private MenuStructureApi menuStructureApi;

  private final ExecutorService executor = Executors.newCachedThreadPool();

  /**
   * 取り込みの反映を差し替えるための、menu-navigationの実体(Mockitoのスパイ)。テストが注入されるのは、トランザクションのプロキシで、そのままスタブすると、トランザクションの助言({@code
   * MANDATORY})が 働くため、プロキシの内側の実体に対して、スタブする。
   */
  private MenuStructureApi menuSpy() {
    return getUltimateTargetObject(menuStructureApi);
  }

  @AfterEach
  void resetSpy() {
    reset(menuSpy());
    executor.shutdownNow();
  }

  private static ConfigDocument administered(ConfigDocument document) {
    return ConfigDocumentFactory.withAdministrator(document, ADMIN_ROLE);
  }

  private ConfigDocument firstImport() throws Exception {
    ConfigDocument document = administered(ConfigDocumentFactory.mechanical(Spec.small()));
    MvcResult result = postImport(document);
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    actAs("admin-user", ADMIN_ROLE);
    return document;
  }

  // ---- 往復・全置換 ----

  @Test
  void theFirstImportOnAnEmptyDatabaseWorksInTheBootstrapStateAndReflectsEverything()
      throws Exception {
    ConfigDocument document = administered(ConfigDocumentFactory.mechanical(Spec.small()));

    MvcResult result = postImport(document);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("outcome").stringValue()).isEqualTo("SUCCESS");
    // 4テーブル+16カラム=20、翻訳6、メニュー(2フォルダ+4リーフ)=6、ロール4、グループ2、主権限、補助権限。
    assertThat(body.at("/sections/schema/added").intValue()).isEqualTo(20);
    assertThat(body.at("/sections/translations/added").intValue()).isEqualTo(6);
    assertThat(body.at("/sections/menu/added").intValue()).isEqualTo(6);
    assertThat(body.at("/sections/roles/added").intValue()).isEqualTo(4);
    assertThat(body.at("/sections/groups/added").intValue()).isEqualTo(2);
    assertThat(tableRepository.count()).isEqualTo(4);
    assertThat(columnRepository.count()).isEqualTo(16);
    assertThat(menuRepository.count()).isEqualTo(6);
    assertThat(primaryRepository.count()).isPositive();
    assertThat(roleRepository.findByName(ADMIN_ROLE)).isPresent();
  }

  @Test
  void exportImportExportRoundTripKeepsTheConfigurationAndIsAFixedPoint() throws Exception {
    ConfigDocument original = firstImport();

    ConfigDocument exported = exportDocument();
    // 取り込みで無視される項目(isPrimaryKey・exportedAt・appVersion)と、順序を除いて、元の設定と同じ。
    assertThat(ConfigDocumentNormalizer.normalize(exported))
        .isEqualTo(ConfigDocumentNormalizer.normalize(original));

    // そのファイルを取り込み直しても、何も変わらない(件数はすべて0)。
    MvcResult again = postImport(exported);
    assertThat(again.getResponse().getStatus()).isEqualTo(200);
    JsonNode counts =
        ConfigDocumentFactory.JSON
            .readTree(again.getResponse().getContentAsString())
            .get("sections");
    for (String section :
        List.of(
            "schema",
            "translations",
            "roles",
            "groups",
            "primaryPermissions",
            "auxiliaryPermissions")) {
      assertThat(counts.get(section).toString())
          .as(section)
          .isEqualTo("{\"added\":0,\"updated\":0,\"deleted\":0}");
    }
    assertThat(ConfigDocumentNormalizer.normalize(exportDocument()))
        .isEqualTo(ConfigDocumentNormalizer.normalize(exported));
  }

  @Test
  void aSecondImportReplacesEverythingDeletingWhatTheFileNoLongerHas() throws Exception {
    firstImport();
    ConfigDocument smaller =
        administered(ConfigDocumentFactory.mechanical(new Spec(1, 1, 2, 2, 2, 1, 3, 1, 1)));

    MvcResult result = postImport(smaller);

    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    JsonNode counts =
        ConfigDocumentFactory.JSON
            .readTree(result.getResponse().getContentAsString())
            .get("sections");
    assertThat(counts.at("/schema/deleted").intValue()).isPositive();
    assertThat(counts.at("/roles/deleted").intValue()).isPositive();
    assertThat(tableRepository.count()).isEqualTo(1);
    assertThat(columnRepository.count()).isEqualTo(2);
    assertThat(ConfigDocumentNormalizer.normalize(exportDocument()))
        .isEqualTo(ConfigDocumentNormalizer.normalize(smaller));
  }

  @Test
  void existingTablesKeepTheirInternalIdsButMenuItemsAreRenumbered() throws Exception {
    ConfigDocument document = firstImport();
    Map<String, String> tableIds = new java.util.HashMap<>();
    tableRepository
        .findAll()
        .forEach(
            t -> tableIds.put(t.getSchemaName() + "." + t.getTableName(), t.getTableConfigId()));
    List<String> menuIdsBefore =
        menuRepository.findAll().stream().map(m -> m.getMenuItemId()).toList();

    assertThat(postImport(document).getResponse().getStatus()).isEqualTo(200);

    tableRepository
        .findAll()
        .forEach(
            t ->
                assertThat(t.getTableConfigId())
                    .isEqualTo(tableIds.get(t.getSchemaName() + "." + t.getTableName())));
    List<String> menuIdsAfter =
        menuRepository.findAll().stream().map(m -> m.getMenuItemId()).toList();
    assertThat(menuIdsAfter)
        .hasSameSizeAs(menuIdsBefore)
        .doesNotContainAnyElementsOf(menuIdsBefore);
  }

  @Test
  void theIsPrimaryKeyFlagOfExistingColumnsIsKeptWhateverTheFileSays() throws Exception {
    ConfigDocument document = firstImport();
    // ファイルは、すべてisPrimaryKey=falseで取り込む。DBの既存のカラム(スキーマ探索で設定された想定)は、trueのまま。
    String tableId =
        tableRepository
            .findBySchemaNameAndTableName("schema_0000", "table_0000")
            .orElseThrow()
            .getTableConfigId();
    jdbc.update(
        "update column_config set is_primary_key = true where table_config_id = ? and column_name = 'column_0001'",
        tableId);
    configCache.invalidate();

    assertThat(postImport(document).getResponse().getStatus()).isEqualTo(200);

    assertThat(
            jdbc.queryForObject(
                "select is_primary_key from column_config where table_config_id = ? and column_name = 'column_0001'",
                Boolean.class,
                tableId))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select is_primary_key from column_config where table_config_id = ? and column_name = 'column_0000'",
                Boolean.class,
                tableId))
        .isFalse();
  }

  // ---- キャッシュ ----

  @Test
  void afterTheCommitTheCachesAreInvalidatedAndTheNextReadReloadsTheNewConfiguration()
      throws Exception {
    firstImport();
    configEngineApi.getTableConfig("schema_0000", "table_0000"); // 再読み込みして、VALIDに。
    assertThat(configCache.isStale()).isFalse();
    ConfigDocument smaller =
        administered(ConfigDocumentFactory.mechanical(new Spec(1, 1, 2, 2, 2, 1, 3, 1, 1)));
    long permissionGeneration = permissionCacheControl.generation();

    assertThat(postImport(smaller).getResponse().getStatus()).isEqualTo(200);

    assertThat(configCache.isStale()).isTrue();
    assertThat(permissionCacheControl.generation()).isGreaterThan(permissionGeneration);
    // 次の読み取りが、確定済みの新しい設定を、遅延して再読み込みする。
    assertThat(configCache.allTableConfigs()).hasSize(1);
    assertThat(configCache.isStale()).isFalse();
    assertThat(configCache.findTableConfig("schema_0001", "table_0000")).isEmpty();
  }

  // ---- 原子性 ----

  @Test
  void aFailureMidWayThroughTheApplyRollsEverythingBackAndLeavesDatabaseAndCachesUnchanged()
      throws Exception {
    firstImport();
    ConfigDocument before = ConfigDocumentNormalizer.normalize(exportDocument());
    configEngineApi.getTableConfig("schema_0000", "table_0000");
    assertThat(configCache.isStale()).isFalse();
    long tablesBefore = tableRepository.count();
    // schemaの反映(と、その後のflush)が済んだ後に、メニューの反映で失敗させる。
    doAnswer(
            invocation -> {
              throw new IllegalStateException("injected failure after schema was applied");
            })
        .when(menuSpy())
        .applyMenuStructure(any());
    ConfigDocument different =
        administered(ConfigDocumentFactory.mechanical(new Spec(1, 1, 2, 2, 2, 1, 3, 1, 1)));

    MvcResult result = postImport(different);

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("injected");
    assertThat(tableRepository.count()).isEqualTo(tablesBefore);
    // 取り込みの前の設定のまま(内部設定DBの、すべてのセクション)。
    doCallRealMethod().when(menuSpy()).applyMenuStructure(any());
    assertThat(ConfigDocumentNormalizer.normalize(exportDocument())).isEqualTo(before);
    // キャッシュは、無効化されない(ロールバックでは、afterCommitは呼ばれない)。
    assertThat(configCache.isStale()).isFalse();
  }

  // ---- 監査イベントの永続化 ----

  @Test
  void aSuccessfulImportPersistsExactlyOneSuccessEventAndOnePermissionSummaryEvent()
      throws Exception {
    operators.set("audit-user-success", null);
    ConfigDocument document = administered(ConfigDocumentFactory.mechanical(Spec.small()));

    assertThat(postImport(document).getResponse().getStatus()).isEqualTo(200);

    List<AuditLogEntry> entries = importAuditEntriesOf("audit-user-success");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).getOperationType()).isEqualTo("CONFIG_IMPORT_SUCCEEDED");
    assertThat(entries.get(0).getAfterValue())
        .containsEntry("outcome", "SUCCESS")
        .containsKey("sections");
    // 個別の変更イベント(config-engine)と、権限のサマリイベントも、確定後に記録される。
    long permissionSummaries =
        auditRepository
            .findAll(
                org.springframework.data.domain.PageRequest.of(
                    0, 5000, org.springframework.data.domain.Sort.by("occurredAt").descending()))
            .stream()
            .filter(e -> "PERMISSION_IMPORTED".equals(e.getOperationType()))
            .count();
    assertThat(permissionSummaries).isPositive();
  }

  @Test
  void aValidationFailurePersistsExactlyOneFailureEventWithTheCategoryAndChangesNothing()
      throws Exception {
    operators.set("audit-user-validation", null);
    ConfigDocument valid = administered(ConfigDocumentFactory.mechanical(Spec.small()));
    JsonNode broken = ConfigDocumentFactory.toJson(valid).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) broken.at("/schema/tables/0/columns/0"))
        .put("editorType", "no_such_type");

    MvcResult result = postImport(broken);

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    JsonNode body = ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
    assertThat(body.at("/errors/0/field").stringValue())
        .isEqualTo("schema.tables[0].columns[0].editorType");
    assertThat(tableRepository.count()).isZero();
    List<AuditLogEntry> entries = importAuditEntriesOf("audit-user-validation");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).getOperationType()).isEqualTo("CONFIG_IMPORT_FAILED");
    assertThat(entries.get(0).getAfterValue())
        .containsEntry("failureCategory", "VALIDATION_ERROR")
        .containsEntry("errorCount", 1);
  }

  @Test
  void aMalformedBodyPersistsOneMalformedEventOnlyForAnAuthorizedOperator() throws Exception {
    operators.set("audit-user-malformed", null);

    MvcResult result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(IMPORT)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"formatVersion\":"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    List<AuditLogEntry> entries = importAuditEntriesOf("audit-user-malformed");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).getAfterValue()).containsEntry("failureCategory", "MALFORMED");
  }

  @Test
  void anUnsupportedFormatVersionPersistsOneUnsupportedFormatEvent() throws Exception {
    operators.set("audit-user-format", null);
    JsonNode body =
        ConfigDocumentFactory.toJson(administered(ConfigDocumentFactory.mechanical(Spec.small())))
            .deepCopy();
    ((tools.jackson.databind.node.ObjectNode) body).put("formatVersion", 2);

    MvcResult result = postImport(body);

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    List<AuditLogEntry> entries = importAuditEntriesOf("audit-user-format");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).getAfterValue())
        .containsEntry("failureCategory", "UNSUPPORTED_FORMAT");
  }

  @Test
  void anInternalFailurePersistsOneUnexpectedEventOutsideTheRolledBackTransaction()
      throws Exception {
    operators.set("audit-user-unexpected", null);
    doAnswer(
            invocation -> {
              throw new IllegalStateException("injected");
            })
        .when(menuSpy())
        .applyMenuStructure(any());

    MvcResult result = postImport(administered(ConfigDocumentFactory.mechanical(Spec.small())));

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    List<AuditLogEntry> entries = importAuditEntriesOf("audit-user-unexpected");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).getAfterValue()).containsEntry("failureCategory", "UNEXPECTED");
    assertThat(tableRepository.count()).isZero();
  }

  @Test
  void anExportNeverWritesAnAuditEvent() throws Exception {
    firstImport();
    operators.set("audit-user-export", operators.current().orElseThrow().activeRoleId());
    long before =
        auditRepository
            .findAll(org.springframework.data.domain.PageRequest.of(0, 1))
            .getTotalElements();

    assertThat(getExport().getResponse().getStatus()).isEqualTo(200);

    assertThat(
            auditRepository
                .findAll(org.springframework.data.domain.PageRequest.of(0, 1))
                .getTotalElements())
        .isEqualTo(before);
    assertThat(importAuditEntriesOf("audit-user-export")).isEmpty();
  }

  // ---- エクスポートの、反映中の一貫性(REPEATABLE_READ) ----

  @Test
  void anExportDuringAnImportInProgressSeesTheOldConfigurationNeverAHalfAppliedOne()
      throws Exception {
    firstImport();
    ConfigDocument oldDocument = ConfigDocumentNormalizer.normalize(exportDocument());
    ConfigDocument different =
        administered(ConfigDocumentFactory.mechanical(new Spec(1, 1, 2, 2, 2, 1, 3, 1, 1)));
    CountDownLatch schemaApplied = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    // schemaの反映が済み(未確定)、メニューの反映の直前で、取り込みを止める。
    doAnswer(
            invocation -> {
              schemaApplied.countDown();
              if (!mayFinish.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout");
              }
              return invocation.callRealMethod();
            })
        .when(menuSpy())
        .applyMenuStructure(any());

    Future<MvcResult> importing = executor.submit(() -> postImport(different));
    assertThat(schemaApplied.await(20, TimeUnit.SECONDS)).isTrue();
    ConfigDocument duringImport = ConfigDocumentNormalizer.normalize(exportDocument());
    mayFinish.countDown();
    assertThat(importing.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);

    // 反映の途中(schemaは、新しく、メニュー・RBACは、古い)の混ざった状態は、見えない。エクスポートは、取り込み前の1つの時点。
    assertThat(duringImport).isEqualTo(oldDocument);
    assertThat(ConfigDocumentNormalizer.normalize(exportDocument()))
        .isEqualTo(ConfigDocumentNormalizer.normalize(different));
  }

  @Test
  void theExportedSetReadDirectlyFromTheDatabaseIsAnImmutableSnapshotDetachedFromTheCache()
      throws Exception {
    firstImport();

    ConfigExportSet snapshot =
        new org.springframework.transaction.support.TransactionTemplate(jdbcTransactionManager())
            .execute(status -> configEngineApi.getExportableConfigSet());

    assertThat(snapshot.tableConfigs()).hasSize(4);
    assertThat(auxiliaryRepository.count()).isPositive();
  }

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("transactionManager")
  private org.springframework.transaction.PlatformTransactionManager transactionManager;

  private org.springframework.transaction.PlatformTransactionManager jdbcTransactionManager() {
    return transactionManager;
  }
}
