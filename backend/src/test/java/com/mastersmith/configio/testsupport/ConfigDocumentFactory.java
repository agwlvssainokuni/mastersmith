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

package com.mastersmith.configio.testsupport;

import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.document.ConfigDocument.AuxiliaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.Choice;
import com.mastersmith.configio.document.ConfigDocument.ColumnEntry;
import com.mastersmith.configio.document.ConfigDocument.FkRef;
import com.mastersmith.configio.document.ConfigDocument.GroupEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuSection;
import com.mastersmith.configio.document.ConfigDocument.PermissionScope;
import com.mastersmith.configio.document.ConfigDocument.PrimaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.RbacSection;
import com.mastersmith.configio.document.ConfigDocument.RoleEntry;
import com.mastersmith.configio.document.ConfigDocument.SchemaSection;
import com.mastersmith.configio.document.ConfigDocument.TableEntry;
import com.mastersmith.configio.document.ConfigDocument.TableRef;
import com.mastersmith.configio.document.ConfigDocument.TranslationItem;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 設定ファイル({@link
 * ConfigDocument})のテストデータの生成器。<b>業務固有の名前を、コードに持たない</b>ため、テーブル・カラム・ロールなどの名前は、連番から機械的に生成する ({@link
 * #mechanical(Spec)}。BR9.20・NFR8.1)。想定規模の上限(NFR1.3)も、同じ生成器で作る({@link Spec#upperBound()})。
 *
 * <p>唯一の例外として、「2種類の、業務ドメインの異なる設定プロファイル」で、同じ操作を流すテスト(team.md Q8-b)のために、業務ドメインの名前を持つプロファイル({@link
 * #productProfile()}・ {@link #libraryProfile()})を用意する。これらは、テストだけが使い、アプリケーションのコードには、業務の名前を持たない。
 */
public final class ConfigDocumentFactory {

  public static final JsonMapper JSON = JsonMapper.builder().build();

  private ConfigDocumentFactory() {}

  /** 生成する設定の規模。 */
  public record Spec(
      int schemas,
      int tablesPerSchema,
      int columnsPerTable,
      int translations,
      int roles,
      int groups,
      int primaryPermissions,
      int auxiliaryPermissions,
      int menuFolders) {

    /** 小さな設定(単体・統合のテスト用)。 */
    public static Spec small() {
      return new Spec(2, 2, 4, 6, 3, 2, 8, 3, 2);
    }

    /** 想定規模の上限(NFR1.3: テーブル100・カラム約3,000・翻訳約6,000・ロール50・主権限約5,000)。 */
    public static Spec upperBound() {
      return new Spec(10, 10, 30, 6000, 50, 20, 5000, 500, 10);
    }
  }

  /** 連番から機械的に、設定を生成する(すべての参照が、ファイルの中で解決でき、一意性の制約を満たす、有効な設定)。 */
  public static ConfigDocument mechanical(Spec spec) {
    List<TableEntry> tables = new ArrayList<>();
    for (int s = 0; s < spec.schemas(); s++) {
      for (int t = 0; t < spec.tablesPerSchema(); t++) {
        List<ColumnEntry> columns = new ArrayList<>();
        for (int c = 0; c < spec.columnsPerTable(); c++) {
          columns.add(column(c, t));
        }
        tables.add(
            new TableEntry(
                "schema_%04d".formatted(s),
                "table_%04d".formatted(t),
                t,
                spec.columnsPerTable() > 0 ? "column_0000" : null,
                columns));
      }
    }
    List<TranslationItem> translations = new ArrayList<>();
    for (int i = 0; i < spec.translations(); i++) {
      translations.add(
          new TranslationItem(
              "key.%05d".formatted(i / 2), i % 2 == 0 ? "ja" : "en", "text_%05d".formatted(i)));
    }
    SchemaSection schema = new SchemaSection(tables, translations);

    List<MenuEntry> menu = new ArrayList<>();
    for (int f = 0; f < spec.menuFolders(); f++) {
      List<MenuEntry> leaves = new ArrayList<>();
      for (int t = 0; t < Math.min(spec.tablesPerSchema(), 3); t++) {
        TableEntry target = tables.get((f * 3 + t) % Math.max(1, tables.size()));
        leaves.add(
            new MenuEntry(
                "leaf_%04d_%04d".formatted(f, t),
                t,
                new TableRef(target.schemaName(), target.tableName()),
                null));
      }
      menu.add(new MenuEntry("folder_%04d".formatted(f), f, null, leaves));
    }

    List<RoleEntry> roles = new ArrayList<>();
    for (int r = 0; r < spec.roles(); r++) {
      roles.add(new RoleEntry("role_%04d".formatted(r)));
    }
    List<GroupEntry> groups = new ArrayList<>();
    for (int g = 0; g < spec.groups(); g++) {
      groups.add(
          new GroupEntry(
              "group_%04d".formatted(g),
              spec.roles() == 0
                  ? List.of()
                  : List.of(
                          "role_%04d".formatted(g % spec.roles()),
                          "role_%04d".formatted((g + 1) % spec.roles()))
                      .stream()
                      .distinct()
                      .toList()));
    }
    List<PermissionScope> scopes = allScopes(tables);
    List<PrimaryPermissionEntry> primary = new ArrayList<>();
    for (int k = 0; k < spec.primaryPermissions() && spec.roles() > 0; k++) {
      primary.add(
          new PrimaryPermissionEntry(
              "role_%04d".formatted(k % spec.roles()),
              scopes.get((k / spec.roles()) % scopes.size()),
              PermissionLevel.values()[k % 3]));
    }
    List<AuxiliaryPermissionEntry> auxiliary = new ArrayList<>();
    List<PermissionScope> auxScopes =
        scopes.stream().filter(s -> s.scopeType() != ScopeType.COLUMN).toList();
    for (int k = 0; k < spec.auxiliaryPermissions() && spec.roles() > 0; k++) {
      auxiliary.add(
          new AuxiliaryPermissionEntry(
              "role_%04d".formatted(k % spec.roles()),
              auxScopes.get((k / spec.roles()) % auxScopes.size()),
              k % 2 == 0 ? Boolean.TRUE : null,
              k % 3 == 0 ? Boolean.FALSE : null));
    }
    return new ConfigDocument(
        ConfigDocument.SUPPORTED_FORMAT_VERSION,
        "2026-09-21T00:00:00Z",
        "test",
        schema,
        new MenuSection(menu),
        new RbacSection(roles, groups, primary, auxiliary));
  }

  private static ColumnEntry column(int index, int tableIndex) {
    String name = "column_%04d".formatted(index);
    return switch (index % 4) {
      case 0 ->
          new ColumnEntry(
              name,
              index,
              null,
              "text",
              Map.of("maxLength", 50),
              "visible",
              index == 0,
              List.of(),
              null);
      case 1 ->
          new ColumnEntry(
              name,
              index,
              "#,##0",
              "integer",
              Map.of("min", 0, "max", 999),
              "visible",
              false,
              List.of(),
              null);
      case 2 ->
          new ColumnEntry(
              name,
              index,
              null,
              "select",
              Map.of(),
              "hidden",
              false,
              List.of(
                  new Choice("v1", "choice.%d.%d.1".formatted(tableIndex, index)),
                  new Choice("v2", "choice.%d.%d.2".formatted(tableIndex, index))),
              null);
      default ->
          new ColumnEntry(
              name,
              index,
              null,
              "select",
              Map.of(),
              "visible",
              false,
              List.of(),
              new FkRef("schema_0000", "table_0000", "column_0000", "column_0001"));
    };
  }

  private static List<PermissionScope> allScopes(List<TableEntry> tables) {
    List<PermissionScope> scopes = new ArrayList<>();
    tables.stream()
        .map(TableEntry::schemaName)
        .distinct()
        .forEach(s -> scopes.add(new PermissionScope(ScopeType.SCHEMA, s, null, null)));
    for (TableEntry table : tables) {
      scopes.add(new PermissionScope(ScopeType.TABLE, table.schemaName(), table.tableName(), null));
    }
    for (TableEntry table : tables) {
      for (ColumnEntry column : table.columns()) {
        scopes.add(
            new PermissionScope(
                ScopeType.COLUMN, table.schemaName(), table.tableName(), column.columnName()));
      }
    }
    return scopes;
  }

  /**
   * 設定に、管理者のロール(設定管理画面の予約スキーマ・設定に含まれるすべてのスキーマに対する、主権限FULLと、補助権限(作成・削除)を持つ)を加える。取り込み後に、RBAC設定が存在する状態(ブートストラップ状態の終了)でも、
   * このロールで、再び、取り込める(権限昇格の判定を、通る)ようにするため。
   */
  public static ConfigDocument withAdministrator(ConfigDocument document, String roleName) {
    List<RoleEntry> roles = new ArrayList<>(document.rbac().roles());
    roles.add(new RoleEntry(roleName));
    List<PrimaryPermissionEntry> primary = new ArrayList<>(document.rbac().primaryPermissions());
    List<AuxiliaryPermissionEntry> auxiliary =
        new ArrayList<>(document.rbac().auxiliaryPermissions());
    List<String> schemas =
        document.schema().tables().stream().map(TableEntry::schemaName).distinct().toList();
    for (String schema : schemas) {
      PermissionScope scope = new PermissionScope(ScopeType.SCHEMA, schema, null, null);
      primary.add(new PrimaryPermissionEntry(roleName, scope, PermissionLevel.FULL));
      auxiliary.add(new AuxiliaryPermissionEntry(roleName, scope, true, true));
    }
    primary.add(
        new PrimaryPermissionEntry(
            roleName,
            new PermissionScope(ScopeType.SCHEMA, ADMIN_SCREEN_SCHEMA, null, null),
            PermissionLevel.FULL));
    return new ConfigDocument(
        document.formatVersion(),
        document.exportedAt(),
        document.appVersion(),
        document.schema(),
        document.menu(),
        new RbacSection(roles, document.rbac().groups(), primary, auxiliary));
  }

  /** 設定管理画面の予約スキーマ名(permission-engineが管理する。テストが、権限を与えるために、明示する)。 */
  public static final String ADMIN_SCREEN_SCHEMA = "__system__:config-import-export";

  /** 設定ファイルを、JSONの木構造にする(リクエスト本体の{@code JsonNode}と同じ型)。 */
  public static JsonNode toJson(ConfigDocument document) {
    return JSON.valueToTree(document);
  }

  // ---- 業務ドメインの異なる、2つの設定プロファイル(team.md Q8-b。テストだけが使う) ----

  /** 商品マスタ用のプロファイル(ECショップ)。 */
  public static ConfigDocument productProfile() {
    return profile(
        "shop",
        "products",
        List.of("product_id", "product_name", "price", "category_id"),
        "categories",
        List.of("category_id", "category_name"),
        List.of("商品管理", "商品マスタ"),
        "shop_admin");
  }

  /** 蔵書マスタ用のプロファイル(蔵書管理)。 */
  public static ConfigDocument libraryProfile() {
    return profile(
        "library",
        "books",
        List.of("book_id", "title", "author", "shelf_id"),
        "shelves",
        List.of("shelf_id", "shelf_name"),
        List.of("蔵書管理", "蔵書マスタ"),
        "librarian");
  }

  private static ConfigDocument profile(
      String schemaName,
      String mainTable,
      List<String> mainColumns,
      String refTable,
      List<String> refColumns,
      List<String> menuLabels,
      String roleName) {
    List<ColumnEntry> main = new ArrayList<>();
    for (int i = 0; i < mainColumns.size(); i++) {
      String column = mainColumns.get(i);
      boolean last = i == mainColumns.size() - 1;
      main.add(
          last
              ? new ColumnEntry(
                  column,
                  i,
                  null,
                  "select",
                  Map.of(),
                  "visible",
                  false,
                  List.of(),
                  new FkRef(schemaName, refTable, refColumns.get(0), refColumns.get(1)))
              : new ColumnEntry(
                  column,
                  i,
                  null,
                  i == 0 ? "integer" : "text",
                  Map.of("required", i == 1),
                  "visible",
                  i == 0,
                  List.of(),
                  null));
    }
    List<ColumnEntry> ref = new ArrayList<>();
    for (int i = 0; i < refColumns.size(); i++) {
      ref.add(
          new ColumnEntry(
              refColumns.get(i),
              i,
              null,
              i == 0 ? "integer" : "text",
              Map.of(),
              "visible",
              i == 0,
              List.of(),
              null));
    }
    TableEntry main1 = new TableEntry(schemaName, mainTable, 1, null, main);
    TableEntry ref1 = new TableEntry(schemaName, refTable, 2, null, ref);
    List<TranslationItem> translations =
        List.of(
            new TranslationItem(
                "table.%s.%s.label".formatted(schemaName, mainTable), "ja", menuLabels.get(1)),
            new TranslationItem(
                "table.%s.%s.label".formatted(schemaName, mainTable), "en", mainTable),
            new TranslationItem(
                "table.%s.%s.label".formatted(schemaName, refTable), "ja", refTable));
    List<MenuEntry> menu =
        List.of(
            new MenuEntry(
                menuLabels.get(0),
                1,
                null,
                List.of(
                    new MenuEntry(menuLabels.get(1), 1, new TableRef(schemaName, mainTable), null),
                    new MenuEntry(refTable, 2, new TableRef(schemaName, refTable), null))));
    return new ConfigDocument(
        ConfigDocument.SUPPORTED_FORMAT_VERSION,
        "2026-09-21T00:00:00Z",
        "test",
        new SchemaSection(List.of(main1, ref1), translations),
        new MenuSection(menu),
        new RbacSection(
            List.of(new RoleEntry(roleName), new RoleEntry(roleName + "_viewer")),
            List.of(new GroupEntry(roleName + "_group", List.of(roleName))),
            List.of(
                new PrimaryPermissionEntry(
                    roleName,
                    new PermissionScope(ScopeType.SCHEMA, schemaName, null, null),
                    PermissionLevel.FULL),
                new PrimaryPermissionEntry(
                    roleName + "_viewer",
                    new PermissionScope(ScopeType.TABLE, schemaName, mainTable, null),
                    PermissionLevel.READ)),
            List.of(
                new AuxiliaryPermissionEntry(
                    roleName,
                    new PermissionScope(ScopeType.SCHEMA, schemaName, null, null),
                    true,
                    true))));
  }
}
