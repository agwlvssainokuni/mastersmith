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
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.dataio.config.BusinessDataSourceConfig;
import com.mastersmith.dataio.csv.CsvColumnDefinitionResolver;
import com.mastersmith.dataio.csv.CsvRowValidator;
import com.mastersmith.dataio.csv.RowValidationResult;
import com.mastersmith.dataio.dto.CsvColumnDefinition;
import com.mastersmith.dataio.dto.ImportOperation;
import com.mastersmith.dataio.dto.ImportResult;
import com.mastersmith.dataio.dto.RowError;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import com.mastersmith.dataio.exception.CsvFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 業務データCSVインポート(FR12.1、C13: importCsv)を実装する(rules.md BR8.3〜BR8.7, BR8.9, BR8.10)。
 *
 * <p>CSVファイルを1回のストリーミング走査で1行ずつ読み取りながら{@link CsvRowValidator}を適用し、検証済みの結果を一時バッファ(メモリ上のリスト)へ
 * 蓄積する(BR8.10)。全行の読み取り完了後、1件でも{@code outcome=INVALID}があれば何も反映せず{@link
 * ImportResult}を返す(BR8.7)。全行が有効な場合のみ、 {@link
 * TransactionTemplate}経由で単一のトランザクション内でINSERT/UPDATEを実行する。
 *
 * <p><b>{@code @Transactional}ではなく{@link TransactionTemplate}を用いる理由</b>: 本メソッドは検証(トランザクション対象外)と
 * コミット(トランザクション対象)を同一クラスの1メソッド呼び出しの中で行き来する。{@code @Transactional}はSpring
 * AOPプロキシ経由の外部呼び出しにのみ適用され、同一クラス内の自己呼び出し(self-invocation)には適用されないため、
 * プライベートメソッド分割による{@code @Transactional}では意図したトランザクション境界を得られない。{@link
 * TransactionTemplate}によるプログラム的トランザクション管理はこの制約を受けず、業務データ用RDBMS専用の{@link
 * PlatformTransactionManager}(businessTransactionManager)を明示的に用いる。
 *
 * <p>楽観ロック対象列の有無に関わらず競合検出は行わない(BR8.4、常に後勝ち)。処理完了時(コミット・全体ロールバック いずれも)、{@link
 * ImportExecutedEvent}をSpringの{@link ApplicationEventPublisher}でfire-and-forget発行する(BR8.9)。
 *
 * <p>{@link BusinessDataSourceConfig}と同一のプロパティで条件付き登録する。業務データ用RDBMSの接続先が未確定の開発環境 ({@code
 * mastersmith.business-datasource.enabled=false}、既定)では、本サービスが依存する{@code
 * businessJdbcTemplate}・{@code businessTransactionManager}が生成されないため、本サービス自体も生成しないことで、
 * アプリケーションコンテキスト全体の起動に影響を与えない(BusinessDataSourceConfigの設計意図と同一)。
 */
@Service
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class CsvImportService {

  private final ConfigEngineApi configEngineApi;
  private final CsvColumnDefinitionResolver columnDefinitionResolver;
  private final CsvRowValidator csvRowValidator;
  private final JdbcTemplate businessJdbcTemplate;
  private final TransactionTemplate businessTransactionTemplate;
  private final ApplicationEventPublisher eventPublisher;

  public CsvImportService(
      ConfigEngineApi configEngineApi,
      CsvColumnDefinitionResolver columnDefinitionResolver,
      CsvRowValidator csvRowValidator,
      @Qualifier("businessJdbcTemplate") JdbcTemplate businessJdbcTemplate,
      @Qualifier("businessTransactionManager")
          PlatformTransactionManager businessTransactionManager,
      ApplicationEventPublisher eventPublisher) {
    this.configEngineApi = configEngineApi;
    this.columnDefinitionResolver = columnDefinitionResolver;
    this.csvRowValidator = csvRowValidator;
    this.businessJdbcTemplate = businessJdbcTemplate;
    this.businessTransactionTemplate = new TransactionTemplate(businessTransactionManager);
    this.eventPublisher = eventPublisher;
  }

  /**
   * 業務データをCSVファイルからインポートする。
   *
   * @throws com.mastersmith.config.exception.TableConfigNotFoundException 対象テーブルが存在しない場合
   * @throws CsvFormatException アップロードされたファイルがBR8.1のCSV形式としてパースできない場合
   */
  public ImportResult importCsv(String tableConfigId, InputStream file, String actor) {
    TableConfig tableConfig = configEngineApi.getTableConfigById(tableConfigId);
    List<ColumnConfig> allColumns = configEngineApi.getColumnConfigs(tableConfigId);
    List<CsvColumnDefinition> columns = columnDefinitionResolver.resolveForImport(allColumns);
    CsvColumnDefinition primaryKeyColumn =
        columns.stream().filter(CsvColumnDefinition::isPrimaryKey).findFirst().orElse(null);
    String tableName = physicalTableName(tableConfig);

    List<RowValidationResult> validatedRows =
        readAndValidateRows(tableConfigId, file, columns, primaryKeyColumn, tableName);

    List<RowError> errors =
        validatedRows.stream()
            .filter(row -> !row.isValid())
            .flatMap(row -> row.report().errors().stream())
            .toList();

    if (!errors.isEmpty()) {
      // BR8.7: 1件でもINVALIDがあれば何も反映しない。
      eventPublisher.publishEvent(
          ImportExecutedEvent.of(tableConfigId, actor, 0, errors.size(), false));
      return ImportResult.rolledBack(errors);
    }

    int successCount = commitRows(tableName, columns, primaryKeyColumn, validatedRows);
    eventPublisher.publishEvent(
        ImportExecutedEvent.of(tableConfigId, actor, successCount, 0, true));
    return ImportResult.committed(successCount);
  }

  private List<RowValidationResult> readAndValidateRows(
      String tableConfigId,
      InputStream file,
      List<CsvColumnDefinition> columns,
      CsvColumnDefinition primaryKeyColumn,
      String tableName) {
    List<RowValidationResult> rows = new ArrayList<>();
    CSVFormat format =
        CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader().setSkipHeaderRecord(true).build();
    try (Reader reader = bomAwareReader(file);
        CSVParser parser = CSVParser.parse(reader, format)) {
      int rowNumber = 0;
      for (CSVRecord record : parser) {
        rowNumber++;
        Map<String, String> rawValues = record.toMap();
        Predicate<String> primaryKeyExists =
            value -> primaryKeyColumn != null && rowExists(tableName, primaryKeyColumn, value);
        rows.add(csvRowValidator.validateRow(rowNumber, rawValues, columns, primaryKeyExists));
      }
    } catch (IOException | RuntimeException e) {
      throw new CsvFormatException(
          "Failed to parse CSV file for tableConfigId=" + tableConfigId, e);
    }
    return rows;
  }

  private static Reader bomAwareReader(InputStream in) throws IOException {
    PushbackInputStream pushback = new PushbackInputStream(in, 3);
    byte[] head = new byte[3];
    int read = pushback.read(head, 0, 3);
    boolean hasBom =
        read == 3
            && (head[0] & 0xFF) == 0xEF
            && (head[1] & 0xFF) == 0xBB
            && (head[2] & 0xFF) == 0xBF;
    if (!hasBom && read > 0) {
      pushback.unread(head, 0, read);
    }
    return new InputStreamReader(pushback, StandardCharsets.UTF_8);
  }

  private boolean rowExists(String tableName, CsvColumnDefinition primaryKeyColumn, String value) {
    String sql = "SELECT 1 FROM " + tableName + " WHERE " + primaryKeyColumn.columnName() + " = ?";
    Object bindValue = convertForLookup(primaryKeyColumn.editorType(), value);
    List<Integer> rows = businessJdbcTemplate.queryForList(sql, Integer.class, bindValue);
    return !rows.isEmpty();
  }

  /**
   * 主キー存在チェックのバインド変数を、対象RDBMSが列の実際の型と暗黙変換なしに比較できるよう、{@code editorType}に応じた型へ 変換する({@link
   * CsvRowValidator}の型変換ロジックと同等。文字列のまま比較すると、対象RDBMSによっては型不一致で 比較が成立しない、またはエラーとなる場合があるため)。
   */
  private static Object convertForLookup(EditorType editorType, String raw) {
    return switch (editorType) {
      case INTEGER -> Long.parseLong(raw);
      case DECIMAL -> new BigDecimal(raw);
      default -> raw;
    };
  }

  private int commitRows(
      String tableName,
      List<CsvColumnDefinition> columns,
      CsvColumnDefinition primaryKeyColumn,
      List<RowValidationResult> validatedRows) {
    Integer successCount =
        businessTransactionTemplate.execute(
            status -> {
              int count = 0;
              for (RowValidationResult row : validatedRows) {
                if (row.report().operation() == ImportOperation.INSERT) {
                  executeInsert(tableName, columns, row.convertedValues());
                } else {
                  executeUpdate(tableName, columns, primaryKeyColumn, row.convertedValues());
                }
                count++;
              }
              return count;
            });
    return successCount == null ? 0 : successCount;
  }

  /**
   * INSERT対象列を組み立てる。主キー列がCSV上で空欄(convertedValue=null、BR8.3の新規行判定)の場合、その主キー列自体を
   * INSERT文の列リストから除外する。これにより、対象RDBMSの自動採番(IDENTITY/AUTO_INCREMENT/serial)列を主キーとする
   * テーブルでも、CSV側で主キー値を明示しない新規行の追加が成立する(主キー値を明示したCSVでは、そのまま自然キーとして INSERT対象に含める)。
   */
  private void executeInsert(
      String tableName, List<CsvColumnDefinition> columns, Map<String, Object> values) {
    List<CsvColumnDefinition> insertColumns =
        columns.stream()
            .filter(c -> !(c.isPrimaryKey() && values.get(c.columnName()) == null))
            .toList();
    String columnList =
        insertColumns.stream()
            .map(CsvColumnDefinition::columnName)
            .collect(Collectors.joining(", "));
    String placeholders = insertColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
    String sql = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";
    Object[] args = insertColumns.stream().map(c -> values.get(c.columnName())).toArray();
    businessJdbcTemplate.update(sql, args);
  }

  private void executeUpdate(
      String tableName,
      List<CsvColumnDefinition> columns,
      CsvColumnDefinition primaryKeyColumn,
      Map<String, Object> values) {
    List<CsvColumnDefinition> nonPrimaryKeyColumns =
        columns.stream().filter(c -> !c.isPrimaryKey()).toList();
    String setClause =
        nonPrimaryKeyColumns.stream()
            .map(c -> c.columnName() + " = ?")
            .collect(Collectors.joining(", "));
    String sql =
        "UPDATE "
            + tableName
            + " SET "
            + setClause
            + " WHERE "
            + primaryKeyColumn.columnName()
            + " = ?";
    Object[] args = new Object[nonPrimaryKeyColumns.size() + 1];
    for (int i = 0; i < nonPrimaryKeyColumns.size(); i++) {
      args[i] = values.get(nonPrimaryKeyColumns.get(i).columnName());
    }
    args[nonPrimaryKeyColumns.size()] = values.get(primaryKeyColumn.columnName());
    businessJdbcTemplate.update(sql, args);
  }

  private static String physicalTableName(TableConfig tableConfig) {
    return tableConfig.getSchemaName() + "." + tableConfig.getTableName();
  }
}
