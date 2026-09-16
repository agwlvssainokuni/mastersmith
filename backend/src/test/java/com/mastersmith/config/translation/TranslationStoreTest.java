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

package com.mastersmith.config.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.entity.TranslationEntryId;
import com.mastersmith.config.event.ConfigChangeOperation;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.config.repository.TranslationEntryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** {@link TranslationStore}の単体テスト(リポジトリ・ConfigCacheをモック)。 */
@ExtendWith(MockitoExtension.class)
class TranslationStoreTest {

  @Mock private TranslationEntryRepository repository;
  @Mock private ConfigCache cache;
  @Mock private ApplicationEventPublisher eventPublisher;

  private TranslationStore translationStore;

  @BeforeEach
  void setUp() {
    translationStore = new TranslationStore(repository, cache, eventPublisher);
  }

  @Test
  void upsertCreatesANewEntryWhenNoneExists() {
    TranslationEntryId id = new TranslationEntryId("table.public.products.label", "ja");
    when(repository.findById(id)).thenReturn(Optional.empty());

    translationStore.upsert("table.public.products.label", "ja", "商品");

    ArgumentCaptor<TranslationEntry> captor = ArgumentCaptor.forClass(TranslationEntry.class);
    verify(repository, times(1)).save(captor.capture());
    assertThat(captor.getValue().getI18nKey()).isEqualTo("table.public.products.label");
    assertThat(captor.getValue().getLocale()).isEqualTo("ja");
    assertThat(captor.getValue().getText()).isEqualTo("商品");
    verify(cache, times(1)).reload();

    ArgumentCaptor<ConfigChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(ConfigChangedEvent.class);
    verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
    ConfigChangedEvent event = eventCaptor.getValue();
    // BR1.13/entities.md: 新規作成のためbeforeValueはnull、targetType/targetIdはTranslationEntry用の
    // 形状("{i18nKey}:{locale}")を持つ。
    assertThat(event.operation()).isEqualTo(ConfigChangeOperation.TRANSLATION_UPSERTED);
    assertThat(event.targetType()).isEqualTo(ConfigChangedEvent.TARGET_TYPE_TRANSLATION_ENTRY);
    assertThat(event.targetId()).isEqualTo("table.public.products.label:ja");
    assertThat(event.beforeValue()).isNull();
    assertThat(event.afterValue()).isInstanceOf(java.util.Map.class);
    assertThat(event.actor()).isEqualTo("system");
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void upsertUpdatesTheExistingEntryInPlaceWhenOneAlreadyExists() {
    TranslationEntryId id = new TranslationEntryId("table.public.products.label", "ja");
    TranslationEntry existing = new TranslationEntry("table.public.products.label", "ja", "旧テキスト");
    when(repository.findById(id)).thenReturn(Optional.of(existing));

    translationStore.upsert("table.public.products.label", "ja", "新テキスト");

    ArgumentCaptor<TranslationEntry> captor = ArgumentCaptor.forClass(TranslationEntry.class);
    verify(repository, times(1)).save(captor.capture());
    assertThat(captor.getValue()).isSameAs(existing);
    assertThat(captor.getValue().getText()).isEqualTo("新テキスト");
    verify(cache, times(1)).reload();

    ArgumentCaptor<ConfigChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(ConfigChangedEvent.class);
    verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
    ConfigChangedEvent event = eventCaptor.getValue();
    // beforeValueは上書き前("旧テキスト")のスナップショットを保持し、既にexisting.setText()で
    // インプレース変更された後のオブジェクトに引きずられない(entity_constraints「スナップショット」)。
    assertThat(event.beforeValue()).isInstanceOf(java.util.Map.class);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> beforeValue = (java.util.Map<String, Object>) event.beforeValue();
    assertThat(beforeValue).containsEntry("text", "旧テキスト");
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> afterValue = (java.util.Map<String, Object>) event.afterValue();
    assertThat(afterValue).containsEntry("text", "新テキスト");
  }

  @Test
  void resolveDelegatesToCache() {
    when(cache.findTranslation("table.public.products.label", "ja")).thenReturn(Optional.of("商品"));

    assertThat(translationStore.resolve("table.public.products.label", "ja")).contains("商品");
  }

  @Test
  void resolveReturnsEmptyForAnUnregisteredKey() {
    when(cache.findTranslation("table.public.unknown.label", "ja")).thenReturn(Optional.empty());

    assertThat(translationStore.resolve("table.public.unknown.label", "ja")).isEmpty();
    verify(repository, never()).save(any());
  }
}
