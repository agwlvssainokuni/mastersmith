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

package com.mastersmith.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * authentication-serviceの設定値({@code
 * mastersmith.auth.*})。トークンの有効期限・ロックのしきい値と時間・再送の猶予・Sessionの削除・キャッシュは、 コードに埋め込まず、ここから与える
 * (NFR8.1、BR5.4)。既定値は、NFR Requirements・NFR Designで確定した値である。
 *
 * <p><b>JWTの署名の鍵は、この設定の束縛の対象にしない</b>。Spring Bootの束縛の失敗の診断は、拒否された値を表示するため、鍵は{@link
 * com.mastersmith.auth.token.JwtKeyProvider}が{@code Environment}から直接読む(環境変数{@code
 * MASTERSMITH_AUTH_JWT_SECRET}、 キー{@code mastersmith.auth.jwt.secret})。
 *
 * <p>不備(0以下の有効期限・しきい値・ロック時間、アクセストークンの有効期限がリフレッシュトークンの有効期限を超えること、など)は、起動時に失敗させる (project.md
 * Mandated、BR5.4・NFR4.4)。エラーのメッセージには、設定のキーの名前と理由だけを含める。
 *
 * @param accessTokenTtl アクセストークンの有効期限(FR3.1、既定10分)
 * @param refreshTokenTtl リフレッシュトークンの有効期限(FR3.1、既定30分)。最後の発行から、この時間後まで有効
 * @param refreshReuseGrace 更新済みのリフレッシュトークンの再送を許す猶予(BR5.6、既定10秒。0で猶予なし)
 */
@ConfigurationProperties("mastersmith.auth")
public record AuthProperties(
    @DefaultValue("10m") Duration accessTokenTtl,
    @DefaultValue("30m") Duration refreshTokenTtl,
    @DefaultValue("10s") Duration refreshReuseGrace,
    @DefaultValue Lock lock,
    @DefaultValue SessionSettings session,
    @DefaultValue CacheSettings cache) {

  private static final String PREFIX = "mastersmith.auth.";

  public AuthProperties {
    requirePositive(accessTokenTtl, "access-token-ttl");
    requirePositive(refreshTokenTtl, "refresh-token-ttl");
    if (accessTokenTtl.compareTo(refreshTokenTtl) > 0) {
      // 超えると、アクセストークンが、Sessionの有効性(BR5.11)より長く有効になり、認証フィルタとC14の結果が食い違う。
      throw new IllegalArgumentException(
          PREFIX + "access-token-ttl must not exceed " + PREFIX + "refresh-token-ttl");
    }
    if (refreshReuseGrace.isNegative()) {
      throw new IllegalArgumentException(PREFIX + "refresh-reuse-grace must not be negative");
    }
  }

  /**
   * ログイン失敗のロック(FR2.7)。
   *
   * @param threshold 連続失敗のしきい値(既定5回)。この回数に達する試行の枠の確保と同時に、ロックが有効になる
   * @param duration ロック時間(既定15分)
   */
  public record Lock(@DefaultValue("5") int threshold, @DefaultValue("15m") Duration duration) {

    public Lock {
      if (threshold < 1) {
        throw new IllegalArgumentException(PREFIX + "lock.threshold must be at least 1");
      }
      requirePositive(duration, "lock.duration");
    }
  }

  /**
   * Sessionの定期削除(NFR4.5)と、起動時の全Sessionの失効(NFR2.5)。
   *
   * @param retention 有効期限から、削除するまでの保持日数(既定7日)
   * @param cleanupInterval 定期削除の実行間隔(既定1日。前回の終了から)
   * @param cleanupInitialDelay 最初の定期削除までの遅延(既定10分。起動直後の負荷を避ける)
   * @param cleanupBatchSize 1回のトランザクションで削除する行数の上限(既定1,000行)
   * @param cleanupMaxBatches 1回の実行での、バッチ数の上限(既定100バッチ)
   * @param revokeAllOnStartup trueなら、起動時に、すべてのSessionを削除する(既定false)
   */
  public record SessionSettings(
      @DefaultValue("7d") Duration retention,
      @DefaultValue("1d") Duration cleanupInterval,
      @DefaultValue("10m") Duration cleanupInitialDelay,
      @DefaultValue("1000") int cleanupBatchSize,
      @DefaultValue("100") int cleanupMaxBatches,
      @DefaultValue("false") boolean revokeAllOnStartup) {

    public SessionSettings {
      if (retention.isNegative()) {
        throw new IllegalArgumentException(PREFIX + "session.retention must not be negative");
      }
      requirePositive(cleanupInterval, "session.cleanup-interval");
      if (cleanupInitialDelay.isNegative()) {
        throw new IllegalArgumentException(
            PREFIX + "session.cleanup-initial-delay must not be negative");
      }
      if (cleanupBatchSize < 1) {
        throw new IllegalArgumentException(
            PREFIX + "session.cleanup-batch-size must be at least 1");
      }
      if (cleanupMaxBatches < 1) {
        throw new IllegalArgumentException(
            PREFIX + "session.cleanup-max-batches must be at least 1");
      }
    }
  }

  /**
   * Sessionのキャッシュ(NFR3.2)。
   *
   * @param maxSize 最大件数(既定1,000件)
   * @param ttl 書き込みからの有効期間(既定60秒、安全網)
   */
  public record CacheSettings(
      @DefaultValue("1000") long maxSize, @DefaultValue("60s") Duration ttl) {

    public CacheSettings {
      if (maxSize < 1) {
        throw new IllegalArgumentException(PREFIX + "cache.max-size must be at least 1");
      }
      // TTLが0以下だと、キャッシュが働かず、NFR1.2のヒット時の目標が意味を失う。
      requirePositive(ttl, "cache.ttl");
    }
  }

  private static void requirePositive(Duration value, String key) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(PREFIX + key + " must be positive");
    }
  }
}
