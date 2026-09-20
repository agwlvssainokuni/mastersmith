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
import static org.mockito.Mockito.when;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.config.store.ConfigEngineApi;
import com.mastersmith.dataio.csv.CsvColumnDefinitionResolver;
import com.mastersmith.dataio.csv.CsvRowValidator;
import com.mastersmith.dataio.dto.ImportResult;
import com.mastersmith.dataio.event.ImportExecutedEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            + "QTY INT)");

    TableConfig tableConfig = new TableConfig("PUBLIC", "WIDGETS");
    when(configEngineApi.getTableConfigById(TABLE_CONFIG_ID)).thenReturn(tableConfig);
    when(configEngineApi.getColumnConfigs(TABLE_CONFIG_ID))
        .thenReturn(List.of(idColumn(), nameColumn(), qtyColumn()));
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
}
