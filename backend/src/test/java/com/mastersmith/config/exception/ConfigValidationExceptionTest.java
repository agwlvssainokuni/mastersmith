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

package com.mastersmith.config.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigValidationException}が、開発者向けスタックトレースを含まず、フィールド単位の
 * 構造化情報のみを保持することを確認するテスト(security-design.md「エラー情報の安全な返却」、 rules.md BR1.11)。
 */
class ConfigValidationExceptionTest {

  @Test
  void exposesOnlyStructuredFieldErrorsThroughGetFieldErrors() {
    List<FieldError> errors =
        List.of(
            new FieldError("tableConfig:public.products.schemaName", "required"),
            new FieldError("columnConfig:status.choiceOptions", "choiceOrFkExclusive"));

    ConfigValidationException exception = new ConfigValidationException(errors);

    assertThat(exception.getFieldErrors()).containsExactlyElementsOf(errors);
  }

  @Test
  void messageIsAGenericFixedStringAndDoesNotEmbedFieldValues() {
    // "SUPER-SECRET-INTERNAL-DETAIL"のような具体的な値がメッセージへ混入しないことを確認する。
    String sensitiveMarker = "SUPER-SECRET-INTERNAL-DETAIL";
    ConfigValidationException exception =
        new ConfigValidationException(List.of(new FieldError(sensitiveMarker, "required")));

    assertThat(exception.getMessage())
        .isEqualTo("Configuration validation failed: 1 field error(s)")
        .doesNotContain(sensitiveMarker);
  }

  @Test
  void doesNotWrapAnUnderlyingCauseThatCouldLeakImplementationDetails() {
    ConfigValidationException exception =
        new ConfigValidationException(List.of(new FieldError("schemaName", "required")));

    assertThat(exception.getCause()).isNull();
  }

  @Test
  void fieldErrorsListIsDefensivelyCopiedAndImmutable() {
    List<FieldError> mutableSource = new java.util.ArrayList<>();
    mutableSource.add(new FieldError("schemaName", "required"));
    ConfigValidationException exception = new ConfigValidationException(mutableSource);

    mutableSource.add(new FieldError("tableName", "required"));

    assertThat(exception.getFieldErrors()).hasSize(1);
  }
}
