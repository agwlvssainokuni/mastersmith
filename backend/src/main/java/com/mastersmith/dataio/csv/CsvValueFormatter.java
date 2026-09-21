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

import com.mastersmith.config.entity.EditorType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CSVエクスポート時の1セル分の値を、{@link CsvRowValidator}(インポート)が受け付ける正規形式の文字列へ整形する (rules.md
 * BR8.1「エクスポート・インポート双方で同一形式を用いる」)。
 *
 * <p>JDBCドライバは列の型に応じて{@link java.sql.Timestamp}・{@link java.sql.Date}・{@link Number}等を返し、その {@code
 * toString()}は形式がまちまち(例: {@code Timestamp}は{@code 2026-01-01 10:00:00.0})でインポートが 受け付けない。そのため、{@code
 * editorType}に応じて次の正規形式へ明示的に整形する。
 *
 * <ul>
 *   <li>{@code DATE}: ISO-8601の日付({@code yyyy-MM-dd})
 *   <li>{@code DATETIME}: ISO-8601の日時({@code yyyy-MM-dd'T'HH:mm:ss}、小数秒は0でなければ付与、タイムゾーンなし)
 *   <li>{@code SWITCH}/{@code CHECKBOX}: {@code true}/{@code false}
 *   <li>{@code INTEGER}/{@code DECIMAL}: 指数表記を用いない10進表記
 *   <li>上記以外: {@code toString()}
 * </ul>
 *
 * <p>NULLは空文字列とする(インポート側は、必須でない列の空セルをNULLとして扱う)。
 */
public final class CsvValueFormatter {

  private CsvValueFormatter() {}

  /**
   * 1セル分の値をCSV出力用の文字列へ整形する。
   *
   * @param editorType 列のエディタ種別
   * @param value {@code ResultSet}から取得した値(nullable)
   * @return 正規形式の文字列(valueがnullの場合は空文字列)
   */
  public static String format(EditorType editorType, Object value) {
    if (value == null) {
      return "";
    }
    return switch (editorType) {
      case DATE -> formatDate(value);
      case DATETIME -> formatDateTime(value);
      case SWITCH, CHECKBOX -> formatBoolean(value);
      case INTEGER, DECIMAL -> formatNumber(value);
      default -> value.toString();
    };
  }

  private static String formatDate(Object value) {
    if (value instanceof LocalDate localDate) {
      return localDate.toString();
    }
    if (value instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate().toString();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toLocalDate().toString();
    }
    if (value instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime().toLocalDate().toString();
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toLocalDate().toString();
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toLocalDate().toString();
    }
    return value.toString();
  }

  private static String formatDateTime(Object value) {
    if (value instanceof LocalDateTime localDateTime) {
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime);
    }
    if (value instanceof Timestamp timestamp) {
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(timestamp.toLocalDateTime());
    }
    if (value instanceof java.sql.Date sqlDate) {
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(sqlDate.toLocalDate().atStartOfDay());
    }
    if (value instanceof LocalDate localDate) {
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDate.atStartOfDay());
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(offsetDateTime.toLocalDateTime());
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(zonedDateTime.toLocalDateTime());
    }
    return value.toString();
  }

  private static String formatBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool.toString();
    }
    if (value instanceof Number number) {
      // MySQL/MariaDBのTINYINT(1)等、真偽値を0/1の数値で返すドライバ設定への備え。
      return new BigDecimal(number.toString()).signum() == 0 ? "false" : "true";
    }
    return value.toString();
  }

  private static String formatNumber(Object value) {
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal.toPlainString();
    }
    if (value instanceof Number number) {
      try {
        return new BigDecimal(number.toString()).toPlainString();
      } catch (NumberFormatException e) {
        // NaN・Infinity等、10進表記にできない値は素の表記のまま出力する(インポート側で型変換エラーとなる)。
        return number.toString();
      }
    }
    return value.toString();
  }
}
