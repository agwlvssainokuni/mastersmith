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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.rdbms.RdbmsTypeNormalizer;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import com.mastersmith.config.validation.ConfigValidator;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@code getTableConfig}/{@code getColumnConfigs}呼び出しの実行時間がNFR1.1(50ms以内、p95相当)
 * を満たすことを確認する簡易テスト(performance-design.md「パフォーマンス予算の検証方法」)。
 *
 * <p>キャッシュ参照(メモリアクセスのみ)の性能を検証する目的のため、実データベースは介さず、
 * ConfigCacheに想定データ量(数十〜百テーブル、tech-stack-decisions.md)相当を読み込んだ状態で 計測する。
 */
class ConfigModelStorePerformanceTest {

  @Test
  void getTableConfigAndGetColumnConfigsCompleteWithinFiftyMilliseconds() {
    TableConfigRepository tableConfigRepository = mock(TableConfigRepository.class);
    ColumnConfigRepository columnConfigRepository = mock(ColumnConfigRepository.class);
    TranslationEntryRepository translationEntryRepository = mock(TranslationEntryRepository.class);

    List<TableConfig> tableConfigs =
        IntStream.range(0, 100).mapToObj(i -> new TableConfig("public", "table_" + i)).toList();
    List<ColumnConfig> columnConfigs =
        tableConfigs.stream()
            .flatMap(
                tc ->
                    IntStream.range(0, 20)
                        .mapToObj(
                            j ->
                                new ColumnConfig(
                                    tc.getTableConfigId(), "col_" + j, EditorType.TEXT)))
            .toList();
    when(tableConfigRepository.findAll()).thenReturn(tableConfigs);
    when(columnConfigRepository.findAll()).thenReturn(columnConfigs);
    when(translationEntryRepository.findAll()).thenReturn(List.of());

    ConfigCache cache =
        new ConfigCache(tableConfigRepository, columnConfigRepository, translationEntryRepository);
    cache.reload();
    ConfigModelStore store =
        new ConfigModelStore(
            cache,
            tableConfigRepository,
            columnConfigRepository,
            translationEntryRepository,
            mock(ConfigValidator.class),
            mock(RdbmsTypeNormalizer.class),
            mock(ApplicationEventPublisher.class));

    TableConfig target = tableConfigs.get(50);

    long startNanos = System.nanoTime();
    TableConfig found = store.getTableConfig("public", target.getTableName());
    List<ColumnConfig> columns = store.getColumnConfigs(found.getTableConfigId());
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

    assertThat(found).isEqualTo(target);
    assertThat(columns).hasSize(20);
    assertThat(elapsedMillis)
        .as("getTableConfig + getColumnConfigs elapsed time (NFR1.1)")
        .isLessThan(50L);
  }
}
