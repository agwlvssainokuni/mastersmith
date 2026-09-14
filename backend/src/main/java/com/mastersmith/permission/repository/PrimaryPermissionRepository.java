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

import com.mastersmith.permission.entity.PrimaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link PrimaryPermission}のSpring Data JPAリポジトリ。{@code
 * findByRoleIdAndScopeTypeAndScopeRef}が、resolveEffectivePermissionのスコープ階層探索(rules.md
 * BR3.4)の各段の等値検索を担う({@code (role_id, scope_type, scope_ref)}複合インデックス、scalability-design.md)。
 */
public interface PrimaryPermissionRepository extends JpaRepository<PrimaryPermission, String> {

  Optional<PrimaryPermission> findByRoleIdAndScopeTypeAndScopeRef(
      String roleId, ScopeType scopeType, String scopeRef);

  // BR3.13ブートストラップ判定(PrimaryPermission行数がシステム全体で0件か)は、
  // 継承元JpaRepositoryのcount()をそのまま利用する(専用メソッドの追加は不要)。
}
