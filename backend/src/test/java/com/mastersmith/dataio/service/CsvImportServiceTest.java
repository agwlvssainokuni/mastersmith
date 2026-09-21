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
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.dataio.csv.CsvColumnDefinitionResolver;
import com.mastersmith.dataio.csv.CsvRowValidator;
import com.mastersmith.dataio.csv.RowValidationResult;
import com.mastersmith.dataio.dto.CsvColumnDefinition;
import com.mastersmith.dataio.dto.ImportOperation;
import com.mastersmith.dataio.dto.ImportResult;
import com.mastersmith.dataio.dto.RowError;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import com.mastersmith.dataio.exception.CsvFormatException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link CsvImportService}の統合テスト(rules.md BR8.3〜BR8.7, BR8.9, BR8.10)。
 *
 * <p>{@link ConfigEngineApi}はMockitoでモックし、業務データ用RDBMSは実際のH2インメモリDBを用いて、実際のテーブルに対する
 * INSERT/UPDATE、全体ロールバック(BR8.7)、後勝ち上書き(BR8.4)、{@link ImportExecutedEvent}発行(BR8.9)を検証する。
 * イベント発行の検証はSpringの{@link ApplicationEvents}アサーション機構を用いる。
 *
 * <p>Spring Bootの全体起動は伴わず、{@code @SpringJUnitConfig}で必要なBeanのみを組み立てる(独立した組込みH2を用いる)。
 * 主キー不正値(R-01)、CSVヘッダーに存在しない列(R-04)、コミット時の失敗(R-06)、ファイル形式エラー(BR8.1)、 識別子の引用符を含む。
 */
@SpringJUnitConfig(classes = CsvImportServiceTest.TestConfig.class)
@RecordApplicationEvents
class CsvImportServiceTest {

  private static final String TABLE_CONFIG_ID = "widgets";

  @Configuration
  static class TestConfig {

    @Bean
    DataSource businessDataSource() {
      return DataSourceBuilder.create()
          .url("jdbc:h2:mem:csv-import-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
          .driverClassName("org.h2.Driver")
          .username("sa")
          .password("")
          .build();
    }

    @Bean
    PlatformTransactionManager businessTransactionManager(
        @Qualifier("businessDataSource") DataSource businessDataSource) {
      return new DataSourceTransactionManager(businessDataSource);
    }

    @Bean
    JdbcTemplate businessJdbcTemplate(
        @Qualifier("businessDataSource") DataSource businessDataSource) {
      return new JdbcTemplate(businessDataSource);
    }

    @Bean
    CsvColumnDefinitionResolver csvColumnDefinitionResolver() {
      return new CsvColumnDefinitionResolver();
    }

    @Bean
    CsvRowValidator csvRowValidator() {
      return new CsvRowValidator();
    }

    @Bean
    CsvImportService csvImportService(
        ConfigEngineApi configEngineApi,
        CsvColumnDefinitionResolver csvColumnDefinitionResolver,
        CsvRowValidator csvRowValidator,
        @Qualifier("businessJdbcTemplate") JdbcTemplate businessJdbcTemplate,
        @Qualifier("businessTransactionManager")
            PlatformTransactionManager businessTransactionManager,
        ApplicationEventPublisher eventPublisher) {
      return new CsvImportService(
          configEngineApi,
          csvColumnDefinitionResolver,
          csvRowValidator,
          businessJdbcTemplate,
          businessTransactionManager,
          eventPublisher);
    }
  }

  @MockitoBean private ConfigEngineApi configEngineApi;

  @Autowired private CsvImportService csvImportService;

  @Autowired
  @Qualifier("businessJdbcTemplate")
  private JdbcTemplate businessJdbcTemplate;

  @Autowired private ApplicationEvents events;

  @BeforeEach
  void setUp() {
    businessJdbcTemplate.execute("DROP TABLE IF EXISTS PUBLIC.WIDGETS");
    businessJdbcTemplate.execute(
        "CREATE TABLE PUBLIC.WIDGETS ("
            + "ID INT AUTO_INCREMENT PRIMARY KEY, "
            + "NAME VARCHAR(100) NOT NULL, "
            + "QTY INT, "
            + "MEMO VARCHAR(100))");

    TableConfig tableConfig = new TableConfig("PUBLIC", "WIDGETS");
    when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID)).thenReturn(tableConfig);
    when(configEngineApi.getColumnConfigs(TABLE_CONFIG_ID))
        .thenReturn(List.of(idColumn(), nameColumn(), qtyColumn(), memoColumn()));
  }

  private static ColumnConfig memoColumn() {
    ColumnConfig column = new ColumnConfig(TABLE_CONFIG_ID, "MEMO", EditorType.TEXT);
    column.setDisplayOrder(3);
    return column;
  }

  private static ColumnConfig idColumn() {
    ColumnConfig column = new ColumnConfig(TABLE_CONFIG_ID, "ID", EditorType.INTEGER, true);
    column.setDisplayOrder(0);
    return column;
  }

  private static ColumnConfig nameColumn() {
    ColumnConfig column = new ColumnConfig(TABLE_CONFIG_ID, "NAME", EditorType.TEXT);
    column.setDisplayOrder(1);
    column.setValidationRule(ValidationRule.builder().rule("required", true).build());
    return column;
  }

  private static ColumnConfig qtyColumn() {
    ColumnConfig column = new ColumnConfig(TABLE_CONFIG_ID, "QTY", EditorType.INTEGER);
    column.setDisplayOrder(2);
    return column;
  }

  private static InputStream csv(String... lines) {
    String content = "\uFEFF" + String.join("\r\n", lines) + "\r\n";
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  private static InputStream rawCsv(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  private List<Map<String, Object>> widgets() {
    return businessJdbcTemplate.queryForList(
        "SELECT ID, NAME, QTY, MEMO FROM PUBLIC.WIDGETS ORDER BY ID");
  }

  private ImportExecutedEvent singleEvent() {
    assertThat(events.stream(ImportExecutedEvent.class)).hasSize(1);
    return events.stream(ImportExecutedEvent.class).findFirst().orElseThrow();
  }

  @Test
  void insertsAndUpdatesAllRowsWhenAllRowsAreValid() {
    businessJdbcTemplate.update("INSERT INTO PUBLIC.WIDGETS (NAME, QTY) VALUES ('old-item', 5)");

    InputStream file = csv("ID,NAME,QTY", ",new-item,10", "1,updated-item,20");

    ImportResult result = csvImportService.importCsv(TABLE_CONFIG_ID, file, "user-1");

    assertThat(result.successCount()).isEqualTo(2);
    assertThat(result.errors()).isEmpty();

    List<Map<String, Object>> rows =
        businessJdbcTemplate.queryForList("SELECT ID, NAME, QTY FROM PUBLIC.WIDGETS ORDER BY ID");
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("NAME", "updated-item").containsEntry("QTY", 20);
    assertThat(rows.get(1)).containsEntry("NAME", "new-item").containsEntry("QTY", 10);

    assertThat(events.stream(ImportExecutedEvent.class)).hasSize(1);
    ImportExecutedEvent event = events.stream(ImportExecutedEvent.class).findFirst().orElseThrow();
    assertThat(event.tableConfigId()).isEqualTo(TABLE_CONFIG_ID);
    assertThat(event.actor()).isEqualTo("user-1");
    assertThat(event.successCount()).isEqualTo(2);
    assertThat(event.errorCount()).isEqualTo(0);
    assertThat(event.committed()).isTrue();
  }

  @Test
  void rollsBackEverythingWhenAtLeastOneRowIsInvalid() {
    businessJdbcTemplate.update("INSERT INTO PUBLIC.WIDGETS (NAME, QTY) VALUES ('old-item', 5)");

    InputStream file =
        csv(
            "ID,NAME,QTY",
            ",would-succeed,10",
            "1,,20", // NAME required違反
            "999,also-invalid,1" // 存在しない主キーへのUPDATE
            );

    ImportResult result = csvImportService.importCsv(TABLE_CONFIG_ID, file, "user-1");

    assertThat(result.successCount()).isEqualTo(0);
    assertThat(result.errors()).hasSize(2);

    // BR8.7: 成功見込みだった行も含め、何も反映されない。
    List<Map<String, Object>> rows =
        businessJdbcTemplate.queryForList("SELECT ID, NAME, QTY FROM PUBLIC.WIDGETS ORDER BY ID");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("NAME", "old-item").containsEntry("QTY", 5);

    assertThat(events.stream(ImportExecutedEvent.class)).hasSize(1);
    ImportExecutedEvent event = events.stream(ImportExecutedEvent.class).findFirst().orElseThrow();
    assertThat(event.successCount()).isEqualTo(0);
    assertThat(event.errorCount()).isEqualTo(2);
    assertThat(event.committed()).isFalse();
  }

  @Test
  void updateOverwritesExistingRowWithoutOptimisticLockConflictDetection() {
    businessJdbcTemplate.update(
        "INSERT INTO PUBLIC.WIDGETS (NAME, QTY) VALUES ('concurrently-edited', 5)");

    InputStream file = csv("ID,NAME,QTY", "1,overwritten-by-import,999");

    ImportResult result = csvImportService.importCsv(TABLE_CONFIG_ID, file, "user-1");

    // BR8.4: 楽観ロック対象列の有無に関わらず競合検出は行わない。常に後勝ち。
    assertThat(result.successCount()).isEqualTo(1);
    List<Map<String, Object>> rows =
        businessJdbcTemplate.queryForList("SELECT NAME, QTY FROM PUBLIC.WIDGETS WHERE ID = 1");
    assertThat(rows.get(0))
        .containsEntry("NAME", "overwritten-by-import")
        .containsEntry("QTY", 999);
  }

  // ---- R-01: 主キー不正値は行単位エラー。DBエラーは形式エラーに誤分類しない ----

  @Test
  void invalidPrimaryKeyValuesAreRowLevelErrorsAndAllRowsAreCollected() {
    businessJdbcTemplate.update("INSERT INTO PUBLIC.WIDGETS (NAME, QTY) VALUES ('old-item', 5)");

    ImportResult result =
        csvImportService.importCsv(
            TABLE_CONFIG_ID,
            csv(
                "ID,NAME,QTY",
                "abc,first,1", // 数値でない主キー
                ",ok,2",
                "1,,3", // NAME required違反
                "99999999999999999999,overflow,4"), // Long範囲を超える主キー
            "user-1");

    assertThat(result.successCount()).isEqualTo(0);
    assertThat(result.errors())
        .containsExactly(
            new RowError(1, "ID", "typeMismatch"),
            new RowError(3, "NAME", "required"),
            new RowError(4, "ID", "typeMismatch"));
    assertThat(widgets()).hasSize(1);
    ImportExecutedEvent event = singleEvent();
    assertThat(event.committed()).isFalse();
    assertThat(event.errorCount()).isEqualTo(3);
  }

  @Test
  void databaseFailureDuringExistenceCheckIsNotReportedAsCsvFormatError() {
    ColumnConfig ghostKey = new ColumnConfig(TABLE_CONFIG_ID, "GHOST_ID", EditorType.INTEGER, true);
    ghostKey.setDisplayOrder(0);
    when(configEngineApi.getColumnConfigs(TABLE_CONFIG_ID))
        .thenReturn(List.of(ghostKey, nameColumn()));

    // 物理テーブルにGHOST_ID列がなく、存在確認のSELECTがSQLエラーになる(設定と実DBの不整合)。
    assertThatThrownBy(
            () ->
                csvImportService.importCsv(TABLE_CONFIG_ID, csv("GHOST_ID,NAME", "1,a"), "user-1"))
        .isInstanceOf(BadSqlGrammarException.class)
        .isNotInstanceOf(CsvFormatException.class);
  }

  // ---- R-04: CSVヘッダーに存在しない列・空セルの扱い ----

  @Test
  void updateKeepsColumnsAbsentFromCsvHeader() {
    businessJdbcTemplate.update(
        "INSERT INTO PUBLIC.WIDGETS (NAME, QTY, MEMO) VALUES ('old-item', 5, 'keep-me')");

    // QTY・MEMOはCSVヘッダーにない(エクスポートで出力されなかった列の想定)。NAMEのみを更新する。
    ImportResult result =
        csvImportService.importCsv(TABLE_CONFIG_ID, csv("ID,NAME", "1,renamed"), "user-1");

    assertThat(result.successCount()).isEqualTo(1);
    assertThat(widgets().get(0))
        .containsEntry("NAME", "renamed")
        .containsEntry("QTY", 5)
        .containsEntry("MEMO", "keep-me");
  }

  @Test
  void requiredIsNotEnforcedOnUpdateForColumnsAbsentFromHeader() {
    businessJdbcTemplate.update("INSERT INTO PUBLIC.WIDGETS (NAME, QTY) VALUES ('old-item', 5)");

    // NAME(required)はヘッダーにない。UPDATEでは既存値を維持し、requiredエラーにしない。
    ImportResult result =
        csvImportService.importCsv(TABLE_CONFIG_ID, csv("ID,QTY", "1,9"), "user-1");

    assertThat(result.errors()).isEmpty();
    assertThat(widgets().get(0)).containsEntry("NAME", "old-item").containsEntry("QTY", 9);
  }

  @Test
  void emptyCellInHeaderColumnSetsNullOnUpdateWhileOtherColumnsAreKept() {
    businessJdbcTemplate.update(
        "INSERT INTO PUBLIC.WIDGETS (NAME, QTY, MEMO) VALUES ('old-item', 5, 'keep-me')");

    ImportResult result =
        csvImportService.importCsv(TABLE_CONFIG_ID, csv("ID,NAME,QTY", "1,renamed,"), "user-1");

    assertThat(result.successCount()).isEqualTo(1);
    assertThat(widgets().get(0))
        .containsEntry("NAME", "renamed")
        .containsEntry("QTY", null)
        .containsEntry("MEMO", "keep-me");
  }

  @Test
  void shortRowMissingTrailingCellsIsTreatedAsEmptyCells() {
    businessJdbcTemplate.update(
        "INSERT INTO PUBLIC.WIDGETS (NAME, QTY, MEMO) VALUES ('old-item', 5, 'keep-me')");

    ImportResult result =
        csvImportService.importCsv(TABLE_CONFIG_ID, csv("ID,NAME,QTY", "1,renamed"), "user-1");

    assertThat(result.successCount()).isEqualTo(1);
    assertThat(widgets().get(0)).containsEntry("NAME", "renamed").containsEntry("QTY", null);
  }

  @Test
  void requiredColumnAbsentFromHeaderIsRowErrorOnInsert() {
    ImportResult result = csvImportService.importCsv(TABLE_CONFIG_ID, csv("QTY", "5"), "user-1");

    assertThat(result.successCount()).isEqualTo(0);
    assertThat(result.errors()).containsExactly(new RowError(1, "NAME", "required"));
    assertThat(widgets()).isEmpty();
  }

  @Test
  void insertOnlyIncludesColumnsPresentInHeaderAndIgnoresUnknownHeaderColumns() {
    ImportResult result =
        csvImportService.importCsv(
            TABLE_CONFIG_ID, csv("NAME,NOT_A_COLUMN", "n1,ignored", "n2,ignored"), "user-1");

    assertThat(result.successCount()).isEqualTo(2);
    List<Map<String, Object>> rows = widgets();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("NAME", "n1").containsEntry("QTY", null);
    assertThat(rows.get(1)).containsEntry("NAME", "n2").containsEntry("MEMO", null);
  }

  // ---- BR8.1: ファイル形式エラー・BOM・空・ヘッダーのみ ----

  @Test
  void acceptsCsvWithoutBom() {
    ImportResult result =
        csvImportService.importCsv(TABLE_CONFIG_ID, rawCsv("NAME,QTY\r\nplain,3\r\n"), "user-1");

    assertThat(result.successCount()).isEqualTo(1);
    assertThat(widgets().get(0)).containsEntry("NAME", "plain");
  }

  @Test
  void headerOnlyFileCommitsZeroRows() {
    ImportResult result = csvImportService.importCsv(TABLE_CONFIG_ID, csv("ID,NAME,QTY"), "user-1");

    assertThat(result.successCount()).isEqualTo(0);
    assertThat(result.errors()).isEmpty();
    ImportExecutedEvent event = singleEvent();
    assertThat(event.committed()).isTrue();
    assertThat(event.successCount()).isEqualTo(0);
  }

  @Test
  void emptyFileIsAFormatError() {
    assertThatThrownBy(() -> csvImportService.importCsv(TABLE_CONFIG_ID, rawCsv(""), "user-1"))
        .isInstanceOf(CsvFormatException.class);
  }

  @Test
  void headerWithoutAnyKnownColumnIsAFormatError() {
    assertThatThrownBy(
            () -> csvImportService.importCsv(TABLE_CONFIG_ID, csv("FOO,BAR", "1,2"), "user-1"))
        .isInstanceOf(CsvFormatException.class);
  }

  @Test
  void malformedQuotingInHeaderIsAFormatError() {
    assertThatThrownBy(
            () ->
                csvImportService.importCsv(
                    TABLE_CONFIG_ID, rawCsv("\"ID,NAME\r\n1,a\r\n"), "user-1"))
        .isInstanceOf(CsvFormatException.class);
  }

  @Test
  void malformedQuotingInDataRowIsAFormatErrorForTheWholeFileAndNothingIsApplied() {
    assertThatThrownBy(
            () ->
                csvImportService.importCsv(
                    TABLE_CONFIG_ID, rawCsv("NAME,QTY\r\nok,1\r\n\"unterminated,2\r\n"), "user-1"))
        .isInstanceOf(CsvFormatException.class);
    assertThat(widgets()).isEmpty();
  }

  @Test
  void missingTableConfigPropagatesTableConfigNotFoundException() {
    when(configEngineApi.getTableConfigById("missing"))
        .thenThrow(
            new TableConfigNotFoundException("TableConfig not found: tableConfigId=missing"));

    assertThatThrownBy(() -> csvImportService.importCsv("missing", csv("ID,NAME", "1,a"), "user-1"))
        .isInstanceOf(TableConfigNotFoundException.class);
  }

  // ---- BR8.6: 行番号・フィールド単位のエラー内容 ----

  @Test
  void reportsRowNumberFieldAndMessageForEachViolation() {
    ImportResult result =
        csvImportService.importCsv(
            TABLE_CONFIG_ID, csv("NAME,QTY", "ok,1", ",2", "ok,not-a-number", "ok,3"), "user-1");

    assertThat(result.errors())
        .containsExactly(
            new RowError(2, "NAME", "required"), new RowError(3, "QTY", "typeMismatch"));
  }

  // ---- R-06: コミット時の失敗 ----

  /** 検証(存在確認)の直後、コミット前に、対象行が他の処理によって削除される状況を再現する検証器。 */
  private static final class RacingValidator extends CsvRowValidator {

    private final JdbcTemplate jdbcTemplate;

    RacingValidator(JdbcTemplate jdbcTemplate) {
      this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RowValidationResult validateRow(
        int rowNumber,
        Map<String, String> rawValues,
        List<CsvColumnDefinition> columns,
        Predicate<Object> primaryKeyExists) {
      RowValidationResult result =
          super.validateRow(rowNumber, rawValues, columns, primaryKeyExists);
      if (result.isValid() && result.report().operation() == ImportOperation.UPDATE) {
        jdbcTemplate.update("DELETE FROM PUBLIC.WIDGETS WHERE ID = 1");
      }
      return result;
    }
  }

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired
  @Qualifier("businessTransactionManager")
  private PlatformTransactionManager businessTransactionManager;

  @Test
  void updateAffectingZeroRowsRollsBackEverythingAndIsReportedAsNotFound() {
    businessJdbcTemplate.update("INSERT INTO PUBLIC.WIDGETS (NAME, QTY) VALUES ('old-item', 5)");
    CsvImportService racing =
        new CsvImportService(
            configEngineApi,
            new CsvColumnDefinitionResolver(),
            new RacingValidator(businessJdbcTemplate),
            businessJdbcTemplate,
            businessTransactionManager,
            eventPublisher);

    ImportResult result =
        racing.importCsv(
            TABLE_CONFIG_ID, csv("ID,NAME,QTY", ",inserted-first,1", "1,updated,2"), "user-1");

    // 0件更新を成功に数えない。先に実行したINSERTも含めてロールバックされる(BR8.7)。
    assertThat(result.successCount()).isEqualTo(0);
    assertThat(result.errors()).containsExactly(new RowError(2, "ID", "notFound"));
    assertThat(widgets()).isEmpty();
    ImportExecutedEvent event = singleEvent();
    assertThat(event.committed()).isFalse();
    assertThat(event.successCount()).isEqualTo(0);
    assertThat(event.errorCount()).isEqualTo(1);
  }

  @Test
  void constraintViolationAtCommitRollsBackAndReturnsSafeRowLevelErrorWithEvent() {
    businessJdbcTemplate.execute("CREATE UNIQUE INDEX UQ_WIDGETS_NAME ON PUBLIC.WIDGETS (NAME)");

    ImportResult result =
        csvImportService.importCsv(
            TABLE_CONFIG_ID, csv("NAME,QTY", "dup,1", "unique,2", "dup,3"), "user-1");

    assertThat(result.successCount()).isEqualTo(0);
    assertThat(result.errors()).hasSize(1);
    RowError error = result.errors().get(0);
    assertThat(error.row()).isEqualTo(3);
    assertThat(error.message()).isEqualTo("constraintViolation");
    // 利用者向けエラーにSQL文・DBのメッセージ・スタックトレースを含めない(BR8.6)。
    assertThat(error.field() + error.message())
        .doesNotContain("INSERT")
        .doesNotContain("UQ_WIDGETS");
    assertThat(widgets()).isEmpty();
    ImportExecutedEvent event = singleEvent();
    assertThat(event.committed()).isFalse();
    assertThat(event.errorCount()).isEqualTo(1);
  }

  @Test
  void unexpectedDatabaseFailureAtCommitPublishesEventAndPropagates() {
    ColumnConfig ghost = new ColumnConfig(TABLE_CONFIG_ID, "GHOST", EditorType.TEXT);
    ghost.setDisplayOrder(2);
    when(configEngineApi.getColumnConfigs(TABLE_CONFIG_ID))
        .thenReturn(List.of(idColumn(), nameColumn(), ghost));

    // INSERTが存在しない列を参照してSQLエラー(データ不備ではなく設定と実DBの不整合)。
    assertThatThrownBy(
            () -> csvImportService.importCsv(TABLE_CONFIG_ID, csv("NAME,GHOST", "a,b"), "user-1"))
        .isInstanceOf(BadSqlGrammarException.class);

    ImportExecutedEvent event = singleEvent();
    assertThat(event.committed()).isFalse();
    assertThat(event.successCount()).isEqualTo(0);
    assertThat(widgets()).isEmpty();
  }

  // ---- R-06(4): 識別子の引用符 ----

  @Test
  void quotesReservedWordIdentifiers() {
    businessJdbcTemplate.execute("DROP TABLE IF EXISTS PUBLIC.\"ORDER\"");
    businessJdbcTemplate.execute(
        "CREATE TABLE PUBLIC.\"ORDER\" (ID INT AUTO_INCREMENT PRIMARY KEY, \"GROUP\" VARCHAR(20), \"SELECT\" VARCHAR(20))");
    ColumnConfig id = new ColumnConfig("orders", "ID", EditorType.INTEGER, true);
    id.setDisplayOrder(0);
    ColumnConfig group = new ColumnConfig("orders", "GROUP", EditorType.TEXT);
    group.setDisplayOrder(1);
    ColumnConfig select = new ColumnConfig("orders", "SELECT", EditorType.TEXT);
    select.setDisplayOrder(2);
    when(configEngineApi.getTableConfigById("orders"))
        .thenReturn(new TableConfig("PUBLIC", "ORDER"));
    when(configEngineApi.getColumnConfigs("orders")).thenReturn(List.of(id, group, select));

    ImportResult result =
        csvImportService.importCsv("orders", csv("ID,GROUP,SELECT", ",g1,s1", "1,g2,s2"), "user-1");

    // 1行目(INSERT)はID=1が採番され、2行目(UPDATE、ID=1は検証時点で存在しない)は存在確認で失敗する。
    assertThat(result.errors()).containsExactly(new RowError(2, "ID", "notFound"));

    ImportResult second =
        csvImportService.importCsv("orders", csv("ID,GROUP,SELECT", ",g1,s1"), "user-1");
    assertThat(second.successCount()).isEqualTo(1);
    ImportResult third =
        csvImportService.importCsv("orders", csv("ID,GROUP,SELECT", "1,g2,s2"), "user-1");
    assertThat(third.successCount()).isEqualTo(1);
    assertThat(
            businessJdbcTemplate.queryForList("SELECT \"GROUP\", \"SELECT\" FROM PUBLIC.\"ORDER\""))
        .singleElement()
        .satisfies(
            row -> assertThat(row).containsEntry("GROUP", "g2").containsEntry("SELECT", "s2"));
  }
}
