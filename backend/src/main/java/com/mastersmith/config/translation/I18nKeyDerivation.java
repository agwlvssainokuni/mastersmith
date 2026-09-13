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

package com.mastersmith.config.translation;

/**
 * TableConfig/ColumnConfigの表示名・バリデーションメッセージのi18nキーを、schemaName/
 * tableName/columnNameから機械的に導出するユーティリティ(rules.md BR1.5, BR1.6)。
 */
public final class I18nKeyDerivation {

  private I18nKeyDerivation() {}

  /** BR1.5: TableConfigの表示名i18nキー({@code table.{schemaName}.{tableName}.label})。 */
  public static String tableLabelKey(String schemaName, String tableName) {
    return "table.%s.%s.label".formatted(schemaName, tableName);
  }

  /** BR1.5: ColumnConfigの表示名i18nキー ({@code table.{schemaName}.{tableName}.{columnName}.label})。 */
  public static String columnLabelKey(String schemaName, String tableName, String columnName) {
    return "table.%s.%s.%s.label".formatted(schemaName, tableName, columnName);
  }

  /**
   * BR1.6: ColumnConfigのバリデーションエラーメッセージi18nキー ({@code
   * table.{schemaName}.{tableName}.{columnName}.validation.{ruleType}})。
   */
  public static String columnValidationMessageKey(
      String schemaName, String tableName, String columnName, String ruleType) {
    return "table.%s.%s.%s.validation.%s".formatted(schemaName, tableName, columnName, ruleType);
  }
}
