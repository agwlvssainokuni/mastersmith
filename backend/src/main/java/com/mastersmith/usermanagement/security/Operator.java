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

package com.mastersmith.usermanagement.security;

/**
 * 操作者(リクエストの認証コンテキストから得た、userIdとactiveRoleId)。解決できなかった項目はnull。
 *
 * @param userId 操作者のuserId
 * @param activeRoleId 操作者が選択中のロール(ロール選択前のトークンではnull)
 */
public record Operator(String userId, String activeRoleId) {

  /** 何も解決できなかった操作者。 */
  public static Operator unresolved() {
    return new Operator(null, null);
  }
}
