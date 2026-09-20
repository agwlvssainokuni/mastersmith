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

package com.mastersmith.usermanagement.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * {@link InitialAdminProperties}の安全失敗のテスト(team.md必須テスト種別(a)、rules.md BR4.7、security-design.md
 * NFR2.11): 未設定・パスワードの長さの違反・emailの形式不正は、 プロパティのバインド時に検知して起動を失敗させる。失敗のメッセージには、パスワードを含めない。
 */
class InitialAdminPropertiesTest {

  /** エラーメッセージに現れない、特徴的な文字の並び。 */
  private static final String PATTERN = "Zq9@";

  @Configuration
  @EnableConfigurationProperties(InitialAdminProperties.class)
  static class PropertiesConfig {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

  private static String messages(Throwable failure) {
    StringBuilder text = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      text.append(t.getMessage()).append('\n');
    }
    return text.toString();
  }

  @Test
  void validSettingsAreBoundWithTheEmailNormalizedAndRoleIdsParsed() {
    runner
        .withPropertyValues(
            "mastersmith.users.initial-admin.email=  Admin@Example.TEST ",
            "mastersmith.users.initial-admin.password=valid-passphrase",
            "mastersmith.users.initial-admin.role-ids=role-a, role-b ,role-a")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              InitialAdminProperties properties = context.getBean(InitialAdminProperties.class);
              assertThat(properties.email()).isEqualTo("admin@example.test");
              assertThat(properties.roleIds()).containsExactly("role-a", "role-b");
              assertThat(properties.name()).isEqualTo("Administrator");
              assertThat(properties.toString()).doesNotContain("valid-passphrase");
            });
  }

  @Test
  void anEmptyRoleIdsSettingMeansNoRoles() {
    runner
        .withPropertyValues(
            "mastersmith.users.initial-admin.email=admin@example.test",
            "mastersmith.users.initial-admin.password=valid-passphrase",
            "mastersmith.users.initial-admin.role-ids=")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(InitialAdminProperties.class).roleIds()).isEmpty();
            });
  }

  @Test
  void failsToStartWhenNothingIsConfigured() {
    runner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsToStartWithoutAPassword() {
    runner
        .withPropertyValues("mastersmith.users.initial-admin.email=admin@example.test")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(messages(context.getStartupFailure())).contains("initial-admin.password");
            });
  }

  @Test
  void failsToStartWithoutAnEmail() {
    runner
        .withPropertyValues("mastersmith.users.initial-admin.password=valid-passphrase")
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest(name = "{0}文字のパスワード")
  @ValueSource(ints = {1, 7, 129, 500})
  void failsToStartWhenThePasswordLengthViolatesThePolicyWithoutLeakingIt(int length) {
    String password = PATTERN.repeat(length).substring(0, length);

    runner
        .withPropertyValues(
            "mastersmith.users.initial-admin.email=admin@example.test",
            "mastersmith.users.initial-admin.password=" + password)
        .run(
            context -> {
              assertThat(context).hasFailed();
              String text = messages(context.getStartupFailure());
              assertThat(text).contains("initial-admin.password").doesNotContain(password);
            });
  }

  @Test
  void aPasswordOfTheBoundaryLengthsIsAccepted() {
    for (int length : new int[] {8, 128}) {
      runner
          .withPropertyValues(
              "mastersmith.users.initial-admin.email=admin@example.test",
              "mastersmith.users.initial-admin.password="
                  + PATTERN.repeat(length).substring(0, length))
          .run(context -> assertThat(context).hasNotFailed());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-email", "a@", "@b.test", "a b@example.test", "a@b@c.test"})
  void failsToStartWithAnInvalidEmail(String email) {
    runner
        .withPropertyValues(
            "mastersmith.users.initial-admin.email=" + email,
            "mastersmith.users.initial-admin.password=valid-passphrase")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(messages(context.getStartupFailure()))
                  .contains("initial-admin.email")
                  .doesNotContain("valid-passphrase");
            });
  }

  @Test
  void failsToStartWhenAnEnvironmentPlaceholderIsUnresolved() {
    // application.ymlは、環境変数のプレースホルダ(既定値なし)で与える。環境変数が未設定なら、起動時に失敗する。
    runner
        .withPropertyValues(
            "mastersmith.users.initial-admin.email=${MASTERSMITH_TEST_UNSET_ADMIN_EMAIL}",
            "mastersmith.users.initial-admin.password=${MASTERSMITH_TEST_UNSET_ADMIN_PASSWORD}")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsToStartWithAnInvalidAdministratorName() {
    runner
        .withPropertyValues(
            "mastersmith.users.initial-admin.email=admin@example.test",
            "mastersmith.users.initial-admin.password=valid-passphrase",
            "mastersmith.users.initial-admin.name= ")
        .run(context -> assertThat(context).hasFailed());
  }
}
