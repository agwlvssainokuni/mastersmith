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

package com.mastersmith.usermanagement.security;

import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.observation.UserObservations;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2idのパスワードのハッシュ計算・検証・パラメータの比較を集約する部品(performance-design.md NFR1.2、security-design.md NFR2.2)。
 * 他のクラスは{@link Argon2PasswordEncoder}を直接使わない。
 *
 * <p>ハッシュの計算・検証は、必ず{@link HashConcurrencyLimiter}の許可の中で行う(NFR1.3)。許可を保持している間はDB接続を取らないため、
 * 呼び出し側はトランザクションの外側で使うこと。平文のパスワードは、引数として受け取るだけで、フィールドに保持せず、ログにも出さない。
 *
 * <p>{@code needsUpgrade}は、{@link
 * Argon2PasswordEncoder#upgradeEncoding(String)}の判定(保存済みのメモリ・反復回数が、現在の設定より
 * 小さいか)に従う。並列度は比較しない(現在の設定より強いパラメータで保存されたハッシュは、更新の対象としない)。
 */
@Component
public class PasswordHasher {

  private static final String ARGON2ID_PREFIX = "$argon2id$";

  /**
   * 検証の結果。
   *
   * @param matches パスワードが一致したか
   * @param upgradedHash 一致し、かつ保存済みのハッシュが古いパラメータの場合に限り、現在のパラメータで作り直したハッシュ。それ以外はnull
   */
  public record VerifyResult(boolean matches, String upgradedHash) {}

  private final Argon2PasswordEncoder encoder;
  private final HashConcurrencyLimiter limiter;
  private final Timer durationTimer;

  /** 観測(スパン)。既定は何もしない。アプリケーションでは、{@link UserObservations}のBeanが注入される(NFR5.3)。 */
  private UserObservations observations = UserObservations.NOOP;

  @Autowired
  public void setObservations(UserObservations observations) {
    this.observations = observations;
  }

  @Autowired
  public PasswordHasher(
      UserManagementProperties properties,
      HashConcurrencyLimiter limiter,
      MeterRegistry meterRegistry) {
    this(properties.hash(), limiter, meterRegistry);
  }

  public PasswordHasher(
      UserManagementProperties.Hash params,
      HashConcurrencyLimiter limiter,
      MeterRegistry meterRegistry) {
    this.encoder =
        new Argon2PasswordEncoder(
            params.saltLength(),
            params.hashLength(),
            params.parallelism(),
            params.memoryKib(),
            params.iterations());
    this.limiter = limiter;
    this.durationTimer =
        Timer.builder("user.password.hash.duration")
            .description("ハッシュ計算・検証の所要時間(許可の待ち時間を含まない、NFR1.2の確認用)")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  /**
   * パスワードをArgon2idでハッシュ化する。呼び出し側が、長さ(8〜128文字)を検証済みである前提で、違反は防御的に拒否する。
   *
   * @throws IllegalArgumentException 長さが方針(8〜128文字)を満たさない場合
   * @throws com.mastersmith.usermanagement.HashCapacityExceededException 許可を待機の上限内に取れなかった場合
   */
  public String hash(String rawPassword) {
    return observations.observe("user.password.compute", "hash", () -> doHash(rawPassword));
  }

  private String doHash(String rawPassword) {
    requireValidLength(rawPassword);
    return limiter.runWithPermit(() -> timed(() -> encoder.encode(rawPassword)));
  }

  /**
   * パスワードを検証する。129文字以上は、許可を取らず、計算もせずにfalse。保存済みのハッシュがnull・空・不正な形式の場合もfalse。
   *
   * @throws com.mastersmith.usermanagement.HashCapacityExceededException 許可を待機の上限内に取れなかった場合
   */
  public boolean verify(String rawPassword, String storedHash) {
    return observations.observe(
        "user.password.compute", "verify", () -> doVerify(rawPassword, storedHash));
  }

  private boolean doVerify(String rawPassword, String storedHash) {
    if (!canVerify(rawPassword, storedHash)) {
      return false;
    }
    return limiter.runWithPermit(() -> timed(() -> encoder.matches(rawPassword, storedHash)));
  }

  /**
   * 検証し、成功して保存済みのハッシュが古いパラメータの場合は、同じ1回の許可の中で、現在のパラメータのハッシュを作り直す (ログイン成功時のハッシュ更新、security-design.md
   * NFR2.2、performance-design.md NFR1.3)。
   *
   * @throws com.mastersmith.usermanagement.HashCapacityExceededException 許可を待機の上限内に取れなかった場合
   */
  public VerifyResult verifyAndUpgrade(String rawPassword, String storedHash) {
    return observations.observe(
        "user.password.compute",
        "verify_and_upgrade",
        () -> doVerifyAndUpgrade(rawPassword, storedHash));
  }

  private VerifyResult doVerifyAndUpgrade(String rawPassword, String storedHash) {
    if (!canVerify(rawPassword, storedHash)) {
      return new VerifyResult(false, null);
    }
    return limiter.runWithPermit(
        () ->
            timed(
                () -> {
                  if (!encoder.matches(rawPassword, storedHash)) {
                    return new VerifyResult(false, null);
                  }
                  String upgraded = needsUpgrade(storedHash) ? encoder.encode(rawPassword) : null;
                  return new VerifyResult(true, upgraded);
                }));
  }

  /** 保存済みのハッシュのパラメータが、現在の設定より古いか(弱いか)。不正な形式は、更新の対象としない(false)。 */
  public boolean needsUpgrade(String storedHash) {
    if (storedHash == null || !storedHash.startsWith(ARGON2ID_PREFIX)) {
      return false;
    }
    try {
      return encoder.upgradeEncoding(storedHash);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static boolean canVerify(String rawPassword, String storedHash) {
    return rawPassword != null
        && !rawPassword.isEmpty()
        && storedHash != null
        && !storedHash.isEmpty()
        && !PasswordPolicy.exceedsMaxLength(rawPassword);
  }

  private static void requireValidLength(String rawPassword) {
    if (!PasswordPolicy.isValidLength(rawPassword)) {
      throw new IllegalArgumentException(
          "password length must be between %d and %d"
              .formatted(PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH));
    }
  }

  private <T> T timed(java.util.function.Supplier<T> computation) {
    return durationTimer.record(computation);
  }
}
