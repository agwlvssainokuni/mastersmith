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

package com.mastersmith.configio.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.mastersmith.config.dto.ColumnConfigSnapshot;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.ConfigNaturalKeySet;
import com.mastersmith.config.dto.TableConfigSnapshot;
import com.mastersmith.config.dto.TranslationSnapshot;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.model.FkReference;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.document.ConfigDocument.MenuEntry;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.menu.dto.MenuImportItem;
import com.mastersmith.menu.dto.MenuStructureEntry;
import com.mastersmith.permission.dto.RbacExport;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * {@link ConfigDocumentMapper}のテスト:
 * 書き出し(内部ID→自然キー、決まった順序、メニューの入れ子、解決できない参照は書き出さない)・取り込み(自然キーの入力の型への変換、内部IDの解決、仮の識別)・
 * 設定ファイルの形式の契約テスト(項目名・内部IDを含まないこと。BR9.2・BR9.3)。業務固有の名前は、連番から機械的に生成する(BR9.20)。
 */
class ConfigDocumentMapperTest {

  private final ConfigDocumentMapper mapper = new ConfigDocumentMapper();
  private static final Instant EXPORTED_AT = Instant.parse("2026-09-21T01:02:03Z");

  private static final String TABLE_A = "id-table-a";
  private static final String TABLE_B = "id-table-b";
  private static final String COLUMN_A1 = "id-column-a1";
  private static final String COLUMN_A2 = "id-column-a2";

  private static ConfigExportSet config() {
    return new ConfigExportSet(
        List.of(
            new TableConfigSnapshot(TABLE_B, "s_1", "t_b", 2, null),
            new TableConfigSnapshot(TABLE_A, "s_1", "t_a", 1, "c_2")),
        List.of(
            new ColumnConfigSnapshot(
                COLUMN_A2,
                TABLE_A,
                "c_2",
                2,
                "fmt",
                EditorType.SELECT,
                ValidationRule.empty(),
                Visibility.HIDDEN,
                List.of(),
                new FkReference("s_1", "t_b", "c_x", "c_y"),
                false),
            new ColumnConfigSnapshot(
                COLUMN_A1,
                TABLE_A,
                "c_1",
                1,
                null,
                EditorType.TEXT,
                new ValidationRule(Map.of("maxLength", 5)),
                Visibility.VISIBLE,
                List.of(new ChoiceOption("v", "k")),
                null,
                true)),
        List.of(
            new TranslationSnapshot("k_2", "ja", "b"),
            new TranslationSnapshot("k_1", "en", "a"),
            new TranslationSnapshot("k_1", "ja", "c")));
  }

  private static List<MenuStructureEntry> menu() {
    return List.of(
        new MenuStructureEntry("m-leaf-2", "m-folder", "leaf_2", 2, TABLE_B),
        new MenuStructureEntry("m-folder", null, "folder", 1, null),
        new MenuStructureEntry("m-leaf-1", "m-folder", "leaf_1", 1, TABLE_A),
        new MenuStructureEntry("m-root-leaf", null, "root_leaf", 2, TABLE_A));
  }

  private static RbacExport rbac() {
    return new RbacExport(
        List.of(new RbacExport.Role("r-2", "role_b"), new RbacExport.Role("r-1", "role_a")),
        List.of(new RbacExport.Group("g-1", "group_a", List.of("r-2", "r-1"))),
        List.of(
            new RbacExport.Primary("r-1", ScopeType.COLUMN, COLUMN_A1, PermissionLevel.READ),
            new RbacExport.Primary("r-1", ScopeType.SCHEMA, "s_1", PermissionLevel.FULL),
            new RbacExport.Primary("r-2", ScopeType.TABLE, TABLE_B, PermissionLevel.NONE)),
        List.of(new RbacExport.Auxiliary("r-1", ScopeType.TABLE, TABLE_A, true, null)));
  }

  private ConfigDocument export() {
    return mapper.toDocument(config(), menu(), rbac(), EXPORTED_AT, "9.9.9");
  }

  // ---- 書き出し ----

  @Test
  void theHeaderCarriesTheFormatVersionTheUtcTimestampAndTheAppVersion() {
    ConfigDocument doc = export();

    assertThat(doc.formatVersion()).isEqualTo(1);
    assertThat(doc.exportedAt()).isEqualTo("2026-09-21T01:02:03Z");
    assertThat(doc.appVersion()).isEqualTo("9.9.9");
  }

  @Test
  void tablesColumnsAndTranslationsAreSortedAndConvertedToNaturalKeys() {
    ConfigDocument doc = export();

    assertThat(doc.schema().tables()).extracting("tableName").containsExactly("t_a", "t_b");
    ConfigDocument.TableEntry tableA = doc.schema().tables().get(0);
    assertThat(tableA.optimisticLockColumn()).isEqualTo("c_2");
    assertThat(tableA.columns()).extracting("columnName").containsExactly("c_1", "c_2");
    ConfigDocument.ColumnEntry c1 = tableA.columns().get(0);
    assertThat(c1.editorType()).isEqualTo("text");
    assertThat(c1.visibility()).isEqualTo("visible");
    assertThat(c1.isPrimaryKey()).isTrue();
    assertThat(c1.validationRule()).containsEntry("maxLength", 5);
    assertThat(c1.choiceOptions()).extracting("value", "i18nKey").containsExactly(tuple("v", "k"));
    ConfigDocument.ColumnEntry c2 = tableA.columns().get(1);
    assertThat(c2.editorType()).isEqualTo("select");
    assertThat(c2.visibility()).isEqualTo("hidden");
    assertThat(c2.fkReference().referencedTableName()).isEqualTo("t_b");
    assertThat(doc.schema().translations())
        .extracting("i18nKey", "locale")
        .containsExactly(tuple("k_1", "en"), tuple("k_1", "ja"), tuple("k_2", "ja"));
  }

  @Test
  void theMenuBecomesANestedTreeSortedByOrderWithNaturalKeyTargets() {
    List<MenuEntry> items = export().menu().items();

    assertThat(items).extracting(MenuEntry::label).containsExactly("folder", "root_leaf");
    MenuEntry folder = items.get(0);
    assertThat(folder.targetTable()).isNull();
    assertThat(folder.children()).extracting(MenuEntry::label).containsExactly("leaf_1", "leaf_2");
    assertThat(folder.children().get(0).targetTable().tableName()).isEqualTo("t_a");
    assertThat(folder.children().get(0).children()).isNull();
    assertThat(items.get(1).targetTable().tableName()).isEqualTo("t_a");
  }

  @Test
  void menuItemsWithDanglingTargetsAreOmittedAndOrphansBecomeRoots() {
    List<MenuStructureEntry> flat =
        List.of(
            new MenuStructureEntry("m-1", null, "dangling", 1, "no-such-table"),
            new MenuStructureEntry("m-2", "missing-parent", "orphan", 2, TABLE_A));

    List<MenuEntry> items =
        mapper.toDocument(config(), flat, rbac(), EXPORTED_AT, "v").menu().items();

    assertThat(items).extracting(MenuEntry::label).containsExactly("orphan");
  }

  @Test
  void aCyclicMenuDoesNotHangTheExport() {
    List<MenuStructureEntry> flat =
        List.of(
            new MenuStructureEntry("m-1", "m-2", "a", 1, null),
            new MenuStructureEntry("m-2", "m-1", "b", 1, null));

    // 循環は、どこにもルートがなく、書き出されない(無限ループにならない)。
    assertThat(mapper.toDocument(config(), flat, rbac(), EXPORTED_AT, "v").menu().items())
        .isEmpty();
  }

  @Test
  void rbacIsConvertedToNamesAndNaturalKeyScopesAndSorted() {
    ConfigDocument.RbacSection section = export().rbac();

    assertThat(section.roles()).extracting("name").containsExactly("role_a", "role_b");
    assertThat(section.groups().get(0).roleNames()).containsExactly("role_a", "role_b");
    assertThat(section.primaryPermissions())
        .extracting(
            "roleName",
            "scope.scopeType",
            "scope.schemaName",
            "scope.tableName",
            "scope.columnName",
            "level")
        .containsExactly(
            tuple("role_a", ScopeType.SCHEMA, "s_1", null, null, PermissionLevel.FULL),
            tuple("role_a", ScopeType.COLUMN, "s_1", "t_a", "c_1", PermissionLevel.READ),
            tuple("role_b", ScopeType.TABLE, "s_1", "t_b", null, PermissionLevel.NONE));
    assertThat(section.auxiliaryPermissions())
        .extracting("roleName", "scope.tableName", "createAllowed", "deleteAllowed")
        .containsExactly(tuple("role_a", "t_a", true, null));
  }

  @Test
  void permissionsThatCannotBeExpressedAreNotExported() {
    RbacExport orphaned =
        new RbacExport(
            List.of(new RbacExport.Role("r-1", "role_a")),
            List.of(),
            List.of(
                new RbacExport.Primary(
                    "r-1", ScopeType.TABLE, "deleted-table", PermissionLevel.FULL),
                new RbacExport.Primary(
                    "r-1", ScopeType.COLUMN, "deleted-column", PermissionLevel.FULL),
                new RbacExport.Primary(
                    "deleted-role", ScopeType.SCHEMA, "s_1", PermissionLevel.FULL),
                new RbacExport.Primary("r-1", ScopeType.SCHEMA, "s_1", PermissionLevel.READ)),
            List.of(new RbacExport.Auxiliary("r-1", ScopeType.TABLE, "deleted-table", true, true)));

    ConfigDocument.RbacSection section =
        mapper.toDocument(config(), menu(), orphaned, EXPORTED_AT, "v").rbac();

    assertThat(section.primaryPermissions()).hasSize(1);
    assertThat(section.auxiliaryPermissions()).isEmpty();
  }

  @Test
  void theSameInputProducesTheSameDocumentEveryTime() {
    assertThat(export()).isEqualTo(export());
    assertThat(ConfigDocumentFactory.toJson(export()))
        .isEqualTo(ConfigDocumentFactory.toJson(export()));
  }

  // ---- 契約テスト(設定ファイルの形式) ----

  @Test
  void theJsonShapeFollowsTheAgreedContractAndContainsNoInternalIds() {
    JsonNode json = ConfigDocumentFactory.toJson(export());

    assertThat(json.propertyNames())
        .containsExactlyInAnyOrder(
            "formatVersion", "exportedAt", "appVersion", "schema", "menu", "rbac");
    assertThat(json.get("schema").propertyNames())
        .containsExactlyInAnyOrder("tables", "translations");
    assertThat(json.at("/schema/tables/0").propertyNames())
        .containsExactlyInAnyOrder(
            "schemaName", "tableName", "displayOrder", "optimisticLockColumn", "columns");
    assertThat(json.at("/schema/tables/0/columns/0").propertyNames())
        .containsExactlyInAnyOrder(
            "columnName",
            "displayOrder",
            "format",
            "editorType",
            "validationRule",
            "visibility",
            "isPrimaryKey",
            "choiceOptions",
            "fkReference");
    assertThat(json.at("/schema/tables/0/columns/1/fkReference").propertyNames())
        .containsExactlyInAnyOrder(
            "referencedSchemaName",
            "referencedTableName",
            "referencedValueColumnName",
            "referencedLabelColumnName");
    assertThat(json.at("/menu/items/0").propertyNames())
        .containsExactlyInAnyOrder("label", "order", "targetTable", "children");
    assertThat(json.get("rbac").propertyNames())
        .containsExactlyInAnyOrder("roles", "groups", "primaryPermissions", "auxiliaryPermissions");
    assertThat(json.at("/rbac/primaryPermissions/0").propertyNames())
        .containsExactlyInAnyOrder("roleName", "scope", "level");
    assertThat(json.at("/rbac/primaryPermissions/0/scope").propertyNames())
        .containsExactlyInAnyOrder("scopeType", "schemaName", "tableName", "columnName");
    assertThat(json.at("/rbac/auxiliaryPermissions/0").propertyNames())
        .containsExactlyInAnyOrder("roleName", "scope", "createAllowed", "deleteAllowed");
    // 内部IDは、どこにも含まれない(値としても)。
    String text = json.toString();
    for (String id :
        List.of(
            TABLE_A, TABLE_B, COLUMN_A1, COLUMN_A2, "m-folder", "m-leaf-1", "r-1", "r-2", "g-1")) {
      assertThat(text).doesNotContain(id);
    }
    assertThat(json.get("formatVersion").intValue()).isEqualTo(1);
    assertThat(json.at("/rbac/primaryPermissions/0/level").stringValue()).isEqualTo("FULL");
  }

  // ---- 取り込み ----

  @Test
  void theNaturalKeySetKeepsTheOrderAndDropsTheIsPrimaryKeyFlag() {
    ConfigDocument doc = ConfigDocumentFactory.mechanical(Spec.small());

    ConfigNaturalKeySet set = mapper.toNaturalKeySet(doc);

    assertThat(set.tables()).hasSize(4);
    assertThat(set.tables().get(0).schemaName()).isEqualTo("schema_0000");
    assertThat(set.tables().get(0).columns()).hasSize(4);
    assertThat(set.tables().get(0).columns().get(2).editorType()).isEqualTo("select");
    assertThat(set.tables().get(0).columns().get(2).choiceOptions()).hasSize(2);
    assertThat(set.tables().get(0).columns().get(3).fkReference().referencedTableName())
        .isEqualTo("table_0000");
    assertThat(set.translations()).hasSize(6);
  }

  @Test
  void menuItemsAreFlattenedWithJsonPositionsParentPositionsAndResolvedTargets() {
    ConfigDocument doc =
        new ConfigDocument(
            1,
            null,
            null,
            null,
            new ConfigDocument.MenuSection(
                List.of(
                    new MenuEntry(
                        "f",
                        1,
                        null,
                        List.of(
                            new MenuEntry(
                                "l_1", 1, new ConfigDocument.TableRef("s_1", "t_a"), null),
                            new MenuEntry(
                                "l_2", 2, new ConfigDocument.TableRef("s_1", "t_new"), null))),
                    new MenuEntry("l_3", 2, new ConfigDocument.TableRef("s_1", "t_b"), null))),
            null);
    NaturalKeyIndex index = NaturalKeyIndex.from(config());

    List<MenuImportItem> flat = mapper.toMenuItems(doc, index);

    assertThat(flat)
        .extracting(
            MenuImportItem::position,
            MenuImportItem::parentPosition,
            MenuImportItem::label,
            MenuImportItem::leaf,
            MenuImportItem::targetTableConfigId)
        .containsExactly(
            tuple("menu.items[0]", null, "f", false, null),
            tuple("menu.items[0].children[0]", "menu.items[0]", "l_1", true, TABLE_A),
            tuple("menu.items[0].children[1]", "menu.items[0]", "l_2", true, null),
            tuple("menu.items[1]", null, "l_3", true, TABLE_B));
  }

  @Test
  void rbacScopesResolveToInternalIdsAndNewTablesAndColumnsBecomeNull() {
    ConfigDocument.PermissionScope existingColumn =
        new ConfigDocument.PermissionScope(ScopeType.COLUMN, "s_1", "t_a", "c_1");
    ConfigDocument.PermissionScope newColumnInExistingTable =
        new ConfigDocument.PermissionScope(ScopeType.COLUMN, "s_1", "t_a", "c_new");
    ConfigDocument.PermissionScope newTable =
        new ConfigDocument.PermissionScope(ScopeType.TABLE, "s_1", "t_new", null);
    ConfigDocument.PermissionScope schema =
        new ConfigDocument.PermissionScope(ScopeType.SCHEMA, "s_1", null, null);
    ConfigDocument doc =
        new ConfigDocument(
            1,
            null,
            null,
            null,
            null,
            new ConfigDocument.RbacSection(
                List.of(new ConfigDocument.RoleEntry("role_a")),
                List.of(new ConfigDocument.GroupEntry("group_a", List.of("role_a"))),
                List.of(
                    new ConfigDocument.PrimaryPermissionEntry(
                        "role_a", existingColumn, PermissionLevel.READ),
                    new ConfigDocument.PrimaryPermissionEntry(
                        "role_a", newColumnInExistingTable, PermissionLevel.READ),
                    new ConfigDocument.PrimaryPermissionEntry(
                        "role_a", newTable, PermissionLevel.FULL),
                    new ConfigDocument.PrimaryPermissionEntry(
                        "role_a", schema, PermissionLevel.NONE)),
                List.of(
                    new ConfigDocument.AuxiliaryPermissionEntry("role_a", newTable, true, false))));

    RbacImportSet set = mapper.toRbacImportSet(doc, NaturalKeyIndex.from(config()));

    assertThat(set.roles()).extracting(RbacImportSet.Role::name).containsExactly("role_a");
    assertThat(set.groups().get(0).roleNames()).containsExactly("role_a");
    assertThat(set.primaryPermissions().stream().map(RbacImportSet.Primary::scope))
        .extracting("scopeType", "schemaName", "tableConfigId", "columnConfigId")
        .containsExactly(
            tuple(ScopeType.COLUMN, "s_1", TABLE_A, COLUMN_A1),
            tuple(ScopeType.COLUMN, "s_1", TABLE_A, null),
            tuple(ScopeType.TABLE, "s_1", null, null),
            tuple(ScopeType.SCHEMA, "s_1", null, null));
    assertThat(set.auxiliaryPermissions().get(0).scope().tableConfigId()).isNull();
    assertThat(set.auxiliaryPermissions().get(0).createAllowed()).isTrue();
  }

  @Test
  void theNaturalKeyIndexIgnoresColumnsOfUnknownTables() {
    ConfigExportSet withOrphanColumn =
        new ConfigExportSet(
            List.of(new TableConfigSnapshot(TABLE_A, "s_1", "t_a", 1, null)),
            List.of(
                new ColumnConfigSnapshot(
                    "id-orphan",
                    "no-table",
                    "c",
                    0,
                    null,
                    EditorType.TEXT,
                    ValidationRule.empty(),
                    Visibility.VISIBLE,
                    List.of(),
                    null,
                    false)),
            List.of());

    NaturalKeyIndex index = NaturalKeyIndex.from(withOrphanColumn);

    assertThat(index.tableId("s_1", "t_a")).isEqualTo(TABLE_A);
    assertThat(index.tableId("s_1", "t_x")).isNull();
    assertThat(index.columnId("s_1", "t_a", "c")).isNull();
  }
}
