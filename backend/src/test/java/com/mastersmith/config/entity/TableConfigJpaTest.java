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

import com.mastersmith.config.testsupport.TableConfigTestFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** TableConfigのJPAマッピングテスト(保存・取得の往復、(schemaName, tableName)の一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TableConfigJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsAllFields() {
    TableConfig tableConfig = TableConfigTestFactory.aTableConfig("public", "inventory_items");
    tableConfig.setDisplayOrder(3);
    tableConfig.setOptimisticLockColumn("updated_at");

    entityManager.persist(tableConfig);
    entityManager.flush();
    entityManager.clear();

    TableConfig reloaded = entityManager.find(TableConfig.class, tableConfig.getTableConfigId());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getSchemaName()).isEqualTo("public");
    assertThat(reloaded.getTableName()).isEqualTo("inventory_items");
    assertThat(reloaded.getDisplayOrder()).isEqualTo(3);
    assertThat(reloaded.getOptimisticLockColumn()).isEqualTo("updated_at");
  }

  @Test
  void optimisticLockColumnDefaultsToNull() {
    TableConfig tableConfig = TableConfigTestFactory.aTableConfig("public", "no_lock_column");

    entityManager.persist(tableConfig);
    entityManager.flush();
    entityManager.clear();

    TableConfig reloaded = entityManager.find(TableConfig.class, tableConfig.getTableConfigId());
    assertThat(reloaded.getOptimisticLockColumn()).isNull();
  }

  @Test
  void rejectsDuplicateSchemaNameAndTableName() {
    TableConfig first = TableConfigTestFactory.aTableConfig("public", "duplicate_target");
    entityManager.persist(first);
    entityManager.flush();

    TableConfig duplicate = TableConfigTestFactory.aTableConfig("public", "duplicate_target");
    entityManager.persist(duplicate);

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }

  @Test
  void labelI18nKeyIsDerivedFromSchemaAndTableName() {
    TableConfig tableConfig = TableConfigTestFactory.aTableConfig("sales", "orders");
    assertThat(tableConfig.labelI18nKey()).isEqualTo("table.sales.orders.label");
  }
}
