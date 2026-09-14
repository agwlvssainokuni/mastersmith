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

/** Roleのマッピングテスト(保存・取得の往復、nameの一意性制約)。ロールは親子階層を持たない(entities.md「Domain Designからの逸脱」)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RoleJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsAllFields() {
    Role role = new Role("data-entry-clerk");

    entityManager.persist(role);
    entityManager.flush();
    entityManager.clear();

    Role reloaded = entityManager.find(Role.class, role.getRoleId());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getName()).isEqualTo("data-entry-clerk");
  }

  @Test
  void rejectsDuplicateName() {
    entityManager.persist(new Role("duplicate-role"));
    entityManager.flush();
    entityManager.clear();

    entityManager.persist(new Role("duplicate-role"));

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }

  @Test
  void twoRolesAreDistinctInstancesEvenWithTheSameNameBeforeThePersistenceContextFlushes() {
    Role first = new Role("role-a");
    Role second = new Role("role-b");

    assertThat(first).isNotEqualTo(second);
    assertThat(first.getRoleId()).isNotEqualTo(second.getRoleId());
  }
}
