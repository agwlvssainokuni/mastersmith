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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link ProblemDetailsWriter}のテスト(NFR2.8・NFR2.9): フィルタでの応答の形式(RFC 9457・{@code code}・固定の文言・{@code
 * instance}を含まないこと)、 {@code WWW-Authenticate: Bearer}(トークンの失敗だけ)、セキュリティヘッダー({@code
 * /api/**}にだけ{@code Cache-Control: no-store})、{@code Set-Cookie}がないこと。
 */
class ProblemDetailsWriterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ProblemDetailsWriter writer = new ProblemDetailsWriter();

  private MockHttpServletResponse write(String path, AuthProblem problem) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    writer.write(request, response, problem);
    return response;
  }

  static Stream<Arguments> problems() {
    return Stream.of(
        Arguments.of(AuthProblem.TOKEN_INVALID, 401, "auth.token.invalid"),
        Arguments.of(AuthProblem.SERVICE_UNAVAILABLE, 503, "auth.service.unavailable"),
        Arguments.of(AuthProblem.REQUEST_TOO_LARGE, 413, "auth.request.too-large"),
        Arguments.of(AuthProblem.FORBIDDEN, 403, "auth.forbidden"),
        Arguments.of(AuthProblem.LOGIN_FAILED, 401, "auth.login.failed"),
        Arguments.of(AuthProblem.REFRESH_REJECTED, 401, "auth.refresh.rejected"),
        Arguments.of(AuthProblem.ROLE_NOT_HELD, 403, "auth.role.not-held"),
        Arguments.of(AuthProblem.REQUEST_MALFORMED, 400, "auth.request.malformed"),
        Arguments.of(AuthProblem.INTERNAL_ERROR, 500, "auth.internal-error"));
  }

  @ParameterizedTest(name = "{0} -> {1} {2}")
  @MethodSource("problems")
  void theBodyIsAnRfc9457ProblemWithAStableI18nKeyAndNoInstance(
      AuthProblem problem, int status, String code) throws Exception {
    MockHttpServletResponse response = write("/api/users", problem);

    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    JsonNode body =
        MAPPER.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(body.get("type").asText()).isEqualTo("about:blank");
    assertThat(body.get("status").asInt()).isEqualTo(status);
    assertThat(body.get("code").asText()).isEqualTo(code);
    assertThat(body.get("title").asText()).isNotBlank();
    assertThat(body.get("detail").asText()).isNotBlank();
    // 生のパスを入れない。
    assertThat(body.has("instance")).isFalse();
    assertThat(response.getContentAsString()).doesNotContain("/api/users");
    assertThat(response.getHeader("Set-Cookie")).isNull();
  }

  @Test
  void onlyATokenFailureCarriesTheBearerChallengeWithoutAReason() throws Exception {
    MockHttpServletResponse unauthorized = write("/api/users", AuthProblem.TOKEN_INVALID);
    MockHttpServletResponse unavailable = write("/api/users", AuthProblem.SERVICE_UNAVAILABLE);
    MockHttpServletResponse tooLarge = write("/api/auth/login", AuthProblem.REQUEST_TOO_LARGE);

    // error・error_descriptionなどの理由は付けない(原因を区別しない)。
    assertThat(unauthorized.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    assertThat(unavailable.getHeader("WWW-Authenticate")).isNull();
    assertThat(tooLarge.getHeader("WWW-Authenticate")).isNull();
  }

  @Test
  void theSecurityHeadersAreWrittenAndNoStoreOnlyForApiPaths() throws Exception {
    MockHttpServletResponse api = write("/api/auth/login", AuthProblem.REQUEST_TOO_LARGE);
    MockHttpServletResponse notApi = write("/index.html", AuthProblem.FORBIDDEN);

    for (MockHttpServletResponse response : new MockHttpServletResponse[] {api, notApi}) {
      assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
      assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
      assertThat(response.getHeader("Content-Security-Policy"))
          .isEqualTo(SecurityHeaderValues.CONTENT_SECURITY_POLICY);
      assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }
    assertThat(api.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(notApi.getHeader("Cache-Control")).isNull();
  }

  @Test
  void theContextPathIsIgnoredWhenDecidingWhetherThePathIsAnApiPath() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api/users");
    request.setContextPath("/app");
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(request, response, AuthProblem.TOKEN_INVALID);

    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void theTextsAreFixedAndNeverContainTheCauseOfTheFailure() throws Exception {
    // 認証フィルタの失敗の全原因が、同一の応答になる(本文・ヘッダーとも)。
    MockHttpServletResponse first = write("/api/users", AuthProblem.TOKEN_INVALID);
    MockHttpServletResponse second = write("/api/menu", AuthProblem.TOKEN_INVALID);

    assertThat(first.getContentAsString()).isEqualTo(second.getContentAsString());
    assertThat(first.getHeaderNames()).containsExactlyInAnyOrderElementsOf(second.getHeaderNames());
  }
}
