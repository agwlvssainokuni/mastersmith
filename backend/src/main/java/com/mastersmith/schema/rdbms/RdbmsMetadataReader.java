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
import com.mastersmith.schema.util.LogSanitizer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 対象RDBMS(業務データ用)のメタデータカタログをJDBC {@link DatabaseMetaData}経由で読み取る(BR2.1, BR2.2, BR2.3, BR2.4, BR2.9,
 * BR2.10)。読み取り専用のメタデータ参照クエリのみを実行し、DDL/DMLは一切実行しない(BR2.1)。
 *
 * <p><b>タイムアウト設計(performance-design.md NFR1.1, NFR4.1)</b>:
 * 接続取得を5秒、接続確立後のメタデータ読み取りを25秒で打ち切る(30秒以内の同期処理という上位目標の内訳)。接続取得はプール実装(HikariCP等)に依存せず、専用の{@link
 * ExecutorService}でラップした{@link CompletableFuture#get(long,
 * TimeUnit)}により打ち切り、打ち切りの後に遅れて得られた接続は、その場で閉じる(プール接続のリーク防止)。 接続確立後の読み取りは、2段で打ち切る。(1)標準JDBCの{@link
 * Connection#setNetworkTimeout(java.util.concurrent.Executor,
 * int)}による、ソケット1回あたりの待ちの上限(H2/PostgreSQL/MySQL/MariaDBはいずれも対応)。(2)読み取り全体の締切(接続確立から25秒)を、テーブルごとの読み取りの前に検査する。
 * 締切を超えた場合は、次のテーブルへ進まず、fail
 * fastする(BR2.9)。1回のメタデータ呼び出しの途中では、締切の検査はできないため、最悪の所要時間は、締切に、ソケット1回あたりの待ちの上限を加えたものとなる。
 *
 * <p><b>カタログ/スキーマの解釈</b>: PostgreSQLはJDBCの{@code
 * schema}パラメータで対象スキーマを絞り込む方式を採用し、MySQL/MariaDBは{@code
 * catalog}パラメータで絞り込む方式(MySQLの「スキーマ」は事実上カタログと同義)を採用する。
 *
 * <p><b>LIKEパターンの扱い(レビュー指摘R-01)</b>: JDBCの{@code getColumns}・{@code
 * getTables}の、スキーマ名・テーブル名の引数は、LIKEパターンである({@code _}は任意の1文字、{@code
 * %}は任意の文字列)。クライアントが指定した名前を、そのまま渡すと、{@code ORDER_ITEMS}が{@code
 * ORDERXITEMS}にも一致するなど、別のテーブルの情報が混入する。そのため、(1){@link
 * DatabaseMetaData#getSearchStringEscape()}で、パターン文字をエスケープして渡し、(2)結果の行の{@code
 * TABLE_NAME}(PostgreSQL方式では{@code TABLE_SCHEM}も)が、指定した名前と一致する行だけを採用する(ドライバがエスケープを解釈しない場合の備え)。
 * 名前の大文字小文字の区別は、DBの判定に従い、(2)の照合は、大文字小文字を区別しない(DBが区別しないために、エスケープ済みの名前で一致した行を落とさないため)。
 */
@Component
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class RdbmsMetadataReader {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsMetadataReader.class);

  private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(25);

  private final DataSource businessDataSource;
  private final Duration connectionTimeout;
  private final Duration readTimeout;
  private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();

  @Autowired
  public RdbmsMetadataReader(@Qualifier("businessDataSource") DataSource businessDataSource) {
    this(businessDataSource, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT);
  }

  /** タイムアウトを差し替えられるコンストラクタ(テスト用。本番は、NFR1.1・NFR4.1の既定値を用いる)。 */
  RdbmsMetadataReader(
      DataSource businessDataSource, Duration connectionTimeout, Duration readTimeout) {
    this.businessDataSource = businessDataSource;
    this.connectionTimeout = connectionTimeout;
    this.readTimeout = readTimeout;
  }

  @PreDestroy
  void shutdown() {
    connectionExecutor.shutdown();
  }

  /**
   * 対象範囲(BR2.2)の全テーブルのメタデータと、判定したRDBMS方言(BR2.3)を1回のconnectionで読み取る。
   *
   * @throws SchemaIntrospectionException 接続失敗・メタデータ読み取り失敗・読み取りの締切超過・方言判定不可の場合(BR2.9)
   */
  public RdbmsSchemaSnapshot readSchema(String schemaName, List<String> tableNames) {
    Connection connection = acquireConnectionWithTimeout();
    // 読み取り全体の締切は、接続が確立してから数える(接続取得の5秒とは別枠、NFR4.1)。
    long deadlineNanos = System.nanoTime() + readTimeout.toNanos();
    try {
      connection.setNetworkTimeout(connectionExecutor, (int) readTimeout.toMillis());
      DatabaseMetaData metaData = connection.getMetaData();
      RdbmsDialect dialect = detectDialect(metaData);
      List<RdbmsTableMetadata> tables =
          readTables(connection, dialect, schemaName, tableNames, deadlineNanos);
      return new RdbmsSchemaSnapshot(dialect, tables);
    } catch (SQLException e) {
      throw new SchemaIntrospectionException(
          "Failed to read business database metadata for schemaName="
              + LogSanitizer.clean(schemaName),
          e);
    } finally {
      closeQuietly(connection);
    }
  }

  private Connection acquireConnectionWithTimeout() {
    CompletableFuture<Connection> result = new CompletableFuture<>();
    try {
      connectionExecutor.execute(
          () -> {
            try {
              Connection acquired = businessDataSource.getConnection();
              if (!result.complete(acquired)) {
                // 呼び出し側が、待つのを打ち切った後に得られた接続。誰も使わないため、ここで閉じる(プール接続のリーク防止、R-04)。
                closeUnused(acquired);
              }
            } catch (SQLException | RuntimeException e) {
              result.completeExceptionally(e);
            }
          });
    } catch (RejectedExecutionException e) {
      throw new SchemaIntrospectionException(
          "Failed to connect to the business database (executor unavailable)", e);
    }
    try {
      return result.get(connectionTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      abandon(result);
      throw new SchemaIntrospectionException(
          "Failed to connect to the business database within "
              + connectionTimeout.toMillis()
              + "ms",
          e);
    } catch (InterruptedException e) {
      abandon(result);
      Thread.currentThread().interrupt();
      throw new SchemaIntrospectionException(
          "Interrupted while connecting to the business database", e);
    } catch (ExecutionException e) {
      throw new SchemaIntrospectionException(
          "Failed to connect to the business database", e.getCause() != null ? e.getCause() : e);
    }
  }

  /**
   * 接続の取得を待つのを打ち切る。打ち切りと同時に、接続が得られていた場合は、その接続を閉じる。まだ得られていない場合は、取得側のスレッドが、得られた時点で(自分の{@code
   * complete}が失敗するため)閉じる。{@link CompletableFuture}の完了は原子的なため、どちらか一方だけが閉じる。
   */
  private void abandon(CompletableFuture<Connection> result) {
    if (!result.cancel(true) && !result.isCompletedExceptionally()) {
      closeUnused(result.getNow(null));
    }
  }

  private void closeUnused(Connection connection) {
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException e) {
      LOG.warn("Failed to close an unused business database connection", e);
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
   * 締切を、呼び出し時点からの{@code readTimeout}として、{@link #readTables(Connection, RdbmsDialect, String, List,
   * long)}を呼ぶ。
   */
  List<RdbmsTableMetadata> readTables(
      Connection connection, RdbmsDialect dialect, String schemaName, List<String> tableNames)
      throws SQLException {
    return readTables(
        connection, dialect, schemaName, tableNames, System.nanoTime() + readTimeout.toNanos());
  }

  /**
   * BR2.2: tableNamesが指定されていればそのテーブルのみ、省略されていればschemaName配下の全テーブルを読み取る。 BR2.1, BR2.4, BR2.10:
   * 各テーブルのカラム名・生の型名・主キー判定・NULL可否を読み取る。
   *
   * @param deadlineNanos 読み取り全体の締切({@link System#nanoTime()}基準)。テーブルごとの読み取りの前に検査する(R-04)
   * @throws SchemaIntrospectionException 指定されたテーブルにカラムが1件も見つからない場合(存在しないテーブル、BR2.9)、または締切を超えた場合
   */
  List<RdbmsTableMetadata> readTables(
      Connection connection,
      RdbmsDialect dialect,
      String schemaName,
      List<String> tableNames,
      long deadlineNanos)
      throws SQLException {
    DatabaseMetaData metaData = connection.getMetaData();
    List<String> resolvedTableNames =
        tableNames == null || tableNames.isEmpty()
            ? listAllTableNames(metaData, dialect, schemaName)
            : List.copyOf(tableNames);
    List<RdbmsTableMetadata> tables = new ArrayList<>();
    boolean explicitTableNames = tableNames != null && !tableNames.isEmpty();
    for (String tableName : resolvedTableNames) {
      checkDeadline(deadlineNanos, schemaName);
      List<RdbmsColumnMetadata> columns = readColumns(metaData, dialect, schemaName, tableName);
      if (columns.isEmpty()) {
        if (explicitTableNames) {
          throw new SchemaIntrospectionException(
              "Table not found or has no readable columns: schemaName="
                  + LogSanitizer.clean(schemaName)
                  + ", tableName="
                  + LogSanitizer.clean(tableName));
        }
        continue;
      }
      tables.add(new RdbmsTableMetadata(schemaName, tableName, columns));
    }
    return tables;
  }

  private void checkDeadline(long deadlineNanos, String schemaName) {
    if (System.nanoTime() - deadlineNanos > 0) {
      throw new SchemaIntrospectionException(
          "Reading the business database metadata exceeded "
              + readTimeout.toMillis()
              + "ms: schemaName="
              + LogSanitizer.clean(schemaName));
    }
  }

  private List<String> listAllTableNames(
      DatabaseMetaData metaData, RdbmsDialect dialect, String schemaName) throws SQLException {
    List<String> names = new ArrayList<>();
    String catalog = catalogParam(dialect, schemaName);
    String schemaPattern = schemaParam(metaData, dialect, schemaName);
    try (ResultSet resultSet =
        metaData.getTables(catalog, schemaPattern, "%", new String[] {"TABLE"})) {
      while (resultSet.next()) {
        if (belongsToSchema(resultSet, dialect, schemaName)) {
          names.add(resultSet.getString("TABLE_NAME"));
        }
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
    String schemaPattern = schemaParam(metaData, dialect, schemaName);
    // getColumnsのテーブル名はLIKEパターンのため、エスケープして渡し、結果も照合する(R-01)。
    String tablePattern = escapePattern(metaData, tableName);
    List<RdbmsColumnMetadata> columns = new ArrayList<>();
    try (ResultSet resultSet = metaData.getColumns(catalog, schemaPattern, tablePattern, "%")) {
      while (resultSet.next()) {
        if (!belongsToSchema(resultSet, dialect, schemaName)
            || !tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
          continue;
        }
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
    // getPrimaryKeysのスキーマ名・テーブル名は、JDBCの仕様上、パターンではなく、完全一致の指定である(エスケープしない)。
    String catalog = catalogParam(dialect, schemaName);
    String schema = isCatalogBased(dialect) ? null : schemaName;
    Set<String> names = new LinkedHashSet<>();
    try (ResultSet resultSet = metaData.getPrimaryKeys(catalog, schema, tableName)) {
      while (resultSet.next()) {
        names.add(resultSet.getString("COLUMN_NAME"));
      }
    }
    return names;
  }

  private static boolean isCatalogBased(RdbmsDialect dialect) {
    return dialect == RdbmsDialect.MYSQL || dialect == RdbmsDialect.MARIADB;
  }

  /** MySQL/MariaDBはJDBCの{@code catalog}パラメータでスキーマ(データベース)を絞り込む(catalogはパターンではなく、完全一致の指定)。 */
  private static String catalogParam(RdbmsDialect dialect, String schemaName) {
    return isCatalogBased(dialect) ? schemaName : null;
  }

  /** PostgreSQLはJDBCの{@code schema}パラメータ(LIKEパターン)でスキーマを絞り込む。パターン文字は、エスケープして渡す。 */
  private static String schemaParam(
      DatabaseMetaData metaData, RdbmsDialect dialect, String schemaName) throws SQLException {
    return isCatalogBased(dialect) ? null : escapePattern(metaData, schemaName);
  }

  /**
   * 結果の行が、指定したスキーマのものか。スキーマで絞り込むのは、PostgreSQL方式({@code
   * TABLE_SCHEM})だけである(MySQL/MariaDBは、パターンではない{@code catalog}で絞り込み済み)。
   */
  private static boolean belongsToSchema(
      ResultSet resultSet, RdbmsDialect dialect, String schemaName) throws SQLException {
    return isCatalogBased(dialect)
        || schemaName == null
        || schemaName.equalsIgnoreCase(resultSet.getString("TABLE_SCHEM"));
  }

  /**
   * JDBCのLIKEパターンの引数に渡す名前の、パターン文字({@code %}・{@code
   * _})とエスケープ文字を、エスケープする(R-01)。エスケープ文字を持たないドライバでは、そのまま返す(結果の照合が、備えとなる)。
   */
  private static String escapePattern(DatabaseMetaData metaData, String name) throws SQLException {
    if (name == null) {
      return null;
    }
    String escape = metaData.getSearchStringEscape();
    if (escape == null || escape.isEmpty()) {
      return name;
    }
    StringBuilder escaped = new StringBuilder(name.length() + 4);
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '%' || c == '_' || escape.equals(String.valueOf(c))) {
        escaped.append(escape);
      }
      escaped.append(c);
    }
    return escaped.toString();
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
