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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link SessionIdGenerator}のテスト(NFR2.3): 128ビットの乱数のBase64URL(パディングなし、22文字)、文字種、一意性(推測不能性の根拠)。 */
class SessionIdGeneratorTest {

  @Test
  void aSessionIdIs22Base64UrlCharactersOfA128BitRandomValue() {
    String sessionId = new SessionIdGenerator().generate();

    assertThat(sessionId).hasSize(22).matches("^[A-Za-z0-9_-]{22}$");
    assertThat(Base64.getUrlDecoder().decode(sessionId)).hasSize(16);
  }

  @Test
  void manySessionIdsAreAllDifferent() {
    SessionIdGenerator generator = new SessionIdGenerator();
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 2000; i++) {
      ids.add(generator.generate());
    }

    assertThat(ids).hasSize(2000);
  }
}
