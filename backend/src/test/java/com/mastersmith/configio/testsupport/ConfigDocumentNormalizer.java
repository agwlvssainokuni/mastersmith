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
import com.mastersmith.configio.document.ConfigDocument.ColumnEntry;
import com.mastersmith.configio.document.ConfigDocument.GroupEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuEntry;
import com.mastersmith.configio.document.ConfigDocument.MenuSection;
import com.mastersmith.configio.document.ConfigDocument.PermissionScope;
import com.mastersmith.configio.document.ConfigDocument.PrimaryPermissionEntry;
import com.mastersmith.configio.document.ConfigDocument.RbacSection;
import com.mastersmith.configio.document.ConfigDocument.RoleEntry;
import com.mastersmith.configio.document.ConfigDocument.SchemaSection;
import com.mastersmith.configio.document.ConfigDocument.TableEntry;
import com.mastersmith.configio.document.ConfigDocument.TranslationItem;
import java.util.Comparator;
import java.util.List;

/**
 * 設定ファイルの、内容の同値性を比べるための、正規化(順序に依存しない比較のために、リストを並べ替える。取り込みで無視される項目({@code exportedAt}・{@code
 * appVersion}・{@code isPrimaryKey})を、揃える)。 「エクスポート→インポート→エクスポート」で、設定が保たれること(往復)の確認に用いる。
 */
public final class ConfigDocumentNormalizer {

  private ConfigDocumentNormalizer() {}

  public static ConfigDocument normalize(ConfigDocument doc) {
    List<TableEntry> tables =
        doc.schema().tables().stream()
            .map(
                t ->
                    new TableEntry(
                        t.schemaName(),
                        t.tableName(),
                        t.displayOrder(),
                        t.optimisticLockColumn(),
                        t.columns().stream()
                            .map(ConfigDocumentNormalizer::column)
                            .sorted(Comparator.comparing(ColumnEntry::columnName))
                            .toList()))
            .sorted(
                Comparator.comparing(TableEntry::schemaName).thenComparing(TableEntry::tableName))
            .toList();
    List<TranslationItem> translations =
        doc.schema().translations().stream()
            .sorted(
                Comparator.comparing(TranslationItem::i18nKey)
                    .thenComparing(TranslationItem::locale))
            .toList();
    List<RoleEntry> roles =
        doc.rbac().roles().stream().sorted(Comparator.comparing(RoleEntry::name)).toList();
    List<GroupEntry> groups =
        doc.rbac().groups().stream()
            .map(g -> new GroupEntry(g.name(), g.roleNames().stream().sorted().toList()))
            .sorted(Comparator.comparing(GroupEntry::name))
            .toList();
    List<PrimaryPermissionEntry> primary =
        doc.rbac().primaryPermissions().stream()
            .sorted(Comparator.comparing(p -> p.roleName() + "|" + key(p.scope())))
            .toList();
    List<AuxiliaryPermissionEntry> auxiliary =
        doc.rbac().auxiliaryPermissions().stream()
            .sorted(Comparator.comparing(a -> a.roleName() + "|" + key(a.scope())))
            .toList();
    return new ConfigDocument(
        doc.formatVersion(),
        null,
        null,
        new SchemaSection(tables, translations),
        new MenuSection(menu(doc.menu().items())),
        new RbacSection(roles, groups, primary, auxiliary));
  }

  private static ColumnEntry column(ColumnEntry c) {
    return new ColumnEntry(
        c.columnName(),
        c.displayOrder(),
        c.format(),
        c.editorType(),
        c.validationRule() == null ? java.util.Map.of() : c.validationRule(),
        c.visibility(),
        false,
        c.choiceOptions() == null ? List.of() : c.choiceOptions(),
        c.fkReference());
  }

  private static List<MenuEntry> menu(List<MenuEntry> items) {
    return items.stream()
        .map(
            m ->
                new MenuEntry(
                    m.label(),
                    m.order(),
                    m.targetTable(),
                    m.children() == null ? null : menu(m.children())))
        .sorted(Comparator.comparingInt(MenuEntry::order).thenComparing(MenuEntry::label))
        .toList();
  }

  private static String key(PermissionScope scope) {
    return "%s|%s|%s|%s"
        .formatted(scope.scopeType(), scope.schemaName(), scope.tableName(), scope.columnName());
  }
}
