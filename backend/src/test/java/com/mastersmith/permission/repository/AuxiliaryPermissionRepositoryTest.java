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

import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link AuxiliaryPermissionRepository}の統合テスト(組込みH2)。createAllowed/deleteAllowedのnull(指定なし)往復も検証する。
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuxiliaryPermissionRepositoryTest {

  @Autowired private AuxiliaryPermissionRepository repository;

  @Test
  void savesAndFindsById() {
    AuxiliaryPermission saved =
        repository.save(new AuxiliaryPermission("role-1", ScopeType.TABLE, "table-1", true, false));

    assertThat(repository.findById(saved.getAuxiliaryPermissionId())).isPresent();
  }

  @Test
  void findsByRoleIdAndScopeTypeAndScopeRef() {
    repository.save(new AuxiliaryPermission("role-1", ScopeType.TABLE, "table-1", true, null));

    Optional<AuxiliaryPermission> found =
        repository.findByRoleIdAndScopeTypeAndScopeRef("role-1", ScopeType.TABLE, "table-1");
    assertThat(found).isPresent();
    assertThat(found.get().getCreateAllowed()).isTrue();
    assertThat(found.get().getDeleteAllowed()).isNull();
  }

  @Test
  void findByRoleIdAndScopeTypeAndScopeRefReturnsEmptyWhenNotFound() {
    assertThat(repository.findByRoleIdAndScopeTypeAndScopeRef("role-1", ScopeType.SCHEMA, "public"))
        .isEmpty();
  }

  @Test
  void bothFieldsCanBeNullSimultaneously() {
    AuxiliaryPermission saved =
        repository.save(new AuxiliaryPermission("role-1", ScopeType.SCHEMA, "public", null, null));

    AuxiliaryPermission reloaded =
        repository.findById(saved.getAuxiliaryPermissionId()).orElseThrow();
    assertThat(reloaded.getCreateAllowed()).isNull();
    assertThat(reloaded.getDeleteAllowed()).isNull();
  }
}
