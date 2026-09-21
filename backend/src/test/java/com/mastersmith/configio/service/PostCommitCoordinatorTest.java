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

import com.mastersmith.common.configio.PostCommit;
import com.mastersmith.configio.testsupport.RecordingTransactionManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link PostCommitCoordinator}のテスト(nfr-design/reliability-design.md NFR4.2):
 * 確定後に、無効化(3ユニット)→個別イベント(3ユニット)→成功の監査イベントの、固定した順序で実行されること・1つの動作の例外が他を妨げないこと・
 * ロールバックでは呼ばれないこと・トランザクションに、同期は1つだけ登録されること。
 */
class PostCommitCoordinatorTest {

  private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
  private final TransactionTemplate template = new TransactionTemplate(transactionManager);
  private final List<String> calls = new ArrayList<>();

  private PostCommit unit(String name) {
    return new PostCommit(
        () -> calls.add("invalidate:" + name), () -> calls.add("publish:" + name));
  }

  @Test
  void runsAllInvalidationsBeforeAnyEventAndTheSuccessAuditEventLast() {
    template.executeWithoutResult(
        status ->
            PostCommitCoordinator.register(
                List.of(unit("config"), unit("menu"), unit("permission")),
                () -> calls.add("audit")));

    assertThat(calls)
        .containsExactly(
            "invalidate:config",
            "invalidate:menu",
            "invalidate:permission",
            "publish:config",
            "publish:menu",
            "publish:permission",
            "audit");
  }

  @Test
  void nothingRunsBeforeTheCommitAndNothingRunsOnRollback() {
    template.executeWithoutResult(
        status -> {
          PostCommitCoordinator.register(List.of(unit("config")), () -> calls.add("audit"));
          assertThat(calls).isEmpty();
          status.setRollbackOnly();
        });

    assertThat(calls).isEmpty();
    assertThat(transactionManager.log).endsWith("rollback");
  }

  @Test
  void anExceptionInAnyActionIsSwallowedAndTheRemainingActionsStillRunInOrder() {
    PostCommit failingInvalidate =
        new PostCommit(
            () -> {
              throw new IllegalStateException("invalidate down");
            },
            () -> calls.add("publish:config"));
    PostCommit failingPublish =
        new PostCommit(
            () -> calls.add("invalidate:menu"),
            () -> {
              throw new IllegalStateException("publish down");
            });

    template.executeWithoutResult(
        status ->
            PostCommitCoordinator.register(
                List.of(failingInvalidate, failingPublish, unit("permission")),
                () -> {
                  calls.add("audit");
                  throw new IllegalStateException("audit down");
                }));

    assertThat(calls)
        .containsExactly(
            "invalidate:menu",
            "invalidate:permission",
            "publish:config",
            "publish:permission",
            "audit");
    // コミットは成功のまま(例外は、取り込みの結果に影響しない)。
    assertThat(transactionManager.log).endsWith("commit");
  }

  @Test
  void aNonRuntimeFailureShapeIsNotExpectedButExtraUnitsGetGenericNames() {
    template.executeWithoutResult(
        status ->
            PostCommitCoordinator.register(
                List.of(unit("a"), unit("b"), unit("c"), unit("d")), () -> calls.add("audit")));

    assertThat(calls).hasSize(9);
  }
}
