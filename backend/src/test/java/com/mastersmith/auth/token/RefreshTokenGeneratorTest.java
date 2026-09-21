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

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link RefreshTokenGenerator}のテスト(NFR2.3): 256ビットの乱数のBase64URL(パディングなし、43文字)、文字種、一意性、乱数源の差し替え。
 */
class RefreshTokenGeneratorTest {

  private static final String BASE64URL = "^[A-Za-z0-9_-]{43}$";

  @Test
  void aTokenIs43Base64UrlCharactersOfA256BitRandomValue() {
    String token = new RefreshTokenGenerator().generate();

    assertThat(token).matches(BASE64URL);
    assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
  }

  @Test
  void manyTokensAreAllDifferent() {
    RefreshTokenGenerator generator = new RefreshTokenGenerator();
    Set<String> tokens = new HashSet<>();
    for (int i = 0; i < 2000; i++) {
      tokens.add(generator.generate());
    }

    assertThat(tokens).hasSize(2000);
  }

  @Test
  void theRandomSourceCanBeReplacedForTestsThatDependOnTheTokenValue() {
    SecureRandom fixed =
        new SecureRandom() {
          private static final long serialVersionUID = 1L;

          @Override
          public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, (byte) 7);
          }
        };

    String token = new RefreshTokenGenerator(fixed).generate();

    assertThat(Base64.getUrlDecoder().decode(token)).containsOnly(7).hasSize(32);
  }
}
