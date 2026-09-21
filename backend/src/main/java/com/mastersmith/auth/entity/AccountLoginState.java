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

package com.mastersmith.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * activeなユーザーごとの、連続ログイン失敗の回数とロックの状態(FR2.7、entities.md AccountLoginState)。ユーザーごとに1行のカウンタで、
 * 更新は、予約型の原子的な条件付きの更新 ({@code
 * AccountLoginStateRepository}、BR5.3)だけで行う。このエンティティは、行の読み取りと、Flywayのスクリプトとのマッピングの一致の検証 ({@code
 * ddl-auto: validate})のために持つ。
 */
@Entity
@Table(name = "account_login_state")
public class AccountLoginState {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private String userId;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "generation", nullable = false)
  private long generation;

  protected AccountLoginState() {
    // JPA用
  }

  public String getUserId() {
    return userId;
  }

  public int getConsecutiveFailures() {
    return consecutiveFailures;
  }

  /** ロックの解除予定日時。ロックされていなければnull。現在時刻がこの日時より前ならロック中。 */
  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public long getGeneration() {
    return generation;
  }
}
