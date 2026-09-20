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

package com.mastersmith.usermanagement.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 招待メールの設定の検証(安全失敗、team.md必須テスト種別(a)): SMTPのホスト未設定、招待リンクのベースURLがhttpでinsecure許可なし、ベースURL・送信元の不備で、
 * 起動が失敗すること(rules.md BR4.16、security-design.md NFR2.7)。
 */
class MailSettingsValidationTest {

  @Configuration
  @EnableConfigurationProperties(InvitationMailProperties.class)
  static class PropertiesConfig {}

  private final ApplicationContextRunner propertiesRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

  private final ApplicationContextRunner smtpRunner =
      new ApplicationContextRunner().withUserConfiguration(MailSettingsValidator.class);

  private static String rootMessages(Throwable failure) {
    StringBuilder messages = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      messages.append(t.getMessage()).append('\n');
    }
    return messages.toString();
  }

  @Test
  void acceptsAnHttpsBaseUrlAndNormalizesTrailingSlashes() {
    propertiesRunner
        .withPropertyValues(
            "mastersmith.mail.base-url=https://app.example.test//",
            "mastersmith.mail.from=no-reply@example.test")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              InvitationMailProperties properties = context.getBean(InvitationMailProperties.class);
              assertThat(properties.baseUrl()).isEqualTo("https://app.example.test");
              assertThat(properties.allowInsecureLink()).isFalse();
            });
  }

  @Test
  void rejectsAnHttpBaseUrlByDefault() {
    propertiesRunner
        .withPropertyValues(
            "mastersmith.mail.base-url=http://localhost:8080",
            "mastersmith.mail.from=no-reply@example.test")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(rootMessages(context.getStartupFailure())).contains("must use https");
            });
  }

  @Test
  void allowsAnHttpBaseUrlOnlyWhenInsecureLinksAreExplicitlyAllowed() {
    propertiesRunner
        .withPropertyValues(
            "mastersmith.mail.base-url=http://localhost:8025",
            "mastersmith.mail.allow-insecure-link=true",
            "mastersmith.mail.from=no-reply@example.test")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "ftp://app.example.test",
        "javascript:alert(1)",
        "app.example.test",
        "https://user:pw@app.example.test",
        "https://app.example.test/?q=1",
        "https://app.example.test/#frag",
        "https://"
      })
  void rejectsInvalidBaseUrls(String baseUrl) {
    propertiesRunner
        .withPropertyValues(
            "mastersmith.mail.base-url=" + baseUrl, "mastersmith.mail.from=no-reply@example.test")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsAMissingBaseUrl() {
    propertiesRunner
        .withPropertyValues("mastersmith.mail.from=no-reply@example.test")
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not-an-address", "a@b, c@d", "Name <>"})
  void rejectsAnInvalidSenderAddress(String from) {
    propertiesRunner
        .withPropertyValues(
            "mastersmith.mail.base-url=https://app.example.test", "mastersmith.mail.from=" + from)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsToStartWithoutTheSmtpHost() {
    smtpRunner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(rootMessages(context.getStartupFailure())).contains("spring.mail.host");
        });
  }

  @Test
  void failsToStartWithABlankSmtpHost() {
    smtpRunner
        .withPropertyValues("spring.mail.host=   ")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void startsWithAnSmtpHostAndDoesNotRequireEncryption() {
    smtpRunner
        .withPropertyValues("spring.mail.host=localhost")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
