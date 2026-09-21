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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
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
 * <p><b>コミット時の失敗の扱い</b>(レビュー指摘R-06対応): 検証後〜コミット前に対象行が削除されて{@code UPDATE}が0件更新となった場合、
 * および行単位で検出できないDB制約違反({@link DataIntegrityViolationException})は、トランザクション全体をロールバックし、
 * 行番号付きの行単位エラー({@code notFound}/{@code constraintViolation}、SQL文・ドライバのメッセージは含めない)として {@link
 * ImportResult}で返す。これら以外の予期しない失敗(接続断・SQL誤り等)は、{@code committed=false}の {@link
 * ImportExecutedEvent}を発行したうえで例外をそのまま伝播する。
 *
 * <p><b>CSVヘッダーに存在しない列</b>(レビュー指摘R-04対応): UPDATE行はCSVヘッダーに存在する列のみをSETし、存在しない列の既存値を
 * 維持する(エクスポートがhidden列・権限のない列を出力しないため、エクスポート→編集→再インポートで当該列を破壊しない)。
 * INSERT行は、CSVヘッダーに存在する列のみをINSERT文の列リストへ含める。詳細は{@link CsvRowValidator}を参照する。
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

  /**
   * 特定の列に帰属しない行単位のエラー(コミット時のDB制約違反)に用いる{@link RowError#field()}。{@code RowError}は
   * フィールド名を必須とするため、列名と衝突しない番兵値を用いる。
   */
  static final String ROW_LEVEL_FIELD = "*";

  static final String NOT_FOUND = "notFound";
  static final String CONSTRAINT_VIOLATION = "constraintViolation";

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
   * @throws CsvFormatException アップロードされたファイルがBR8.1のCSV形式としてパースできない場合(空ファイル・ヘッダー行なし・
   *     対象テーブルの列を1つも含まないヘッダーを含む)
   */
  public ImportResult importCsv(String tableConfigId, InputStream file, String actor) {
    TableConfig tableConfig = configEngineApi.getTableConfigById(tableConfigId);
    List<ColumnConfig> allColumns = configEngineApi.getColumnConfigs(tableConfigId);
    List<CsvColumnDefinition> columns = columnDefinitionResolver.resolveForImport(allColumns);
    CsvColumnDefinition primaryKeyColumn =
        columns.stream().filter(CsvColumnDefinition::isPrimaryKey).findFirst().orElse(null);
    String quote = identifierQuote();
    String tableName = SqlIdentifiers.qualifiedTableName(quote, tableConfig);

    List<RowValidationResult> validatedRows =
        readAndValidateRows(tableConfigId, file, columns, primaryKeyColumn, quote, tableName);

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

    int successCount;
    try {
      successCount = commitRows(quote, tableName, columns, primaryKeyColumn, validatedRows);
    } catch (RowCommitFailedException e) {
      // 検証後〜コミット前の状態変化・DB制約違反。トランザクションは全体ロールバック済み(BR8.7)。
      eventPublisher.publishEvent(ImportExecutedEvent.of(tableConfigId, actor, 0, 1, false));
      return ImportResult.rolledBack(List.of(e.toRowError()));
    } catch (RuntimeException e) {
      // 予期しない失敗(接続断・SQL誤り等)。BR8.9は全結果でイベントを発行するため、例外を伝播する前に発行する。
      eventPublisher.publishEvent(ImportExecutedEvent.of(tableConfigId, actor, 0, 0, false));
      throw e;
    }
    eventPublisher.publishEvent(
        ImportExecutedEvent.of(tableConfigId, actor, successCount, 0, true));
    return ImportResult.committed(successCount);
  }

  /** 接続先JDBCドライバが報告する識別子の引用符を取得する(R-06(4))。 */
  private String identifierQuote() {
    String quote =
        businessJdbcTemplate.execute((ConnectionCallback<String>) SqlIdentifiers::quoteStringOf);
    return quote == null ? "" : quote;
  }

  private List<RowValidationResult> readAndValidateRows(
      String tableConfigId,
      InputStream file,
      List<CsvColumnDefinition> columns,
      CsvColumnDefinition primaryKeyColumn,
      String quote,
      String tableName) {
    List<RowValidationResult> rows = new ArrayList<>();
    Predicate<Object> primaryKeyExists =
        value -> primaryKeyColumn != null && rowExists(quote, tableName, primaryKeyColumn, value);
    // CSVのパース由来の失敗のみを形式エラー(ファイル全体のエラー、BR8.1)へ変換する。
    // 行の検証(型変換・validationRule)や主キー存在確認(DBアクセス)由来の例外は、形式エラーに誤分類しないよう
    // catchの対象外とする(レビュー指摘R-01対応)。
    try (Reader reader = bomAwareReader(file);
        CSVParser parser = openParser(reader, tableConfigId)) {
      List<String> header = parser.getHeaderNames();
      requireKnownColumnInHeader(tableConfigId, header, columns);
      Iterator<CSVRecord> iterator = parser.iterator();
      int rowNumber = 0;
      while (hasNextRecord(iterator, tableConfigId)) {
        CSVRecord record = nextRecord(iterator, tableConfigId);
        rowNumber++;
        rows.add(
            csvRowValidator.validateRow(
                rowNumber, toRawValues(record, header), columns, primaryKeyExists));
      }
    } catch (IOException e) {
      throw new CsvFormatException("Failed to read CSV file for tableConfigId=" + tableConfigId, e);
    }
    return rows;
  }

  private static CSVParser openParser(Reader reader, String tableConfigId) throws IOException {
    CSVFormat format =
        CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader().setSkipHeaderRecord(true).build();
    try {
      return CSVParser.parse(reader, format);
    } catch (IllegalArgumentException e) {
      // ヘッダー行の不備(空の列名など)。
      throw new CsvFormatException(
          "Invalid CSV header for tableConfigId=" + tableConfigId + ": " + e.getMessage(), e);
    }
  }

  private static boolean hasNextRecord(Iterator<CSVRecord> iterator, String tableConfigId) {
    try {
      return iterator.hasNext();
    } catch (UncheckedIOException e) {
      throw new CsvFormatException(
          "Failed to parse CSV file for tableConfigId=" + tableConfigId, e.getCause());
    }
  }

  private static CSVRecord nextRecord(Iterator<CSVRecord> iterator, String tableConfigId) {
    try {
      return iterator.next();
    } catch (UncheckedIOException e) {
      throw new CsvFormatException(
          "Failed to parse CSV file for tableConfigId=" + tableConfigId, e.getCause());
    }
  }

  /**
   * BR8.1: 1行目はカラム名のヘッダー行である。対象テーブルの列を1つも含まないヘッダー(空ファイル・ヘッダー行のないファイルを含む)は、
   * ヘッダー行として解釈できないためファイル形式エラーとする。
   */
  private static void requireKnownColumnInHeader(
      String tableConfigId, List<String> header, List<CsvColumnDefinition> columns) {
    Set<String> known =
        columns.stream().map(CsvColumnDefinition::columnName).collect(Collectors.toSet());
    if (header.stream().noneMatch(known::contains)) {
      throw new CsvFormatException(
          "CSV header has no column of the target table for tableConfigId=" + tableConfigId);
    }
  }

  /**
   * CSVの1レコードを、ヘッダーに存在する列名をキーとした生の文字列値へ変換する。値の数がヘッダーより少ない行の欠けた列は
   * 空セルとして扱う(ヘッダーに存在する列を「CSVに存在する列」とみなすため、欠けた列を未存在にはしない)。
   */
  private static Map<String, String> toRawValues(CSVRecord record, List<String> header) {
    Map<String, String> rawValues = new LinkedHashMap<>();
    for (String name : header) {
      rawValues.put(name, record.isSet(name) ? record.get(name) : "");
    }
    return rawValues;
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

  /**
   * 主キー値({@link CsvRowValidator}が型変換済みの値)で対象行の存在を確認する。UPDATE行ごとに1回のSELECTを発行する
   * (DB側の比較規則(照合順序等)で判定するため、Java側の値比較による一括照合は採用していない)。
   */
  private boolean rowExists(
      String quote, String tableName, CsvColumnDefinition primaryKeyColumn, Object value) {
    String sql =
        "SELECT 1 FROM "
            + tableName
            + " WHERE "
            + SqlIdentifiers.quote(quote, primaryKeyColumn.columnName())
            + " = ?";
    List<Integer> rows = businessJdbcTemplate.queryForList(sql, Integer.class, value);
    return !rows.isEmpty();
  }

  private int commitRows(
      String quote,
      String tableName,
      List<CsvColumnDefinition> columns,
      CsvColumnDefinition primaryKeyColumn,
      List<RowValidationResult> validatedRows) {
    Integer successCount =
        businessTransactionTemplate.execute(
            status -> {
              int count = 0;
              for (RowValidationResult row : validatedRows) {
                int rowNumber = row.report().rowNumber();
                try {
                  if (row.report().operation() == ImportOperation.INSERT) {
                    executeInsert(quote, tableName, columns, row.convertedValues());
                  } else if (!executeUpdate(
                      quote, tableName, columns, primaryKeyColumn, row.convertedValues())) {
                    // 検証後〜コミット前に対象行が削除された。0件更新を成功に数えず、全体をロールバックする。
                    throw new RowCommitFailedException(
                        rowNumber, primaryKeyColumn.columnName(), NOT_FOUND, null);
                  }
                } catch (DataIntegrityViolationException e) {
                  throw new RowCommitFailedException(
                      rowNumber, ROW_LEVEL_FIELD, CONSTRAINT_VIOLATION, e);
                }
                count++;
              }
              return count;
            });
    return successCount == null ? 0 : successCount;
  }

  /**
   * INSERT対象列を組み立てる。CSVヘッダーに存在しない列はINSERT文の列リストから外し(対象RDBMSの既定値が適用される)、
   * 主キー列がCSV上で空欄(convertedValue=null、BR8.3の新規行判定)の場合も、その主キー列自体を除外する。これにより、
   * 対象RDBMSの自動採番(IDENTITY/AUTO_INCREMENT/serial)列を主キーとするテーブルでも、CSV側で主キー値を明示しない
   * 新規行の追加が成立する(主キー値を明示したCSVでは、そのまま自然キーとしてINSERT対象に含める)。
   */
  private void executeInsert(
      String quote,
      String tableName,
      List<CsvColumnDefinition> columns,
      Map<String, Object> values) {
    List<CsvColumnDefinition> insertColumns =
        columns.stream()
            .filter(c -> values.containsKey(c.columnName()))
            .filter(c -> !(c.isPrimaryKey() && values.get(c.columnName()) == null))
            .toList();
    if (insertColumns.isEmpty()) {
      businessJdbcTemplate.update("INSERT INTO " + tableName + " DEFAULT VALUES");
      return;
    }
    String columnList =
        insertColumns.stream()
            .map(c -> SqlIdentifiers.quote(quote, c.columnName()))
            .collect(Collectors.joining(", "));
    String placeholders = insertColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
    String sql = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";
    Object[] args = insertColumns.stream().map(c -> values.get(c.columnName())).toArray();
    businessJdbcTemplate.update(sql, args);
  }

  /**
   * CSVヘッダーに存在する非主キー列のみをSETしてUPDATEする(存在しない列の既存値は維持する、R-04)。
   *
   * @return 対象行を更新できた場合はtrue、対象行が存在せず0件更新だった場合はfalse(SET対象の列がなく、UPDATE文を発行しなかった場合は、
   *     検証時に存在確認済みのためtrue)
   */
  private boolean executeUpdate(
      String quote,
      String tableName,
      List<CsvColumnDefinition> columns,
      CsvColumnDefinition primaryKeyColumn,
      Map<String, Object> values) {
    List<CsvColumnDefinition> setColumns =
        columns.stream()
            .filter(c -> !c.isPrimaryKey())
            .filter(c -> values.containsKey(c.columnName()))
            .toList();
    if (setColumns.isEmpty()) {
      return true;
    }
    String setClause =
        setColumns.stream()
            .map(c -> SqlIdentifiers.quote(quote, c.columnName()) + " = ?")
            .collect(Collectors.joining(", "));
    String sql =
        "UPDATE "
            + tableName
            + " SET "
            + setClause
            + " WHERE "
            + SqlIdentifiers.quote(quote, primaryKeyColumn.columnName())
            + " = ?";
    Object[] args = new Object[setColumns.size() + 1];
    for (int i = 0; i < setColumns.size(); i++) {
      args[i] = values.get(setColumns.get(i).columnName());
    }
    args[setColumns.size()] = values.get(primaryKeyColumn.columnName());
    return businessJdbcTemplate.update(sql, args) > 0;
  }

  /** コミット中に検出した行単位の失敗。{@link TransactionTemplate}が全体をロールバックしたうえで、呼び出し元へ伝播する。 */
  private static final class RowCommitFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int row;
    private final String field;
    private final String message;

    RowCommitFailedException(int row, String field, String message, Throwable cause) {
      super("Import commit failed at row " + row + ": " + message, cause);
      this.row = row;
      this.field = field;
      this.message = message;
    }

    RowError toRowError() {
      return new RowError(row, field, message);
    }
  }
}
