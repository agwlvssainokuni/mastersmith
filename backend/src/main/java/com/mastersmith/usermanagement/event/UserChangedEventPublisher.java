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

package com.mastersmith.usermanagement.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link UserChangedEvent}を、コミット後に発行する(reliability-design.md NFR4.3)。
 *
 * <p>呼び出し側は、業務のトランザクション({@code TransactionTemplate})のコミットが成功し、排他・許可を返した<b>後</b>に{@link
 * #publish(UserChangedEvent)}を呼ぶ。コミットに失敗した場合・ロールバックした場合・招待メールの送信に失敗して巻き戻した場合は、呼ばない。
 *
 * <p>受信側(audit-loggingの同期の{@code @EventListener})の書き込みを確定させるため、この発行を、<b>新しい独立したトランザクション ({@code
 * REQUIRES_NEW})の中で</b>同期の{@code publishEvent}として行う。受信側の書き込みは、この新しいトランザクションに参加し、発行側が抜けるときに
 * コミットされる。発行全体(新しいトランザクションの開始からコミットまで)を{@code try-catch}で囲み、例外が起きても、警告ログ(userIdのみ)と メトリクス({@code
 * user.event.publish.failed})に記録し、HTTPの結果には影響させない(コミット済みでメール送信済みの招待が500で返ることを避ける)。
 * 再送やAt-Least-Onceの保証は設けない(他ユニットと同じ方針)。
 */
@Component
public class UserChangedEventPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(UserChangedEventPublisher.class);

  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate newTransaction;
  private final Counter publishFailedCounter;

  public UserChangedEventPublisher(
      ApplicationEventPublisher eventPublisher,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      MeterRegistry meterRegistry) {
    this.eventPublisher = eventPublisher;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.publishFailedCounter =
        Counter.builder("user.event.publish.failed")
            .description("コミット後のUserChangedEventの発行に失敗した回数(NFR4.3)")
            .register(meterRegistry);
  }

  /** 独立したトランザクションの中でイベントを発行する。いかなる例外も、呼び出し元へ伝えない。 */
  public void publish(UserChangedEvent event) {
    try {
      newTransaction.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    } catch (Exception e) {
      publishFailedCounter.increment();
      // userIdのみを記録する(氏名・メールアドレスは含めない、NFR2.6)。
      LOG.warn(
          "Failed to publish UserChangedEvent: operation={}, userId={}, cause={}",
          event.operation(),
          event.targetId(),
          e.getClass().getName());
    }
  }
}
