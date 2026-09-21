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

import com.mastersmith.permission.entity.AuxiliaryPermission;
import com.mastersmith.permission.entity.ScopeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link AuxiliaryPermission}のSpring Data JPAリポジトリ。{@code
 * findByRoleIdAndScopeTypeAndScopeRef}が、resolveEffectivePermissionの補助権限のスコープ階層探索(rules.md
 * BR3.5)の各段の等値検索を担う。
 */
public interface AuxiliaryPermissionRepository extends JpaRepository<AuxiliaryPermission, String> {

  Optional<AuxiliaryPermission> findByRoleIdAndScopeTypeAndScopeRef(
      String roleId, ScopeType scopeType, String scopeRef);

  /** 指定のロールの、すべての割当(取り込みの、操作者の実効権限の解決を、メモリ上で行うために、一括で読む)。 */
  List<AuxiliaryPermission> findByRoleId(String roleId);
}
