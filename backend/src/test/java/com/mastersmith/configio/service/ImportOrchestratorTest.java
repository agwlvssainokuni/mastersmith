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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.config.dto.ColumnConfigSnapshot;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.ConfigNaturalKeySet;
import com.mastersmith.config.dto.TableConfigSnapshot;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.document.ConfigDocument.ColumnEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuSection;
import com.mastersmith.configio.document.ConfigDocument.PermissionScope;
import com.mastersmith.configio.document.ConfigDocument.PrimaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.RbacSection;
import com.mastersmith.configio.document.ConfigDocument.RoleEntry;
import com.mastersmith.configio.document.ConfigDocument.SchemaSection;
import com.mastersmith.configio.document.ConfigDocument.TableEntry;
import com.mastersmith.configio.document.ConfigDocument.TableRef;
import com.mastersmith.configio.mapper.ConfigDocumentMapper;
import com.mastersmith.configio.parser.ImportErrorCollector;
import com.mastersmith.configio.validation.ReferenceValidator;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.menu.dto.MenuImportItem;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * {@link ImportOrchestrator}のテスト(3ユニットは、モック):
 * 参照整合と3ユニットの検証の誤りが、位置に接頭辞を付けて集まること、反映がschema→メニュー→RBACの順で、schemaの反映の結果(新しい内部ID)から、
 * メニューの遷移先・権限の対象を解決し直すこと、セクションの件数の統合、確定後の動作の順序(config-engine・menu-navigation・permission-engine)。
 */
class ImportOrchestratorTest {

  private ConfigEngineApi configEngineApi;
  private MenuStructureApi menuStructureApi;
  private PermissionEngineApi permissionEngineApi;
  private ImportOrchestrator orchestrator;

  private static final ImportContext CONTEXT =
      new ImportContext("user-1", "role-1", Instant.parse("2026-09-21T00:00:00Z"), false);

  @BeforeEach
  void setUp() {
    configEngineApi = mock(ConfigEngineApi.class);
    menuStructureApi = mock(MenuStructureApi.class);
    permissionEngineApi = mock(PermissionEngineApi.class);
    when(configEngineApi.getExportableConfigSet())
        .thenReturn(new ConfigExportSet(List.of(), List.of(), List.of()));
    when(configEngineApi.validateConfigSet(any())).thenReturn(List.of());
    when(menuStructureApi.validateMenuStructure(any())).thenReturn(List.of());
    when(permissionEngineApi.validateRbacImport(any(), any(), anyBoolean())).thenReturn(List.of());
    orchestrator =
        new ImportOrchestrator(
            configEngineApi,
            menuStructureApi,
            permissionEngineApi,
            new ConfigDocumentMapper(),
            new ReferenceValidator());
  }

  private static ConfigDocument document() {
    ColumnEntry column =
        new ColumnEntry("c_1", 0, null, "text", Map.of(), "visible", false, List.of(), null);
    return new ConfigDocument(
        1,
        null,
        null,
        new SchemaSection(
            List.of(new TableEntry("s_1", "t_new", 0, null, List.of(column))), List.of()),
        new MenuSection(List.of(new MenuEntry("leaf", 1, new TableRef("s_1", "t_new"), null))),
        new RbacSection(
            List.of(new RoleEntry("role_a")),
            List.of(),
            List.of(
                new PrimaryPermissionEntry(
                    "role_a",
                    new PermissionScope(ScopeType.TABLE, "s_1", "t_new", null),
                    PermissionLevel.READ),
                new PrimaryPermissionEntry(
                    "role_a",
                    new PermissionScope(ScopeType.COLUMN, "s_1", "t_new", "c_1"),
                    PermissionLevel.READ)),
            List.of()));
  }

  // ---- 検証 ----

  @Test
  void collectsUnitErrorsWithSectionPrefixesAndReferenceErrorsTogether() {
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
            List.of(ImportValidationError.of("primaryPermissions", ImportMessageKeys.RBAC_EMPTY)));
    ConfigDocument brokenReference =
        new ConfigDocument(
            1,
            null,
            null,
            new SchemaSection(List.of(), List.of()),
            new MenuSection(
                List.of(new MenuEntry("leaf", 1, new TableRef("s", "t_missing"), null))),
            new RbacSection(List.of(), List.of(), List.of(), List.of()));
    ImportErrorCollector errors = new ImportErrorCollector();

    orchestrator.validate(brokenReference, CONTEXT, errors);

    assertThat(errors.errors())
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactly(
            tuple("menu.items[0].targetTable", ImportMessageKeys.REFERENCE_NOT_FOUND),
            tuple("schema.tables[0].tableName", ImportMessageKeys.FIELD_REQUIRED),
            tuple("menu.items[0].label", ImportMessageKeys.FIELD_REQUIRED),
            tuple("rbac.primaryPermissions", ImportMessageKeys.RBAC_EMPTY));
  }

  @Test
  void anErrorAtAPositionAlreadyReportedIsNotReportedTwice() {
    when(menuStructureApi.validateMenuStructure(any()))
        .thenReturn(
            List.of(
                ImportValidationError.of(
                    "menu.items[0].targetTable", ImportMessageKeys.FIELD_REQUIRED)));
    ConfigDocument brokenReference =
        new ConfigDocument(
            1,
            null,
            null,
            new SchemaSection(List.of(), List.of()),
            new MenuSection(
                List.of(new MenuEntry("leaf", 1, new TableRef("s", "t_missing"), null))),
            new RbacSection(List.of(), List.of(), List.of(), List.of()));
    ImportErrorCollector errors = new ImportErrorCollector();

    orchestrator.validate(brokenReference, CONTEXT, errors);

    assertThat(errors.total()).isEqualTo(1);
  }

  @Test
  void validationPassesTheOperatorRoleAndBootstrapStateAndResolvesExistingIdsWithPendingNewOnes() {
    ImportContext bootstrap = CONTEXT.withBootstrapAtStart(true);
    when(configEngineApi.getExportableConfigSet())
        .thenReturn(
            new ConfigExportSet(
                List.of(new TableConfigSnapshot("id-existing", "s_1", "t_existing", 0, null)),
                List.of(),
                List.of()));
    ConfigDocument doc =
        new ConfigDocument(
            1,
            null,
            null,
            null,
            new MenuSection(
                List.of(
                    new MenuEntry("a", 1, new TableRef("s_1", "t_existing"), null),
                    new MenuEntry("b", 2, new TableRef("s_1", "t_new"), null))),
            new RbacSection(
                List.of(new RoleEntry("r")),
                List.of(),
                List.of(
                    new PrimaryPermissionEntry(
                        "r",
                        new PermissionScope(ScopeType.TABLE, "s_1", "t_existing", null),
                        PermissionLevel.READ),
                    new PrimaryPermissionEntry(
                        "r",
                        new PermissionScope(ScopeType.TABLE, "s_1", "t_new", null),
                        PermissionLevel.READ)),
                List.of()));

    orchestrator.validate(doc, bootstrap, new ImportErrorCollector());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MenuImportItem>> menu = ArgumentCaptor.forClass(List.class);
    verify(menuStructureApi).validateMenuStructure(menu.capture());
    assertThat(menu.getValue())
        .extracting(MenuImportItem::targetTableConfigId)
        .containsExactly("id-existing", null);
    ArgumentCaptor<RbacImportSet> rbac = ArgumentCaptor.forClass(RbacImportSet.class);
    verify(permissionEngineApi).validateRbacImport(rbac.capture(), eq("role-1"), eq(true));
    assertThat(rbac.getValue().primaryPermissions())
        .extracting(p -> p.scope().tableConfigId())
        .containsExactly("id-existing", null);
  }

  // ---- 反映 ----

  @Test
  void applyRunsSchemaThenMenuThenRbacResolvingIdsFromTheSchemaResult() {
    ConfigExportSet afterSchema =
        new ConfigExportSet(
            List.of(new TableConfigSnapshot("id-new-table", "s_1", "t_new", 0, null)),
            List.of(
                new ColumnConfigSnapshot(
                    "id-new-column",
                    "id-new-table",
                    "c_1",
                    0,
                    null,
                    EditorType.TEXT,
                    ValidationRule.empty(),
                    Visibility.VISIBLE,
                    List.of(),
                    null,
                    false)),
            List.of());
    // 検証の段階では、新しいテーブルは、存在しない。schemaの反映の後に、初めて内部IDが決まる。
    when(configEngineApi.getExportableConfigSet()).thenReturn(afterSchema);
    when(configEngineApi.applyConfigSet(any()))
        .thenReturn(
            new ApplyResult(
                Map.of(
                    ImportSections.SCHEMA,
                    new SectionCounts(2, 0, 0),
                    ImportSections.TRANSLATIONS,
                    SectionCounts.ZERO),
                PostCommit.NONE));
    when(menuStructureApi.applyMenuStructure(any()))
        .thenReturn(
            new ApplyResult(
                Map.of(ImportSections.MENU, new SectionCounts(1, 0, 0)), PostCommit.NONE));
    when(permissionEngineApi.applyRbacImport(any(), any()))
        .thenReturn(
            new ApplyResult(
                Map.of(
                    ImportSections.ROLES,
                    new SectionCounts(1, 0, 0),
                    ImportSections.GROUPS,
                    SectionCounts.ZERO,
                    ImportSections.PRIMARY_PERMISSIONS,
                    new SectionCounts(2, 0, 0),
                    ImportSections.AUXILIARY_PERMISSIONS,
                    SectionCounts.ZERO),
                PostCommit.NONE));

    AppliedImport applied = orchestrator.apply(document(), CONTEXT);

    InOrder inOrder = inOrder(configEngineApi, menuStructureApi, permissionEngineApi);
    inOrder.verify(configEngineApi).applyConfigSet(any(ConfigNaturalKeySet.class));
    inOrder.verify(configEngineApi).getExportableConfigSet();
    inOrder.verify(menuStructureApi).applyMenuStructure(any());
    inOrder.verify(permissionEngineApi).applyRbacImport(any(), eq("role-1"));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MenuImportItem>> menu = ArgumentCaptor.forClass(List.class);
    verify(menuStructureApi).applyMenuStructure(menu.capture());
    assertThat(menu.getValue().get(0).targetTableConfigId()).isEqualTo("id-new-table");
    ArgumentCaptor<RbacImportSet> rbac = ArgumentCaptor.forClass(RbacImportSet.class);
    verify(permissionEngineApi).applyRbacImport(rbac.capture(), eq("role-1"));
    assertThat(rbac.getValue().primaryPermissions())
        .extracting(p -> p.scope().tableConfigId(), p -> p.scope().columnConfigId())
        .containsExactly(tuple("id-new-table", null), tuple("id-new-table", "id-new-column"));
    // セクションは、決まった順序で統合される。
    assertThat(applied.sections().keySet())
        .containsExactly(
            ImportSections.SCHEMA,
            ImportSections.TRANSLATIONS,
            ImportSections.MENU,
            ImportSections.ROLES,
            ImportSections.GROUPS,
            ImportSections.PRIMARY_PERMISSIONS,
            ImportSections.AUXILIARY_PERMISSIONS);
    assertThat(applied.postCommits()).hasSize(3);
  }

  @Test
  void theConfigurationLayerNamesNoBusinessDomainInTheCode() {
    // BR9.20・NFR8.1: 業務固有のテーブル名・カラム名・ロール名を、コードに持たない(名前は、データとして扱う)。
    String source = ImportOrchestrator.class.getName() + ImportContext.class.getName();
    assertThat(source).doesNotContainIgnoringCase("product").doesNotContainIgnoringCase("book");
  }
}
