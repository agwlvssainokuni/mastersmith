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

/** GroupRoleのマッピングテスト(複合主キー(groupId, roleId)の保存・取得・一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GroupRoleJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsByCompositeKey() {
    GroupRole groupRole = new GroupRole("group-1", "role-1");

    entityManager.persist(groupRole);
    entityManager.flush();
    entityManager.clear();

    GroupRole reloaded = entityManager.find(GroupRole.class, new GroupRoleId("group-1", "role-1"));
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getGroupId()).isEqualTo("group-1");
    assertThat(reloaded.getRoleId()).isEqualTo("role-1");
  }

  @Test
  void rejectsDuplicateCompositeKey() {
    entityManager.persist(new GroupRole("group-1", "role-1"));
    entityManager.flush();
    entityManager.clear();

    entityManager.persist(new GroupRole("group-1", "role-1"));

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }
}
