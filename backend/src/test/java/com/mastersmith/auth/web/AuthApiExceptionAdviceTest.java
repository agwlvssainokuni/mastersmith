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

package com.mastersmith.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.AuthenticationRequiredException;
import com.mastersmith.auth.exception.LoginFailedException;
import com.mastersmith.auth.exception.RefreshRejectedException;
import com.mastersmith.auth.exception.RequestBodyTooLargeException;
import com.mastersmith.auth.exception.RoleNotHeldException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.exception.SessionNotFoundException;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@link AuthApiExceptionAdvice}のテスト(security-design.md NFR2.8・NFR2.10、テーブル駆動): 例外と、RFC
 * 9457のProblemDetails(ステータス・{@code code}・{@code WWW-Authenticate})の対応、 {@code
 * instance}がルートのテンプレート(生のパスを含まない)であること、{@code
 * HttpMessageNotReadableException}の原因の連鎖のたどり(包まれる場合と直接伝わる場合の両方で413)。
 */
class AuthApiExceptionAdviceTest {

  private final AuthApiExceptionAdvice advice = new AuthApiExceptionAdvice();

  private static MockHttpServletRequest request(String route) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", route);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
    return request;
  }

  static Stream<Arguments> mappings() {
    return Stream.of(
        Arguments.of(new LoginFailedException(), 401, "auth.login.failed", false),
        Arguments.of(new RefreshRejectedException(), 401, "auth.refresh.rejected", false),
        Arguments.of(new RoleNotHeldException(), 403, "auth.role.not-held", false),
        Arguments.of(new AuthenticationRequiredException(), 401, "auth.token.invalid", true),
        Arguments.of(new SessionNotFoundException(), 401, "auth.token.invalid", true),
        Arguments.of(new SessionExpiredException(), 401, "auth.token.invalid", true),
        Arguments.of(
            new HashCapacityExceededException("capacity"), 503, "auth.service.unavailable", false),
        Arguments.of(
            new AuthStorageUnavailableException("db down"), 503, "auth.service.unavailable", false),
        Arguments.of(
            new IllegalStateException("SECRET detail"), 500, "auth.internal-error", false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mappings")
  void everyExceptionIsMappedToAProblemWithAStableCodeAndNeverExposesTheMessage(
      RuntimeException exception, int status, String code, boolean bearer) {
    MockHttpServletRequest request = request("/api/auth/login");

    ResponseEntity<ProblemDetail> response =
        switch (exception) {
          case LoginFailedException e -> advice.handleLoginFailed(request);
          case RefreshRejectedException e -> advice.handleRefreshRejected(request);
          case RoleNotHeldException e -> advice.handleRoleNotHeld(request);
          case AuthenticationRequiredException e -> advice.handleUnauthorized(request);
          case SessionNotFoundException e -> advice.handleUnauthorized(request);
          case SessionExpiredException e -> advice.handleUnauthorized(request);
          case HashCapacityExceededException e -> advice.handleServiceUnavailable(request);
          case AuthStorageUnavailableException e -> advice.handleServiceUnavailable(request);
          default -> advice.handleUnexpected(exception, request);
        };

    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(response.getBody().getProperties()).containsEntry("code", code);
    assertThat(response.getHeaders().getFirst("WWW-Authenticate"))
        .isEqualTo(bearer ? "Bearer" : null);
    assertThat(response.getHeaders().getContentType().toString())
        .startsWith("application/problem+json");
    assertThat(response.getBody().getDetail())
        .doesNotContain("SECRET")
        .doesNotContain("capacity")
        .doesNotContain("db down");
  }

  @Test
  void theInstanceIsTheRouteTemplateAndNeverTheRawPath() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/auth/anything/RAW-SECRET-SEGMENT");
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/auth/anything/{segment}");

    ProblemDetail problem = advice.handleLoginFailed(request).getBody();

    assertThat(problem.getInstance().toString())
        .isEqualTo("/api/auth/anything/%7Bsegment%7D")
        .doesNotContain("RAW-SECRET");
  }

  @Test
  void whenTheRouteIsNotKnownTheInstanceIsAboutBlankNotTheRawPath() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/auth/RAW-SECRET-SEGMENT");

    ProblemDetail problem = advice.handleLoginFailed(request).getBody();

    assertThat(problem.getInstance().toString()).isEqualTo("about:blank");
  }

  @Test
  void aTooLargeBodyIsConvertedTo413WhetherWrappedByTheJsonReaderOrPropagatedDirectly() {
    MockHttpServletRequest request = request("/api/auth/login");
    RequestBodyTooLargeException tooLarge = new RequestBodyTooLargeException(64 * 1024);

    ResponseEntity<ProblemDetail> direct = advice.handleTooLarge(request);
    ResponseEntity<ProblemDetail> wrapped =
        advice.handleNotReadable(
            new HttpMessageNotReadableException(
                "JSON parse error", tooLarge, new MockHttpInputMessage(new byte[0])),
            request);
    ResponseEntity<ProblemDetail> deeplyWrapped =
        advice.handleNotReadable(
            new HttpMessageNotReadableException(
                "JSON parse error",
                new RuntimeException("outer", new IllegalStateException("middle", tooLarge)),
                new MockHttpInputMessage(new byte[0])),
            request);

    for (ResponseEntity<ProblemDetail> response :
        java.util.List.of(direct, wrapped, deeplyWrapped)) {
      assertThat(response.getStatusCode().value()).isEqualTo(413);
      assertThat(response.getBody().getProperties())
          .containsEntry("code", "auth.request.too-large");
    }
  }

  @Test
  void anUnreadableBodyWithoutATooLargeCauseIsA400WithoutDetails() {
    MockHttpServletRequest request = request("/api/auth/login");

    ResponseEntity<ProblemDetail> response =
        advice.handleNotReadable(
            new HttpMessageNotReadableException(
                "Unexpected character SECRET", new MockHttpInputMessage(new byte[0])),
            request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().getProperties()).containsEntry("code", "auth.request.malformed");
    assertThat(response.getBody().getDetail()).doesNotContain("SECRET");
  }
}
