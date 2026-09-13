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

package com.mastersmith.config.rdbms;

import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.exception.FieldError;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL/MySQL/MariaDBのメタデータ型名をConfigEngine内部の{@link LogicalType}へ正規化する (rules.md
 * BR1.12(a))。物理層SQL生成方言(BR1.12(b))は本クラスの対象外とする(list-engine/ record-edit-engine側の責務)。
 *
 * <p>未対応の方言・型名が検出された場合は{@link ConfigValidationException}として扱う (BR1.12 violation_behaviour)。
 */
@Component
public class RdbmsTypeNormalizer {

  public LogicalType normalize(RdbmsDialect dialect, String rawTypeName) {
    Objects.requireNonNull(dialect, "dialect must not be null");
    String base = baseName(rawTypeName);
    LogicalType resolved =
        switch (dialect) {
          case POSTGRESQL -> normalizePostgresql(base);
          // MySQLとMariaDBは型名体系を共有するため同一の正規化ロジックを用いる。
          case MYSQL, MARIADB -> normalizeMysqlFamily(base);
        };
    if (resolved == null) {
      throw new ConfigValidationException(
          List.of(new FieldError("rawTypeName:" + dialect, "unsupportedRdbmsType")));
    }
    return resolved;
  }

  private static String baseName(String rawTypeName) {
    if (rawTypeName == null || rawTypeName.isBlank()) {
      return "";
    }
    String trimmed = rawTypeName.trim().toLowerCase(Locale.ROOT);
    int parenIndex = trimmed.indexOf('(');
    String beforeParen = parenIndex >= 0 ? trimmed.substring(0, parenIndex) : trimmed;
    return beforeParen
        .replaceAll("\\b(unsigned|zerofill|auto_increment)\\b", "")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private static LogicalType normalizePostgresql(String base) {
    return switch (base) {
      case "varchar", "character varying", "char", "character", "bpchar" -> LogicalType.STRING;
      case "text" -> LogicalType.LONG_TEXT;
      case "smallint",
          "integer",
          "int",
          "int2",
          "int4",
          "bigint",
          "int8",
          "serial",
          "smallserial",
          "bigserial" ->
          LogicalType.INTEGER;
      case "numeric", "decimal", "real", "double precision", "float4", "float8" ->
          LogicalType.DECIMAL;
      case "boolean", "bool" -> LogicalType.BOOLEAN;
      case "date" -> LogicalType.DATE;
      case "timestamp", "timestamptz", "timestamp without time zone", "timestamp with time zone" ->
          LogicalType.DATETIME;
      default -> null;
    };
  }

  private static LogicalType normalizeMysqlFamily(String base) {
    return switch (base) {
      case "varchar", "char", "tinytext", "mediumtext" -> LogicalType.STRING;
      case "text", "longtext" -> LogicalType.LONG_TEXT;
      case "tinyint", "smallint", "mediumint", "int", "integer", "bigint" -> LogicalType.INTEGER;
      case "decimal", "numeric", "float", "double" -> LogicalType.DECIMAL;
      case "boolean", "bool" -> LogicalType.BOOLEAN;
      case "date" -> LogicalType.DATE;
      case "datetime", "timestamp" -> LogicalType.DATETIME;
      default -> null;
    };
  }
}
