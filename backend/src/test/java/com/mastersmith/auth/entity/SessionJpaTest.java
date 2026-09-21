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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@link Session}のJPAのマッピングのテスト(実H2、Flywayのスクリプト{@code V5}との一致は{@code ddl-auto:
 * validate}が起動時に確認する): 往復の永続化・一意制約 ({@code refresh_token_hash}・{@code
 * previous_refresh_token_hash}、NULLの複数行を許す)・ミリ秒精度・値オブジェクトの性質。
 */
@DataJpaTest
class SessionJpaTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-03-04T05:06:07.123Z");

  @Autowired private SessionRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void aSessionRoundTripsWithAllItsFields() {
    String userId = AuthTestFactory.uniqueUserId();
    String roleId = AuthTestFactory.uniqueRoleId();
    String hash = AuthTestFactory.newHash();
    Session session =
        AuthTestFactory.session(userId, roleId, hash, ISSUED_AT, Duration.ofMinutes(30));

    repository.saveAndFlush(session);
    entityManager.clear();
    Session loaded = repository.findById(session.getSessionId()).orElseThrow();

    assertThat(loaded.getUserId()).isEqualTo(userId);
    assertThat(loaded.getActiveRoleId()).isEqualTo(roleId);
    assertThat(loaded.getRefreshTokenHash()).isEqualTo(hash);
    assertThat(loaded.getPreviousRefreshTokenHash()).isNull();
    assertThat(loaded.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    // ミリ秒精度(UTC)で、往復する。
    assertThat(loaded.getIssuedAt()).isEqualTo(ISSUED_AT);
    assertThat(loaded.getLastRefreshedAt()).isEqualTo(ISSUED_AT);
    assertThat(loaded.getRefreshExpiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(30)));
  }

  @Test
  void anUnselectedActiveRoleIsStoredAsNull() {
    Session session =
        AuthTestFactory.session(
            AuthTestFactory.uniqueUserId(),
            null,
            AuthTestFactory.newHash(),
            ISSUED_AT,
            Duration.ofMinutes(30));

    repository.saveAndFlush(session);
    entityManager.clear();

    assertThat(repository.findById(session.getSessionId()).orElseThrow().getActiveRoleId())
        .isNull();
  }

  @Test
  void theRefreshTokenHashMustBeUniqueAcrossSessions() {
    String hash = AuthTestFactory.newHash();
    repository.saveAndFlush(
        AuthTestFactory.session(
            AuthTestFactory.uniqueUserId(), null, hash, ISSUED_AT, Duration.ofMinutes(30)));

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(
                    AuthTestFactory.session(
                        AuthTestFactory.uniqueUserId(),
                        null,
                        hash,
                        ISSUED_AT,
                        Duration.ofMinutes(30))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void multipleSessionsMayHaveNoPreviousHashButAPreviousHashMustBeUnique() {
    // NULLは、一意制約の対象外(複数行が、直前のハッシュを持たない)。
    repository.saveAndFlush(
        AuthTestFactory.session(
            AuthTestFactory.uniqueUserId(),
            null,
            AuthTestFactory.newHash(),
            ISSUED_AT,
            Duration.ofMinutes(30)));
    repository.saveAndFlush(
        AuthTestFactory.session(
            AuthTestFactory.uniqueUserId(),
            null,
            AuthTestFactory.newHash(),
            ISSUED_AT,
            Duration.ofMinutes(30)));

    assertThat(repository.count()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void aSessionIsValidOnlyWhileActiveAndNotExpired() {
    Session session =
        AuthTestFactory.session(
            AuthTestFactory.uniqueUserId(),
            null,
            AuthTestFactory.newHash(),
            ISSUED_AT,
            Duration.ofMinutes(30));
    Instant expiry = ISSUED_AT.plus(Duration.ofMinutes(30));

    assertThat(session.isValidAt(ISSUED_AT)).isTrue();
    assertThat(session.isValidAt(expiry.minusMillis(1))).isTrue();
    // 有効期限の経過(ちょうどの時刻を含む)は、有効でない(BR5.11)。
    assertThat(session.isValidAt(expiry)).isFalse();
    assertThat(session.isValidAt(expiry.plusSeconds(1))).isFalse();
  }

  @Test
  void toStringNeverContainsTheRefreshTokenHashOrTheRole() {
    String hash = AuthTestFactory.newHash();
    Session session =
        AuthTestFactory.session(
            AuthTestFactory.uniqueUserId(), "secret-role", hash, ISSUED_AT, Duration.ofMinutes(30));

    assertThat(session.toString()).doesNotContain(hash).doesNotContain("secret-role");
  }
}
