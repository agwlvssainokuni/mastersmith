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

package com.mastersmith.configio.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.common.configio.ImportValidationError;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** {@link ImportErrorCollector}の単体テスト(最大100件・打ち切りの表示・位置ごとの重複の排除・失敗の分類のための記憶)。 */
class ImportErrorCollectorTest {

  @Test
  void startsEmpty() {
    ImportErrorCollector collector = new ImportErrorCollector();

    assertThat(collector.hasErrors()).isFalse();
    assertThat(collector.total()).isZero();
    assertThat(collector.truncated()).isFalse();
    assertThat(collector.errors()).isEmpty();
  }

  @Test
  void keepsAtMostOneHundredErrorsButCountsAllOfThem() {
    ImportErrorCollector collector = new ImportErrorCollector();

    IntStream.range(0, 150).forEach(i -> collector.add("f[" + i + "]", "m"));

    assertThat(collector.errors()).hasSize(100);
    assertThat(collector.total()).isEqualTo(150);
    assertThat(collector.truncated()).isTrue();
    assertThat(collector.errors().get(0).field()).isEqualTo("f[0]");
    assertThat(collector.errors().get(99).field()).isEqualTo("f[99]");
  }

  @Test
  void exactlyOneHundredIsNotTruncated() {
    ImportErrorCollector collector = new ImportErrorCollector();

    IntStream.range(0, 100).forEach(i -> collector.add("f[" + i + "]", "m"));

    assertThat(collector.truncated()).isFalse();
    assertThat(collector.total()).isEqualTo(100);
  }

  @Test
  void rememberedMessageKeysIncludeErrorsThatWereNotKept() {
    ImportErrorCollector collector = new ImportErrorCollector();
    IntStream.range(0, 100).forEach(i -> collector.add("f[" + i + "]", "first"));

    collector.add("late", "escalation");

    assertThat(collector.errors()).hasSize(100);
    assertThat(collector.hasMessage("escalation")).isTrue();
    assertThat(collector.hasMessage("other")).isFalse();
  }

  @Test
  void addAllPrefixesThePositionAndSkipsPositionsThatAlreadyHaveAnError() {
    ImportErrorCollector collector = new ImportErrorCollector();
    collector.add("schema.tables[0].displayOrder", "parser");

    collector.addAll(
        "schema",
        List.of(
            ImportValidationError.of("tables[0].displayOrder", "unit"),
            ImportValidationError.of("tables[1].tableName", "unit", Map.of("k", 1))));

    assertThat(collector.errors())
        .extracting(ImportValidationError::field, ImportValidationError::message)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple("schema.tables[0].displayOrder", "parser"),
            org.assertj.core.api.Assertions.tuple("schema.tables[1].tableName", "unit"));
    assertThat(collector.total()).isEqualTo(2);
    assertThat(collector.errors().get(1).params()).containsEntry("k", 1);
  }

  @Test
  void theReturnedListIsACopy() {
    ImportErrorCollector collector = new ImportErrorCollector();
    collector.add("f", "m");

    List<ImportValidationError> snapshot = collector.errors();
    collector.add("g", "m");

    assertThat(snapshot).hasSize(1);
  }
}
