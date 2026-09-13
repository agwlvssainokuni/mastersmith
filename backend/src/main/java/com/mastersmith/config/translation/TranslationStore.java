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

import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.entity.TranslationEntryId;
import com.mastersmith.config.event.ConfigChangeOperation;
import com.mastersmith.config.event.ConfigChangedEvent;
import com.mastersmith.config.repository.TranslationEntryRepository;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 業務設定層のi18nキーに対する言語別テキストの登録・更新・解決を担う(rules.md BR1.10, functional-spec.md W6)。 */
@Service
public class TranslationStore {

  private final TranslationEntryRepository repository;
  private final ConfigCache cache;
  private final ApplicationEventPublisher eventPublisher;

  public TranslationStore(
      TranslationEntryRepository repository,
      ConfigCache cache,
      ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.cache = cache;
    this.eventPublisher = eventPublisher;
  }

  /** 指定された(i18nKey, locale)のテキストを新規登録または更新する(upsert)。 */
  @Transactional
  public void upsert(String i18nKey, String locale, String text) {
    TranslationEntryId id = new TranslationEntryId(i18nKey, locale);
    TranslationEntry entry =
        repository
            .findById(id)
            .map(
                existing -> {
                  existing.setText(text);
                  return existing;
                })
            .orElseGet(() -> new TranslationEntry(i18nKey, locale, text));
    repository.save(entry);
    cache.reload();
    eventPublisher.publishEvent(
        ConfigChangedEvent.of(
            ConfigChangeOperation.TRANSLATION_UPSERTED,
            "%s[%s]".formatted(i18nKey, locale),
            "system"));
  }

  /** 指定された(i18nKey, locale)のテキストを解決する。未登録の場合は空(呼び出し側で未翻訳表示にフォールバックする、BR1.10)。 */
  public Optional<String> resolve(String i18nKey, String locale) {
    return cache.findTranslation(i18nKey, locale);
  }
}
