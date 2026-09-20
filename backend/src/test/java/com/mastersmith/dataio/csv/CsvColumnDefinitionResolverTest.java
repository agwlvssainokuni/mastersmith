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

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.dataio.dto.CsvColumnDefinition;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link CsvColumnDefinitionResolver}の単体テスト(rules.md BR8.2: hidden列・permittedColumnNames対象外列の除外)。
 */
class CsvColumnDefinitionResolverTest {

  private final CsvColumnDefinitionResolver resolver = new CsvColumnDefinitionResolver();

  private static ColumnConfig column(String name, Visibility visibility, int displayOrder) {
    ColumnConfig columnConfig = new ColumnConfig("table-1", name, EditorType.TEXT);
    columnConfig.setVisibility(visibility);
    columnConfig.setDisplayOrder(displayOrder);
    return columnConfig;
  }

  static Stream<Arguments> exportExclusionCases() {
    return Stream.of(
        Arguments.of(
            "hidden列を除外する",
            List.of(
                column("visible_col", Visibility.VISIBLE, 0),
                column("hidden_col", Visibility.HIDDEN, 1)),
            List.of("visible_col", "hidden_col"),
            List.of("visible_col")),
        Arguments.of(
            "permittedColumnNamesに含まれない列を除外する",
            List.of(
                column("permitted_col", Visibility.VISIBLE, 0),
                column("not_permitted_col", Visibility.VISIBLE, 1)),
            List.of("permitted_col"),
            List.of("permitted_col")),
        Arguments.of(
            "hidden列かつpermittedColumnNames対象外の列も除外する(両条件を満たす列のみ残る)",
            List.of(
                column("both_ok", Visibility.VISIBLE, 0),
                column("hidden_and_permitted", Visibility.HIDDEN, 1),
                column("visible_and_not_permitted", Visibility.VISIBLE, 2)),
            List.of("both_ok", "hidden_and_permitted"),
            List.of("both_ok")),
        Arguments.of(
            "permittedColumnNamesが空の場合は全列を除外する",
            List.of(column("any_col", Visibility.VISIBLE, 0)),
            List.of(),
            List.of()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("exportExclusionCases")
  void resolveForExportExcludesHiddenAndUnpermittedColumns(
      String description,
      List<ColumnConfig> columnConfigs,
      List<String> permittedColumnNames,
      List<String> expectedColumnNames) {
    List<CsvColumnDefinition> resolved =
        resolver.resolveForExport(columnConfigs, permittedColumnNames);

    assertThat(resolved.stream().map(CsvColumnDefinition::columnName).toList())
        .isEqualTo(expectedColumnNames);
  }

  @Test
  void resolveForExportOrdersByDisplayOrder() {
    List<ColumnConfig> columnConfigs =
        List.of(column("second", Visibility.VISIBLE, 2), column("first", Visibility.VISIBLE, 1));

    List<CsvColumnDefinition> resolved =
        resolver.resolveForExport(columnConfigs, List.of("second", "first"));

    assertThat(resolved.stream().map(CsvColumnDefinition::columnName).toList())
        .containsExactly("first", "second");
  }

  @Test
  void resolveForImportIncludesAllColumnsRegardlessOfVisibilityOrPermission() {
    List<ColumnConfig> columnConfigs =
        List.of(
            column("visible_col", Visibility.VISIBLE, 0),
            column("hidden_col", Visibility.HIDDEN, 1));

    List<CsvColumnDefinition> resolved = resolver.resolveForImport(columnConfigs);

    assertThat(resolved.stream().map(CsvColumnDefinition::columnName).toList())
        .containsExactly("visible_col", "hidden_col");
  }

  @Test
  void resolveForImportPropagatesIsPrimaryKey() {
    ColumnConfig primaryKeyColumn = new ColumnConfig("table-1", "id", EditorType.INTEGER, true);
    List<ColumnConfig> columnConfigs = List.of(primaryKeyColumn);

    List<CsvColumnDefinition> resolved = resolver.resolveForImport(columnConfigs);

    assertThat(resolved).hasSize(1);
    assertThat(resolved.get(0).isPrimaryKey()).isTrue();
  }
}
