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
          MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256は、JDKが必ず提供する。
      throw new IllegalStateException("SHA-256 is not available");
    }
  }
}
