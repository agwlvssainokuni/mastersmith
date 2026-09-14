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

package com.mastersmith.config.store;

import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.dto.ColumnDraftEntry;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.ConfigImportSet;
import com.mastersmith.config.dto.TableConfigDraft;
import com.mastersmith.config.dto.TableDraftEntry;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.event.ConfigChangeOperation;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.rdbms.LogicalType;
import com.mastersmith.config.rdbms.RdbmsTypeNormalizer;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import com.mastersmith.config.validation.ConfigValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ConfigEngineApi}(C9契約)の実装。読み取り系は{@link ConfigCache}へ委譲し、 書き込み系は{@link
 * ConfigValidator}検証後に内部設定DBへ永続化した上でキャッシュを 再構築する(performance-design.md)。
 */
@Service
public class ConfigModelStore implements ConfigEngineApi {

  private final ConfigCache cache;
  private final TableConfigRepository tableConfigRepository;
  private final ColumnConfigRepository columnConfigRepository;
  private final TranslationEntryRepository translationEntryRepository;
  private final ConfigValidator configValidator;
  private final RdbmsTypeNormalizer rdbmsTypeNormalizer;
  private final ApplicationEventPublisher eventPublisher;

  public ConfigModelStore(
      ConfigCache cache,
      TableConfigRepository tableConfigRepository,
      ColumnConfigRepository columnConfigRepository,
      TranslationEntryRepository translationEntryRepository,
      ConfigValidator configValidator,
      RdbmsTypeNormalizer rdbmsTypeNormalizer,
      ApplicationEventPublisher eventPublisher) {
    this.cache = cache;
    this.tableConfigRepository = tableConfigRepository;
    this.columnConfigRepository = columnConfigRepository;
    this.translationEntryRepository = translationEntryRepository;
    this.configValidator = configValidator;
    this.rdbmsTypeNormalizer = rdbmsTypeNormalizer;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public TableConfig getTableConfig(String schemaName, String tableName) {
    return cache
        .findTableConfig(schemaName, tableName)
        .orElseThrow(
            () ->
                new TableConfigNotFoundException(
                    "TableConfig not found: %s.%s".formatted(schemaName, tableName)));
  }

  @Override
  public List<ColumnConfig> getColumnConfigs(String tableConfigId) {
    return cache.findColumnConfigs(tableConfigId);
  }

  @Override
  public TableConfig getTableConfigById(String tableConfigId) {
    return cache
        .findTableConfigById(tableConfigId)
        .orElseThrow(
            () ->
                new TableConfigNotFoundException(
                    "TableConfig not found: tableConfigId=" + tableConfigId));
  }

  @Override
  public Optional<String> getOptimisticLockColumn(String tableConfigId) {
    TableConfig tableConfig =
        cache
            .findTableConfigById(tableConfigId)
            .orElseThrow(
                () ->
                    new TableConfigNotFoundException(
                        "TableConfig not found: tableConfigId=" + tableConfigId));
    return Optional.ofNullable(tableConfig.getOptimisticLockColumn());
  }

  @Override
  @Transactional
  public List<String> writeTableConfigDraft(TableConfigDraft draft) {
    List<String> generatedTableConfigIds = new ArrayList<>();
    for (TableDraftEntry tableDraft : draft.tables()) {
      // BR1.8: 既存設定がある場合は上書きしない(fail fastではなくスキップ)。
      if (cache.findTableConfig(tableDraft.schemaName(), tableDraft.tableName()).isPresent()) {
        continue;
      }
      TableConfig tableConfig = new TableConfig(tableDraft.schemaName(), tableDraft.tableName());
      List<ColumnConfig> columnConfigs =
          buildColumnConfigs(draft, tableDraft, tableConfig.getTableConfigId());

      configValidator.validate(tableConfig);
      columnConfigs.forEach(configValidator::validate);

      tableConfigRepository.save(tableConfig);
      columnConfigRepository.saveAll(columnConfigs);
      generatedTableConfigIds.add(tableConfig.getTableConfigId());
    }

    if (!generatedTableConfigIds.isEmpty()) {
      cache.reload();
      eventPublisher.publishEvent(
          ConfigChangedEvent.of(
              ConfigChangeOperation.DRAFT_IMPORTED,
              "tables:%d".formatted(generatedTableConfigIds.size()),
              "system"));
    }
    return List.copyOf(generatedTableConfigIds);
  }

  private List<ColumnConfig> buildColumnConfigs(
      TableConfigDraft draft, TableDraftEntry tableDraft, String tableConfigId) {
    List<ColumnConfig> columnConfigs = new ArrayList<>();
    for (ColumnDraftEntry columnDraft : tableDraft.columns()) {
      LogicalType logicalType =
          rdbmsTypeNormalizer.normalize(draft.dialect(), columnDraft.rawTypeName());
      columnConfigs.add(
          new ColumnConfig(
              tableConfigId,
              columnDraft.columnName(),
              logicalType.defaultEditorType(),
              columnDraft.isPrimaryKey()));
    }
    return columnConfigs;
  }

  @Override
  public ConfigExportSet getExportableConfigSet() {
    return new ConfigExportSet(
        cache.allTableConfigs(), cache.allColumnConfigs(), cache.allTranslationEntries());
  }

  @Override
  @Transactional
  public void importConfigSet(ConfigImportSet configSet) {
    // BR1.1, BR1.11: all-or-nothing。検証を全件先に行い、1件でも違反すれば何も反映しない。
    configValidator.validateAll(configSet.tableConfigs(), configSet.columnConfigs());

    tableConfigRepository.saveAll(configSet.tableConfigs());
    columnConfigRepository.saveAll(configSet.columnConfigs());
    translationEntryRepository.saveAll(configSet.translationEntries());
    cache.reload();

    eventPublisher.publishEvent(
        ConfigChangedEvent.of(
            ConfigChangeOperation.CONFIG_SET_IMPORTED,
            "tables:%d".formatted(configSet.tableConfigs().size()),
            "system"));
  }
}
