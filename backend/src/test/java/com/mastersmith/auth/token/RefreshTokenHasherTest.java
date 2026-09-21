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

import java.util.Base64;
import org.junit.jupiter.api.Test;

/** {@link RefreshTokenHasher}のテスト(NFR2.3): SHA-256のBase64URL(43文字)、決定性、平文がハッシュに含まれないこと。 */
class RefreshTokenHasherTest {

  private final RefreshTokenHasher hasher = new RefreshTokenHasher();

  @Test
  void theHashIsTheSha256OfTheTokenInBase64UrlWithoutPadding() {
    // SHA-256("abc")の既知の値
    byte[] expected =
        java.util.HexFormat.of()
            .parseHex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    String hash = hasher.hash("abc");

    assertThat(hash).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
    assertThat(Base64.getUrlDecoder().decode(hash)).isEqualTo(expected);
  }

  @Test
  void theSameTokenAlwaysHasTheSameHashAndDifferentTokensDiffer() {
    String token = new RefreshTokenGenerator().generate();

    assertThat(hasher.hash(token)).isEqualTo(hasher.hash(token));
    assertThat(hasher.hash(token)).isNotEqualTo(hasher.hash(token + "x"));
  }

  @Test
  void thePlaintextIsNotContainedInTheHash() {
    String token = new RefreshTokenGenerator().generate();

    assertThat(hasher.hash(token)).doesNotContain(token).doesNotContain(token.substring(0, 10));
  }
}
