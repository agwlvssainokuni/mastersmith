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

import com.mastersmith.config.entity.TableConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link TableConfigRepository}の統合テスト(組込みH2)。基本CRUD・検索メソッドの動作確認。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TableConfigRepositoryTest {

  @Autowired private TableConfigRepository repository;

  @Test
  void savesAndFindsById() {
    TableConfig saved = repository.save(new TableConfig("public", "products"));

    Optional<TableConfig> found = repository.findById(saved.getTableConfigId());
    assertThat(found).isPresent();
    assertThat(found.get().getTableName()).isEqualTo("products");
  }

  @Test
  void findsBySchemaNameAndTableName() {
    repository.save(new TableConfig("public", "orders"));

    Optional<TableConfig> found = repository.findBySchemaNameAndTableName("public", "orders");
    assertThat(found).isPresent();
  }

  @Test
  void findBySchemaNameAndTableNameReturnsEmptyWhenNotFound() {
    Optional<TableConfig> found =
        repository.findBySchemaNameAndTableName("public", "does_not_exist");
    assertThat(found).isEmpty();
  }

  @Test
  void existsBySchemaNameAndTableNameReflectsPersistedState() {
    assertThat(repository.existsBySchemaNameAndTableName("public", "categories")).isFalse();

    repository.save(new TableConfig("public", "categories"));

    assertThat(repository.existsBySchemaNameAndTableName("public", "categories")).isTrue();
  }

  @Test
  void deleteRemovesTheRow() {
    TableConfig saved = repository.save(new TableConfig("public", "to_delete"));

    repository.deleteById(saved.getTableConfigId());

    assertThat(repository.findById(saved.getTableConfigId())).isEmpty();
  }
}
