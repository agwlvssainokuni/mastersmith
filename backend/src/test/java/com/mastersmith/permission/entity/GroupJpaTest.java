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

package com.mastersmith.permission.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** Groupのマッピングテスト(保存・取得の往復、nameの一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GroupJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsAllFields() {
    Group group = new Group("sales-team");

    entityManager.persist(group);
    entityManager.flush();
    entityManager.clear();

    Group reloaded = entityManager.find(Group.class, group.getGroupId());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getName()).isEqualTo("sales-team");
  }

  @Test
  void rejectsDuplicateName() {
    entityManager.persist(new Group("duplicate-group"));
    entityManager.flush();
    entityManager.clear();

    entityManager.persist(new Group("duplicate-group"));

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }
}
