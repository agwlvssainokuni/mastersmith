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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.auth.exception.InvalidAccessTokenException;
import com.mastersmith.auth.observation.UnauthorizedReason;
import com.mastersmith.auth.testsupport.AuthTestProperties;
import com.mastersmith.auth.testsupport.MutableClock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link AccessTokenIssuer}・{@link AccessTokenVerifier}のテスト(BR5.5・NFR2.2、テーブル駆動): 正常な発行と検証、{@code
 * alg: none}・HS256以外・署名の不正・必須の値の欠落・
 * 期限切れ(<b>時計のずれの許容0の境界</b>)が、いずれも検証の失敗になること、ロール・メールアドレス・氏名がクレームに含まれないこと。
 */
class AccessTokenTest {

  private static final String USER_ID = "user-123";
  private static final String SESSION_ID = "session-abc";

  private MutableClock clock;
  private byte[] keyBytes;
  private AccessTokenIssuer issuer;
  private AccessTokenVerifier verifier;

  @BeforeEach
  void setUp() {
    clock = new MutableClock();
    keyBytes = new byte[32];
    new SecureRandom().nextBytes(keyBytes);
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty(
        JwtKeyProvider.KEY_PROPERTY, Base64.getEncoder().encodeToString(keyBytes));
    JwtKeyProvider keyProvider = new JwtKeyProvider(environment);
    issuer = new AccessTokenIssuer(keyProvider, AuthTestProperties.defaults(), clock);
    verifier = new AccessTokenVerifier(keyProvider, clock);
  }

  // ---- 正常系 ----

  @Test
  void anIssuedTokenIsVerifiedAndCarriesOnlySubSidIatAndExp() throws Exception {
    String token = issuer.issue(USER_ID, SESSION_ID);

    AccessTokenClaims claims = verifier.verify(token);

    assertThat(claims.sub()).isEqualTo(USER_ID);
    assertThat(claims.sid()).isEqualTo(SESSION_ID);
    assertThat(claims.iat()).isEqualTo(clock.instant());
    assertThat(claims.exp()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
    // ロール・メールアドレス・氏名などの、その他の値は含めない(entities.md AccessTokenClaims)。
    Map<String, Object> all = SignedJWT.parse(token).getJWTClaimsSet().getClaims();
    assertThat(all.keySet()).containsExactlyInAnyOrder("sub", "sid", "iat", "exp");
    assertThat(SignedJWT.parse(token).getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
  }

  @Test
  void theTokenIsValidUntilTheExpiryAndNotAtTheExpiryInstantBecauseTheClockSkewToleranceIsZero() {
    String token = issuer.issue(USER_ID, SESSION_ID);

    clock.advance(Duration.ofMinutes(10).minusMillis(1));
    assertThat(verifier.verify(token).sub()).isEqualTo(USER_ID);

    // 有効期限ちょうどは、期限切れ(リーウェイ0)。Spring SecurityのJwtDecoderの既定の60秒の許容は、入らない。
    clock.advance(Duration.ofMillis(1));
    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOfSatisfying(
            InvalidAccessTokenException.class,
            e -> assertThat(e.reason()).isEqualTo(UnauthorizedReason.EXPIRED));
  }

  @Test
  void aTokenExpiredByOneSecondIsRejectedEvenThoughACommonLibraryWouldAllowTheDefaultSkew() {
    String token = issuer.issue(USER_ID, SESSION_ID);
    clock.advance(Duration.ofMinutes(10).plusSeconds(1));

    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOf(InvalidAccessTokenException.class);
  }

  // ---- 検証の失敗(テーブル駆動) ----

  private String sign(JWSAlgorithm algorithm, byte[] key, JWTClaimsSet claims) throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
    // HS384・HS512は、鍵の長さ(48・64バイト以上)を要するため、同じ鍵の繰り返しで、長さを揃える(署名の検証の前に、algで拒否されることの確認)。
    byte[] signingKey = key.length >= 64 ? key : java.util.Arrays.copyOf(repeat(key, 64), 64);
    jwt.sign(new MACSigner(algorithm.equals(JWSAlgorithm.HS256) ? key : signingKey));
    return jwt.serialize();
  }

  private JWTClaimsSet.Builder validClaims() {
    Date now = Date.from(clock.instant());
    return new JWTClaimsSet.Builder()
        .subject(USER_ID)
        .claim("sid", SESSION_ID)
        .issueTime(now)
        .expirationTime(Date.from(clock.instant().plus(Duration.ofMinutes(10))));
  }

  private static byte[] repeat(byte[] key, int length) {
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = key[i % key.length];
    }
    return result;
  }

  private static byte[] otherKey(int length) {
    byte[] bytes = new byte[length];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }

  record Case(String label, Function<AccessTokenTest, String> token, UnauthorizedReason reason) {
    @Override
    public String toString() {
      return label;
    }
  }

  static Stream<Case> failingTokens() {
    return Stream.of(
        new Case("空の文字列", t -> "", UnauthorizedReason.INVALID),
        new Case("JWTではない文字列", t -> "not-a-jwt", UnauthorizedReason.INVALID),
        new Case("ドットだけ", t -> "..", UnauthorizedReason.INVALID),
        new Case(
            "alg: none(署名なしのJWT)",
            t -> new PlainJWT(new PlainHeader(), t.validClaimsUnchecked()).serialize(),
            UnauthorizedReason.INVALID),
        new Case(
            "alg: none(署名部が空のコンパクト形式)",
            t -> {
              String header = b64("{\"alg\":\"none\"}");
              String payload =
                  b64(
                      "{\"sub\":\"user-123\",\"sid\":\"session-abc\",\"exp\":9999999999,\"iat\":1}");
              return header + "." + payload + ".";
            },
            UnauthorizedReason.INVALID),
        new Case(
            "alg: HS384(同じ鍵で署名)",
            t -> t.signUnchecked(JWSAlgorithm.HS384, t.keyBytes, t.validClaimsUnchecked()),
            UnauthorizedReason.INVALID),
        new Case(
            "alg: HS512(同じ鍵で署名)",
            t -> t.signUnchecked(JWSAlgorithm.HS512, t.keyBytes, t.validClaimsUnchecked()),
            UnauthorizedReason.INVALID),
        new Case(
            "alg: RS256(RSA鍵で署名)",
            t -> {
              try {
                RSAKey rsa = new RSAKeyGenerator(2048).generate();
                SignedJWT jwt =
                    new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), t.validClaimsUnchecked());
                jwt.sign(new RSASSASigner(rsa));
                return jwt.serialize();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            },
            UnauthorizedReason.INVALID),
        new Case(
            "署名の不正(別の鍵で署名)",
            t -> t.signUnchecked(JWSAlgorithm.HS256, otherKey(32), t.validClaimsUnchecked()),
            UnauthorizedReason.INVALID),
        new Case(
            "署名の不正(署名部の改ざん)",
            t -> {
              String token = t.issuer.issue(USER_ID, SESSION_ID);
              return token.substring(0, token.length() - 4) + "AAAA";
            },
            UnauthorizedReason.INVALID),
        new Case(
            "署名の不正(ペイロードの書き換え: 別のユーザー)",
            t -> {
              String[] parts = t.issuer.issue(USER_ID, SESSION_ID).split("\\.");
              String forged =
                  b64(
                      "{\"sub\":\"admin\",\"sid\":\"session-abc\",\"iat\":1,\"exp\":"
                          + (t.clock.instant().getEpochSecond() + 600)
                          + "}");
              return parts[0] + "." + forged + "." + parts[2];
            },
            UnauthorizedReason.INVALID),
        new Case(
            "必須の値の欠落: sub",
            t -> t.signUnchecked(JWSAlgorithm.HS256, t.keyBytes, t.claimsWithout("sub")),
            UnauthorizedReason.INVALID),
        new Case(
            "必須の値の欠落: sid",
            t -> t.signUnchecked(JWSAlgorithm.HS256, t.keyBytes, t.claimsWithout("sid")),
            UnauthorizedReason.INVALID),
        new Case(
            "必須の値の欠落: exp",
            t -> t.signUnchecked(JWSAlgorithm.HS256, t.keyBytes, t.claimsWithout("exp")),
            UnauthorizedReason.INVALID),
        new Case(
            "必須の値の欠落: iat",
            t -> t.signUnchecked(JWSAlgorithm.HS256, t.keyBytes, t.claimsWithout("iat")),
            UnauthorizedReason.INVALID),
        new Case(
            "subが空",
            t ->
                t.signUnchecked(
                    JWSAlgorithm.HS256,
                    t.keyBytes,
                    new JWTClaimsSet.Builder(t.validClaimsUnchecked()).subject("").build()),
            UnauthorizedReason.INVALID),
        new Case(
            "sidが文字列ではない(数値)",
            t ->
                t.signUnchecked(
                    JWSAlgorithm.HS256,
                    t.keyBytes,
                    new JWTClaimsSet.Builder(t.validClaimsUnchecked()).claim("sid", 12345).build()),
            UnauthorizedReason.INVALID),
        new Case(
            "期限切れ(1秒前)",
            t ->
                t.signUnchecked(
                    JWSAlgorithm.HS256,
                    t.keyBytes,
                    new JWTClaimsSet.Builder(t.validClaimsUnchecked())
                        .expirationTime(Date.from(t.clock.instant().minusSeconds(1)))
                        .build()),
            UnauthorizedReason.EXPIRED),
        new Case(
            "期限切れ(現在時刻ちょうど)",
            t ->
                t.signUnchecked(
                    JWSAlgorithm.HS256,
                    t.keyBytes,
                    new JWTClaimsSet.Builder(t.validClaimsUnchecked())
                        .expirationTime(Date.from(t.clock.instant()))
                        .build()),
            UnauthorizedReason.EXPIRED));
  }

  private static String b64(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private JWTClaimsSet validClaimsUnchecked() {
    return validClaims().build();
  }

  private String signUnchecked(JWSAlgorithm algorithm, byte[] key, JWTClaimsSet claims) {
    try {
      return sign(algorithm, key, claims);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JWTClaimsSet claimsWithout(String name) {
    Map<String, Object> all = new java.util.LinkedHashMap<>(validClaims().build().getClaims());
    all.remove(name);
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
    all.forEach(builder::claim);
    return builder.build();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("failingTokens")
  void everyFailureIsAVerificationFailureWithAClassificationAndNoTokenContent(Case testCase) {
    String token = testCase.token().apply(this);

    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOfSatisfying(
            InvalidAccessTokenException.class,
            e -> {
              assertThat(e.reason()).isEqualTo(testCase.reason());
              // 例外には、トークンの内容・解析の例外のメッセージを持たせない(NFR2.7)。
              assertThat(e.getMessage()).doesNotContain(token.isEmpty() ? "\u0000" : token);
              assertThat(e.getCause()).isNull();
            });
  }

  @Test
  void aTokenIssuedAtAnotherTimeIsVerifiedAgainstTheInjectedClockNotTheSystemClock() {
    clock.set(Instant.parse("2020-05-05T05:05:05Z"));
    String token = issuer.issue(USER_ID, SESSION_ID);

    assertThat(verifier.verify(token).iat()).isEqualTo(Instant.parse("2020-05-05T05:05:05Z"));
  }

  @Test
  void twoTokensForDifferentSessionsCarryTheirOwnSid() {
    List<String> sids =
        Stream.of("s1", "s2")
            .map(sid -> verifier.verify(issuer.issue(USER_ID, sid)).sid())
            .toList();

    assertThat(sids).containsExactly("s1", "s2");
  }
}
