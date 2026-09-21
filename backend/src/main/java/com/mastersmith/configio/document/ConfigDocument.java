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

package com.mastersmith.configio.document;

import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import java.util.List;
import java.util.Map;

/**
 * 設定一式のJSONファイル全体を表す値オブジェクト(config-import-export entities.md
 * ConfigDocument。永続化しない)。エクスポートが作り、インポートが読む。
 * 他のユニットが持つエンティティを、環境に依存しない形(自然キー)で写した表現で、<b>内部ID(UUID)は、含めない</b>(BR9.3)。
 *
 * <p>JSONの項目名は、レコードの構成要素の名前そのものである(契約: C7の追補)。取り込み時は、未知のプロパティ(すべての階層)を無視する(BR9.8。パーサーが、必要な項目だけを読む)。
 *
 * @param formatVersion ファイルの形式の版(MVPは1)
 * @param exportedAt 書き出した日時(ISO 8601、UTC)。参考情報で、取り込みの検証には使わない
 * @param appVersion 書き出したアプリケーションのバージョン。参考情報で、取り込みの検証には使わない
 * @param schema スキーマ定義(テーブル・カラムの表示設定)と翻訳
 * @param menu 業務メニューの構成
 * @param rbac RBAC設定(ロール・グループ・権限)
 */
public record ConfigDocument(
    int formatVersion,
    String exportedAt,
    String appVersion,
    SchemaSection schema,
    MenuSection menu,
    RbacSection rbac) {

  /** 対応するファイルの形式の版(BR9.8)。 */
  public static final int SUPPORTED_FORMAT_VERSION = 1;

  public ConfigDocument {
    schema = schema == null ? new SchemaSection(List.of(), List.of()) : schema;
    menu = menu == null ? new MenuSection(List.of()) : menu;
    rbac = rbac == null ? new RbacSection(List.of(), List.of(), List.of(), List.of()) : rbac;
  }

  /** スキーマ定義(テーブル・カラム)と翻訳のセクション。 */
  public record SchemaSection(List<TableEntry> tables, List<TranslationItem> translations) {
    public SchemaSection {
      tables = tables == null ? List.of() : List.copyOf(tables);
      translations = translations == null ? List.of() : List.copyOf(translations);
    }
  }

  /** 1つのテーブルの設定(自然キー=schemaName・tableName)。 */
  public record TableEntry(
      String schemaName,
      String tableName,
      int displayOrder,
      String optimisticLockColumn,
      List<ColumnEntry> columns) {
    public TableEntry {
      columns = columns == null ? List.of() : List.copyOf(columns);
    }
  }

  /** 1つのカラムの設定(自然キー=所属するテーブルの自然キー+columnName)。 */
  public record ColumnEntry(
      String columnName,
      int displayOrder,
      String format,
      String editorType,
      Map<String, Object> validationRule,
      String visibility,
      Boolean isPrimaryKey,
      List<Choice> choiceOptions,
      FkRef fkReference) {
    public ColumnEntry {
      choiceOptions = choiceOptions == null ? null : List.copyOf(choiceOptions);
    }
  }

  /** 静的な選択肢1件。 */
  public record Choice(String value, String i18nKey) {}

  /** FK参照の参照先(自然キー)。 */
  public record FkRef(
      String referencedSchemaName,
      String referencedTableName,
      String referencedValueColumnName,
      String referencedLabelColumnName) {}

  /** 翻訳1件。 */
  public record TranslationItem(String i18nKey, String locale, String text) {}

  /** 業務メニューのセクション(入れ子の木構造)。 */
  public record MenuSection(List<MenuEntry> items) {
    public MenuSection {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  /** 1つのメニュー項目。遷移先のテーブルを持つ項目(リーフ)と、子を持つフォルダ項目がある。 */
  public record MenuEntry(String label, int order, TableRef targetTable, List<MenuEntry> children) {
    public MenuEntry {
      children = children == null ? null : List.copyOf(children);
    }
  }

  /** テーブルを、自然キーで指す(メニューの遷移先で用いる)。 */
  public record TableRef(String schemaName, String tableName) {}

  /** RBAC設定のセクション。ユーザー・ユーザーのグループ所属は、含めない。 */
  public record RbacSection(
      List<RoleEntry> roles,
      List<GroupEntry> groups,
      List<PrimaryPermissionEntry> primaryPermissions,
      List<AuxiliaryPermissionEntry> auxiliaryPermissions) {
    public RbacSection {
      roles = roles == null ? List.of() : List.copyOf(roles);
      groups = groups == null ? List.of() : List.copyOf(groups);
      primaryPermissions = primaryPermissions == null ? List.of() : List.copyOf(primaryPermissions);
      auxiliaryPermissions =
          auxiliaryPermissions == null ? List.of() : List.copyOf(auxiliaryPermissions);
    }
  }

  /** ロール1件(名前が自然キー)。 */
  public record RoleEntry(String name) {}

  /** グループ1件と、対応するロールの名前。 */
  public record GroupEntry(String name, List<String> roleNames) {
    public GroupEntry {
      roleNames = roleNames == null ? List.of() : List.copyOf(roleNames);
    }
  }

  /** 権限の対象(自然キー)。TABLEはschemaName+tableName、COLUMNはschemaName+tableName+columnName。 */
  public record PermissionScope(
      ScopeType scopeType, String schemaName, String tableName, String columnName) {}

  /** 主権限の割当1件。 */
  public record PrimaryPermissionEntry(
      String roleName, PermissionScope scope, PermissionLevel level) {}

  /** 補助権限の割当1件(nullは「指定なし」)。 */
  public record AuxiliaryPermissionEntry(
      String roleName, PermissionScope scope, Boolean createAllowed, Boolean deleteAllowed) {}
}
