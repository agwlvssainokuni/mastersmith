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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import java.util.UUID;

/**
 * 権限判定の単位となるロール(entities.md Role)。
 *
 * <p>Domain Design(inception/domain-design/components.md)では{@code parentRoleId}(親ロール参照)を持つ木構造として
 * 定義されていたが、functional-design(construction/permission-engine/functional-design/entities.md「Domain
 * Designからの逸脱」)での確認の結果、ロール間の階層継承は本MVPでは実装しないことが確定した。実効権限の階層継承は、スコープ({@link ScopeType#COLUMN} →
 * {@link ScopeType#TABLE} → {@link ScopeType#SCHEMA})の1軸のみで行う(rules.md BR3.4)。
 */
@Entity
@Table(name = "role", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class Role {

  @Id
  @Column(name = "role_id", nullable = false, updatable = false, length = 36)
  private String roleId;

  @NotBlank
  @Column(name = "name", nullable = false)
  private String name;

  protected Role() {
    // JPA用
  }

  public Role(String name) {
    this(UUID.randomUUID().toString(), name);
  }

  public Role(String roleId, String name) {
    this.roleId = roleId;
    this.name = name;
  }

  public String getRoleId() {
    return roleId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Role other)) {
      return false;
    }
    return Objects.equals(roleId, other.roleId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(roleId);
  }

  @Override
  public String toString() {
    return "Role{roleId='%s', name='%s'}".formatted(roleId, name);
  }
}
