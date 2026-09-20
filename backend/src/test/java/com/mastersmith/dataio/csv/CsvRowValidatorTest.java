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

package com.mastersmith.dataio.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.dataio.dto.CsvColumnDefinition;
import com.mastersmith.dataio.dto.ImportOperation;
import com.mastersmith.dataio.dto.ImportOutcome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link CsvRowValidator}の単体テスト(rules.md BR8.3, BR8.5, BR8.6)。
 * 型変換エラー・validationRule違反・UPDATE対象行不在エラー・正常系のINSERT/UPDATE判定を
 * テーブル駆動で網羅する(team.md確定の安全失敗/バリデーションテスト)。
 */
class CsvRowValidatorTest {

  private final CsvRowValidator validator = new CsvRowValidator();

  private static final Predicate<String> ALWAYS_EXISTS = value -> true;
  private static final Predicate<String> NEVER_EXISTS = value -> false;

  private static CsvColumnDefinition column(
      String name, EditorType editorType, ValidationRule rule, boolean isPrimaryKey) {
    return new CsvColumnDefinition(name, editorType, rule, Visibility.VISIBLE, isPrimaryKey);
  }

  static Stream<Arguments> validationFailureCases() {
    return Stream.of(
        Arguments.of(
            "整数列に数値でない文字列を与えると型変換エラーになる",
            List.of(column("qty", EditorType.INTEGER, ValidationRule.empty(), false)),
            Map.of("qty", "not-a-number"),
            "qty",
            "typeMismatch"),
        Arguments.of(
            "小数列に数値でない文字列を与えると型変換エラーになる",
            List.of(column("price", EditorType.DECIMAL, ValidationRule.empty(), false)),
            Map.of("price", "abc"),
            "price",
            "typeMismatch"),
        Arguments.of(
            "日付列に不正な書式を与えると型変換エラーになる",
            List.of(column("published_on", EditorType.DATE, ValidationRule.empty(), false)),
            Map.of("published_on", "not-a-date"),
            "published_on",
            "typeMismatch"),
        Arguments.of(
            "required違反(空文字)",
            List.of(
                column(
                    "name",
                    EditorType.TEXT,
                    ValidationRule.builder().rule("required", true).build(),
                    false)),
            Map.of("name", ""),
            "name",
            "required"),
        Arguments.of(
            "minLength違反",
            List.of(
                column(
                    "code",
                    EditorType.TEXT,
                    ValidationRule.builder().rule("minLength", 5).build(),
                    false)),
            Map.of("code", "ab"),
            "code",
            "minLength"),
        Arguments.of(
            "maxLength違反",
            List.of(
                column(
                    "code",
                    EditorType.TEXT,
                    ValidationRule.builder().rule("maxLength", 3).build(),
                    false)),
            Map.of("code", "abcdef"),
            "code",
            "maxLength"),
        Arguments.of(
            "min違反",
            List.of(
                column(
                    "qty",
                    EditorType.INTEGER,
                    ValidationRule.builder().rule("min", 10).build(),
                    false)),
            Map.of("qty", "5"),
            "qty",
            "min"),
        Arguments.of(
            "max違反",
            List.of(
                column(
                    "qty",
                    EditorType.INTEGER,
                    ValidationRule.builder().rule("max", 10).build(),
                    false)),
            Map.of("qty", "99"),
            "qty",
            "max"),
        Arguments.of(
            "pattern違反",
            List.of(
                column(
                    "sku",
                    EditorType.TEXT,
                    ValidationRule.builder().rule("pattern", "^[A-Z]{2}-\\d{3}$").build(),
                    false)),
            Map.of("sku", "invalid-format"),
            "sku",
            "pattern"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validationFailureCases")
  void validateRowReportsFieldLevelError(
      String description,
      List<CsvColumnDefinition> columns,
      Map<String, String> rawValues,
      String expectedField,
      String expectedMessage) {
    var result = validator.validateRow(1, rawValues, columns, NEVER_EXISTS);

    assertThat(result.isValid()).isFalse();
    assertThat(result.report().outcome()).isEqualTo(ImportOutcome.INVALID);
    assertThat(result.report().errors()).hasSize(1);
    assertThat(result.report().errors().get(0).row()).isEqualTo(1);
    assertThat(result.report().errors().get(0).field()).isEqualTo(expectedField);
    assertThat(result.report().errors().get(0).message()).isEqualTo(expectedMessage);
  }

  @Test
  void primaryKeyValuePresentButNoExistingRowIsAnError() {
    List<CsvColumnDefinition> columns =
        List.of(column("id", EditorType.INTEGER, ValidationRule.empty(), true));
    Map<String, String> rawValues = Map.of("id", "999");

    var result = validator.validateRow(3, rawValues, columns, NEVER_EXISTS);

    assertThat(result.isValid()).isFalse();
    assertThat(result.report().errors()).hasSize(1);
    assertThat(result.report().errors().get(0).field()).isEqualTo("id");
    assertThat(result.report().errors().get(0).message()).isEqualTo("notFound");
  }

  @Test
  void emptyPrimaryKeyValueMeansInsert() {
    List<CsvColumnDefinition> columns =
        List.of(
            column("id", EditorType.INTEGER, ValidationRule.empty(), true),
            column("name", EditorType.TEXT, ValidationRule.empty(), false));
    Map<String, String> rawValues = new LinkedHashMap<>();
    rawValues.put("id", "");
    rawValues.put("name", "widget");

    var result = validator.validateRow(1, rawValues, columns, NEVER_EXISTS);

    assertThat(result.isValid()).isTrue();
    assertThat(result.report().operation()).isEqualTo(ImportOperation.INSERT);
    assertThat(result.convertedValues()).containsEntry("name", "widget");
  }

  @Test
  void primaryKeyValuePresentAndExistingRowMeansUpdate() {
    List<CsvColumnDefinition> columns =
        List.of(
            column("id", EditorType.INTEGER, ValidationRule.empty(), true),
            column("name", EditorType.TEXT, ValidationRule.empty(), false));
    Map<String, String> rawValues = Map.of("id", "42", "name", "widget");

    var result = validator.validateRow(2, rawValues, columns, ALWAYS_EXISTS);

    assertThat(result.isValid()).isTrue();
    assertThat(result.report().operation()).isEqualTo(ImportOperation.UPDATE);
    assertThat(result.convertedValues()).containsEntry("id", 42L).containsEntry("name", "widget");
  }

  @Test
  void convertsEachEditorTypeToItsCorrespondingJavaType() {
    List<CsvColumnDefinition> columns =
        List.of(
            column("qty", EditorType.INTEGER, ValidationRule.empty(), false),
            column("price", EditorType.DECIMAL, ValidationRule.empty(), false),
            column("published_on", EditorType.DATE, ValidationRule.empty(), false),
            column("active", EditorType.SWITCH, ValidationRule.empty(), false));
    Map<String, String> rawValues =
        Map.of(
            "qty", "10",
            "price", "19.99",
            "published_on", "2026-09-14",
            "active", "true");

    var result = validator.validateRow(1, rawValues, columns, NEVER_EXISTS);

    assertThat(result.isValid()).isTrue();
    assertThat(result.convertedValues()).containsEntry("qty", 10L);
    assertThat(result.convertedValues()).containsEntry("price", new BigDecimal("19.99"));
    assertThat(result.convertedValues()).containsEntry("published_on", LocalDate.of(2026, 9, 14));
    assertThat(result.convertedValues()).containsEntry("active", Boolean.TRUE);
  }

  @Test
  void nonRequiredEmptyValueConvertsToNull() {
    List<CsvColumnDefinition> columns =
        List.of(column("note", EditorType.TEXT, ValidationRule.empty(), false));
    Map<String, String> rawValues = Map.of("note", "");

    var result = validator.validateRow(1, rawValues, columns, NEVER_EXISTS);

    assertThat(result.isValid()).isTrue();
    assertThat(result.convertedValues()).containsEntry("note", null);
  }
}
