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
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.event.ConfigChangeOperation;
import com.mastersmith.config.event.ConfigChangeSnapshots;
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
  public Optional<ColumnConfig> findColumnConfigById(String columnConfigId) {
    return cache.findColumnConfigById(columnConfigId);
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

  /**
   * schema-introspectorからの初期ドラフトを取り込む(C9契約, W2, BR1.8)。
   *
   * <p><b>既存判定はリポジトリ(DB)に対して行う</b>: {@link ConfigCache}は起動時ロード後、 書込み時にのみ{@link
   * ConfigCache#reload()}で再構築される非トランザクショナルな インスタンスローカルスナップショットである。そのため、直前の書込みが{@code reload()}を
   * 終える前に後続の呼び出しがキャッシュを参照すると、両方が「未存在」と判定してBR1.8の スキップ判定を素通りし、DBの一意制約違反(schema_name,
   * table_name)で失敗し得る (単一インスタンス構成を前提とした設計上のレビュー指摘R-02)。判定を{@link
   * TableConfigRepository#existsBySchemaNameAndTableName}に対して行うことで、この
   * ウィンドウを閉じる。同一トランザクション内で複数の同時呼び出しがDBの一意制約に競合する
   * 残余の可能性は、単一インスタンス構成・低頻度書込み(schema-introspectorからの初期取り込み) という受入れ済みの前提の下では許容されるリスクとする
   * (scalability-requirements.mdの既知の制約と同様の位置づけ)。
   */
  @Override
  @Transactional
  public List<String> writeTableConfigDraft(TableConfigDraft draft) {
    List<String> generatedTableConfigIds = new ArrayList<>();
    // BR1.13: 変更されたエンティティ(TableConfig/ColumnConfig)ごとに個別のConfigChangedEventを発行する。
    // 呼び出し単位でまとめた1イベントにはしない。
    List<ConfigChangedEvent> pendingEvents = new ArrayList<>();
    for (TableDraftEntry tableDraft : draft.tables()) {
      // BR1.8: 既存設定がある場合は上書きしない(fail fastではなくスキップ)。
      if (tableConfigRepository.existsBySchemaNameAndTableName(
          tableDraft.schemaName(), tableDraft.tableName())) {
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

      // 新規作成(W2)のため、beforeValueは常にnull(entities.md ConfigChangedEvent)。
      pendingEvents.add(
          ConfigChangedEvent.of(
              ConfigChangeOperation.DRAFT_IMPORTED,
              ConfigChangedEvent.TARGET_TYPE_TABLE_CONFIG,
              tableConfig.getTableConfigId(),
              null,
              ConfigChangeSnapshots.of(tableConfig),
              "system"));
      for (ColumnConfig columnConfig : columnConfigs) {
        pendingEvents.add(
            ConfigChangedEvent.of(
                ConfigChangeOperation.DRAFT_IMPORTED,
                ConfigChangedEvent.TARGET_TYPE_COLUMN_CONFIG,
                columnConfig.getColumnConfigId(),
                null,
                ConfigChangeSnapshots.of(columnConfig),
                "system"));
      }
    }

    if (!generatedTableConfigIds.isEmpty()) {
      cache.reload();
      pendingEvents.forEach(eventPublisher::publishEvent);
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

    // BR1.13: 変更されたエンティティ(TableConfig/ColumnConfig/TranslationEntry)ごとに個別の
    // ConfigChangedEventを発行する。beforeValueは上書き前の既存状態(存在する場合)を反映するため、
    // saveAllで上書きする前にDBから読み取ってスナップショットを確保する。
    List<ConfigChangedEvent> pendingEvents = new ArrayList<>();
    for (TableConfig tableConfig : configSet.tableConfigs()) {
      TableConfig before =
          tableConfigRepository.findById(tableConfig.getTableConfigId()).orElse(null);
      pendingEvents.add(
          ConfigChangedEvent.of(
              ConfigChangeOperation.CONFIG_SET_IMPORTED,
              ConfigChangedEvent.TARGET_TYPE_TABLE_CONFIG,
              tableConfig.getTableConfigId(),
              ConfigChangeSnapshots.of(before),
              ConfigChangeSnapshots.of(tableConfig),
              "system"));
    }
    for (ColumnConfig columnConfig : configSet.columnConfigs()) {
      ColumnConfig before =
          columnConfigRepository.findById(columnConfig.getColumnConfigId()).orElse(null);
      pendingEvents.add(
          ConfigChangedEvent.of(
              ConfigChangeOperation.CONFIG_SET_IMPORTED,
              ConfigChangedEvent.TARGET_TYPE_COLUMN_CONFIG,
              columnConfig.getColumnConfigId(),
              ConfigChangeSnapshots.of(before),
              ConfigChangeSnapshots.of(columnConfig),
              "system"));
    }
    for (TranslationEntry translationEntry : configSet.translationEntries()) {
      TranslationEntry before =
          translationEntryRepository.findById(translationEntry.getId()).orElse(null);
      pendingEvents.add(
          ConfigChangedEvent.of(
              ConfigChangeOperation.CONFIG_SET_IMPORTED,
              ConfigChangedEvent.TARGET_TYPE_TRANSLATION_ENTRY,
              "%s:%s".formatted(translationEntry.getI18nKey(), translationEntry.getLocale()),
              ConfigChangeSnapshots.of(before),
              ConfigChangeSnapshots.of(translationEntry),
              "system"));
    }

    tableConfigRepository.saveAll(configSet.tableConfigs());
    columnConfigRepository.saveAll(configSet.columnConfigs());
    translationEntryRepository.saveAll(configSet.translationEntries());
    cache.reload();

    pendingEvents.forEach(eventPublisher::publishEvent);
  }
}
