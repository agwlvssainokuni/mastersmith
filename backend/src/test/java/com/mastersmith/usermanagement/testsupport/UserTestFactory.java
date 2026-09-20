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

package com.mastersmith.usermanagement.testsupport;

import com.mastersmith.usermanagement.entity.User;
import java.util.List;
import java.util.UUID;

/** U4のテストで、独立したテストデータを構築するファクトリ(emailはUUIDを含めて一意にする)。 */
public final class UserTestFactory {

  /** テストで用いるダミーのArgon2id形式のハッシュ(実在しない値。パラメータの部分だけが意味を持つ)。 */
  public static final String DUMMY_PASSWORD_HASH =
      "$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHRzb21lc2FsdA$ZHVtbXloYXNoZHVtbXloYXNoZHVtbXloYXNoMTI";

  private UserTestFactory() {}

  public static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.test";
  }

  public static String newToken() {
    return UUID.randomUUID().toString();
  }

  public static User invitedUser() {
    return User.invited("招待 太郎", uniqueEmail(), List.of("role-a"), newToken());
  }

  public static User invitedUser(String email, List<String> roleIds, String token) {
    return User.invited("招待 太郎", email, roleIds, token);
  }

  public static User activeUser() {
    return User.activeAdmin("有効 花子", uniqueEmail(), DUMMY_PASSWORD_HASH, List.of("role-a"));
  }

  public static User activeUser(String email, List<String> roleIds) {
    return User.activeAdmin("有効 花子", email, DUMMY_PASSWORD_HASH, roleIds);
  }
}
