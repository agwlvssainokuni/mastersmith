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

package com.mastersmith.schema.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link LogSanitizer}の単体テスト(レビュー指摘R-07: ログインジェクション対策)。 */
class LogSanitizerTest {

  @Test
  void leavesOrdinaryAndJapaneseNamesUntouched() {
    assertThat(LogSanitizer.clean("shop_master")).isEqualTo("shop_master");
    assertThat(LogSanitizer.clean("商品マスタ")).isEqualTo("商品マスタ");
  }

  @Test
  void replacesLineBreaksAndOtherControlCharactersWithVisibleEscapes() {
    assertThat(LogSanitizer.clean("a\nb\rc\td")).isEqualTo("a\\u000ab\\u000dc\\u0009d");
    assertThat(LogSanitizer.clean("a\u0000b")).isEqualTo("a\\u0000b");
  }

  @Test
  void replacesUnicodeLineSeparatorsAndBidirectionalControls() {
    assertThat(LogSanitizer.clean("a b c")).isEqualTo("a\\u2028b\\u2029c");
    assertThat(LogSanitizer.clean("a‮b")).isEqualTo("a\\u202eb");
  }

  @Test
  void keepsNullAsNullAndSanitizesEachElementOfAList() {
    assertThat(LogSanitizer.clean((String) null)).isNull();
    assertThat(LogSanitizer.clean((List<String>) null)).isNull();
    assertThat(LogSanitizer.clean(List.of("ok", "x\ny"))).containsExactly("ok", "x\\u000ay");
  }
}
