package com.mastersmith.auth.service;

import com.mastersmith.auth.config.AuthProperties;
import com.mastersmith.auth.entity.AccountLoginState;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.repository.AccountLoginStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン失敗のロックの、予約型の更新(BR5.3、reliability-design.md NFR4.1)。「先に試行の枠を確保してから検証する」ことで、しきい値を超える同時の試行や、
 * ロックの解除後の再ロックの取りこぼしがないようにする。
 *
 * <p>トランザクションには、<b>参加するだけ</b>である({@code MANDATORY}。トランザクションの境界は{@link
 * AuthenticationApplicationService}が所有する)。トランザクションの外から呼ぶと、例外になる(誤用の防止)。時刻は、注入された{@link
 * Clock}の値を、パラメータとして渡す(NFR4.6)。ロック時間の加算も、SQLの中では行わず、Java側で計算する。
 *
 * <p>行の初回作成で、同時の作成により主キーの一意制約に違反した場合の{@link
 * org.springframework.dao.DataIntegrityViolationException}は、この部品ではやり直さない(参加中のトランザクションを、rollback-onlyにしうるため)。
 * {@link AuthenticationApplicationService}が、トランザクションの外で捕捉して、新しいトランザクションで、やり直す。
 */
@Component
public class LoginAttemptGate {

  /** 予約の更新が0件で、行を読み直した結果が、他のトランザクションの更新で変わっていた場合の、やり直しの上限。 */
  private static final int MAX_LOCAL_ATTEMPTS = 3;

  /**
   * 試行の予約(reservation)。確保した時点の世代と、この確保がロックを設定した場合の{@code lockedUntil}を保持する(補償の更新の条件)。
   *
   * @param acquired 枠を確保できたか(falseは、ロック中・想定外の状態で、検証せず、失敗回数も数えず、ロック期間も延長しない)
   * @param generation 確保した時点の世代
   * @param lockedUntilSetByThis この確保が、しきい値に達してロックを設定した場合の{@code lockedUntil}。設定しなかった場合はnull
   */
  public record Reservation(boolean acquired, long generation, Instant lockedUntilSetByThis) {

    /** 枠を確保できなかった(ロック中など)。 */
    public static final Reservation NOT_ACQUIRED = new Reservation(false, 0L, null);
  }

  private final AccountLoginStateRepository repository;
  private final AuthProperties properties;
  private final Clock clock;
  private final AuthMetrics metrics;
  private final AuthEventLogger logger;

  public LoginAttemptGate(
      AccountLoginStateRepository repository,
      AuthProperties properties,
      Clock clock,
      AuthMetrics metrics,
      AuthEventLogger logger) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
    this.metrics = metrics;
    this.logger = logger;
  }

  /**
   * 試行の枠を確保する(予約)。ロック中(未来の{@code lockedUntil})なら確保しない(検証せず、数えず、ロック期間も延長しない)。ロックの解除済みなら、回数を0に戻し、
   * 世代を進めてから数える。回数がしきい値に達する確保は、同じ更新の中で{@code lockedUntil}も設定する。しきい値以上で{@code lockedUntil}が空の、想定外の状態は、
   * {@code lockedUntil}を設定して確保しない(自己修復。永続的にロックされたままにならない)。
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Reservation reserve(String userId) {
    int threshold = properties.lock().threshold();
    for (int attempt = 0; attempt < MAX_LOCAL_ATTEMPTS; attempt++) {
      Instant now = clock.instant();
      Instant newLockedUntil = now.plus(properties.lock().duration());
      if (repository.reserve(userId, now, newLockedUntil, threshold) == 1) {
        return acquired(userId);
      }
      Optional<AccountLoginState> found = repository.findById(userId);
      if (found.isEmpty()) {
        // 行がない: 最初の試行の枠の確保として、行を作る(同時の作成による一意制約の違反は、呼び出し側がやり直す)。
        if (threshold <= 1) {
          repository.insertLocked(userId, newLockedUntil);
        } else {
          repository.insertCounted(userId);
        }
        return acquired(userId);
      }
      AccountLoginState state = found.get();
      Instant lockedUntil = state.getLockedUntil();
      if (lockedUntil != null && lockedUntil.isAfter(now)) {
        return Reservation.NOT_ACQUIRED; // ロック中
      }
      if (lockedUntil == null && state.getConsecutiveFailures() >= threshold) {
        repository.repair(userId, newLockedUntil, threshold); // 想定外の状態の自己修復
        return Reservation.NOT_ACQUIRED;
      }
      // 上記のいずれでもない: 更新の後、行を読むまでの間に、他のトランザクションが状態を変えた。やり直す。
    }
    return Reservation.NOT_ACQUIRED; // 安全側
  }

  /**
   * 条件付きの補償の更新(ハッシュ計算の上限超過・内部設定DBの障害で、確保した枠を返す)。世代が予約の時点と同じで、回数が0より大きい場合に限り、回数を1戻し、
   * {@code lockedUntil}が、この予約が設定した値と一致する場合に限り、ロックを解く。条件を満たさない場合(別の試行の成功によるリセット、ロックの解除後の新しい世代の
   * 予約など)は、何もしない(エラーにしない)。
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void compensate(String userId, Reservation reservation) {
    if (!reservation.acquired()) {
      return;
    }
    if (reservation.lockedUntilSetByThis() != null) {
      repository.compensateWithLock(
          userId, reservation.generation(), reservation.lockedUntilSetByThis());
    } else {
      repository.compensateCount(userId, reservation.generation());
    }
  }

  /** ログイン成功のリセット(回数0、ロックの解除、世代+1)。しきい値に達した試行であっても、正しいパスワードでの成功は、ロックを解く。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void succeed(String userId) {
    repository.reset(userId);
  }

  private Reservation acquired(String userId) {
    // 自分の更新の結果を読む(更新の行のロックは、トランザクションの終わりまで保持されるため、他の試行の更新を含まない)。
    AccountLoginState state = repository.findById(userId).orElseThrow();
    Instant lockedUntil = state.getLockedUntil();
    if (lockedUntil != null) {
      // 確保の後にlocked_untilが非nullなら、この確保が設定した値である(確保できる状態は、ロックなしか、解除済みのみ)。
      metrics.accountLocked();
      logger.accountLocked(userId, lockedUntil);
    }
    return new Reservation(true, state.getGeneration(), lockedUntil);
  }
}
