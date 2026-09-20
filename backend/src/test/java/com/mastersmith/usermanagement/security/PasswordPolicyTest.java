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

package com.mastersmith.usermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link PasswordPolicy}: 長さ(Unicodeコードポイント数)の境界値(NFR2.3、NFR8.2)。 */
class PasswordPolicyTest {

  private static final String EMOJI = "😀"; // U+1F600(サロゲートペア。1コードポイント、UTF-16で2char)

  @ParameterizedTest(name = "{0}文字(ASCII)の有効判定は{1}")
  @CsvSource({
    "0, false",
    "1, false",
    "7, false",
    "8, true",
    "9, true",
    "64, true",
    "127, true",
    "128, true",
    "129, false",
    "1000, false"
  })
  void validatesTheLengthBoundaries(int length, boolean valid) {
    assertThat(PasswordPolicy.isValidLength("a".repeat(length))).isEqualTo(valid);
  }

  @ParameterizedTest(name = "サロゲートペア{0}文字の有効判定は{1}")
  @CsvSource({"7, false", "8, true", "128, true", "129, false"})
  void countsCodePointsNotUtf16Chars(int codePoints, boolean valid) {
    String password = EMOJI.repeat(codePoints);

    assertThat(password.length()).isEqualTo(codePoints * 2);
    assertThat(PasswordPolicy.length(password)).isEqualTo(codePoints);
    assertThat(PasswordPolicy.isValidLength(password)).isEqualTo(valid);
  }

  @ParameterizedTest
  @NullSource
  void rejectsNull(String password) {
    assertThat(PasswordPolicy.isValidLength(password)).isFalse();
    assertThat(PasswordPolicy.exceedsMaxLength(password)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {128, 129, 130, 5000})
  void exceedsMaxLengthOnlyAboveTheLimit(int length) {
    assertThat(PasswordPolicy.exceedsMaxLength("a".repeat(length))).isEqualTo(length > 128);
  }

  @ParameterizedTest
  @ValueSource(ints = {128, 129})
  void exceedsMaxLengthCountsCodePoints(int codePoints) {
    assertThat(PasswordPolicy.exceedsMaxLength(EMOJI.repeat(codePoints)))
        .isEqualTo(codePoints > 128);
  }
}
