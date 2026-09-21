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

import jakarta.servlet.http.HttpServletResponse;

/**
 * セキュリティヘッダーの値を、1か所に持つ(security-design.md NFR2.9)。{@code SecurityFilterChain}の設定({@link
 * AuthSecurityConfig})と、チェーンの手前で 応答を書く{@link
 * ProblemDetailsWriter}(サイズ制限の413など)が、同じ値を使う(チャンク転送の413と、値がそろう)。
 *
 * <p>{@code Strict-Transport-Security}は、Spring Securityの既定に従う(HTTPSのリクエストにのみ付く。TLSの終端は、環境の前提)。
 */
public final class SecurityHeaderValues {

  /** すべてのレスポンス(user-managementの要求、招待トークンのRefererによる漏えいの防止)。 */
  public static final String REFERRER_POLICY = "no-referrer";

  /** すべてのレスポンス。 */
  public static final String CONTENT_TYPE_OPTIONS = "nosniff";

  /** すべてのレスポンス。初期値であり、frontend-ui(U12)の実装で必要になれば緩める。 */
  public static final String CONTENT_SECURITY_POLICY =
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
          + " connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

  /** すべてのレスポンス。 */
  public static final String FRAME_OPTIONS = "DENY";

  /** {@code /api/**}のすべてのレスポンス(ログイン・リフレッシュの応答を含む)。静的ファイルには付けない。 */
  public static final String API_CACHE_CONTROL = "no-store";

  private SecurityHeaderValues() {}

  /**
   * チェーンの手前・チェーンの中で、自前で書く応答に、セキュリティヘッダーを付ける。{@code setHeader}のため、チェーンの{@code
   * HeaderWriterFilter}が同じ値を書いても、二重にならない。
   *
   * @param apiPath 対象が{@code /api/**}なら、{@code Cache-Control: no-store}も付ける
   */
  public static void apply(HttpServletResponse response, boolean apiPath) {
    response.setHeader("Referrer-Policy", REFERRER_POLICY);
    response.setHeader("X-Content-Type-Options", CONTENT_TYPE_OPTIONS);
    response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    response.setHeader("X-Frame-Options", FRAME_OPTIONS);
    if (apiPath) {
      response.setHeader("Cache-Control", API_CACHE_CONTROL);
    }
  }
}
