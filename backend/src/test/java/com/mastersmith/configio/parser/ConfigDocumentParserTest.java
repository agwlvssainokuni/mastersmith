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

package com.mastersmith.configio.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.configio.exception.ConfigImportValidationException;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link ConfigDocumentParser}のテスト(config-import-export BR9.6〜BR9.8):
 * 構文・形式の誤りはその誤りだけ、未知のプロパティは無視、構造(必須項目・型・許容値・一意性)の誤りは、位置つきで全件を集める。
 * 設定ファイルの契約テスト(config-schemaの形式の検査。team.md Q7)を兼ねる。表形式のテスト(1行=1つの不正な項目)を含む。
 */
class ConfigDocumentParserTest {

  private final ConfigDocumentParser parser = new ConfigDocumentParser();

  private static ObjectNode validJson() {
    return (ObjectNode)
        ConfigDocumentFactory.toJson(ConfigDocumentFactory.mechanical(Spec.small())).deepCopy();
  }

  private static ObjectNode at(ObjectNode root, String pointer) {
    return (ObjectNode) root.at(pointer);
  }

  private ImportErrorCollector parseCollecting(JsonNode json) {
    ImportErrorCollector errors = new ImportErrorCollector();
    parser.parse(json, errors);
    return errors;
  }

  // ---- 正常系・往復 ----

  @Test
  void aValidDocumentParsesWithoutErrorsAndRoundTripsExactly() {
    ConfigDocument original = ConfigDocumentFactory.mechanical(Spec.small());
    ImportErrorCollector errors = new ImportErrorCollector();

    ConfigDocument parsed = parser.parse(ConfigDocumentFactory.toJson(original), errors);

    assertThat(errors.hasErrors()).isFalse();
    assertThat(parsed).isEqualTo(original);
  }

  @Test
  void bothBusinessDomainProfilesRoundTripExactly() {
    for (ConfigDocument profile :
        List.of(ConfigDocumentFactory.productProfile(), ConfigDocumentFactory.libraryProfile())) {
      ImportErrorCollector errors = new ImportErrorCollector();

      ConfigDocument parsed = parser.parse(ConfigDocumentFactory.toJson(profile), errors);

      assertThat(errors.hasErrors()).isFalse();
      assertThat(parsed).isEqualTo(profile);
    }
  }

  @Test
  void theUpperBoundDocumentParsesWithoutErrors() {
    ConfigDocument original = ConfigDocumentFactory.mechanical(Spec.upperBound());
    ImportErrorCollector errors = new ImportErrorCollector();

    ConfigDocument parsed = parser.parse(ConfigDocumentFactory.toJson(original), errors);

    assertThat(errors.hasErrors()).isFalse();
    assertThat(parsed.schema().tables()).hasSize(100);
    assertThat(parsed.rbac().primaryPermissions()).hasSize(5000);
  }

  @Test
  void unknownPropertiesAreIgnoredAtEveryLevel() {
    ObjectNode json = validJson();
    json.put("futureTopLevel", 1);
    at(json, "/schema").put("futureSchema", true);
    at(json, "/schema/tables/0").putObject("futureTable").put("x", 1);
    at(json, "/schema/tables/0/columns/0").putArray("futureColumn").add(1);
    at(json, "/menu").put("futureMenu", "x");
    at(json, "/menu/items/0").put("futureItem", 1);
    at(json, "/rbac").put("futureRbac", 1);
    at(json, "/rbac/primaryPermissions/0/scope").put("futureScope", 1);

    ImportErrorCollector errors = new ImportErrorCollector();
    ConfigDocument parsed = parser.parse(json, errors);

    assertThat(errors.hasErrors()).isFalse();
    assertThat(parsed).isEqualTo(ConfigDocumentFactory.mechanical(Spec.small()));
  }

  @Test
  void nullOptionalValuesAreTreatedAsAbsent() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/0").putNull("optimisticLockColumn");
    at(json, "/schema/tables/0/columns/0").putNull("format");
    at(json, "/schema/tables/0/columns/0").putNull("validationRule");
    at(json, "/schema/tables/0/columns/0").putNull("fkReference");

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.hasErrors()).isFalse();
  }

  @Test
  void validationRuleIsConvertedToPlainJavaValuesDroppingNulls() {
    ObjectNode json = validJson();
    ObjectNode rule = at(json, "/schema/tables/0/columns/0").putObject("validationRule");
    rule.put("maxLength", 10).put("pattern", "^a$").put("required", true).putNull("dropped");
    rule.putArray("list").add(1).add("x");
    rule.putObject("nested").put("d", 1.5);

    ConfigDocument parsed = parser.parse(json, new ImportErrorCollector());

    assertThat(parsed.schema().tables().get(0).columns().get(0).validationRule())
        .containsEntry("maxLength", 10)
        .containsEntry("pattern", "^a$")
        .containsEntry("required", true)
        .containsEntry("list", List.of(1, "x"))
        .doesNotContainKey("dropped")
        .containsKey("nested");
  }

  // ---- 構文・形式(その誤りだけを返す) ----

  static Stream<Arguments> nonObjectRoots() {
    var mapper = ConfigDocumentFactory.JSON;
    return Stream.of(
        Arguments.of(mapper.readTree("[]")),
        Arguments.of(mapper.readTree("\"text\"")),
        Arguments.of(mapper.readTree("12")),
        Arguments.of(mapper.readTree("null")));
  }

  @ParameterizedTest
  @MethodSource("nonObjectRoots")
  void aRootThatIsNotAJsonObjectIsMalformed(JsonNode root) {
    assertThatThrownBy(() -> parser.parse(root, new ImportErrorCollector()))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> {
              assertThat(e.category()).isEqualTo(FailureCategory.MALFORMED);
              assertThat(e.errors())
                  .extracting(ImportValidationError::message)
                  .containsExactly(ImportMessageKeys.JSON_MALFORMED);
              assertThat(e.totalErrorCount()).isEqualTo(1);
            });
  }

  @Test
  void aNullRootIsMalformedToo() {
    assertThatThrownBy(() -> parser.parse(null, new ImportErrorCollector()))
        .isInstanceOf(ConfigImportValidationException.class);
  }

  static Stream<Arguments> unsupportedFormatVersions() {
    return Stream.of(
        Arguments.of("missing", (Consumer<ObjectNode>) o -> o.remove("formatVersion")),
        Arguments.of("null", (Consumer<ObjectNode>) o -> o.putNull("formatVersion")),
        Arguments.of("zero", (Consumer<ObjectNode>) o -> o.put("formatVersion", 0)),
        Arguments.of("two", (Consumer<ObjectNode>) o -> o.put("formatVersion", 2)),
        Arguments.of("string one", (Consumer<ObjectNode>) o -> o.put("formatVersion", "1")),
        Arguments.of("decimal", (Consumer<ObjectNode>) o -> o.put("formatVersion", 1.5)),
        Arguments.of("huge", (Consumer<ObjectNode>) o -> o.put("formatVersion", 4_294_967_297L)),
        Arguments.of("boolean", (Consumer<ObjectNode>) o -> o.put("formatVersion", true)));
  }

  @ParameterizedTest(name = "formatVersion: {0}")
  @MethodSource("unsupportedFormatVersions")
  void aMissingOrUnsupportedFormatVersionReportsOnlyThatAndNothingElse(
      String label, Consumer<ObjectNode> mutate) {
    ObjectNode json = validJson();
    mutate.accept(json);
    json.remove("schema"); // 以降の検証は、行わない(構造の誤りは、報告されない)。
    ImportErrorCollector errors = new ImportErrorCollector();

    assertThatThrownBy(() -> parser.parse(json, errors))
        .isInstanceOfSatisfying(
            ConfigImportValidationException.class,
            e -> {
              assertThat(e.category()).isEqualTo(FailureCategory.UNSUPPORTED_FORMAT);
              assertThat(e.errors())
                  .extracting(ImportValidationError::field, ImportValidationError::message)
                  .containsExactly(tuple("formatVersion", ImportMessageKeys.FORMAT_UNSUPPORTED));
            });
    assertThat(errors.hasErrors()).isFalse();
  }

  // ---- 構造(表形式: 1行=1つの不正な項目) ----

  private record Case(String name, Consumer<ObjectNode> mutate, String field, String message) {
    @Override
    public String toString() {
      return name;
    }
  }

  private static Case c(String name, Consumer<ObjectNode> mutate, String field, String message) {
    return new Case(name, mutate, field, message);
  }

  private static Consumer<ObjectNode> remove(String pointer, String property) {
    return root -> at(root, pointer).remove(property);
  }

  private static Consumer<ObjectNode> set(String pointer, String property, Object value) {
    return root -> {
      ObjectNode node = at(root, pointer);
      if (value instanceof String s) {
        node.put(property, s);
      } else if (value instanceof Integer i) {
        node.put(property, i);
      } else if (value instanceof Long l) {
        node.put(property, l);
      } else if (value instanceof Double d) {
        node.put(property, d);
      } else if (value instanceof Boolean b) {
        node.put(property, b);
      } else if (value == null) {
        node.putNull(property);
      } else {
        throw new IllegalArgumentException();
      }
    };
  }

  private static Consumer<ObjectNode> setArray(String pointer, String property) {
    return root -> at(root, pointer).putArray(property);
  }

  private static Consumer<ObjectNode> setObject(String pointer, String property) {
    return root -> at(root, pointer).putObject(property);
  }

  private static Consumer<ObjectNode> duplicate(String arrayPointer, int from) {
    return root -> {
      ArrayNode array = (ArrayNode) root.at(arrayPointer);
      array.add(array.get(from).deepCopy());
    };
  }

  private static final String FIELD_REQUIRED = ImportMessageKeys.FIELD_REQUIRED;
  private static final String FIELD_TYPE = ImportMessageKeys.FIELD_TYPE;
  private static final String FIELD_DUPLICATE = ImportMessageKeys.FIELD_DUPLICATE;
  private static final String FIELD_VALUE_INVALID = ImportMessageKeys.FIELD_VALUE_INVALID;

  static Stream<Case> invalidCases() {
    // small(): テーブル4(schema_0000・schema_0001 ×
    // table_0000・table_0001)、カラム4(各テーブル)、翻訳6、ロール3、グループ2、主権限8、補助権限3、メニュー2フォルダ。
    return Stream.of(
        // セクション・配列
        c("スキーマの欠落", root -> root.remove("schema"), "schema", FIELD_REQUIRED),
        c("スキーマが文字列", root -> root.put("schema", "x"), "schema", FIELD_TYPE),
        c("tablesの欠落", remove("/schema", "tables"), "schema.tables", FIELD_REQUIRED),
        c("tablesがオブジェクト", setObject("/schema", "tables"), "schema.tables", FIELD_TYPE),
        c(
            "translationsの欠落",
            remove("/schema", "translations"),
            "schema.translations",
            FIELD_REQUIRED),
        c("メニューの欠落", root -> root.remove("menu"), "menu", FIELD_REQUIRED),
        c("menu.itemsの欠落", remove("/menu", "items"), "menu.items", FIELD_REQUIRED),
        c("rbacの欠落", root -> root.remove("rbac"), "rbac", FIELD_REQUIRED),
        c("rolesの欠落", remove("/rbac", "roles"), "rbac.roles", FIELD_REQUIRED),
        c("groupsの欠落", remove("/rbac", "groups"), "rbac.groups", FIELD_REQUIRED),
        c(
            "primaryPermissionsの欠落",
            remove("/rbac", "primaryPermissions"),
            "rbac.primaryPermissions",
            FIELD_REQUIRED),
        c(
            "auxiliaryPermissionsの欠落",
            remove("/rbac", "auxiliaryPermissions"),
            "rbac.auxiliaryPermissions",
            FIELD_REQUIRED),
        c(
            "要素がオブジェクトでない",
            root -> ((ArrayNode) root.at("/schema/tables")).set(0, root.numberNode(5)),
            "schema.tables[0]",
            FIELD_TYPE),
        // テーブル
        c(
            "schemaNameの欠落",
            remove("/schema/tables/0", "schemaName"),
            "schema.tables[0].schemaName",
            FIELD_REQUIRED),
        c(
            "tableNameが空",
            set("/schema/tables/0", "tableName", " "),
            "schema.tables[0].tableName",
            FIELD_REQUIRED),
        c(
            "tableNameが数値",
            set("/schema/tables/0", "tableName", 5),
            "schema.tables[0].tableName",
            FIELD_TYPE),
        c(
            "displayOrderの欠落",
            remove("/schema/tables/1", "displayOrder"),
            "schema.tables[1].displayOrder",
            FIELD_REQUIRED),
        c(
            "displayOrderが文字列",
            set("/schema/tables/1", "displayOrder", "1"),
            "schema.tables[1].displayOrder",
            FIELD_TYPE),
        c(
            "displayOrderが小数",
            set("/schema/tables/1", "displayOrder", 1.5),
            "schema.tables[1].displayOrder",
            FIELD_TYPE),
        c(
            "displayOrderがintを超える",
            set("/schema/tables/1", "displayOrder", 3_000_000_000L),
            "schema.tables[1].displayOrder",
            FIELD_TYPE),
        c(
            "optimisticLockColumnが数値",
            set("/schema/tables/0", "optimisticLockColumn", 5),
            "schema.tables[0].optimisticLockColumn",
            FIELD_TYPE),
        c(
            "columnsの欠落",
            remove("/schema/tables/0", "columns"),
            "schema.tables[0].columns",
            FIELD_REQUIRED),
        c("テーブルの重複", duplicate("/schema/tables", 0), "schema.tables[4]", FIELD_DUPLICATE),
        // カラム
        c(
            "columnNameの欠落",
            remove("/schema/tables/0/columns/0", "columnName"),
            "schema.tables[0].columns[0].columnName",
            FIELD_REQUIRED),
        c(
            "カラムのdisplayOrderが真偽値",
            set("/schema/tables/0/columns/1", "displayOrder", true),
            "schema.tables[0].columns[1].displayOrder",
            FIELD_TYPE),
        c(
            "formatが数値",
            set("/schema/tables/0/columns/0", "format", 1),
            "schema.tables[0].columns[0].format",
            FIELD_TYPE),
        c(
            "editorTypeが数値",
            set("/schema/tables/0/columns/0", "editorType", 5),
            "schema.tables[0].columns[0].editorType",
            FIELD_TYPE),
        c(
            "visibilityが真偽値",
            set("/schema/tables/0/columns/0", "visibility", true),
            "schema.tables[0].columns[0].visibility",
            FIELD_TYPE),
        c(
            "validationRuleが配列",
            setArray("/schema/tables/0/columns/0", "validationRule"),
            "schema.tables[0].columns[0].validationRule",
            FIELD_TYPE),
        c(
            "isPrimaryKeyが文字列",
            set("/schema/tables/0/columns/0", "isPrimaryKey", "yes"),
            "schema.tables[0].columns[0].isPrimaryKey",
            FIELD_TYPE),
        c(
            "choiceOptionsがオブジェクト",
            setObject("/schema/tables/0/columns/0", "choiceOptions"),
            "schema.tables[0].columns[0].choiceOptions",
            FIELD_TYPE),
        c(
            "選択肢のvalueの欠落",
            remove("/schema/tables/0/columns/2/choiceOptions/0", "value"),
            "schema.tables[0].columns[2].choiceOptions[0].value",
            FIELD_REQUIRED),
        c(
            "選択肢のi18nKeyの欠落",
            remove("/schema/tables/0/columns/2/choiceOptions/1", "i18nKey"),
            "schema.tables[0].columns[2].choiceOptions[1].i18nKey",
            FIELD_REQUIRED),
        c(
            "fkReferenceが文字列",
            set("/schema/tables/0/columns/0", "fkReference", "x"),
            "schema.tables[0].columns[0].fkReference",
            FIELD_TYPE),
        c(
            "fkのreferencedTableNameの欠落",
            remove("/schema/tables/0/columns/3/fkReference", "referencedTableName"),
            "schema.tables[0].columns[3].fkReference.referencedTableName",
            FIELD_REQUIRED),
        c(
            "fkのreferencedLabelColumnNameの欠落",
            remove("/schema/tables/0/columns/3/fkReference", "referencedLabelColumnName"),
            "schema.tables[0].columns[3].fkReference.referencedLabelColumnName",
            FIELD_REQUIRED),
        c(
            "カラムの重複",
            duplicate("/schema/tables/0/columns", 0),
            "schema.tables[0].columns[4]",
            FIELD_DUPLICATE),
        // 翻訳
        c(
            "i18nKeyの欠落",
            remove("/schema/translations/0", "i18nKey"),
            "schema.translations[0].i18nKey",
            FIELD_REQUIRED),
        c(
            "localeの欠落",
            remove("/schema/translations/0", "locale"),
            "schema.translations[0].locale",
            FIELD_REQUIRED),
        c(
            "textの欠落",
            remove("/schema/translations/0", "text"),
            "schema.translations[0].text",
            FIELD_REQUIRED),
        c("翻訳の重複", duplicate("/schema/translations", 0), "schema.translations[6]", FIELD_DUPLICATE),
        // メニュー
        c("メニューのlabelの欠落", remove("/menu/items/0", "label"), "menu.items[0].label", FIELD_REQUIRED),
        c("メニューのorderの欠落", remove("/menu/items/0", "order"), "menu.items[0].order", FIELD_REQUIRED),
        c(
            "遷移先のtableNameの欠落",
            remove("/menu/items/0/children/0/targetTable", "tableName"),
            "menu.items[0].children[0].targetTable.tableName",
            FIELD_REQUIRED),
        c(
            "targetTableが文字列",
            set("/menu/items/0/children/0", "targetTable", "x"),
            "menu.items[0].children[0].targetTable",
            FIELD_TYPE),
        c(
            "childrenがオブジェクト",
            setObject("/menu/items/0", "children"),
            "menu.items[0].children",
            FIELD_TYPE),
        // RBAC
        c("ロール名が空", set("/rbac/roles/0", "name", ""), "rbac.roles[0].name", FIELD_REQUIRED),
        c("ロールの重複", duplicate("/rbac/roles", 0), "rbac.roles[3]", FIELD_DUPLICATE),
        c("グループ名の欠落", remove("/rbac/groups/0", "name"), "rbac.groups[0].name", FIELD_REQUIRED),
        c(
            "グループのroleNamesの欠落",
            remove("/rbac/groups/0", "roleNames"),
            "rbac.groups[0].roleNames",
            FIELD_REQUIRED),
        c(
            "グループのroleNamesの要素が数値",
            root -> ((ArrayNode) root.at("/rbac/groups/0/roleNames")).set(0, root.numberNode(1)),
            "rbac.groups[0].roleNames[0]",
            FIELD_TYPE),
        c(
            "グループのroleNamesの重複",
            root -> ((ArrayNode) root.at("/rbac/groups/0/roleNames")).add("role_0000"),
            "rbac.groups[0].roleNames[2]",
            FIELD_DUPLICATE),
        c("グループの重複", duplicate("/rbac/groups", 0), "rbac.groups[2]", FIELD_DUPLICATE),
        c(
            "権限のroleNameの欠落",
            remove("/rbac/primaryPermissions/0", "roleName"),
            "rbac.primaryPermissions[0].roleName",
            FIELD_REQUIRED),
        c(
            "levelの欠落",
            remove("/rbac/primaryPermissions/0", "level"),
            "rbac.primaryPermissions[0].level",
            FIELD_REQUIRED),
        c(
            "levelが許容値外",
            set("/rbac/primaryPermissions/0", "level", "ALL"),
            "rbac.primaryPermissions[0].level",
            FIELD_VALUE_INVALID),
        c(
            "levelが小文字",
            set("/rbac/primaryPermissions/0", "level", "full"),
            "rbac.primaryPermissions[0].level",
            FIELD_VALUE_INVALID),
        c(
            "scopeの欠落",
            remove("/rbac/primaryPermissions/0", "scope"),
            "rbac.primaryPermissions[0].scope",
            FIELD_REQUIRED),
        c(
            "scopeTypeが許容値外",
            set("/rbac/primaryPermissions/0/scope", "scopeType", "ROW"),
            "rbac.primaryPermissions[0].scope.scopeType",
            FIELD_VALUE_INVALID),
        c(
            "scopeのschemaNameの欠落",
            remove("/rbac/primaryPermissions/0/scope", "schemaName"),
            "rbac.primaryPermissions[0].scope.schemaName",
            FIELD_REQUIRED),
        c(
            "補助権限のroleNameの欠落",
            remove("/rbac/auxiliaryPermissions/0", "roleName"),
            "rbac.auxiliaryPermissions[0].roleName",
            FIELD_REQUIRED),
        c(
            "createAllowedが文字列",
            set("/rbac/auxiliaryPermissions/0", "createAllowed", "yes"),
            "rbac.auxiliaryPermissions[0].createAllowed",
            FIELD_TYPE),
        c(
            "deleteAllowedが数値",
            set("/rbac/auxiliaryPermissions/0", "deleteAllowed", 1),
            "rbac.auxiliaryPermissions[0].deleteAllowed",
            FIELD_TYPE),
        c(
            "主権限の重複",
            duplicate("/rbac/primaryPermissions", 0),
            "rbac.primaryPermissions[8]",
            FIELD_DUPLICATE),
        c(
            "補助権限の重複",
            duplicate("/rbac/auxiliaryPermissions", 0),
            "rbac.auxiliaryPermissions[3]",
            FIELD_DUPLICATE));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidCases")
  void everyInvalidItemIsReportedWithItsPositionAndMessageKey(Case testCase) {
    ObjectNode json = validJson();
    testCase.mutate().accept(json);

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.errors())
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .contains(tuple(testCase.field(), testCase.message()));
  }

  @Test
  void anAllowedValueErrorListsTheAllowedValuesAsParams() {
    ObjectNode json = validJson();
    at(json, "/rbac/primaryPermissions/0").put("level", "ALL");

    ImportErrorCollector errors = parseCollecting(json);

    ImportValidationError error =
        errors.errors().stream()
            .filter(e -> e.field().endsWith(".level"))
            .findFirst()
            .orElseThrow();
    assertThat(error.params().get("allowed")).isEqualTo(List.of("NONE", "READ", "FULL"));
  }

  @Test
  void aTypeErrorNamesTheExpectedTypeAsParams() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/0").put("displayOrder", "x");

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.errors().get(0).params()).containsEntry("expected", "integer");
  }

  @Test
  void anAuxiliaryPermissionOnAColumnIsAStructuralError() {
    ObjectNode json = validJson();
    ObjectNode scope = at(json, "/rbac/auxiliaryPermissions/0/scope");
    scope
        .put("scopeType", "COLUMN")
        .put("tableName", "table_0000")
        .put("columnName", "column_0000");

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.errors())
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .contains(
            tuple(
                "rbac.auxiliaryPermissions[0].scope.scopeType",
                ImportMessageKeys.RBAC_AUXILIARY_COLUMN));
  }

  @Test
  void tableAndColumnScopesRequireTheirNames() {
    ObjectNode json = validJson();
    // 主権限の1件目を、TABLE(tableNameなし)・2件目をCOLUMN(columnNameなし)にする。
    at(json, "/rbac/primaryPermissions/0/scope").put("scopeType", "TABLE").remove("tableName");
    at(json, "/rbac/primaryPermissions/1/scope")
        .put("scopeType", "COLUMN")
        .put("tableName", "table_0000")
        .remove("columnName");

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.errors())
        .extracting(ImportValidationError::field)
        .contains(
            "rbac.primaryPermissions[0].scope.tableName",
            "rbac.primaryPermissions[1].scope.columnName");
  }

  // ---- 全件を集める・除外の方針 ----

  @Test
  void collectsErrorsFromEverySectionInOneParseInsteadOfStoppingAtTheFirst() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/0").remove("tableName");
    at(json, "/menu/items/0").remove("label");
    at(json, "/rbac/roles/0").remove("name");
    at(json, "/schema/translations/0").remove("text");

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.errors())
        .extracting(ImportValidationError::field)
        .contains(
            "schema.tables[0].tableName",
            "menu.items[0].label",
            "rbac.roles[0].name",
            "schema.translations[0].text");
  }

  @Test
  void entriesWithoutANaturalKeyAreDroppedAndTheOthersKept() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/0").remove("tableName");
    at(json, "/rbac/roles/0").remove("name");

    ImportErrorCollector errors = new ImportErrorCollector();
    ConfigDocument parsed = parser.parse(json, errors);

    assertThat(parsed.schema().tables()).hasSize(3);
    assertThat(parsed.rbac().roles()).hasSize(2);
  }

  @Test
  void aMissingDisplayOrderIsReportedButTheEntryIsKeptWithADefaultToAvoidCascadingErrors() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/0").remove("displayOrder");

    ImportErrorCollector errors = new ImportErrorCollector();
    ConfigDocument parsed = parser.parse(json, errors);

    assertThat(errors.total()).isEqualTo(1);
    assertThat(parsed.schema().tables()).hasSize(4);
    assertThat(parsed.schema().tables().get(0).displayOrder()).isZero();
  }

  @Test
  void missingEditorTypeAndVisibilityAreLeftToConfigEngine() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/0/columns/0").remove("editorType");
    at(json, "/schema/tables/0/columns/0").remove("visibility");

    ImportErrorCollector errors = new ImportErrorCollector();
    ConfigDocument parsed = parser.parse(json, errors);

    assertThat(errors.hasErrors()).isFalse();
    assertThat(parsed.schema().tables().get(0).columns().get(0).editorType()).isNull();
  }

  @Test
  void aHundredAndFiftyBrokenTablesAreCountedButOnlyAHundredAreKept() {
    ObjectNode json = validJson();
    ArrayNode tables = (ArrayNode) json.at("/schema/tables");
    tables.removeAll();
    for (int i = 0; i < 150; i++) {
      tables
          .addObject()
          .put("schemaName", "s")
          .put("tableName", "t_" + i)
          .put("displayOrder", "bad")
          .putArray("columns");
    }

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.total()).isEqualTo(150);
    assertThat(errors.errors()).hasSize(100);
    assertThat(errors.truncated()).isTrue();
  }

  @Test
  void errorPositionsUseTheJsonPositionFormat() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/3/columns/2").put("editorType", 1);

    ImportErrorCollector errors = parseCollecting(json);

    // 添え字は、ファイルの中の位置(0始まり)。スキーマ・テーブル・カラムの経路を、ドットと[添え字]でつなぐ。
    assertThat(errors.errors())
        .extracting(ImportValidationError::field)
        .containsExactly("schema.tables[3].columns[2].editorType");
  }

  @Test
  void errorsNeverContainTheSubmittedValues() {
    ObjectNode json = validJson();
    at(json, "/schema/tables/0").put("displayOrder", "SECRET-VALUE-123");
    at(json, "/rbac/primaryPermissions/0").put("level", "SECRET-LEVEL-456");

    ImportErrorCollector errors = parseCollecting(json);

    assertThat(errors.errors().toString())
        .doesNotContain("SECRET-VALUE-123")
        .doesNotContain("SECRET-LEVEL-456");
  }
}
