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

/** AuxiliaryPermissionのマッピングテスト(保存・取得の往復、createAllowed/deleteAllowedのnull(指定なし)往復、一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuxiliaryPermissionJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsAllFields() {
    AuxiliaryPermission permission =
        new AuxiliaryPermission("role-1", ScopeType.TABLE, "t1", true, false);

    entityManager.persist(permission);
    entityManager.flush();
    entityManager.clear();

    AuxiliaryPermission reloaded =
        entityManager.find(AuxiliaryPermission.class, permission.getAuxiliaryPermissionId());
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getCreateAllowed()).isTrue();
    assertThat(reloaded.getDeleteAllowed()).isFalse();
  }

  @Test
  void createAllowedAndDeleteAllowedDefaultToNullWhenUnspecified() {
    AuxiliaryPermission permission =
        new AuxiliaryPermission("role-1", ScopeType.SCHEMA, "public", null, null);

    entityManager.persist(permission);
    entityManager.flush();
    entityManager.clear();

    AuxiliaryPermission reloaded =
        entityManager.find(AuxiliaryPermission.class, permission.getAuxiliaryPermissionId());
    assertThat(reloaded.getCreateAllowed()).isNull();
    assertThat(reloaded.getDeleteAllowed()).isNull();
  }

  @Test
  void rejectsDuplicateRoleScopeTypeScopeRef() {
    entityManager.persist(
        new AuxiliaryPermission("role-1", ScopeType.SCHEMA, "public", true, true));
    entityManager.flush();
    entityManager.clear();

    entityManager.persist(
        new AuxiliaryPermission("role-1", ScopeType.SCHEMA, "public", false, false));

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }
}
