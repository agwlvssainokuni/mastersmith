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

package com.mastersmith.common.cache;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * キャッシュの再読み込み(ロード)の失敗の抑制(config-import-export
 * nfr-design/reliability-design.md「各ユニットのキャッシュの共通の契約」、レビュー指摘R-14)。
 *
 * <p>失敗の後、抑制の期間の間は、後続の読み取りが、再読み込みを試みず、すぐに失敗を返す(再読み込みを間引く)。これにより、内部設定DBが不通の間、待っているスレッドが、直列に1つずつ、接続・ロックのタイムアウトまで待つ連鎖を防ぐ。
 * 期間が過ぎたら、次の1つのスレッドが再試行する。時刻は、単調増加の{@link System#nanoTime()}を用いる(テストでは、差し替えられる)。
 */
public final class ReloadFailureBackoff {

  private final long backoffNanos;
  private final LongSupplier nanoClock;
  private volatile boolean failed;
  private volatile long failedAtNanos;

  public ReloadFailureBackoff(Duration backoff) {
    this(backoff, System::nanoTime);
  }

  public ReloadFailureBackoff(Duration backoff, LongSupplier nanoClock) {
    this.backoffNanos = backoff.toNanos();
    this.nanoClock = nanoClock;
  }

  /** 失敗を記録する(この時点から、抑制の期間が始まる)。 */
  public void recordFailure() {
    failedAtNanos = nanoClock.getAsLong();
    failed = true;
  }

  /** 成功を記録する(抑制を解く)。 */
  public void recordSuccess() {
    failed = false;
  }

  /** 抑制の期間の中か(すなわち、再読み込みを試みず、すぐに失敗を返すべきか)。 */
  public boolean isSuppressing() {
    return failed && nanoClock.getAsLong() - failedAtNanos < backoffNanos;
  }
}
