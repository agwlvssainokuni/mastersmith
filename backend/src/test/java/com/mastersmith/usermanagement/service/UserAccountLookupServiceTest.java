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

package com.mastersmith.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.UserAccount;
import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.HashConcurrencyLimiter;
import com.mastersmith.usermanagement.security.PasswordHasher;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link UserAccountLookupService}(C11)のテスト(W8、rules.md BR4.13): {@code
 * findByEmail}(和集合・passwordHashがnull・正規化)、 {@code
 * verifyPasswordHash}(activeのみ検証・129文字以上はfalse・許可待ちの例外の伝播)、ログイン成功時のハッシュ更新(古いパラメータの更新・競合で更新しない・更新の失敗が
 * ログインの成否に影響しない・読み取り専用のトランザクションの中から呼んでも更新が失われない)、{@code isDisabled}(最新のstatus・不存在はtrue)。
 * コミットを伴う検証のため、テストはトランザクションの外で実行し、作成したUserは後始末で削除する。
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserAccountLookupServiceTest {

  private static final String PASSWORD = "correct horse battery";

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final PermissionEngineApi permissionEngineApi = mock(PermissionEngineApi.class);
  private PasswordHasher currentHasher;
  private PasswordHasher legacyHasher;
  private UserAccountLookupService service;

  private PasswordHasher hasher(int memoryKib, int iterations) {
    UserManagementProperties.Hash params =
        new UserManagementProperties.Hash(
            memoryKib, iterations, 1, 16, 32, 4, Duration.ofSeconds(5));
    return new PasswordHasher(
        params, new HashConcurrencyLimiter(4, params.waitTimeout(), meterRegistry), meterRegistry);
  }

  @BeforeEach
  void setUp() {
    currentHasher = hasher(2048, 2);
    legacyHasher = hasher(1024, 1);
    service =
        new UserAccountLookupService(
            userRepository, permissionEngineApi, currentHasher, transactionManager);
  }

  @AfterEach
  void tearDown() {
    preferenceRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User activeUserWithHash(String hash) {
    return userRepository.saveAndFlush(
        User.activeAdmin("有効 花子", UserTestFactory.uniqueEmail(), hash, List.of("r-direct")));
  }

  // ---- findByEmail ----

  @Test
  void findByEmailReturnsTheUnionOfDirectAndGroupRolesWithoutThePasswordHash() {
    User user = activeUserWithHash(currentHasher.hash(PASSWORD));
    when(permissionEngineApi.getGroupDerivedRoleIds(user.getUserId()))
        .thenReturn(List.of("r-group", "r-direct", "r-another"));

    UserAccount account = service.findByEmail(user.getEmail()).orElseThrow();

    assertThat(account.userId()).isEqualTo(user.getUserId());
    assertThat(account.passwordHash()).isNull();
    assertThat(account.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(account.roleIds()).containsExactly("r-another", "r-direct", "r-group");
  }

  @Test
  void findByEmailNormalizesTheEmailAndReturnsUsersOfAnyStatus() {
    User invited =
        userRepository.saveAndFlush(
            UserTestFactory.invitedUser(
                UserTestFactory.uniqueEmail(), List.of(), UserTestFactory.newToken()));
    when(permissionEngineApi.getGroupDerivedRoleIds(anyString())).thenReturn(List.of());

    Optional<UserAccount> found =
        service.findByEmail("  " + invited.getEmail().toUpperCase() + "  ");

    assertThat(found).isPresent();
    assertThat(found.get().status()).isEqualTo(UserStatus.INVITED);
    assertThat(found.get().roleIds()).isEmpty();
  }

  @Test
  void findByEmailReturnsEmptyForUnknownNullAndBlankEmails() {
    assertThat(service.findByEmail("nobody@example.test")).isEmpty();
    assertThat(service.findByEmail(null)).isEmpty();
    assertThat(service.findByEmail("   ")).isEmpty();
  }

  // ---- verifyPasswordHash ----

  @Test
  void verifyPasswordHashAcceptsTheCorrectPasswordOfAnActiveUserAndRejectsAWrongOne() {
    User user = activeUserWithHash(currentHasher.hash(PASSWORD));

    assertThat(service.verifyPasswordHash(user.getUserId(), PASSWORD)).isTrue();
    assertThat(service.verifyPasswordHash(user.getUserId(), "another password")).isFalse();
  }

  @Test
  void verifyPasswordHashIsFalseWithoutComputingForInvitedDisabledAndUnknownUsers() {
    PasswordHasher neverUsed = mock(PasswordHasher.class);
    UserAccountLookupService guarded =
        new UserAccountLookupService(
            userRepository, permissionEngineApi, neverUsed, transactionManager);
    User invited =
        userRepository.saveAndFlush(
            UserTestFactory.invitedUser(
                UserTestFactory.uniqueEmail(), List.of(), UserTestFactory.newToken()));
    User disabled = activeUserWithHash(currentHasher.hash(PASSWORD));
    disabled.disable();
    userRepository.saveAndFlush(disabled);

    assertThat(guarded.verifyPasswordHash(invited.getUserId(), PASSWORD)).isFalse();
    assertThat(guarded.verifyPasswordHash(disabled.getUserId(), PASSWORD)).isFalse();
    assertThat(guarded.verifyPasswordHash("no-such-user", PASSWORD)).isFalse();
    assertThat(guarded.verifyPasswordHash(null, PASSWORD)).isFalse();
    verifyNoInteractions(neverUsed);
  }

  @Test
  void verifyPasswordHashIsFalseForAnActiveUserWithoutAPasswordHash() {
    // passwordHashがnullのactive(通常は起こらない不整合)は、検証せずにfalse。
    UserRepository inconsistent = mock(UserRepository.class);
    User noHash = mock(User.class);
    when(noHash.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(noHash.getPasswordHash()).thenReturn(null);
    when(inconsistent.findById("user-x")).thenReturn(Optional.of(noHash));
    PasswordHasher neverUsed = mock(PasswordHasher.class);
    UserAccountLookupService guarded =
        new UserAccountLookupService(
            inconsistent, permissionEngineApi, neverUsed, transactionManager);

    assertThat(guarded.verifyPasswordHash("user-x", PASSWORD)).isFalse();
    verifyNoInteractions(neverUsed);
  }

  @Test
  void verifyPasswordHashDoesNotComputeForPasswordsLongerThanTheMaximum() {
    User user = activeUserWithHash(currentHasher.hash("a".repeat(128)));
    long before = meterRegistry.get("user.password.hash.duration").timer().count();

    assertThat(service.verifyPasswordHash(user.getUserId(), "a".repeat(129))).isFalse();

    assertThat(meterRegistry.get("user.password.hash.duration").timer().count()).isEqualTo(before);
    assertThat(service.verifyPasswordHash(user.getUserId(), "a".repeat(128))).isTrue();
  }

  @Test
  void aCapacityFailurePropagatesToTheCallerWhichConvertsItToHttp() {
    PasswordHasher busy = mock(PasswordHasher.class);
    when(busy.verifyAndUpgrade(any(), any())).thenThrow(new HashCapacityExceededException("busy"));
    User user = activeUserWithHash(currentHasher.hash(PASSWORD));
    UserAccountLookupService guarded =
        new UserAccountLookupService(userRepository, permissionEngineApi, busy, transactionManager);

    assertThatThrownBy(() -> guarded.verifyPasswordHash(user.getUserId(), PASSWORD))
        .isInstanceOf(HashCapacityExceededException.class);
  }

  // ---- ログイン成功時のハッシュ更新 ----

  @Test
  void aSuccessfulLoginUpgradesAHashWithOldParametersExactlyOnce() {
    String oldHash = legacyHasher.hash(PASSWORD);
    User user = activeUserWithHash(oldHash);

    assertThat(service.verifyPasswordHash(user.getUserId(), PASSWORD)).isTrue();

    String upgraded = userRepository.findById(user.getUserId()).orElseThrow().getPasswordHash();
    assertThat(upgraded).isNotEqualTo(oldHash).startsWith("$argon2id$v=19$m=2048,t=2,p=1$");
    assertThat(currentHasher.verify(PASSWORD, upgraded)).isTrue();
    // 更新後は、古いパラメータではないため、再度の更新は起きない。
    assertThat(service.verifyPasswordHash(user.getUserId(), PASSWORD)).isTrue();
    assertThat(userRepository.findById(user.getUserId()).orElseThrow().getPasswordHash())
        .isEqualTo(upgraded);
  }

  @Test
  void aFailedLoginNeverUpgradesTheHash() {
    String oldHash = legacyHasher.hash(PASSWORD);
    User user = activeUserWithHash(oldHash);

    assertThat(service.verifyPasswordHash(user.getUserId(), "wrong password")).isFalse();

    assertThat(userRepository.findById(user.getUserId()).orElseThrow().getPasswordHash())
        .isEqualTo(oldHash);
  }

  @Test
  void theUpgradeIsSkippedWhenThePasswordHashChangedConcurrentlyButTheLoginStillSucceeds() {
    String oldHash = legacyHasher.hash(PASSWORD);
    User user = activeUserWithHash(oldHash);
    PasswordHasher racing = mock(PasswordHasher.class);
    // 検証の後、更新の前に、パスワードが変更された(passwordHashが別の値になった)状況を再現する。
    doAnswer(
            invocation -> {
              new TransactionTemplate(transactionManager)
                  .executeWithoutResult(
                      status ->
                          userRepository.updatePasswordHashIfUnchanged(
                              user.getUserId(), oldHash, "changed-by-password-reset"));
              return new PasswordHasher.VerifyResult(true, "upgraded-from-old-hash");
            })
        .when(racing)
        .verifyAndUpgrade(any(), any());
    UserAccountLookupService racingService =
        new UserAccountLookupService(
            userRepository, permissionEngineApi, racing, transactionManager);

    assertThat(racingService.verifyPasswordHash(user.getUserId(), PASSWORD)).isTrue();

    assertThat(userRepository.findById(user.getUserId()).orElseThrow().getPasswordHash())
        .isEqualTo("changed-by-password-reset");
  }

  @Test
  void aFailureOfTheUpgradeDoesNotAffectTheLoginResult() {
    UserRepository failing = mock(UserRepository.class);
    User user = User.activeAdmin("名前", "a@example.test", "stored-hash", List.of());
    when(failing.findById(user.getUserId())).thenReturn(Optional.of(user));
    when(failing.updatePasswordHashIfUnchanged(anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("db down"));
    PasswordHasher upgrading = mock(PasswordHasher.class);
    when(upgrading.verifyAndUpgrade(PASSWORD, "stored-hash"))
        .thenReturn(new PasswordHasher.VerifyResult(true, "upgraded-hash"));
    UserAccountLookupService failingService =
        new UserAccountLookupService(failing, permissionEngineApi, upgrading, transactionManager);

    assertThat(failingService.verifyPasswordHash(user.getUserId(), PASSWORD)).isTrue();
  }

  @Test
  void theUpgradeIsNotLostWhenCalledFromInsideAReadOnlyTransaction() {
    String oldHash = legacyHasher.hash(PASSWORD);
    User user = activeUserWithHash(oldHash);
    TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
    readOnly.setReadOnly(true);

    Boolean verified =
        readOnly.execute(status -> service.verifyPasswordHash(user.getUserId(), PASSWORD));

    assertThat(verified).isTrue();
    // 呼び出し元の読み取り専用のトランザクションが終わった後も、独立したトランザクションで確定した更新が残る。
    assertThat(userRepository.findById(user.getUserId()).orElseThrow().getPasswordHash())
        .isNotEqualTo(oldHash)
        .startsWith("$argon2id$v=19$m=2048,t=2,p=1$");
  }

  // ---- isDisabled ----

  @Test
  void isDisabledReflectsTheStatusAndTreatsUnknownUsersAsDisabled() {
    User active = activeUserWithHash(currentHasher.hash(PASSWORD));
    User invited =
        userRepository.saveAndFlush(
            UserTestFactory.invitedUser(
                UserTestFactory.uniqueEmail(), List.of(), UserTestFactory.newToken()));
    User disabled = activeUserWithHash(currentHasher.hash(PASSWORD));
    disabled.disable();
    userRepository.saveAndFlush(disabled);

    assertThat(service.isDisabled(active.getUserId())).isFalse();
    assertThat(service.isDisabled(invited.getUserId())).isFalse();
    assertThat(service.isDisabled(disabled.getUserId())).isTrue();
    assertThat(service.isDisabled("no-such-user")).isTrue();
    assertThat(service.isDisabled(null)).isTrue();
  }

  @Test
  void isDisabledAlwaysReadsTheLatestStatusEvenInsideAnOpenTransaction() {
    User user = activeUserWithHash(currentHasher.hash(PASSWORD));
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate independent = new TransactionTemplate(transactionManager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    Boolean disabledSeen =
        outer.execute(
            status -> {
              // 呼び出し元のトランザクションの永続化コンテキストに、activeのエンティティが読み込まれた状態。
              assertThat(userRepository.findById(user.getUserId()).orElseThrow().getStatus())
                  .isEqualTo(UserStatus.ACTIVE);
              independent.executeWithoutResult(
                  inner -> {
                    User other = userRepository.findById(user.getUserId()).orElseThrow();
                    other.disable();
                    userRepository.saveAndFlush(other);
                  });
              return service.isDisabled(user.getUserId());
            });

    assertThat(disabledSeen).isTrue();
  }
}
