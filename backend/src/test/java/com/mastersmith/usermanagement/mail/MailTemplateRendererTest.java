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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.usermanagement.entity.UiLocale;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@link MailTemplateRenderer}: 言語ごとのテンプレートの選択、HTMLエスケープ(エンジンの既定の挙動の確認を含む)、リンクの差し込み(NFR2.7・NFR7.1)。
 */
class MailTemplateRendererTest {

  private static final String LINK =
      "https://app.example.test/invitations/accept#token=11111111-2222-3333-4444-555555555555";

  private final MailTemplateRenderer renderer = new MailTemplateRenderer();

  @Test
  void selectsTheTemplateByLocale() {
    String ja = renderer.render(UiLocale.JA, "太郎", LINK);
    String en = renderer.render(UiLocale.EN, "Taro", LINK);

    assertThat(ja).contains("<html lang=\"ja\">").contains("太郎 様").contains("ユーザー招待のご案内");
    assertThat(en)
        .contains("<html lang=\"en\">")
        .contains("Dear Taro,")
        .contains("You have been invited");
  }

  @Test
  void defaultLocaleIsJapanese() {
    assertThat(UiLocale.DEFAULT).isEqualTo(UiLocale.JA);
  }

  @Test
  void theEngineHtmlEscapesInterpolatedValuesByDefault() {
    String html = renderer.render(UiLocale.EN, "<script>alert(1)</script> & \"q\"", LINK);

    // 氏名に含まれるHTML特殊文字(& < > ")は、エンジンの既定のエスケープで無害化される(二重エスケープもされない)。
    assertThat(html)
        .doesNotContain("<script>")
        .contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;q&quot;")
        .doesNotContain("&amp;lt;");
  }

  @Test
  void injectedTitleMarkupInTheNameCannotCreateATitleElement() {
    String html = renderer.render(UiLocale.EN, "</title><title>evil", LINK);

    assertThat(html).doesNotContain("<title>evil");
    assertThat(new SubjectExtractor().extract(html))
        .isEqualTo("[MasterSmith] You have been invited");
  }

  @Test
  void embedsTheInvitationLinkWithTheTokenInTheFragment() {
    String html = renderer.render(UiLocale.JA, "太郎", LINK);

    assertThat(html).contains("<a href=\"" + LINK + "\">" + LINK + "</a>");
    assertThat(LINK).contains("/invitations/accept#token=");
  }

  @Test
  void templatesDoNotUseSingleQuotedAttributesOrUnescapedVariables() throws IOException {
    // エンジンはシングルクォートをエスケープしないため、属性値はダブルクォートで囲む。エスケープなしの展開({{{ }})も使わない。
    for (String name : new String[] {"invitation_ja.html", "invitation_en.html"}) {
      try (var in = getClass().getClassLoader().getResourceAsStream("mail/" + name)) {
        String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(Pattern.compile("=\\s*'").matcher(template).find()).as(name).isFalse();
        assertThat(template).as(name).doesNotContain("{{{").doesNotContain("{{&");
      }
    }
  }

  @Test
  void failsFastWhenATemplateIsMissing() {
    assertThatThrownBy(() -> new MailTemplateRenderer("mail-does-not-exist"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("template not found");
  }
}
