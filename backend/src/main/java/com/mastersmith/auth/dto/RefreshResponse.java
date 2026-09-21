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
 * リフレッシュの成功のレスポンス(C4の追補、BR5.6)。ローテーションした新しいリフレッシュトークンと、選択可能なロール・アクティブロール(未選択ならnull)を返す (FR4.2:
 * ページの再読み込みのあとも、ロール選択・ヘッダーの表示ができる)。{@code toString}は、トークンを含めない(NFR2.7)。
 */
public record RefreshResponse(
    String accessToken, String refreshToken, List<String> roles, String activeRoleId) {

  @Override
  public String toString() {
    return "RefreshResponse[activeRoleId=" + activeRoleId + "]";
  }
}
