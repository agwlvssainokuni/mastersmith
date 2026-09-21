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

package com.mastersmith.auth.dto;

import java.util.List;

/**
 * ログインの成功のレスポンス(C4)。選択可能なロール({@code roles})は、直接付与分とGroup経由分の和集合(BR5.9)。{@code
 * activeRoleId}は、未選択ならnull。 {@code toString}は、トークンを含めない(NFR2.7)。
 */
public record LoginResponse(
    String accessToken, String refreshToken, List<String> roles, String activeRoleId) {

  @Override
  public String toString() {
    return "LoginResponse[activeRoleId=" + activeRoleId + "]";
  }
}
