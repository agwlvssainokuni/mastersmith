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

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 招待メールの設定({@code mastersmith.mail.*}、security-design.md NFR2.7)。設定の不備は、バインド時に検知して起動を失敗させる
 * (project.md Mandated: 設定定義自体の誤りはfail fastする)。
 *
 * @param baseUrl 招待リンクのベースURL。リクエストのHostなどのヘッダーからは導出しない。HTTPSを必須とし、httpは{@code
 *     allowInsecureLink}がtrueの場合(開発のMailpit環境向け)に限って許可する
 * @param from 送信元のメールアドレス
 * @param allowInsecureLink httpのベースURLを許可するか(既定false)
 */
@ConfigurationProperties("mastersmith.mail")
public record InvitationMailProperties(
    String baseUrl, String from, @DefaultValue("false") boolean allowInsecureLink) {

  public InvitationMailProperties {
    baseUrl = normalizeBaseUrl(baseUrl, allowInsecureLink);
    requireValidFrom(from);
  }

  private static String normalizeBaseUrl(String baseUrl, boolean allowInsecureLink) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("mastersmith.mail.base-url must be set");
    }
    URI uri;
    try {
      uri = new URI(baseUrl.strip());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("mastersmith.mail.base-url is not a valid URL");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    boolean schemeAllowed = scheme.equals("https") || (allowInsecureLink && scheme.equals("http"));
    if (!schemeAllowed) {
      throw new IllegalArgumentException(
          "mastersmith.mail.base-url must use https (http is allowed only when"
              + " mastersmith.mail.allow-insecure-link=true)");
    }
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null) {
      throw new IllegalArgumentException(
          "mastersmith.mail.base-url must have a host and no user info, query or fragment");
    }
    String text = uri.toString();
    while (text.endsWith("/")) {
      text = text.substring(0, text.length() - 1);
    }
    return text;
  }

  private static void requireValidFrom(String from) {
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("mastersmith.mail.from must be set");
    }
    try {
      new InternetAddress(from, true).validate();
    } catch (AddressException e) {
      throw new IllegalArgumentException("mastersmith.mail.from is not a valid email address");
    }
  }
}
