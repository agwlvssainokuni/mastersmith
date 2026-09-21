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

import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.AuthenticationRequiredException;
import com.mastersmith.auth.exception.LoginFailedException;
import com.mastersmith.auth.exception.RefreshRejectedException;
import com.mastersmith.auth.exception.RequestBodyTooLargeException;
import com.mastersmith.auth.exception.RoleNotHeldException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.exception.SessionNotFoundException;
import com.mastersmith.auth.security.AuthProblem;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 認証のAPI({@link AuthController})の例外を、RFC 9457のProblemDetails({@code
 * application/problem+json})に変換する(security-design.md NFR2.8)。
 *
 * <ul>
 *   <li>401: ログイン失敗(全原因、{@code auth.login.failed})・リフレッシュの失敗(全原因、{@code
 *       auth.refresh.rejected})・認証済みの操作者を解決できない・ Sessionが有効でない({@code auth.token.invalid}、{@code
 *       WWW-Authenticate: Bearer})。
 *   <li>403: 保持していないロールの選択({@code auth.role.not-held})。
 *   <li>413: リクエストボディが上限を超えた({@code HttpMessageNotReadableException}の原因の連鎖をたどって判別する)。400:
 *       読み取れないJSON。
 *   <li>503: ハッシュ計算の待機超過・内部設定DBの障害(原因を区別しない、{@code auth.service.unavailable})。
 *   <li>500: 分類できない例外(詳細なし)。
 * </ul>
 *
 * <p>入力値(パスワード・トークン)・スタックトレース・内部の型名・例外のメッセージは、応答に含めない。{@code instance}は、マッチしたルートのテンプレートにする (Spring
 * MVCは、{@code instance}がnullだと、リクエストの生のパスを入れるため、必ず設定する)。
 *
 * <p>対象は、{@link AuthController}に限定する(共通基盤の汎用の例外ハンドラ・他ユニットの例外処理と衝突しない)。{@code @Order}は、最高優先を明記する。
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthApiExceptionAdvice {

  private static final Logger LOG = LoggerFactory.getLogger(AuthApiExceptionAdvice.class);

  @ExceptionHandler(LoginFailedException.class)
  public ResponseEntity<ProblemDetail> handleLoginFailed(HttpServletRequest request) {
    return problem(AuthProblem.LOGIN_FAILED, request);
  }

  @ExceptionHandler(RefreshRejectedException.class)
  public ResponseEntity<ProblemDetail> handleRefreshRejected(HttpServletRequest request) {
    return problem(AuthProblem.REFRESH_REJECTED, request);
  }

  @ExceptionHandler(RoleNotHeldException.class)
  public ResponseEntity<ProblemDetail> handleRoleNotHeld(HttpServletRequest request) {
    return problem(AuthProblem.ROLE_NOT_HELD, request);
  }

  @ExceptionHandler({
    AuthenticationRequiredException.class,
    SessionNotFoundException.class,
    SessionExpiredException.class
  })
  public ResponseEntity<ProblemDetail> handleUnauthorized(HttpServletRequest request) {
    return problem(AuthProblem.TOKEN_INVALID, request);
  }

  /** 503: ハッシュ計算の待機超過・内部設定DBの障害。原因を区別しない。 */
  @ExceptionHandler({HashCapacityExceededException.class, AuthStorageUnavailableException.class})
  public ResponseEntity<ProblemDetail> handleServiceUnavailable(HttpServletRequest request) {
    return problem(AuthProblem.SERVICE_UNAVAILABLE, request);
  }

  @ExceptionHandler(RequestBodyTooLargeException.class)
  public ResponseEntity<ProblemDetail> handleTooLarge(HttpServletRequest request) {
    return problem(AuthProblem.REQUEST_TOO_LARGE, request);
  }

  /** JSONの読み取りの失敗。原因の連鎖に{@link RequestBodyTooLargeException}があれば413、それ以外は400(詳細は含めない)。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    Throwable cause = e;
    for (int depth = 0; cause != null && depth < 10; depth++) {
      if (cause instanceof RequestBodyTooLargeException) {
        return problem(AuthProblem.REQUEST_TOO_LARGE, request);
      }
      cause = cause.getCause();
    }
    return problem(AuthProblem.REQUEST_MALFORMED, request);
  }

  /** 分類できない例外(バグ・制約違反など)。詳細を含めない500とし、ERRORログには、例外の型だけを記録する(メッセージ全文は出さない)。 */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(
      RuntimeException e, HttpServletRequest request) {
    LOG.error("Unexpected error in the authentication API: {}", e.getClass().getName());
    return problem(AuthProblem.INTERNAL_ERROR, request);
  }

  /** {@link AuthProblem}から、ProblemDetailsの応答を作る。 */
  static ResponseEntity<ProblemDetail> problem(AuthProblem type, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(type.status(), type.detail());
    problem.setTitle(type.title());
    problem.setProperty("code", type.code());
    problem.setInstance(instance(request));
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(type.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (type.bearerChallenge()) {
      builder.header("WWW-Authenticate", "Bearer");
    }
    return builder.body(problem);
  }

  /** {@code instance}: マッチしたルートのテンプレート。得られない場合は{@code about:blank}(生のパスを入れない)。 */
  private static URI instance(HttpServletRequest request) {
    String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (route == null) {
      return URI.create("about:blank");
    }
    try {
      return new URI(null, null, route, null);
    } catch (URISyntaxException e) {
      return URI.create("about:blank");
    }
  }
}
