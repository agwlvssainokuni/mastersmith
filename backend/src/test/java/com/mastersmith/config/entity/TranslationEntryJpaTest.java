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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** TranslationEntryのJPAマッピングテスト(複合主キー(i18nKey, locale)の保存・取得・一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TranslationEntryJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsByCompositeKey() {
    TranslationEntry entry = new TranslationEntry("table.public.products.label", "ja", "商品");

    entityManager.persist(entry);
    entityManager.flush();
    entityManager.clear();

    TranslationEntry reloaded =
        entityManager.find(
            TranslationEntry.class, new TranslationEntryId("table.public.products.label", "ja"));
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getText()).isEqualTo("商品");
  }

  @Test
  void sameKeyDifferentLocaleAreDistinctRows() {
    entityManager.persist(new TranslationEntry("table.public.products.label", "ja", "商品"));
    entityManager.persist(new TranslationEntry("table.public.products.label", "en", "Product"));
    entityManager.flush();
    entityManager.clear();

    TranslationEntry ja =
        entityManager.find(
            TranslationEntry.class, new TranslationEntryId("table.public.products.label", "ja"));
    TranslationEntry en =
        entityManager.find(
            TranslationEntry.class, new TranslationEntryId("table.public.products.label", "en"));
    assertThat(ja.getText()).isEqualTo("商品");
    assertThat(en.getText()).isEqualTo("Product");
  }

  @Test
  void rejectsDuplicateCompositeKey() {
    entityManager.persist(new TranslationEntry("table.public.products.label", "ja", "商品"));
    entityManager.flush();
    // 永続化コンテキストを空にして、2件目の重複挿入が実際にDB制約で検出されるようにする
    // (同一コンテキスト内では、埋め込みIDが重複する2つ目の永続化はDBに到達する前に
    // NonUniqueObjectExceptionとなるため)。
    entityManager.clear();

    entityManager.persist(new TranslationEntry("table.public.products.label", "ja", "duplicate"));

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }
}
