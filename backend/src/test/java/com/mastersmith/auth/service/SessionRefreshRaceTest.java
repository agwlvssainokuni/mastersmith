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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * リフレッシュとロール選択の並行実行の、競合の扱い(security-design.md NFR2.3の判定表、NFR8.2):
 * リフレッシュの読み取りと更新の間に、ロール選択がコミットされても、その選択を、
 * 古いロールで上書きしない(失われた更新の防止。1回だけやり直す)こと、やり直しも失敗した場合に401でSessionを失効させないこと。競合は、C11の呼び出しの間(読み取りの後、更新の前)に、
 * 別のトランザクションでロール選択を行うことで、決定的に再現する({@link SessionRepository}をspyし、更新の失敗を再現する)。
 */
class SessionRefreshRaceTest extends AuthIntegrationTestBase {

  @Autowired private AuthenticationApplicationService service;
  @MockitoSpyBean private SessionRepository sessionRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private Session newSession(String userId, String activeRoleId, String token) {
    Session session =
        Session.issue(
            AuthTestFactory.newSessionId(),
            userId,
            activeRoleId,
            AuthTestFactory.hashOf(token),
            clock.instant(),
            clock.instant().plus(Duration.ofMinutes(30)));
    return sessionRepository.saveAndFlush(session);
  }

  private void stubRoles(String userId, String... roles) {
    when(userAccountLookupApi.isDisabled(userId)).thenReturn(false);
    when(userAccountLookupApi.findByUserId(userId))
        .thenReturn(Optional.of(new UserAccount(userId, null, UserStatus.ACTIVE, List.of(roles))));
  }

  @Test
  void aRoleSelectionCommittedBetweenTheReadAndTheRotationIsNotOverwrittenWithTheOldRole() {
    String userId = AuthTestFactory.uniqueUserId();
    String token = AuthTestFactory.newRefreshToken();
    Session session = newSession(userId, "r1", token);
    stubRoles(userId, "r1", "r2");
    // リフレッシュがSessionを読み取った後(C11のfindByUserIdの呼び出し)に、別のリクエストが、ロール選択をコミットする。
    doAnswer(
            invocation -> {
              new TransactionTemplate(transactionManager)
                  .executeWithoutResult(
                      status -> sessionRepository.updateActiveRole(session.getSessionId(), "r2"));
              return Optional.of(
                  new UserAccount(userId, null, UserStatus.ACTIVE, List.of("r1", "r2")));
            })
        .when(userAccountLookupApi)
        .findByUserId(userId);

    RefreshResponse response = service.refresh(token);

    // 選択(r2)は、失われない。1回だけやり直して、更新は成功する。
    assertThat(response.activeRoleId()).isEqualTo("r2");
    Session after = sessionRepository.findById(session.getSessionId()).orElseThrow();
    assertThat(after.getActiveRoleId()).isEqualTo("r2");
    assertThat(after.getRefreshTokenHash())
        .isEqualTo(AuthTestFactory.hashOf(response.refreshToken()));
    verify(sessionRepository, times(2))
        .rotateWithRole(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
  }

  @Test
  void ifTheRetryAlsoLosesTheRefreshIsRejectedWithoutRevokingTheSessionAndRetriesOnlyOnce() {
    String userId = AuthTestFactory.uniqueUserId();
    String token = AuthTestFactory.newRefreshToken();
    Session session = newSession(userId, "r1", token);
    stubRoles(userId, "r1", "r2");
    // 更新が、常に条件を満たさない(0件)状態を再現する。
    doReturn(0)
        .when(sessionRepository)
        .rotateWithRole(anyString(), anyString(), anyString(), any(), any(), any(), anyString());

    assertThatThrownBy(() -> service.refresh(token)).isInstanceOf(RefreshRejectedException.class);

    // やり直しは、1回だけ(合計2回)。Sessionは、失効させず、トークンも更新しない。
    verify(sessionRepository, times(2))
        .rotateWithRole(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
    Session after = sessionRepository.findById(session.getSessionId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(after.getRefreshTokenHash()).isEqualTo(AuthTestFactory.hashOf(token));
  }

  @Test
  void ifTheTokenWasRotatedByAnotherRequestTheLoserDoesNotRetryNorRevoke() {
    String userId = AuthTestFactory.uniqueUserId();
    String token = AuthTestFactory.newRefreshToken();
    Session session = newSession(userId, "r1", token);
    stubRoles(userId, "r1");
    // C11の呼び出しの間に、別のリクエストが、同じトークンでローテーションを完了する(同時のリフレッシュに負ける)。
    doAnswer(
            invocation -> {
              new TransactionTemplate(transactionManager)
                  .executeWithoutResult(
                      status ->
                          sessionRepository.rotateWithRole(
                              session.getSessionId(),
                              AuthTestFactory.hashOf(token),
                              AuthTestFactory.newHash(),
                              clock.instant(),
                              clock.instant().plus(Duration.ofMinutes(30)),
                              "r1",
                              "r1"));
              return Optional.of(new UserAccount(userId, null, UserStatus.ACTIVE, List.of("r1")));
            })
        .when(userAccountLookupApi)
        .findByUserId(userId);

    assertThatThrownBy(() -> service.refresh(token)).isInstanceOf(RefreshRejectedException.class);

    assertThat(sessionRepository.findById(session.getSessionId()).orElseThrow().getStatus())
        .isEqualTo(SessionStatus.ACTIVE);
    // 自分の更新は1回だけ試みて、やり直さない(ハッシュが変わっている)。
    verify(sessionRepository, times(2))
        .rotateWithRole(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
  }
}
