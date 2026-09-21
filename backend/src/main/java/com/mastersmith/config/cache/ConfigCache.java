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

import com.mastersmith.common.cache.CacheReloadUnavailableException;
import com.mastersmith.common.cache.ReloadFailureBackoff;
import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.entity.TranslationEntry;
import com.mastersmith.config.repository.ColumnConfigRepository;
import com.mastersmith.config.repository.TableConfigRepository;
import com.mastersmith.config.repository.TranslationEntryRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 起動時に内部設定DBから全設定を読み込み、不変スナップショットとしてアプリケーション内
 * メモリに保持するキャッシュ(performance-design.md「Load-All-At-Startup」パターン、NFR1.1, NFR1.2)。
 *
 * <p>読み取りはロックフリー(不変スナップショットの参照取得のみ)。個別の更新の反映は{@link
 * #reload()}によりスナップショット全体を再構築し、原子的に差し替える(部分更新は行わない)。
 *
 * <p><b>世代管理(config-import-export nfr-design/reliability-design.md
 * NFR4.2「各ユニットのキャッシュの共通の契約」、レビュー指摘R-12・R-14・R-17)</b>: 状態({@code VALID}・{@code
 * STALE})・世代番号・スナップショットを、<b>1つの不変な値</b>({@link State})にまとめ、compare-and-setで置き換える。
 *
 * <ul>
 *   <li>{@link #invalidate()}: 世代番号を進め、{@code STALE}にする。メモリ上の値の置き換えだけで、失敗しえない(取り込みの確定後に呼ばれる)。
 *   <li>読み取り: {@code STALE}なら、排他のロックを、待ちの上限({@code
 *       reload-wait-timeout})つきで取り、二重の確認の後、<b>独立した読み取り専用トランザクション</b>({@code REQUIRES_NEW}・{@code
 *       REPEATABLE_READ})で、全件を再読み込みする。呼び出し元の未確定の内容は、読まない・載せない。終了時に、世代番号が読み込みの開始時のままの場合に限り、{@code
 *       VALID}にする(読み込み中に{@code invalidate()}が呼ばれていたら、{@code STALE}のまま。読んだ内容は、その呼び出しには返す)。
 *   <li>再読み込みの失敗は、その読み取りの{@link CacheReloadUnavailableException}(503)とし、失敗の後、抑制の期間({@code
 *       reload-failure-backoff})の間は、再読み込みを試みず、すぐに失敗を返す(待ちの連鎖を防ぐ)。
 *   <li>個別の更新({@link #reload()}): {@code VALID}のときは、従来どおり、全体を読み直して差し替える。{@code
 *       STALE}のとき、および、更新の最中に世代が進んだときは、キャッシュを更新せず、 {@link
 *       #invalidate()}を呼ぶ(更新が、再読み込みに上書きされて失われることを防ぐ)。
 * </ul>
 */
@Component
public class ConfigCache {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigCache.class);

  /** 再読み込みの排他を待つ上限の既定(config-import-export nfr-design)。 */
  static final Duration DEFAULT_RELOAD_WAIT_TIMEOUT = Duration.ofSeconds(5);

  /** 再読み込みの失敗の抑制の期間の既定(config-import-export nfr-design)。 */
  static final Duration DEFAULT_RELOAD_FAILURE_BACKOFF = Duration.ofSeconds(2);

  private final TableConfigRepository tableConfigRepository;
  private final ColumnConfigRepository columnConfigRepository;
  private final TranslationEntryRepository translationEntryRepository;
  private final TransactionOperations reloadTransaction;
  private final Duration reloadWaitTimeout;
  private final ReloadFailureBackoff failureBackoff;
  private final ReentrantLock reloadLock = new ReentrantLock();

  private final AtomicReference<State> stateRef = new AtomicReference<>(State.initial());

  /** Springが用いるコンストラクター。再読み込みは、独立した読み取り専用トランザクション(REQUIRES_NEW・REPEATABLE_READ)で行う。 */
  @Autowired
  public ConfigCache(
      TableConfigRepository tableConfigRepository,
      ColumnConfigRepository columnConfigRepository,
      TranslationEntryRepository translationEntryRepository,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager,
      @Value("${mastersmith.config.cache.reload-wait-timeout:5s}") Duration reloadWaitTimeout,
      @Value("${mastersmith.config.cache.reload-failure-backoff:2s}")
          Duration reloadFailureBackoff) {
    this(
        tableConfigRepository,
        columnConfigRepository,
        translationEntryRepository,
        readOnlyRequiresNew(transactionManager),
        reloadWaitTimeout,
        new ReloadFailureBackoff(reloadFailureBackoff));
  }

  /** トランザクションなし・既定の待ち上限・抑制の期間を用いるコンストラクター(リポジトリをモックする既存の単体テスト用)。 */
  public ConfigCache(
      TableConfigRepository tableConfigRepository,
      ColumnConfigRepository columnConfigRepository,
      TranslationEntryRepository translationEntryRepository) {
    this(
        tableConfigRepository,
        columnConfigRepository,
        translationEntryRepository,
        TransactionOperations.withoutTransaction(),
        DEFAULT_RELOAD_WAIT_TIMEOUT,
        new ReloadFailureBackoff(DEFAULT_RELOAD_FAILURE_BACKOFF));
  }

  /** すべての協調オブジェクトを指定するコンストラクター(世代管理・失敗の抑制のテスト用)。 */
  public ConfigCache(
      TableConfigRepository tableConfigRepository,
      ColumnConfigRepository columnConfigRepository,
      TranslationEntryRepository translationEntryRepository,
      TransactionOperations reloadTransaction,
      Duration reloadWaitTimeout,
      ReloadFailureBackoff failureBackoff) {
    this.tableConfigRepository = tableConfigRepository;
    this.columnConfigRepository = columnConfigRepository;
    this.translationEntryRepository = translationEntryRepository;
    this.reloadTransaction = reloadTransaction;
    this.reloadWaitTimeout = reloadWaitTimeout;
    this.failureBackoff = failureBackoff;
  }

  private static TransactionOperations readOnlyRequiresNew(
      PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setReadOnly(true);
    return template;
  }

  /**
   * 個別の更新(書き込み)の反映: 内部設定DBの全件を読み込み、スナップショットを原子的に差し替える。{@code
   * STALE}のとき、および、読み込みの最中に世代が進んだ(別の取り込み・更新が起きた)ときは、 差し替えず、{@link
   * #invalidate()}を呼ぶ(次の読み取りが、確定済みの内容を再読み込みする)。
   */
  public void reload() {
    State state = stateRef.get();
    if (state.status() == Status.STALE) {
      invalidate();
      return;
    }
    Snapshot fresh = loadSnapshot();
    if (!stateRef.compareAndSet(state, new State(Status.VALID, state.generation(), fresh))) {
      invalidate();
    }
  }

  /** キャッシュを無効にする(世代番号を進め、{@code STALE}にする)。メモリ上の値の置き換えだけで、DBにも他のキャッシュにも触れず、失敗しえない。取り込みの確定後に呼ばれる。 */
  public void invalidate() {
    stateRef.updateAndGet(
        state -> new State(Status.STALE, state.generation() + 1, state.snapshot()));
  }

  /** 現在の世代番号(テスト・観測用)。 */
  public long generation() {
    return stateRef.get().generation();
  }

  /** {@code STALE}か(テスト・観測用)。 */
  public boolean isStale() {
    return stateRef.get().status() == Status.STALE;
  }

  private Snapshot loadSnapshot() {
    List<TableConfig> tableConfigs = tableConfigRepository.findAll();
    List<ColumnConfig> columnConfigs = columnConfigRepository.findAll();
    List<TranslationEntry> translationEntries = translationEntryRepository.findAll();
    return Snapshot.of(tableConfigs, columnConfigs, translationEntries);
  }

  /** {@code STALE}の間の読み取り: 待ちの上限つきの排他・二重の確認・失敗の抑制・独立した読み取り専用トランザクションでの再読み込み・世代の確認。 */
  private Snapshot reloadStale() {
    boolean locked;
    try {
      locked = reloadLock.tryLock(reloadWaitTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CacheReloadUnavailableException("config cache reload interrupted");
    }
    if (!locked) {
      throw new CacheReloadUnavailableException("config cache reload wait timeout");
    }
    try {
      State state = stateRef.get();
      if (state.status() == Status.VALID) {
        return state.snapshot();
      }
      if (failureBackoff.isSuppressing()) {
        throw new CacheReloadUnavailableException("config cache reload suppressed after a failure");
      }
      Snapshot fresh;
      try {
        fresh = reloadTransaction.execute(status -> loadSnapshot());
      } catch (RuntimeException e) {
        failureBackoff.recordFailure();
        LOG.error("event=config.cache.reload-failed cause={}", e.getClass().getName());
        throw new CacheReloadUnavailableException("config cache reload failed", e);
      }
      failureBackoff.recordSuccess();
      // 読み込みの開始時から世代が進んでいた(invalidateが呼ばれた)場合は、CASが失敗し、STALEのまま残る。
      stateRef.compareAndSet(state, new State(Status.VALID, state.generation(), fresh));
      return fresh;
    } finally {
      reloadLock.unlock();
    }
  }

  public Optional<TableConfig> findTableConfig(String schemaName, String tableName) {
    return Optional.ofNullable(
        snapshot().byNameKey().get(new TableConfigKey(schemaName, tableName)));
  }

  public Optional<TableConfig> findTableConfigById(String tableConfigId) {
    return Optional.ofNullable(snapshot().byId().get(tableConfigId));
  }

  public List<ColumnConfig> findColumnConfigs(String tableConfigId) {
    return snapshot().columnConfigsByTable().getOrDefault(tableConfigId, List.of());
  }

  public Optional<ColumnConfig> findColumnConfigById(String columnConfigId) {
    return Optional.ofNullable(snapshot().columnConfigById().get(columnConfigId));
  }

  public Optional<String> findTranslation(String i18nKey, String locale) {
    return Optional.ofNullable(snapshot().translations().get(new TranslationKey(i18nKey, locale)));
  }

  public List<TableConfig> allTableConfigs() {
    return snapshot().tableConfigList();
  }

  public List<ColumnConfig> allColumnConfigs() {
    return snapshot().columnConfigList();
  }

  public List<TranslationEntry> allTranslationEntries() {
    return snapshot().translationEntryList();
  }

  /** 現在のスナップショット。{@code STALE}の間は、待ちの上限つきの排他で、内部設定DBから再読み込みする。 */
  private Snapshot snapshot() {
    State state = stateRef.get();
    if (state.status() == Status.VALID) {
      return state.snapshot();
    }
    return reloadStale();
  }

  private enum Status {
    VALID,
    STALE
  }

  /** キャッシュの状態・世代番号・スナップショットを、1つにまとめた不変な値(compare-and-setで置き換える。R-12)。 */
  private record State(Status status, long generation, Snapshot snapshot) {
    static State initial() {
      return new State(Status.VALID, 0L, Snapshot.empty());
    }
  }

  private record TableConfigKey(String schemaName, String tableName) {}

  private record TranslationKey(String i18nKey, String locale) {}

  private record Snapshot(
      Map<TableConfigKey, TableConfig> byNameKey,
      Map<String, TableConfig> byId,
      Map<String, List<ColumnConfig>> columnConfigsByTable,
      Map<String, ColumnConfig> columnConfigById,
      Map<TranslationKey, String> translations,
      List<TableConfig> tableConfigList,
      List<ColumnConfig> columnConfigList,
      List<TranslationEntry> translationEntryList) {

    static Snapshot empty() {
      return new Snapshot(
          Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of());
    }

    static Snapshot of(
        List<TableConfig> tableConfigs,
        List<ColumnConfig> columnConfigs,
        List<TranslationEntry> translationEntries) {
      Map<TableConfigKey, TableConfig> byNameKey =
          tableConfigs.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      tc -> new TableConfigKey(tc.getSchemaName(), tc.getTableName()),
                      tc -> tc,
                      (a, b) -> a));
      Map<String, TableConfig> byId =
          tableConfigs.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      TableConfig::getTableConfigId, tc -> tc, (a, b) -> a));
      Map<String, List<ColumnConfig>> columnConfigsByTable =
          Map.copyOf(
              columnConfigs.stream()
                  .collect(
                      Collectors.groupingBy(
                          ColumnConfig::getTableConfigId, Collectors.toUnmodifiableList())));
      Map<String, ColumnConfig> columnConfigById =
          columnConfigs.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      ColumnConfig::getColumnConfigId, cc -> cc, (a, b) -> a));
      Map<TranslationKey, String> translations =
          translationEntries.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      te -> new TranslationKey(te.getI18nKey(), te.getLocale()),
                      TranslationEntry::getText,
                      (a, b) -> a));
      return new Snapshot(
          byNameKey,
          byId,
          columnConfigsByTable,
          columnConfigById,
          translations,
          List.copyOf(tableConfigs),
          List.copyOf(columnConfigs),
          List.copyOf(translationEntries));
    }
  }
}
