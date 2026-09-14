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

package com.mastersmith.schema.web;

import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.schema.dto.SchemaIntrospectionRequest;
import com.mastersmith.schema.dto.SchemaIntrospectionResult;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import com.mastersmith.schema.exception.SchemaIntrospectionForbiddenException;
import com.mastersmith.schema.security.ActiveRoleResolver;
import com.mastersmith.schema.service.SchemaIntrospectionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * C8(schema-introspector REST API、FR1.4、Unit Generationレビュー指摘R-01)を実装する。
 *
 * <p>BR2.8: 呼び出しごとに{@link ActiveRoleResolver}でactiveRoleIdを解決し、{@link
 * PermissionEngineApi#canAccessScreen}でサーバー側の実効権限を再検証する(画面表示の出し分けだけに依存しない、project.md
 * Mandated)。screenKeyは設定管理画面がconfig-import-exportと共通の画面であるため、既存の予約screenKey({@value
 * #SCREEN_KEY})をそのまま用いる。拒否時は403(C8のForbiddenレスポンス)を返し、以降の処理(メタデータ読み取り・{@code
 * writeTableConfigDraft}呼び出し)は一切行わない。
 *
 * <p>{@link SchemaIntrospectionException}(BR2.9:
 * 接続失敗・メタデータ読み取り失敗・方言判定不可)は422(C8のValidationErrorレスポンス)へマッピングする。レスポンスボディには接続文字列・詳細な例外メッセージを含めない(security-design.md
 * NFR2.2)。
 *
 * <p>observability-design.md準拠の構造化ログ(実行開始/完了/失敗)と、403拒否分の{@code
 * schema_introspection_failures_total}メトリクスをここで記録する(422分・所要時間・テーブル数・生成件数は{@link
 * SchemaIntrospectionService}が記録する)。
 *
 * <p>{@link SchemaIntrospectionService}と同じ条件({@code
 * mastersmith.business-datasource.enabled=true})でのみBean化する。
 */
@RestController
@ConditionalOnProperty(
    prefix = "mastersmith.business-datasource",
    name = "enabled",
    havingValue = "true")
public class SchemaIntrospectionController {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaIntrospectionController.class);

  /** BR2.8: 設定管理画面(config-import-exportと共通)の予約screenKey。 */
  private static final String SCREEN_KEY = "config-import-export";

  private static final String GENERIC_VALIDATION_ERROR_DETAIL =
      "対象データベースへの接続、またはメタデータの読み取りに失敗しました。接続設定を確認のうえ再実行してください。";
  private static final String GENERIC_FORBIDDEN_DETAIL = "この操作を実行する権限がありません。";

  private final SchemaIntrospectionService service;
  private final PermissionEngineApi permissionEngineApi;
  private final ActiveRoleResolver activeRoleResolver;
  private final Counter forbiddenCounter;

  public SchemaIntrospectionController(
      SchemaIntrospectionService service,
      PermissionEngineApi permissionEngineApi,
      ActiveRoleResolver activeRoleResolver,
      MeterRegistry meterRegistry) {
    this.service = service;
    this.permissionEngineApi = permissionEngineApi;
    this.activeRoleResolver = activeRoleResolver;
    this.forbiddenCounter =
        Counter.builder("schema_introspection_failures_total")
            .description("Number of failed schema-introspection attempts (403/422, cumulative)")
            .register(meterRegistry);
  }

  @PostMapping("/api/config/schema-introspection")
  public ResponseEntity<SchemaIntrospectionResult> introspect(
      @Valid @RequestBody SchemaIntrospectionRequest request, HttpServletRequest httpRequest) {
    String activeRoleId = activeRoleResolver.resolveActiveRoleId(httpRequest);
    if (activeRoleId == null || !permissionEngineApi.canAccessScreen(activeRoleId, SCREEN_KEY)) {
      forbiddenCounter.increment();
      LOG.warn(
          "Schema introspection denied: schemaName={}, activeRoleId={}",
          request.schemaName(),
          activeRoleId);
      throw new SchemaIntrospectionForbiddenException(
          "Schema introspection denied for schemaName=" + request.schemaName());
    }
    LOG.info(
        "Schema introspection started: schemaName={}, tableNames={}",
        request.schemaName(),
        request.tableNames());
    SchemaIntrospectionResult result = service.introspect(request);
    LOG.info(
        "Schema introspection completed: schemaName={}, generatedCount={}",
        request.schemaName(),
        result.generatedTableConfigIds().size());
    return ResponseEntity.ok(result);
  }

  @ExceptionHandler(SchemaIntrospectionForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(SchemaIntrospectionForbiddenException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, GENERIC_FORBIDDEN_DETAIL);
    problem.setTitle("Schema introspection forbidden");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(SchemaIntrospectionException.class)
  public ResponseEntity<ProblemDetail> handleIntrospectionFailure(SchemaIntrospectionException e) {
    LOG.error("Schema introspection failed: {}", e.getMessage(), e);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT, GENERIC_VALIDATION_ERROR_DETAIL);
    problem.setTitle("Schema introspection failed");
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
  }
}
