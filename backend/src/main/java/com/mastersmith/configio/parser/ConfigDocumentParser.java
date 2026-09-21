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

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportValidationError;
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
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.configio.exception.ConfigImportValidationException;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 設定ファイルの{@link JsonNode}(木構造)を、{@link
 * ConfigDocument}へ変換し、構造(必須項目・型・許容値・一意性)を検証する(config-import-export BR9.6〜BR9.8)。誤りは、位置(JSON上の位置。例
 * {@code schema.tables[3].columns[2].editorType})と、翻訳用のメッセージキーで、{@link
 * ImportErrorCollector}へ<b>全件を集める</b>(1件目で止めない)。
 *
 * <ul>
 *   <li>構文: ルートが、JSONのオブジェクトでなければ、その誤りだけで終了する({@code MALFORMED})。形式: {@code
 *       formatVersion}が欠落・未対応なら、その誤りだけで終了する({@code
 *       UNSUPPORTED_FORMAT}。BR9.8)。どちらも、以降の検証が意味を持たないため、例外で返す。
 *   <li>未知のプロパティは、すべての階層で、無視する(BR9.8)。読むのは、必要な項目だけである。
 *   <li>戻り値は、誤りがあっても、後続の検証(参照整合・各ユニットの検証)を続けられる、可能な範囲の文書である。自然キー(名前)を得られないエントリは、除く。ほかの項目の欠落・型の誤りは、誤りとして
 *       報告したうえで、既定の値で補い、エントリを残す(後続の検証で、連鎖した誤りを増やさないため)。
 *   <li>カラムの{@code editorType}・{@code visibility}・{@code validationRule}の中身・翻訳の{@code
 *       locale}の許容値の検証は、config-engineの規則に委ねる(BR9.20)。ここでは、文字列かどうかだけを見る。
 * </ul>
 *
 * <p>業務固有の名前を、コードに持たない。名前・値の意味は、解釈しない(BR9.20)。
 */
@Component
public class ConfigDocumentParser {

  private static final String EXPECTED_STRING = "string";
  private static final String EXPECTED_INTEGER = "integer";
  private static final String EXPECTED_BOOLEAN = "boolean";
  private static final String EXPECTED_OBJECT = "object";
  private static final String EXPECTED_ARRAY = "array";

  /**
   * @param root リクエスト本体({@code @RequestBody JsonNode})
   * @param errors 構造の誤りを集める先
   * @throws ConfigImportValidationException 構文の誤り(ルートがオブジェクトでない)・形式の誤り({@code
   *     formatVersion}が欠落・未対応)。その誤りだけを持つ
   */
  public ConfigDocument parse(JsonNode root, ImportErrorCollector errors) {
    if (root == null || !root.isObject()) {
      throw envelopeError(
          FailureCategory.MALFORMED,
          ImportValidationError.of("", ImportMessageKeys.JSON_MALFORMED));
    }
    JsonNode version = root.get("formatVersion");
    if (version == null
        || !version.isIntegralNumber()
        || !version.canConvertToInt()
        || version.intValue() != ConfigDocument.SUPPORTED_FORMAT_VERSION) {
      throw envelopeError(
          FailureCategory.UNSUPPORTED_FORMAT,
          ImportValidationError.of(
              "formatVersion",
              ImportMessageKeys.FORMAT_UNSUPPORTED,
              Map.of("supported", ConfigDocument.SUPPORTED_FORMAT_VERSION)));
    }
    String exportedAt = optionalString(root, "exportedAt", "exportedAt", errors);
    String appVersion = optionalString(root, "appVersion", "appVersion", errors);
    SchemaSection schema = parseSchema(requiredObject(root, "schema", "schema", errors), errors);
    MenuSection menu = parseMenu(requiredObject(root, "menu", "menu", errors), errors);
    RbacSection rbac = parseRbac(requiredObject(root, "rbac", "rbac", errors), errors);
    return new ConfigDocument(
        ConfigDocument.SUPPORTED_FORMAT_VERSION, exportedAt, appVersion, schema, menu, rbac);
  }

  private static ConfigImportValidationException envelopeError(
      FailureCategory category, ImportValidationError error) {
    return new ConfigImportValidationException(category, List.of(error), 1, false);
  }

  // ---- schema ----

  private SchemaSection parseSchema(JsonNode node, ImportErrorCollector errors) {
    if (node == null) {
      return null;
    }
    List<TableEntry> tables = new ArrayList<>();
    Set<String> tableKeys = new HashSet<>();
    JsonNode tableArray = requiredArray(node, "tables", "schema.tables", errors);
    for (int i = 0; tableArray != null && i < tableArray.size(); i++) {
      String path = "schema.tables[" + i + "]";
      JsonNode table = elementObject(tableArray.get(i), path, errors);
      if (table == null) {
        continue;
      }
      TableEntry entry = parseTable(table, path, errors);
      if (entry == null) {
        continue;
      }
      if (!tableKeys.add(key(entry.schemaName(), entry.tableName()))) {
        errors.add(path, ImportMessageKeys.FIELD_DUPLICATE);
        continue;
      }
      tables.add(entry);
    }
    List<TranslationItem> translations = new ArrayList<>();
    Set<String> translationKeys = new HashSet<>();
    JsonNode translationArray = requiredArray(node, "translations", "schema.translations", errors);
    for (int i = 0; translationArray != null && i < translationArray.size(); i++) {
      String path = "schema.translations[" + i + "]";
      JsonNode item = elementObject(translationArray.get(i), path, errors);
      if (item == null) {
        continue;
      }
      String i18nKey = requiredString(item, "i18nKey", path + ".i18nKey", errors);
      String locale = requiredString(item, "locale", path + ".locale", errors);
      String text = requiredString(item, "text", path + ".text", errors);
      if (i18nKey == null || locale == null) {
        continue;
      }
      if (!translationKeys.add(key(i18nKey, locale))) {
        errors.add(path, ImportMessageKeys.FIELD_DUPLICATE);
        continue;
      }
      translations.add(new TranslationItem(i18nKey, locale, text));
    }
    return new SchemaSection(tables, translations);
  }

  private TableEntry parseTable(JsonNode node, String path, ImportErrorCollector errors) {
    String schemaName = requiredString(node, "schemaName", path + ".schemaName", errors);
    String tableName = requiredString(node, "tableName", path + ".tableName", errors);
    int displayOrder = requiredInt(node, "displayOrder", path + ".displayOrder", errors);
    String optimisticLockColumn =
        optionalString(node, "optimisticLockColumn", path + ".optimisticLockColumn", errors);
    List<ColumnEntry> columns = new ArrayList<>();
    Set<String> columnNames = new HashSet<>();
    JsonNode columnArray = requiredArray(node, "columns", path + ".columns", errors);
    for (int j = 0; columnArray != null && j < columnArray.size(); j++) {
      String columnPath = path + ".columns[" + j + "]";
      JsonNode column = elementObject(columnArray.get(j), columnPath, errors);
      if (column == null) {
        continue;
      }
      ColumnEntry entry = parseColumn(column, columnPath, errors);
      if (entry == null) {
        continue;
      }
      if (!columnNames.add(entry.columnName())) {
        errors.add(columnPath, ImportMessageKeys.FIELD_DUPLICATE);
        continue;
      }
      columns.add(entry);
    }
    if (schemaName == null || tableName == null) {
      return null;
    }
    return new TableEntry(schemaName, tableName, displayOrder, optimisticLockColumn, columns);
  }

  private ColumnEntry parseColumn(JsonNode node, String path, ImportErrorCollector errors) {
    String columnName = requiredString(node, "columnName", path + ".columnName", errors);
    int displayOrder = requiredInt(node, "displayOrder", path + ".displayOrder", errors);
    String format = optionalString(node, "format", path + ".format", errors);
    // editorType・visibilityの必須・許容値の検証は、config-engineの規則に委ねる(BR9.20)。ここでは、文字列かどうかだけを見る。
    String editorType = stringOrNullReportingType(node, "editorType", path + ".editorType", errors);
    String visibility = stringOrNullReportingType(node, "visibility", path + ".visibility", errors);
    Map<String, Object> validationRule =
        optionalRule(node, "validationRule", path + ".validationRule", errors);
    Boolean isPrimaryKey = optionalBoolean(node, "isPrimaryKey", path + ".isPrimaryKey", errors);
    List<Choice> choices = parseChoices(node, path, errors);
    FkRef fk = parseFk(node, path, errors);
    if (columnName == null) {
      return null;
    }
    return new ColumnEntry(
        columnName,
        displayOrder,
        format,
        editorType,
        validationRule,
        visibility,
        isPrimaryKey,
        choices,
        fk);
  }

  private List<Choice> parseChoices(JsonNode column, String path, ImportErrorCollector errors) {
    JsonNode array = optionalArray(column, "choiceOptions", path + ".choiceOptions", errors);
    if (array == null) {
      return null;
    }
    List<Choice> choices = new ArrayList<>();
    for (int k = 0; k < array.size(); k++) {
      String choicePath = path + ".choiceOptions[" + k + "]";
      JsonNode choice = elementObject(array.get(k), choicePath, errors);
      if (choice == null) {
        continue;
      }
      choices.add(
          new Choice(
              requiredString(choice, "value", choicePath + ".value", errors),
              requiredString(choice, "i18nKey", choicePath + ".i18nKey", errors)));
    }
    return choices;
  }

  private FkRef parseFk(JsonNode column, String path, ImportErrorCollector errors) {
    JsonNode fk = optionalObject(column, "fkReference", path + ".fkReference", errors);
    if (fk == null) {
      return null;
    }
    String fkPath = path + ".fkReference";
    return new FkRef(
        requiredString(fk, "referencedSchemaName", fkPath + ".referencedSchemaName", errors),
        requiredString(fk, "referencedTableName", fkPath + ".referencedTableName", errors),
        requiredString(
            fk, "referencedValueColumnName", fkPath + ".referencedValueColumnName", errors),
        requiredString(
            fk, "referencedLabelColumnName", fkPath + ".referencedLabelColumnName", errors));
  }

  // ---- menu ----

  private MenuSection parseMenu(JsonNode node, ImportErrorCollector errors) {
    if (node == null) {
      return null;
    }
    return new MenuSection(
        parseMenuEntries(requiredArray(node, "items", "menu.items", errors), "menu.items", errors));
  }

  private List<MenuEntry> parseMenuEntries(
      JsonNode array, String path, ImportErrorCollector errors) {
    List<MenuEntry> entries = new ArrayList<>();
    for (int i = 0; array != null && i < array.size(); i++) {
      String itemPath = path + "[" + i + "]";
      JsonNode item = elementObject(array.get(i), itemPath, errors);
      if (item == null) {
        continue;
      }
      String label = requiredString(item, "label", itemPath + ".label", errors);
      int order = requiredInt(item, "order", itemPath + ".order", errors);
      TableRef target = parseTableRef(item, "targetTable", itemPath + ".targetTable", errors);
      JsonNode children = optionalArray(item, "children", itemPath + ".children", errors);
      List<MenuEntry> childEntries =
          children == null ? null : parseMenuEntries(children, itemPath + ".children", errors);
      entries.add(new MenuEntry(label == null ? "" : label, order, target, childEntries));
    }
    return entries;
  }

  private TableRef parseTableRef(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = optionalObject(parent, name, path, errors);
    if (node == null) {
      return null;
    }
    return new TableRef(
        requiredString(node, "schemaName", path + ".schemaName", errors),
        requiredString(node, "tableName", path + ".tableName", errors));
  }

  // ---- rbac ----

  private RbacSection parseRbac(JsonNode node, ImportErrorCollector errors) {
    if (node == null) {
      return null;
    }
    List<RoleEntry> roles = new ArrayList<>();
    Set<String> roleNames = new HashSet<>();
    JsonNode roleArray = requiredArray(node, "roles", "rbac.roles", errors);
    for (int i = 0; roleArray != null && i < roleArray.size(); i++) {
      String path = "rbac.roles[" + i + "]";
      JsonNode role = elementObject(roleArray.get(i), path, errors);
      if (role == null) {
        continue;
      }
      String name = requiredString(role, "name", path + ".name", errors);
      if (name == null) {
        continue;
      }
      if (!roleNames.add(name)) {
        errors.add(path, ImportMessageKeys.FIELD_DUPLICATE);
        continue;
      }
      roles.add(new RoleEntry(name));
    }
    List<GroupEntry> groups =
        parseGroups(requiredArray(node, "groups", "rbac.groups", errors), errors);
    List<PrimaryPermissionEntry> primary =
        parsePrimary(
            requiredArray(node, "primaryPermissions", "rbac.primaryPermissions", errors), errors);
    List<AuxiliaryPermissionEntry> auxiliary =
        parseAuxiliary(
            requiredArray(node, "auxiliaryPermissions", "rbac.auxiliaryPermissions", errors),
            errors);
    return new RbacSection(roles, groups, primary, auxiliary);
  }

  private List<GroupEntry> parseGroups(JsonNode array, ImportErrorCollector errors) {
    List<GroupEntry> groups = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (int i = 0; array != null && i < array.size(); i++) {
      String path = "rbac.groups[" + i + "]";
      JsonNode group = elementObject(array.get(i), path, errors);
      if (group == null) {
        continue;
      }
      String name = requiredString(group, "name", path + ".name", errors);
      List<String> roleNames = new ArrayList<>();
      Set<String> seenRoles = new HashSet<>();
      JsonNode roleArray = requiredArray(group, "roleNames", path + ".roleNames", errors);
      for (int j = 0; roleArray != null && j < roleArray.size(); j++) {
        String rolePath = path + ".roleNames[" + j + "]";
        JsonNode roleNode = roleArray.get(j);
        if (!roleNode.isString() || roleNode.stringValue().isBlank()) {
          errors.add(
              rolePath,
              roleNode.isString() || roleNode.isNull()
                  ? ImportMessageKeys.FIELD_REQUIRED
                  : ImportMessageKeys.FIELD_TYPE,
              roleNode.isString() || roleNode.isNull()
                  ? Map.of()
                  : Map.of("expected", EXPECTED_STRING));
          continue;
        }
        if (!seenRoles.add(roleNode.stringValue())) {
          errors.add(rolePath, ImportMessageKeys.FIELD_DUPLICATE);
          continue;
        }
        roleNames.add(roleNode.stringValue());
      }
      if (name == null) {
        continue;
      }
      if (!names.add(name)) {
        errors.add(path, ImportMessageKeys.FIELD_DUPLICATE);
        continue;
      }
      groups.add(new GroupEntry(name, roleNames));
    }
    return groups;
  }

  private List<PrimaryPermissionEntry> parsePrimary(JsonNode array, ImportErrorCollector errors) {
    List<PrimaryPermissionEntry> entries = new ArrayList<>();
    Set<String> keys = new HashSet<>();
    for (int i = 0; array != null && i < array.size(); i++) {
      String path = "rbac.primaryPermissions[" + i + "]";
      JsonNode item = elementObject(array.get(i), path, errors);
      if (item == null) {
        continue;
      }
      String roleName = requiredString(item, "roleName", path + ".roleName", errors);
      PermissionScope scope = parseScope(item, path + ".scope", false, errors);
      PermissionLevel level =
          enumValue(item, "level", path + ".level", PermissionLevel.class, errors);
      if (roleName == null || scope == null || level == null) {
        continue;
      }
      if (!keys.add(key(roleName, scopeKey(scope)))) {
        errors.add(path, ImportMessageKeys.FIELD_DUPLICATE);
        continue;
      }
      entries.add(new PrimaryPermissionEntry(roleName, scope, level));
    }
    return entries;
  }

  private List<AuxiliaryPermissionEntry> parseAuxiliary(
      JsonNode array, ImportErrorCollector errors) {
    List<AuxiliaryPermissionEntry> entries = new ArrayList<>();
    Set<String> keys = new HashSet<>();
    for (int i = 0; array != null && i < array.size(); i++) {
      String path = "rbac.auxiliaryPermissions[" + i + "]";
      JsonNode item = elementObject(array.get(i), path, errors);
      if (item == null) {
        continue;
      }
      String roleName = requiredString(item, "roleName", path + ".roleName", errors);
      PermissionScope scope = parseScope(item, path + ".scope", true, errors);
      Boolean create = optionalBoolean(item, "createAllowed", path + ".createAllowed", errors);
      Boolean delete = optionalBoolean(item, "deleteAllowed", path + ".deleteAllowed", errors);
      if (roleName == null || scope == null) {
        continue;
      }
      if (!keys.add(key(roleName, scopeKey(scope)))) {
        errors.add(path, ImportMessageKeys.FIELD_DUPLICATE);
        continue;
      }
      entries.add(new AuxiliaryPermissionEntry(roleName, scope, create, delete));
    }
    return entries;
  }

  /** 権限の対象(scope)。TABLEはtableName、COLUMNはtableName・columnNameが必須。補助権限のCOLUMNは、構造の誤り。 */
  private PermissionScope parseScope(
      JsonNode parent, String path, boolean auxiliary, ImportErrorCollector errors) {
    JsonNode node = requiredObject(parent, "scope", path, errors);
    if (node == null) {
      return null;
    }
    ScopeType scopeType =
        enumValue(node, "scopeType", path + ".scopeType", ScopeType.class, errors);
    String schemaName = requiredString(node, "schemaName", path + ".schemaName", errors);
    String tableName = null;
    String columnName = null;
    if (scopeType == ScopeType.TABLE || scopeType == ScopeType.COLUMN) {
      tableName = requiredString(node, "tableName", path + ".tableName", errors);
    } else {
      tableName = optionalString(node, "tableName", path + ".tableName", errors);
    }
    if (scopeType == ScopeType.COLUMN) {
      columnName = requiredString(node, "columnName", path + ".columnName", errors);
    } else {
      columnName = optionalString(node, "columnName", path + ".columnName", errors);
    }
    if (auxiliary && scopeType == ScopeType.COLUMN) {
      errors.add(path + ".scopeType", ImportMessageKeys.RBAC_AUXILIARY_COLUMN);
      return null;
    }
    if (scopeType == null || schemaName == null) {
      return null;
    }
    if (scopeType != ScopeType.COLUMN && scopeType != ScopeType.TABLE) {
      tableName = null;
    }
    if (scopeType != ScopeType.COLUMN) {
      columnName = null;
    }
    if ((scopeType != ScopeType.SCHEMA && tableName == null)
        || (scopeType == ScopeType.COLUMN && columnName == null)) {
      return null;
    }
    return new PermissionScope(scopeType, schemaName, tableName, columnName);
  }

  private static String scopeKey(PermissionScope scope) {
    return key(scope.scopeType().name(), scope.schemaName(), scope.tableName(), scope.columnName());
  }

  // ---- 値の読み取り(誤りは、位置つきで集める) ----

  private static JsonNode child(JsonNode parent, String name) {
    JsonNode node = parent.get(name);
    return node == null || node.isNull() ? null : node;
  }

  private static JsonNode requiredObject(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      errors.add(path, ImportMessageKeys.FIELD_REQUIRED);
      return null;
    }
    if (!node.isObject()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_OBJECT));
      return null;
    }
    return node;
  }

  private static JsonNode optionalObject(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      return null;
    }
    if (!node.isObject()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_OBJECT));
      return null;
    }
    return node;
  }

  private static JsonNode requiredArray(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      errors.add(path, ImportMessageKeys.FIELD_REQUIRED);
      return null;
    }
    if (!node.isArray()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_ARRAY));
      return null;
    }
    return node;
  }

  private static JsonNode optionalArray(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      return null;
    }
    if (!node.isArray()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_ARRAY));
      return null;
    }
    return node;
  }

  /** 配列の要素が、オブジェクトであること(でなければ、型の誤りを加え、nullを返す)。 */
  private static JsonNode elementObject(JsonNode node, String path, ImportErrorCollector errors) {
    if (node == null || !node.isObject()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_OBJECT));
      return null;
    }
    return node;
  }

  /** 必須の文字列。欠落・空は必須の誤り、文字列でなければ型の誤り。 */
  private static String requiredString(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      errors.add(path, ImportMessageKeys.FIELD_REQUIRED);
      return null;
    }
    if (!node.isString()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_STRING));
      return null;
    }
    if (node.stringValue().isBlank()) {
      errors.add(path, ImportMessageKeys.FIELD_REQUIRED);
      return null;
    }
    return node.stringValue();
  }

  private static String optionalString(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      return null;
    }
    if (!node.isString()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_STRING));
      return null;
    }
    return node.stringValue();
  }

  /** 欠落は、ここでは誤りにせず(config-engineの検証が、必須として報告する)、文字列でない場合だけ、型の誤りとして報告する。 */
  private static String stringOrNullReportingType(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    return optionalString(parent, name, path, errors);
  }

  private static int requiredInt(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      errors.add(path, ImportMessageKeys.FIELD_REQUIRED);
      return 0;
    }
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_INTEGER));
      return 0;
    }
    return node.intValue();
  }

  private static Boolean optionalBoolean(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = child(parent, name);
    if (node == null) {
      return null;
    }
    if (!node.isBoolean()) {
      errors.add(path, ImportMessageKeys.FIELD_TYPE, Map.of("expected", EXPECTED_BOOLEAN));
      return null;
    }
    return node.booleanValue();
  }

  /** 列挙型の値(名前の完全一致)。欠落は必須の誤り、許容されない値は、許容値の一覧つきの誤り。 */
  private static <E extends Enum<E>> E enumValue(
      JsonNode parent, String name, String path, Class<E> type, ImportErrorCollector errors) {
    String text = requiredString(parent, name, path, errors);
    if (text == null) {
      return null;
    }
    for (E constant : type.getEnumConstants()) {
      if (constant.name().equals(text)) {
        return constant;
      }
    }
    errors.add(
        path,
        ImportMessageKeys.FIELD_VALUE_INVALID,
        Map.of("allowed", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
    return null;
  }

  /** {@code validationRule}(オブジェクト)を、Javaの値(Map・List・文字列・数値・真偽値)に変換する。nullの値を持つキーは、除く。 */
  private static Map<String, Object> optionalRule(
      JsonNode parent, String name, String path, ImportErrorCollector errors) {
    JsonNode node = optionalObject(parent, name, path, errors);
    if (node == null) {
      return null;
    }
    Map<String, Object> rule = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : node.properties()) {
      Object value = toJava(entry.getValue());
      if (value != null) {
        rule.put(entry.getKey(), value);
      }
    }
    return rule;
  }

  static Object toJava(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isString()) {
      return node.stringValue();
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isNumber()) {
      return node.numberValue();
    }
    if (node.isArray()) {
      List<Object> list = new ArrayList<>();
      for (JsonNode element : node.values()) {
        Object value = toJava(element);
        if (value != null) {
          list.add(value);
        }
      }
      return list;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : node.properties()) {
      Object value = toJava(entry.getValue());
      if (value != null) {
        map.put(entry.getKey(), value);
      }
    }
    return map;
  }

  private static String key(String... parts) {
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      builder.append(part == null ? "\u0000" : part).append('\u0001');
    }
    return builder.toString();
  }
}
