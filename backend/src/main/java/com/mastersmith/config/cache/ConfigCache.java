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

package com.mastersmith.config.cache;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 起動時に内部設定DBから全設定を読み込み、不変スナップショットとしてアプリケーション内
 * メモリに保持するキャッシュ(performance-design.md「Load-All-At-Startup」パターン、NFR1.1, NFR1.2)。
 *
 * <p>読み取りはロックフリー(不変スナップショットの参照取得のみ)。書き込み反映時は {@link #reload()}によりスナップショット全体を再構築し、{@link
 * AtomicReference}で原子的に 差し替える(部分更新は行わない)。
 */
@Component
public class ConfigCache {

  private final TableConfigRepository tableConfigRepository;
  private final ColumnConfigRepository columnConfigRepository;
  private final TranslationEntryRepository translationEntryRepository;

  private final AtomicReference<Snapshot> snapshotRef = new AtomicReference<>(Snapshot.empty());

  public ConfigCache(
      TableConfigRepository tableConfigRepository,
      ColumnConfigRepository columnConfigRepository,
      TranslationEntryRepository translationEntryRepository) {
    this.tableConfigRepository = tableConfigRepository;
    this.columnConfigRepository = columnConfigRepository;
    this.translationEntryRepository = translationEntryRepository;
  }

  /** 内部設定DBの全件を読み込み、スナップショットを原子的に差し替える。 */
  public void reload() {
    List<TableConfig> tableConfigs = tableConfigRepository.findAll();
    List<ColumnConfig> columnConfigs = columnConfigRepository.findAll();
    List<TranslationEntry> translationEntries = translationEntryRepository.findAll();
    snapshotRef.set(Snapshot.of(tableConfigs, columnConfigs, translationEntries));
  }

  public Optional<TableConfig> findTableConfig(String schemaName, String tableName) {
    return Optional.ofNullable(
        snapshot().byNameKey().get(new TableConfigKey(schemaName, tableName)));
  }

  public Optional<TableConfig> findTableConfigById(String tableConfigId) {
    return Optional.ofNullable(snapshot().byId().get(tableConfigId));
  }

  public List<ColumnConfig> findColumnConfigs(String tableConfigId) {
    return snapshot().columnConfigsByTable().getOrDefault(tableConfigId, List.of());
  }

  public Optional<String> findTranslation(String i18nKey, String locale) {
    return Optional.ofNullable(snapshot().translations().get(new TranslationKey(i18nKey, locale)));
  }

  public List<TableConfig> allTableConfigs() {
    return snapshot().tableConfigList();
  }

  public List<ColumnConfig> allColumnConfigs() {
    return snapshot().columnConfigList();
  }

  public List<TranslationEntry> allTranslationEntries() {
    return snapshot().translationEntryList();
  }

  private Snapshot snapshot() {
    return snapshotRef.get();
  }

  private record TableConfigKey(String schemaName, String tableName) {}

  private record TranslationKey(String i18nKey, String locale) {}

  private record Snapshot(
      Map<TableConfigKey, TableConfig> byNameKey,
      Map<String, TableConfig> byId,
      Map<String, List<ColumnConfig>> columnConfigsByTable,
      Map<TranslationKey, String> translations,
      List<TableConfig> tableConfigList,
      List<ColumnConfig> columnConfigList,
      List<TranslationEntry> translationEntryList) {

    static Snapshot empty() {
      return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of());
    }

    static Snapshot of(
        List<TableConfig> tableConfigs,
        List<ColumnConfig> columnConfigs,
        List<TranslationEntry> translationEntries) {
      Map<TableConfigKey, TableConfig> byNameKey =
          tableConfigs.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      tc -> new TableConfigKey(tc.getSchemaName(), tc.getTableName()),
                      tc -> tc,
                      (a, b) -> a));
      Map<String, TableConfig> byId =
          tableConfigs.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      TableConfig::getTableConfigId, tc -> tc, (a, b) -> a));
      Map<String, List<ColumnConfig>> columnConfigsByTable =
          Map.copyOf(
              columnConfigs.stream()
                  .collect(
                      Collectors.groupingBy(
                          ColumnConfig::getTableConfigId, Collectors.toUnmodifiableList())));
      Map<TranslationKey, String> translations =
          translationEntries.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      te -> new TranslationKey(te.getI18nKey(), te.getLocale()),
                      TranslationEntry::getText,
                      (a, b) -> a));
      return new Snapshot(
          byNameKey,
          byId,
          columnConfigsByTable,
          translations,
          List.copyOf(tableConfigs),
          List.copyOf(columnConfigs),
          List.copyOf(translationEntries));
    }
  }
}
