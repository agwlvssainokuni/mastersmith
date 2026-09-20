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

import com.mastersmith.usermanagement.exception.SubjectRejectedException;
import com.mastersmith.usermanagement.exception.SubjectRejectedException.Rejection;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** {@link SubjectExtractor}: 件名の抽出、文字参照のデコード、デコード後の制御文字・長さ・欠落の拒否(NFR2.7、NFR8.2)。 */
class SubjectExtractorTest {

  private static final String EMOJI = "😀";

  /** 不可視の文字(U+00A0)を、ソース上で明示する。 */
  private static final String NBSP = String.valueOf((char) 0xA0);

  private final SubjectExtractor extractor = new SubjectExtractor();

  private static String page(String title) {
    return "<html><head><title>" + title + "</title></head><body>x</body></html>";
  }

  private static void assertRejected(SubjectExtractor extractor, String html, Rejection expected) {
    assertThatThrownBy(() -> extractor.extract(html))
        .isInstanceOfSatisfying(
            SubjectRejectedException.class, e -> assertThat(e.getRejection()).isEqualTo(expected));
  }

  static Stream<Arguments> decodableTitles() {
    return Stream.of(
        Arguments.of("plain", "Hello MasterSmith", "Hello MasterSmith"),
        Arguments.of("japanese", "【MasterSmith】ご案内", "【MasterSmith】ご案内"),
        Arguments.of("amp", "R&amp;D", "R&D"),
        Arguments.of("lt-gt-quot", "&lt;a&gt; &quot;b&quot;", "<a> \"b\""),
        Arguments.of("apos", "it&apos;s", "it's"),
        Arguments.of("numeric-decimal", "A&#66;C", "ABC"),
        Arguments.of("numeric-hex-lower-x", "A&#x42;C", "ABC"),
        Arguments.of("numeric-hex-upper-x", "A&#X42;C", "ABC"),
        Arguments.of("numeric-leading-zeros", "A&#0066;C", "ABC"),
        Arguments.of("numeric-supplementary-plane", "&#x1F600;", EMOJI),
        Arguments.of("named-hellip", "Wait&hellip;", "Wait…"),
        Arguments.of("named-nbsp-inside", "A&nbsp;B", "A" + NBSP + "B"),
        Arguments.of("decoded-only-once", "&amp;lt;", "&lt;"),
        Arguments.of("bare-ampersand-is-literal", "R&D", "R&D"),
        Arguments.of("reference-without-semicolon-is-literal", "&#66 x", "&#66 x"),
        Arguments.of("surrounding-spaces-are-stripped", "   spaced  ", "spaced"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("decodableTitles")
  void extractsAndDecodesTheSubject(String label, String title, String expected) {
    assertThat(extractor.extract(page(title))).isEqualTo(expected);
  }

  static Stream<Arguments> controlCharacterTitles() {
    return Stream.of(
        Arguments.of("lf-decimal-reference", "ok&#10;Bcc: evil@example.test"),
        Arguments.of("cr-decimal-reference", "ok&#13;more"),
        Arguments.of("crlf-hex-references", "ok&#x0d;&#x0a;more"),
        Arguments.of("lf-uppercase-x-reference", "ok&#X0A;more"),
        Arguments.of("lf-leading-zeros", "ok&#000010;more"),
        Arguments.of("tab-reference", "ok&#9;more"),
        Arguments.of("c1-control-reference", "ok&#133;more"),
        Arguments.of("line-separator-reference", "ok&#8232;more"),
        Arguments.of("paragraph-separator-reference", "ok&#x2029;more"),
        Arguments.of("literal-lf", "Line\nBreak"),
        Arguments.of("literal-cr", "Line\rBreak"),
        Arguments.of("literal-crlf", "Line\r\nBreak"),
        Arguments.of("leading-newline-is-not-stripped", "\nSubject"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("controlCharacterTitles")
  void rejectsControlCharactersAfterDecodingWithoutStrippingThem(String label, String title) {
    assertRejected(extractor, page(title), Rejection.CONTROL_CHARACTER);
  }

  static Stream<Arguments> lengths() {
    return Stream.of(
        Arguments.of(1, true),
        Arguments.of(199, true),
        Arguments.of(200, true),
        Arguments.of(201, false),
        Arguments.of(1000, false));
  }

  @ParameterizedTest(name = "{0}文字: 受理={1}")
  @MethodSource("lengths")
  void enforcesTheMaximumSubjectLength(int length, boolean accepted) {
    String title = "a".repeat(length);

    if (accepted) {
      assertThat(extractor.extract(page(title))).hasSize(length);
    } else {
      assertRejected(extractor, page(title), Rejection.TOO_LONG);
    }
  }

  @ParameterizedTest(name = "サロゲートペア{0}文字: 受理={1}")
  @MethodSource("codePointLengths")
  void countsCodePointsForTheLengthLimit(int codePoints, boolean accepted) {
    String title = EMOJI.repeat(codePoints);

    if (accepted) {
      assertThat(extractor.extract(page(title))).isEqualTo(title);
    } else {
      assertRejected(extractor, page(title), Rejection.TOO_LONG);
    }
  }

  static Stream<Arguments> codePointLengths() {
    return Stream.of(Arguments.of(200, true), Arguments.of(201, false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void rejectsAMissingTitleForNullAndEmptyHtml(String html) {
    assertRejected(extractor, html, Rejection.MISSING_TITLE);
  }

  @org.junit.jupiter.api.Test
  void rejectsHtmlWithoutATitleElement() {
    assertRejected(
        extractor, "<html><head></head><body>no title</body></html>", Rejection.MISSING_TITLE);
  }

  @ParameterizedTest
  @MethodSource("emptyTitles")
  void rejectsAnEmptyOrBlankTitle(String title) {
    assertRejected(extractor, page(title), Rejection.EMPTY_TITLE);
  }

  static Stream<String> emptyTitles() {
    return Stream.of("", "   ", "&#32;&#32;");
  }

  @org.junit.jupiter.api.Test
  void aNonBreakingSpaceOnlyTitleIsNotEmpty() {
    assertThat(extractor.extract(page("&nbsp;"))).isEqualTo(NBSP);
  }

  static Stream<Arguments> unsupportedReferences() {
    return Stream.of(
        Arguments.of("unknown-named-reference", "a&unknownref;b"),
        Arguments.of("null-code-point", "a&#0;b"),
        Arguments.of("beyond-unicode", "a&#x110000;b"),
        Arguments.of("surrogate-code-point", "a&#xD800;b"),
        Arguments.of("overflowing-number", "a&#99999999999999999999;b"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unsupportedReferences")
  void rejectsUnsupportedReferencesInsteadOfLeavingThemUndecoded(String label, String title) {
    assertRejected(extractor, page(title), Rejection.UNSUPPORTED_REFERENCE);
  }

  @org.junit.jupiter.api.Test
  void findsTheTitleElementCaseInsensitivelyAndWithAttributes() {
    assertThat(extractor.extract("<html><head><TITLE>Upper</TITLE></head></html>"))
        .isEqualTo("Upper");
    assertThat(extractor.extract("<head><title lang=\"en\">Attr</title ></head>"))
        .isEqualTo("Attr");
  }

  @org.junit.jupiter.api.Test
  void theExceptionMessageDoesNotContainTheSubject() {
    assertThatThrownBy(() -> extractor.extract(page("secret-subject&#10;x")))
        .hasMessageNotContaining("secret-subject");
  }
}
