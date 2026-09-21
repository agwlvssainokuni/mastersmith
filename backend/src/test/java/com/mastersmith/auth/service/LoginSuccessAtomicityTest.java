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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.mastersmith.auth.entity.AccountLoginState;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.repository.AccountLoginStateRepository;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.token.RefreshTokenGenerator;
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.entity.UserStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * ログイン成功の更新({@code AccountLoginState}のリセット)と、Sessionの作成が、<b>一体で</b>反映されること(reliability-design.md
 * NFR4.1): Sessionの作成が失敗した場合は、
 * 失敗回数のリセットも、ロールバックされる。Sessionの作成の失敗は、リフレッシュトークンのハッシュの一意制約の違反(既存のSessionと同じハッシュを生成させる)で再現する。
 */
class LoginSuccessAtomicityTest extends AuthIntegrationTestBase {

  @Autowired private AuthenticationApplicationService service;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private AccountLoginStateRepository stateRepository;
  @MockitoSpyBean private RefreshTokenGenerator refreshTokenGenerator;

  @Test
  void ifTheSessionCannotBeCreatedTheResetOfTheFailureCountIsRolledBackToo() {
    String email = "atomic-" + java.util.UUID.randomUUID() + "@example.test";
    UserAccount user =
        new UserAccount(AuthTestFactory.uniqueUserId(), null, UserStatus.ACTIVE, List.of("r1"));
    when(userAccountLookupApi.findByEmail(email)).thenReturn(Optional.of(user));
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), "wrong")).thenReturn(false);
    when(userAccountLookupApi.verifyPasswordHash(user.userId(), "right")).thenReturn(true);
    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(() -> service.login(email, "wrong")).isInstanceOf(RuntimeException.class);
    }
    assertThat(failures(user.userId())).isEqualTo(2);

    // 既存のSessionと同じリフレッシュトークンを生成させて、Sessionの作成(一意制約)を失敗させる。
    String taken = AuthTestFactory.newRefreshToken();
    sessionRepository.saveAndFlush(
        Session.issue(
            AuthTestFactory.newSessionId(),
            AuthTestFactory.uniqueUserId(),
            null,
            AuthTestFactory.hashOf(taken),
            clock.instant(),
            clock.instant().plus(Duration.ofMinutes(30))));
    doReturn(taken).when(refreshTokenGenerator).generate();

    assertThatThrownBy(() -> service.login(email, "right"))
        .isInstanceOf(DataIntegrityViolationException.class);

    // 成功の更新(リセット)は、Sessionの作成と一体でロールバックされる(予約の枠は、失敗として数えたまま)。
    assertThat(failures(user.userId())).isEqualTo(3);
  }

  private int failures(String userId) {
    return stateRepository
        .findById(userId)
        .map(AccountLoginState::getConsecutiveFailures)
        .orElseThrow();
  }
}
