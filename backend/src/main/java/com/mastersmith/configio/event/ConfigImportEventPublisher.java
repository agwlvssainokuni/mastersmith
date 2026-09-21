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

package com.mastersmith.configio.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ConfigImportExecutedEvent}を、audit-loggingへ、<b>独立した新しいトランザクション({@code
 * REQUIRES_NEW})の中で、同期発行</b>する(config-import-export nfr-design/reliability-design.md
 * NFR4.5、レビュー指摘R-01)。audit-loggingのリスナーは、同期の{@code @EventListener}で、書き込みは発行元のトランザクションに参加するため、コミット後の
 * {@code
 * afterCommit}の中(元のトランザクションの資源が、まだ束縛されている)や、ロールバック後の失敗の記録では、独立したトランザクションでなければ、書き込みが確定しない(既存のuser-managementの
 * {@code UserChangedEventPublisher}と同じ対処)。
 *
 * <p>成功は{@code PostCommitCoordinator}から、失敗は{@code ConfigImportService}のトランザクションの外の{@code
 * catch}から呼ばれる。発行の例外は握りつぶし、ERRORのログに出す(取り込みの結果に、影響させない。
 * BR9.16)。再送・At-Least-Onceの保証は設けない(fire-and-forget)。
 */
@Component
public class ConfigImportEventPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigImportEventPublisher.class);

  private final ApplicationEventPublisher eventPublisher;
  private final TransactionOperations newTransaction;

  @Autowired
  public ConfigImportEventPublisher(
      ApplicationEventPublisher eventPublisher,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
    this(eventPublisher, requiresNew(transactionManager));
  }

  /** トランザクションを指定するコンストラクター(テスト用)。 */
  public ConfigImportEventPublisher(
      ApplicationEventPublisher eventPublisher, TransactionOperations newTransaction) {
    this.eventPublisher = eventPublisher;
    this.newTransaction = newTransaction;
  }

  private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  /** 独立したトランザクションの中でイベントを発行する。いかなる例外も、呼び出し元へ伝えない。 */
  public void publish(ConfigImportExecutedEvent event) {
    try {
      newTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    } catch (RuntimeException e) {
      // ファイルの内容・例外のメッセージは、出さない(NFR5.2)。
      LOG.error(
          "event=config.import.event-publish-failed outcome={} cause={}",
          event.outcome(),
          e.getClass().getName());
    }
  }
}
