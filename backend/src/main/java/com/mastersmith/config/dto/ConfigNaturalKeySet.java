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

package com.mastersmith.config.dto;

import java.util.List;
import java.util.Map;

/**
 * config-import-exportが、config-engineに渡す、スキーマ定義・翻訳の取り込みの入力(C9の{@code validateConfigSet}・{@code
 * applyConfigSet}の引数型、functional-spec.md
 * 追補一覧2番)。内部ID(tableConfigId・columnConfigId)に依存せず、自然キー(テーブル=(schemaName,
 * tableName)、カラム=(schemaName, tableName, columnName)、翻訳=(i18nKey,
 * locale))で表す。既存の項目との照合は、config-engineが、この自然キーで行う(BR9.9)。
 *
 * <p>値の意味の検証(editorTypeの許容値・choiceOptionsとfkReferenceの排他・必須の項目など)は、config-engineが行う(BR9.20)ため、editorType・visibilityは、文字列のまま受け取る。リストの添え字は、呼び出し元の
 * ファイルの中の順序に対応し、検証の誤りの位置({@code tables[3].columns[2].editorType})に用いる。
 *
 * @param tables テーブルの設定
 * @param translations 翻訳
 */
public record ConfigNaturalKeySet(List<Table> tables, List<Translation> translations) {

  public ConfigNaturalKeySet {
    tables = tables == null ? List.of() : List.copyOf(tables);
    translations = translations == null ? List.of() : List.copyOf(translations);
  }

  /** 1つのテーブルの設定(自然キー=schemaName・tableName)。 */
  public record Table(
      String schemaName,
      String tableName,
      Integer displayOrder,
      String optimisticLockColumn,
      List<Column> columns) {
    public Table {
      columns = columns == null ? List.of() : List.copyOf(columns);
    }
  }

  /** 1つのカラムの設定(自然キー=所属するテーブルの自然キー+columnName)。{@code isPrimaryKey}は、取り込まない(BR1.14)ため、持たない。 */
  public record Column(
      String columnName,
      Integer displayOrder,
      String format,
      String editorType,
      Map<String, Object> validationRule,
      String visibility,
      List<Choice> choiceOptions,
      Fk fkReference) {
    public Column {
      validationRule = validationRule == null ? Map.of() : Map.copyOf(validationRule);
      choiceOptions = choiceOptions == null ? List.of() : List.copyOf(choiceOptions);
    }
  }

  /** 静的な選択肢1件。 */
  public record Choice(String value, String i18nKey) {}

  /** FK参照の参照先(自然キー)。 */
  public record Fk(
      String referencedSchemaName,
      String referencedTableName,
      String referencedValueColumnName,
      String referencedLabelColumnName) {}

  /** 翻訳1件。 */
  public record Translation(String i18nKey, String locale, String text) {}
}
