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

import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.exception.SubjectRejectedException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 起動時に、全言語のテンプレートを、サンプルの値でレンダリングし、{@link SubjectExtractor}で件名を取り出せることを確認する。取り出せなければ、起動を
 * 失敗させる(テンプレートは設定定義にあたるため、fail fastする。project.md Mandated、NFR2.7・NFR4.4)。
 */
@Component
public class MailTemplateValidator implements InitializingBean {

  private static final String SAMPLE_NAME = "Sample <User> & \"Name\"";
  private static final String SAMPLE_LINK =
      "https://sample.example.test/invitations/accept#token=00000000-0000-0000-0000-000000000000";

  private final MailTemplateRenderer renderer;
  private final SubjectExtractor subjectExtractor;

  public MailTemplateValidator(MailTemplateRenderer renderer, SubjectExtractor subjectExtractor) {
    this.renderer = renderer;
    this.subjectExtractor = subjectExtractor;
  }

  @Override
  public void afterPropertiesSet() {
    for (UiLocale locale : UiLocale.values()) {
      try {
        subjectExtractor.extract(renderer.render(locale, SAMPLE_NAME, SAMPLE_LINK));
      } catch (SubjectRejectedException e) {
        throw new IllegalStateException(
            "Invitation mail template '%s' has no usable subject (<title>): %s"
                .formatted(locale.value(), e.getRejection()),
            e);
      } catch (RuntimeException e) {
        throw new IllegalStateException(
            "Invitation mail template '%s' cannot be rendered".formatted(locale.value()), e);
      }
    }
  }
}
