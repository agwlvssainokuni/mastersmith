package com.mastersmith.auth.token;

import com.mastersmith.auth.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * アクセストークン(JWT、HS256)を署名して発行する(BR5.5、NFR2.2)。運ぶ値は{@code sub}(userId)・{@code sid}(sessionId)・{@code iat}・
 * {@code exp}だけで、アクティブロール・メールアドレス・氏名は含めない。{@code exp}は、注入された{@link Clock}の現在時刻に、設定の有効期限を加えた値とする。
 */
@Component
public class AccessTokenIssuer {

  private final JwtKeyProvider keyProvider;
  private final AuthProperties properties;
  private final Clock clock;

  public AccessTokenIssuer(JwtKeyProvider keyProvider, AuthProperties properties, Clock clock) {
    this.keyProvider = keyProvider;
    this.properties = properties;
    this.clock = clock;
  }

  /** アクセストークンを発行する。 */
  public String issue(String userId, String sessionId) {
    Instant issuedAt = clock.instant();
    Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(userId)
            .claim("sid", sessionId)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    try {
      jwt.sign(new MACSigner(keyProvider.key().copyOfBytes()));
    } catch (JOSEException e) {
      // 署名の失敗は、鍵の不備(起動時に検証済み)以外では起こらない。メッセージ(鍵の情報を含みうる)は連鎖させない。
      throw new IllegalStateException("Failed to sign the access token");
    }
    return jwt.serialize();
  }
}
