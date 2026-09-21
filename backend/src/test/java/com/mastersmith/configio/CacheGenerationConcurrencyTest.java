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
import static org.mockito.Mockito.reset;
import static org.springframework.test.util.AopTestUtils.getUltimateTargetObject;

import com.mastersmith.common.cache.ReloadFailureBackoff;
import com.mastersmith.config.cache.ConfigCache;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import com.mastersmith.configio.document.ConfigDocument;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory;
import com.mastersmith.configio.testsupport.ConfigDocumentFactory.Spec;
import com.mastersmith.configio.testsupport.ConfigIoIntegrationTestBase;
import com.mastersmith.menu.MenuStructureApi;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * キャッシュの世代の競合のテスト(実際のスレッド・ラッチ・実際の組込みH2。{@code Thread.sleep}による時間待ちに依存しない。code-generation-plan.md
 * Step 20、nfr-design/reliability-design.md NFR4.2): 再読み込みの最中の {@code
 * invalidate()}が失われない・個別の更新の反映が、取り込みの無効化を上書きしない・取り込みの未確定の内容が、キャッシュに載らない。
 */
class CacheGenerationConcurrencyTest extends ConfigIoIntegrationTestBase {

  private static final String READER = "cache-reader";

  @Autowired private TableConfigRepository tableConfigRepository;
  @Autowired private ColumnConfigRepository columnConfigRepository;
  @Autowired private TranslationEntryRepository translationEntryRepository;

  @Autowired
  @Qualifier("transactionManager")
  private PlatformTransactionManager transactionManager;

  @MockitoSpyBean private MenuStructureApi menuStructureApi;

  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(READER + "-" + thread.threadId());
            return thread;
          });

  @AfterEach
  void tearDown() {
    reset(menuSpy());
    executor.shutdownNow();
  }

  private MenuStructureApi menuSpy() {
    return getUltimateTargetObject(menuStructureApi);
  }

  private static boolean isReader() {
    return Thread.currentThread().getName().startsWith(READER);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private void seed() throws Exception {
    MvcResult seeded =
        postImport(
            ConfigDocumentFactory.withAdministrator(
                ConfigDocumentFactory.mechanical(Spec.small()), ADMIN_ROLE));
    assertThat(seeded.getResponse().getStatus()).isEqualTo(200);
    actAs("cache-admin", ADMIN_ROLE);
  }

  /**
   * 実際のリポジトリ(実際のH2)を包み、{@code findAll}の呼び出しを、読み取りのスレッドに限って、止める{@link
   * ConfigCache}(独立した読み取り専用トランザクション(REQUIRES_NEW・REPEATABLE_READ)は、 実際のトランザクションマネージャーで開く)。
   */
  private ConfigCache pausableCache(CountDownLatch loading, CountDownLatch mayFinish) {
    TableConfigRepository pausable =
        (TableConfigRepository)
            Proxy.newProxyInstance(
                TableConfigRepository.class.getClassLoader(),
                new Class<?>[] {TableConfigRepository.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("findAll") && isReader()) {
                    loading.countDown();
                    await(mayFinish);
                  }
                  try {
                    return method.invoke(tableConfigRepository, args);
                  } catch (InvocationTargetException e) {
                    throw e.getCause();
                  }
                });
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setReadOnly(true);
    return new ConfigCache(
        pausable,
        columnConfigRepository,
        translationEntryRepository,
        template,
        Duration.ofSeconds(20),
        new ReloadFailureBackoff(Duration.ofSeconds(2)));
  }

  @Test
  void anInvalidateDuringAReloadOnTheRealDatabaseIsNotLostAndTheNextReadReloads() throws Exception {
    seed();
    CountDownLatch loading = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    ConfigCache cache = pausableCache(loading, mayFinish);
    cache.invalidate();

    Future<Integer> reader = executor.submit(() -> cache.allTableConfigs().size());
    await(loading);
    cache.invalidate(); // 読み込みの最中に、別の取り込みが確定した。
    mayFinish.countDown();

    assertThat(reader.get(20, TimeUnit.SECONDS)).isEqualTo(4);
    assertThat(cache.isStale()).isTrue();
    assertThat(cache.allTableConfigs()).hasSize(4);
    assertThat(cache.isStale()).isFalse();
  }

  @Test
  void anIndividualUpdateReloadRacingWithAnInvalidationDoesNotOverwriteIt() throws Exception {
    seed();
    CountDownLatch loading = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    ConfigCache cache = pausableCache(loading, mayFinish);
    cache.reload(); // 呼び出し元のスレッド(読み取りのスレッドではない)で、VALIDにしておく。
    assertThat(cache.isStale()).isFalse();

    // 個別の更新の反映(config-engineの既存の書き込みの経路が呼ぶreload)が、全体を読み直している最中に、取り込みが確定した。
    Future<?> update = executor.submit(cache::reload);
    await(loading);
    cache.invalidate();
    mayFinish.countDown();
    update.get(20, TimeUnit.SECONDS);

    // 個別の更新の反映が、取り込みの無効化を、上書きして、VALIDに戻してはならない(古い内容が、残ってはならない)。
    assertThat(cache.isStale()).isTrue();
    assertThat(cache.allTableConfigs()).hasSize(4);
  }

  @Test
  void theUncommittedContentOfAnImportInProgressNeverReachesTheCache() throws Exception {
    seed();
    ConfigDocument different =
        ConfigDocumentFactory.withAdministrator(
            ConfigDocumentFactory.mechanical(new Spec(1, 1, 2, 2, 2, 1, 3, 1, 1)), ADMIN_ROLE);
    CountDownLatch schemaApplied = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    // schemaの反映が済み(テーブルは、4件から1件に置き換わった。未確定)、メニューの反映の直前で、取り込みを止める。
    doAnswer(
            invocation -> {
              schemaApplied.countDown();
              await(mayFinish);
              return invocation.callRealMethod();
            })
        .when(menuSpy())
        .applyMenuStructure(any());

    Future<MvcResult> importing = executor.submit(() -> postImport(different));
    await(schemaApplied);
    configCache.invalidate();
    List<TableConfig> whileImporting = configCache.allTableConfigs(); // STALEの再読み込み: 確定済みの内容だけ。
    boolean validWhileImporting = !configCache.isStale();
    mayFinish.countDown();
    assertThat(importing.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);

    assertThat(whileImporting).hasSize(4); // 取り込み前の(確定済みの)内容。未確定の1件ではない。
    assertThat(validWhileImporting).isTrue();
    // 確定後の無効化で、次の読み取りが、新しい内容を読む。
    assertThat(configCache.isStale()).isTrue();
    assertThat(configCache.allTableConfigs()).hasSize(1);
  }
}
