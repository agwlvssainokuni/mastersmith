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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.mastersmith.auth.cache.SessionCache;
import com.mastersmith.auth.dto.RefreshResponse;
import com.mastersmith.auth.entity.Session;
import com.mastersmith.auth.entity.SessionStatus;
import com.mastersmith.auth.exception.AuthStorageUnavailableException;
import com.mastersmith.auth.exception.RefreshRejectedException;
import com.mastersmith.auth.exception.RoleNotHeldException;
import com.mastersmith.auth.exception.SessionExpiredException;
import com.mastersmith.auth.repository.SessionRepository;
import com.mastersmith.auth.testsupport.AuthIntegrationTestBase;
import com.mastersmith.auth.testsupport.AuthTestFactory;
import com.mastersmith.auth.token.AccessTokenVerifier;
import com.mastersmith.common.security.Operator;
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.entity.UserStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * リフレッシュ(W2)・ログアウト(W3)・アクティブロールの選択(W4)と、{@link
 * SessionService}のテスト(実H2、C11はモック。BR5.6〜BR5.10、NFR2.3・NFR2.6・NFR4.1・NFR4.3):
 * ローテーション(同一のトークンの同時の更新で1件のみ成功、負けた側はSessionを失効させない)、猶予内・猶予を超えた再使用、ログアウトが該当のSessionだけを失効させること、
 * ロール選択(保持しないロールは403相当)、リフレッシュ時のロールの再確認の判定表の全ケース、失効の冪等。
 */
class SessionServiceTest extends AuthIntegrationTestBase {

  private static final Duration TTL = Duration.ofMinutes(30);
  private static final Duration GRACE = Duration.ofSeconds(10);

  @Autowired private AuthenticationApplicationService service;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private SessionCache cache;
  @Autowired private AccessTokenVerifier accessTokenVerifier;
  @Autowired private MeterRegistry meterRegistry;

  /** ユーザー(有効、指定のロール)と、そのSession(指定のアクティブロール・リフレッシュトークン)を用意する。 */
  private record Fixture(String userId, String sessionId, String refreshToken) {}

  private Fixture fixture(String activeRoleId, String... roles) {
    String userId = AuthTestFactory.uniqueUserId();
    stubUser(userId, roles);
    return insertSession(userId, activeRoleId);
  }

  private void stubUser(String userId, String... roles) {
    when(userAccountLookupApi.isDisabled(userId)).thenReturn(false);
    when(userAccountLookupApi.findByUserId(userId))
        .thenReturn(Optional.of(new UserAccount(userId, null, UserStatus.ACTIVE, List.of(roles))));
  }

  private Fixture insertSession(String userId, String activeRoleId) {
    String token = AuthTestFactory.newRefreshToken();
    Session session =
        Session.issue(
            AuthTestFactory.newSessionId(),
            userId,
            activeRoleId,
            AuthTestFactory.hashOf(token),
            clock.instant(),
            clock.instant().plus(TTL));
    sessionRepository.saveAndFlush(session);
    return new Fixture(userId, session.getSessionId(), token);
  }

  private Session session(Fixture fixture) {
    return sessionRepository.findById(fixture.sessionId()).orElseThrow();
  }

  private double counter(String name) {
    return meterRegistry.get(name).counter().count();
  }

  // ---- リフレッシュ: 成功 ----

  @Test
  void aRefreshRotatesTheTokenExtendsTheExpiryAndKeepsTheOldHashAsThePreviousOne() {
    Fixture fixture = fixture("r1", "r1", "r2");
    clock.advance(Duration.ofMinutes(5));

    RefreshResponse response = service.refresh(fixture.refreshToken());

    assertThat(response.refreshToken()).isNotEqualTo(fixture.refreshToken());
    assertThat(response.roles()).containsExactly("r1", "r2");
    assertThat(response.activeRoleId()).isEqualTo("r1");
    assertThat(accessTokenVerifier.verify(response.accessToken()).sid())
        .isEqualTo(fixture.sessionId());
    Session rotated = session(fixture);
    assertThat(rotated.getRefreshTokenHash())
        .isEqualTo(AuthTestFactory.hashOf(response.refreshToken()));
    assertThat(rotated.getPreviousRefreshTokenHash())
        .isEqualTo(AuthTestFactory.hashOf(fixture.refreshToken()));
    assertThat(rotated.getLastRefreshedAt()).isEqualTo(clock.instant());
    assertThat(rotated.getRefreshExpiresAt()).isEqualTo(clock.instant().plus(TTL));
    assertThat(rotated.getStatus()).isEqualTo(SessionStatus.ACTIVE);
  }

  @Test
  void theNewTokenCanBeRefreshedAgainAndTheChainOfRotationsContinues() {
    Fixture fixture = fixture("r1", "r1");
    RefreshResponse first = service.refresh(fixture.refreshToken());
    clock.advance(Duration.ofMinutes(1));

    RefreshResponse second = service.refresh(first.refreshToken());

    assertThat(second.refreshToken()).isNotIn(fixture.refreshToken(), first.refreshToken());
    assertThat(session(fixture).getPreviousRefreshTokenHash())
        .isEqualTo(AuthTestFactory.hashOf(first.refreshToken()));
  }

  @Test
  void theUserAccountApiIsCalledOutsideAnyTransactionDuringARefresh() {
    Fixture fixture = fixture("r1", "r1");
    List<String> violations = new ArrayList<>();
    org.mockito.stubbing.Answer<Object> observe =
        invocation -> {
          if (TransactionSynchronizationManager.isActualTransactionActive()) {
            violations.add(invocation.getMethod().getName());
          }
          return invocation.getMethod().getReturnType() == boolean.class
              ? Boolean.FALSE
              : Optional.of(
                  new UserAccount(fixture.userId(), null, UserStatus.ACTIVE, List.of("r1")));
        };
    org.mockito.Mockito.doAnswer(observe).when(userAccountLookupApi).isDisabled(anyString());
    org.mockito.Mockito.doAnswer(observe).when(userAccountLookupApi).findByUserId(anyString());

    service.refresh(fixture.refreshToken());

    assertThat(violations).isEmpty();
  }

  // ---- リフレッシュ: 拒否 ----

  @Test
  void unknownBlankAndGarbageTokensAreAllRejectedTheSameWay() {
    for (String token :
        new String[] {null, "", "  ", "unknown-token", AuthTestFactory.newRefreshToken()}) {
      assertThatThrownBy(() -> service.refresh(token))
          .isInstanceOf(RefreshRejectedException.class)
          .hasMessage("Refresh rejected");
    }
  }

  @Test
  void anExpiredSessionIsRejectedWithoutChangingIt() {
    Fixture fixture = fixture("r1", "r1");
    clock.advance(TTL); // リフレッシュの有効期限ちょうどは、有効でない

    assertThatThrownBy(() -> service.refresh(fixture.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);

    Session unchanged = session(fixture);
    assertThat(unchanged.getRefreshTokenHash())
        .isEqualTo(AuthTestFactory.hashOf(fixture.refreshToken()));
    assertThat(unchanged.getStatus()).isEqualTo(SessionStatus.ACTIVE);
  }

  @Test
  void aRevokedSessionIsRejected() {
    Fixture fixture = fixture("r1", "r1");
    service.logout(new Operator(fixture.userId(), fixture.sessionId(), "r1"));

    assertThatThrownBy(() -> service.refresh(fixture.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);
  }

  // ---- 再送の猶予・再使用の検知(BR5.6) ----

  @Test
  void anOldTokenSubmittedWithinTheGraceIsRejectedButTheSessionAndTheNewTokenStayValid() {
    Fixture fixture = fixture("r1", "r1");
    RefreshResponse first = service.refresh(fixture.refreshToken());
    double before = counter("auth.refresh.reuse.within.grace");

    clock.advance(GRACE.minusSeconds(1));
    assertThatThrownBy(() -> service.refresh(fixture.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);

    // 正当な再送・複数タブ・同時実行による競合とみなし、Sessionは失効させない。
    assertThat(session(fixture).getStatus()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(counter("auth.refresh.reuse.within.grace")).isEqualTo(before + 1);
    assertThat(service.refresh(first.refreshToken()).refreshToken()).isNotBlank();
  }

  @Test
  void theGraceBoundaryIsInclusiveAndOneMillisecondLaterTheReuseIsTreatedAsATheft() {
    Fixture atBoundary = fixture("r1", "r1");
    service.refresh(atBoundary.refreshToken());
    clock.advance(GRACE);
    assertThatThrownBy(() -> service.refresh(atBoundary.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);
    assertThat(session(atBoundary).getStatus()).isEqualTo(SessionStatus.ACTIVE);

    clock.set(BASE_TIME);
    Fixture pastBoundary = fixture("r1", "r1");
    service.refresh(pastBoundary.refreshToken());
    clock.advance(GRACE.plusMillis(1));
    assertThatThrownBy(() -> service.refresh(pastBoundary.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);
    assertThat(session(pastBoundary).getStatus()).isEqualTo(SessionStatus.REVOKED);
  }

  @Test
  void anOldTokenSubmittedAfterTheGraceRevokesTheSessionSoEvenTheNewTokenStopsWorking() {
    Fixture fixture = fixture("r1", "r1");
    RefreshResponse first = service.refresh(fixture.refreshToken());
    double before = counter("auth.refresh.token.reuse.detected");

    clock.advance(GRACE.plusSeconds(1));
    assertThatThrownBy(() -> service.refresh(fixture.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);

    assertThat(session(fixture).getStatus()).isEqualTo(SessionStatus.REVOKED);
    assertThat(counter("auth.refresh.token.reuse.detected")).isEqualTo(before + 1);
    assertThat(cache.find(fixture.sessionId()))
        .get()
        .extracting("status")
        .isEqualTo(SessionStatus.REVOKED);
    assertThatThrownBy(() -> service.refresh(first.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);
  }

  // ---- ユーザーの無効化(BR5.7) ----

  @Test
  void aDisabledUserIsRejectedAtRefreshAndTheSessionIsRevoked() {
    Fixture fixture = fixture("r1", "r1");
    when(userAccountLookupApi.isDisabled(fixture.userId())).thenReturn(true);

    assertThatThrownBy(() -> service.refresh(fixture.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);

    assertThat(session(fixture).getStatus()).isEqualTo(SessionStatus.REVOKED);
    assertThat(cache.find(fixture.sessionId()))
        .get()
        .extracting("status")
        .isEqualTo(SessionStatus.REVOKED);
  }

  @Test
  void aUserWhoNoLongerExistsIsTreatedAsDisabled() {
    Fixture fixture = fixture("r1", "r1");
    when(userAccountLookupApi.findByUserId(fixture.userId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh(fixture.refreshToken()))
        .isInstanceOf(RefreshRejectedException.class);

    assertThat(session(fixture).getStatus()).isEqualTo(SessionStatus.REVOKED);
  }

  @Test
  void aStorageFailureAtRefreshDoesNotRotateTheTokenSoTheClientCanRetryWithTheSameOne() {
    Fixture fixture = fixture("r1", "r1");
    when(userAccountLookupApi.isDisabled(fixture.userId()))
        .thenThrow(new DataAccessResourceFailureException("db down"));

    assertThatThrownBy(() -> service.refresh(fixture.refreshToken()))
        .isInstanceOf(AuthStorageUnavailableException.class);

    assertThat(session(fixture).getRefreshTokenHash())
        .isEqualTo(AuthTestFactory.hashOf(fixture.refreshToken()));
    org.mockito.Mockito.doReturn(false).when(userAccountLookupApi).isDisabled(fixture.userId());
    assertThat(service.refresh(fixture.refreshToken()).refreshToken()).isNotBlank();
  }

  // ---- ロールの再確認(BR5.10、判定表の全ケース) ----

  static Stream<Arguments> roleReconfirmationTable() {
    return Stream.of(
        Arguments.of("選択済みで、含まれる", "r1", List.of("r1", "r2"), "r1"),
        Arguments.of("選択済みで、含まれる(唯一)", "r1", List.of("r1"), "r1"),
        Arguments.of("選択済みで、含まれなくなり、残りが1つ(自動選択)", "r1", List.of("r2"), "r2"),
        Arguments.of("選択済みで、含まれなくなり、残りが2つ以上(未選択に戻す)", "r1", List.of("r2", "r3"), null),
        Arguments.of("選択済みで、ロールがなくなった(未選択に戻す)", "r1", List.of(), null),
        Arguments.of("未選択で、ロールがちょうど1つになった(自動選択)", null, List.of("r2"), "r2"),
        Arguments.of("未選択で、ロールが複数(未選択のまま)", null, List.of("r1", "r2"), null),
        Arguments.of("未選択で、ロールが0個(未選択のまま)", null, List.of(), null));
  }

  @ParameterizedTest(name = "{0}: {1} × {2} -> {3}")
  @MethodSource("roleReconfirmationTable")
  void theActiveRoleIsReconfirmedAgainstTheLatestRolesAtEveryRefresh(
      String label, String current, List<String> roles, String expected) {
    Fixture fixture = fixture(current, roles.toArray(String[]::new));

    RefreshResponse response = service.refresh(fixture.refreshToken());

    assertThat(response.activeRoleId()).isEqualTo(expected);
    assertThat(response.roles()).containsExactlyElementsOf(roles);
    assertThat(session(fixture).getActiveRoleId()).isEqualTo(expected);
    // 純粋な判定表(SessionService.reconfirmActiveRole)と、一致する。
    assertThat(SessionService.reconfirmActiveRole(current, roles)).isEqualTo(expected);
  }

  static Stream<Arguments> initialRoleTable() {
    return Stream.of(
        Arguments.of(List.of(), null),
        Arguments.of(List.of("only"), "only"),
        Arguments.of(List.of("a", "b"), null),
        Arguments.of(List.of("a", "b", "c"), null));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("initialRoleTable")
  void theInitialActiveRoleIsSelectedAutomaticallyOnlyForExactlyOneRole(
      Collection<String> roles, String expected) {
    assertThat(SessionService.initialActiveRole(roles)).isEqualTo(expected);
  }

  // ---- 同時のリフレッシュ ----

  @Test
  void onlyOneOfManyConcurrentRefreshesOfTheSameTokenSucceedsAndTheLosersDoNotRevokeTheSession()
      throws Exception {
    Fixture fixture = fixture("r1", "r1");
    int attempts = 8;
    ExecutorService executor = Executors.newFixedThreadPool(attempts);
    List<RefreshResponse> winners = new ArrayList<>();
    int rejected = 0;
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Object>> outcomes = new ArrayList<>();
      for (int i = 0; i < attempts; i++) {
        outcomes.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return service.refresh(fixture.refreshToken());
                  } catch (RefreshRejectedException e) {
                    return e;
                  }
                }));
      }
      start.countDown();
      for (Future<Object> outcome : outcomes) {
        Object result = outcome.get(60, TimeUnit.SECONDS);
        if (result instanceof RefreshResponse response) {
          winners.add(response);
        } else {
          rejected++;
        }
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(winners).hasSize(1);
    assertThat(rejected).isEqualTo(attempts - 1);
    Session after = session(fixture);
    // 負けた側は、Sessionを失効させない(到着の順序によらず、猶予内の再使用と同じ結果)。
    assertThat(after.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(after.getRefreshTokenHash())
        .isEqualTo(AuthTestFactory.hashOf(winners.get(0).refreshToken()));
  }

  // ---- ログアウト(BR5.8) ----

  @Test
  void logoutRevokesOnlyTheSessionOfTheRequestAndNotTheOtherDevicesOfTheSameUser() {
    String userId = AuthTestFactory.uniqueUserId();
    stubUser(userId, "r1");
    Fixture phone = insertSession(userId, "r1");
    Fixture laptop = insertSession(userId, "r1");
    // キャッシュに載せておく(ログアウトのコミット後に、無効化されることの確認)。
    assertThat(cache.find(phone.sessionId()))
        .get()
        .extracting("status")
        .isEqualTo(SessionStatus.ACTIVE);

    service.logout(new Operator(userId, phone.sessionId(), "r1"));

    assertThat(session(phone).getStatus()).isEqualTo(SessionStatus.REVOKED);
    assertThat(session(laptop).getStatus()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(cache.find(phone.sessionId()))
        .get()
        .extracting("status")
        .isEqualTo(SessionStatus.REVOKED);
    assertThat(service.refresh(laptop.refreshToken()).refreshToken()).isNotBlank();
  }

  @Test
  void revokingAnAlreadyRevokedSessionIsIdempotent() {
    Fixture fixture = fixture("r1", "r1");
    Operator operator = new Operator(fixture.userId(), fixture.sessionId(), "r1");

    service.logout(operator);
    service.logout(operator);

    assertThat(session(fixture).getStatus()).isEqualTo(SessionStatus.REVOKED);
  }

  // ---- アクティブロールの選択(BR5.9) ----

  @Test
  void aHeldRoleIsSelectedForThisSessionOnlyAndTakesEffectImmediately() {
    String userId = AuthTestFactory.uniqueUserId();
    stubUser(userId, "r1", "r2");
    Fixture phone = insertSession(userId, null);
    Fixture laptop = insertSession(userId, "r1");
    assertThat(cache.find(phone.sessionId())).get().extracting("activeRoleId").isNull();

    String selected = service.selectActiveRole(new Operator(userId, phone.sessionId(), null), "r2");

    assertThat(selected).isEqualTo("r2");
    assertThat(session(phone).getActiveRoleId()).isEqualTo("r2");
    assertThat(session(laptop).getActiveRoleId()).isEqualTo("r1");
    // キャッシュは、コミット後に無効化されるため、以降のリクエストへ即時に反映される。
    assertThat(cache.find(phone.sessionId())).get().extracting("activeRoleId").isEqualTo("r2");
  }

  static Stream<Arguments> notHeldRoles() {
    return Stream.of(
        Arguments.of("保持していないロール", "someone-elses-role"),
        Arguments.of("null", null),
        Arguments.of("空", ""),
        Arguments.of("空白のみ", "  "));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("notHeldRoles")
  void aRoleThatIsNotHeldIsRejectedAndTheSessionIsNotChanged(String label, String roleId) {
    String userId = AuthTestFactory.uniqueUserId();
    stubUser(userId, "r1", "r2");
    Fixture fixture = insertSession(userId, "r1");

    assertThatThrownBy(
            () -> service.selectActiveRole(new Operator(userId, fixture.sessionId(), "r1"), roleId))
        .isInstanceOf(RoleNotHeldException.class);

    assertThat(session(fixture).getActiveRoleId()).isEqualTo("r1");
  }

  @Test
  void aUserWhoNoLongerExistsCannotSelectARole() {
    String userId = AuthTestFactory.uniqueUserId();
    Fixture fixture = insertSession(userId, null);
    when(userAccountLookupApi.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.selectActiveRole(new Operator(userId, fixture.sessionId(), null), "r1"))
        .isInstanceOf(RoleNotHeldException.class);
  }

  @Test
  void selectingARoleOnASessionThatBecameInactiveAfterAuthenticationIsRejectedAs401() {
    String userId = AuthTestFactory.uniqueUserId();
    stubUser(userId, "r1", "r2");
    Fixture fixture = insertSession(userId, "r1");
    service.logout(new Operator(userId, fixture.sessionId(), "r1"));

    assertThatThrownBy(
            () -> service.selectActiveRole(new Operator(userId, fixture.sessionId(), "r1"), "r2"))
        .isInstanceOf(SessionExpiredException.class);

    assertThat(session(fixture).getActiveRoleId()).isEqualTo("r1");
  }

  @Test
  void theRolesOfTheUserAreReadFreshAtEverySelectionSoARemovedRoleCannotBeSelected() {
    String userId = AuthTestFactory.uniqueUserId();
    stubUser(userId, "r1", "r2");
    Fixture fixture = insertSession(userId, "r1");
    service.selectActiveRole(new Operator(userId, fixture.sessionId(), "r1"), "r2");
    // ロールr2が外された。
    stubUser(userId, "r1");

    assertThatThrownBy(
            () -> service.selectActiveRole(new Operator(userId, fixture.sessionId(), "r2"), "r2"))
        .isInstanceOf(RoleNotHeldException.class);
  }
}
