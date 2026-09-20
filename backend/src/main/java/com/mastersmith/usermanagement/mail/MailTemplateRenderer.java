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

import cherry.mustache.Mustache;
import cherry.mustache.Template;
import com.mastersmith.usermanagement.entity.UiLocale;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 自作mustacheエンジン(cherry-mustache-core)へのアダプタ。言語(ja/en)ごとのHTMLテンプレートを{@code mail/}から読み、招待メールのHTML
 * 本文を生成する(NFR2.7・NFR7.1)。テンプレートは起動時にすべて読み込み、コンパイルする(欠落・構文の不備は起動を失敗させる)。
 *
 * <p><b>HTMLエスケープ</b>: エンジンは、公式のMustache仕様に従い、{@code {{変数}}}の値を、既定でHTMLエスケープする({@code & < > "}。
 * シングルクォートはエスケープしない)。したがって本アダプタは、差し込む値を、事前にエスケープしない(二重エスケープを避ける)。テンプレートは、
 * 属性値をダブルクォートで囲み、エスケープなしの展開({@code {{{ }}})を使わない。
 */
@Component
public class MailTemplateRenderer {

  private static final String DEFAULT_DIRECTORY = "mail";

  private final Map<UiLocale, Template> templates = new EnumMap<>(UiLocale.class);

  public MailTemplateRenderer() {
    this(DEFAULT_DIRECTORY);
  }

  /** テンプレートの置き場所を指定する(テスト用)。 */
  MailTemplateRenderer(String resourceDirectory) {
    for (UiLocale locale : UiLocale.values()) {
      templates.put(locale, load(resourceDirectory, locale));
    }
  }

  /**
   * 招待メールのHTML本文を生成する。
   *
   * @param locale 招待メールの言語(テンプレートの選択)
   * @param name 招待されたユーザーの氏名(エンジンがHTMLエスケープして差し込む)
   * @param inviteLink 招待リンク
   */
  public String render(UiLocale locale, String name, String inviteLink) {
    return templates.get(locale).render(Map.of("name", name, "inviteLink", inviteLink));
  }

  private static Template load(String directory, UiLocale locale) {
    String path = "%s/invitation_%s.html".formatted(directory, locale.value());
    try (InputStream in = MailTemplateRenderer.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Invitation mail template not found: " + path);
      }
      return Mustache.compile(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read invitation mail template: " + path, e);
    }
  }
}
