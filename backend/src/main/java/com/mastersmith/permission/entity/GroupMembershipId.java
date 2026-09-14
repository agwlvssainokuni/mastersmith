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

/** GroupMembershipの複合主キー(groupId, userId)(entities.md GroupMembership、(groupId, userId)の組は一意)。 */
@Embeddable
public class GroupMembershipId implements Serializable {

  @Column(name = "group_id")
  private String groupId;

  @Column(name = "user_id")
  private String userId;

  protected GroupMembershipId() {
    // JPA用
  }

  public GroupMembershipId(String groupId, String userId) {
    this.groupId = groupId;
    this.userId = userId;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupMembershipId other)) {
      return false;
    }
    return Objects.equals(groupId, other.groupId) && Objects.equals(userId, other.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, userId);
  }

  @Override
  public String toString() {
    return "GroupMembershipId{groupId='%s', userId='%s'}".formatted(groupId, userId);
  }
}
