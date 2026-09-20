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

package com.mastersmith.usermanagement.repository;

import com.mastersmith.usermanagement.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;

/** {@link UserRepositoryCustom}の実装。ロック待ちのタイムアウトを、設定値からヒントとして与える。 */
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

  /** 設定がない場合の既定値(NFR4.2: 15秒)。スライスのテストでも設定なしで動くよう、既定値を持つ。 */
  private static final String DEFAULT_LOCK_TIMEOUT = "15s";

  @PersistenceContext private EntityManager entityManager;

  private final Duration lockTimeout;

  public UserRepositoryCustomImpl(
      @Value("${mastersmith.users.db-lock-timeout:" + DEFAULT_LOCK_TIMEOUT + "}")
          Duration lockTimeout) {
    this.lockTimeout = lockTimeout;
  }

  @Override
  public Optional<User> findByIdForUpdate(String userId) {
    User user =
        entityManager.find(
            User.class,
            userId,
            LockModeType.PESSIMISTIC_WRITE,
            Map.of("jakarta.persistence.lock.timeout", (int) lockTimeout.toMillis()));
    return Optional.ofNullable(user);
  }
}
