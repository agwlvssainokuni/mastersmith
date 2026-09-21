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

import com.mastersmith.auth.exception.InvalidAccessTokenException;
import com.mastersmith.auth.observation.UnauthorizedReason;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * アクセストークン(JWT)を検証する(NFR2.2)。Nimbus JOSE + JWTを直接使い、Spring SecurityのOAuth2 Resource Serverの{@code
 * JwtDecoder}は 使わない(署名方式のHS256限定・時計のずれ0・すべての失敗を同一の401にする、を自前で確実に制御するため)。
 *
 * <p>手順(いずれかに失敗した時点で{@link InvalidAccessTokenException}): (1)JWSのコンパクト形式として解析できる。(2)ヘッダーの{@code
 * alg}が {@code HS256}である({@code
 * none}・HS384・HS512・RS256などは、署名の検証に進まず拒否)。(3)HMAC-SHA256の署名が、注入された鍵で検証できる。 (4){@code sub}・{@code
 * sid}・{@code iat}・{@code exp}が存在し、妥当である。(5){@code exp}が、注入された{@link Clock}の現在時刻より後である
 * (<b>時計のずれの許容(リーウェイ)は0</b>)。
 *
 * <p>例外には、原因の分類だけを持たせ、トークンの内容・解析の例外のメッセージ(値を含みうる)は持たせない(NFR2.7)。
 */
@Component
public class AccessTokenVerifier {

  private final JwtKeyProvider keyProvider;
  private final Clock clock;

  public AccessTokenVerifier(JwtKeyProvider keyProvider, Clock clock) {
    this.keyProvider = keyProvider;
    this.clock = clock;
  }

  /**
   * トークンを検証し、運ぶ値を返す。
   *
   * @throws InvalidAccessTokenException 検証に失敗した場合({@code INVALID}または{@code EXPIRED})
   */
  public AccessTokenClaims verify(String token) {
    SignedJWT jwt = parse(token);
    requireHs256(jwt);
    requireValidSignature(jwt);
    JWTClaimsSet claims = claimsOf(jwt);
    String sub = stringClaim(claims, "sub");
    String sid = stringClaim(claims, "sid");
    Date iat = claims.getIssueTime();
    Date exp = claims.getExpirationTime();
    if (sub == null
        || sub.isEmpty()
        || sid == null
        || sid.isEmpty()
        || iat == null
        || exp == null) {
      throw invalid();
    }
    Instant expiresAt = exp.toInstant();
    if (!expiresAt.isAfter(clock.instant())) {
      throw new InvalidAccessTokenException(UnauthorizedReason.EXPIRED);
    }
    return new AccessTokenClaims(sub, sid, iat.toInstant(), expiresAt);
  }

  private static SignedJWT parse(String token) {
    try {
      return SignedJWT.parse(token);
    } catch (ParseException | RuntimeException e) {
      throw invalid();
    }
  }

  private static void requireHs256(SignedJWT jwt) {
    if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
      throw invalid();
    }
  }

  private void requireValidSignature(SignedJWT jwt) {
    try {
      if (!jwt.verify(new MACVerifier(keyProvider.key().copyOfBytes()))) {
        throw invalid();
      }
    } catch (JOSEException e) {
      throw invalid();
    }
  }

  private static JWTClaimsSet claimsOf(SignedJWT jwt) {
    try {
      return jwt.getJWTClaimsSet();
    } catch (ParseException e) {
      throw invalid();
    }
  }

  private static String stringClaim(JWTClaimsSet claims, String name) {
    try {
      return claims.getStringClaim(name);
    } catch (ParseException e) {
      throw invalid();
    }
  }

  private static InvalidAccessTokenException invalid() {
    return new InvalidAccessTokenException(UnauthorizedReason.INVALID);
  }
}
