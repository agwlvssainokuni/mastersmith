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

/** PrimaryPermissionのマッピングテスト(保存・取得の往復、(role_id, scope_type, scope_ref)の一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PrimaryPermissionJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsAllFields() {
    PrimaryPermission permission =
        new PrimaryPermission("role-1", ScopeType.COLUMN, "col-1", PermissionLevel.FULL);

    entityManager.persist(permission);
    entityManager.flush();
    entityManager.clear();

    PrimaryPermission reloaded =
        entityManager.find(PrimaryPermission.class, permission.getPrimaryPermissionId());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getRoleId()).isEqualTo("role-1");
    assertThat(reloaded.getScopeType()).isEqualTo(ScopeType.COLUMN);
    assertThat(reloaded.getScopeRef()).isEqualTo("col-1");
    assertThat(reloaded.getLevel()).isEqualTo(PermissionLevel.FULL);
  }

  @Test
  void rejectsDuplicateRoleScopeTypeScopeRef() {
    entityManager.persist(
        new PrimaryPermission("role-1", ScopeType.SCHEMA, "public", PermissionLevel.FULL));
    entityManager.flush();
    entityManager.clear();

    entityManager.persist(
        new PrimaryPermission("role-1", ScopeType.SCHEMA, "public", PermissionLevel.READ));

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }

  @Test
  void sameScopeRefDifferentScopeTypeAreDistinctRows() {
    entityManager.persist(
        new PrimaryPermission("role-1", ScopeType.TABLE, "t1", PermissionLevel.FULL));
    entityManager.persist(
        new PrimaryPermission("role-1", ScopeType.SCHEMA, "t1", PermissionLevel.READ));

    entityManager.flush();

    assertThat(entityManager.createQuery("select p from PrimaryPermission p").getResultList())
        .hasSize(2);
  }
}
