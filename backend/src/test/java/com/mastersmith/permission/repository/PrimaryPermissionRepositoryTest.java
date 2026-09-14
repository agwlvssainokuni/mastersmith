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

import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** {@link PrimaryPermissionRepository}の統合テスト(組込みH2)。BR3.13ブートストラップ判定が用いる{@code count()}も検証する。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PrimaryPermissionRepositoryTest {

  @Autowired private PrimaryPermissionRepository repository;

  @Test
  void savesAndFindsById() {
    PrimaryPermission saved =
        repository.save(
            new PrimaryPermission("role-1", ScopeType.TABLE, "table-1", PermissionLevel.FULL));

    assertThat(repository.findById(saved.getPrimaryPermissionId())).isPresent();
  }

  @Test
  void findsByRoleIdAndScopeTypeAndScopeRef() {
    repository.save(
        new PrimaryPermission("role-1", ScopeType.TABLE, "table-1", PermissionLevel.READ));

    Optional<PrimaryPermission> found =
        repository.findByRoleIdAndScopeTypeAndScopeRef("role-1", ScopeType.TABLE, "table-1");
    assertThat(found).isPresent();
    assertThat(found.get().getLevel()).isEqualTo(PermissionLevel.READ);
  }

  @Test
  void findByRoleIdAndScopeTypeAndScopeRefReturnsEmptyWhenNotFound() {
    assertThat(repository.findByRoleIdAndScopeTypeAndScopeRef("role-1", ScopeType.SCHEMA, "public"))
        .isEmpty();
  }

  @Test
  void countReflectsBootstrapState() {
    // BR3.13: PrimaryPermission行数がシステム全体で0件かどうかの判定に、count()をそのまま用いる。
    assertThat(repository.count()).isZero();

    repository.save(
        new PrimaryPermission("role-1", ScopeType.SCHEMA, "public", PermissionLevel.FULL));

    assertThat(repository.count()).isEqualTo(1);
  }
}
