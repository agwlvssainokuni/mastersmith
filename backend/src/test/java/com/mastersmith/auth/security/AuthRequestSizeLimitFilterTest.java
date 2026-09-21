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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.auth.exception.RequestBodyTooLargeException;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link AuthRequestSizeLimitFilter}のテスト(NFR2.10): {@code /api/auth/**}のボディの上限(64KiB)。{@code
 * Content-Length}が上限を超える場合は、内容を読まずに413(セキュリティヘッダー付き、 {@code
 * instance}なし)、チャンク転送(長さの宣言なし)では、上限を超えた時点で{@link
 * RequestBodyTooLargeException}、ちょうど上限は通す、対象外のパスには影響しない。
 */
class AuthRequestSizeLimitFilterTest {

  private final AuthRequestSizeLimitFilter filter =
      new AuthRequestSizeLimitFilter(new ProblemDetailsWriter());

  private MockHttpServletRequest request(
      String method, String path, byte[] body, boolean declareLength) {
    MockHttpServletRequest request =
        declareLength
            ? new MockHttpServletRequest(method, path)
            : new MockHttpServletRequest(method, path) {
              @Override
              public long getContentLengthLong() {
                return -1;
              }

              @Override
              public int getContentLength() {
                return -1;
              }
            };
    request.setContent(body);
    request.setContentType("application/json");
    return request;
  }

  @Test
  void aBodyWhoseDeclaredLengthExceedsTheLimitIsRejectedWithoutReadingItWith413() throws Exception {
    MockHttpServletRequest request =
        request("POST", "/api/auth/login", new byte[64 * 1024 + 1], true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(chain.getRequest()).isNull();
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString()).contains("\"code\":\"auth.request.too-large\"");
    // 生のパスを入れない(instanceなし)。セキュリティヘッダーを、自前で付ける。
    assertThat(response.getContentAsString())
        .doesNotContain("instance")
        .doesNotContain("/api/auth/login");
    assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("Content-Security-Policy"))
        .isEqualTo(SecurityHeaderValues.CONTENT_SECURITY_POLICY);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void aBodyOfExactlyTheLimitIsAccepted() throws Exception {
    MockHttpServletRequest request = request("POST", "/api/auth/login", new byte[64 * 1024], true);
    AtomicReference<byte[]> read = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
              throws IOException, jakarta.servlet.ServletException {
            read.set(req.getInputStream().readAllBytes());
            super.doFilter(req, res);
          }
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(read.get()).hasSize(64 * 1024);
  }

  @Test
  void aChunkedBodyIsRejectedByAnExceptionAtTheMomentTheLimitIsExceeded() throws Exception {
    MockHttpServletRequest request =
        request("POST", "/api/auth/refresh", new byte[64 * 1024 + 1], false);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
              throws IOException, jakarta.servlet.ServletException {
            try (ServletInputStream in = req.getInputStream()) {
              in.readAllBytes();
            } catch (RequestBodyTooLargeException e) {
              failure.set(e);
            }
          }
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(failure.get()).isInstanceOf(RequestBodyTooLargeException.class);
  }

  @Test
  void aChunkedBodyOfExactlyTheLimitIsReadCompletely() throws Exception {
    MockHttpServletRequest request =
        request("POST", "/api/auth/refresh", new byte[64 * 1024], false);
    AtomicReference<Integer> length = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
              throws IOException, jakarta.servlet.ServletException {
            length.set(req.getInputStream().readAllBytes().length);
          }
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(length.get()).isEqualTo(64 * 1024);
  }

  @Test
  void theReaderIsAlsoCountedSoTheLimitCannotBeBypassedThroughIt() throws Exception {
    MockHttpServletRequest request =
        request("POST", "/api/auth/login", new byte[64 * 1024 + 1], false);
    request.setCharacterEncoding("UTF-8");
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
              throws IOException, jakarta.servlet.ServletException {
            assertThatThrownBy(() -> req.getReader().transferTo(java.io.Writer.nullWriter()))
                .isInstanceOf(RequestBodyTooLargeException.class);
          }
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);
  }

  static Stream<Arguments> targetedAndUntargetedPaths() {
    return Stream.of(
        Arguments.of("/api/auth/login", true),
        Arguments.of("/api/auth/refresh", true),
        Arguments.of("/api/auth/logout", true),
        Arguments.of("/api/auth/active-role", true),
        // 他のユニットのURLの上限は、各ユニットが所有する(このフィルタは、広げない)。
        Arguments.of("/api/users", false),
        Arguments.of("/api/menu", false),
        Arguments.of("/api/audit-log", false),
        Arguments.of("/index.html", false));
  }

  @ParameterizedTest(name = "{0} は対象: {1}")
  @MethodSource("targetedAndUntargetedPaths")
  void onlyTheAuthenticationApiIsSubjectToTheLimit(String path, boolean targeted) throws Exception {
    MockHttpServletRequest request = request("POST", path, new byte[64 * 1024 + 1], true);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    if (targeted) {
      assertThat(response.getStatus()).isEqualTo(413);
      assertThat(chain.getRequest()).isNull();
    } else {
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(chain.getRequest()).isNotNull();
    }
  }
}
