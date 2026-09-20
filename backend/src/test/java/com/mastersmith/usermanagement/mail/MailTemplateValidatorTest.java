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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link MailTemplateValidator}:
 * 全言語のテンプレートに、件名(&lt;title&gt;)を取り出せることを、起動時に確認する(安全失敗、NFR2.7・NFR4.4)。
 */
class MailTemplateValidatorTest {

  private static MailTemplateValidator validatorFor(MailTemplateRenderer renderer) {
    return new MailTemplateValidator(renderer, new SubjectExtractor());
  }

  @Test
  void acceptsTheShippedTemplates() {
    assertThatCode(() -> validatorFor(new MailTemplateRenderer()).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void failsWhenATemplateHasNoTitle() {
    assertThatThrownBy(
            () -> validatorFor(new MailTemplateRenderer("mail-notitle")).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MISSING_TITLE");
  }

  @Test
  void failsWhenATitleHasAnUndecodableReference() {
    assertThatThrownBy(
            () -> validatorFor(new MailTemplateRenderer("mail-badtitle")).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("UNSUPPORTED_REFERENCE");
  }

  @Configuration
  static class NoTitleConfig {
    @Bean
    MailTemplateRenderer renderer() {
      return new MailTemplateRenderer("mail-notitle");
    }

    @Bean
    SubjectExtractor subjectExtractor() {
      return new SubjectExtractor();
    }

    @Bean
    MailTemplateValidator validator(MailTemplateRenderer renderer, SubjectExtractor extractor) {
      return new MailTemplateValidator(renderer, extractor);
    }
  }

  @Configuration
  static class ShippedConfig {
    @Bean
    MailTemplateRenderer renderer() {
      return new MailTemplateRenderer();
    }

    @Bean
    SubjectExtractor subjectExtractor() {
      return new SubjectExtractor();
    }

    @Bean
    MailTemplateValidator validator(MailTemplateRenderer renderer, SubjectExtractor extractor) {
      return new MailTemplateValidator(renderer, extractor);
    }
  }

  @Test
  void theApplicationContextFailsToStartWhenATemplateHasNoTitle() {
    new ApplicationContextRunner()
        .withUserConfiguration(NoTitleConfig.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(Exception.class);
              assertThat(rootMessages(context.getStartupFailure())).contains("MISSING_TITLE");
            });
  }

  @Test
  void theApplicationContextStartsWithTheShippedTemplates() {
    new ApplicationContextRunner()
        .withUserConfiguration(ShippedConfig.class)
        .run(context -> assertThat(context).hasNotFailed());
  }

  private static String rootMessages(Throwable failure) {
    StringBuilder messages = new StringBuilder();
    for (Throwable t = failure; t != null; t = t.getCause()) {
      messages.append(t.getMessage()).append('\n');
    }
    return messages.toString();
  }
}
