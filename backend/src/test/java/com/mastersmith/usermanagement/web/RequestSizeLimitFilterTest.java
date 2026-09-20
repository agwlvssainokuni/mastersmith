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

package com.mastersmith.usermanagement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.exception.RequestBodyTooLargeException;
import com.mastersmith.usermanagement.testsupport.JsonBodies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link RequestSizeLimitFilter}のテスト(security-design.md NFR2.3): {@code
 * Content-Length}が64KiBを超える場合に、内容を読まずに413になること、チャンク転送・宣言と
 * 実際の大きさが違う場合に、読み込み量を数えるストリームが上限で例外を投げること、64KiBちょうどが通ること、U4のURLパターンに限ること、最高優先の順序。
 */
class RequestSizeLimitFilterTest {

  private static final int LIMIT = 64 * 1024;

  private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter();

  /** 入力ストリームが読まれたかを記録する。 */
  private static class TrackingRequest extends MockHttpServletRequest {
    final AtomicBoolean streamOpened = new AtomicBoolean();

    TrackingRequest(String uri, byte[] body) {
      super("POST", uri);
      setContent(body);
    }

    @Override
    public ServletInputStream getInputStream() {
      streamOpened.set(true);
      return super.getInputStream();
    }
  }

  /** {@code Content-Length}が宣言されない(チャンク転送の)リクエスト、または、指定の値を宣言するリクエスト。 */
  private static final class DeclaringRequest extends TrackingRequest {
    private final long declared;

    DeclaringRequest(String uri, byte[] body, long declared) {
      super(uri, body);
      this.declared = declared;
    }

    @Override
    public long getContentLengthLong() {
      return declared;
    }

    @Override
    public int getContentLength() {
      return (int) declared;
    }
  }

  private static final class Recording implements FilterChain {
    final AtomicInteger invocations = new AtomicInteger();
    Throwable failure;
    long bytesRead;
    boolean useReader;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
        throws IOException {
      invocations.incrementAndGet();
      try {
        if (useReader) {
          try (BufferedReader reader = request.getReader()) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
              bytesRead += read;
            }
          }
        } else {
          bytesRead = request.getInputStream().readAllBytes().length;
        }
      } catch (IOException e) {
        failure = e;
        throw e;
      }
    }
  }

  @Test
  void aDeclaredLengthOverTheLimitIsRejectedWith413WithoutReadingTheBody() throws Exception {
    TrackingRequest request =
        new TrackingRequest(
            "/api/users/invitations/SECRET-TOKEN/accept", JsonBodies.filler(LIMIT + 1));
    MockHttpServletResponse response = new MockHttpServletResponse();
    Recording chain = new Recording();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .contains("\"status\":413")
        .doesNotContain("SECRET-TOKEN")
        .doesNotContain("/api/");
    assertThat(chain.invocations.get()).isZero();
    assertThat(request.streamOpened.get()).isFalse();
  }

  @Test
  void aBodyOfExactlyTheLimitPasses() throws Exception {
    TrackingRequest request = new TrackingRequest("/api/users", JsonBodies.filler(LIMIT));
    Recording chain = new Recording();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.invocations.get()).isEqualTo(1);
    assertThat(chain.bytesRead).isEqualTo(LIMIT);
  }

  @Test
  void aChunkedBodyOverTheLimitFailsWhileReadingWithTheDedicatedException() throws Exception {
    DeclaringRequest request = new DeclaringRequest("/api/users", JsonBodies.filler(LIMIT + 1), -1);
    Recording chain = new Recording();

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
        .isInstanceOf(RequestBodyTooLargeException.class);
    assertThat(chain.invocations.get()).isEqualTo(1);
  }

  @Test
  void aChunkedBodyOfExactlyTheLimitPasses() throws Exception {
    DeclaringRequest request =
        new DeclaringRequest("/api/me/preferences", JsonBodies.filler(LIMIT), -1);
    Recording chain = new Recording();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.bytesRead).isEqualTo(LIMIT);
    assertThat(chain.failure).isNull();
  }

  @Test
  void aBodyLargerThanItsDeclaredLengthIsStillLimitedWhileReading() {
    DeclaringRequest request = new DeclaringRequest("/api/users", JsonBodies.filler(LIMIT * 2), 10);

    assertThatThrownBy(
            () -> filter.doFilter(request, new MockHttpServletResponse(), new Recording()))
        .isInstanceOf(RequestBodyTooLargeException.class);
  }

  @Test
  void theReaderIsLimitedToo() {
    DeclaringRequest request = new DeclaringRequest("/api/users", JsonBodies.filler(LIMIT + 1), -1);
    Recording chain = new Recording();
    chain.useReader = true;

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
        .isInstanceOf(RequestBodyTooLargeException.class);
  }

  @Test
  void readingByteByByteFailsAtTheFirstByteOverTheLimit() throws Exception {
    DeclaringRequest request = new DeclaringRequest("/api/users", JsonBodies.filler(LIMIT + 1), -1);
    AtomicInteger consumed = new AtomicInteger();
    FilterChain chain =
        (req, res) -> {
          ServletInputStream in = req.getInputStream();
          try {
            while (in.read() >= 0) {
              consumed.incrementAndGet();
            }
          } catch (RequestBodyTooLargeException e) {
            consumed.set(-consumed.get());
            throw e;
          }
        };

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
        .isInstanceOf(RequestBodyTooLargeException.class);
    // 上限ちょうど(65536バイト)まで読めて、次の1バイトで失敗する。
    assertThat(consumed.get()).isEqualTo(-LIMIT);
  }

  @ParameterizedTest(name = "{0}は対象")
  @ValueSource(
      strings = {
        "/api/users",
        "/api/users/",
        "/api/users/u-1",
        "/api/users/invitations/some-token/accept",
        "/api/me/preferences",
        "/api/users;jsessionid=1"
      })
  void limitsTheUserManagementUrls(String uri) throws Exception {
    TrackingRequest request = new TrackingRequest(uri, JsonBodies.filler(LIMIT + 1));
    MockHttpServletResponse response = new MockHttpServletResponse();
    Recording chain = new Recording();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(chain.invocations.get()).isZero();
  }

  @ParameterizedTest(name = "{0}は対象外")
  @ValueSource(
      strings = {
        "/api/menu",
        "/api/menu-items",
        "/api/audit-log",
        "/api/config/import",
        "/api/usersx",
        "/api/me/other",
        "/api/me",
        "/other/api/users"
      })
  void doesNotLimitOtherUnitsUrls(String uri) throws Exception {
    TrackingRequest request = new TrackingRequest(uri, JsonBodies.filler(LIMIT * 4));
    MockHttpServletResponse response = new MockHttpServletResponse();
    Recording chain = new Recording();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.invocations.get()).isEqualTo(1);
    assertThat(chain.bytesRead).isEqualTo(LIMIT * 4L);
  }

  @Test
  void isRegisteredWithTheHighestPrecedenceSoItRunsBeforeAuthentication() {
    Order order = RequestSizeLimitFilter.class.getAnnotation(Order.class);

    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
