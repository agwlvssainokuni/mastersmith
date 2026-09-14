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

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

/** GroupとRoleの多対多関連を表す中間エンティティ(entities.md GroupRole)。 */
@Entity
@Table(name = "group_role")
public class GroupRole {

  @EmbeddedId private GroupRoleId id;

  protected GroupRole() {
    // JPA用
  }

  public GroupRole(String groupId, String roleId) {
    this.id = new GroupRoleId(groupId, roleId);
  }

  public GroupRoleId getId() {
    return id;
  }

  public String getGroupId() {
    return id.getGroupId();
  }

  public String getRoleId() {
    return id.getRoleId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupRole other)) {
      return false;
    }
    return Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return "GroupRole{id=%s}".formatted(id);
  }
}
