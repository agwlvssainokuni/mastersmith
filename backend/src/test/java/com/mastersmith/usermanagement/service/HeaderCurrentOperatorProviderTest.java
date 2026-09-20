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

package com.mastersmith.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.usermanagement.security.HeaderCurrentOperatorProvider;
import com.mastersmith.usermanagement.security.Operator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** {@link HeaderCurrentOperatorProvider}(暫定実装): リクエストヘッダーから操作者を読む。ヘッダーがなければ未解決(401の元)。 */
class HeaderCurrentOperatorProviderTest {

  private final HeaderCurrentOperatorProvider provider = new HeaderCurrentOperatorProvider();

  @Test
  void readsTheUserIdAndTheActiveRoleIdFromTheHeaders() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", " user-1 ");
    request.addHeader("X-Active-Role-Id", "role-1");

    assertThat(provider.resolve(request)).isEqualTo(new Operator("user-1", "role-1"));
  }

  @Test
  void missingOrBlankHeadersAreUnresolved() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "   ");

    assertThat(provider.resolve(request)).isEqualTo(Operator.unresolved());
  }

  @Test
  void aUserIdWithoutAnActiveRoleIsResolvedPartially() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "user-1");

    assertThat(provider.resolve(request)).isEqualTo(new Operator("user-1", null));
  }
}
