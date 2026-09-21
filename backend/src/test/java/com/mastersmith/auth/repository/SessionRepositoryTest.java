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

package com.mastersmith.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.entity.SessionStatus;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

/**
 * {@link SessionRepository}のテスト(実H2):
 * 検索、ローテーションの条件付きの更新の更新件数(1件・0件)、失効(冪等)、ロール選択の条件付きの更新、期限切れの行のバッチ削除
 * (有効なSessionを削除しない)。同時のローテーションは{@link SessionRepositoryConcurrencyTest}。
 */
@DataJpaTest
class SessionRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(30);

  @Autowired private SessionRepository repository;
  @Autowired private TestEntityManager entityManager;

  private Session saved(String roleId, String hash) {
    Session session =
        repository.saveAndFlush(
            AuthTestFactory.session(AuthTestFactory.uniqueUserId(), roleId, hash, NOW, TTL));
    entityManager.clear();
    return session;
  }

  private Session reload(Session session) {
    entityManager.clear();
    return repository.findById(session.getSessionId()).orElseThrow();
  }

  @Test
  void findsByTheCurrentAndThePreviousRefreshTokenHash() {
    String oldHash = AuthTestFactory.newHash();
    String newHash = AuthTestFactory.newHash();
    Session session = saved("r1", oldHash);
    assertThat(repository.findByRefreshTokenHash(oldHash)).isPresent();
    assertThat(repository.findByPreviousRefreshTokenHash(oldHash)).isEmpty();

    repository.rotateWithRole(
        session.getSessionId(), oldHash, newHash, NOW.plusSeconds(5), NOW.plus(TTL), "r1", "r1");

    assertThat(repository.findByRefreshTokenHash(newHash)).isPresent();
    assertThat(repository.findByRefreshTokenHash(oldHash)).isEmpty();
    assertThat(repository.findByPreviousRefreshTokenHash(oldHash))
        .get()
        .extracting(Session::getSessionId)
        .isEqualTo(session.getSessionId());
  }

  @Test
  void rotateWithRoleUpdatesExactlyOneRowAndMovesTheHashToPrevious() {
    String oldHash = AuthTestFactory.newHash();
    String newHash = AuthTestFactory.newHash();
    Session session = saved("r1", oldHash);
    Instant now = NOW.plusSeconds(60);
    Instant newExpiry = now.plus(TTL);

    int updated =
        repository.rotateWithRole(
            session.getSessionId(), oldHash, newHash, now, newExpiry, "r2", "r1");

    assertThat(updated).isEqualTo(1);
    Session loaded = reload(session);
    assertThat(loaded.getRefreshTokenHash()).isEqualTo(newHash);
    assertThat(loaded.getPreviousRefreshTokenHash()).isEqualTo(oldHash);
    assertThat(loaded.getLastRefreshedAt()).isEqualTo(now);
    assertThat(loaded.getRefreshExpiresAt()).isEqualTo(newExpiry);
    assertThat(loaded.getActiveRoleId()).isEqualTo("r2");
    assertThat(loaded.getIssuedAt()).isEqualTo(NOW);
  }

  @Test
  void rotateWithoutRoleHandlesASessionWhoseRoleIsNotSelected() {
    String oldHash = AuthTestFactory.newHash();
    Session session = saved(null, oldHash);

    int updated =
        repository.rotateWithoutRole(
            session.getSessionId(),
            oldHash,
            AuthTestFactory.newHash(),
            NOW.plusSeconds(1),
            NOW.plus(TTL),
            "only-role");

    assertThat(updated).isEqualTo(1);
    assertThat(reload(session).getActiveRoleId()).isEqualTo("only-role");
  }

  @Test
  void rotationUpdatesNothingWhenAnyOfTheConditionsIsNotMet() {
    String oldHash = AuthTestFactory.newHash();
    Session session = saved("r1", oldHash);
    Instant now = NOW.plusSeconds(60);
    String newHash = AuthTestFactory.newHash();

    // 現在のハッシュが異なる(同時の更新に負けた側)
    assertThat(
            repository.rotateWithRole(
                session.getSessionId(),
                AuthTestFactory.newHash(),
                newHash,
                now,
                now.plus(TTL),
                "r1",
                "r1"))
        .isZero();
    // 読み取り時点のアクティブロールと異なる(並行するロール選択に負けた)
    assertThat(
            repository.rotateWithRole(
                session.getSessionId(), oldHash, newHash, now, now.plus(TTL), "r1", "other"))
        .isZero();
    // 読み取り時点では未選択だったが、実際には選択済み
    assertThat(
            repository.rotateWithoutRole(
                session.getSessionId(), oldHash, newHash, now, now.plus(TTL), "r1"))
        .isZero();
    // リフレッシュの有効期限が、経過している(ちょうどの時刻を含む)
    assertThat(
            repository.rotateWithRole(
                session.getSessionId(),
                oldHash,
                newHash,
                NOW.plus(TTL),
                NOW.plus(TTL).plus(TTL),
                "r1",
                "r1"))
        .isZero();
    // 失効済み
    repository.revoke(session.getSessionId());
    assertThat(
            repository.rotateWithRole(
                session.getSessionId(), oldHash, newHash, now, now.plus(TTL), "r1", "r1"))
        .isZero();

    Session unchanged = reload(session);
    assertThat(unchanged.getRefreshTokenHash()).isEqualTo(oldHash);
    assertThat(unchanged.getPreviousRefreshTokenHash()).isNull();
  }

  @Test
  void revokeIsIdempotentAndOnlyAffectsTheTargetSession() {
    Session target = saved("r1", AuthTestFactory.newHash());
    Session other = saved("r1", AuthTestFactory.newHash());

    assertThat(repository.revoke(target.getSessionId())).isEqualTo(1);
    // 冪等: すでにrevokedでも、エラーにしない(更新の件数は0)。
    assertThat(repository.revoke(target.getSessionId())).isZero();
    assertThat(repository.revoke("no-such-session")).isZero();

    assertThat(reload(target).getStatus()).isEqualTo(SessionStatus.REVOKED);
    assertThat(reload(other).getStatus()).isEqualTo(SessionStatus.ACTIVE);
  }

  @Test
  void updateActiveRoleChangesOnlyAnActiveSession() {
    Session active = saved("r1", AuthTestFactory.newHash());
    Session revoked = saved("r1", AuthTestFactory.newHash());
    repository.revoke(revoked.getSessionId());

    assertThat(repository.updateActiveRole(active.getSessionId(), "r2")).isEqualTo(1);
    assertThat(repository.updateActiveRole(revoked.getSessionId(), "r2")).isZero();
    assertThat(repository.updateActiveRole("no-such-session", "r2")).isZero();

    assertThat(reload(active).getActiveRoleId()).isEqualTo("r2");
    assertThat(reload(revoked).getActiveRoleId()).isEqualTo("r1");
  }

  @Test
  void expiredSessionsAreFoundInExpiryOrderUpToTheBatchSizeAndActiveOnesAreNeverDeleted() {
    Instant cutoff = NOW.plus(Duration.ofDays(1));
    Session expiredOld =
        repository.saveAndFlush(
            AuthTestFactory.session(
                AuthTestFactory.uniqueUserId(),
                null,
                AuthTestFactory.newHash(),
                NOW.minus(Duration.ofDays(10)),
                TTL));
    Session expiredRevoked =
        repository.saveAndFlush(
            AuthTestFactory.session(
                AuthTestFactory.uniqueUserId(),
                null,
                AuthTestFactory.newHash(),
                NOW.minus(Duration.ofDays(5)),
                TTL));
    repository.revoke(expiredRevoked.getSessionId());
    Session stillValid =
        repository.saveAndFlush(
            AuthTestFactory.session(
                AuthTestFactory.uniqueUserId(),
                null,
                AuthTestFactory.newHash(),
                cutoff.plusSeconds(1),
                TTL));
    entityManager.clear();

    List<String> ids = repository.findExpiredIds(cutoff, PageRequest.ofSize(1000));
    assertThat(ids).contains(expiredOld.getSessionId(), expiredRevoked.getSessionId());
    assertThat(ids).doesNotContain(stillValid.getSessionId());
    assertThat(ids.indexOf(expiredOld.getSessionId()))
        .isLessThan(ids.indexOf(expiredRevoked.getSessionId()));
    // バッチの上限。
    assertThat(repository.findExpiredIds(cutoff, PageRequest.ofSize(1))).hasSize(1);

    // 削除は、期限の条件を再確認する: 有効なSessionのidを渡しても、削除されない。
    int deleted =
        repository.deleteExpiredByIds(
            List.of(
                expiredOld.getSessionId(),
                expiredRevoked.getSessionId(),
                stillValid.getSessionId()),
            cutoff);
    assertThat(deleted).isEqualTo(2);
    assertThat(repository.findById(stillValid.getSessionId())).isPresent();
    assertThat(repository.findById(expiredOld.getSessionId())).isEmpty();
  }

  @Test
  void deleteAllSessionsRemovesEveryRow() {
    saved("r1", AuthTestFactory.newHash());
    saved(null, AuthTestFactory.newHash());

    assertThat(repository.deleteAllSessions()).isGreaterThanOrEqualTo(2);
    assertThat(repository.count()).isZero();
  }
}
