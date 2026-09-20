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

package com.mastersmith.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.MastersmithApplication;
import com.mastersmith.permission.PermissionEngineApi;
import com.mastersmith.usermanagement.entity.User;
import com.mastersmith.usermanagement.entity.UserStatus;
import com.mastersmith.usermanagement.repository.UserPreferenceRepository;
import com.mastersmith.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * user-managementの、アプリケーション全体の起動時の結線の確認(統合テスト):
 * 設定プロパティのバインド・Beanの結線・起動時の初期管理者の自動作成(W5)・C11が実際のBeanで動く こと。テスト用の{@code
 * application.yml}のダミー値(実在しない値)で、起動時のfail fast検証を通る。
 */
@SpringBootTest(classes = MastersmithApplication.class)
class UserManagementStartupTest {

  @Autowired private UserRepository userRepository;
  @Autowired private UserPreferenceRepository preferenceRepository;
  @Autowired private UserAccountLookupApi userAccountLookupApi;
  @Autowired private PermissionEngineApi permissionEngineApi;

  @Test
  void theInitialAdministratorIsCreatedAtStartupFromTheConfiguration() {
    User admin = userRepository.findByEmail("initial-admin@example.test").orElseThrow();

    assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(admin.getPasswordHash()).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    assertThat(preferenceRepository.findById(admin.getUserId())).isPresent();
  }

  @Test
  void theC11ApiIsWiredAndAuthenticatesTheInitialAdministrator() {
    UserAccount account =
        userAccountLookupApi.findByEmail("  Initial-Admin@Example.TEST ").orElseThrow();

    assertThat(account.passwordHash()).isNull();
    assertThat(
            userAccountLookupApi.verifyPasswordHash(
                account.userId(), "dummy-initial-admin-passphrase"))
        .isTrue();
    assertThat(userAccountLookupApi.verifyPasswordHash(account.userId(), "wrong-passphrase"))
        .isFalse();
    assertThat(userAccountLookupApi.isDisabled(account.userId())).isFalse();
    assertThat(userAccountLookupApi.isDisabled("no-such-user")).isTrue();
  }

  @Test
  void theRealPermissionEngineAnswersRoleExists() {
    assertThat(permissionEngineApi.roleExists("no-such-role")).isFalse();
  }
}
