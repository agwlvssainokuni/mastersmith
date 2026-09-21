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

package com.mastersmith.config.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mastersmith.common.cache.CacheReloadUnavailableException;
import com.mastersmith.common.cache.ReloadFailureBackoff;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * {@link ConfigCache}の世代管理(NFR4.2、レビュー指摘R-12・R-14)のテスト: {@code invalidate()}・{@code
 * STALE}の間の読み取りでの再読み込み(独立したトランザクション・待ちの上限・二重の確認・世代の確認)・ 失敗の抑制・個別の更新の経路。競合は、実際のスレッドとラッチで作り、{@code
 * Thread.sleep}による時間待ちに依存しない(unit-test-instructions.md)。
 */
class ConfigCacheGenerationTest {

  private TableConfigRepository tableConfigRepository;
  private ColumnConfigRepository columnConfigRepository;
  private TranslationEntryRepository translationEntryRepository;
  private final AtomicInteger transactionsOpened = new AtomicInteger();
  private final AtomicLong nanos = new AtomicLong(1_000);
  private final ExecutorService executor = Executors.newCachedThreadPool();

  @BeforeEach
  void setUp() {
    tableConfigRepository = mock(TableConfigRepository.class);
    columnConfigRepository = mock(ColumnConfigRepository.class);
    translationEntryRepository = mock(TranslationEntryRepository.class);
    when(columnConfigRepository.findAll()).thenReturn(List.of());
    when(translationEntryRepository.findAll()).thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private TransactionOperations countingTransaction() {
    return new TransactionOperations() {
      @Override
      public <T> T execute(TransactionCallback<T> action) {
        transactionsOpened.incrementAndGet();
        return action.doInTransaction(mock(TransactionStatus.class));
      }
    };
  }

  private ConfigCache cache(Duration waitTimeout) {
    return new ConfigCache(
        tableConfigRepository,
        columnConfigRepository,
        translationEntryRepository,
        countingTransaction(),
        waitTimeout,
        new ReloadFailureBackoff(Duration.ofSeconds(2), nanos::get));
  }

  private static TableConfig table(String name) {
    return new TableConfig("s", name);
  }

  @Test
  void invalidateMakesTheCacheStaleAndAdvancesTheGeneration() {
    ConfigCache cache = cache(Duration.ofSeconds(5));
    long before = cache.generation();

    cache.invalidate();

    assertThat(cache.isStale()).isTrue();
    assertThat(cache.generation()).isEqualTo(before + 1);
  }

  @Test
  void aStaleReadReloadsInAnIndependentTransactionAndBecomesValidAgain() {
    when(tableConfigRepository.findAll()).thenReturn(List.of(table("a")));
    ConfigCache cache = cache(Duration.ofSeconds(5));
    cache.invalidate();

    assertThat(cache.allTableConfigs()).hasSize(1);

    assertThat(cache.isStale()).isFalse();
    assertThat(transactionsOpened).hasValue(1);
    // VALIDになった後の読み取りは、再読み込みしない。
    assertThat(cache.allTableConfigs()).hasSize(1);
    assertThat(transactionsOpened).hasValue(1);
  }

  @Test
  void anIndividualUpdateReloadOnAStaleCacheOnlyInvalidatesAndDoesNotReadTheDatabase() {
    ConfigCache cache = cache(Duration.ofSeconds(5));
    cache.invalidate();
    long generation = cache.generation();

    cache.reload();

    assertThat(cache.isStale()).isTrue();
    assertThat(cache.generation()).isGreaterThan(generation);
    verifyNoInteractions(tableConfigRepository);
  }

  @Test
  void anIndividualUpdateReloadOnAValidCacheReplacesTheSnapshotWithoutChangingTheGeneration() {
    when(tableConfigRepository.findAll()).thenReturn(List.of(table("a")));
    ConfigCache cache = cache(Duration.ofSeconds(5));
    long generation = cache.generation();

    cache.reload();

    assertThat(cache.isStale()).isFalse();
    assertThat(cache.generation()).isEqualTo(generation);
    assertThat(cache.allTableConfigs()).hasSize(1);
    // 個別の更新は、呼び出し元のトランザクションの中で読む(独立したトランザクションは開かない)。
    assertThat(transactionsOpened).hasValue(0);
  }

  @Test
  void anInvalidateDuringAReloadIsNotLostAndTheCacheStaysStale() throws Exception {
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    List<TableConfig> old = List.of(table("old"));
    when(tableConfigRepository.findAll())
        .thenAnswer(
            invocation -> {
              loadStarted.countDown();
              await(mayFinish);
              return old;
            })
        .thenReturn(List.of(table("new")));
    ConfigCache cache = cache(Duration.ofSeconds(5));
    cache.invalidate();

    Future<List<TableConfig>> reader = executor.submit(cache::allTableConfigs);
    await(loadStarted);
    cache.invalidate(); // 読み込みの最中に、別の取り込みが確定した。
    mayFinish.countDown();

    // 読んだ内容は、その呼び出しには返す。ただし、VALIDにはしない(invalidateが、失われない)。
    assertThat(reader.get(5, TimeUnit.SECONDS)).hasSize(1);
    assertThat(cache.isStale()).isTrue();
    // 次の読み取りが、再び再読み込みを行い、新しい内容になる。
    assertThat(cache.allTableConfigs().get(0).getTableName()).isEqualTo("new");
    assertThat(cache.isStale()).isFalse();
  }

  @Test
  void anIndividualUpdateDuringAnInvalidateIsNotWrittenOverTheInvalidation() throws Exception {
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    when(tableConfigRepository.findAll())
        .thenAnswer(
            invocation -> {
              loadStarted.countDown();
              await(mayFinish);
              return List.of(table("individual"));
            });
    ConfigCache cache = cache(Duration.ofSeconds(5));

    Future<?> update = executor.submit(cache::reload);
    await(loadStarted);
    cache.invalidate(); // 個別の更新の読み込みの最中に、取り込みが確定した。
    mayFinish.countDown();
    update.get(5, TimeUnit.SECONDS);

    // 個別の更新の結果で、VALIDに戻してはならない(古い内容が、取り込みの内容を上書きして残る)。
    assertThat(cache.isStale()).isTrue();
  }

  @Test
  void manyConcurrentStaleReadersTriggerExactlyOneReload() throws Exception {
    when(tableConfigRepository.findAll()).thenReturn(List.of(table("a")));
    ConfigCache cache = cache(Duration.ofSeconds(5));
    cache.invalidate();
    int readers = 8;
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (int i = 0; i < readers; i++) {
      futures.add(
          executor.submit(
              () -> {
                await(go);
                return cache.allTableConfigs().size();
              }));
    }
    go.countDown();

    for (Future<Integer> future : futures) {
      assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(1);
    }
    // 二重の確認: 排他を得た後に、すでにVALIDなら、再読み込みしない。
    verify(tableConfigRepository, times(1)).findAll();
    assertThat(transactionsOpened).hasValue(1);
  }

  @Test
  void aReaderWaitingForTheReloadLockGivesUpAtTheWaitLimitWith503Semantics() throws Exception {
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    when(tableConfigRepository.findAll())
        .thenAnswer(
            invocation -> {
              loadStarted.countDown();
              await(mayFinish);
              return List.of(table("a"));
            });
    ConfigCache cache = cache(Duration.ofMillis(100));
    cache.invalidate();
    Future<?> holder = executor.submit(cache::allTableConfigs);
    await(loadStarted);

    assertThatThrownBy(cache::allTableConfigs)
        .isInstanceOf(CacheReloadUnavailableException.class)
        .isInstanceOf(org.springframework.dao.DataAccessException.class);

    mayFinish.countDown();
    holder.get(5, TimeUnit.SECONDS);
  }

  @Test
  void aFailedReloadIsSharedAndSuppressedForTheBackoffThenRetried() {
    when(tableConfigRepository.findAll())
        .thenThrow(new IllegalStateException("db down"))
        .thenReturn(List.of(table("a")));
    ConfigCache cache = cache(Duration.ofSeconds(5));
    cache.invalidate();

    assertThatThrownBy(cache::allTableConfigs).isInstanceOf(CacheReloadUnavailableException.class);
    // 抑制の期間の中: 再読み込みを試みず、すぐに失敗する(DBを、もう一度読まない)。
    assertThatThrownBy(cache::allTableConfigs).isInstanceOf(CacheReloadUnavailableException.class);
    assertThatThrownBy(cache::allTableConfigs).isInstanceOf(CacheReloadUnavailableException.class);
    verify(tableConfigRepository, times(1)).findAll();
    assertThat(cache.isStale()).isTrue();

    // 抑制の期間が過ぎたら、再試行する。
    nanos.addAndGet(Duration.ofSeconds(3).toNanos());
    assertThat(cache.allTableConfigs()).hasSize(1);
    verify(tableConfigRepository, times(2)).findAll();
    assertThat(cache.isStale()).isFalse();
  }

  @Test
  void whileTheDatabaseIsDownWaitingReadersDoNotChainTheirTimeouts() throws Exception {
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch mayFail = new CountDownLatch(1);
    AtomicInteger loads = new AtomicInteger();
    when(tableConfigRepository.findAll())
        .thenAnswer(
            invocation -> {
              loads.incrementAndGet();
              loadStarted.countDown();
              await(mayFail);
              throw new IllegalStateException("connection timeout");
            });
    ConfigCache cache = cache(Duration.ofSeconds(5));
    cache.invalidate();

    Future<?> first = executor.submit(cache::allTableConfigs);
    await(loadStarted);
    List<Future<?>> waiting = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      waiting.add(executor.submit(cache::allTableConfigs));
    }
    mayFail.countDown();

    for (Future<?> future : waiting) {
      assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
          .hasCauseInstanceOf(CacheReloadUnavailableException.class);
    }
    assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS))
        .hasCauseInstanceOf(CacheReloadUnavailableException.class);
    // 待っていたスレッドは、失敗を共有し、それぞれがDBの障害を待つ(直列の連鎖)ことは、ない。
    assertThat(loads).hasValue(1);
  }

  @Test
  void uncommittedContentOfTheCallersTransactionIsNotLoadedByAStaleRead() {
    // 再読み込みは、独立したトランザクション(TransactionOperations)の中で行う。呼び出し元のトランザクションには、参加しない。
    when(tableConfigRepository.findAll()).thenReturn(List.of());
    ConfigCache cache = cache(Duration.ofSeconds(5));
    cache.invalidate();

    cache.allTableConfigs();

    assertThat(transactionsOpened).hasValue(1);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
