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

import com.mastersmith.permission.entity.GroupRole;
import com.mastersmith.permission.entity.GroupRoleId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link GroupRoleRepository}の統合テスト(組込みH2)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GroupRoleRepositoryTest {

  @Autowired private GroupRoleRepository repository;

  @Test
  void savesAndFindsByCompositeKey() {
    repository.save(new GroupRole("group-1", "role-1"));

    assertThat(repository.findById(new GroupRoleId("group-1", "role-1"))).isPresent();
  }

  @Test
  void findsAllRolesGrantedToMultipleGroups() {
    repository.save(new GroupRole("group-1", "role-1"));
    repository.save(new GroupRole("group-1", "role-2"));
    repository.save(new GroupRole("group-2", "role-3"));
    repository.save(new GroupRole("group-3", "role-4"));

    List<GroupRole> found = repository.findByIdGroupIdIn(List.of("group-1", "group-2"));

    assertThat(found)
        .hasSize(3)
        .extracting(GroupRole::getRoleId)
        .containsExactlyInAnyOrder("role-1", "role-2", "role-3");
  }

  @Test
  void findByIdGroupIdInReturnsEmptyListWhenNoGroupMatches() {
    assertThat(repository.findByIdGroupIdIn(List.of("no-such-group"))).isEmpty();
  }
}
