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
import static org.mockito.Mockito.when;

import com.mastersmith.auth.dto.RefreshResponse;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.entity.SessionStatus;
import com.mastersmith.auth.exception.RefreshRejectedException;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.entity.UserStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * 再送の猶予を0({@code
 * mastersmith.auth.refresh-reuse-grace=0s})にすると、Q5=Aの元の挙動(すでに無効になったトークンが再び使われたら、そのSessionを、即、失効させる)に
 * なること(機能設計の{@code [assumption]}との差異の確認、code-generation-plan.md 前提事項6)。
 */
@TestPropertySource(properties = "mastersmith.auth.refresh-reuse-grace=0s")
class RefreshGraceZeroTest extends AuthIntegrationTestBase {

  @Autowired private AuthenticationApplicationService service;
  @Autowired private SessionRepository sessionRepository;

  @Test
  void withNoGraceTheImmediateReuseOfAnOldTokenRevokesTheSessionAtOnce() {
    String userId = AuthTestFactory.uniqueUserId();
    when(userAccountLookupApi.isDisabled(userId)).thenReturn(false);
    when(userAccountLookupApi.findByUserId(userId))
        .thenReturn(Optional.of(new UserAccount(userId, null, UserStatus.ACTIVE, List.of("r1"))));
    String token = AuthTestFactory.newRefreshToken();
    Session session =
        Session.issue(
            AuthTestFactory.newSessionId(),
            userId,
            "r1",
            AuthTestFactory.hashOf(token),
            clock.instant(),
            clock.instant().plus(Duration.ofMinutes(30)));
    sessionRepository.saveAndFlush(session);
    RefreshResponse first = service.refresh(token);

    // 同じ時刻(経過0)でも、猶予がないため、無効なトークンの再使用(盗用の疑い)。
    clock.advance(Duration.ofMillis(1));
    assertThatThrownBy(() -> service.refresh(token)).isInstanceOf(RefreshRejectedException.class);

    assertThat(sessionRepository.findById(session.getSessionId()).orElseThrow().getStatus())
        .isEqualTo(SessionStatus.REVOKED);
    assertThatThrownBy(() -> service.refresh(first.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);
  }
}
