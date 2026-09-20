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

package com.mastersmith.usermanagement.dto;

import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.repository.UserSummary;
import java.util.List;

/**
 * Userの応答型(C5の{@code User}スキーマ)。{@code passwordHash}と{@code
 * invitationToken}を持たない型として定義し、誤って出力される経路を作らない (security-design.md NFR2.2)。
 *
 * @param status 小文字の状態(active・invited・disabled)
 */
public record UserResponse(
    String userId, String name, String email, String status, List<String> roleIds) {

  public UserResponse {
    roleIds = List.copyOf(roleIds);
  }

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getUserId(),
        user.getName(),
        user.getEmail(),
        user.getStatus().value(),
        user.getRoleIds());
  }

  public static UserResponse from(UserSummary summary) {
    return new UserResponse(
        summary.userId(),
        summary.name(),
        summary.email(),
        summary.status().value(),
        summary.roleIds());
  }
}
