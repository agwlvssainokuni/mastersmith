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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.dataio.csv.CsvColumnDefinitionResolver;
import com.mastersmith.dataio.exception.CsvExportException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.jdbc.DataSourceBuilder;

/**
 * {@link CsvExportService}の単体テスト(rules.md BR8.1形式・BR8.2列絞り込み・BR8.10ストリーミング、対象テーブル不在時の例外伝播、
 * 空データ、値の正規形式、filter/sort、出力ストリームの非クローズ、識別子の引用符、コネクション設定)。
 *
 * <p>{@link ConfigEngineApi}はMockitoでモックし、業務データ用RDBMSは実際のH2インメモリDBを用いる
 * (JDBCカーソル経由の逐次読み取り自体を検証するため)。PostgreSQL/MySQL/MariaDB向けのストリーミング設定は、
 * JDBCオブジェクトのモックで発行される呼び出し(読み取り専用・自動コミット無効・fetchSize)を検証する。
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

    execute("CREATE TABLE PUBLIC.ITEMS (SKU VARCHAR(50), UNIT_PRICE DECIMAL(10,2))");
  }

  @AfterEach
  void tearDown() throws SQLException {
    execute("DROP ALL OBJECTS");
  }

  private void execute(String sql) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static ColumnConfig column(String name, EditorType editorType, int displayOrder) {
    ColumnConfig columnConfig = new ColumnConfig("table-1", name, editorType);
    columnConfig.setDisplayOrder(displayOrder);
    return columnConfig;
  }

  private void givenTable(String schema, String table, ColumnConfig... columns) {
    when(configEngineApi.getTableConfigById("table-1")).thenReturn(new TableConfig(schema, table));
    when(configEngineApi.getColumnConfigs("table-1")).thenReturn(List.of(columns));
  }

  private static String export(
      CsvExportService service, Map<String, Object> filter, String sort, List<String> permitted) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.exportCsv("table-1", filter, sort, permitted, out);
    return out.toString(StandardCharsets.UTF_8);
  }

  @Test
  void exportsCsvWithUtf8BomCommaHeaderAndCrlf() throws SQLException {
    givenTable(
        "PUBLIC",
        "ITEMS",
        column("SKU", EditorType.TEXT, 0),
        column("UNIT_PRICE", EditorType.DECIMAL, 1));
    execute("INSERT INTO PUBLIC.ITEMS VALUES ('A-001', 19.99)");
    execute("INSERT INTO PUBLIC.ITEMS VALUES ('A-002', 5.50)");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    service.exportCsv("table-1", null, null, List.of("SKU", "UNIT_PRICE"), out);

    String csv = out.toString(StandardCharsets.UTF_8);
    assertThat(csv.charAt(0)).isEqualTo('﻿');
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
    givenTable("PUBLIC", "ITEMS", column("SKU", EditorType.TEXT, 0));

    String csv = export(service, null, null, List.of("SKU")).substring(1);

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

  // ---- BR8.2: hidden列・権限のない列は出力しない(実データでの確認) ----

  @Test
  void omitsHiddenColumnsAndColumnsNotPermitted() throws SQLException {
    execute(
        "CREATE TABLE PUBLIC.PEOPLE (ID INT, NAME VARCHAR(20), SECRET VARCHAR(20), SALARY INT)");
    execute("INSERT INTO PUBLIC.PEOPLE VALUES (1, 'alice', 'hidden-value', 300)");
    ColumnConfig secret = column("SECRET", EditorType.TEXT, 2);
    secret.setVisibility(Visibility.HIDDEN);
    givenTable(
        "PUBLIC",
        "PEOPLE",
        column("ID", EditorType.INTEGER, 0),
        column("NAME", EditorType.TEXT, 1),
        secret,
        column("SALARY", EditorType.INTEGER, 3));

    // SECRETはhidden、SALARYはpermittedColumnNamesに含まれない。
    String csv = export(service, null, null, List.of("ID", "NAME", "SECRET")).substring(1);

    assertThat(csv).isEqualTo("ID,NAME\r\n1,alice\r\n");
    assertThat(csv).doesNotContain("hidden-value").doesNotContain("300");
  }

  // ---- filter / sort ----

  @Test
  void appliesEqualityFilterAndSortAndIgnoresUnknownColumns() throws SQLException {
    givenTable(
        "PUBLIC",
        "ITEMS",
        column("SKU", EditorType.TEXT, 0),
        column("UNIT_PRICE", EditorType.DECIMAL, 1));
    execute("INSERT INTO PUBLIC.ITEMS VALUES ('B', 2.00)");
    execute("INSERT INTO PUBLIC.ITEMS VALUES ('A', 2.00)");
    execute("INSERT INTO PUBLIC.ITEMS VALUES ('C', 3.00)");

    // 未知の列名のfilter・sortは無視される(SQLへ連結されない)。
    Map<String, Object> filter =
        Map.of(
            "UNIT_PRICE", new java.math.BigDecimal("2.00"), "NOT_A_COLUMN'; DROP TABLE X--", "x");
    String csv = export(service, filter, "SKU,desc", List.of("SKU", "UNIT_PRICE")).substring(1);

    assertThat(csv).isEqualTo("SKU,UNIT_PRICE\r\nB,2.00\r\nA,2.00\r\n");

    String ignoredSort = export(service, null, "NOT_A_COLUMN", List.of("SKU")).substring(1);
    assertThat(ignoredSort.split("\r\n")).hasSize(4);
  }

  // ---- R-02: 値の正規形式(インポートが受け付ける形式) ----

  @Test
  void exportsDateDatetimeBooleanAndNullInImportableCanonicalForm() throws SQLException {
    execute(
        "CREATE TABLE PUBLIC.EVENTS (ID INT, HELD_ON DATE, UPDATED_AT TIMESTAMP, ACTIVE BOOLEAN,"
            + " NOTE VARCHAR(20))");
    execute(
        "INSERT INTO PUBLIC.EVENTS VALUES (1, DATE '2026-09-14', TIMESTAMP '2026-01-01 10:00:00', TRUE, NULL)");
    givenTable(
        "PUBLIC",
        "EVENTS",
        column("ID", EditorType.INTEGER, 0),
        column("HELD_ON", EditorType.DATE, 1),
        column("UPDATED_AT", EditorType.DATETIME, 2),
        column("ACTIVE", EditorType.SWITCH, 3),
        column("NOTE", EditorType.TEXT, 4));

    String csv =
        export(service, null, null, List.of("ID", "HELD_ON", "UPDATED_AT", "ACTIVE", "NOTE"))
            .substring(1);

    assertThat(csv)
        .isEqualTo(
            "ID,HELD_ON,UPDATED_AT,ACTIVE,NOTE\r\n1,2026-09-14,2026-01-01T10:00:00,true,\r\n");
  }

  // ---- R-07: 識別子の引用符(予約語の列名・テーブル名) ----

  @Test
  void quotesIdentifiersSoReservedWordsWork() throws SQLException {
    execute("CREATE TABLE PUBLIC.\"ORDER\" (\"GROUP\" VARCHAR(10), \"SELECT\" INT)");
    execute("INSERT INTO PUBLIC.\"ORDER\" VALUES ('g1', 7)");
    givenTable(
        "PUBLIC",
        "ORDER",
        column("GROUP", EditorType.TEXT, 0),
        column("SELECT", EditorType.INTEGER, 1));

    String csv =
        export(service, Map.of("GROUP", "g1"), "SELECT,desc", List.of("GROUP", "SELECT"))
            .substring(1);

    assertThat(csv).isEqualTo("GROUP,SELECT\r\ng1,7\r\n");
  }

  // ---- R-05: 出力ストリームはクローズしない(DataImportExportApi.exportCsvの契約) ----

  @Test
  void doesNotCloseTheCallersOutputStreamButFlushesIt() {
    givenTable("PUBLIC", "ITEMS", column("SKU", EditorType.TEXT, 0));
    CloseTrackingOutputStream out = new CloseTrackingOutputStream();

    service.exportCsv("table-1", null, null, List.of("SKU"), out);

    assertThat(out.closed).isFalse();
    assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("﻿SKU\r\n");
  }

  private static final class CloseTrackingOutputStream extends ByteArrayOutputStream {
    private boolean closed;

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }

  // ---- 失敗の伝播: CsvExportException ----

  @Test
  void wrapsSqlFailureInCsvExportException() {
    // 物理テーブルが存在しない(設定と実DBの不整合)。
    givenTable("PUBLIC", "NO_SUCH_TABLE", column("SKU", EditorType.TEXT, 0));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertThatThrownBy(() -> service.exportCsv("table-1", null, null, List.of("SKU"), out))
        .isInstanceOf(CsvExportException.class)
        .hasCauseInstanceOf(SQLException.class)
        .hasMessageContaining("table-1");
  }

  @Test
  void wrapsOutputStreamFailureInCsvExportException() {
    givenTable("PUBLIC", "ITEMS", column("SKU", EditorType.TEXT, 0));
    OutputStream failing =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            throw new IOException("broken pipe");
          }
        };

    assertThatThrownBy(() -> service.exportCsv("table-1", null, null, List.of("SKU"), failing))
        .isInstanceOf(CsvExportException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  // ---- R-03: ストリーミングのためのコネクション設定 ----

  @ParameterizedTest(name = "{0}: fetchSize={1}")
  @CsvSource({
    "PostgreSQL, 500",
    "MySQL, -2147483648",
    "MariaDB, -2147483648",
    "H2, 500",
    "Oracle, 500"
  })
  void configuresReadOnlyNonAutoCommitConnectionAndDriverSpecificFetchSize(
      String productName, int expectedFetchSize) throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    when(mockDataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getIdentifierQuoteString()).thenReturn("\"");
    when(metaData.getDatabaseProductName()).thenReturn(productName);
    // 1回目: 元の状態(自動コミット有効)、2回目: 変更後の状態(復元時の確認)。
    when(connection.getAutoCommit()).thenReturn(true, false);
    when(connection.isReadOnly()).thenReturn(false);
    when(connection.prepareStatement(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(ResultSet.TYPE_FORWARD_ONLY),
            org.mockito.ArgumentMatchers.eq(ResultSet.CONCUR_READ_ONLY)))
        .thenReturn(statement);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);
    givenTable("PUBLIC", "ITEMS", column("SKU", EditorType.TEXT, 0));
    CsvExportService mockedService =
        new CsvExportService(configEngineApi, new CsvColumnDefinitionResolver(), mockDataSource);

    mockedService.exportCsv("table-1", null, null, List.of("SKU"), new ByteArrayOutputStream());

    InOrder order = inOrder(connection, statement);
    // 読み取り専用・自動コミット無効(PostgreSQLのカーソル取得の前提)→fetchSize→元の状態へ復元。
    order.verify(connection).setReadOnly(true);
    order.verify(connection).setAutoCommit(false);
    order.verify(statement).setFetchSize(expectedFetchSize);
    order.verify(connection).rollback();
    order.verify(connection).setAutoCommit(true);
    order.verify(connection).setReadOnly(false);
    order.verify(connection).close();
  }

  @Test
  void restoresConnectionStateEvenWhenTheQueryFails() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(mockDataSource.getConnection()).thenReturn(connection);
    when(connection.getMetaData()).thenReturn(metaData);
    when(metaData.getIdentifierQuoteString()).thenReturn("\"");
    when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(connection.getAutoCommit()).thenReturn(true, false);
    when(connection.prepareStatement(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenThrow(new SQLException("boom"));
    givenTable("PUBLIC", "ITEMS", column("SKU", EditorType.TEXT, 0));
    CsvExportService mockedService =
        new CsvExportService(configEngineApi, new CsvColumnDefinitionResolver(), mockDataSource);

    assertThatThrownBy(
            () ->
                mockedService.exportCsv(
                    "table-1", null, null, List.of("SKU"), new ByteArrayOutputStream()))
        .isInstanceOf(CsvExportException.class);

    InOrder order = inOrder(connection);
    order.verify(connection).rollback();
    order.verify(connection).setAutoCommit(true);
    order.verify(connection).close();
  }

  @Test
  void fetchSizeIsRowByRowStreamingForMySqlFamilyAndBatchedOtherwise() {
    assertThat(CsvExportService.fetchSizeFor("MySQL")).isEqualTo(Integer.MIN_VALUE);
    assertThat(CsvExportService.fetchSizeFor("MariaDB")).isEqualTo(Integer.MIN_VALUE);
    assertThat(CsvExportService.fetchSizeFor("PostgreSQL")).isEqualTo(500);
    assertThat(CsvExportService.fetchSizeFor(null)).isEqualTo(500);
  }
}
