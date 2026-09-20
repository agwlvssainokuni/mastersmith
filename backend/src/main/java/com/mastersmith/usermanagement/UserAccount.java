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

package com.mastersmith.usermanagement;

import com.mastersmith.usermanagement.entity.UserStatus;
import java.util.List;

/**
 * C11のユーザーアカウント({@code UserAccountLookupApi#findByEmail}の戻り値)。
 *
 * @param passwordHash 常にnull(ハッシュ値を呼び出し元へ返さない。検証は{@code verifyPasswordHash}が行う、C11追補)
 * @param roleIds 直接付与分とGroup経由分の和集合(ソート済み)
 */
public record UserAccount(
    String userId, String passwordHash, UserStatus status, List<String> roleIds) {

  public UserAccount {
    roleIds = List.copyOf(roleIds);
  }
}
