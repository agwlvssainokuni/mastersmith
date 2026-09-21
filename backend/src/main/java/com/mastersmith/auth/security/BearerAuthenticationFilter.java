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

import com.mastersmith.auth.cache.SessionCache;
import com.mastersmith.auth.cache.SessionCache.Lookup;
import com.mastersmith.auth.cache.SessionState;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.InvalidAccessTokenException;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.observation.AuthObservations;
import com.mastersmith.auth.observation.UnauthorizedReason;
import com.mastersmith.auth.token.AccessTokenClaims;
import com.mastersmith.auth.token.AccessTokenVerifier;
import com.mastersmith.common.security.Operator;
import io.micrometer.observation.Observation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 認証フィルタ(BR5.11、security-design.md NFR2.1・NFR2.2、performance-design.md NFR1.2)。認証を要するパス({@link
 * AuthRequestRules#REQUIRES_BEARER}: {@code
 * /api/**}から、認証を要しない3つを除いたもの)に<b>だけ</b>適用し、認証不要のパスでは実行しない({@code shouldNotFilter})。認証不要のパスでは、{@code
 * Authorization}ヘッダーの有無・内容(期限切れ・不正なトークンを含む)にかかわらず、何も検証せず、素通しにする。これにより、
 * 期限切れのアクセストークンを付けたまま、リフレッシュ・ログイン・招待受諾を呼んでも、フィルタでは401にならない。
 *
 * <p>検証の手順(いずれかに失敗した時点で、原因を区別しない同一の401): (1){@code Authorization:
 * Bearer}を取り出す(スキーム名は、大文字小文字を区別しない。ヘッダーが複数 ある場合も、なし扱い)。(2)アクセストークンを検証する({@link
 * AccessTokenVerifier}: HS256・署名・必須の値・{@code exp}、時計のずれ0)。(3){@code sid}のSessionを、 {@link
 * SessionCache}から引き、存在し、有効(BR5.11の定義)であることを確認する。(4)トークンの{@code sub}が、Sessionの{@code userId}と一致することを
 * 確認する。成功したら、{@link
 * Operator}(userId=sub・sessionId=sid・アクティブロール=Sessionの値(nullを含む))を、セキュリティコンテキストに設定する。
 * 内部設定DBの障害(キャッシュミスの読み込みの失敗)は、401ではなく503にする(fail closed。Sessionを確認できないまま、通さない)。
 *
 * <p>原因({@link UnauthorizedReason})は、メトリクスのタグとDEBUGログにだけ用い、応答には出さない。トークンの内容・解析の例外のメッセージは、ログに出さない
 * (NFR2.7)。リクエストヘッダー({@code X-User-Id}・{@code X-Active-Role-Id})は、読まない。認証に成功したリクエストの間、MDCに{@code
 * userId}・ {@code sessionId}を追加し、終了時に必ず取り除く。
 *
 * <p>このクラスは、Springのコンポーネントにしない(サーブレットフィルタとして、二重に登録されないため)。{@link AuthSecurityConfig}が、{@code
 * SecurityFilterChain}に組み込む。
 */
public class BearerAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AccessTokenVerifier verifier;
  private final SessionCache cache;
  private final Clock clock;
  private final AuthMetrics metrics;
  private final AuthEventLogger logger;
  private final AuthObservations observations;
  private final ProblemDetailsWriter problemWriter;

  public BearerAuthenticationFilter(
      AccessTokenVerifier verifier,
      SessionCache cache,
      Clock clock,
      AuthMetrics metrics,
      AuthEventLogger logger,
      AuthObservations observations,
      ProblemDetailsWriter problemWriter) {
    this.verifier = verifier;
    this.cache = cache;
    this.clock = clock;
    this.metrics = metrics;
    this.logger = logger;
    this.observations = observations;
    this.problemWriter = problemWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !AuthRequestRules.REQUIRES_BEARER.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Operator operator = authenticate(request, response);
    if (operator == null) {
      return; // 401・503の応答は、書き込み済み
    }
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new OperatorAuthentication(operator));
    SecurityContextHolder.setContext(context);
    MDC.put("userId", operator.userId());
    MDC.put("sessionId", operator.sessionId());
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("userId");
      MDC.remove("sessionId");
      SecurityContextHolder.clearContext();
    }
  }

  /** 認証する。失敗した場合は、応答(401・503)を書いて、nullを返す。 */
  private Operator authenticate(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    long startedAt = System.nanoTime();
    Observation observation = observations.start("auth.filter");
    try {
      String token = bearerToken(request);
      if (token == null) {
        return reject(request, response, observation, UnauthorizedReason.MISSING);
      }
      AccessTokenClaims claims;
      try {
        claims = verifier.verify(token);
      } catch (InvalidAccessTokenException e) {
        return reject(request, response, observation, e.reason());
      }
      Lookup lookup;
      try {
        lookup = cache.lookup(claims.sid());
      } catch (AuthStorageUnavailableException e) {
        // Sessionを確認できないまま、通さない(fail closed)。401にしない。
        problemWriter.write(request, response, AuthProblem.SERVICE_UNAVAILABLE);
        return null;
      }
      metrics
          .filterDuration(lookup.hit())
          .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
      observation.lowCardinalityKeyValue("cache", lookup.hit() ? "hit" : "miss");
      SessionState state = lookup.state();
      if (state == null || !state.isValidAt(clock.instant())) {
        return reject(request, response, observation, UnauthorizedReason.SESSION_INACTIVE);
      }
      if (!state.userId().equals(claims.sub())) {
        // 鍵が漏えいしても、有効なsidを知らない攻撃者は、他のユーザーになりすませない。
        return reject(request, response, observation, UnauthorizedReason.INVALID);
      }
      return new Operator(claims.sub(), claims.sid(), state.activeRoleId());
    } catch (RuntimeException e) {
      observation.error(e);
      throw e;
    } finally {
      observation.stop();
    }
  }

  private Operator reject(
      HttpServletRequest request,
      HttpServletResponse response,
      Observation observation,
      UnauthorizedReason reason)
      throws IOException {
    metrics.filterUnauthorized(reason);
    logger.unauthorized(reason);
    observation.lowCardinalityKeyValue("reason", reason.tag());
    problemWriter.write(request, response, AuthProblem.TOKEN_INVALID);
    return null;
  }

  /** {@code Authorization: Bearer <token>}のトークン。ヘッダーがない・複数ある・Bearerでない・トークンが空の場合は、null。 */
  private static String bearerToken(HttpServletRequest request) {
    var headers = Collections.list(request.getHeaders("Authorization"));
    if (headers.size() != 1) {
      return null;
    }
    String header = headers.get(0);
    if (header == null
        || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    String token = header.substring(BEARER_PREFIX.length()).strip();
    return token.isEmpty() ? null : token;
  }
}
