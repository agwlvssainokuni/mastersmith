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

import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.ConfigDocumentNormalizer;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 安全失敗・バリデーションのテスト(team.md Q8-a、code-generation-plan.md Step 18):
 * 不正・不完全な設定ファイルは、<b>422で拒否</b>され、<b>内部設定DBは、まったく変わらない</b>(すべての設定の表の内容が、取り込み前と同一。
 * キャッシュも有効なまま)こと。誤りは、位置(JSON上の位置)とi18nキーで返り、入力値・内部の詳細は、含まれない。実際の組込みH2・実際の3ユニット・取り込みのAPI経由。1行=1つの不正な設定ファイルの、表形式。
 */
class ConfigImportSafeFailureTest extends ConfigIoIntegrationTestBase {

  private static final List<String> CONFIG_TABLES =
      List.of(
          "table_config",
          "column_config",
          "translation_entry",
          "menu_item",
          "role",
          "permission_group",
          "group_role",
          "primary_permission",
          "auxiliary_permission");

  private record Case(
      String name, Consumer<ObjectNode> corrupt, String expectedField, String expectedMessage) {
    @Override
    public String toString() {
      return name;
    }
  }

  private static ObjectNode at(ObjectNode root, String pointer) {
    return (ObjectNode) root.at(pointer);
  }

  private static Case c(String name, Consumer<ObjectNode> corrupt, String field, String message) {
    return new Case(name, corrupt, field, message);
  }

  static Stream<Arguments> invalidFiles() {
    return cases().map(Arguments::of);
  }

  private static Stream<Case> cases() {
    return Stream.of(
        // 構造
        c("必須のセクションの欠落(schema)", r -> r.remove("schema"), "schema", "config.import.field.required"),
        c("必須のセクションの欠落(menu)", r -> r.remove("menu"), "menu", "config.import.field.required"),
        c("必須のセクションの欠落(rbac)", r -> r.remove("rbac"), "rbac", "config.import.field.required"),
        c("セクションの型の誤り", r -> r.put("rbac", 5), "rbac", "config.import.field.type"),
        c(
            "テーブル名の欠落",
            r -> at(r, "/schema/tables/0").remove("tableName"),
            "schema.tables[0].tableName",
            "config.import.field.required"),
        c(
            "表示順の型の誤り",
            r -> at(r, "/schema/tables/1").put("displayOrder", "x"),
            "schema.tables[1].displayOrder",
            "config.import.field.type"),
        c(
            "テーブルの重複",
            r -> ((ArrayNode) r.at("/schema/tables")).add(r.at("/schema/tables/0").deepCopy()),
            "schema.tables[4]",
            "config.import.field.duplicate"),
        c(
            "翻訳の言語が許容外",
            r -> at(r, "/schema/translations/0").put("locale", "fr"),
            "schema.translations[0].locale",
            "config.import.field.value.invalid"),
        // 各ユニットの検証(config-engine)
        c(
            "editorTypeが許容外",
            r -> at(r, "/schema/tables/0/columns/0").put("editorType", "no_such_type"),
            "schema.tables[0].columns[0].editorType",
            "config.import.field.value.invalid"),
        c(
            "editorTypeの欠落",
            r -> at(r, "/schema/tables/0/columns/1").remove("editorType"),
            "schema.tables[0].columns[1].editorType",
            "config.import.field.required"),
        c(
            "visibilityが許容外",
            r -> at(r, "/schema/tables/0/columns/0").put("visibility", "half"),
            "schema.tables[0].columns[0].visibility",
            "config.import.field.value.invalid"),
        c(
            "選択部品に選択肢もFK参照もない",
            r -> {
              ObjectNode column = at(r, "/schema/tables/0/columns/2");
              column.putArray("choiceOptions");
            },
            "schema.tables[0].columns[2]",
            "config.import.column.choiceOrFkExclusive"),
        // 参照整合
        c(
            "メニューの遷移先が、ファイルにない",
            r -> at(r, "/menu/items/0/children/0/targetTable").put("tableName", "table_9999"),
            "menu.items[0].children[0].targetTable",
            "config.import.reference.notFound"),
        c(
            "権限の対象のテーブルが、ファイルにない",
            r ->
                at(r, "/rbac/primaryPermissions/0/scope")
                    .put("scopeType", "TABLE")
                    .put("tableName", "table_9999"),
            "rbac.primaryPermissions[0].scope",
            "config.import.reference.notFound"),
        c(
            "権限のロールが、ファイルにない",
            r -> at(r, "/rbac/primaryPermissions/0").put("roleName", "role_9999"),
            "rbac.primaryPermissions[0].roleName",
            "config.import.reference.notFound"),
        c(
            "グループのロールが、ファイルにない",
            r -> ((ArrayNode) r.at("/rbac/groups/0/roleNames")).add("role_9999"),
            "rbac.groups[0].roleNames[2]",
            "config.import.reference.notFound"),
        c(
            "FK参照先が、ファイルにない",
            r ->
                at(r, "/schema/tables/0/columns/3/fkReference")
                    .put("referencedTableName", "table_9999"),
            "schema.tables[0].columns[3].fkReference.referencedTableName",
            "config.import.reference.notFound"),
        c(
            "楽観ロック列が、テーブルにない",
            r -> at(r, "/schema/tables/0").put("optimisticLockColumn", "column_9999"),
            "schema.tables[0].optimisticLockColumn",
            "config.import.reference.notFound"),
        // メニューの構造
        c(
            "リーフが子を持つ",
            r -> {
              ObjectNode leaf = at(r, "/menu/items/0/children/0");
              leaf.putArray("children").addObject().put("label", "x").put("order", 1);
            },
            "menu.items[0].children[0]",
            "config.import.menu.leafHasChildren"),
        // 権限の構造
        c(
            "補助権限の対象がCOLUMN",
            r ->
                at(r, "/rbac/auxiliaryPermissions/0/scope")
                    .put("scopeType", "COLUMN")
                    .put("tableName", "table_0000")
                    .put("columnName", "column_0000"),
            "rbac.auxiliaryPermissions[0].scope.scopeType",
            "config.import.rbac.auxiliaryColumn"),
        c(
            "未知の予約スキーマ名",
            r ->
                at(r, "/rbac/primaryPermissions/0/scope")
                    .put("scopeType", "SCHEMA")
                    .put("schemaName", "__system__:nothing"),
            "rbac.primaryPermissions[0].scope.schemaName",
            "config.import.rbac.reservedUnknown"),
        c(
            "主権限が0件",
            r -> ((ArrayNode) r.at("/rbac/primaryPermissions")).removeAll(),
            "rbac.primaryPermissions",
            "config.import.rbac.empty"));
  }

  /** 内部設定DBの設定の表の、全行の内容(表ごと・行の文字列表現の集合)。取り込み前後の同一性の確認に用いる。 */
  private Map<String, List<String>> snapshotOfConfigTables() {
    Map<String, List<String>> snapshot = new java.util.LinkedHashMap<>();
    for (String table : CONFIG_TABLES) {
      snapshot.put(
          table,
          jdbc.queryForList("select * from " + table).stream()
              .map(ConfigImportSafeFailureTest::render)
              .sorted()
              .toList());
    }
    return snapshot;
  }

  /** 行の内容を、比較できる文字列にする(JSON型の列は、バイト列で返るため、その内容を文字列にする)。 */
  private static String render(Map<String, Object> row) {
    Map<String, Object> sorted = new java.util.TreeMap<>();
    row.forEach(
        (k, v) ->
            sorted.put(
                k,
                v instanceof byte[] bytes
                    ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    : v));
    return sorted.toString();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidFiles")
  void anInvalidFileIsRejectedWith422AndTheInternalConfigurationDatabaseIsCompletelyUnchanged(
      Case testCase) throws Exception {
    ConfigDocument valid =
        ConfigDocumentFactory.withAdministrator(
            ConfigDocumentFactory.mechanical(Spec.small()), ADMIN_ROLE);
    assertThat(postImport(valid).getResponse().getStatus()).isEqualTo(200);
    actAs("safe-admin", ADMIN_ROLE);
    configCache.allTableConfigs();
    Map<String, List<String>> before = snapshotOfConfigTables();
    ConfigDocument exportedBefore = ConfigDocumentNormalizer.normalize(exportDocument());
    long generationBefore = configCache.generation();
    JsonNode broken = ConfigDocumentFactory.toJson(valid).deepCopy();
    testCase.corrupt().accept((ObjectNode) broken);

    MvcResult result = postImport(broken);

    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(422);
    JsonNode body = ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("errors").size()).isPositive();
    boolean reported = false;
    for (JsonNode error : body.get("errors")) {
      if (error.get("field").stringValue().equals(testCase.expectedField())
          && error.get("message").stringValue().equals(testCase.expectedMessage())) {
        reported = true;
      }
    }
    assertThat(reported).as("errors=" + body.get("errors")).isTrue();
    // 何も変わらない: 設定の全表・エクスポートの内容・キャッシュ。
    assertThat(snapshotOfConfigTables()).isEqualTo(before);
    assertThat(ConfigDocumentNormalizer.normalize(exportDocument())).isEqualTo(exportedBefore);
    assertThat(configCache.generation()).isEqualTo(generationBefore);
    assertThat(configCache.isStale()).isFalse();
    // 応答に、入力値そのもの・内部の詳細は含まれない。
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("Exception")
        .doesNotContain("at com.mastersmith")
        .doesNotContain("select ");
  }

  @ParameterizedTest(name = "空のDBに対する不正なファイル [{index}] {0}")
  @MethodSource("invalidFiles")
  void anInvalidFileOnAnEmptyDatabaseLeavesItEmpty(Case testCase) throws Exception {
    ConfigDocument valid =
        ConfigDocumentFactory.withAdministrator(
            ConfigDocumentFactory.mechanical(Spec.small()), ADMIN_ROLE);
    JsonNode broken = ConfigDocumentFactory.toJson(valid).deepCopy();
    testCase.corrupt().accept((ObjectNode) broken);

    MvcResult result = postImport(broken);

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    snapshotOfConfigTables().forEach((table, rows) -> assertThat(rows).as(table).isEmpty());
  }

  @org.junit.jupiter.api.Test
  void everyErrorOfAFileWithManyProblemsIsReportedInOneResponseUpToTheLimit() throws Exception {
    ConfigDocument valid =
        ConfigDocumentFactory.withAdministrator(
            ConfigDocumentFactory.mechanical(Spec.small()), ADMIN_ROLE);
    ObjectNode broken = (ObjectNode) ConfigDocumentFactory.toJson(valid).deepCopy();
    ArrayNode tables = (ArrayNode) broken.at("/schema/tables");
    for (int i = 0; i < 4; i++) {
      ArrayNode columns = (ArrayNode) tables.get(i).get("columns");
      for (JsonNode column : columns) {
        ((ObjectNode) column).put("editorType", "bad");
      }
    }
    for (int i = 0; i < 120; i++) {
      tables
          .addObject()
          .put("schemaName", "s")
          .put("tableName", "t_" + i)
          .put("displayOrder", "bad")
          .putArray("columns");
    }

    MvcResult result = postImport(broken);

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    JsonNode body = ConfigDocumentFactory.JSON.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("errors").size()).isEqualTo(100);
    assertThat(body.get("errorCount").intValue()).isGreaterThan(100);
    assertThat(body.get("truncated").booleanValue()).isTrue();
    assertThat(tableCount()).isZero();
  }

  private long tableCount() {
    return jdbc.queryForObject("select count(*) from table_config", Long.class);
  }
}
