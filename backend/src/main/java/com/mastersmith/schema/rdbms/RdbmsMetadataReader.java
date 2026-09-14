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

import com.mastersmith.config.rdbms.RdbmsDialect;
import com.mastersmith.schema.dto.RdbmsColumnMetadata;
import com.mastersmith.schema.dto.RdbmsTableMetadata;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 対象RDBMS(業務データ用)のメタデータカタログをJDBC {@link DatabaseMetaData}経由で読み取る(BR2.1, BR2.2, BR2.3, BR2.4, BR2.9,
 * BR2.10)。読み取り専用のメタデータ参照クエリのみを実行し、DDL/DMLは一切実行しない(BR2.1)。
 *
 * <p><b>タイムアウト設計(performance-design.md NFR1.1, NFR4.1)</b>:
 * 接続取得を5秒、接続確立後のメタデータ読み取りを25秒で打ち切る(30秒以内の同期処理という上位目標の内訳)。接続取得はプール実装(HikariCP等)に依存せず、専用の{@link
 * ExecutorService}でラップした{@link Future#get(long, TimeUnit)}により打ち切る。接続確立後は標準JDBCの{@link
 * Connection#setNetworkTimeout(java.util.concurrent.Executor,
 * int)}を用いる(対応ドライバであることを前提とする、H2/PostgreSQL/MySQL/MariaDBはいずれも対応)。
 *
 * <p><b>カタログ/スキーマの解釈</b>: PostgreSQLはJDBCの{@code
 * schema}パラメータで対象スキーマを絞り込む方式を採用し、MySQL/MariaDBは{@code
 * catalog}パラメータで絞り込む方式(MySQLの「スキーマ」は事実上カタログと同義)を採用する。
 */
@Component
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class RdbmsMetadataReader {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsMetadataReader.class);

  private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

  private final DataSource businessDataSource;
  private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();

  public RdbmsMetadataReader(@Qualifier("businessDataSource") DataSource businessDataSource) {
    this.businessDataSource = businessDataSource;
  }

  @PreDestroy
  void shutdown() {
    connectionExecutor.shutdown();
  }

  /**
   * 対象範囲(BR2.2)の全テーブルのメタデータと、判定したRDBMS方言(BR2.3)を1回のconnectionで読み取る。
   *
   * @throws SchemaIntrospectionException 接続失敗・メタデータ読み取り失敗・方言判定不可の場合(BR2.9)
   */
  public RdbmsSchemaSnapshot readSchema(String schemaName, List<String> tableNames) {
    Connection connection = acquireConnectionWithTimeout();
    try {
      connection.setNetworkTimeout(connectionExecutor, (int) READ_TIMEOUT.toMillis());
      DatabaseMetaData metaData = connection.getMetaData();
      RdbmsDialect dialect = detectDialect(metaData);
      List<RdbmsTableMetadata> tables = readTables(connection, dialect, schemaName, tableNames);
      return new RdbmsSchemaSnapshot(dialect, tables);
    } catch (SQLException e) {
      throw new SchemaIntrospectionException(
          "Failed to read business database metadata for schemaName=" + schemaName, e);
    } finally {
      closeQuietly(connection);
    }
  }

  private Connection acquireConnectionWithTimeout() {
    Callable<Connection> task = businessDataSource::getConnection;
    Future<Connection> future = connectionExecutor.submit(task);
    try {
      return future.get(CONNECTION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new SchemaIntrospectionException(
          "Failed to connect to the business database within "
              + CONNECTION_TIMEOUT.toSeconds()
              + "s",
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SchemaIntrospectionException(
          "Interrupted while connecting to the business database", e);
    } catch (ExecutionException e) {
      throw new SchemaIntrospectionException(
          "Failed to connect to the business database", e.getCause() != null ? e.getCause() : e);
    }
  }

  private void closeQuietly(Connection connection) {
    try {
      connection.setNetworkTimeout(connectionExecutor, 0);
    } catch (SQLException e) {
      LOG.warn(
          "Failed to reset network timeout before returning the business database connection", e);
    } finally {
      try {
        connection.close();
      } catch (SQLException e) {
        LOG.warn("Failed to close the business database connection", e);
      }
    }
  }

  /**
   * BR2.3: 対象RDBMSの方言を{@link DatabaseMetaData#getDatabaseProductName()}から判定する。
   *
   * @throws SchemaIntrospectionException 未対応の方言が検出された場合(BR2.3 violation_behaviour)
   */
  RdbmsDialect detectDialect(DatabaseMetaData metaData) throws SQLException {
    String productName = metaData.getDatabaseProductName();
    String normalized = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
    if (normalized.contains("postgresql")) {
      return RdbmsDialect.POSTGRESQL;
    }
    if (normalized.contains("mariadb")) {
      return RdbmsDialect.MARIADB;
    }
    if (normalized.contains("mysql")) {
      return RdbmsDialect.MYSQL;
    }
    throw new SchemaIntrospectionException("Unsupported RDBMS dialect: " + productName);
  }

  /**
   * BR2.2: tableNamesが指定されていればそのテーブルのみ、省略されていればschemaName配下の全テーブルを読み取る。 BR2.1, BR2.4, BR2.10:
   * 各テーブルのカラム名・生の型名・主キー判定・NULL可否を読み取る。
   *
   * @throws SchemaIntrospectionException 指定されたテーブルにカラムが1件も見つからない場合(存在しないテーブル、BR2.9)
   */
  List<RdbmsTableMetadata> readTables(
      Connection connection, RdbmsDialect dialect, String schemaName, List<String> tableNames)
      throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    List<String> resolvedTableNames =
        tableNames == null || tableNames.isEmpty()
            ? listAllTableNames(metaData, dialect, schemaName)
            : List.copyOf(tableNames);
    List<RdbmsTableMetadata> tables = new ArrayList<>();
    boolean explicitTableNames = tableNames != null && !tableNames.isEmpty();
    for (String tableName : resolvedTableNames) {
      List<RdbmsColumnMetadata> columns = readColumns(metaData, dialect, schemaName, tableName);
      if (columns.isEmpty()) {
        if (explicitTableNames) {
          throw new SchemaIntrospectionException(
              "Table not found or has no readable columns: schemaName="
                  + schemaName
                  + ", tableName="
                  + tableName);
        }
        continue;
      }
      tables.add(new RdbmsTableMetadata(schemaName, tableName, columns));
    }
    return tables;
  }

  private List<String> listAllTableNames(
      DatabaseMetaData metaData, RdbmsDialect dialect, String schemaName) throws SQLException {
    List<String> names = new ArrayList<>();
    String catalog = catalogParam(dialect, schemaName);
    String schemaPattern = schemaParam(dialect, schemaName);
    try (ResultSet resultSet =
        metaData.getTables(catalog, schemaPattern, "%", new String[] {"TABLE"})) {
      while (resultSet.next()) {
        names.add(resultSet.getString("TABLE_NAME"));
      }
    }
    return names;
  }

  private List<RdbmsColumnMetadata> readColumns(
      DatabaseMetaData metaData, RdbmsDialect dialect, String schemaName, String tableName)
      throws SQLException {
    Set<String> primaryKeyColumnNames =
        readPrimaryKeyColumnNames(metaData, dialect, schemaName, tableName);
    String catalog = catalogParam(dialect, schemaName);
    String schemaPattern = schemaParam(dialect, schemaName);
    List<RdbmsColumnMetadata> columns = new ArrayList<>();
    try (ResultSet resultSet = metaData.getColumns(catalog, schemaPattern, tableName, "%")) {
      while (resultSet.next()) {
        String columnName = resultSet.getString("COLUMN_NAME");
        String rawTypeName = buildRawTypeName(resultSet);
        boolean isPrimaryKey = primaryKeyColumnNames.contains(columnName);
        boolean nullable = isNullable(resultSet);
        columns.add(new RdbmsColumnMetadata(columnName, rawTypeName, isPrimaryKey, nullable));
      }
    }
    return columns;
  }

  private Set<String> readPrimaryKeyColumnNames(
      DatabaseMetaData metaData, RdbmsDialect dialect, String schemaName, String tableName)
      throws SQLException {
    String catalog = catalogParam(dialect, schemaName);
    String schemaParam = schemaParam(dialect, schemaName);
    Set<String> names = new LinkedHashSet<>();
    try (ResultSet resultSet = metaData.getPrimaryKeys(catalog, schemaParam, tableName)) {
      while (resultSet.next()) {
        names.add(resultSet.getString("COLUMN_NAME"));
      }
    }
    return names;
  }

  /** MySQL/MariaDBはJDBCの{@code catalog}パラメータでスキーマ(データベース)を絞り込む。 */
  private static String catalogParam(RdbmsDialect dialect, String schemaName) {
    return dialect == RdbmsDialect.MYSQL || dialect == RdbmsDialect.MARIADB ? schemaName : null;
  }

  /** PostgreSQLはJDBCの{@code schema}パラメータでスキーマを絞り込む。 */
  private static String schemaParam(RdbmsDialect dialect, String schemaName) {
    return dialect == RdbmsDialect.MYSQL || dialect == RdbmsDialect.MARIADB ? null : schemaName;
  }

  private static boolean isNullable(ResultSet columnsResultSet) throws SQLException {
    String isNullable = columnsResultSet.getString("IS_NULLABLE");
    // BR2.10: "NO"のみを確定的な非NULL可否とし、"YES"・不明("")はいずれも安全側(nullable=true)として扱う。
    return !"NO".equalsIgnoreCase(isNullable);
  }

  private static String buildRawTypeName(ResultSet columnsResultSet) throws SQLException {
    String typeName = columnsResultSet.getString("TYPE_NAME");
    int dataType = columnsResultSet.getInt("DATA_TYPE");
    int columnSize = columnsResultSet.getInt("COLUMN_SIZE");
    int decimalDigits = columnsResultSet.getInt("DECIMAL_DIGITS");
    return switch (dataType) {
      case Types.CHAR,
          Types.VARCHAR,
          Types.NCHAR,
          Types.NVARCHAR,
          Types.LONGVARCHAR,
          Types.LONGNVARCHAR ->
          columnSize > 0 ? typeName + "(" + columnSize + ")" : typeName;
      case Types.DECIMAL, Types.NUMERIC ->
          columnSize > 0
              ? typeName + "(" + columnSize + (decimalDigits > 0 ? "," + decimalDigits : "") + ")"
              : typeName;
      default -> typeName;
    };
  }
}
