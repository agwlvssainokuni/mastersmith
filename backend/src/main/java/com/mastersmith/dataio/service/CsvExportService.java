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

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.dataio.csv.CsvColumnDefinitionResolver;
import com.mastersmith.dataio.csv.CsvValueFormatter;
import com.mastersmith.dataio.dto.CsvColumnDefinition;
import com.mastersmith.dataio.exception.CsvExportException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 業務データCSVエクスポート(FR12.1、C13: exportCsv)を実装する(rules.md BR8.1, BR8.2, BR8.8, BR8.10)。
 *
 * <p>業務データ用RDBMSへJDBCカーソル経由(fetchSize指定によるドライバ一括先読みの抑止、 performance-design.md)で対象データを逐次読み取り、Apache
 * Commons CSVの{@link CSVPrinter}で 1行ずつ出力ストリームへ書き込む(全件を一括でメモリへ読み込まない、BR8.10)。列単位の実効READ権限は
 * 呼び出し元が事前に検証済みであることを前提とし、本サービス自身はPermissionEngineへの問い合わせを 行わない(BR8.8)。
 *
 * <p><b>filter/sortの解釈</b>: list-engineの検索条件・ソート順スキーマの詳細はFunctional Design(list-engine
 * Unit)で確定する未解決事項であるため(contract-summary.md Open Questions)、 本サービスは安全側の最小実装として、{@code
 * filter}を列名をキーとした等価条件のANDとして解釈し、 {@code sort}を{@code "columnName"}または{@code
 * "columnName,asc|desc"}形式として解釈する。 いずれも、対象テーブルのエクスポート対象列に含まれない列名は無視する(SQLインジェクション防止。
 * filterの値自体はPreparedStatementのバインド変数として渡すため安全)。
 *
 * <p><b>ストリーミングの前提</b>(BR8.10、レビュー指摘R-03対応): {@code fetchSize}の指定だけでは、対象RDBMSによって
 * 全件がメモリへ先読みされる。そのためエクスポート専用に取得したコネクションを、読み取り専用・自動コミット無効 ({@code
 * setAutoCommit(false)})にして、処理後に元の状態へ戻す。ドライバ別の扱いは次のとおり。
 *
 * <ul>
 *   <li>PostgreSQL: 自動コミット無効のトランザクション内でのみ{@code fetchSize}のカーソルが有効になる(500行ずつ取得)。
 *   <li>MySQL/MariaDB: {@code fetchSize=Integer.MIN_VALUE}を指定し、行単位のストリーミングを有効にする(接続URLの {@code
 *       useCursorFetch}等の指定を要しない)。ストリーミング中は同一コネクションで他のSQLを発行できない。
 *   <li>その他(H2等): {@code fetchSize=500}を指定する。
 * </ul>
 *
 * <p><b>値の形式</b>(BR8.1、レビュー指摘R-02対応): 日付・日時・真偽値・数値は{@link CsvValueFormatter}によりインポートが
 * 受け付ける正規形式へ整形する。SQL中の識別子(スキーマ・テーブル・列名)はドライバが報告する引用符で囲む。
 */
@Service
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class CsvExportService {

  private static final Logger LOG = LoggerFactory.getLogger(CsvExportService.class);

  private static final int FETCH_SIZE = 500;

  private final ConfigEngineApi configEngineApi;
  private final CsvColumnDefinitionResolver columnDefinitionResolver;
  private final DataSource businessDataSource;

  public CsvExportService(
      ConfigEngineApi configEngineApi,
      CsvColumnDefinitionResolver columnDefinitionResolver,
      @Qualifier("businessDataSource") DataSource businessDataSource) {
    this.configEngineApi = configEngineApi;
    this.columnDefinitionResolver = columnDefinitionResolver;
    this.businessDataSource = businessDataSource;
  }

  /**
   * 業務データをCSVとして{@code out}へストリーミング書き込みする。
   *
   * @throws com.mastersmith.config.exception.TableConfigNotFoundException 対象テーブルが存在しない場合
   * @throws CsvExportException 業務データ用RDBMSへのアクセス・出力ストリームへの書き込みに失敗した場合
   */
  public void exportCsv(
      String tableConfigId,
      Map<String, Object> filter,
      String sort,
      List<String> permittedColumnNames,
      OutputStream out) {
    TableConfig tableConfig = configEngineApi.getTableConfigById(tableConfigId);
    List<ColumnConfig> allColumns = configEngineApi.getColumnConfigs(tableConfigId);
    List<CsvColumnDefinition> columns =
        columnDefinitionResolver.resolveForExport(allColumns, permittedColumnNames);
    String[] header = columns.stream().map(CsvColumnDefinition::columnName).toArray(String[]::new);
    Set<String> knownColumnNames = new LinkedHashSet<>(List.of(header));

    try {
      Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
      // BR8.1: UTF-8 BOM付き。Apache Commons CSV自体はBOM書き込みを行わないため、
      // 出力ストリームの先頭にBOM文字を明示的に書き込む(tech-stack-decisions.md)。
      writer.write('\uFEFF');
      CSVFormat format =
          CSVFormat.Builder.create(CSVFormat.DEFAULT)
              .setHeader(header)
              .setRecordSeparator("\r\n")
              .build();
      // DataImportExportApi.exportCsvの契約どおり、呼び出し元の出力ストリームはクローズしない
      // (CSVPrinterをtry-with-resourcesで閉じると、包んでいるWriter経由でoutまで閉じてしまう)。
      // 書き込んだ内容はflushのみ行い、ストリームの後始末は呼び出し元に委ねる。
      CSVPrinter printer = new CSVPrinter(writer, format);
      if (header.length > 0) {
        writeRows(tableConfig, columns, knownColumnNames, filter, sort, printer);
      }
      printer.flush();
    } catch (SQLException e) {
      throw new CsvExportException(
          "Failed to read business data for tableConfigId=" + tableConfigId, e);
    } catch (IOException e) {
      throw new CsvExportException(
          "Failed to write CSV output for tableConfigId=" + tableConfigId, e);
    }
  }

  private void writeRows(
      TableConfig tableConfig,
      List<CsvColumnDefinition> columns,
      Set<String> knownColumnNames,
      Map<String, Object> filter,
      String sort,
      CSVPrinter printer)
      throws SQLException, IOException {
    Map<String, Object> effectiveFilter = filterableEntries(filter, knownColumnNames);
    try (Connection connection = businessDataSource.getConnection()) {
      String quote = SqlIdentifiers.quoteStringOf(connection);
      String sql =
          buildSelectSql(quote, tableConfig, columns, effectiveFilter, sort, knownColumnNames);
      int fetchSize = fetchSizeFor(connection.getMetaData().getDatabaseProductName());
      boolean originalAutoCommit = connection.getAutoCommit();
      boolean originalReadOnly = connection.isReadOnly();
      try {
        // BR8.10: 読み取り専用のトランザクション内(自動コミット無効)で読み取る。
        // PostgreSQLは自動コミット有効時にfetchSizeを無視して全件を先読みするため、この設定が必須である。
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try (PreparedStatement statement =
            connection.prepareStatement(
                sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
          statement.setFetchSize(fetchSize);
          int index = 1;
          for (Object value : effectiveFilter.values()) {
            statement.setObject(index++, value);
          }
          try (ResultSet resultSet = statement.executeQuery()) {
            Object[] values = new Object[columns.size()];
            while (resultSet.next()) {
              for (int i = 0; i < values.length; i++) {
                // 列名ではなく位置で取得する(列名の大文字小文字・別名の差異に依存しない)。
                values[i] =
                    CsvValueFormatter.format(
                        columns.get(i).editorType(), resultSet.getObject(i + 1));
              }
              printer.printRecord(values);
            }
          }
        }
      } finally {
        restoreConnection(connection, originalAutoCommit, originalReadOnly);
      }
    }
  }

  /**
   * エクスポート用に変更したコネクションの状態(読み取り専用・自動コミット)を元へ戻す。コネクションプールへ
   * 返却されるため、失敗しても処理結果(CSVの出力)には影響させず、警告として記録する。
   */
  private static void restoreConnection(
      Connection connection, boolean originalAutoCommit, boolean originalReadOnly) {
    try {
      if (!connection.getAutoCommit()) {
        connection.rollback();
      }
      connection.setAutoCommit(originalAutoCommit);
      connection.setReadOnly(originalReadOnly);
    } catch (SQLException e) {
      LOG.warn("Failed to restore the business data connection state after CSV export", e);
    }
  }

  /**
   * 対象RDBMSのストリーミング取得に必要なfetchSizeを返す。MySQL/MariaDBは、{@code Integer.MIN_VALUE}で
   * 行単位のストリーミングとなる(正の値はuseCursorFetch指定がない限り全件先読みとなる)。
   */
  static int fetchSizeFor(String databaseProductName) {
    String product =
        databaseProductName == null ? "" : databaseProductName.toLowerCase(Locale.ROOT);
    if (product.contains("mysql") || product.contains("mariadb")) {
      return Integer.MIN_VALUE;
    }
    return FETCH_SIZE;
  }

  private static Map<String, Object> filterableEntries(
      Map<String, Object> filter, Set<String> knownColumnNames) {
    if (filter == null || filter.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      if (knownColumnNames.contains(entry.getKey())) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  private static String buildSelectSql(
      String quote,
      TableConfig tableConfig,
      List<CsvColumnDefinition> columns,
      Map<String, Object> effectiveFilter,
      String sort,
      Set<String> knownColumnNames) {
    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(
        columns.stream()
            .map(column -> SqlIdentifiers.quote(quote, column.columnName()))
            .collect(Collectors.joining(", ")));
    sql.append(" FROM ").append(SqlIdentifiers.qualifiedTableName(quote, tableConfig));
    if (!effectiveFilter.isEmpty()) {
      sql.append(" WHERE ");
      boolean first = true;
      for (String column : effectiveFilter.keySet()) {
        if (!first) {
          sql.append(" AND ");
        }
        sql.append(SqlIdentifiers.quote(quote, column)).append(" = ?");
        first = false;
      }
    }
    String orderBy = buildOrderBy(quote, sort, knownColumnNames);
    if (orderBy != null) {
      sql.append(" ORDER BY ").append(orderBy);
    }
    return sql.toString();
  }

  private static String buildOrderBy(String quote, String sort, Set<String> knownColumnNames) {
    if (sort == null || sort.isBlank()) {
      return null;
    }
    String[] parts = sort.split(",", 2);
    String column = parts[0].trim();
    if (!knownColumnNames.contains(column)) {
      return null;
    }
    String direction = parts.length > 1 ? parts[1].trim() : "";
    String sqlDirection = "desc".equalsIgnoreCase(direction) ? "DESC" : "ASC";
    return SqlIdentifiers.quote(quote, column) + " " + sqlDirection;
  }
}
