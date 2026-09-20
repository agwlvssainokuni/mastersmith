package com.mastersmith.auth.service;

import com.mastersmith.auth.config.AuthProperties;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthObservations;
import com.mastersmith.auth.repository.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 期限切れ・失効したSessionの定期削除(NFR4.5、reliability-design.md)。
 *
 * <ul>
 *   <li><b>実行</b>: {@code @Scheduled(fixedDelay)}(前回の終了から、設定の実行間隔ごと)。1回目は、設定の初回の遅延(既定10分)の後。実行中は、次の実行を始めない。
 *   <li><b>削除の対象</b>: {@code refresh_expires_at}が、{@code 現在時刻 − 保持日数}より前のSession(statusを問わない)。有効なSessionは、この条件に該当しない。
 *   <li><b>1回のトランザクションの行数の上限</b>: 1,000行(SELECTとDELETEを1つのトランザクションで行う。データベースの方言に依存しないため)。1,000行未満に
 *       なるまで、トランザクションを分けて繰り返す。1回の実行での上限は100バッチ(10万行)で、残りは次の実行で削除する。
 *   <li><b>失敗の扱い</b>: 例外は捕捉し、ERRORログ(例外の型と、それまでに削除した行数)に記録して、その回の実行を終える。認証のリクエストの処理には影響させない。
 * </ul>
 *
 * <p>削除の対象は、すでに有効でないSessionだけのため、認証の処理と、同じ行を書き換える競合は起きない。キャッシュの無効化は要らない。
 */
@Component
public class SessionCleanupJob {

  private final SessionRepository repository;
  private final AuthProperties properties;
  private final Clock clock;
  private final AuthEventLogger logger;
  private final AuthObservations observations;
  private final TransactionTemplate tx;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public SessionCleanupJob(
      SessionRepository repository,
      AuthProperties properties,
      Clock clock,
      AuthEventLogger logger,
      AuthObservations observations,
      @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
    this.logger = logger;
    this.observations = observations;
    this.tx = new TransactionTemplate(transactionManager);
  }

  /** スケジューラから呼ばれる入口。例外を外へ出さない(次の実行で、再試行する)。 */
  @Scheduled(
      fixedDelayString = "${mastersmith.auth.session.cleanup-interval:1d}",
      initialDelayString = "${mastersmith.auth.session.cleanup-initial-delay:10m}")
  public void scheduledRun() {
    runOnce();
  }

  /**
   * 1回の実行。実行中に再び呼ばれた場合は、何もしない。
   *
   * @return この実行で削除した行数(失敗した場合は、失敗までに削除した行数)
   */
  public long runOnce() {
    if (!running.compareAndSet(false, true)) {
      return 0L;
    }
    try {
      long[] deleted = {0L};
      observations.run("auth.session.cleanup", () -> deleted[0] = cleanup());
      return deleted[0];
    } finally {
      running.set(false);
    }
  }

  private long cleanup() {
    long startedAt = System.nanoTime();
    long deleted = 0L;
    try {
      Instant cutoff = clock.instant().minus(properties.session().retention());
      int batchSize = properties.session().cleanupBatchSize();
      for (int batch = 0; batch < properties.session().cleanupMaxBatches(); batch++) {
        Integer rows = tx.execute(status -> deleteBatch(cutoff, batchSize));
        int count = rows == null ? 0 : rows;
        deleted += count;
        if (count < batchSize) {
          break;
        }
      }
      logger.cleanupCompleted(deleted, (System.nanoTime() - startedAt) / 1_000_000L);
    } catch (RuntimeException e) {
      logger.cleanupFailed(deleted, e);
    }
    return deleted;
  }

  private int deleteBatch(Instant cutoff, int batchSize) {
    List<String> ids = repository.findExpiredIds(cutoff, PageRequest.ofSize(batchSize));
    if (ids.isEmpty()) {
      return 0;
    }
    return repository.deleteExpiredByIds(ids, cutoff);
  }
}
