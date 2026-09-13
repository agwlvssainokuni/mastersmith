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

package com.mastersmith.config.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.entity.TranslationEntryId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link TranslationEntryRepository}の統合テスト(組込みH2)。複合主キーによる検索。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TranslationEntryRepositoryTest {

  @Autowired private TranslationEntryRepository repository;

  @Test
  void savesAndFindsByCompositeId() {
    repository.save(new TranslationEntry("table.public.products.label", "ja", "商品"));

    Optional<TranslationEntry> found =
        repository.findById(new TranslationEntryId("table.public.products.label", "ja"));

    assertThat(found).isPresent();
    assertThat(found.get().getText()).isEqualTo("商品");
  }

  @Test
  void findByIdReturnsEmptyWhenLocaleDoesNotMatch() {
    repository.save(new TranslationEntry("table.public.products.label", "ja", "商品"));

    Optional<TranslationEntry> found =
        repository.findById(new TranslationEntryId("table.public.products.label", "en"));

    assertThat(found).isEmpty();
  }
}
