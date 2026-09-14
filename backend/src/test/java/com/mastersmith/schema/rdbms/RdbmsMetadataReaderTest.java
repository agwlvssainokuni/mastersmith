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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.config.rdbms.RdbmsDialect;
import com.mastersmith.schema.dto.RdbmsColumnMetadata;
import com.mastersmith.schema.dto.RdbmsTableMetadata;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
