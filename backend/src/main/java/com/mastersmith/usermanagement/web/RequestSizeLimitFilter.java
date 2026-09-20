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

import com.mastersmith.usermanagement.exception.RequestBodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * リクエストボディの上限(64KiB)を強制するサーブレットフィルタ(security-design.md NFR2.3)。対象は、user-managementのURLパターン ({@code
 * /api/users}・{@code /api/users/**}・{@code /api/me/preferences})に限る(他ユニットのURLには影響しない)。
 *
 * <ul>
 *   <li>{@code Content-Length}が上限を超える場合: 内容を読まずに、フィルタの中で413を返す。
 *   <li>{@code Content-Length}がない(チャンク転送)場合、または宣言より多く送られた場合: 入力ストリームを、読み込み量を数えるストリームで包み、上限を超えた時点で
 *       {@link RequestBodyTooLargeException}を投げる。この例外は、JSONの読み取りの中で{@code
 *       HttpMessageNotReadableException}に包まれるため、 {@link UserApiExceptionAdvice}が原因を判別して、413に変換する。
 * </ul>
 *
 * <p><b>順序</b>: 認証前の巨大なボディを読み込ませないため、認証フィルタより前に置く(最高優先の順序で登録する)。フィルタチェーンの全体の順序は、共通基盤の設計と
 * 調整する(logical-components.mdの「共通基盤への要求」)。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  /** ボディの上限(64KiB)。ちょうど上限の大きさは、通す。 */
  public static final long MAX_BODY_BYTES = 64 * 1024;

  private static final String PROBLEM_JSON =
      "{\"type\":\"about:blank\",\"title\":\"Payload Too Large\",\"status\":413,"
          + "\"detail\":\"リクエストボディが大きすぎます(上限64KiB)。\"}";

  /** 対象のURLパターン。Spring MVCと同じパスの照合({@link PathPattern})を用いる(セミコロン・符号化された文字の扱いを揃える)。 */
  private static final List<PathPattern> TARGET_PATTERNS =
      Stream.of("/api/users", "/api/users/**", "/api/me/preferences")
          .map(PathPatternParser.defaultInstance::parse)
          .toList();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    PathContainer path = ServletRequestPathUtils.parse(request).pathWithinApplication();
    return TARGET_PATTERNS.stream().noneMatch(pattern -> pattern.matches(path));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      // 内容を読まずに拒否する。ProblemDetailsには、リクエストのパス(招待トークンを含みうる)を含めない。
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      response.setContentType("application/problem+json");
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write(PROBLEM_JSON);
      return;
    }
    chain.doFilter(new LimitedRequest(request), response);
  }

  /** 読み込み量を数える入力ストリームで、リクエストを包む。 */
  private static final class LimitedRequest extends HttpServletRequestWrapper {

    private CountingServletInputStream stream;

    LimitedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new CountingServletInputStream(super.getInputStream());
      }
      return stream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }

  private static final class CountingServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private long count;

    CountingServletInputStream(ServletInputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value >= 0) {
        add(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = delegate.read(buffer, offset, length);
      if (read > 0) {
        add(read);
      }
      return read;
    }

    private void add(long bytes) throws RequestBodyTooLargeException {
      count += bytes;
      if (count > MAX_BODY_BYTES) {
        throw new RequestBodyTooLargeException(MAX_BODY_BYTES);
      }
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }
  }
}
