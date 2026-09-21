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

package com.mastersmith.permission.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mastersmith.common.cache.CacheReloadUnavailableException;
import com.mastersmith.common.cache.ReloadFailureBackoff;
import com.mastersmith.permission.bootstrap.BootstrapStateChecker;
import com.mastersmith.permission.dto.EffectivePermission;
import com.mastersmith.permission.entity.PermissionLevel;
import com.mastersmith.permission.entity.ScopeType;
import com.mastersmith.permission.escalation.PermissionEscalationChecker;
import com.mastersmith.permission.rbacio.RbacTransfer;
import com.mastersmith.permission.repository.AuxiliaryPermissionRepository;
import com.mastersmith.permission.repository.GroupMembershipRepository;
import com.mastersmith.permission.repository.GroupRoleRepository;
import com.mastersmith.permission.repository.PrimaryPermissionRepository;
import com.mastersmith.permission.repository.RoleRepository;
import com.mastersmith.permission.resolver.PermissionResolver;
import com.mastersmith.permission.service.PermissionEngineApiImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 実効権限のキャッシュの世代管理(nfr-design/reliability-design.md NFR4.2、レビュー指摘R-14)のテスト:
 * 世代番号がキーに含まれ、無効化の後に、進行中の計算が古い値を格納しても、読み取りに使われないこと。
 * 計算が、独立した読み取り専用トランザクションで行われること・失敗の抑制・トランザクションの中ではキャッシュを介さないこと。競合は、実際のスレッドとラッチで作り、時間待ちに依存しない。
 */
class PermissionCacheGenerationTest {

  private static final String ROLE = "role-1";

  private PermissionResolver resolver;
  private RoleRepository roleRepository;
  private Cache<PermissionCacheKey, EffectivePermission> cache;
  private PermissionCacheControl control;
  private final AtomicInteger transactionsOpened = new AtomicInteger();
  private final AtomicLong nanos = new AtomicLong(1_000);
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private PermissionEngineApiImpl api;

  @BeforeEach
  void setUp() {
    resolver = mock(PermissionResolver.class);
    roleRepository = mock(RoleRepository.class);
    when(roleRepository.existsById(ROLE)).thenReturn(true);
    cache = Caffeine.newBuilder().build();
    control = new PermissionCacheControl(cache);
    api =
        new PermissionEngineApiImpl(
            roleRepository,
            mock(PrimaryPermissionRepository.class),
            mock(AuxiliaryPermissionRepository.class),
            mock(GroupMembershipRepository.class),
            mock(GroupRoleRepository.class),
            resolver,
            mock(PermissionEscalationChecker.class),
            mock(BootstrapStateChecker.class),
            cache,
            new SimpleMeterRegistry(),
            control,
            mock(RbacTransfer.class),
            new TransactionOperations() {
              @Override
              public <T> T execute(TransactionCallback<T> action) {
                transactionsOpened.incrementAndGet();
                return action.doInTransaction(mock(TransactionStatus.class));
              }
            },
            new ReloadFailureBackoff(Duration.ofSeconds(2), nanos::get));
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  private EffectivePermission read() {
    return api.resolveEffectivePermission(ROLE, ScopeType.SCHEMA, "s");
  }

  private static EffectivePermission level(PermissionLevel level) {
    return new EffectivePermission(level, false, false);
  }

  @Test
  void aReadComputesInAnIndependentTransactionAndIsCachedForTheSameGeneration() {
    when(resolver.resolve(ROLE, ScopeType.SCHEMA, "s")).thenReturn(level(PermissionLevel.READ));

    assertThat(read().level()).isEqualTo(PermissionLevel.READ);
    assertThat(read().level()).isEqualTo(PermissionLevel.READ);

    verify(resolver, times(1)).resolve(ROLE, ScopeType.SCHEMA, "s");
    assertThat(transactionsOpened).hasValue(1);
  }

  @Test
  void invalidateAdvancesTheGenerationAndForcesARecomputation() {
    when(resolver.resolve(ROLE, ScopeType.SCHEMA, "s"))
        .thenReturn(level(PermissionLevel.READ))
        .thenReturn(level(PermissionLevel.FULL));
    long generation = control.generation();
    assertThat(read().level()).isEqualTo(PermissionLevel.READ);

    control.invalidate();

    assertThat(control.generation()).isEqualTo(generation + 1);
    assertThat(read().level()).isEqualTo(PermissionLevel.FULL);
  }

  @Test
  void aValueStoredByAComputationStartedBeforeTheInvalidationIsNeverUsedAfterIt() throws Exception {
    CountDownLatch computing = new CountDownLatch(1);
    CountDownLatch mayFinish = new CountDownLatch(1);
    when(resolver.resolve(ROLE, ScopeType.SCHEMA, "s"))
        .thenAnswer(
            invocation -> {
              computing.countDown();
              await(mayFinish);
              return level(PermissionLevel.READ); // 古い設定に基づく値
            })
        .thenReturn(level(PermissionLevel.FULL)); // 取り込みの後の、新しい設定に基づく値
    Future<EffectivePermission> inFlight = executor.submit(this::read);
    await(computing);

    control.invalidate(); // 取り込みが確定した。
    mayFinish.countDown();
    assertThat(inFlight.get(5, TimeUnit.SECONDS).level()).isEqualTo(PermissionLevel.READ);

    // 進行中の計算が、古い世代のキーに、古い値を格納した。新しい世代の読み取りは、それを使わない。
    assertThat(read().level()).isEqualTo(PermissionLevel.FULL);
  }

  @Test
  void aFailedComputationIsSuppressedForTheBackoffThenRetried() {
    when(resolver.resolve(ROLE, ScopeType.SCHEMA, "s"))
        .thenThrow(new DataAccessResourceFailureException("db down"))
        .thenReturn(level(PermissionLevel.READ));

    assertThatThrownBy(this::read)
        .isInstanceOf(CacheReloadUnavailableException.class)
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    // 抑制の期間の中は、計算を試みず、すぐに失敗する(他のキーも同様。DBを読まない)。
    assertThatThrownBy(this::read).isInstanceOf(CacheReloadUnavailableException.class);
    assertThatThrownBy(() -> api.resolveEffectivePermission(ROLE, ScopeType.TABLE, "other"))
        .isInstanceOf(CacheReloadUnavailableException.class);
    verify(resolver, times(1)).resolve(any(), any(), any());

    nanos.addAndGet(Duration.ofSeconds(3).toNanos());
    assertThat(read().level()).isEqualTo(PermissionLevel.READ);
  }

  @Test
  void exceptionsThatAreNotDatabaseFailuresPropagateUnchangedAndDoNotStartTheBackoff() {
    when(resolver.resolve(ROLE, ScopeType.SCHEMA, "s"))
        .thenThrow(new IllegalArgumentException("bad"))
        .thenReturn(level(PermissionLevel.READ));

    assertThatThrownBy(this::read).isInstanceOf(IllegalArgumentException.class);

    assertThat(read().level()).isEqualTo(PermissionLevel.READ);
  }

  @Test
  void insideACallersTransactionTheCacheIsBypassedAndNothingIsStored() {
    when(resolver.resolve(ROLE, ScopeType.SCHEMA, "s"))
        .thenReturn(level(PermissionLevel.READ))
        .thenReturn(level(PermissionLevel.FULL));
    TransactionSynchronizationManager.setActualTransactionActive(true);

    // 呼び出し元のトランザクションの中では、そのトランザクションの見える内容で解決し、共有のキャッシュに載せない。
    assertThat(read().level()).isEqualTo(PermissionLevel.READ);
    assertThat(read().level()).isEqualTo(PermissionLevel.FULL);

    assertThat(transactionsOpened).hasValue(0);
    assertThat(cache.asMap()).isEmpty();
  }

  @Test
  void aBlankOrUnknownRoleDoesNotTouchTheResolver() {
    when(roleRepository.existsById("unknown")).thenReturn(false);

    assertThat(api.resolveEffectivePermission("unknown", ScopeType.SCHEMA, "s"))
        .isEqualTo(EffectivePermission.NONE);
    assertThat(api.resolveEffectivePermission(" ", ScopeType.SCHEMA, "s"))
        .isEqualTo(EffectivePermission.NONE);

    verifyNoInteractions(resolver);
  }

  @Test
  void theKeyKeepsBackwardCompatibleThreeArgumentEqualityAtGenerationZero() {
    assertThat(new PermissionCacheKey("r", ScopeType.TABLE, "t"))
        .isEqualTo(new PermissionCacheKey("r", ScopeType.TABLE, "t", 0L))
        .isNotEqualTo(new PermissionCacheKey("r", ScopeType.TABLE, "t", 1L));
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
