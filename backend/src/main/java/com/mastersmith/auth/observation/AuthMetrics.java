package com.mastersmith.auth.observation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * authentication-serviceのメトリクスの窓口(nfr-design/observability-design.md NFR5.1)。Micrometerの{@link MeterRegistry}に、次のメーターを登録する。
 *
 * <p><b>タグ・ラベルに、個人を特定しうる値(メールアドレス・氏名・userId・sessionId)、トークン、鍵を含めない</b>(NFR2.7)。タグは、値が固定された
 * 列挙({@link UnauthorizedReason})・{@code result}({@code hit}・{@code miss})だけである。ログイン失敗は、原因を区別せず数える(BR5.14)。
 *
 * <p>メーターの名前は、Micrometerの規約(ドット区切り)で登録する。エクスポートの形式(Prometheus形式では{@code _total}が付く、など)は、共通基盤の設定に従う。
 */
@Component
public class AuthMetrics {

  private final Counter loginFailed;
  private final Counter accountLocked;
  private final Counter refreshReuseDetected;
  private final Counter refreshReuseWithinGrace;
  private final Counter hashCapacityExceeded;
  private final Counter dbUnavailable;
  private final Map<UnauthorizedReason, Counter> filterUnauthorized =
      new EnumMap<>(UnauthorizedReason.class);
  private final Timer loginDuration;
  private final Timer filterDurationHit;
  private final Timer filterDurationMiss;

  public AuthMetrics(MeterRegistry registry) {
    this.loginFailed =
        Counter.builder("auth.login.failed")
            .description("ログインが401で終了した回数(原因を区別しない)")
            .register(registry);
    this.accountLocked =
        Counter.builder("auth.account.locked")
            .description("予約の更新が、しきい値に達してロックを設定した回数")
            .register(registry);
    this.refreshReuseDetected =
        Counter.builder("auth.refresh.token.reuse.detected")
            .description("猶予を超えた無効なリフレッシュトークンの再使用を検知して、Sessionを失効させた回数")
            .register(registry);
    this.refreshReuseWithinGrace =
        Counter.builder("auth.refresh.reuse.within.grace")
            .description("猶予内の再使用を、401だけで、Sessionを失効させずに返した回数")
            .register(registry);
    this.hashCapacityExceeded =
        Counter.builder("auth.login.hash.capacity.exceeded")
            .description("ログインが、ハッシュ計算の待機超過で503になった回数(実際・ダミーの検証のどちらも)")
            .register(registry);
    this.dbUnavailable =
        Counter.builder("auth.db.unavailable")
            .description("内部設定DBの障害で、503を返した回数")
            .register(registry);
    for (UnauthorizedReason reason : UnauthorizedReason.values()) {
      filterUnauthorized.put(
          reason,
          Counter.builder("auth.filter.unauthorized")
              .description("認証フィルタが401を返した回数")
              .tag("reason", reason.tag())
              .register(registry));
    }
    this.loginDuration =
        Timer.builder("auth.login.duration")
            .description("ログインの所要時間(NFR1.1の確認用)")
            .publishPercentileHistogram()
            .register(registry);
    this.filterDurationHit =
        Timer.builder("auth.filter.duration")
            .description("認証フィルタの処理時間(NFR1.2の確認用)")
            .tag("result", "hit")
            .publishPercentileHistogram()
            .register(registry);
    this.filterDurationMiss =
        Timer.builder("auth.filter.duration")
            .description("認証フィルタの処理時間(NFR1.2の確認用)")
            .tag("result", "miss")
            .publishPercentileHistogram()
            .register(registry);
  }

  public void loginFailed() {
    loginFailed.increment();
  }

  public void accountLocked() {
    accountLocked.increment();
  }

  public void refreshReuseDetected() {
    refreshReuseDetected.increment();
  }

  public void refreshReuseWithinGrace() {
    refreshReuseWithinGrace.increment();
  }

  public void hashCapacityExceeded() {
    hashCapacityExceeded.increment();
  }

  public void dbUnavailable() {
    dbUnavailable.increment();
  }

  public void filterUnauthorized(UnauthorizedReason reason) {
    filterUnauthorized.get(reason).increment();
  }

  /** ログインの所要時間。 */
  public Timer loginDuration() {
    return loginDuration;
  }

  /** 認証フィルタの処理時間({@code hit}: キャッシュのヒット、{@code miss}: DBからの読み込み)。 */
  public Timer filterDuration(boolean cacheHit) {
    return cacheHit ? filterDurationHit : filterDurationMiss;
  }
}
