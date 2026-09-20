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

package com.mastersmith.usermanagement.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * user-managementの設定値({@code mastersmith.users.*})。ハッシュのパラメータ・同時実行数・タイムアウトはコードに埋め込まず、ここから与える
 * (NFR8.1)。既定値は、設計(nfr-design)で確定した値である。初期管理者の設定({@code mastersmith.users.initial-admin.*})は、別のクラスで
 * 束ねる(NFR2.11)。
 *
 * @param dbLockTimeout User行のロック待ちの最大時間(NFR4.2)。{@code UserRepositoryCustomImpl}は同じ設定名を直接読む
 */
@ConfigurationProperties("mastersmith.users")
public record UserManagementProperties(
    @DefaultValue Hash hash,
    @DefaultValue Invitation invitation,
    @DefaultValue("15s") Duration dbLockTimeout) {

  /**
   * Argon2idのパラメータと、同時計算の制御(NFR1.2・NFR1.3)。
   *
   * @param memoryKib メモリ(KiB)。19456KiBは約19MiB
   * @param iterations 反復回数
   * @param parallelism 並列度
   * @param saltLength ソルトの長さ(バイト)
   * @param hashLength ハッシュの長さ(バイト)
   * @param maxConcurrent 同時に実行できるハッシュ計算の数。0以下ならCPUコア数
   * @param waitTimeout ハッシュ計算の許可を待つ最大時間
   */
  public record Hash(
      @DefaultValue("19456") int memoryKib,
      @DefaultValue("2") int iterations,
      @DefaultValue("1") int parallelism,
      @DefaultValue("16") int saltLength,
      @DefaultValue("32") int hashLength,
      @DefaultValue("0") int maxConcurrent,
      @DefaultValue("2s") Duration waitTimeout) {

    public Hash {
      if (memoryKib < 8 || iterations < 1 || parallelism < 1 || saltLength < 8 || hashLength < 4) {
        throw new IllegalArgumentException(
            "mastersmith.users.hash: invalid Argon2 parameters (memory-kib>=8, iterations>=1,"
                + " parallelism>=1, salt-length>=8, hash-length>=4)");
      }
      if (waitTimeout.isNegative()) {
        throw new IllegalArgumentException(
            "mastersmith.users.hash.wait-timeout must not be negative");
      }
    }

    /** 同時実行数。0以下ならCPUコア数(NFR1.3)。 */
    public int effectiveMaxConcurrent() {
      return maxConcurrent > 0 ? maxConcurrent : Runtime.getRuntime().availableProcessors();
    }
  }

  /**
   * 招待の同時実行数・排他・メール送信(NFR1.4・NFR4.2)。
   *
   * @param maxConcurrent 招待の同時実行数(SMTP応答待ちの間、内部設定DBの接続を保持する招待の上限)
   * @param emailLockWait 同一emailの排他を待つ最大時間
   * @param mailPoolSize 招待メール送信の専用プールのスレッド数
   * @param mailTimeout 呼び出し側が招待メールの送信結果を待つ最大時間
   */
  public record Invitation(
      @DefaultValue("5") int maxConcurrent,
      @DefaultValue("12s") Duration emailLockWait,
      @DefaultValue("5") int mailPoolSize,
      @DefaultValue("10s") Duration mailTimeout) {

    public Invitation {
      if (maxConcurrent < 1 || mailPoolSize < 1) {
        throw new IllegalArgumentException(
            "mastersmith.users.invitation: max-concurrent and mail-pool-size must be >= 1");
      }
      if (emailLockWait.isNegative() || mailTimeout.isNegative() || mailTimeout.isZero()) {
        throw new IllegalArgumentException(
            "mastersmith.users.invitation: invalid email-lock-wait or mail-timeout");
      }
    }
  }
}
