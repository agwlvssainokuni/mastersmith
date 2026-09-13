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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ConfigCache}の単体テスト(リポジトリをモック)。起動時ロード、書込み後の全体差し替え、
 * スナップショットの不変性を検証する(performance-design.md)。
 */
@ExtendWith(MockitoExtension.class)
class ConfigCacheTest {

  @Mock private TableConfigRepository tableConfigRepository;
  @Mock private ColumnConfigRepository columnConfigRepository;
  @Mock private TranslationEntryRepository translationEntryRepository;

  private ConfigCache cache;

  @BeforeEach
  void setUp() {
    cache =
        new ConfigCache(tableConfigRepository, columnConfigRepository, translationEntryRepository);
  }

  @Test
  void beforeReloadEverythingIsEmpty() {
    assertThat(cache.allTableConfigs()).isEmpty();
    assertThat(cache.findTableConfig("public", "products")).isEmpty();
  }

  @Test
  void reloadPopulatesTheSnapshotFromAllThreeRepositories() {
    TableConfig tableConfig = new TableConfig("public", "products");
    ColumnConfig columnConfig =
        new ColumnConfig(tableConfig.getTableConfigId(), "name", EditorType.TEXT);
    TranslationEntry translationEntry =
        new TranslationEntry("table.public.products.label", "ja", "商品");

    when(tableConfigRepository.findAll()).thenReturn(List.of(tableConfig));
    when(columnConfigRepository.findAll()).thenReturn(List.of(columnConfig));
    when(translationEntryRepository.findAll()).thenReturn(List.of(translationEntry));

    cache.reload();

    assertThat(cache.findTableConfig("public", "products")).contains(tableConfig);
    assertThat(cache.findTableConfigById(tableConfig.getTableConfigId())).contains(tableConfig);
    assertThat(cache.findColumnConfigs(tableConfig.getTableConfigId()))
        .containsExactly(columnConfig);
    assertThat(cache.findTranslation("table.public.products.label", "ja")).contains("商品");
    assertThat(cache.allTableConfigs()).containsExactly(tableConfig);
    assertThat(cache.allColumnConfigs()).containsExactly(columnConfig);
    assertThat(cache.allTranslationEntries()).containsExactly(translationEntry);
  }

  @Test
  void findColumnConfigsReturnsEmptyListForUnknownTableConfigId() {
    when(tableConfigRepository.findAll()).thenReturn(List.of());
    when(columnConfigRepository.findAll()).thenReturn(List.of());
    when(translationEntryRepository.findAll()).thenReturn(List.of());
    cache.reload();

    assertThat(cache.findColumnConfigs("unknown-id")).isEmpty();
  }

  @Test
  void reloadReplacesTheWholeSnapshotAtomicallyRatherThanMergingIt() {
    TableConfig first = new TableConfig("public", "products");
    when(tableConfigRepository.findAll()).thenReturn(List.of(first));
    when(columnConfigRepository.findAll()).thenReturn(List.of());
    when(translationEntryRepository.findAll()).thenReturn(List.of());
    cache.reload();
    assertThat(cache.allTableConfigs()).containsExactly(first);

    TableConfig second = new TableConfig("public", "orders");
    when(tableConfigRepository.findAll()).thenReturn(List.of(second));
    cache.reload();

    // 差分マージではなく全体差し替え: 最初のTableConfigはもはや含まれない。
    assertThat(cache.allTableConfigs()).containsExactly(second);
    assertThat(cache.findTableConfig("public", "products")).isEmpty();
  }

  @Test
  void snapshotViewsReturnedBeforeReloadAreUnaffectedByALaterReload() {
    when(tableConfigRepository.findAll())
        .thenReturn(List.of(new TableConfig("public", "products")));
    when(columnConfigRepository.findAll()).thenReturn(List.of());
    when(translationEntryRepository.findAll()).thenReturn(List.of());
    cache.reload();

    List<TableConfig> firstView = cache.allTableConfigs();

    when(tableConfigRepository.findAll()).thenReturn(List.of(new TableConfig("public", "orders")));
    cache.reload();

    // 並行読み取り時の一貫性: reload前に取得したビューは、reload後も変化しない不変スナップショットのまま。
    assertThat(firstView).hasSize(1);
    assertThat(firstView.get(0).getTableName()).isEqualTo("products");
  }

  @Test
  void allTableConfigsIsUnmodifiable() {
    when(tableConfigRepository.findAll())
        .thenReturn(List.of(new TableConfig("public", "products")));
    when(columnConfigRepository.findAll()).thenReturn(List.of());
    when(translationEntryRepository.findAll()).thenReturn(List.of());
    cache.reload();

    List<TableConfig> view = cache.allTableConfigs();
    assertThatThrownBy(() -> view.add(new TableConfig("public", "extra")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
