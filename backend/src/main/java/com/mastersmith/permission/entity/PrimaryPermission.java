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
 * ロールに対する主権限(FULL/READ/NONE)の明示的な割当(entities.md PrimaryPermission)。
 *
 * <p>スキーマ・テーブル・カラムの各階層({@link ScopeType})に対して個別に割り当てる。「指定なし」は、当該(roleId, scopeType,
 * scopeRef)の組に対応する行が存在しないことで表現する(明示的な第4の値は持たない、rules.md BR3.6)。
 *
 * <p>{@code scopeRef}は、config-engineが認識するschemaName/tableConfigId/columnConfigId、またはrules.md
 * BR3.15が定める予約スキーマ名(管理系画面用)のいずれかを指す不透明な識別子として扱う。permission-engineはこの値をconfig-engine側の実在チェックに
 * かけない(rules.md BR3.14)。
 *
 * <p>{@code (role_id, scope_type, scope_ref)}に一意複合インデックスを付与する(scalability-design.md
 * インデックス設計、resolveEffectivePermissionのスコープ階層探索を高速化するNFR3.2実装)。
 */
@Entity
@Table(
    name = "primary_permission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "scope_type", "scope_ref"}),
    indexes =
        @Index(name = "ix_primary_permission_scope", columnList = "role_id, scope_type, scope_ref"))
public class PrimaryPermission {

  @Id
  @Column(name = "primary_permission_id", nullable = false, updatable = false, length = 36)
  private String primaryPermissionId;

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

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false)
  private PermissionLevel level;

  protected PrimaryPermission() {
    // JPA用
  }

  public PrimaryPermission(
      String roleId, ScopeType scopeType, String scopeRef, PermissionLevel level) {
    this(UUID.randomUUID().toString(), roleId, scopeType, scopeRef, level);
  }

  public PrimaryPermission(
      String primaryPermissionId,
      String roleId,
      ScopeType scopeType,
      String scopeRef,
      PermissionLevel level) {
    this.primaryPermissionId = primaryPermissionId;
    this.roleId = roleId;
    this.scopeType = scopeType;
    this.scopeRef = scopeRef;
    this.level = level;
  }

  public String getPrimaryPermissionId() {
    return primaryPermissionId;
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

  public PermissionLevel getLevel() {
    return level;
  }

  public void setLevel(PermissionLevel level) {
    this.level = level;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PrimaryPermission other)) {
      return false;
    }
    return Objects.equals(primaryPermissionId, other.primaryPermissionId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(primaryPermissionId);
  }

  @Override
  public String toString() {
    return "PrimaryPermission{roleId='%s', scopeType=%s, scopeRef='%s', level=%s}"
        .formatted(roleId, scopeType, scopeRef, level);
  }
}
