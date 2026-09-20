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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 起動時の設定検証: SMTPのホスト({@code spring.mail.host})が設定されていること(rules.md BR4.16: SMTP設定自体の不備は、起動時に検知して
 * fail fastする)。招待リンクのベースURLと送信元は{@link InvitationMailProperties}が、テンプレートは{@link
 * MailTemplateValidator}が検証する。
 *
 * <p>SMTP接続の暗号化(STARTTLS・SMTPS)の有無は、検査しない(環境ごとの設定に任せる。security-design.md NFR2.7、Q2=B)。
 */
@Component
public class MailSettingsValidator implements InitializingBean {

  private final Environment environment;

  public MailSettingsValidator(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    String host = environment.getProperty("spring.mail.host");
    if (host == null || host.isBlank()) {
      throw new IllegalStateException(
          "spring.mail.host must be set (SMTP server for invitation mail)");
    }
  }
}
