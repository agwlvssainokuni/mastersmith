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

package com.mastersmith.configio.web;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.common.security.Operator;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.configio.exception.ConfigImportExceptions;
import com.mastersmith.configio.exception.ConfigImportForbiddenException;
import com.mastersmith.configio.exception.ConfigImportUnauthorizedException;
import com.mastersmith.configio.exception.ConfigImportValidationException;
import com.mastersmith.configio.service.ConfigImportService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * {@link ConfigImportExportController}専用の例外ハンドラー(C7、BR9.7・BR9.17・BR9.21、security-design.md
 * NFR2.6)。例外を、RFC 9457のProblemDetails({@code application/problem+json})へ 変換する。{@code
 * code}は、フロントエンド(U12)が、表示する文言を選ぶための、安定したi18nキーである。
 *
 * <ul>
 *   <li>401(操作者を解決できない)・403(権限不足)。
 *   <li>422(入力の誤り): {@code errors[]}(位置{@code field}・i18nキー{@code message}・必要な場合だけ{@code
 *       params})を、最大100件と、総数{@code errorCount}・打ち切りの有無{@code truncated}で返す。
 *       入力値そのもの・スタックトレース・内部の型名は含めない。
 *   <li>束縛の例外(構文の誤り・空の本体・Content-Typeの不一致。コントローラーに入る前に起きる):
 *       <b>認可を先に</b>行い(権限のない利用者に、構文の誤りの詳細を返さず、監査ログに行を追加させない)、
 *       成功した場合だけ、422(MALFORMED)と、失敗の監査イベントを返す。
 *   <li>503(内部設定DBの障害・接続の取得の失敗・更新の競合・ロック待ちのタイムアウト・キャッシュの再読み込みの失敗)と、500(それ以外)。汎用のメッセージで、スタックトレース・SQL・内部設定DBの接続情報を含めない
 *       (詳細は、ERRORのログにだけ出す)。
 * </ul>
 */
@RestControllerAdvice(assignableTypes = ConfigImportExportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfigImportExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigImportExceptionHandler.class);

  static final String CODE_UNAUTHORIZED = "auth.token.invalid";
  static final String CODE_FORBIDDEN = "config.import.forbidden";
  static final String CODE_VALIDATION_FAILED = "config.import.validation.failed";
  static final String CODE_UNAVAILABLE = "config.import.unavailable";
  static final String CODE_INTERNAL_ERROR = "config.import.internal-error";

  private final ObjectProvider<ConfigImportAuthorizer> authorizer;
  private final ObjectProvider<ConfigImportService> importService;

  /**
   * 依存は、使う時点で解決する({@code
   * ObjectProvider})。{@code @ControllerAdvice}は、{@code @WebMvcTest}のスライス(このユニットの部品を持たない、他のユニットのコントローラーのテスト)にも
   * 読み込まれるため、生成の時点で、認可・取り込みのサービスを要求しない。
   */
  public ConfigImportExceptionHandler(
      ObjectProvider<ConfigImportAuthorizer> authorizer,
      ObjectProvider<ConfigImportService> importService) {
    this.authorizer = authorizer;
    this.importService = importService;
  }

  @ExceptionHandler(ConfigImportUnauthorizedException.class)
  public ResponseEntity<ProblemDetail> handleUnauthorized(HttpServletRequest request) {
    return unauthorized(request);
  }

  @ExceptionHandler(ConfigImportForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(HttpServletRequest request) {
    return forbidden(request);
  }

  @ExceptionHandler(ConfigImportValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      ConfigImportValidationException e, HttpServletRequest request) {
    return validationProblem(
        codeFor(e.category()), e.errors(), e.totalErrorCount(), e.truncated(), request);
  }

  /** リクエスト本体の束縛の失敗(読めないJSON・空の本体・Content-Typeの不一致)。コントローラーの認可より前に起きるため、ここで、同じ認可を先に行う。 */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    HttpMediaTypeNotSupportedException.class
  })
  public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpServletRequest request) {
    Operator operator;
    try {
      operator = authorizer.getObject().authorize();
    } catch (ConfigImportUnauthorizedException e) {
      return unauthorized(request);
    } catch (ConfigImportForbiddenException e) {
      return forbidden(request);
    } catch (RuntimeException e) {
      return ConfigImportExceptions.isServiceUnavailable(e)
          ? serviceUnavailable(e, request)
          : internalError(e, request);
    }
    importService.getObject().recordMalformed(operator);
    return validationProblem(
        ImportMessageKeys.JSON_MALFORMED,
        List.of(ImportValidationError.of("", ImportMessageKeys.JSON_MALFORMED)),
        1,
        false,
        request);
  }

  /**
   * 503: 内部設定DBの障害・接続の取得の失敗・更新の競合・ロック待ちのタイムアウト(並行制御・資源・一時的な障害の例外。{@link
   * ConfigImportExceptions#isServiceUnavailable})。それ以外の想定外の例外は、500。
   */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(
      RuntimeException e, HttpServletRequest request) {
    return ConfigImportExceptions.isServiceUnavailable(e)
        ? serviceUnavailable(e, request)
        : internalError(e, request);
  }

  // ---- 応答の組み立て ----

  private static ResponseEntity<ProblemDetail> unauthorized(HttpServletRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.UNAUTHORIZED, "認証情報を確認できませんでした。", CODE_UNAUTHORIZED, request);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static ResponseEntity<ProblemDetail> forbidden(HttpServletRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.FORBIDDEN, "この操作を実行する権限がありません。", CODE_FORBIDDEN, request);
    return respond(HttpStatus.FORBIDDEN, problem);
  }

  private static ResponseEntity<ProblemDetail> validationProblem(
      String code,
      List<ImportValidationError> errors,
      int totalErrorCount,
      boolean truncated,
      HttpServletRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "設定ファイルの内容に誤りがあります。", code, request);
    List<Map<String, Object>> list = new ArrayList<>();
    for (ImportValidationError error : errors) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("field", error.field());
      item.put("message", error.message());
      if (!error.params().isEmpty()) {
        item.put("params", error.params());
      }
      list.add(item);
    }
    problem.setProperty("errors", list);
    problem.setProperty("errorCount", totalErrorCount);
    problem.setProperty("truncated", truncated);
    return respond(HttpStatus.UNPROCESSABLE_CONTENT, problem);
  }

  private static ResponseEntity<ProblemDetail> serviceUnavailable(
      RuntimeException e, HttpServletRequest request) {
    LOG.error("event=config.import.error status=503 cause={}", e.getClass().getName());
    ProblemDetail problem =
        problem(
            HttpStatus.SERVICE_UNAVAILABLE,
            "ただいま処理できません。しばらくしてから、もう一度お試しください。",
            CODE_UNAVAILABLE,
            request);
    return respond(HttpStatus.SERVICE_UNAVAILABLE, problem);
  }

  private static ResponseEntity<ProblemDetail> internalError(
      RuntimeException e, HttpServletRequest request) {
    LOG.error("event=config.import.error status=500 cause={}", e.getClass().getName());
    ProblemDetail problem =
        problem(HttpStatus.INTERNAL_SERVER_ERROR, "処理中にエラーが発生しました。", CODE_INTERNAL_ERROR, request);
    return respond(HttpStatus.INTERNAL_SERVER_ERROR, problem);
  }

  private static ProblemDetail problem(
      HttpStatus status, String detail, String code, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    return problem;
  }

  private static ResponseEntity<ProblemDetail> respond(HttpStatus status, ProblemDetail problem) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /** 失敗の分類に対応する、応答全体の{@code code}(分類が、フロントエンドで判別できるように)。 */
  private static String codeFor(FailureCategory category) {
    return switch (category) {
      case MALFORMED -> ImportMessageKeys.JSON_MALFORMED;
      case UNSUPPORTED_FORMAT -> ImportMessageKeys.FORMAT_UNSUPPORTED;
      case ESCALATION_DENIED -> ImportMessageKeys.RBAC_ESCALATION;
      case RBAC_EMPTY -> ImportMessageKeys.RBAC_EMPTY;
      default -> CODE_VALIDATION_FAILED;
    };
  }
}
