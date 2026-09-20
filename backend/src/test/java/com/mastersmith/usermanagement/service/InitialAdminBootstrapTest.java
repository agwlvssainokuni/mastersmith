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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mastersmith.usermanagement.HashCapacityExceededException;
import com.mastersmith.usermanagement.config.InitialAdminProperties;
import com.mastersmith.usermanagement.config.UserManagementProperties;
import com.mastersmith.usermanagement.entity.FontSize;
import com.mastersmith.usermanagement.entity.Theme;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserPreference;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.event.UserChangeOperation;
import com.mastersmith.usermanagement.event.UserChangedEvent;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import com.mastersmith.usermanagement.security.HashConcurrencyLimiter;
import com.mastersmith.usermanagement.security.PasswordHasher;
import com.mastersmith.usermanagement.testsupport.EventRecorder;
import com.mastersmith.usermanagement.testsupport.UserTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link InitialAdminBootstrap}のテスト(W5、rules.md BR4.7、reliability-design.md NFR4.4):
 * 作成(active・roleIds・UserPreference既定値・BOOTSTRAPPED)・冪等
 * (存在すれば何もしない・ハッシュを計算しない)・一意制約違反は既存として扱う・roleIdの実在検証をしない・パスワードがログ・例外に出ないこと。
 * コミットを伴う検証のため、テストはトランザクションの外で実行し、作成したUserは後始末で削除する。
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InitialAdminBootstrapTest {

  private static final String PASSWORD = "initial-admin-passphrase";

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final EventRecorder events = new EventRecorder();
  private PasswordHasher hasher;
  private String email;

  @BeforeEach
  void setUp() {
    UserManagementProperties.Hash params =
        new UserManagementProperties.Hash(1024, 1, 1, 16, 32, 4, Duration.ofSeconds(5));
    hasher =
        new PasswordHasher(
            params,
            new HashConcurrencyLimiter(4, params.waitTimeout(), meterRegistry),
            meterRegistry);
    email = UserTestFactory.uniqueEmail();
  }

  @AfterEach
  void tearDown() {
    preferenceRepository.deleteAll();
    userRepository.deleteAll();
  }

  private InitialAdminBootstrap bootstrap(PasswordHasher passwordHasher, List<String> roleIds) {
    return new InitialAdminBootstrap(
        new InitialAdminProperties(
            " " + email.toUpperCase() + " ", PASSWORD, roleIds, "Administrator"),
        userRepository,
        preferenceRepository,
        transactionManager,
        passwordHasher,
        events.publisher());
  }

  @Test
  void createsAnActiveAdministratorWithHashedPasswordDefaultPreferenceAndABootstrappedEvent() {
    boolean created = bootstrap(hasher, List.of("role-admin", "role-admin", "role-x")).bootstrap();

    assertThat(created).isTrue();
    User admin = userRepository.findByEmail(email).orElseThrow();
    assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(admin.getName()).isEqualTo("Administrator");
    assertThat(admin.getInvitationToken()).isNull();
    // roleIdの実在検証は行わない(ロールは、後続のRBAC設定インポートで定義されるため、作成時点では未定義でありうる)。
    assertThat(admin.getRoleIds()).containsExactly("role-admin", "role-x");
    assertThat(admin.getPasswordHash()).startsWith("$argon2id$").doesNotContain(PASSWORD);
    assertThat(hasher.verify(PASSWORD, admin.getPasswordHash())).isTrue();
    UserPreference preference = preferenceRepository.findById(admin.getUserId()).orElseThrow();
    assertThat(preference.getTheme()).isEqualTo(Theme.LIGHT);
    assertThat(preference.getFontSize()).isEqualTo(FontSize.MEDIUM);
    assertThat(preference.getLocale()).isEqualTo(UiLocale.JA);

    UserChangedEvent event = events.single();
    assertThat(event.operation()).isEqualTo(UserChangeOperation.BOOTSTRAPPED);
    assertThat(event.actor()).isEqualTo("system");
    assertThat(event.beforeValue()).isNull();
    assertThat(event.afterValue().status()).isEqualTo("active");
    assertThat(event.afterValue().email()).isEqualTo(email);
    assertThat(event.targetId()).isEqualTo(admin.getUserId());
  }

  @Test
  void withoutRoleIdsTheAdministratorHasNoRoles() {
    bootstrap(hasher, List.of()).bootstrap();

    assertThat(userRepository.findByEmail(email).orElseThrow().getRoleIds()).isEmpty();
  }

  @Test
  void isIdempotentAndDoesNotComputeAHashWhenTheUserAlreadyExists() {
    PasswordHasher counting = mock(PasswordHasher.class);
    when(counting.hash(any())).thenReturn(UserTestFactory.DUMMY_PASSWORD_HASH);
    InitialAdminBootstrap bootstrap = bootstrap(counting, List.of());

    boolean first = bootstrap.bootstrap();
    boolean second = bootstrap.bootstrap();
    boolean third = bootstrap.bootstrap();

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    assertThat(third).isFalse();
    verify(counting, times(1)).hash(any());
    assertThat(userRepository.findAll()).filteredOn(u -> u.getEmail().equals(email)).hasSize(1);
    assertThat(events.events()).hasSize(1);
  }

  @Test
  void anExistingUserOfAnyStatusIsLeftUntouched() {
    User disabled =
        User.activeAdmin("元の名前", email, UserTestFactory.DUMMY_PASSWORD_HASH, List.of("keep"));
    disabled.disable();
    userRepository.saveAndFlush(disabled);
    PasswordHasher neverUsed = mock(PasswordHasher.class);

    boolean created = bootstrap(neverUsed, List.of("role-admin")).bootstrap();

    assertThat(created).isFalse();
    verify(neverUsed, never()).hash(any());
    User stored = userRepository.findByEmail(email).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(stored.getName()).isEqualTo("元の名前");
    assertThat(stored.getRoleIds()).containsExactly("keep");
    assertThat(events.events()).isEmpty();
  }

  @Test
  void aUniqueConstraintViolationFromAConcurrentStartupIsTreatedAsAlreadyExisting() {
    PasswordHasher racing = mock(PasswordHasher.class);
    // ハッシュ計算の間に、別のプロセスが、同じemailのUserを作成した状況を再現する。
    doAnswer(
            invocation -> {
              new TransactionTemplate(transactionManager)
                  .executeWithoutResult(
                      status ->
                          userRepository.saveAndFlush(
                              User.activeAdmin(
                                  "別のプロセス",
                                  email,
                                  UserTestFactory.DUMMY_PASSWORD_HASH,
                                  List.of())));
              return UserTestFactory.DUMMY_PASSWORD_HASH;
            })
        .when(racing)
        .hash(any());

    boolean created = bootstrap(racing, List.of()).bootstrap();

    assertThat(created).isFalse();
    assertThat(userRepository.findByEmail(email).orElseThrow().getName()).isEqualTo("別のプロセス");
    assertThat(preferenceRepository.count()).isZero();
    assertThat(events.events()).isEmpty();
  }

  @Test
  void aCapacityFailureAtStartupFailsTheStartupInsteadOfBeingSwallowed() {
    PasswordHasher busy = mock(PasswordHasher.class);
    when(busy.hash(any())).thenThrow(new HashCapacityExceededException("busy"));

    assertThatThrownBy(() -> bootstrap(busy, List.of()).run(null))
        .isInstanceOf(HashCapacityExceededException.class)
        .hasMessageNotContaining(PASSWORD);

    assertThat(userRepository.findByEmail(email)).isEmpty();
    assertThat(events.events()).isEmpty();
  }

  @Test
  void runDelegatesToBootstrap() {
    bootstrap(hasher, List.of()).run(null);

    assertThat(userRepository.findByEmail(email)).isPresent();
  }
}
