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

package com.mastersmith.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークンのハッシュを求める(NFR2.3)。トークンの文字列(UTF-8)のSHA-256を、Base64URL(パディングなし、43文字)にする。保存するのは
 * このハッシュだけで、平文は保存・ログ・トレースに出さない。トークンは、256ビットの乱数のため、ソルト・低速なハッシュは要らない。
 */
@Component
public class RefreshTokenHasher {

  public String hash(String refreshToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256は、JDKが必ず提供する。
      throw new IllegalStateException("SHA-256 is not available");
    }
  }
}
