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

package com.mastersmith.audit.web;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.audit.exception.AuditLogForbiddenException;
import com.mastersmith.audit.exception.AuditLogQueryValidationException;
import com.mastersmith.audit.repository.AuditLogEntryRepository;
import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import com.mastersmith.permission.PermissionEngineApi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C6(audit-logging REST API、FR8.4)の閲覧エンドポイントを実装する。
 *
 * <p>本コントローラは自前の権限判定ロジックを持たず、呼び出しごとに{@link
 * OperatorContext}(C15、認証フィルタが値を設定する)からactiveRoleIdを読み、{@link
 * PermissionEngineApi#canAccessScreen}(screenKey={@value #SCREEN_KEY})へ認可判定を委譲する(BR7.10、project.md
 * Mandated「画面表示の出し分けだけに依存せず、必ずサーバー側で実効権限を再検証する」)。予約screenKey
 * "audit-log"はpermission-engineがBR3.10・BR3.15で既に予約・実装済みのものをそのまま利用する。
 *
 * <p>クエリパラメータ(page/pageSize/targetType)は範囲外・不正な場合400(security-design.md「クエリパラメータの入力検証」、Q4確定、
 * サイレントクランプは行わない)、内部設定DBが利用不可の場合は503(reliability-design.md「内部設定DB接続断時の閲覧APIの挙動」、NFR4.4、 C6契約追補)を返す。
 */
@RestController
public class AuditLogController {

  private static final Logger LOG = LoggerFactory.getLogger(AuditLogController.class);

  /** BR7.10: permission-engineが既に予約・実装済みの監査ログ閲覧画面用screenKey。本ユニット側で新規定義は行わない。 */
  private static final String SCREEN_KEY = "audit-log";

  private static final int MIN_PAGE_SIZE = 1;
  private static final int MAX_PAGE_SIZE = 100;

  /**
   * entities.md targetType.allowed_values(BR7.11: 共通エンジン層としてハードコードを避けたいが、契約上の既知値集合として明示する必要がある)。
   */
  private static final Set<String> ALLOWED_TARGET_TYPES =
      Set.of("ConfigEngine", "PermissionEngine", "DataImportExport");

  private static final String GENERIC_FORBIDDEN_DETAIL = "この操作を実行する権限がありません。";
  private static final String GENERIC_SERVICE_UNAVAILABLE_DETAIL =
      "監査ログの読み取りに一時的に失敗しました。しばらく待ってから再実行してください。";

  private final AuditLogEntryRepository repository;
  private final PermissionEngineApi permissionEngineApi;
  private final OperatorContext operatorContext;
  private final Timer requestDurationTimer;
  private final Counter errorCounter;

  public AuditLogController(
      AuditLogEntryRepository repository,
      PermissionEngineApi permissionEngineApi,
      OperatorContext operatorContext,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.permissionEngineApi = permissionEngineApi;
    this.operatorContext = operatorContext;
    this.requestDurationTimer =
        Timer.builder("audit_logging.get_audit_log.duration")
            .description("GET /api/audit-log request latency")
            .register(meterRegistry);
    this.errorCounter =
        Counter.builder("audit_logging.get_audit_log.error_count")
            .description("Number of failed GET /api/audit-log requests (400/403/503, cumulative)")
            .register(meterRegistry);
  }

  @GetMapping("/api/audit-log")
  public ResponseEntity<AuditLogPageResponse> getAuditLog(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String targetType) {
    Timer.Sample sample = Timer.start();
    try {
      validateQueryParameters(page, pageSize, targetType);

      // activeRoleIdが未選択(null)でも、自前で拒否せず、そのままC10へ渡す(fail closedで権限なしと判定される。
      // authentication-serviceの機能設計 BR5.12)。
      String activeRoleId = operatorContext.current().map(Operator::activeRoleId).orElse(null);
      if (!permissionEngineApi.canAccessScreen(activeRoleId, SCREEN_KEY)) {
        LOG.warn("Audit log access denied: activeRoleId={}", activeRoleId);
        throw new AuditLogForbiddenException(
            "Audit log access denied for activeRoleId=" + activeRoleId);
      }

      // BR7.9: occurredAt降順が既定の並び順(C6契約にソートパラメータは追加しない)。
      Pageable pageable =
          PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
      Page<AuditLogEntry> result =
          targetType == null
              ? repository.findAll(pageable)
              : repository.findByTargetType(targetType, pageable);

      return ResponseEntity.ok(AuditLogPageResponse.from(result));
    } catch (RuntimeException e) {
      errorCounter.increment();
      throw e;
    } finally {
      sample.stop(requestDurationTimer);
    }
  }

  private void validateQueryParameters(int page, int pageSize, String targetType) {
    if (page < 1) {
      throw new AuditLogQueryValidationException("page must be 1 or greater: " + page);
    }
    if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
      throw new AuditLogQueryValidationException(
          "pageSize must be between %d and %d: %d"
              .formatted(MIN_PAGE_SIZE, MAX_PAGE_SIZE, pageSize));
    }
    if (targetType != null && !ALLOWED_TARGET_TYPES.contains(targetType)) {
      throw new AuditLogQueryValidationException("Unknown targetType: " + targetType);
    }
  }

  @ExceptionHandler(AuditLogQueryValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidationFailure(AuditLogQueryValidationException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setTitle("Invalid audit log query parameters");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(AuditLogForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(AuditLogForbiddenException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, GENERIC_FORBIDDEN_DETAIL);
    problem.setTitle("Audit log access forbidden");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ProblemDetail> handleDataAccessFailure(DataAccessException e) {
    // NFR4.4: 内部設定DBが利用不可の場合の一般的な障害処理(専用フォールバックなし)。
    LOG.error("Audit log query failed: internal config datastore unavailable", e);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, GENERIC_SERVICE_UNAVAILABLE_DETAIL);
    problem.setTitle("Audit log temporarily unavailable");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
  }
}
