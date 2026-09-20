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

import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.exception.EmailLockTimeoutException;
import com.mastersmith.usermanagement.exception.InvitationCapacityExceededException;
import com.mastersmith.usermanagement.exception.InvitationMailException;
import com.mastersmith.usermanagement.exception.InvitationTokenNotFoundException;
import com.mastersmith.usermanagement.exception.OperatorUnresolvedException;
import com.mastersmith.usermanagement.exception.RequestBodyTooLargeException;
import com.mastersmith.usermanagement.exception.UserAccessDeniedException;
import com.mastersmith.usermanagement.exception.UserFieldError;
import com.mastersmith.usermanagement.exception.UserNotFoundException;
import com.mastersmith.usermanagement.exception.UserValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * user-managementのAPI({@link UserController}・{@link InvitationAcceptController}・{@link
 * MePreferencesController})固有の例外を、RFC 9457のProblemDetails({@code
 * application/problem+json})に変換する(security-design.md NFR2.9、logical-components.md)。
 *
 * <ul>
 *   <li>401: 操作者を解決できない。403: 権限がない。404: 対象のUserが存在しない・招待トークンの不一致(未知・使用済み・取消済みを区別しない)。
 *   <li>413: リクエストボディが上限を超えた({@code HttpMessageNotReadableException}の原因を判別する)。
 *   <li>422: フィールド単位の検証エラー({@code errors[]}に{@code field}・{@code message}(i18nキー)・必要なら{@code
 *       params})。
 *   <li>503: ハッシュ計算の待機超過・招待の同時実行数の上限超過・同一emailの排他の待機超過・DBのロック待ちタイムアウト・招待メールの送信失敗(件名の拒否を含む)。
 *   <li>400: 読み取れないJSON(詳細は含めない)。
 * </ul>
 *
 * <p>入力値(パスワードなど)・スタックトレース・内部の型名・例外のメッセージは、応答に含めない。{@code instance}は、リクエストの生のパスではなく、ルートの テンプレート(例:
 * {@code /api/users/invitations/{token}/accept}。URIとして符号化される)にする(招待トークンの露出の抑止、NFR2.10)。
 *
 * <p>対象は、U4のコントローラに限定する(他ユニットの例外処理に影響しない)。{@code @Order}は、共通基盤の汎用ハンドラより優先するよう、最高優先を明記する。
 */
@RestControllerAdvice(
    assignableTypes = {
      UserController.class,
      InvitationAcceptController.class,
      MePreferencesController.class
    })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserApiExceptionAdvice {

  private static final Logger LOG = LoggerFactory.getLogger(UserApiExceptionAdvice.class);

  private static final Pattern ACCEPT_PATH = Pattern.compile("/api/users/invitations/[^/]+/accept");
  private static final String ACCEPT_ROUTE = "/api/users/invitations/{token}/accept";

  @ExceptionHandler(OperatorUnresolvedException.class)
  public ResponseEntity<ProblemDetail> handleUnauthorized(HttpServletRequest request) {
    return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "認証情報を確認できませんでした。", request);
  }

  @ExceptionHandler(UserAccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(HttpServletRequest request) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", "この操作を実行する権限がありません。", request);
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleUserNotFound(HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "User not found", "対象のユーザーが見つかりません。", request);
  }

  /** 未知・使用済み・取消済みのトークンを区別せず、同一の応答にする(NFR2.4)。 */
  @ExceptionHandler(InvitationTokenNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleInvitationNotFound(HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Invitation not found", "招待が見つかりません。", request);
  }

  @ExceptionHandler(UserValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      UserValidationException e, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed", "入力内容に誤りがあります。", request);
    List<Map<String, Object>> errors = new ArrayList<>();
    for (UserFieldError error : e.getErrors()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("field", error.field());
      entry.put("message", error.message());
      if (!error.params().isEmpty()) {
        entry.put("params", error.params());
      }
      errors.add(entry);
    }
    response.getBody().setProperty("errors", errors);
    return response;
  }

  /** 503: 混雑・排他の待機超過・DBのロック待ちタイムアウト・招待メールの送信失敗。再試行してもらう。 */
  @ExceptionHandler({
    HashCapacityExceededException.class,
    InvitationCapacityExceededException.class,
    EmailLockTimeoutException.class,
    InvitationMailException.class,
    PessimisticLockingFailureException.class,
    CannotCreateTransactionException.class
  })
  public ResponseEntity<ProblemDetail> handleServiceUnavailable(
      Exception e, HttpServletRequest request) {
    // 例外のメッセージ(宛先などを含みうる)は出さず、型名だけを記録する。
    LOG.warn("Service unavailable: {}", e.getClass().getSimpleName());
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Service Unavailable",
        "ただいま処理できません。しばらくしてから、もう一度お試しください。",
        request);
  }

  @ExceptionHandler(RequestBodyTooLargeException.class)
  public ResponseEntity<ProblemDetail> handleTooLarge(HttpServletRequest request) {
    return problem(
        HttpStatus.CONTENT_TOO_LARGE, "Payload Too Large", "リクエストボディが大きすぎます(上限64KiB)。", request);
  }

  /** JSONの読み取りの失敗。原因が{@link RequestBodyTooLargeException}(チャンク転送で上限を超えた)なら413、それ以外は400(詳細は含めない)。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    Throwable cause = e;
    for (int depth = 0; cause != null && depth < 10; depth++) {
      if (cause instanceof RequestBodyTooLargeException) {
        return handleTooLarge(request);
      }
      cause = cause.getCause();
    }
    return problem(HttpStatus.BAD_REQUEST, "Bad Request", "リクエストボディを読み取れません。", request);
  }

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setInstance(instance(request));
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * {@code instance}: マッチしたルートのテンプレートを用いる。ハンドラに到達する前の失敗などで得られない場合は、招待受諾のパスならテンプレート、それ以外はパスを用いる。
   * Spring MVCは、{@code instance}がnullだと、リクエストの生のパスを入れるため、必ず設定する。
   */
  private static URI instance(HttpServletRequest request) {
    String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (route == null) {
      String path = ServletRequestPathUtils.parse(request).pathWithinApplication().value();
      route = ACCEPT_PATH.matcher(path).matches() ? ACCEPT_ROUTE : path;
    }
    try {
      // {token}などの記号は、URIとして符号化される(%7Btoken%7D)。
      return new URI(null, null, route, null);
    } catch (URISyntaxException e) {
      return URI.create("about:blank");
    }
  }
}
