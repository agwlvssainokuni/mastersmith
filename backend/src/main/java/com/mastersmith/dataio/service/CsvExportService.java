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
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 業務データCSVエクスポート(FR12.1、C13: exportCsv)を実装する(rules.md BR8.1, BR8.2, BR8.8, BR8.10)。
 *
 * <p>業務データ用RDBMSへJDBCカーソル経由(fetchSize指定によるドライバ一括先読みの抑止、
 * performance-design.md)で対象データを逐次読み取り、Apache Commons CSVの{@link CSVPrinter}で
 * 1行ずつ出力ストリームへ書き込む(全件を一括でメモリへ読み込まない、BR8.10)。列単位の実効READ権限は
 * 呼び出し元が事前に検証済みであることを前提とし、本サービス自身はPermissionEngineへの問い合わせを
 * 行わない(BR8.8)。
 *
 * <p><b>filter/sortの解釈</b>: list-engineの検索条件・ソート順スキーマの詳細はFunctional
 * Design(list-engine Unit)で確定する未解決事項であるため(contract-summary.md Open Questions)、
 * 本サービスは安全側の最小実装として、{@code filter}を列名をキーとした等価条件のANDとして解釈し、
 * {@code sort}を{@code "columnName"}または{@code "columnName,asc|desc"}形式として解釈する。
 * いずれも、対象テーブルのエクスポート対象列に含まれない列名は無視する(SQLインジェクション防止。
 * filterの値自体はPreparedStatementのバインド変数として渡すため安全)。
 */
@Service
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class CsvExportService {

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
          CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader(header).setRecordSeparator("\r\n").build();
      try (CSVPrinter printer = new CSVPrinter(writer, format)) {
        if (header.length > 0) {
          writeRows(tableConfig, header, knownColumnNames, filter, sort, printer);
        }
        printer.flush();
      }
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
      String[] header,
      Set<String> knownColumnNames,
      Map<String, Object> filter,
      String sort,
      CSVPrinter printer)
      throws SQLException, IOException {
    Map<String, Object> effectiveFilter = filterableEntries(filter, knownColumnNames);
    String sql = buildSelectSql(tableConfig, header, effectiveFilter, sort, knownColumnNames);
    try (Connection connection = businessDataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
      // BR8.10: fetchSizeを小さい値に明示し、ドライバの一括先読みによるメモリ膨張を防ぐ。
      statement.setFetchSize(FETCH_SIZE);
      int index = 1;
      for (Object value : effectiveFilter.values()) {
        statement.setObject(index++, value);
      }
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          Object[] values = new Object[header.length];
          for (int i = 0; i < header.length; i++) {
            values[i] = resultSet.getObject(header[i]);
          }
          printer.printRecord(values);
        }
      }
    }
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
      TableConfig tableConfig,
      String[] header,
      Map<String, Object> effectiveFilter,
      String sort,
      Set<String> knownColumnNames) {
    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(String.join(", ", header));
    sql.append(" FROM ").append(tableConfig.getSchemaName()).append('.').append(tableConfig.getTableName());
    if (!effectiveFilter.isEmpty()) {
      sql.append(" WHERE ");
      boolean first = true;
      for (String column : effectiveFilter.keySet()) {
        if (!first) {
          sql.append(" AND ");
        }
        sql.append(column).append(" = ?");
        first = false;
      }
    }
    String orderBy = buildOrderBy(sort, knownColumnNames);
    if (orderBy != null) {
      sql.append(" ORDER BY ").append(orderBy);
    }
    return sql.toString();
  }

  private static String buildOrderBy(String sort, Set<String> knownColumnNames) {
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
    return column + " " + sqlDirection;
  }
}
