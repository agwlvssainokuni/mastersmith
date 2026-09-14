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

/** GroupMembershipのマッピングテスト(複合主キー(groupId, userId)の保存・取得・一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GroupMembershipJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsByCompositeKey() {
    GroupMembership membership = new GroupMembership("group-1", "user-1");

    entityManager.persist(membership);
    entityManager.flush();
    entityManager.clear();

    GroupMembership reloaded =
        entityManager.find(GroupMembership.class, new GroupMembershipId("group-1", "user-1"));
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.getGroupId()).isEqualTo("group-1");
    assertThat(reloaded.getUserId()).isEqualTo("user-1");
  }

  @Test
  void rejectsDuplicateCompositeKey() {
    entityManager.persist(new GroupMembership("group-1", "user-1"));
    entityManager.flush();
    // 永続化コンテキストを空にして、2件目の重複挿入が実際にDB制約で検出されるようにする
    // (config-engine TranslationEntryJpaTestと同じ理由: 同一コンテキスト内では重複するIDの
    // 2つ目の永続化がDBに到達する前にNonUniqueObjectExceptionとなるため)。
    entityManager.clear();

    entityManager.persist(new GroupMembership("group-1", "user-1"));

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }

  @Test
  void sameGroupDifferentUserAreDistinctRows() {
    entityManager.persist(new GroupMembership("group-1", "user-1"));
    entityManager.persist(new GroupMembership("group-1", "user-2"));

    entityManager.flush();

    assertThat(entityManager.createQuery("select m from GroupMembership m").getResultList())
        .hasSize(2);
  }
}
