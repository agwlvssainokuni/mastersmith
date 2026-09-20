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

package com.mastersmith.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.usermanagement.entity.FontSize;
import com.mastersmith.usermanagement.entity.Theme;
import com.mastersmith.usermanagement.entity.UiLocale;
import com.mastersmith.usermanagement.exception.UserFieldError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link UserInputValidator}: email・name・roleIds・パスワード・表示設定の検証と正規化(rules.md
 * BR4.15とその追補、BR4.2・BR4.3、NFR7.2)。
 */
class UserInputValidatorTest {

  private static final String EMOJI = "😀";

  private final List<UserFieldError> errors = new ArrayList<>();

  private List<String> messages() {
    return errors.stream().map(UserFieldError::message).toList();
  }

  static Stream<Arguments> validEmails() {
    return Stream.of(
        Arguments.of("  Alice@Example.TEST  ", "alice@example.test"),
        Arguments.of("a.b+c@sub.example.test", "a.b+c@sub.example.test"),
        Arguments.of("admin@localhost", "admin@localhost"),
        Arguments.of("UPPER@EXAMPLE.TEST", "upper@example.test"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validEmails")
  void normalizesValidEmailsByTrimmingAndLowercasing(String input, String expected) {
    assertThat(UserInputValidator.normalizeEmail(input, errors)).isEqualTo(expected);
    assertThat(errors).isEmpty();
  }

  static Stream<Arguments> invalidEmails() {
    return Stream.of(
        Arguments.of(null, "user.validation.email.required"),
        Arguments.of("", "user.validation.email.required"),
        Arguments.of("   ", "user.validation.email.required"),
        Arguments.of("no-at-sign", "user.validation.email.invalid"),
        Arguments.of("@example.test", "user.validation.email.invalid"),
        Arguments.of("user@", "user.validation.email.invalid"),
        Arguments.of("a b@example.test", "user.validation.email.invalid"),
        Arguments.of("a@b@example.test", "user.validation.email.invalid"),
        Arguments.of("a@example.test, b@example.test", "user.validation.email.invalid"),
        Arguments.of("a@example.test\r\nBcc: x@example.test", "user.validation.email.invalid"),
        Arguments.of("<a@example.test>", "user.validation.email.invalid"),
        Arguments.of("a@-example.test", "user.validation.email.invalid"),
        Arguments.of("a".repeat(250) + "@example.test", "user.validation.email.tooLong"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("invalidEmails")
  void rejectsInvalidEmailsWithAFieldLevelKey(String input, String expectedKey) {
    assertThat(UserInputValidator.normalizeEmail(input, errors)).isNull();
    assertThat(messages()).containsExactly(expectedKey);
    assertThat(errors.get(0).field()).isEqualTo("email");
  }

  static Stream<Arguments> names() {
    return Stream.of(
        Arguments.of("  山田 太郎  ", "山田 太郎", null),
        Arguments.of("a".repeat(100), "a".repeat(100), null),
        Arguments.of(EMOJI.repeat(100), EMOJI.repeat(100), null),
        Arguments.of("a".repeat(101), null, "user.validation.name.tooLong"),
        Arguments.of(EMOJI.repeat(101), null, "user.validation.name.tooLong"),
        Arguments.of(null, null, "user.validation.name.required"),
        Arguments.of("", null, "user.validation.name.required"),
        Arguments.of("   ", null, "user.validation.name.required"),
        Arguments.of("a\nb", null, "user.validation.name.controlCharacter"),
        Arguments.of("a\rb", null, "user.validation.name.controlCharacter"),
        Arguments.of("a\r\nBcc: x", null, "user.validation.name.controlCharacter"),
        Arguments.of("a\tb", null, "user.validation.name.controlCharacter"),
        Arguments.of("\nleading", null, "user.validation.name.controlCharacter"),
        Arguments.of("a b", null, "user.validation.name.controlCharacter"));
  }

  @ParameterizedTest
  @MethodSource("names")
  void validatesAndTrimsTheName(String input, String expected, String expectedKey) {
    String normalized = UserInputValidator.normalizeName(input, errors);

    assertThat(normalized).isEqualTo(expected);
    if (expectedKey == null) {
      assertThat(errors).isEmpty();
    } else {
      assertThat(messages()).containsExactly(expectedKey);
    }
  }

  @Test
  void theTooLongErrorCarriesTheLimitAsAParameter() {
    UserInputValidator.normalizeName("a".repeat(101), errors);

    assertThat(errors.get(0).params()).containsEntry("max", 100);
  }

  @Test
  void roleIdsAreDeduplicatedKeepingTheOrderAndStripped() {
    List<String> normalized =
        UserInputValidator.normalizeRoleIds(
            Arrays.asList("b", " a ", "b", "c", "a"), false, errors);

    assertThat(normalized).containsExactly("b", "a", "c");
    assertThat(errors).isEmpty();
  }

  @Test
  void nullRoleIdsAreEmptyWhenOptionalAndAnErrorWhenRequired() {
    assertThat(UserInputValidator.normalizeRoleIds(null, false, errors)).isEmpty();
    assertThat(errors).isEmpty();

    assertThat(UserInputValidator.normalizeRoleIds(null, true, errors)).isNull();
    assertThat(messages()).containsExactly("user.validation.roleIds.required");
  }

  @Test
  void blankOrTooLongRoleIdsAreRejected() {
    assertThat(UserInputValidator.normalizeRoleIds(Arrays.asList("ok", " ", null), false, errors))
        .isNull();
    assertThat(messages())
        .containsExactly("user.validation.roleIds.blank", "user.validation.roleIds.blank");

    errors.clear();
    assertThat(UserInputValidator.normalizeRoleIds(List.of("r".repeat(256)), false, errors))
        .isNull();
    assertThat(messages()).containsExactly("user.validation.roleIds.tooLong");
  }

  @ParameterizedTest(name = "{0}文字: 有効={1}")
  @MethodSource("passwordLengths")
  void validatesThePasswordLengthWithoutExposingTheValue(int length, boolean valid) {
    boolean result = UserInputValidator.validatePassword("p".repeat(length), errors);

    assertThat(result).isEqualTo(valid);
    if (!valid) {
      assertThat(messages()).containsExactly("user.validation.password.length");
      assertThat(errors.get(0).params()).containsEntry("min", 8).containsEntry("max", 128);
      assertThat(errors.toString()).doesNotContain("ppp");
    }
  }

  static Stream<Arguments> passwordLengths() {
    return Stream.of(
        Arguments.of(1, false),
        Arguments.of(7, false),
        Arguments.of(8, true),
        Arguments.of(128, true),
        Arguments.of(129, false));
  }

  @Test
  void aMissingPasswordIsRequired() {
    assertThat(UserInputValidator.validatePassword(null, errors)).isFalse();
    assertThat(UserInputValidator.validatePassword("", errors)).isFalse();
    assertThat(messages())
        .containsExactly("user.validation.password.required", "user.validation.password.required");
  }

  @Test
  void displaySettingsFallBackToDefaultsWhenOptionalAndAreRequiredOtherwise() {
    assertThat(UserInputValidator.parseTheme(null, false, errors)).isEqualTo(Theme.LIGHT);
    assertThat(UserInputValidator.parseFontSize(null, false, errors)).isEqualTo(FontSize.MEDIUM);
    assertThat(UserInputValidator.parseLocale(null, false, errors)).isEqualTo(UiLocale.JA);
    assertThat(errors).isEmpty();

    assertThat(UserInputValidator.parseTheme(null, true, errors)).isNull();
    assertThat(UserInputValidator.parseFontSize(null, true, errors)).isNull();
    assertThat(UserInputValidator.parseLocale(null, true, errors)).isNull();
    assertThat(messages())
        .containsExactly(
            "user.validation.theme.required",
            "user.validation.fontSize.required",
            "user.validation.locale.required");
  }

  @Test
  void displaySettingsAcceptOnlyTheAllowedValues() {
    assertThat(UserInputValidator.parseTheme("dark", true, errors)).isEqualTo(Theme.DARK);
    assertThat(UserInputValidator.parseFontSize("small", true, errors)).isEqualTo(FontSize.SMALL);
    assertThat(UserInputValidator.parseLocale("en", true, errors)).isEqualTo(UiLocale.EN);
    assertThat(errors).isEmpty();

    assertThat(UserInputValidator.parseTheme("blue", false, errors)).isNull();
    assertThat(UserInputValidator.parseFontSize("huge", false, errors)).isNull();
    assertThat(UserInputValidator.parseLocale("fr", false, errors)).isNull();
    assertThat(messages())
        .containsExactly(
            "user.validation.theme.invalid",
            "user.validation.fontSize.invalid",
            "user.validation.locale.invalid");
  }
}
