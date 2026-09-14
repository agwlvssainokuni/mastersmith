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

import com.mastersmith.permission.entity.Group;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link GroupRepository}の統合テスト(組込みH2)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GroupRepositoryTest {

  @Autowired private GroupRepository repository;

  @Test
  void savesAndFindsById() {
    Group saved = repository.save(new Group("sales-team"));

    Optional<Group> found = repository.findById(saved.getGroupId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("sales-team");
  }

  @Test
  void findsByName() {
    repository.save(new Group("support-team"));

    assertThat(repository.findByName("support-team")).isPresent();
  }

  @Test
  void findByNameReturnsEmptyWhenNotFound() {
    assertThat(repository.findByName("no-such-group")).isEmpty();
  }
}
