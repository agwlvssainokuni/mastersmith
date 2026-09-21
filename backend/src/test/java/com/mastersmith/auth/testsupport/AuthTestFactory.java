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

package com.mastersmith.auth.testsupport;

import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.token.RefreshTokenGenerator;
import com.mastersmith.auth.token.RefreshTokenHasher;
import com.mastersmith.auth.token.SessionIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** authentication-serviceのテストデータの生成(テストメソッドごとに、独立した一意の値を作る)。 */
public final class AuthTestFactory {

  private static final SessionIdGenerator SESSION_IDS = new SessionIdGenerator();
  private static final RefreshTokenGenerator REFRESH_TOKENS = new RefreshTokenGenerator();
  private static final RefreshTokenHasher HASHER = new RefreshTokenHasher();

  private AuthTestFactory() {}

  public static String uniqueUserId() {
    return "auth-test-user-" + UUID.randomUUID();
  }

  public static String uniqueRoleId() {
    return "auth-test-role-" + UUID.randomUUID();
  }

  public static String newSessionId() {
    return SESSION_IDS.generate();
  }

  /** 新しいリフレッシュトークンの平文。 */
  public static String newRefreshToken() {
    return REFRESH_TOKENS.generate();
  }

  public static String hashOf(String refreshToken) {
    return HASHER.hash(refreshToken);
  }

  /** 一意なハッシュ(43文字)。 */
  public static String newHash() {
    return hashOf(newRefreshToken());
  }

  /** 有効なSession(発行は{@code issuedAt}、リフレッシュの有効期限は{@code issuedAt + ttl})。 */
  public static Session session(
      String userId, String activeRoleId, String refreshTokenHash, Instant issuedAt, Duration ttl) {
    return Session.issue(
        newSessionId(), userId, activeRoleId, refreshTokenHash, issuedAt, issuedAt.plus(ttl));
  }
}
