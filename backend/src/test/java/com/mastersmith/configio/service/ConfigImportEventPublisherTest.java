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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mastersmith.common.configio.ImportSections;
import com.mastersmith.common.configio.SectionCounts;
import com.mastersmith.configio.event.ConfigImportEventPublisher;
import com.mastersmith.configio.event.ConfigImportExecutedEvent;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import com.mastersmith.configio.testsupport.RecordingTransactionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ConfigImportEventPublisher}のテスト(NFR4.5): 独立した新しいトランザクション({@code
 * REQUIRES_NEW})の中で同期発行すること・発行の例外を握りつぶすこと。
 */
class ConfigImportEventPublisherTest {

  private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
  private final List<Object> published = new ArrayList<>();

  private ConfigImportEventPublisher publisher(
      org.springframework.context.ApplicationEventPublisher delegate) {
    TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return new ConfigImportEventPublisher(delegate, requiresNew);
  }

  @Test
  void publishesSynchronouslyInsideANewTransactionThatIsCommitted() {
    ConfigImportEventPublisher publisher =
        publisher(
            event -> {
              // 発行の時点で、新しいトランザクションが開始済み(確定は、発行の後)。
              published.add(transactionManager.log.size());
              published.add(event);
            });
    ConfigImportExecutedEvent event =
        ConfigImportExecutedEvent.success(
            "u", "r", Map.of(ImportSections.MENU, SectionCounts.ZERO));

    publisher.publish(event);

    assertThat(published.get(0)).isEqualTo(1);
    assertThat(published.get(1)).isSameAs(event);
    assertThat(transactionManager.log)
        .containsExactly(
            "begin isolation=-1 readOnly=false propagation=%d"
                .formatted(TransactionDefinition.PROPAGATION_REQUIRES_NEW),
            "commit");
  }

  @Test
  void isolatesItselfFromAnOuterTransactionByRequiringANewOne() {
    ConfigImportEventPublisher publisher = publisher(published::add);
    TransactionTemplate outer = new TransactionTemplate(transactionManager);

    outer.executeWithoutResult(
        status ->
            publisher.publish(
                ConfigImportExecutedEvent.failure("u", null, FailureCategory.UNEXPECTED, 0)));

    assertThat(transactionManager.log).contains("suspend", "resume");
    assertThat(published).hasSize(1);
  }

  @Test
  void neverPropagatesAListenerFailureOrACommitFailure() {
    ConfigImportEventPublisher failingListener =
        publisher(
            event -> {
              throw new IllegalStateException("listener down");
            });
    assertThatCode(
            () ->
                failingListener.publish(
                    ConfigImportExecutedEvent.failure("u", null, FailureCategory.MALFORMED, 1)))
        .doesNotThrowAnyException();
    assertThat(transactionManager.log).endsWith("rollback");

    transactionManager.failOnCommit = true;
    ConfigImportEventPublisher failingCommit = publisher(published::add);
    assertThatCode(
            () ->
                failingCommit.publish(
                    ConfigImportExecutedEvent.failure("u", null, FailureCategory.MALFORMED, 1)))
        .doesNotThrowAnyException();
  }
}
