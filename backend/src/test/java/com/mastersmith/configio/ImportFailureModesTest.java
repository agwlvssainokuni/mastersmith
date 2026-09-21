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

package com.mastersmith.configio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.util.AopTestUtils.getUltimateTargetObject;

import com.mastersmith.audit.entity.AuditLogEntry;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import com.mastersmith.menu.MenuStructureApi;
import com.mastersmith.permission.cache.PermissionCacheControl;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 並行・競合・障害のテスト(実際のスレッド・ラッチ・実際の組込みH2。code-generation-plan.md Step
 * 20、nfr-design/reliability-design.md NFR4.3〜NFR4.6、レビュー指摘R-15): 更新の競合({@code
 * REPEATABLE_READ}のスナップショットの後に、別の取り込みが
 * 確定した行の書き込み)・行のロック待ちのタイムアウト・内部設定DBの障害が、503に変換され、失敗の監査イベント(UNEXPECTED)が、確定して残ること。確定後の動作の1つの例外が、他を妨げないこと。
 */
class ImportFailureModesTest extends ConfigIoIntegrationTestBase {

  @MockitoSpyBean private MenuStructureApi menuStructureApi;
  @MockitoSpyBean private com.mastersmith.config.cache.ConfigCache configCacheSpy;
  @Autowired private PermissionCacheControl permissionControl;

  private final ExecutorService executor = Executors.newCachedThreadPool();

  private MenuStructureApi menuSpy() {
    return getUltimateTargetObject(menuStructureApi);
  }

  @AfterEach
  void tearDown() {
    reset(menuSpy());
    reset(configCacheSpy);
    executor.shutdownNow();
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static ConfigDocument administered(Spec spec, int displayOrderShift) {
    ConfigDocument m =
        ConfigDocumentFactory.withAdministrator(ConfigDocumentFactory.mechanical(spec), ADMIN_ROLE);
    List<ConfigDocument.TableEntry> tables =
        m.schema().tables().stream()
            .map(
                t ->
                    new ConfigDocument.TableEntry(
                        t.schemaName(),
                        t.tableName(),
                        t.displayOrder() + displayOrderShift,
                        t.optimisticLockColumn(),
                        t.columns()))
            .toList();
    return new ConfigDocument(
        1,
        null,
        null,
        new ConfigDocument.SchemaSection(tables, m.schema().translations()),
        m.menu(),
        m.rbac());
  }

  private void seed() throws Exception {
    MvcResult seeded = postImport(administered(Spec.small(), 0));
    assertThat(seeded.getResponse().getStatus()).isEqualTo(200);
    actAs("failure-admin", ADMIN_ROLE);
  }

  private AuditLogEntry latestAudit() {
    List<AuditLogEntry> entries = importAuditEntriesOf("failure-admin");
    assertThat(entries).isNotEmpty();
    return entries.get(0);
  }

  @Test
  void
      aRowChangedByAnotherImportAfterTheSnapshotIsAnUpdateConflictMappedTo503AndTheFailureEventIsPersisted()
          throws Exception {
    seed();
    long auditsBefore = importAuditEntriesOf("failure-admin").size();
    CountDownLatch bValidating = new CountDownLatch(1);
    CountDownLatch mayContinue = new CountDownLatch(1);
    // 取り込みB: 検証の途中(スナップショットの取得後)で止める。
    doAnswer(
            invocation -> {
              bValidating.countDown();
              await(mayContinue);
              return invocation.callRealMethod();
            })
        .when(menuSpy())
        .validateMenuStructure(any());

    Future<MvcResult> importB = executor.submit(() -> postImport(administered(Spec.small(), 2)));
    await(bValidating);
    // 取り込みA(同じ行を変更)を、その間に、確定させる。
    reset(menuSpy());
    MvcResult importA = postImport(administered(Spec.small(), 1));
    assertThat(importA.getResponse().getStatus())
        .as(importA.getResponse().getContentAsString())
        .isEqualTo(200);
    mayContinue.countDown();
    MvcResult resultB = importB.get(30, TimeUnit.SECONDS);

    // Bは、Aが確定した同じ行を書き込もうとして、更新の競合(REPEATABLE_READの副産物)で失敗する。503(汎用のメッセージ)。
    assertThat(resultB.getResponse().getStatus())
        .as(resultB.getResponse().getContentAsString())
        .isEqualTo(503);
    JsonNode problem =
        ConfigDocumentFactory.JSON.readTree(resultB.getResponse().getContentAsString());
    assertThat(problem.get("code").stringValue()).isEqualTo("config.import.unavailable");
    assertThat(resultB.getResponse().getContentAsString())
        .doesNotContain("Concurrent")
        .doesNotContain("jdbc")
        .doesNotContain("Exception");
    // 内部設定DBは、Aの内容(Bは、ロールバック)。
    assertThat(jdbc.queryForObject("select min(display_order) from table_config", Integer.class))
        .isEqualTo(1);
    // 失敗の監査イベント(UNEXPECTED)が、ロールバックの外で、確定して残る(R-15)。Aの成功のイベントと、Bの失敗のイベント。
    List<AuditLogEntry> entries = importAuditEntriesOf("failure-admin");
    assertThat(entries).hasSize((int) auditsBefore + 2);
    assertThat(entries)
        .anySatisfy(
            e -> assertThat(e.getAfterValue()).containsEntry("failureCategory", "UNEXPECTED"));
  }

  @Test
  void aRowLockedByAnotherImportInProgressTimesOutAndIsMappedTo503WhileTheFirstImportStillSucceeds()
      throws Exception {
    seed();
    CountDownLatch schemaApplied = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    // 取り込みA: schemaの反映が済み(未確定。行のロックを保持)、メニューの反映の直前で止める。
    doAnswer(
            invocation -> {
              schemaApplied.countDown();
              await(mayFinish);
              return invocation.callRealMethod();
            })
        .when(menuSpy())
        .applyMenuStructure(any());
    Future<MvcResult> importA = executor.submit(() -> postImport(administered(Spec.small(), 1)));
    await(schemaApplied);

    // 取り込みB: 同じ行を書き込もうとして、ロック待ち(H2の既定2秒)のタイムアウトになる。
    MvcResult resultB = postImport(administered(Spec.small(), 2));

    assertThat(resultB.getResponse().getStatus())
        .as(resultB.getResponse().getContentAsString())
        .isEqualTo(503);
    mayFinish.countDown();
    assertThat(importA.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
    assertThat(jdbc.queryForObject("select min(display_order) from table_config", Integer.class))
        .isEqualTo(1);
    assertThat(importAuditEntriesOf("failure-admin"))
        .anySatisfy(
            e -> assertThat(e.getAfterValue()).containsEntry("failureCategory", "UNEXPECTED"));
  }

  @Test
  void aDatabaseFailureDuringApplyIs503RollsBackAndPersistsAnUnexpectedEvent() throws Exception {
    seed();
    long tablesBefore = jdbc.queryForObject("select count(*) from table_config", Long.class);
    doThrow(new DataAccessResourceFailureException("connection lost"))
        .when(menuSpy())
        .applyMenuStructure(any());

    MvcResult result = postImport(administered(new Spec(1, 1, 2, 2, 2, 1, 3, 1, 1), 0));

    assertThat(result.getResponse().getStatus()).isEqualTo(503);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("connection lost");
    assertThat(jdbc.queryForObject("select count(*) from table_config", Long.class))
        .isEqualTo(tablesBefore);
    assertThat(latestAudit().getAfterValue()).containsEntry("failureCategory", "UNEXPECTED");
  }

  @Test
  void aFailureInOnePostCommitActionDoesNotStopTheOthersNorTheSuccessResponseNorTheSuccessEvent()
      throws Exception {
    seed();
    long generation = permissionControl.generation();
    long auditsBefore = importAuditEntriesOf("failure-admin").size();
    // config-engineのキャッシュの無効化(確定後の順1)が、例外を投げる。
    doThrow(new IllegalStateException("invalidate failed")).when(configCacheSpy).invalidate();

    MvcResult result = postImport(administered(Spec.small(), 1));

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    // 後続の動作(permission-engineの無効化・個別イベント・成功の監査イベント)は、実行された。
    assertThat(permissionControl.generation()).isGreaterThan(generation);
    List<AuditLogEntry> entries = importAuditEntriesOf("failure-admin");
    assertThat(entries).hasSize((int) auditsBefore + 1);
    assertThat(entries.get(0).getOperationType()).isEqualTo("CONFIG_IMPORT_SUCCEEDED");
  }
}
