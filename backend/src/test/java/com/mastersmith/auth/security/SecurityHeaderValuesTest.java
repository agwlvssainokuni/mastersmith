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

package com.mastersmith.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@link SecurityHeaderValues}のテスト(NFR2.9): 値(設計で確定した初期値)と、{@code apply}が付けるヘッダー。 */
class SecurityHeaderValuesTest {

  @Test
  void theValuesAreTheOnesFixedByTheDesign() {
    assertThat(SecurityHeaderValues.REFERRER_POLICY).isEqualTo("no-referrer");
    assertThat(SecurityHeaderValues.CONTENT_TYPE_OPTIONS).isEqualTo("nosniff");
    assertThat(SecurityHeaderValues.FRAME_OPTIONS).isEqualTo("DENY");
    assertThat(SecurityHeaderValues.API_CACHE_CONTROL).isEqualTo("no-store");
    assertThat(SecurityHeaderValues.CONTENT_SECURITY_POLICY)
        .isEqualTo(
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
                + " connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
  }

  @Test
  void applyIsIdempotentBecauseTheHeadersAreSetNotAdded() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    SecurityHeaderValues.apply(response, true);
    SecurityHeaderValues.apply(response, true);

    assertThat(response.getHeaders("Referrer-Policy")).containsExactly("no-referrer");
    assertThat(response.getHeaders("Cache-Control")).containsExactly("no-store");
  }
}
