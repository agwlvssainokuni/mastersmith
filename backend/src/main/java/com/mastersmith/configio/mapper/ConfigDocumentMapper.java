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

import com.mastersmith.config.dto.ColumnConfigSnapshot;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.ConfigNaturalKeySet;
import com.mastersmith.config.dto.TableConfigSnapshot;
import com.mastersmith.config.dto.TranslationSnapshot;
import com.mastersmith.config.model.FkReference;
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
import com.mastersmith.menu.dto.MenuImportItem;
import com.mastersmith.menu.dto.MenuStructureEntry;
import com.mastersmith.permission.dto.RbacExport;
import com.mastersmith.permission.dto.RbacImportSet;
import com.mastersmith.permission.entity.ScopeType;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 内部の表現(3ユニットの、内部IDを持つエクスポート用の型)と、設定ファイルの値オブジェクト({@link
 * ConfigDocument}。自然キー)を、双方向に変換する(config-import-export BR9.3・BR9.14)。
 * 内部IDと自然キーの対応は、メモリ上のマップで解決する(N+1を避ける。NFR1.1)。業務固有の名前を持たず、名前・値の意味は解釈しない(BR9.20)。
 *
 * <ul>
 *   <li><b>書き出し</b>({@link #toDocument}):
 *       内部IDをすべて自然キーに変換し、決まった順序(表示順・名前)に並べる(同じ設定は、同じ内容のファイルになる)。メニューは、親子関係から入れ子の木構造にし、
 *       遷移先のテーブルを自然キーに変換する。解決できない参照(削除済みのテーブルへの遷移先・権限など)は、ファイルで表せないため、書き出さない。
 *   <li><b>取り込み</b>({@link #toNaturalKeySet}・{@link #toMenuItems}・{@link #toRbacImportSet}):
 *       ファイルの内容を、各ユニットの検証・反映の入力の型に変換する。現在の環境の内部ID ({@link
 *       NaturalKeyIndex})が引けない項目(新規のテーブル・カラム)は、内部IDをnull(仮の識別)にする。
 * </ul>
 */
@Component
public class ConfigDocumentMapper {

  // ---- 書き出し ----

  public ConfigDocument toDocument(
      ConfigExportSet config,
      List<MenuStructureEntry> menu,
      RbacExport rbac,
      Instant exportedAt,
      String appVersion) {
    Map<String, TableConfigSnapshot> tableById = new HashMap<>();
    config.tableConfigs().forEach(t -> tableById.put(t.tableConfigId(), t));
    Map<String, ColumnConfigSnapshot> columnById = new HashMap<>();
    config.columnConfigs().forEach(c -> columnById.put(c.columnConfigId(), c));
    return new ConfigDocument(
        ConfigDocument.SUPPORTED_FORMAT_VERSION,
        DateTimeFormatter.ISO_INSTANT.format(exportedAt),
        appVersion,
        toSchemaSection(config, tableById),
        new MenuSection(toMenuTree(menu, tableById)),
        toRbacSection(rbac, tableById, columnById));
  }

  private SchemaSection toSchemaSection(
      ConfigExportSet config, Map<String, TableConfigSnapshot> tableById) {
    Map<String, List<ColumnConfigSnapshot>> columnsByTable = new HashMap<>();
    for (ColumnConfigSnapshot column : config.columnConfigs()) {
      columnsByTable.computeIfAbsent(column.tableConfigId(), k -> new ArrayList<>()).add(column);
    }
    List<TableEntry> tables =
        config.tableConfigs().stream()
            .sorted(
                Comparator.comparingInt(TableConfigSnapshot::displayOrder)
                    .thenComparing(TableConfigSnapshot::schemaName)
                    .thenComparing(TableConfigSnapshot::tableName))
            .map(
                table ->
                    new TableEntry(
                        table.schemaName(),
                        table.tableName(),
                        table.displayOrder(),
                        table.optimisticLockColumn(),
                        columnsByTable.getOrDefault(table.tableConfigId(), List.of()).stream()
                            .sorted(
                                Comparator.comparingInt(ColumnConfigSnapshot::displayOrder)
                                    .thenComparing(ColumnConfigSnapshot::columnName))
                            .map(ConfigDocumentMapper::toColumnEntry)
                            .toList()))
            .toList();
    List<TranslationItem> translations =
        config.translationEntries().stream()
            .sorted(
                Comparator.comparing(TranslationSnapshot::i18nKey)
                    .thenComparing(TranslationSnapshot::locale))
            .map(t -> new TranslationItem(t.i18nKey(), t.locale(), t.text()))
            .toList();
    return new SchemaSection(tables, translations);
  }

  private static ColumnEntry toColumnEntry(ColumnConfigSnapshot column) {
    FkReference fk = column.fkReference();
    return new ColumnEntry(
        column.columnName(),
        column.displayOrder(),
        column.format(),
        column.editorType() == null ? null : column.editorType().toWireValue(),
        column.validationRule().rules(),
        column.visibility() == null
            ? null
            : column.visibility().name().toLowerCase(java.util.Locale.ROOT),
        column.primaryKey(),
        column.choiceOptions().stream().map(c -> new Choice(c.value(), c.i18nKey())).toList(),
        fk == null
            ? null
            : new FkRef(
                fk.referencedSchemaName(),
                fk.referencedTableName(),
                fk.referencedValueColumnName(),
                fk.referencedLabelColumnName()));
  }

  private List<MenuEntry> toMenuTree(
      List<MenuStructureEntry> flat, Map<String, TableConfigSnapshot> tableById) {
    Set<String> ids = new HashSet<>();
    flat.forEach(e -> ids.add(e.menuItemId()));
    Map<String, List<MenuStructureEntry>> childrenByParent = new HashMap<>();
    List<MenuStructureEntry> roots = new ArrayList<>();
    for (MenuStructureEntry entry : flat) {
      String parent = entry.parentMenuItemId();
      if (parent == null || !ids.contains(parent)) {
        roots.add(entry);
      } else {
        childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(entry);
      }
    }
    return buildLevel(roots, childrenByParent, tableById, new HashSet<>());
  }

  private List<MenuEntry> buildLevel(
      List<MenuStructureEntry> level,
      Map<String, List<MenuStructureEntry>> childrenByParent,
      Map<String, TableConfigSnapshot> tableById,
      Set<String> visited) {
    List<MenuEntry> entries = new ArrayList<>();
    List<MenuStructureEntry> sorted =
        level.stream()
            .sorted(
                Comparator.comparingInt(MenuStructureEntry::order)
                    .thenComparing(MenuStructureEntry::label))
            .toList();
    for (MenuStructureEntry item : sorted) {
      if (!visited.add(item.menuItemId())) {
        continue; // 循環の防止(不整合なデータでも、書き出しが終わる)。
      }
      if (item.targetTableConfigId() != null) {
        TableConfigSnapshot table = tableById.get(item.targetTableConfigId());
        if (table == null) {
          continue; // 削除済みのテーブルへの遷移先は、ファイルで表せない。
        }
        entries.add(
            new MenuEntry(
                item.label(),
                item.order(),
                new TableRef(table.schemaName(), table.tableName()),
                null));
      } else {
        entries.add(
            new MenuEntry(
                item.label(),
                item.order(),
                null,
                buildLevel(
                    childrenByParent.getOrDefault(item.menuItemId(), List.of()),
                    childrenByParent,
                    tableById,
                    visited)));
      }
    }
    return entries;
  }

  private RbacSection toRbacSection(
      RbacExport rbac,
      Map<String, TableConfigSnapshot> tableById,
      Map<String, ColumnConfigSnapshot> columnById) {
    Map<String, String> roleNameById = new HashMap<>();
    rbac.roles().forEach(r -> roleNameById.put(r.roleId(), r.name()));
    List<RoleEntry> roles =
        rbac.roles().stream().map(RbacExport.Role::name).sorted().map(RoleEntry::new).toList();
    List<GroupEntry> groups =
        rbac.groups().stream()
            .sorted(Comparator.comparing(RbacExport.Group::name))
            .map(
                g ->
                    new GroupEntry(
                        g.name(),
                        g.roleIds().stream()
                            .map(roleNameById::get)
                            .filter(java.util.Objects::nonNull)
                            .sorted()
                            .toList()))
            .toList();
    List<PrimaryPermissionEntry> primary = new ArrayList<>();
    for (RbacExport.Primary p : rbac.primaryPermissions()) {
      String role = roleNameById.get(p.roleId());
      PermissionScope scope = toScope(p.scopeType(), p.scopeRef(), tableById, columnById);
      if (role != null && scope != null) {
        primary.add(new PrimaryPermissionEntry(role, scope, p.level()));
      }
    }
    List<AuxiliaryPermissionEntry> auxiliary = new ArrayList<>();
    for (RbacExport.Auxiliary a : rbac.auxiliaryPermissions()) {
      String role = roleNameById.get(a.roleId());
      PermissionScope scope = toScope(a.scopeType(), a.scopeRef(), tableById, columnById);
      if (role != null && scope != null) {
        auxiliary.add(
            new AuxiliaryPermissionEntry(role, scope, a.createAllowed(), a.deleteAllowed()));
      }
    }
    primary.sort(
        Comparator.comparing(PrimaryPermissionEntry::roleName)
            .thenComparing(p -> scopeSortKey(p.scope())));
    auxiliary.sort(
        Comparator.comparing(AuxiliaryPermissionEntry::roleName)
            .thenComparing(a -> scopeSortKey(a.scope())));
    return new RbacSection(roles, groups, primary, auxiliary);
  }

  private static String scopeSortKey(PermissionScope scope) {
    return "%d|%s|%s|%s"
        .formatted(
            scope.scopeType().ordinal(),
            scope.schemaName(),
            scope.tableName() == null ? "" : scope.tableName(),
            scope.columnName() == null ? "" : scope.columnName());
  }

  /** 対象の識別子(不透明な文字列)を、自然キーに変換する。TABLE・COLUMNで、解決できないものは、nullを返す(書き出さない)。 */
  private static PermissionScope toScope(
      ScopeType type,
      String ref,
      Map<String, TableConfigSnapshot> tableById,
      Map<String, ColumnConfigSnapshot> columnById) {
    switch (type) {
      case SCHEMA:
        return new PermissionScope(ScopeType.SCHEMA, ref, null, null);
      case TABLE:
        TableConfigSnapshot table = tableById.get(ref);
        return table == null
            ? null
            : new PermissionScope(ScopeType.TABLE, table.schemaName(), table.tableName(), null);
      case COLUMN:
        ColumnConfigSnapshot column = columnById.get(ref);
        TableConfigSnapshot owner = column == null ? null : tableById.get(column.tableConfigId());
        return owner == null
            ? null
            : new PermissionScope(
                ScopeType.COLUMN, owner.schemaName(), owner.tableName(), column.columnName());
      default:
        return null;
    }
  }

  // ---- 取り込み ----

  /** ファイルのスキーマ・翻訳を、config-engineの入力(自然キー)にする。リストの順序(添え字)は、ファイルのまま。 */
  public ConfigNaturalKeySet toNaturalKeySet(ConfigDocument document) {
    List<ConfigNaturalKeySet.Table> tables =
        document.schema().tables().stream()
            .map(
                t ->
                    new ConfigNaturalKeySet.Table(
                        t.schemaName(),
                        t.tableName(),
                        t.displayOrder(),
                        t.optimisticLockColumn(),
                        t.columns().stream().map(ConfigDocumentMapper::toNaturalColumn).toList()))
            .toList();
    List<ConfigNaturalKeySet.Translation> translations =
        document.schema().translations().stream()
            .map(t -> new ConfigNaturalKeySet.Translation(t.i18nKey(), t.locale(), t.text()))
            .toList();
    return new ConfigNaturalKeySet(tables, translations);
  }

  private static ConfigNaturalKeySet.Column toNaturalColumn(ColumnEntry c) {
    return new ConfigNaturalKeySet.Column(
        c.columnName(),
        c.displayOrder(),
        c.format(),
        c.editorType(),
        c.validationRule(),
        c.visibility(),
        c.choiceOptions() == null
            ? List.of()
            : c.choiceOptions().stream()
                .map(o -> new ConfigNaturalKeySet.Choice(o.value(), o.i18nKey()))
                .toList(),
        c.fkReference() == null
            ? null
            : new ConfigNaturalKeySet.Fk(
                c.fkReference().referencedSchemaName(),
                c.fkReference().referencedTableName(),
                c.fkReference().referencedValueColumnName(),
                c.fkReference().referencedLabelColumnName()));
  }

  /**
   * ファイルのメニューを、menu-navigationの入力(フラットな一覧。親を、位置の文字列で結ぶ)にする。位置は、ファイルの中のJSON上の位置({@code
   * menu.items[0].children[2]})で、検証の誤りの位置に、 そのまま用いる。遷移先のテーブルのIDは、{@code index}から引く(新規のテーブルは、null)。
   */
  public List<MenuImportItem> toMenuItems(ConfigDocument document, NaturalKeyIndex index) {
    List<MenuImportItem> flat = new ArrayList<>();
    flatten(document.menu().items(), "menu.items", null, index, flat);
    return flat;
  }

  private void flatten(
      List<MenuEntry> entries,
      String basePath,
      String parentPosition,
      NaturalKeyIndex index,
      List<MenuImportItem> out) {
    for (int i = 0; i < entries.size(); i++) {
      MenuEntry entry = entries.get(i);
      String position = basePath + "[" + i + "]";
      TableRef target = entry.targetTable();
      out.add(
          new MenuImportItem(
              position,
              parentPosition,
              entry.label(),
              entry.order(),
              target != null,
              target == null ? null : index.tableId(target.schemaName(), target.tableName())));
      if (entry.children() != null) {
        flatten(entry.children(), position + ".children", position, index, out);
      }
    }
  }

  /** ファイルのRBAC設定を、permission-engineの入力にする。権限の対象は、{@code index}から、内部IDを引く(新規のテーブル・カラムは、null)。 */
  public RbacImportSet toRbacImportSet(ConfigDocument document, NaturalKeyIndex index) {
    RbacSection rbac = document.rbac();
    return new RbacImportSet(
        rbac.roles().stream().map(r -> new RbacImportSet.Role(r.name())).toList(),
        rbac.groups().stream().map(g -> new RbacImportSet.Group(g.name(), g.roleNames())).toList(),
        rbac.primaryPermissions().stream()
            .map(
                p ->
                    new RbacImportSet.Primary(
                        p.roleName(), toImportScope(p.scope(), index), p.level()))
            .toList(),
        rbac.auxiliaryPermissions().stream()
            .map(
                a ->
                    new RbacImportSet.Auxiliary(
                        a.roleName(),
                        toImportScope(a.scope(), index),
                        a.createAllowed(),
                        a.deleteAllowed()))
            .toList());
  }

  private static RbacImportSet.Scope toImportScope(PermissionScope scope, NaturalKeyIndex index) {
    String tableId =
        scope.scopeType() == ScopeType.SCHEMA
            ? null
            : index.tableId(scope.schemaName(), scope.tableName());
    String columnId =
        scope.scopeType() == ScopeType.COLUMN
            ? index.columnId(scope.schemaName(), scope.tableName(), scope.columnName())
            : null;
    return new RbacImportSet.Scope(scope.scopeType(), scope.schemaName(), tableId, columnId);
  }
}
