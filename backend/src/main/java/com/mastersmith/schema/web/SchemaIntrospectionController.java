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

import com.mastersmith.common.security.Operator;
import com.mastersmith.common.security.OperatorContext;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.schema.dto.SchemaIntrospectionRequest;
import com.mastersmith.schema.dto.SchemaIntrospectionResult;
import com.mastersmith.schema.exception.SchemaDraftValidationException;
import com.mastersmith.schema.exception.SchemaIntrospectionException;
import com.mastersmith.schema.exception.SchemaIntrospectionForbiddenException;
import com.mastersmith.schema.exception.SchemaIntrospectionUnauthorizedException;
import com.mastersmith.schema.service.SchemaIntrospectionService;
import com.mastersmith.schema.util.LogSanitizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>BR2.8: 呼び出しごとに{@link OperatorContext}(C15、認証フィルタが値を設定する)から操作者を読み、{@link
 * PermissionEngineApi#canAccessScreen}でサーバー側の実効権限を再検証する(画面表示の出し分けだけに依存しない、project.md
 * Mandated)。screenKeyは設定管理画面がconfig-import-exportと共通の画面であるため、既存の予約screenKey({@value
 * #SCREEN_KEY})をそのまま用いる。
 *
 * <ul>
 *   <li>操作者そのものを解決できない(未認証)場合は、権限判定を呼ばずに、自前で401を返す(認証フィルタが{@code
 *       /api/**}を認証必須にしていても、権限の再検証を、別ユニットのフィルタ設定に依存させないため。user-managementの{@code
 *       UserAuthorizer}と同じ流儀、authentication-serviceの機能設計 BR5.12)。
 *   <li>操作者は解決済みで、アクティブロールが未選択(null)の場合は、そのままC10へ渡す(C10は、fail
 *       closedで判定するため、権限なし(403)になる。RBAC設定が空の間のconfig-import-exportの例外は、C10が、activeRoleIdにかかわらず適用する)。
 *   <li>権限がない場合は403(C8のForbiddenレスポンス)を返す。
 * </ul>
 *
 * いずれの拒否でも、以降の処理(メタデータ読み取り・{@code writeTableConfigDraft}呼び出し)は一切行わない。
 *
 * <p>{@link SchemaIntrospectionException}(BR2.9:
 * 接続失敗・メタデータ読み取り失敗・方言判定不可)は422(C8のValidationErrorレスポンス)へマッピングする。config-engineが初期ドラフトを受け付けなかった場合({@link
 * SchemaDraftValidationException}。未対応の型など)も422で、フィールド単位のエラーを{@code errors}(RFC 9457の拡張メンバー、{@code
 * field}・{@code message})に載せる。レスポンスボディには接続文字列・詳細な例外メッセージを含めない(security-design.md NFR2.2)。
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
  private static final String GENERIC_UNAUTHORIZED_DETAIL = "認証情報を確認できませんでした。";
  private static final String DRAFT_VALIDATION_ERROR_DETAIL =
      "読み取ったスキーマの内容を、設定の初期ドラフトとして取り込めませんでした。未対応の型のカラムが含まれていないか確認してください。";

  private final SchemaIntrospectionService service;
  private final PermissionEngineApi permissionEngineApi;
  private final OperatorContext operatorContext;
  private final Counter forbiddenCounter;

  public SchemaIntrospectionController(
      SchemaIntrospectionService service,
      PermissionEngineApi permissionEngineApi,
      OperatorContext operatorContext,
      MeterRegistry meterRegistry) {
    this.service = service;
    this.permissionEngineApi = permissionEngineApi;
    this.operatorContext = operatorContext;
    this.forbiddenCounter =
        Counter.builder("schema_introspection_failures_total")
            .description("Number of failed schema-introspection attempts (403/422, cumulative)")
            .register(meterRegistry);
  }

  @PostMapping("/api/config/schema-introspection")
  public ResponseEntity<SchemaIntrospectionResult> introspect(
      @Valid @RequestBody SchemaIntrospectionRequest request) {
    // 操作者そのものを解決できない場合は、C10を呼ぶ前に、自前で拒否する(未認証。ブートストラップ状態では、C10は
    // config-import-exportに限り、activeRoleIdにかかわらずtrueを返すため、C10に任せると、操作者のいない要求が通ってしまう)。
    Operator operator =
        operatorContext.current().orElseThrow(SchemaIntrospectionUnauthorizedException::new);
    // activeRoleIdが未選択(null)でも、自前で拒否せず、そのままC10へ渡す(fail closedで権限なしと判定される。RBAC設定が空の間の
    // config-import-exportの例外は、activeRoleIdにかかわらず適用される。authentication-serviceの機能設計 BR5.12)。
    String activeRoleId = operator.activeRoleId();
    String schemaName = LogSanitizer.clean(request.schemaName());
    if (!permissionEngineApi.canAccessScreen(activeRoleId, SCREEN_KEY)) {
      forbiddenCounter.increment();
      LOG.warn(
          "Schema introspection denied: schemaName={}, activeRoleId={}", schemaName, activeRoleId);
      throw new SchemaIntrospectionForbiddenException(
          "Schema introspection denied for schemaName=" + schemaName);
    }
    // クライアントが指定した値は、制御文字を無害化してから、ログへ出す(ログインジェクション対策、R-07)。
    LOG.info(
        "Schema introspection started: schemaName={}, tableNames={}",
        schemaName,
        LogSanitizer.clean(request.tableNames()));
    SchemaIntrospectionResult result = service.introspect(request);
    LOG.info(
        "Schema introspection completed: schemaName={}, generatedCount={}",
        schemaName,
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

  @ExceptionHandler(SchemaIntrospectionUnauthorizedException.class)
  public ResponseEntity<ProblemDetail> handleUnauthorized(
      SchemaIntrospectionUnauthorizedException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, GENERIC_UNAUTHORIZED_DETAIL);
    problem.setTitle("Unauthorized");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }

  @ExceptionHandler(SchemaIntrospectionException.class)
  public ResponseEntity<ProblemDetail> handleIntrospectionFailure(SchemaIntrospectionException e) {
    LOG.error("Schema introspection failed: {}", LogSanitizer.clean(e.getMessage()), e);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT, GENERIC_VALIDATION_ERROR_DETAIL);
    problem.setTitle("Schema introspection failed");
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
  }

  /**
   * config-engineが初期ドラフトを受け付けなかった場合(BR2.9)。{@link
   * SchemaIntrospectionException}より具体的なため、こちらが選ばれる。フィールド単位のエラー({@code field}・{@code
   * message}(ルール種別))を、{@code errors}に載せる。
   */
  @ExceptionHandler(SchemaDraftValidationException.class)
  public ResponseEntity<ProblemDetail> handleDraftValidationFailure(
      SchemaDraftValidationException e) {
    LOG.warn("Schema introspection rejected by config-engine: {}", e.getMessage());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT, DRAFT_VALIDATION_ERROR_DETAIL);
    problem.setTitle("Schema introspection rejected");
    List<Map<String, Object>> errors = new ArrayList<>();
    e.getFieldErrors()
        .forEach(
            fieldError -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("field", fieldError.field());
              entry.put("message", fieldError.ruleType());
              errors.add(entry);
            });
    problem.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
  }
}
