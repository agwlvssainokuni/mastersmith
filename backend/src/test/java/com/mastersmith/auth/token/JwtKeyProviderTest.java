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

import com.mastersmith.MastersmithApplication;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link JwtKeyProvider}・{@link SecretKeyMaterial}のテスト(NFR2.2・NFR4.4、安全失敗のテスト。team.mdの必須テスト種別(a)):
 * 鍵の未設定・Base64として不正・ 32バイト未満で、起動が失敗すること、<b>起動失敗の出力全体に、鍵の値・その断片が現れないこと</b>、{@link
 * SecretKeyMaterial#toString()}が伏せ字であること。
 */
@ExtendWith(OutputCaptureExtension.class)
class JwtKeyProviderTest {

  private static String base64OfRandomBytes(int length) {
    byte[] bytes = new byte[length];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static JwtKeyProvider providerWith(String secret) {
    MockEnvironment environment = new MockEnvironment();
    if (secret != null) {
      environment.setProperty(JwtKeyProvider.KEY_PROPERTY, secret);
    }
    return new JwtKeyProvider(environment);
  }

  @Test
  void aValidKeyIsDecodedAndKept() {
    String secret = base64OfRandomBytes(32);

    JwtKeyProvider provider = providerWith(secret);

    assertThat(provider.key().length()).isEqualTo(32);
    assertThat(provider.key().copyOfBytes()).isEqualTo(Base64.getDecoder().decode(secret));
  }

  @Test
  void theKeyIsReadFromTheEnvironmentVariableMastersmithAuthJwtSecret() {
    // 環境変数MASTERSMITH_AUTH_JWT_SECRETが、キーmastersmith.auth.jwt.secretとして読める(Spring Bootの緩やかな対応)。
    String secret = base64OfRandomBytes(32);
    org.springframework.core.env.StandardEnvironment environment =
        new org.springframework.core.env.StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new org.springframework.core.env.SystemEnvironmentPropertySource(
                "testSystemEnvironment", java.util.Map.of("MASTERSMITH_AUTH_JWT_SECRET", secret)));

    JwtKeyProvider provider = new JwtKeyProvider(environment);

    assertThat(provider.key().copyOfBytes()).isEqualTo(Base64.getDecoder().decode(secret));
  }

  @Test
  void aLongerKeyIsAccepted() {
    assertThat(providerWith(base64OfRandomBytes(64)).key().length()).isEqualTo(64);
  }

  static Stream<Arguments> invalidKeys() {
    return Stream.of(
        Arguments.of("未設定", null, "missing"),
        Arguments.of("空", "", "missing"),
        Arguments.of("空白のみ", "   ", "missing"),
        Arguments.of("Base64として不正", "not-base64-!!!", "format"),
        Arguments.of("Base64URLの文字(標準のBase64ではない)", "abc_def-ghi_jkl-mno_pqr-stu_vwx-yz", "format"),
        Arguments.of("31バイト(32バイト未満)", base64OfRandomBytes(31), "too short"),
        Arguments.of("1バイト", base64OfRandomBytes(1), "too short"));
  }

  @ParameterizedTest(name = "{0} -> 起動失敗")
  @MethodSource("invalidKeys")
  void anInvalidKeyFailsWithTheSettingNameAndTheReasonOnly(
      String label, String secret, String reason) {
    assertThatThrownBy(() -> providerWith(secret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(JwtKeyProvider.KEY_PROPERTY)
        .hasMessageContaining(reason)
        .hasNoCause();
    if (secret != null && !secret.isBlank()) {
      assertThatThrownBy(() -> providerWith(secret))
          .satisfies(e -> assertThat(e.getMessage()).doesNotContain(secret));
    }
  }

  @Test
  void toStringIsAFixedMaskAndDoesNotUseTheValueForEqualityOrHash() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    SecretKeyMaterial first = new SecretKeyMaterial(bytes);
    SecretKeyMaterial second = new SecretKeyMaterial(bytes);

    assertThat(first.toString()).isEqualTo("SecretKeyMaterial[****]");
    assertThat(first.toString()).doesNotContain(Base64.getEncoder().encodeToString(bytes));
    assertThat(first).isNotEqualTo(second).isEqualTo(first);
    assertThat(first.hashCode()).isNotEqualTo(second.hashCode());
  }

  @Test
  void theKeyBytesAreCopiedSoTheCallerCannotChangeTheStoredKey() {
    byte[] bytes = new byte[32];
    SecretKeyMaterial material = new SecretKeyMaterial(bytes);

    bytes[0] = 1;
    material.copyOfBytes()[1] = 1;

    assertThat(material.copyOfBytes()).containsOnly(0);
  }

  static Stream<Arguments> keysThatMustNeverAppearInTheStartupOutput() {
    return Stream.of(
        Arguments.of("Base64として不正な値", "SECRET-KEY-NOT-BASE64-FRAGMENT-XYZZY!!"),
        Arguments.of("32バイト未満の値", base64OfRandomBytes(20)));
  }

  @ParameterizedTest(name = "{0}: 起動失敗の出力全体に鍵の値・断片が現れない")
  @MethodSource("keysThatMustNeverAppearInTheStartupOutput")
  void theStartupFailureOutputNeverContainsTheKeyOrAFragmentOfIt(
      String label, String secret, CapturedOutput output) {
    Throwable failure = null;
    try {
      new SpringApplicationBuilder(MastersmithApplication.class)
          .web(WebApplicationType.SERVLET)
          // コマンドライン引数は、application.ymlのダミーの鍵より優先する(propertiesは既定値で、最も弱い)。
          .run("--mastersmith.auth.jwt.secret=" + secret, "--server.port=0");
    } catch (Throwable e) {
      failure = e;
    }

    assertThat(failure).isNotNull();
    StringBuilder everything = new StringBuilder(output.getAll());
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      everything.append(cause).append('\n');
    }
    String text = everything.toString();
    assertThat(text).contains(JwtKeyProvider.KEY_PROPERTY);
    assertThat(text).doesNotContain(secret);
    // 断片(先頭・末尾・中央の一部)も、現れない。
    int length = secret.length();
    assertThat(text).doesNotContain(secret.substring(0, 12));
    assertThat(text).doesNotContain(secret.substring(length - 12));
    assertThat(text).doesNotContain(secret.substring(length / 2 - 6, length / 2 + 6));
  }
}
