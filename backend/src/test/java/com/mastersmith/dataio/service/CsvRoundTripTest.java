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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.dataio.csv.CsvColumnDefinitionResolver;
import com.mastersmith.dataio.csv.CsvRowValidator;
import com.mastersmith.dataio.dto.ImportResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * エクスポートしたCSVを編集せずにそのままインポートし直しても、業務データが変化しないこと(往復)の確認 (rules.md
 * BR8.1「エクスポート・インポート双方で同一形式」、レビュー指摘R-02・R-04の回帰テスト)。
 *
 * <p>DATE・DATETIME(小数秒を含む)・真偽値・小数・NULLを持つテーブルと、エクスポートに出力されないhidden列 (かつ{@code
 * required})を用いる。hidden列の値が再インポートで破壊(NULL上書き)されないことも確認する。
 */
class CsvRoundTripTest {

  private static final String TABLE_CONFIG_ID = "gadgets";

  private JdbcTemplate jdbc;
  private CsvExportService exportService;
  private CsvImportService importService;

  @BeforeEach
  void setUp() {
    DataSource dataSource =
        DataSourceBuilder.create()
            .url("jdbc:h2:mem:csv-roundtrip-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
            .driverClassName("org.h2.Driver")
            .username("sa")
            .password("")
            .build();
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        "CREATE TABLE PUBLIC.GADGETS ("
            + "ID INT AUTO_INCREMENT PRIMARY KEY, NAME VARCHAR(50) NOT NULL, PRICE DECIMAL(10,2), "
            + "RELEASED DATE, UPDATED_AT TIMESTAMP, ACTIVE BOOLEAN, SECRET VARCHAR(50) NOT NULL, "
            + "NOTE VARCHAR(50))");

    ConfigEngineApi configEngineApi = mock(ConfigEngineApi.class);
    when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID))
        .thenReturn(new TableConfig("PUBLIC", "GADGETS"));
    when(configEngineApi.getColumnConfigs(TABLE_CONFIG_ID))
        .thenReturn(
            List.of(
                column("ID", EditorType.INTEGER, 0, true, false, false),
                column("NAME", EditorType.TEXT, 1, false, true, false),
                column("PRICE", EditorType.DECIMAL, 2, false, false, false),
                column("RELEASED", EditorType.DATE, 3, false, false, false),
                column("UPDATED_AT", EditorType.DATETIME, 4, false, false, false),
                column("ACTIVE", EditorType.SWITCH, 5, false, false, false),
                // hidden列(エクスポートに出力されない)かつrequired。
                column("SECRET", EditorType.TEXT, 6, false, true, true),
                column("NOTE", EditorType.TEXT, 7, false, false, false)));

    CsvColumnDefinitionResolver resolver = new CsvColumnDefinitionResolver();
    exportService = new CsvExportService(configEngineApi, resolver, dataSource);
    importService =
        new CsvImportService(
            configEngineApi,
            resolver,
            new CsvRowValidator(),
            jdbc,
            new DataSourceTransactionManager(dataSource),
            mock(ApplicationEventPublisher.class));
  }

  private static ColumnConfig column(
      String name,
      EditorType editorType,
      int displayOrder,
      boolean primaryKey,
      boolean required,
      boolean hidden) {
    ColumnConfig column = new ColumnConfig(TABLE_CONFIG_ID, name, editorType, primaryKey);
    column.setDisplayOrder(displayOrder);
    if (required) {
      column.setValidationRule(ValidationRule.builder().rule("required", true).build());
    }
    if (hidden) {
      column.setVisibility(Visibility.HIDDEN);
    }
    return column;
  }

  private List<Map<String, Object>> rows() {
    return jdbc.queryForList("SELECT * FROM PUBLIC.GADGETS ORDER BY ID");
  }

  @Test
  void exportedCsvCanBeImportedBackWithoutChangingAnyData() {
    jdbc.update(
        "INSERT INTO PUBLIC.GADGETS (NAME, PRICE, RELEASED, UPDATED_AT, ACTIVE, SECRET, NOTE) VALUES"
            + " ('first', 19.99, DATE '2026-09-14', TIMESTAMP '2026-01-01 10:00:00', TRUE, 'secret-1',"
            + " 'note-1')");
    jdbc.update(
        "INSERT INTO PUBLIC.GADGETS (NAME, PRICE, RELEASED, UPDATED_AT, ACTIVE, SECRET, NOTE) VALUES"
            + " ('second', 5.50, DATE '2025-12-31', TIMESTAMP '2026-02-03 04:05:06.123', FALSE,"
            + " 'secret-2', NULL)");
    jdbc.update(
        "INSERT INTO PUBLIC.GADGETS (NAME, PRICE, RELEASED, UPDATED_AT, ACTIVE, SECRET, NOTE) VALUES"
            + " ('third-with-nulls', NULL, NULL, NULL, NULL, 'secret-3', NULL)");
    List<Map<String, Object>> before = rows();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    exportService.exportCsv(
        TABLE_CONFIG_ID,
        null,
        null,
        List.of("ID", "NAME", "PRICE", "RELEASED", "UPDATED_AT", "ACTIVE", "SECRET", "NOTE"),
        out);
    String csv = out.toString(StandardCharsets.UTF_8);

    // エクスポートは正規形式(ISO-8601)で出力し、hidden列は出力しない。
    assertThat(csv).contains("2026-01-01T10:00:00").contains("2026-02-03T04:05:06.123");
    assertThat(csv).doesNotContain("SECRET").doesNotContain("secret-1");

    ImportResult result =
        importService.importCsv(
            TABLE_CONFIG_ID, new ByteArrayInputStream(out.toByteArray()), "user-1");

    assertThat(result.errors()).isEmpty();
    assertThat(result.successCount()).isEqualTo(3);
    // 値(日付・日時・真偽値・小数・NULL)もhidden列(SECRET)も、往復の前後で変化しない。
    assertThat(rows()).isEqualTo(before);
  }

  @Test
  void editingAnExportedRowUpdatesOnlyTheEditedColumns() {
    jdbc.update(
        "INSERT INTO PUBLIC.GADGETS (NAME, PRICE, RELEASED, UPDATED_AT, ACTIVE, SECRET, NOTE) VALUES"
            + " ('first', 19.99, DATE '2026-09-14', TIMESTAMP '2026-01-01 10:00:00', TRUE, 'secret-1',"
            + " 'note-1')");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    exportService.exportCsv(
        TABLE_CONFIG_ID, null, null, List.of("ID", "NAME", "PRICE", "UPDATED_AT"), out);
    // 出力されなかった列(RELEASED・ACTIVE・SECRET・NOTE)はCSVに存在せず、更新されない。
    String edited = out.toString(StandardCharsets.UTF_8).replace("first", "renamed");

    ImportResult result =
        importService.importCsv(
            TABLE_CONFIG_ID,
            new ByteArrayInputStream(edited.getBytes(StandardCharsets.UTF_8)),
            "user-1");

    assertThat(result.successCount()).isEqualTo(1);
    assertThat(rows().get(0))
        .containsEntry("NAME", "renamed")
        .containsEntry("SECRET", "secret-1")
        .containsEntry("NOTE", "note-1")
        .containsEntry("ACTIVE", true);
  }
}
