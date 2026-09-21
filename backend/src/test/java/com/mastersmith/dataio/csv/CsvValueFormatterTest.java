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
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link CsvValueFormatter}の単体テスト(rules.md BR8.1、レビュー指摘R-02)。エクスポートが出力する各型の値が、 {@link
 * CsvRowValidator}(インポート)の受け付ける正規形式になることを、型ごとにテーブル駆動で確認する。
 */
class CsvValueFormatterTest {

  static Stream<Arguments> formatCases() {
    return Stream.of(
        Arguments.of(
            "DATE: java.sql.Date",
            EditorType.DATE,
            java.sql.Date.valueOf("2026-09-14"),
            "2026-09-14"),
        Arguments.of("DATE: LocalDate", EditorType.DATE, LocalDate.of(2026, 9, 14), "2026-09-14"),
        Arguments.of(
            "DATE: Timestamp(日付のみを保持しない列)",
            EditorType.DATE,
            Timestamp.valueOf("2026-09-14 10:20:30"),
            "2026-09-14"),
        Arguments.of(
            "DATETIME: Timestamp(Timestamp.toString()の'2026-01-01 10:00:00.0'にしない)",
            EditorType.DATETIME,
            Timestamp.valueOf("2026-01-01 10:00:00"),
            "2026-01-01T10:00:00"),
        Arguments.of(
            "DATETIME: 小数秒付きTimestamp",
            EditorType.DATETIME,
            Timestamp.valueOf("2026-01-01 10:00:00.123"),
            "2026-01-01T10:00:00.123"),
        Arguments.of(
            "DATETIME: LocalDateTime(秒が0でも秒を出力する)",
            EditorType.DATETIME,
            LocalDateTime.of(2026, 1, 1, 10, 0),
            "2026-01-01T10:00:00"),
        Arguments.of(
            "DATETIME: OffsetDateTime(タイムゾーンなしの日時へ)",
            EditorType.DATETIME,
            OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)),
            "2026-01-01T10:00:00"),
        Arguments.of(
            "DATETIME: java.sql.Date(日付のみの列)",
            EditorType.DATETIME,
            java.sql.Date.valueOf("2026-01-01"),
            "2026-01-01T00:00:00"),
        Arguments.of("SWITCH: true", EditorType.SWITCH, Boolean.TRUE, "true"),
        Arguments.of("CHECKBOX: false", EditorType.CHECKBOX, Boolean.FALSE, "false"),
        Arguments.of("SWITCH: 数値1で真偽値を返すドライバ", EditorType.SWITCH, 1, "true"),
        Arguments.of("CHECKBOX: 数値0で真偽値を返すドライバ", EditorType.CHECKBOX, 0, "false"),
        Arguments.of("DECIMAL: 指数表記にしない", EditorType.DECIMAL, new BigDecimal("1E+3"), "1000"),
        Arguments.of("DECIMAL: 末尾のゼロを保持", EditorType.DECIMAL, new BigDecimal("5.50"), "5.50"),
        Arguments.of("DECIMAL: Double", EditorType.DECIMAL, 1.5d, "1.5"),
        Arguments.of("INTEGER: Integer", EditorType.INTEGER, 42, "42"),
        Arguments.of("INTEGER: Long", EditorType.INTEGER, 9_000_000_000L, "9000000000"),
        Arguments.of("TEXT: そのまま", EditorType.TEXT, "a,b\"c", "a,b\"c"),
        Arguments.of("NULLは空文字列: DATETIME", EditorType.DATETIME, null, ""),
        Arguments.of("NULLは空文字列: TEXT", EditorType.TEXT, null, ""),
        Arguments.of("NULLは空文字列: SWITCH", EditorType.SWITCH, null, ""));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formatCases")
  void formatsValueIntoImportableCanonicalForm(
      String description, EditorType editorType, Object value, String expected) {
    assertThat(CsvValueFormatter.format(editorType, value)).isEqualTo(expected);
  }

  @Test
  void formattedTemporalAndBooleanValuesAreAcceptedByTheImportValidator() {
    // BR8.1: エクスポート形式は、インポートの型変換がそのまま受け付ける。
    var validator = new CsvRowValidator();
    var columns =
        List.of(
            new com.mastersmith.dataio.dto.CsvColumnDefinition(
                "d",
                EditorType.DATE,
                null,
                com.mastersmith.config.entity.Visibility.VISIBLE,
                false),
            new com.mastersmith.dataio.dto.CsvColumnDefinition(
                "dt",
                EditorType.DATETIME,
                null,
                com.mastersmith.config.entity.Visibility.VISIBLE,
                false),
            new com.mastersmith.dataio.dto.CsvColumnDefinition(
                "b",
                EditorType.SWITCH,
                null,
                com.mastersmith.config.entity.Visibility.VISIBLE,
                false));
    Map<String, String> raw =
        Map.of(
            "d", CsvValueFormatter.format(EditorType.DATE, java.sql.Date.valueOf("2026-09-14")),
            "dt",
                CsvValueFormatter.format(
                    EditorType.DATETIME, Timestamp.valueOf("2026-01-01 10:00:00.5")),
            "b", CsvValueFormatter.format(EditorType.SWITCH, Boolean.TRUE));

    var result = validator.validateRow(1, raw, columns, value -> true);

    assertThat(result.isValid()).isTrue();
    assertThat(result.convertedValues())
        .containsEntry("d", LocalDate.of(2026, 9, 14))
        .containsEntry("dt", LocalDateTime.of(2026, 1, 1, 10, 0, 0, 500_000_000))
        .containsEntry("b", Boolean.TRUE);
  }
}
