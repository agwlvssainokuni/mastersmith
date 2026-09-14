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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;

/**
 * ロールに対する補助権限(CREATE/DELETE)の明示的な割当(entities.md AuxiliaryPermission)。
 *
 * <p>スキーマ・テーブル単位のみを対象とする({@link ScopeType#COLUMN}は対象外)。{@code createAllowed}/{@code
 * deleteAllowed}は、それぞれ独立に「許可/禁止/指定なし」の3値を取る(フィールドが{@code null} = 指定なし、rules.md BR3.5)。
 *
 * <p>{@code (role_id, scope_type, scope_ref)}に一意複合インデックスを付与する(scalability-design.mdインデックス設計)。
 */
@Entity
@Table(
    name = "auxiliary_permission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "scope_type", "scope_ref"}),
    indexes =
        @Index(
            name = "ix_auxiliary_permission_scope",
            columnList = "role_id, scope_type, scope_ref"))
public class AuxiliaryPermission {

  @Id
  @Column(name = "auxiliary_permission_id", nullable = false, updatable = false, length = 36)
  private String auxiliaryPermissionId;

  @NotBlank
  @Column(name = "role_id", nullable = false)
  private String roleId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "scope_type", nullable = false)
  private ScopeType scopeType;

  @NotBlank
  @Column(name = "scope_ref", nullable = false, length = 255)
  private String scopeRef;

  /** {@code null} = 指定なし。{@code true} = 許可。{@code false} = 禁止。 */
  @Column(name = "create_allowed")
  private Boolean createAllowed;

  /** {@code null} = 指定なし。{@code true} = 許可。{@code false} = 禁止。 */
  @Column(name = "delete_allowed")
  private Boolean deleteAllowed;

  protected AuxiliaryPermission() {
    // JPA用
  }

  public AuxiliaryPermission(
      String roleId,
      ScopeType scopeType,
      String scopeRef,
      Boolean createAllowed,
      Boolean deleteAllowed) {
    this(UUID.randomUUID().toString(), roleId, scopeType, scopeRef, createAllowed, deleteAllowed);
  }

  public AuxiliaryPermission(
      String auxiliaryPermissionId,
      String roleId,
      ScopeType scopeType,
      String scopeRef,
      Boolean createAllowed,
      Boolean deleteAllowed) {
    this.auxiliaryPermissionId = auxiliaryPermissionId;
    this.roleId = roleId;
    this.scopeType = scopeType;
    this.scopeRef = scopeRef;
    this.createAllowed = createAllowed;
    this.deleteAllowed = deleteAllowed;
  }

  public String getAuxiliaryPermissionId() {
    return auxiliaryPermissionId;
  }

  public String getRoleId() {
    return roleId;
  }

  public ScopeType getScopeType() {
    return scopeType;
  }

  public String getScopeRef() {
    return scopeRef;
  }

  public Boolean getCreateAllowed() {
    return createAllowed;
  }

  public void setCreateAllowed(Boolean createAllowed) {
    this.createAllowed = createAllowed;
  }

  public Boolean getDeleteAllowed() {
    return deleteAllowed;
  }

  public void setDeleteAllowed(Boolean deleteAllowed) {
    this.deleteAllowed = deleteAllowed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AuxiliaryPermission other)) {
      return false;
    }
    return Objects.equals(auxiliaryPermissionId, other.auxiliaryPermissionId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(auxiliaryPermissionId);
  }

  @Override
  public String toString() {
    return "AuxiliaryPermission{roleId='%s', scopeType=%s, scopeRef='%s', createAllowed=%s, deleteAllowed=%s}"
        .formatted(roleId, scopeType, scopeRef, createAllowed, deleteAllowed);
  }
}
