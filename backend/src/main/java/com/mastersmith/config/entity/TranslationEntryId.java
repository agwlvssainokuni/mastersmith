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

package com.mastersmith.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** TranslationEntryの複合主キー(i18nKey, locale)(entities.md TranslationEntry)。 */
@Embeddable
public class TranslationEntryId implements Serializable {

  @Column(name = "i18n_key")
  private String i18nKey;

  @Column(name = "locale")
  private String locale;

  protected TranslationEntryId() {
    // JPA用
  }

  public TranslationEntryId(String i18nKey, String locale) {
    this.i18nKey = i18nKey;
    this.locale = locale;
  }

  public String getI18nKey() {
    return i18nKey;
  }

  public String getLocale() {
    return locale;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TranslationEntryId other)) {
      return false;
    }
    return Objects.equals(i18nKey, other.i18nKey) && Objects.equals(locale, other.locale);
  }

  @Override
  public int hashCode() {
    return Objects.hash(i18nKey, locale);
  }

  @Override
  public String toString() {
    return "TranslationEntryId{i18nKey='%s', locale='%s'}".formatted(i18nKey, locale);
  }
}
