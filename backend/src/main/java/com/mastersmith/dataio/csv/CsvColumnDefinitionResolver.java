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

package com.mastersmith.dataio.csv;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.dataio.dto.CsvColumnDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * config-engineの{@code ColumnConfig}一覧から、CSVエクスポート/インポート処理用の{@link
 * CsvColumnDefinition}一覧を組み立てる(rules.md BR8.2)。
 */
@Component
public class CsvColumnDefinitionResolver {

  /**
   * エクスポート対象列を組み立てる(BR8.2)。{@code visibility: hidden}の列、および{@code
   * permittedColumnNames}に含まれない列を除外する。列単位の実効READ権限の判定自体は呼び出し元
   * (list-engine)が実施済みであり、本メソッドはその結果を反映するのみで再判定はしない(BR8.8)。
   *
   * @param columnConfigs 対象テーブルの全ColumnConfig
   * @param permittedColumnNames 呼び出し元が事前に判定済みの実効READ権限列名一覧
   * @return displayOrder昇順に並んだ、出力対象のCsvColumnDefinition一覧
   */
  public List<CsvColumnDefinition> resolveForExport(
      List<ColumnConfig> columnConfigs, List<String> permittedColumnNames) {
    Set<String> permitted = Set.copyOf(permittedColumnNames == null ? List.of() : permittedColumnNames);
    return columnConfigs.stream()
        .sorted(Comparator.comparingInt(ColumnConfig::getDisplayOrder))
        .filter(column -> column.getVisibility() != Visibility.HIDDEN)
        .filter(column -> permitted.contains(column.getColumnName()))
        .map(CsvColumnDefinitionResolver::toDefinition)
        .toList();
  }

  /**
   * インポート対象列を組み立てる。インポートの実効権限(CREATE/FULL)は呼び出し元(record-edit-engine)が
   * 事前に検証済みであり(BR8.8)、列単位のvisibility/permittedColumnNamesによる絞り込みはBR8.2の対象外
   * (エクスポートのみに適用されるルール)であるため、対象テーブルの全ColumnConfigをそのまま用いる。
   *
   * @param columnConfigs 対象テーブルの全ColumnConfig
   * @return displayOrder昇順に並んだ、インポート対象のCsvColumnDefinition一覧
   */
  public List<CsvColumnDefinition> resolveForImport(List<ColumnConfig> columnConfigs) {
    return columnConfigs.stream()
        .sorted(Comparator.comparingInt(ColumnConfig::getDisplayOrder))
        .map(CsvColumnDefinitionResolver::toDefinition)
        .toList();
  }

  private static CsvColumnDefinition toDefinition(ColumnConfig column) {
    return new CsvColumnDefinition(
        column.getColumnName(),
        column.getEditorType(),
        column.getValidationRule(),
        column.getVisibility(),
        column.isPrimaryKey());
  }
}
