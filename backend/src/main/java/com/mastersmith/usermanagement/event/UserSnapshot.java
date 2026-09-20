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

package com.mastersmith.usermanagement.event;

import com.mastersmith.usermanagement.entity.User;
import java.util.List;

/**
 * Userの変更前後のスナップショット({@code name}・{@code email}・{@code status}・{@code roleIds}のみ)。{@code
 * passwordHash}と {@code invitationToken}は、認証情報のため、持たない型として定義する(security-design.md NFR2.2、rules.md
 * BR4.9)。
 *
 * @param status 小文字の状態(invited・active・disabled)
 * @param roleIds 直接付与のロールID(ソート済み)
 */
public record UserSnapshot(String name, String email, String status, List<String> roleIds) {

  public UserSnapshot {
    roleIds = List.copyOf(roleIds);
  }

  public static UserSnapshot from(User user) {
    return new UserSnapshot(
        user.getName(), user.getEmail(), user.getStatus().value(), user.getRoleIds());
  }
}
