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
 * 複数のUserへロールをまとめて付与するための単位(entities.md Group、FR4.1「グループ」)。
 *
 * <p>inception/domain-design/components.mdには存在しないエンティティであり、functional-design(construction
 * /permission-engine/functional-design/entities.md)で新設した。{@link GroupMembership}(Group⇔User)・{@link
 * GroupRole}(Group⇔Role)を介して、Userは直接付与のRoleに加え、所属Group経由でもRoleを得る(rules.md BR3.2, BR3.3)。
 */
@Entity
@Table(name = "permission_group", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class Group {

  @Id
  @Column(name = "group_id", nullable = false, updatable = false, length = 36)
  private String groupId;

  @NotBlank
  @Column(name = "name", nullable = false)
  private String name;

  protected Group() {
    // JPA用
  }

  public Group(String name) {
    this(UUID.randomUUID().toString(), name);
  }

  public Group(String groupId, String name) {
    this.groupId = groupId;
    this.name = name;
  }

  public String getGroupId() {
    return groupId;
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
    if (!(o instanceof Group other)) {
      return false;
    }
    return Objects.equals(groupId, other.groupId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(groupId);
  }

  @Override
  public String toString() {
    return "Group{groupId='%s', name='%s'}".formatted(groupId, name);
  }
}
