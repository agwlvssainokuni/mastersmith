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

package com.mastersmith.configio.service;

import com.mastersmith.common.configio.ImportMessageKeys;
import com.mastersmith.common.security.Operator;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.event.ConfigImportEventPublisher;
import com.mastersmith.configio.event.ConfigImportExecutedEvent;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.configio.exception.ConfigImportExceptions;
import com.mastersmith.configio.exception.ConfigImportValidationException;
import com.mastersmith.configio.parser.ConfigDocumentParser;
import com.mastersmith.configio.parser.ImportErrorCollector;
import com.mastersmith.permission.PermissionEngineApi;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 設定一式の取り込み(FR11.1・FR1.3、BR9.4〜BR9.17)の調整役。{@code @Transactional}は付けず、プログラム的なトランザクション({@link
 * TransactionTemplate}。{@code REPEATABLE_READ}。既存のdata-import-exportの {@code
 * CsvImportService}と同じ流儀)で、検証・反映の部分だけを包む(nfr-design/reliability-design.md NFR4.1)。理由:
 * (a)失敗の監査イベントを、トランザクションの外で発行するため(全体が1つのトランザクションだと、
 * 発行した記録が、取り込みと一緒にロールバックされる)。(b)コミット時の例外(確定の失敗)を、{@code catch}で捕まえて分類するため。
 *
 * <pre>
 * importConfig(body, operator):                    // トランザクションなし
 *    try:
 *       parse(body)                                  // 構文・形式(その誤りだけ)・構造
 *       result = transactionTemplate.execute(tx -&gt; { // REPEATABLE_READ
 *            bootstrapAtStartを固定 → 参照整合・各ユニットの検証(全件を集める) → 誤りがあれば中止(何も反映しない)
 *            → schema→メニュー→RBACの反映 → PostCommitCoordinatorを、1つ、登録
 *       })
 *    catch 検証の誤り(422) / その他(UNEXPECTED):
 *       失敗の監査イベントを発行(REQUIRES_NEW。try-catchで包む)   // トランザクションは、すでに終了(ロールバック済み)
 *       例外を、呼び出し元へ
 * </pre>
 *
 * <p>失敗の分類は、権限昇格 → 主権限が0件 → それ以外の検証の誤りの優先順位で、最初に該当するものを1つ選ぶ(BR9.16。誤りの総数は{@code
 * errorCount})。ログには、ファイルの内容・ファイル名・検証の誤りの個々の 内容を出さない(NFR5.2)。
 */
@Service
public class ConfigImportService {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigImportService.class);

  private final ConfigDocumentParser parser;
  private final ImportOrchestrator orchestrator;
  private final PermissionEngineApi permissionEngineApi;
  private final ConfigImportEventPublisher eventPublisher;
  private final TransactionOperations importTransaction;
  private final Clock clock;

  @Autowired
  public ConfigImportService(
      ConfigDocumentParser parser,
      ImportOrchestrator orchestrator,
      PermissionEngineApi permissionEngineApi,
      ConfigImportEventPublisher eventPublisher,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      ObjectProvider<Clock> clock) {
    this(
        parser,
        orchestrator,
        permissionEngineApi,
        eventPublisher,
        repeatableRead(transactionManager),
        clock.getIfAvailable(Clock::systemUTC));
  }

  /** トランザクション・時計を指定するコンストラクター(テスト用)。 */
  public ConfigImportService(
      ConfigDocumentParser parser,
      ImportOrchestrator orchestrator,
      PermissionEngineApi permissionEngineApi,
      ConfigImportEventPublisher eventPublisher,
      TransactionOperations importTransaction,
      Clock clock) {
    this.parser = parser;
    this.orchestrator = orchestrator;
    this.permissionEngineApi = permissionEngineApi;
    this.eventPublisher = eventPublisher;
    this.importTransaction = importTransaction;
    this.clock = clock;
  }

  private static TransactionOperations repeatableRead(
      PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return template;
  }

  /**
   * 設定一式を取り込む。認可(401・403)は、呼び出し元が、事前に行う。
   *
   * @throws ConfigImportValidationException 入力の誤り(何も反映していない。失敗の監査イベントを、1件発行済み)
   * @throws RuntimeException 内部の障害(反映は、全体をロールバック済み。失敗の監査イベント(UNEXPECTED)を、1件発行済み)
   */
  public ImportResult importConfig(JsonNode body, Operator operator) {
    long started = System.nanoTime();
    LOG.info("event=config.import.start operatorUserId={}", operator.userId());
    ImportContext context =
        new ImportContext(operator.userId(), operator.activeRoleId(), clock.instant(), false);
    try {
      ImportErrorCollector errors = new ImportErrorCollector();
      ConfigDocument document = parser.parse(body, errors);
      ImportResult result =
          importTransaction.execute(status -> validateAndApply(document, context, errors));
      LOG.info(
          "event=config.import.end operatorUserId={} outcome=SUCCESS sections={} durationMs={}",
          operator.userId(),
          result.sections(),
          elapsedMillis(started));
      return result;
    } catch (ConfigImportValidationException e) {
      publishFailure(operator, e.category(), e.totalErrorCount());
      LOG.info(
          "event=config.import.end operatorUserId={} outcome=FAILURE failureCategory={} errorCount={} durationMs={}",
          operator.userId(),
          e.category(),
          e.totalErrorCount(),
          elapsedMillis(started));
      throw e;
    } catch (RuntimeException e) {
      // 素のJPAの例外(更新の競合・ロック待ちのタイムアウト)は、DataAccessExceptionに翻訳して、503に対応づけられるようにする(R-15)。
      RuntimeException translated = ConfigImportExceptions.translate(e);
      publishFailure(operator, FailureCategory.UNEXPECTED, 0);
      LOG.error(
          "event=config.import.error operatorUserId={} cause={} durationMs={}",
          operator.userId(),
          translated.getClass().getName(),
          elapsedMillis(started));
      throw translated;
    }
  }

  /**
   * リクエスト本体が読めなかった(構文の誤り・空の本体・Content-Typeの不一致。コントローラーに入る前の束縛の失敗)ことを、失敗の監査イベント(MALFORMED)として記録する。認可に成功した操作者に限り、
   * 呼ばれる(呼び出し元が、認可を先に行う。security-design.md「Q3=Bの帰結」)。
   */
  public void recordMalformed(Operator operator) {
    publishFailure(operator, FailureCategory.MALFORMED, 1);
    LOG.info(
        "event=config.import.end operatorUserId={} outcome=FAILURE failureCategory=MALFORMED errorCount=1",
        operator.userId());
  }

  /** トランザクションの中: bootstrapAtStartの固定 → 検証(全件を集める) → 反映 → 確定後の動作の登録。 */
  private ImportResult validateAndApply(
      ConfigDocument document, ImportContext base, ImportErrorCollector errors) {
    // 取り込み開始時点(このトランザクションのスナップショット)で、初期状態(主権限が0件)だったかを、最初に固定する
    // (権限昇格の判定の基準と、同じスナップショット。BR9.11)。
    ImportContext context = base.withBootstrapAtStart(permissionEngineApi.isBootstrapState());
    orchestrator.validate(document, context, errors);
    if (errors.hasErrors()) {
      throw new ConfigImportValidationException(
          classify(errors), errors.errors(), errors.total(), errors.truncated());
    }
    AppliedImport applied = orchestrator.apply(document, context);
    ImportResult result = new ImportResult(applied.sections());
    PostCommitCoordinator.register(
        applied.postCommits(),
        () ->
            eventPublisher.publish(
                ConfigImportExecutedEvent.success(
                    context.operatorUserId(), context.operatorActiveRoleId(), result.sections())));
    return result;
  }

  /** 失敗の分類: 権限昇格 → 主権限が0件 → それ以外の検証の誤り(BR9.16)。 */
  static FailureCategory classify(ImportErrorCollector errors) {
    if (errors.hasMessage(ImportMessageKeys.RBAC_ESCALATION)) {
      return FailureCategory.ESCALATION_DENIED;
    }
    if (errors.hasMessage(ImportMessageKeys.RBAC_EMPTY)) {
      return FailureCategory.RBAC_EMPTY;
    }
    return FailureCategory.VALIDATION_ERROR;
  }

  private void publishFailure(Operator operator, FailureCategory category, int errorCount) {
    eventPublisher.publish(
        ConfigImportExecutedEvent.failure(
            operator.userId(), operator.activeRoleId(), category, errorCount));
  }

  private static long elapsedMillis(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }
}
