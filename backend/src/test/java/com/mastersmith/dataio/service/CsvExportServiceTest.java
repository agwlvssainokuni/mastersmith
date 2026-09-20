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

package com.mastersmith.dataio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.dataio.csv.CsvColumnDefinitionResolver;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.jdbc.DataSourceBuilder;

/**
 * {@link CsvExportService}の単体テスト(rules.md BR8.1形式、対象テーブル不在時の例外伝播、空データ)。
 *
 * <p>{@link ConfigEngineApi}はMockitoでモックし、業務データ用RDBMSは実際のH2インメモリDBを用いる
 * (JDBCカーソル経由の逐次読み取り自体を検証するため)。
 */
@ExtendWith(MockitoExtension.class)
class CsvExportServiceTest {

  @Mock private ConfigEngineApi configEngineApi;

  private DataSource dataSource;
  private CsvExportService service;

  @BeforeEach
  void setUp() throws SQLException {
    dataSource =
        DataSourceBuilder.create()
            .url("jdbc:h2:mem:csv-export-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
            .driverClassName("org.h2.Driver")
            .username("sa")
            .password("")
            .build();
    service = new CsvExportService(configEngineApi, new CsvColumnDefinitionResolver(), dataSource);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE PUBLIC.ITEMS (SKU VARCHAR(50), UNIT_PRICE DECIMAL(10,2))");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP ALL OBJECTS");
    }
  }

  private static ColumnConfig column(String name, EditorType editorType, int displayOrder) {
    ColumnConfig columnConfig = new ColumnConfig("table-1", name, editorType);
    columnConfig.setDisplayOrder(displayOrder);
    return columnConfig;
  }

  @Test
  void exportsCsvWithUtf8BomCommaHeaderAndCrlf() throws SQLException {
    TableConfig tableConfig = new TableConfig("PUBLIC", "ITEMS");
    when(configEngineApi.getTableConfigById("table-1")).thenReturn(tableConfig);
    when(configEngineApi.getColumnConfigs("table-1"))
        .thenReturn(
            List.of(
                column("SKU", EditorType.TEXT, 0), column("UNIT_PRICE", EditorType.DECIMAL, 1)));
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO PUBLIC.ITEMS VALUES ('A-001', 19.99)");
      statement.execute("INSERT INTO PUBLIC.ITEMS VALUES ('A-002', 5.50)");
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.exportCsv("table-1", null, null, List.of("SKU", "UNIT_PRICE"), out);

    String csv = out.toString(StandardCharsets.UTF_8);
    assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
    String withoutBom = csv.substring(1);
    String[] lines = withoutBom.split("\r\n");
    assertThat(lines).hasSize(3);
    assertThat(lines[0]).isEqualTo("SKU,UNIT_PRICE");
    assertThat(lines[1]).isEqualTo("A-001,19.99");
    assertThat(lines[2]).isEqualTo("A-002,5.50");
    assertThat(withoutBom).endsWith("\r\n");
  }

  @Test
  void exportsHeaderOnlyRowWhenNoDataMatches() {
    TableConfig tableConfig = new TableConfig("PUBLIC", "ITEMS");
    when(configEngineApi.getTableConfigById("table-1")).thenReturn(tableConfig);
    when(configEngineApi.getColumnConfigs("table-1"))
        .thenReturn(List.of(column("SKU", EditorType.TEXT, 0)));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.exportCsv("table-1", null, null, List.of("SKU"), out);

    String csv = out.toString(StandardCharsets.UTF_8).substring(1);
    assertThat(csv).isEqualTo("SKU\r\n");
  }

  @Test
  void propagatesTableConfigNotFoundExceptionFromConfigEngine() {
    when(configEngineApi.getTableConfigById("missing"))
        .thenThrow(
            new TableConfigNotFoundException("TableConfig not found: tableConfigId=missing"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertThatThrownBy(() -> service.exportCsv("missing", null, null, List.of(), out))
        .isInstanceOf(TableConfigNotFoundException.class);
  }
}
