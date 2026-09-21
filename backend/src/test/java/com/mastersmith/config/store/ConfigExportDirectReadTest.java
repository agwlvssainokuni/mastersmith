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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.rdbms.RdbmsTypeNormalizer;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import com.mastersmith.config.validation.ConfigValidator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link
 * ConfigEngineApi#getExportableConfigSet()}が、キャッシュを介さず、内部設定DB(リポジトリ)から直接読み(NFR4.4)、他から変更できないスナップショットを返すこと(config-engineのレビュー指摘R-07)
 * のテスト。
 */
class ConfigExportDirectReadTest {

  private ConfigCache cache;
  private TableConfigRepository tableRepository;
  private ColumnConfigRepository columnRepository;
  private TranslationEntryRepository translationRepository;
  private ConfigModelStore store;

  @BeforeEach
  void setUp() {
    cache = mock(ConfigCache.class);
    tableRepository = mock(TableConfigRepository.class);
    columnRepository = mock(ColumnConfigRepository.class);
    translationRepository = mock(TranslationEntryRepository.class);
    store =
        new ConfigModelStore(
            cache,
            tableRepository,
            columnRepository,
            translationRepository,
            mock(ConfigValidator.class),
            mock(RdbmsTypeNormalizer.class),
            mock(ApplicationEventPublisher.class));
  }

  @Test
  void readsFromTheRepositoriesAndNeverConsultsTheCache() {
    TableConfig table = new TableConfig("id-1", "s1", "t1", 0, null);
    when(tableRepository.findAll()).thenReturn(List.of(table));
    when(columnRepository.findAll()).thenReturn(List.of());
    when(translationRepository.findAll()).thenReturn(List.of());

    ConfigExportSet result = store.getExportableConfigSet();

    assertThat(result.tableConfigs()).hasSize(1);
    assertThat(result.tableConfigs().get(0).tableConfigId()).isEqualTo(table.getTableConfigId());
    verifyNoInteractions(cache);
  }

  @Test
  void theSnapshotIsACopyThatLaterEntityChangesDoNotAffect() {
    TableConfig table = new TableConfig("s1", "t1");
    table.setDisplayOrder(3);
    ColumnConfig column = new ColumnConfig(table.getTableConfigId(), "c1", EditorType.SELECT, true);
    List<ChoiceOption> options = new ArrayList<>(List.of(new ChoiceOption("v", "k")));
    column.setChoiceOptions(options);
    TranslationEntry translation = new TranslationEntry("k", "ja", "text");
    when(tableRepository.findAll()).thenReturn(List.of(table));
    when(columnRepository.findAll()).thenReturn(List.of(column));
    when(translationRepository.findAll()).thenReturn(List.of(translation));

    ConfigExportSet result = store.getExportableConfigSet();
    table.setDisplayOrder(99);
    column.setDisplayOrder(77);
    options.add(new ChoiceOption("v2", "k2"));
    translation.setText("changed");

    assertThat(result.tableConfigs().get(0).displayOrder()).isEqualTo(3);
    assertThat(result.columnConfigs().get(0).displayOrder()).isZero();
    assertThat(result.columnConfigs().get(0).choiceOptions()).hasSize(1);
    assertThat(result.columnConfigs().get(0).primaryKey()).isTrue();
    assertThat(result.translationEntries().get(0).text()).isEqualTo("text");
  }

  @Test
  void theReturnedListsAreUnmodifiable() {
    when(tableRepository.findAll()).thenReturn(List.of(new TableConfig("s1", "t1")));
    when(columnRepository.findAll()).thenReturn(List.of());
    when(translationRepository.findAll()).thenReturn(List.of());

    ConfigExportSet result = store.getExportableConfigSet();

    assertThatThrownBy(() -> result.tableConfigs().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.columnConfigs().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aNullListInTheRecordBecomesEmpty() {
    ConfigExportSet empty = new ConfigExportSet(null, null, null);

    assertThat(empty.tableConfigs()).isEmpty();
    assertThat(empty.columnConfigs()).isEmpty();
    assertThat(empty.translationEntries()).isEmpty();
  }
}
