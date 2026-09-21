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

package com.mastersmith.config.validation;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.exception.FieldError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * TableConfig/ColumnConfigのfail-fast検証(rules.md BR1.1〜BR1.4)を実行する。
 *
 * <p>Jakarta Bean Validationアノテーション(必須プロパティ)とカスタム{@link
 * ChoiceOrFkExclusive}制約(BR1.4)による検証結果を、{@link ConfigValidationException}の
 * フィールド単位エラー情報へマッピングする(BR1.11)。単一エンティティの検証と、起動時・ インポート時のための集約検証({@link #validateAll})の両方を提供する。
 */
@Component
public class ConfigValidator {

  private final Validator validator;

  public ConfigValidator(Validator validator) {
    this.validator = validator;
  }

  /** 単一TableConfigを検証する。違反があれば{@link ConfigValidationException}を送出する(BR1.2)。 */
  public void validate(TableConfig tableConfig) {
    validateAll(List.of(tableConfig), List.of());
  }

  /** 単一ColumnConfigを検証する。違反があれば{@link ConfigValidationException}を送出する(BR1.3, BR1.4)。 */
  public void validate(ColumnConfig columnConfig) {
    validateAll(List.of(), List.of(columnConfig));
  }

  /**
   * 複数のTableConfig/ColumnConfigを一括検証する。起動時の全件検証(W1)およびインポート時の
   * all-or-nothing検証(W5)で用いる。違反はすべて集約したうえで単一の{@link ConfigValidationException}として送出する(BR1.1)。
   */
  public void validateAll(List<TableConfig> tableConfigs, List<ColumnConfig> columnConfigs) {
    List<FieldError> errors = new ArrayList<>();
    for (TableConfig tableConfig : tableConfigs) {
      for (ConstraintViolation<TableConfig> violation : validator.validate(tableConfig)) {
        errors.add(toFieldError(describeTableConfig(tableConfig), violation));
      }
    }
    for (ColumnConfig columnConfig : columnConfigs) {
      for (ConstraintViolation<ColumnConfig> violation : validator.validate(columnConfig)) {
        errors.add(toFieldError(describeColumnConfig(columnConfig), violation));
      }
    }
    if (!errors.isEmpty()) {
      throw new ConfigValidationException(errors);
    }
  }

  /**
   * 単一TableConfigを検証し、違反を、例外ではなく、プロパティ単位の一覧(位置=プロパティの経路、ルール種別)として返す(取り込みの検証専用メソッドが、全件を集めるための、
   * {@link #validate(TableConfig)}の、例外を投げない版。config-import-export BR9.10)。違反がなければ、空。
   */
  public List<FieldError> propertyErrors(TableConfig tableConfig) {
    List<FieldError> errors = new ArrayList<>();
    for (ConstraintViolation<TableConfig> violation : validator.validate(tableConfig)) {
      errors.add(new FieldError(violation.getPropertyPath().toString(), ruleType(violation)));
    }
    return errors;
  }

  /** 単一ColumnConfigを検証し、違反を、プロパティ単位の一覧として返す({@link #propertyErrors(TableConfig)}と同じ)。 */
  public List<FieldError> propertyErrors(ColumnConfig columnConfig) {
    List<FieldError> errors = new ArrayList<>();
    for (ConstraintViolation<ColumnConfig> violation : validator.validate(columnConfig)) {
      errors.add(new FieldError(violation.getPropertyPath().toString(), ruleType(violation)));
    }
    return errors;
  }

  private static String describeTableConfig(TableConfig tableConfig) {
    return "tableConfig:%s.%s"
        .formatted(
            orPlaceholder(tableConfig.getSchemaName()), orPlaceholder(tableConfig.getTableName()));
  }

  private static String describeColumnConfig(ColumnConfig columnConfig) {
    return "columnConfig:%s".formatted(orPlaceholder(columnConfig.getColumnName()));
  }

  private static String orPlaceholder(String value) {
    return value == null || value.isBlank() ? "?" : value;
  }

  private static <T> FieldError toFieldError(
      String entityDescriptor, ConstraintViolation<T> violation) {
    String path = violation.getPropertyPath().toString();
    String field = path.isBlank() ? entityDescriptor : entityDescriptor + "." + path;
    return new FieldError(field, ruleType(violation));
  }

  private static String ruleType(ConstraintViolation<?> violation) {
    String annotationName =
        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    return switch (annotationName) {
      case "NotBlank", "NotNull", "NotEmpty" -> "required";
      case "ChoiceOrFkExclusive" -> "choiceOrFkExclusive";
      default -> Objects.requireNonNullElse(annotationName, "invalid");
    };
  }
}
