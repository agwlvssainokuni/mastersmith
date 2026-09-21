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

package com.mastersmith.configio.validation;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.document.ConfigDocument.AuxiliaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.ColumnEntry;
import com.mastersmith.configio.document.ConfigDocument.FkRef;
import com.mastersmith.configio.document.ConfigDocument.GroupEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuEntry;
import com.mastersmith.configio.document.ConfigDocument.PermissionScope;
import com.mastersmith.configio.document.ConfigDocument.PrimaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.TableEntry;
import com.mastersmith.configio.parser.ImportErrorCollector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * セクションをまたぐ参照が、ファイルの中で解決できるかを検証する(config-import-export BR9.13)。参照先が見つからない場合は、その参照を持つ要素の位置を{@code
 * field}として、 {@code config.import.reference.notFound}の誤りに加える。
 *
 * <ol>
 *   <li>メニューの{@code targetTable}: schemaセクションのテーブル。
 *   <li>権限のTABLE・COLUMNの対象: schemaセクションのテーブル・カラム。SCHEMAの対象:
 *       schemaセクションのスキーマ名、または、permission-engineが管理する予約スキーマ名(本ユニットは、 解釈せず、{@code
 *       reservedSchemaName}の問い合わせに委ねる。BR9.20)。
 *   <li>権限の{@code roleName}・グループの{@code roleNames}: rbacセクションのロール。
 *   <li>{@code fkReference}: 参照先のスキーマ・テーブル・値の列・名称の列が、schemaセクションに存在すること。
 *   <li>{@code optimisticLockColumn}: そのテーブルのcolumnsに含まれること。
 * </ol>
 *
 * <p>業務固有の名前を、コードに持たない。名前は、データとして、機械的に照合するだけである。
 */
@Component
public class ReferenceValidator {

  /** ファイルの、テーブル(自然キー)→そのカラム名の集合。 */
  private record Catalog(
      Map<String, Map<String, Set<String>>> columnsByTable, Set<String> schemas) {

    boolean hasTable(String schemaName, String tableName) {
      Map<String, Set<String>> tables = columnsByTable.get(schemaName);
      return tables != null && tables.containsKey(tableName);
    }

    boolean hasColumn(String schemaName, String tableName, String columnName) {
      Map<String, Set<String>> tables = columnsByTable.get(schemaName);
      Set<String> columns = tables == null ? null : tables.get(tableName);
      return columns != null && columns.contains(columnName);
    }
  }

  /**
   * @param document 構造の検証を終えた設定ファイル
   * @param reservedSchemaName permission-engineが管理する予約スキーマ名か(SCHEMAの権限の対象で、ファイルのスキーマにない名前の判定)
   * @param errors 誤りを集める先
   */
  public void validate(
      ConfigDocument document, Predicate<String> reservedSchemaName, ImportErrorCollector errors) {
    Catalog catalog = catalog(document);
    validateOptimisticLockAndFk(document, catalog, errors);
    validateMenu(document.menu().items(), "menu.items", catalog, errors);
    validateRbac(document, catalog, reservedSchemaName, errors);
  }

  private static Catalog catalog(ConfigDocument document) {
    Map<String, Map<String, Set<String>>> columnsByTable = new HashMap<>();
    Set<String> schemas = new HashSet<>();
    for (TableEntry table : document.schema().tables()) {
      schemas.add(table.schemaName());
      Set<String> columns = new HashSet<>();
      table.columns().forEach(c -> columns.add(c.columnName()));
      columnsByTable
          .computeIfAbsent(table.schemaName(), k -> new HashMap<>())
          .put(table.tableName(), columns);
    }
    return new Catalog(columnsByTable, schemas);
  }

  private static void validateOptimisticLockAndFk(
      ConfigDocument document, Catalog catalog, ImportErrorCollector errors) {
    List<TableEntry> tables = document.schema().tables();
    for (int i = 0; i < tables.size(); i++) {
      TableEntry table = tables.get(i);
      String path = "schema.tables[" + i + "]";
      String lockColumn = table.optimisticLockColumn();
      if (lockColumn != null
          && !catalog.hasColumn(table.schemaName(), table.tableName(), lockColumn)) {
        errors.add(path + ".optimisticLockColumn", ImportMessageKeys.REFERENCE_NOT_FOUND);
      }
      for (int j = 0; j < table.columns().size(); j++) {
        ColumnEntry column = table.columns().get(j);
        FkRef fk = column.fkReference();
        if (fk == null) {
          continue;
        }
        String fkPath = path + ".columns[" + j + "].fkReference";
        if (fk.referencedSchemaName() == null || fk.referencedTableName() == null) {
          continue; // 必須の誤りは、構造の検証が報告済み。
        }
        if (!catalog.hasTable(fk.referencedSchemaName(), fk.referencedTableName())) {
          errors.add(fkPath + ".referencedTableName", ImportMessageKeys.REFERENCE_NOT_FOUND);
          continue;
        }
        if (fk.referencedValueColumnName() != null
            && !catalog.hasColumn(
                fk.referencedSchemaName(),
                fk.referencedTableName(),
                fk.referencedValueColumnName())) {
          errors.add(fkPath + ".referencedValueColumnName", ImportMessageKeys.REFERENCE_NOT_FOUND);
        }
        if (fk.referencedLabelColumnName() != null
            && !catalog.hasColumn(
                fk.referencedSchemaName(),
                fk.referencedTableName(),
                fk.referencedLabelColumnName())) {
          errors.add(fkPath + ".referencedLabelColumnName", ImportMessageKeys.REFERENCE_NOT_FOUND);
        }
      }
    }
  }

  private static void validateMenu(
      List<MenuEntry> entries, String basePath, Catalog catalog, ImportErrorCollector errors) {
    for (int i = 0; i < entries.size(); i++) {
      MenuEntry entry = entries.get(i);
      String path = basePath + "[" + i + "]";
      if (entry.targetTable() != null
          && entry.targetTable().schemaName() != null
          && entry.targetTable().tableName() != null
          && !catalog.hasTable(entry.targetTable().schemaName(), entry.targetTable().tableName())) {
        errors.add(path + ".targetTable", ImportMessageKeys.REFERENCE_NOT_FOUND);
      }
      if (entry.children() != null) {
        validateMenu(entry.children(), path + ".children", catalog, errors);
      }
    }
  }

  private static void validateRbac(
      ConfigDocument document,
      Catalog catalog,
      Predicate<String> reservedSchemaName,
      ImportErrorCollector errors) {
    Set<String> roleNames = new HashSet<>();
    document.rbac().roles().forEach(r -> roleNames.add(r.name()));
    List<GroupEntry> groups = document.rbac().groups();
    for (int i = 0; i < groups.size(); i++) {
      List<String> names = groups.get(i).roleNames();
      for (int j = 0; j < names.size(); j++) {
        if (!roleNames.contains(names.get(j))) {
          errors.add(
              "rbac.groups[" + i + "].roleNames[" + j + "]", ImportMessageKeys.REFERENCE_NOT_FOUND);
        }
      }
    }
    List<PrimaryPermissionEntry> primary = document.rbac().primaryPermissions();
    for (int i = 0; i < primary.size(); i++) {
      String path = "rbac.primaryPermissions[" + i + "]";
      validatePermission(
          primary.get(i).roleName(),
          primary.get(i).scope(),
          path,
          roleNames,
          catalog,
          reservedSchemaName,
          errors);
    }
    List<AuxiliaryPermissionEntry> auxiliary = document.rbac().auxiliaryPermissions();
    for (int i = 0; i < auxiliary.size(); i++) {
      String path = "rbac.auxiliaryPermissions[" + i + "]";
      validatePermission(
          auxiliary.get(i).roleName(),
          auxiliary.get(i).scope(),
          path,
          roleNames,
          catalog,
          reservedSchemaName,
          errors);
    }
  }

  private static void validatePermission(
      String roleName,
      PermissionScope scope,
      String path,
      Set<String> roleNames,
      Catalog catalog,
      Predicate<String> reservedSchemaName,
      ImportErrorCollector errors) {
    if (!roleNames.contains(roleName)) {
      errors.add(path + ".roleName", ImportMessageKeys.REFERENCE_NOT_FOUND);
    }
    boolean resolved =
        switch (scope.scopeType()) {
          case SCHEMA ->
              catalog.schemas().contains(scope.schemaName())
                  || reservedSchemaName.test(scope.schemaName());
          case TABLE -> catalog.hasTable(scope.schemaName(), scope.tableName());
          case COLUMN ->
              catalog.hasColumn(scope.schemaName(), scope.tableName(), scope.columnName());
        };
    if (!resolved) {
      errors.add(path + ".scope", ImportMessageKeys.REFERENCE_NOT_FOUND);
    }
  }
}
