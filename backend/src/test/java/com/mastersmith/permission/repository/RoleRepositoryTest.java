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

import com.mastersmith.permission.entity.Role;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link RoleRepository}の統合テスト(組込みH2)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RoleRepositoryTest {

  @Autowired private RoleRepository repository;

  @Test
  void savesAndFindsById() {
    Role saved = repository.save(new Role("editor"));

    Optional<Role> found = repository.findById(saved.getRoleId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("editor");
  }

  @Test
  void findsByName() {
    repository.save(new Role("viewer"));

    assertThat(repository.findByName("viewer")).isPresent();
  }

  @Test
  void findByNameReturnsEmptyWhenNotFound() {
    assertThat(repository.findByName("no-such-role")).isEmpty();
  }

  @Test
  void existsByIdReflectsPersistedState() {
    Role saved = repository.save(new Role("admin"));

    assertThat(repository.existsById(saved.getRoleId())).isTrue();
    assertThat(repository.existsById("unknown-role-id")).isFalse();
  }
}
