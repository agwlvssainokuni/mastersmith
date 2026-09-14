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

/**
 * GroupとUser(user-managementが所有するエンティティ、本ユニットからはuserIdの外部参照として扱う)の多対多関連を表す中間エンティティ (entities.md
 * GroupMembership)。
 */
@Entity
@Table(name = "group_membership")
public class GroupMembership {

  @EmbeddedId private GroupMembershipId id;

  protected GroupMembership() {
    // JPA用
  }

  public GroupMembership(String groupId, String userId) {
    this.id = new GroupMembershipId(groupId, userId);
  }

  public GroupMembershipId getId() {
    return id;
  }

  public String getGroupId() {
    return id.getGroupId();
  }

  public String getUserId() {
    return id.getUserId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupMembership other)) {
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
    return "GroupMembership{id=%s}".formatted(id);
  }
}
