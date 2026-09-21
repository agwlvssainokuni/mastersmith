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

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.auth.repository.AccountLoginStateRepository;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link AccountLoginState}のJPAのマッピングのテスト(実H2): 往復の永続化・{@code
 * account_login_state}の既定値(回数0・世代0・{@code locked_until}なし)・ミリ秒精度。Flywayのスクリプト{@code V5}との一致は、{@code
 * ddl-auto: validate}が起動時に確認する。
 */
@DataJpaTest
class AccountLoginStateJpaTest {

  @Autowired private AccountLoginStateRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void aRowInsertedWithOnlyTheUserIdGetsTheColumnDefaults() {
    String userId = AuthTestFactory.uniqueUserId();
    jdbcTemplate.update("INSERT INTO account_login_state (user_id) VALUES (?)", userId);

    AccountLoginState state = repository.findById(userId).orElseThrow();

    assertThat(state.getUserId()).isEqualTo(userId);
    assertThat(state.getConsecutiveFailures()).isZero();
    assertThat(state.getLockedUntil()).isNull();
    assertThat(state.getGeneration()).isZero();
  }

  @Test
  void theStateRoundTripsWithMillisecondPrecisionInUtc() {
    String userId = AuthTestFactory.uniqueUserId();
    Instant lockedUntil = Instant.parse("2026-03-04T05:06:07.123Z");

    repository.insertLocked(userId, lockedUntil);
    AccountLoginState state = repository.findById(userId).orElseThrow();

    assertThat(state.getConsecutiveFailures()).isEqualTo(1);
    assertThat(state.getLockedUntil()).isEqualTo(lockedUntil);
    assertThat(state.getGeneration()).isZero();
  }
}
