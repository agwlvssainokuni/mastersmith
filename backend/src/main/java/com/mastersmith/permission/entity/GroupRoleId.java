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
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** GroupRoleの複合主キー(groupId, roleId)(entities.md GroupRole、(groupId, roleId)の組は一意)。 */
@Embeddable
public class GroupRoleId implements Serializable {

  @Column(name = "group_id")
  private String groupId;

  @Column(name = "role_id")
  private String roleId;

  protected GroupRoleId() {
    // JPA用
  }

  public GroupRoleId(String groupId, String roleId) {
    this.groupId = groupId;
    this.roleId = roleId;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getRoleId() {
    return roleId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupRoleId other)) {
      return false;
    }
    return Objects.equals(groupId, other.groupId) && Objects.equals(roleId, other.roleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, roleId);
  }

  @Override
  public String toString() {
    return "GroupRoleId{groupId='%s', roleId='%s'}".formatted(groupId, roleId);
  }
}
