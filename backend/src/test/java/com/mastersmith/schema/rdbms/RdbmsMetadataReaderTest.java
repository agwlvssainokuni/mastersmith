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

package com.mastersmith.schema.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.config.rdbms.RdbmsDialect;
import com.mastersmith.schema.dto.RdbmsColumnMetadata;
import com.mastersmith.schema.dto.RdbmsTableMetadata;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.jdbc.DataSourceBuilder;

/**
 * {@link RdbmsMetadataReader}の単体テスト(BR2.1〜BR2.4, BR2.9, BR2.10)。
 *
 * <p>方言判定(BR2.3、{@link RdbmsMetadataReader#detectDialect})はMockitoでモックした{@link
 * DatabaseMetaData}で検証する。テーブル・カラム読み取り(BR2.1, BR2.2, BR2.4, BR2.10)は、実際のH2インメモリDBに作成した
 * テストスキーマに対し、実際のJDBC {@link
 * DatabaseMetaData}を用いて検証する(モックしない)。対象RDBMSはPostgreSQL/MySQL/MariaDBのいずれかだが、H2はこの3方言のいずれでもないため、カラム読み取りテストでは
 * H2のJDBCメタデータ挙動がPostgreSQL方式(catalog=null, schema=対象スキーマ)と互換であることを踏まえ、{@link
 * RdbmsDialect#POSTGRESQL}を指定して{@code readTables}を直接呼び出す。
 */
class RdbmsMetadataReaderTest {

  private DataSource dataSource;
  private RdbmsMetadataReader reader;
  private final List<RdbmsMetadataReader> extraReaders = new ArrayList<>();

  @BeforeEach
  void setUp() throws SQLException {
    dataSource =
        DataSourceBuilder.create()
            .url("jdbc:h2:mem:schema-introspector-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
            .driverClassName("org.h2.Driver")
            .username("sa")
            .password("")
            .build();
    reader = new RdbmsMetadataReader(dataSource);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE PUBLIC.ITEMS ("
              + "SKU VARCHAR(50) NOT NULL, "
              + "NAME VARCHAR(100), "
              + "PRICE DECIMAL(10,2) NOT NULL, "
              + "PRIMARY KEY (SKU))");
      statement.execute(
          "CREATE TABLE PUBLIC.ORDER_ITEMS ("
              + "ORDER_ID VARCHAR(20) NOT NULL, "
              + "LINE_NO INT NOT NULL, "
              + "QTY INT NOT NULL, "
              + "PRIMARY KEY (ORDER_ID, LINE_NO))");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP ALL OBJECTS");
    }
    reader.shutdown();
    extraReaders.forEach(RdbmsMetadataReader::shutdown);
  }

  /** タイムアウトを差し替えたreaderを作る(テスト終了時に、後始末される)。 */
  private RdbmsMetadataReader readerWith(
      DataSource source, Duration connectionTimeout, Duration readTimeout) {
    RdbmsMetadataReader created = new RdbmsMetadataReader(source, connectionTimeout, readTimeout);
    extraReaders.add(created);
    return created;
  }

  /**
   * 実際のH2の接続を、PostgreSQLとして名乗らせる({@code
   * getDatabaseProductName}だけを差し替える)DataSource。H2は、この3方言のいずれでもないため、公開の {@code
   * readSchema}の成功経路(方言判定を含む)を、実データで通すために用いる。
   */
  private DataSource postgresqlLookingDataSource() {
    DataSource delegate = dataSource;
    return (DataSource)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {DataSource.class},
            (p, method, args) -> {
              Object result = invoke(delegate, method, args);
              if ("getConnection".equals(method.getName())) {
                return asPostgresql((Connection) result);
              }
              return result;
            });
  }

  private static Connection asPostgresql(Connection real) {
    return (Connection)
        Proxy.newProxyInstance(
            RdbmsMetadataReaderTest.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (p, method, args) -> {
              Object result = invoke(real, method, args);
              if ("getMetaData".equals(method.getName())) {
                DatabaseMetaData realMetaData = (DatabaseMetaData) result;
                return Proxy.newProxyInstance(
                    RdbmsMetadataReaderTest.class.getClassLoader(),
                    new Class<?>[] {DatabaseMetaData.class},
                    (mp, metaMethod, metaArgs) ->
                        "getDatabaseProductName".equals(metaMethod.getName())
                            ? "PostgreSQL"
                            : invoke(realMetaData, metaMethod, metaArgs));
              }
              return result;
            });
  }

  private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  /**
   * {@code getTables}・{@code getColumns}・{@code
   * getPrimaryKeys}の結果を、指定の行で返すDatabaseMetaData(MySQL/MariaDBの分岐の検証用)。
   */
  private static DatabaseMetaData mockMetaDataWithTable(
      String catalog, String tableName, String columnName) throws SQLException {
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(metaData.getSearchStringEscape()).thenReturn("\\");

    ResultSet tables = mock(ResultSet.class);
    when(tables.next()).thenReturn(true, false);
    when(tables.getString("TABLE_NAME")).thenReturn(tableName);
    when(metaData.getTables(eq(catalog), isNull(), eq("%"), aryEq(new String[] {"TABLE"})))
        .thenReturn(tables);

    ResultSet primaryKeys = mock(ResultSet.class);
    when(primaryKeys.next()).thenReturn(true, false);
    when(primaryKeys.getString("COLUMN_NAME")).thenReturn(columnName);
    when(metaData.getPrimaryKeys(catalog, null, tableName)).thenReturn(primaryKeys);

    ResultSet columns = mock(ResultSet.class);
    when(columns.next()).thenReturn(true, false);
    when(columns.getString("TABLE_NAME")).thenReturn(tableName);
    when(columns.getString("COLUMN_NAME")).thenReturn(columnName);
    when(columns.getString("TYPE_NAME")).thenReturn("VARCHAR");
    when(columns.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
    when(columns.getInt("COLUMN_SIZE")).thenReturn(20);
    when(columns.getString("IS_NULLABLE")).thenReturn("NO");
    when(metaData.getColumns(eq(catalog), isNull(), any(), eq("%"))).thenReturn(columns);
    return metaData;
  }

  private static Optional<RdbmsColumnMetadata> column(RdbmsTableMetadata table, String columnName) {
    return table.columns().stream()
        .filter(c -> c.columnName().equalsIgnoreCase(columnName))
        .findFirst();
  }

  @Test
  void detectDialectRecognizesPostgresql() throws SQLException {
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

    assertThat(reader.detectDialect(metaData)).isEqualTo(RdbmsDialect.POSTGRESQL);
  }

  @Test
  void detectDialectRecognizesMysql() throws SQLException {
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(metaData.getDatabaseProductName()).thenReturn("MySQL");

    assertThat(reader.detectDialect(metaData)).isEqualTo(RdbmsDialect.MYSQL);
  }

  @Test
  void detectDialectRecognizesMariadbBeforeMysql() throws SQLException {
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(metaData.getDatabaseProductName()).thenReturn("MariaDB");

    assertThat(reader.detectDialect(metaData)).isEqualTo(RdbmsDialect.MARIADB);
  }

  @Test
  void detectDialectFailsFastForUnsupportedProduct() throws SQLException {
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(metaData.getDatabaseProductName()).thenReturn("H2");

    assertThatThrownBy(() -> reader.detectDialect(metaData))
        .isInstanceOf(SchemaIntrospectionException.class)
        .hasMessageContaining("H2");
  }

  @Test
  void readTablesReadsAllTablesWhenTableNamesOmitted() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      List<RdbmsTableMetadata> tables =
          reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBLIC", null);

      assertThat(tables)
          .extracting(RdbmsTableMetadata::tableName)
          .containsExactlyInAnyOrder("ITEMS", "ORDER_ITEMS");
    }
  }

  @Test
  void readTablesRestrictsToExplicitTableNames() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      List<RdbmsTableMetadata> tables =
          reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBLIC", List.of("ITEMS"));

      assertThat(tables).extracting(RdbmsTableMetadata::tableName).containsExactly("ITEMS");
    }
  }

  @Test
  void readTablesMarksSinglePrimaryKeyColumnAndPreservesNullability() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      List<RdbmsTableMetadata> tables =
          reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBLIC", List.of("ITEMS"));

      RdbmsTableMetadata items = tables.get(0);
      RdbmsColumnMetadata sku = column(items, "SKU").orElseThrow();
      RdbmsColumnMetadata name = column(items, "NAME").orElseThrow();
      RdbmsColumnMetadata price = column(items, "PRICE").orElseThrow();

      assertThat(sku.isPrimaryKey()).isTrue();
      assertThat(sku.nullable()).isFalse();
      // H2の実際のTYPE_NAME表記("CHARACTER VARYING"等)は方言依存であり本テストの関心事ではない。
      // BR2.5: 正規化せず生のまま(サイズ情報付きで)渡していることのみを確認する。
      assertThat(sku.rawTypeName()).contains("(50)");
      assertThat(name.isPrimaryKey()).isFalse();
      assertThat(name.nullable()).isTrue();
      assertThat(price.isPrimaryKey()).isFalse();
      assertThat(price.nullable()).isFalse();
      assertThat(price.rawTypeName()).contains("(10,2)");
    }
  }

  @Test
  void readTablesMarksAllCompositePrimaryKeyColumnsAsTrue() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      List<RdbmsTableMetadata> tables =
          reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBLIC", List.of("ORDER_ITEMS"));

      RdbmsTableMetadata orderItems = tables.get(0);
      assertThat(column(orderItems, "ORDER_ID").orElseThrow().isPrimaryKey()).isTrue();
      assertThat(column(orderItems, "LINE_NO").orElseThrow().isPrimaryKey()).isTrue();
      assertThat(column(orderItems, "QTY").orElseThrow().isPrimaryKey()).isFalse();
    }
  }

  @Test
  void readTablesReturnsEmptyListWhenSchemaHasNoTables() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      List<RdbmsTableMetadata> tables =
          reader.readTables(connection, RdbmsDialect.POSTGRESQL, "NO_SUCH_SCHEMA", null);

      assertThat(tables).isEmpty();
    }
  }

  @Test
  void readTablesFailsFastWhenExplicitTableNameDoesNotExist() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      assertThatThrownBy(
              () ->
                  reader.readTables(
                      connection, RdbmsDialect.POSTGRESQL, "PUBLIC", List.of("NO_SUCH_TABLE")))
          .isInstanceOf(SchemaIntrospectionException.class);
    }
  }

  @Test
  void readSchemaFailsFastForUnsupportedDialectLikeH2() {
    // BR2.9: H2はPostgreSQL/MySQL/MariaDBのいずれでもないため、readSchema全体としては
    // 方言判定不可でfail fastする(接続取得・タイムアウト設定を含む一連の配線自体はここで検証する)。
    assertThatThrownBy(() -> reader.readSchema("PUBLIC", null))
        .isInstanceOf(SchemaIntrospectionException.class);
  }

  // ---- R-01: LIKEパターンの扱い(別テーブルのカラムの混入の防止) ----

  private void execute(String... statements) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.execute(sql);
      }
    }
  }

  private static List<String> columnNames(RdbmsTableMetadata table) {
    return table.columns().stream().map(RdbmsColumnMetadata::columnName).toList();
  }

  @Test
  void readTablesDoesNotMixInColumnsOfATableWhoseNameMatchesTheUnderscoreWildcard()
      throws SQLException {
    // ORDER_ITEMSの「_」は、LIKEパターンでは任意の1文字のため、ORDERXITEMSにも一致する。
    // ORDERXITEMSは、主キーをQTYにして、getPrimaryKeysも、完全一致であることを確かめる。
    execute(
        "CREATE TABLE PUBLIC.ORDERXITEMS ("
            + "ORDER_ID VARCHAR(20) NOT NULL, "
            + "LINE_NO INT NOT NULL, "
            + "QTY INT NOT NULL, "
            + "SECRET VARCHAR(10), "
            + "PRIMARY KEY (QTY))");
    try (Connection connection = dataSource.getConnection()) {
      List<RdbmsTableMetadata> tables =
          reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBLIC", List.of("ORDER_ITEMS"));

      assertThat(tables).hasSize(1);
      RdbmsTableMetadata orderItems = tables.get(0);
      assertThat(columnNames(orderItems)).containsExactly("ORDER_ID", "LINE_NO", "QTY");
      assertThat(column(orderItems, "QTY").orElseThrow().isPrimaryKey()).isFalse();
      assertThat(column(orderItems, "ORDER_ID").orElseThrow().isPrimaryKey()).isTrue();
    }
  }

  @Test
  void readTablesKeepsEachTablesOwnColumnsWhenReadingAllTables() throws SQLException {
    execute(
        "CREATE TABLE PUBLIC.ORDERXITEMS ("
            + "ORDER_ID VARCHAR(20) NOT NULL, "
            + "SECRET VARCHAR(10), "
            + "PRIMARY KEY (ORDER_ID))");
    try (Connection connection = dataSource.getConnection()) {
      List<RdbmsTableMetadata> tables =
          reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBLIC", null);

      assertThat(tables)
          .extracting(RdbmsTableMetadata::tableName)
          .containsExactlyInAnyOrder("ITEMS", "ORDER_ITEMS", "ORDERXITEMS");
      RdbmsTableMetadata orderItems =
          tables.stream()
              .filter(t -> t.tableName().equals("ORDER_ITEMS"))
              .findFirst()
              .orElseThrow();
      RdbmsTableMetadata orderXItems =
          tables.stream()
              .filter(t -> t.tableName().equals("ORDERXITEMS"))
              .findFirst()
              .orElseThrow();
      assertThat(columnNames(orderItems)).containsExactly("ORDER_ID", "LINE_NO", "QTY");
      assertThat(columnNames(orderXItems)).containsExactly("ORDER_ID", "SECRET");
    }
  }

  @Test
  void readTablesTreatsAPercentInATableNameAsALiteralNotAWildcard() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      assertThatThrownBy(
              () -> reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBLIC", List.of("%")))
          .isInstanceOf(SchemaIntrospectionException.class);
    }
  }

  @Test
  void readTablesTreatsAnUnderscoreInATableNameAsALiteralWhenNoTableHasThatExactName()
      throws SQLException {
    // 「ITEM_」は、パターンでは「ITEMS」に一致するが、完全一致では、存在しないテーブルである。
    try (Connection connection = dataSource.getConnection()) {
      assertThatThrownBy(
              () ->
                  reader.readTables(
                      connection, RdbmsDialect.POSTGRESQL, "PUBLIC", List.of("ITEM_")))
          .isInstanceOf(SchemaIntrospectionException.class);
    }
  }

  @Test
  void readTablesTreatsAPercentInTheSchemaNameAsALiteralNotAWildcard() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      // 「%」は全スキーマに一致するパターンだが、リテラルの名前として扱う。そのようなスキーマは存在しない。
      assertThat(reader.readTables(connection, RdbmsDialect.POSTGRESQL, "%", null)).isEmpty();
      assertThatThrownBy(
              () -> reader.readTables(connection, RdbmsDialect.POSTGRESQL, "%", List.of("ITEMS")))
          .isInstanceOf(SchemaIntrospectionException.class);
    }
  }

  @Test
  void readTablesTreatsAnUnderscoreInTheSchemaNameAsALiteral() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      // 「PUBL_C」は、パターンでは「PUBLIC」に一致する。
      assertThat(reader.readTables(connection, RdbmsDialect.POSTGRESQL, "PUBL_C", null)).isEmpty();
    }
  }

  @Test
  void readTablesDoesNotLeakControlCharactersOfClientSuppliedNamesIntoTheMessage()
      throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      assertThatThrownBy(
              () ->
                  reader.readTables(
                      connection,
                      RdbmsDialect.POSTGRESQL,
                      "PUBLIC",
                      List.of("NO_SUCH\nINFO forged log entry")))
          .isInstanceOf(SchemaIntrospectionException.class)
          .satisfies(
              e -> {
                assertThat(e.getMessage()).doesNotContain("\n");
                assertThat(e.getMessage()).contains("NO_SUCH\\u000aINFO forged log entry");
              });
    }
  }

  // ---- R-05: 公開のreadSchemaの成功経路と、MySQL/MariaDBの分岐 ----

  @Test
  void readSchemaReturnsTheDialectAndTheTablesThroughTheRealConnectionPath() {
    RdbmsMetadataReader postgresqlReader =
        readerWith(postgresqlLookingDataSource(), Duration.ofSeconds(5), Duration.ofSeconds(25));

    RdbmsSchemaSnapshot snapshot = postgresqlReader.readSchema("PUBLIC", null);

    assertThat(snapshot.dialect()).isEqualTo(RdbmsDialect.POSTGRESQL);
    assertThat(snapshot.tables())
        .extracting(RdbmsTableMetadata::tableName)
        .containsExactlyInAnyOrder("ITEMS", "ORDER_ITEMS");
    RdbmsTableMetadata items =
        snapshot.tables().stream().filter(t -> t.tableName().equals("ITEMS")).findFirst().get();
    assertThat(column(items, "SKU").orElseThrow().isPrimaryKey()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = RdbmsDialect.class,
      names = {"MYSQL", "MARIADB"})
  void catalogBasedDialectsNarrowBySchemaNameThroughTheCatalogArgument(RdbmsDialect dialect)
      throws SQLException {
    DatabaseMetaData metaData = mockMetaDataWithTable("shop", "items", "sku");
    Connection connection = mock(Connection.class);
    when(connection.getMetaData()).thenReturn(metaData);

    List<RdbmsTableMetadata> tables = reader.readTables(connection, dialect, "shop", null);

    assertThat(tables).hasSize(1);
    assertThat(tables.get(0).schemaName()).isEqualTo("shop");
    assertThat(tables.get(0).tableName()).isEqualTo("items");
    assertThat(tables.get(0).columns().get(0).isPrimaryKey()).isTrue();
    assertThat(tables.get(0).columns().get(0).nullable()).isFalse();
    // schemaNameは、パターンではない、完全一致のcatalogに渡し、schemaのパターンには、何も渡さない。
    verify(metaData).getTables(eq("shop"), isNull(), eq("%"), aryEq(new String[] {"TABLE"}));
    verify(metaData).getPrimaryKeys("shop", null, "items");
  }

  @ParameterizedTest
  @EnumSource(
      value = RdbmsDialect.class,
      names = {"MYSQL", "MARIADB"})
  void catalogBasedDialectsEscapeThePatternCharactersOfTheTableName(RdbmsDialect dialect)
      throws SQLException {
    DatabaseMetaData metaData = mockMetaDataWithTable("shop", "order_items", "id");
    Connection connection = mock(Connection.class);
    when(connection.getMetaData()).thenReturn(metaData);

    List<RdbmsTableMetadata> tables =
        reader.readTables(connection, dialect, "shop", List.of("order_items"));

    assertThat(tables).extracting(RdbmsTableMetadata::tableName).containsExactly("order_items");
    // getColumnsのテーブル名はLIKEパターンのため、「_」をエスケープして渡す。getPrimaryKeysは完全一致のため、そのまま。
    verify(metaData).getColumns("shop", null, "order\\_items", "%");
    verify(metaData).getPrimaryKeys("shop", null, "order_items");
  }

  @Test
  void aCatalogBasedDialectDiscardsColumnRowsOfAnotherTableThatTheDriverReturned()
      throws SQLException {
    // ドライバがエスケープを解釈せず、パターンに一致する別のテーブルの行も返した場合の備え(結果の照合)。
    DatabaseMetaData metaData = mockMetaDataWithTable("shop", "order_items", "id");
    ResultSet mixedColumns = mock(ResultSet.class);
    // 行の位置に応じて値を返す(照合で読み飛ばされた行では、値が読まれないため、呼び出し順には頼れない)。
    String[] tableNamesByRow = {"orderxitems", "order_items"};
    String[] columnNamesByRow = {"secret", "id"};
    int[] row = {-1};
    when(mixedColumns.next()).thenAnswer(invocation -> ++row[0] < tableNamesByRow.length);
    when(mixedColumns.getString("TABLE_NAME")).thenAnswer(invocation -> tableNamesByRow[row[0]]);
    when(mixedColumns.getString("COLUMN_NAME")).thenAnswer(invocation -> columnNamesByRow[row[0]]);
    when(mixedColumns.getString("TYPE_NAME")).thenReturn("VARCHAR");
    when(mixedColumns.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
    when(mixedColumns.getString("IS_NULLABLE")).thenReturn("YES");
    when(metaData.getColumns(eq("shop"), isNull(), any(), eq("%"))).thenReturn(mixedColumns);
    Connection connection = mock(Connection.class);
    when(connection.getMetaData()).thenReturn(metaData);

    List<RdbmsTableMetadata> tables =
        reader.readTables(connection, RdbmsDialect.MYSQL, "shop", List.of("order_items"));

    assertThat(columnNames(tables.get(0))).containsExactly("id");
  }

  // ---- R-04: タイムアウト ----

  @Test
  void failsFastWhenTheReadDeadlineHasPassedBeforeTheNextTable() {
    RdbmsMetadataReader impatientReader =
        readerWith(postgresqlLookingDataSource(), Duration.ofSeconds(5), Duration.ofNanos(1));

    assertThatThrownBy(() -> impatientReader.readSchema("PUBLIC", null))
        .isInstanceOf(SchemaIntrospectionException.class)
        .hasMessageContaining("exceeded");
  }

  @Test
  void reportsAConnectionFailureAsASchemaIntrospectionException() throws SQLException {
    DataSource broken = mock(DataSource.class);
    when(broken.getConnection()).thenThrow(new SQLException("connection refused"));
    RdbmsMetadataReader brokenReader =
        readerWith(broken, Duration.ofSeconds(5), Duration.ofSeconds(25));

    assertThatThrownBy(() -> brokenReader.readSchema("PUBLIC", null))
        .isInstanceOf(SchemaIntrospectionException.class)
        .hasMessageContaining("Failed to connect");
  }

  @Test
  void closesAConnectionThatIsAcquiredAfterTheConnectionTimeoutHasElapsed() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch closed = new CountDownLatch(1);
    Connection lateConnection = mock(Connection.class);
    doAnswer(
            invocation -> {
              closed.countDown();
              return null;
            })
        .when(lateConnection)
        .close();
    DataSource slow = mock(DataSource.class);
    when(slow.getConnection())
        .thenAnswer(
            invocation -> {
              release.await(10, TimeUnit.SECONDS);
              return lateConnection;
            });
    RdbmsMetadataReader slowReader =
        readerWith(slow, Duration.ofMillis(100), Duration.ofSeconds(25));

    assertThatThrownBy(() -> slowReader.readSchema("PUBLIC", null))
        .isInstanceOf(SchemaIntrospectionException.class)
        .hasMessageContaining("within");
    // 接続は、まだ得られていない。得られた時点で、誰も使わない接続は、閉じられる(プール接続のリークの防止)。
    assertThat(closed.getCount()).isEqualTo(1);
    release.countDown();
    assertThat(closed.await(10, TimeUnit.SECONDS)).isTrue();
  }
}
