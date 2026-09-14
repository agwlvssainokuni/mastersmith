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

package com.mastersmith.permission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.permission.entity.GroupMembership;
import com.mastersmith.permission.entity.GroupMembershipId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link GroupMembershipRepository}の統合テスト(組込みH2)。複合主キー(groupId, userId)の往復・一意性制約を検証する。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GroupMembershipRepositoryTest {

  @Autowired private GroupMembershipRepository repository;

  @Test
  void savesAndFindsByCompositeKey() {
    repository.save(new GroupMembership("group-1", "user-1"));

    assertThat(repository.findById(new GroupMembershipId("group-1", "user-1"))).isPresent();
  }

  @Test
  void findsAllGroupsForAUserId() {
    repository.save(new GroupMembership("group-1", "user-1"));
    repository.save(new GroupMembership("group-2", "user-1"));
    repository.save(new GroupMembership("group-3", "user-2"));

    List<GroupMembership> memberships = repository.findByIdUserId("user-1");

    assertThat(memberships)
        .hasSize(2)
        .extracting(GroupMembership::getGroupId)
        .containsExactlyInAnyOrder("group-1", "group-2");
  }

  @Test
  void findByIdUserIdReturnsEmptyListWhenUserBelongsToNoGroup() {
    assertThat(repository.findByIdUserId("lone-user")).isEmpty();
  }
}
