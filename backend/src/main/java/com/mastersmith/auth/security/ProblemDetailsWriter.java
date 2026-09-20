package com.mastersmith.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * フィルタ(コントローラの手前)での、RFC 9457のProblemDetails({@code application/problem+json})の応答の書き出し(security-design.md NFR2.8・NFR2.9)。
 * 認証フィルタは、コントローラの手前で応答を返すため、{@code @RestControllerAdvice}が使えない。
 *
 * <ul>
 *   <li>{@code type}は{@code about:blank}、{@code title}・{@code detail}は、{@link AuthProblem}の固定の文言(原因の詳細・入力値を含めない)、{@code code}は
 *       i18nキー(拡張メンバー)。
 *   <li>{@code instance}は、含めない(生のパスを入れない)。
 *   <li>セキュリティヘッダー({@link SecurityHeaderValues})を、自前で付ける(サイズ制限のフィルタは、Spring Securityのチェーンより前に置かれ、チェーンの
 *       {@code HeaderWriterFilter}が付けるヘッダーが付かないため)。{@code /api/**}には、{@code Cache-Control: no-store}も付ける。
 *   <li>401の{@code token invalid}には、{@code WWW-Authenticate: Bearer}だけを付ける({@code error}などの理由は付けない)。
 * </ul>
 *
 * <p>{@code sendError}は使わない(エラーのディスパッチを起こさず、この応答が、そのまま返る)。
 */
@Component
public class ProblemDetailsWriter {

  /** 応答を書く。 */
  public void write(HttpServletRequest request, HttpServletResponse response, AuthProblem problem)
      throws IOException {
    response.setStatus(problem.status().value());
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    SecurityHeaderValues.apply(response, isApiPath(request));
    if (problem.bearerChallenge()) {
      response.setHeader("WWW-Authenticate", "Bearer");
    }
    response.getWriter().write(body(problem));
    response.getWriter().flush();
  }

  static String body(AuthProblem problem) {
    return "{\"type\":\"about:blank\",\"title\":\""
        + escape(problem.title())
        + "\",\"status\":"
        + problem.status().value()
        + ",\"detail\":\""
        + escape(problem.detail())
        + "\",\"code\":\""
        + escape(problem.code())
        + "\"}";
  }

  /** {@code /api/**}へのリクエストか(サーブレットのコンテキストパスを除いた、パスで判定する)。 */
  static boolean isApiPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    return path.equals("/api") || path.startsWith("/api/");
  }

  private static String escape(String value) {
    StringBuilder builder = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (c < 0x20) {
            builder.append(String.format("\\u%04x", (int) c));
          } else {
            builder.append(c);
          }
        }
      }
    }
    return builder.toString();
  }
}
