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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.dto.ColumnDraftEntry;
import com.mastersmith.config.dto.ConfigImportSet;
import com.mastersmith.config.dto.TableConfigDraft;
import com.mastersmith.config.dto.TableDraftEntry;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.TableConfigNotFoundException;
import com.mastersmith.config.rdbms.LogicalType;
import com.mastersmith.config.rdbms.RdbmsDialect;
import com.mastersmith.config.rdbms.RdbmsTypeNormalizer;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import com.mastersmith.config.validation.ConfigValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** {@link ConfigModelStore}の単体テスト(ConfigCache・リポジトリ・検証・正規化をモック)。 */
@ExtendWith(MockitoExtension.class)
class ConfigModelStoreTest {

  @Mock private ConfigCache cache;
  @Mock private TableConfigRepository tableConfigRepository;
  @Mock private ColumnConfigRepository columnConfigRepository;
  @Mock private TranslationEntryRepository translationEntryRepository;
  @Mock private ConfigValidator configValidator;
  @Mock private RdbmsTypeNormalizer rdbmsTypeNormalizer;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ConfigModelStore store;

  @BeforeEach
  void setUp() {
    store =
        new ConfigModelStore(
            cache,
            tableConfigRepository,
            columnConfigRepository,
            translationEntryRepository,
            configValidator,
            rdbmsTypeNormalizer,
            eventPublisher);
  }

  @Test
  void getTableConfigReturnsCachedValueWhenPresent() {
    TableConfig tableConfig = new TableConfig("public", "products");
    when(cache.findTableConfig("public", "products")).thenReturn(Optional.of(tableConfig));

    assertThat(store.getTableConfig("public", "products")).isSameAs(tableConfig);
  }

  @Test
  void getTableConfigThrowsWhenNotFound() {
    when(cache.findTableConfig("public", "missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.getTableConfig("public", "missing"))
        .isInstanceOf(TableConfigNotFoundException.class);
  }

  @Test
  void getColumnConfigsDelegatesToCache() {
    ColumnConfig columnConfig = new ColumnConfig("t1", "name", EditorType.TEXT);
    when(cache.findColumnConfigs("t1")).thenReturn(List.of(columnConfig));

    assertThat(store.getColumnConfigs("t1")).containsExactly(columnConfig);
  }

  @Test
  void getOptimisticLockColumnReturnsConfiguredColumn() {
    TableConfig tableConfig = new TableConfig("t1", "public", "products", 0, "updated_at");
    when(cache.findTableConfigById("t1")).thenReturn(Optional.of(tableConfig));

    assertThat(store.getOptimisticLockColumn("t1")).contains("updated_at");
  }

  @Test
  void getOptimisticLockColumnReturnsEmptyWhenNotConfigured() {
    TableConfig tableConfig = new TableConfig("t1", "public", "products", 0, null);
    when(cache.findTableConfigById("t1")).thenReturn(Optional.of(tableConfig));

    assertThat(store.getOptimisticLockColumn("t1")).isEmpty();
  }

  @Test
  void getOptimisticLockColumnThrowsWhenTableConfigIdUnknown() {
    when(cache.findTableConfigById("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.getOptimisticLockColumn("unknown"))
        .isInstanceOf(TableConfigNotFoundException.class);
  }

  @Test
  void writeTableConfigDraftSkipsTablesThatAlreadyHaveATableConfig() {
    when(cache.findTableConfig("public", "products"))
        .thenReturn(Optional.of(new TableConfig("public", "products")));
    TableConfigDraft draft =
        new TableConfigDraft(
            RdbmsDialect.POSTGRESQL, List.of(new TableDraftEntry("public", "products", List.of())));

    List<String> generated = store.writeTableConfigDraft(draft);

    assertThat(generated).isEmpty();
    verify(tableConfigRepository, never()).save(any());
    verify(cache, never()).reload();
    verify(eventPublisher, never())
        .publishEvent(any(com.mastersmith.config.event.ConfigChangedEvent.class));
  }

  @Test
  void writeTableConfigDraftCreatesNewTableAndColumnsWhenAbsent() {
    when(cache.findTableConfig("public", "new_table")).thenReturn(Optional.empty());
    when(rdbmsTypeNormalizer.normalize(RdbmsDialect.POSTGRESQL, "varchar(255)"))
        .thenReturn(LogicalType.STRING);
    TableConfigDraft draft =
        new TableConfigDraft(
            RdbmsDialect.POSTGRESQL,
            List.of(
                new TableDraftEntry(
                    "public", "new_table", List.of(new ColumnDraftEntry("name", "varchar(255)")))));

    List<String> generated = store.writeTableConfigDraft(draft);

    assertThat(generated).hasSize(1);
    verify(tableConfigRepository, times(1)).save(any());
    verify(columnConfigRepository, times(1)).saveAll(anyList());
    verify(configValidator, times(1)).validate(any(TableConfig.class));
    verify(configValidator, times(1)).validate(any(ColumnConfig.class));
    verify(cache, times(1)).reload();
    verify(eventPublisher, times(1))
        .publishEvent(any(com.mastersmith.config.event.ConfigChangedEvent.class));
  }

  @Test
  void getExportableConfigSetBuildsFromCurrentCacheSnapshot() {
    TableConfig tableConfig = new TableConfig("public", "products");
    when(cache.allTableConfigs()).thenReturn(List.of(tableConfig));
    when(cache.allColumnConfigs()).thenReturn(List.of());
    when(cache.allTranslationEntries()).thenReturn(List.of());

    var exportSet = store.getExportableConfigSet();

    assertThat(exportSet.tableConfigs()).containsExactly(tableConfig);
  }

  @Test
  void importConfigSetPersistsNothingWhenValidationFails() {
    ConfigImportSet importSet =
        new ConfigImportSet(List.of(new TableConfig("public", "products")), List.of(), List.of());
    org.mockito.Mockito.doThrow(
            new com.mastersmith.config.exception.ConfigValidationException(List.of()))
        .when(configValidator)
        .validateAll(anyList(), anyList());

    assertThatThrownBy(() -> store.importConfigSet(importSet))
        .isInstanceOf(com.mastersmith.config.exception.ConfigValidationException.class);

    verify(tableConfigRepository, never()).saveAll(anyList());
    verify(columnConfigRepository, never()).saveAll(anyList());
    verify(translationEntryRepository, never()).saveAll(anyList());
    verify(cache, never()).reload();
  }

  @Test
  void importConfigSetPersistsAllThreeCollectionsAndReloadsCacheWhenValid() {
    ConfigImportSet importSet =
        new ConfigImportSet(List.of(new TableConfig("public", "products")), List.of(), List.of());

    store.importConfigSet(importSet);

    verify(tableConfigRepository, times(1)).saveAll(importSet.tableConfigs());
    verify(columnConfigRepository, times(1)).saveAll(importSet.columnConfigs());
    verify(translationEntryRepository, times(1)).saveAll(importSet.translationEntries());
    verify(cache, times(1)).reload();
    verify(eventPublisher, times(1))
        .publishEvent(any(com.mastersmith.config.event.ConfigChangedEvent.class));
  }
}
