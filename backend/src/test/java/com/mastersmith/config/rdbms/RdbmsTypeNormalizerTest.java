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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.config.exception.ConfigValidationException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link RdbmsTypeNormalizer}の表駆動テスト(rules.md BR1.12)。PostgreSQL/MySQL/MariaDBの
 * 代表的な型名が正しい論理型へ変換されることを確認する(unit-test-instructions.md)。
 */
class RdbmsTypeNormalizerTest {

  private final RdbmsTypeNormalizer normalizer = new RdbmsTypeNormalizer();

  static Stream<Arguments> representativeTypes() {
    return Stream.of(
        // PostgreSQL
        Arguments.of(RdbmsDialect.POSTGRESQL, "varchar(255)", LogicalType.STRING),
        Arguments.of(RdbmsDialect.POSTGRESQL, "text", LogicalType.LONG_TEXT),
        Arguments.of(RdbmsDialect.POSTGRESQL, "integer", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.POSTGRESQL, "serial", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.POSTGRESQL, "bigserial", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.POSTGRESQL, "numeric(10,2)", LogicalType.DECIMAL),
        Arguments.of(RdbmsDialect.POSTGRESQL, "boolean", LogicalType.BOOLEAN),
        Arguments.of(RdbmsDialect.POSTGRESQL, "date", LogicalType.DATE),
        Arguments.of(RdbmsDialect.POSTGRESQL, "timestamp without time zone", LogicalType.DATETIME),
        Arguments.of(RdbmsDialect.POSTGRESQL, "timestamptz", LogicalType.DATETIME),
        // MySQL
        Arguments.of(RdbmsDialect.MYSQL, "varchar(255)", LogicalType.STRING),
        Arguments.of(RdbmsDialect.MYSQL, "longtext", LogicalType.LONG_TEXT),
        Arguments.of(RdbmsDialect.MYSQL, "int", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.MYSQL, "int(11) auto_increment", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.MYSQL, "int unsigned", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.MYSQL, "bigint", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.MYSQL, "decimal(10,2)", LogicalType.DECIMAL),
        Arguments.of(RdbmsDialect.MYSQL, "boolean", LogicalType.BOOLEAN),
        Arguments.of(RdbmsDialect.MYSQL, "date", LogicalType.DATE),
        Arguments.of(RdbmsDialect.MYSQL, "datetime", LogicalType.DATETIME),
        // MariaDB(MySQLと型名体系を共有)
        Arguments.of(RdbmsDialect.MARIADB, "varchar(255)", LogicalType.STRING),
        Arguments.of(RdbmsDialect.MARIADB, "int(11) auto_increment", LogicalType.INTEGER),
        Arguments.of(RdbmsDialect.MARIADB, "decimal(10,2)", LogicalType.DECIMAL),
        Arguments.of(RdbmsDialect.MARIADB, "datetime", LogicalType.DATETIME));
  }

  @ParameterizedTest
  @MethodSource("representativeTypes")
  void normalizesRepresentativeRawTypeNames(
      RdbmsDialect dialect, String rawTypeName, LogicalType expected) {
    assertThat(normalizer.normalize(dialect, rawTypeName)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("representativeTypes")
  void defaultEditorTypeIsDerivedFromTheNormalizedLogicalType(
      RdbmsDialect dialect, String rawTypeName, LogicalType expected) {
    assertThat(normalizer.normalize(dialect, rawTypeName).defaultEditorType())
        .isEqualTo(expected.defaultEditorType());
  }

  @ParameterizedTest
  @MethodSource("unsupportedTypes")
  void throwsConfigValidationExceptionForUnsupportedTypeNames(
      RdbmsDialect dialect, String rawTypeName) {
    assertThatThrownBy(() -> normalizer.normalize(dialect, rawTypeName))
        .isInstanceOf(ConfigValidationException.class)
        .satisfies(
            ex ->
                assertThat(((ConfigValidationException) ex).getFieldErrors())
                    .extracting(com.mastersmith.config.exception.FieldError::ruleType)
                    .containsExactly("unsupportedRdbmsType"));
  }

  static Stream<Arguments> unsupportedTypes() {
    return Stream.of(
        Arguments.of(RdbmsDialect.POSTGRESQL, "hstore"),
        Arguments.of(RdbmsDialect.MYSQL, "geometry"),
        Arguments.of(RdbmsDialect.MARIADB, ""));
  }
}
