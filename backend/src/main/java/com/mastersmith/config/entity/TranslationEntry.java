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
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * 業務設定層のi18nキーに対する言語別テキスト(entities.md TranslationEntry)。
 *
 * <p>TableConfig/ColumnConfigの表示名・バリデーションメッセージ・静的選択肢displayNameの
 * i18nキーに対応する言語別テキストを、管理画面から登録・編集可能な実行時データとして保持する (rules.md
 * BR1.10)。基盤(エンジン)層の固定UI文言は対象外であり、引き続きビルド成果物の 翻訳リソースファイルで管理する。
 */
@Entity
@Table(name = "translation_entry")
public class TranslationEntry {

  @EmbeddedId private TranslationEntryId id;

  @NotBlank
  @Column(name = "text")
  private String text;

  protected TranslationEntry() {
    // JPA用
  }

  public TranslationEntry(String i18nKey, String locale, String text) {
    this.id = new TranslationEntryId(i18nKey, locale);
    this.text = text;
  }

  public TranslationEntryId getId() {
    return id;
  }

  public String getI18nKey() {
    return id.getI18nKey();
  }

  public String getLocale() {
    return id.getLocale();
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TranslationEntry other)) {
      return false;
    }
    return Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return "TranslationEntry{id=%s}".formatted(id);
  }
}
