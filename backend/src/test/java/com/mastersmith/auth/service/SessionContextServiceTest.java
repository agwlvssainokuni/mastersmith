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

package com.mastersmith.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.mastersmith.auth.SessionContextApi;
import com.mastersmith.auth.cache.SessionCache;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.exception.SessionNotFoundException;
import com.mastersmith.auth.observation.AuthEventLogger;
import com.mastersmith.auth.observation.AuthMetrics;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.testsupport.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SessionContextService}(C14、{@link SessionContextApi})の契約テスト(BR5.13):
 * Sessionが存在しない場合の{@link SessionNotFoundException}、有効でない場合(revoked、 またはリフレッシュの有効期限の経過)の{@link
 * SessionExpiredException}、アクティブロールが未選択の場合のnull、内部設定DBの障害での{@link AuthStorageUnavailableException}。
 * 認証フィルタと同じ{@link SessionCache}から引く。
 */
class SessionContextServiceTest extends AuthIntegrationTestBase {

  @Autowired private SessionContextApi api;
  @Autowired private SessionRepository repository;
  @Autowired private SessionCache cache;
  @Autowired private PlatformTransactionManager transactionManager;

  private Session saved(String role) {
    return repository.saveAndFlush(
        Session.issue(
            AuthTestFactory.newSessionId(),
            AuthTestFactory.uniqueUserId(),
            role,
            AuthTestFactory.newHash(),
            clock.instant(),
            clock.instant().plus(Duration.ofMinutes(30))));
  }

  @Test
  void theActiveRoleIdOfAnActiveSessionIsReturned() {
    Session session = saved("role-x");

    assertThat(api.getActiveRoleId(session.getSessionId())).isEqualTo("role-x");
  }

  @Test
  void anUnselectedActiveRoleIsReturnedAsNullNotAsAnException() {
    Session session = saved(null);

    assertThat(api.getActiveRoleId(session.getSessionId())).isNull();
  }

  @Test
  void aSessionThatDoesNotExistThrowsSessionNotFound() {
    assertThatThrownBy(() -> api.getActiveRoleId("no-such-session"))
        .isInstanceOf(SessionNotFoundException.class);
    assertThatThrownBy(() -> api.getActiveRoleId(AuthTestFactory.newSessionId()))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  void aRevokedSessionThrowsSessionExpired() {
    Session session = saved("r1");
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> repository.revoke(session.getSessionId()));
    cache.invalidate(session.getSessionId());

    assertThatThrownBy(() -> api.getActiveRoleId(session.getSessionId()))
        .isInstanceOf(SessionExpiredException.class);
  }

  @Test
  void aSessionPastItsRefreshExpiryThrowsSessionExpiredEvenIfItIsStillMarkedActive() {
    Session session = saved("r1");
    clock.advance(Duration.ofMinutes(30)); // 有効期限ちょうど

    assertThatThrownBy(() -> api.getActiveRoleId(session.getSessionId()))
        .isInstanceOf(SessionExpiredException.class);
  }

  @Test
  void aStorageFailureOnACacheMissThrowsStorageUnavailable() {
    SessionRepository failing = mock(SessionRepository.class);
    when(failing.findById(org.mockito.ArgumentMatchers.anyString()))
        .thenThrow(new QueryTimeoutException("db down"));
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    SessionCache missing =
        new SessionCache(
            failing,
            10,
            Duration.ofSeconds(60),
            registry,
            new AuthExceptionTranslator(new AuthMetrics(registry), new AuthEventLogger()),
            Ticker.systemTicker());
    SessionContextService service = new SessionContextService(missing, new MutableClock());

    assertThatThrownBy(() -> service.getActiveRoleId("any"))
        .isInstanceOf(AuthStorageUnavailableException.class);
  }

  @Test
  void theOptionalContractIsUsedByTheCacheWithoutRememberingAMissingSession() {
    // 存在しなかったSessionが、あとで作られた場合、次の呼び出しで見える(否定的な結果を保持しない)。
    Session later =
        Session.issue(
            AuthTestFactory.newSessionId(),
            AuthTestFactory.uniqueUserId(),
            "r9",
            AuthTestFactory.newHash(),
            clock.instant(),
            clock.instant().plus(Duration.ofMinutes(30)));
    assertThatThrownBy(() -> api.getActiveRoleId(later.getSessionId()))
        .isInstanceOf(SessionNotFoundException.class);

    repository.saveAndFlush(later);

    assertThat(Optional.ofNullable(api.getActiveRoleId(later.getSessionId()))).contains("r9");
  }
}
