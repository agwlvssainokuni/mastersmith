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

import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.dataio.dto.CsvColumnDefinition;
import com.mastersmith.dataio.dto.CsvImportRowResult;
import com.mastersmith.dataio.dto.ImportOperation;
import com.mastersmith.dataio.dto.ImportOutcome;
import com.mastersmith.dataio.dto.RowError;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * CSVインポートの1行分のバリデーション(rules.md BR8.3, BR8.5, BR8.6)を行う。
 *
 * <p>各列について、config-engineから取得した{@code editorType}への型変換、および{@code
 * validationRule}(required/minLength/maxLength/min/max/pattern)の適用を行い、違反があれば行番号・
 * フィールド単位のエラー(BR8.6)として記録する。あわせて、主キー列の値の有無からINSERT/UPDATEを判定する
 * (BR8.3)。UPDATE判定時、対象行の存在確認は呼び出し元が{@link Predicate}として渡す
 * (本クラス自体はDBアクセスを持たず、単体テストで容易に検証できるようにするための設計)。
 */
@Component
public class CsvRowValidator {

  /**
   * 1行分の値をバリデーションする。
   *
   * @param rowNumber CSVファイル上の行番号(ヘッダー行を除く、1始まり)
   * @param rawValues CSVの列名をキーとした生の文字列値(欠落列は空文字列として扱われる)
   * @param columns 対象テーブルのCsvColumnDefinition一覧(displayOrder順を仮定しない)
   * @param primaryKeyExists 主キー値を受け取り、対象テーブルに既存行が存在するか判定する関数(UPDATE判定時のみ呼び出す)
   * @return バリデーション結果(outcome=VALIDの場合は型変換後の値を、INVALIDの場合はエラー一覧を保持する)
   */
  public RowValidationResult validateRow(
      int rowNumber,
      Map<String, String> rawValues,
      List<CsvColumnDefinition> columns,
      Predicate<String> primaryKeyExists) {

    List<RowError> errors = new ArrayList<>();
    Map<String, Object> convertedValues = new LinkedHashMap<>();

    for (CsvColumnDefinition column : columns) {
      validateColumn(rowNumber, rawValues, column, errors, convertedValues);
    }

    CsvColumnDefinition primaryKeyColumn =
        columns.stream().filter(CsvColumnDefinition::isPrimaryKey).findFirst().orElse(null);
    ImportOperation operation = ImportOperation.INSERT;
    if (primaryKeyColumn != null) {
      String rawPrimaryKey = trimmedOrEmpty(rawValues.get(primaryKeyColumn.columnName()));
      if (!rawPrimaryKey.isEmpty()) {
        operation = ImportOperation.UPDATE;
        if (!primaryKeyExists.test(rawPrimaryKey)) {
          errors.add(new RowError(rowNumber, primaryKeyColumn.columnName(), "notFound"));
        }
      }
    }

    if (!errors.isEmpty()) {
      return new RowValidationResult(
          new CsvImportRowResult(rowNumber, ImportOutcome.INVALID, null, errors), Map.of());
    }
    return new RowValidationResult(
        new CsvImportRowResult(rowNumber, ImportOutcome.VALID, operation, List.of()),
        convertedValues);
  }

  private void validateColumn(
      int rowNumber,
      Map<String, String> rawValues,
      CsvColumnDefinition column,
      List<RowError> errors,
      Map<String, Object> convertedValues) {
    String raw = trimmedOrEmpty(rawValues.get(column.columnName()));
    ValidationRule rule = column.validationRule();
    if (raw.isEmpty()) {
      if (isRequired(rule)) {
        errors.add(new RowError(rowNumber, column.columnName(), "required"));
      } else {
        convertedValues.put(column.columnName(), null);
      }
      return;
    }

    Object converted;
    try {
      converted = convert(column, raw);
    } catch (RuntimeException e) {
      errors.add(new RowError(rowNumber, column.columnName(), "typeMismatch"));
      return;
    }

    List<String> ruleViolations = applyValidationRule(rule, raw, converted);
    if (!ruleViolations.isEmpty()) {
      for (String violation : ruleViolations) {
        errors.add(new RowError(rowNumber, column.columnName(), violation));
      }
      return;
    }
    convertedValues.put(column.columnName(), converted);
  }

  private static boolean isRequired(ValidationRule rule) {
    return Boolean.TRUE.equals(rule.get("required"));
  }

  private static Object convert(CsvColumnDefinition column, String raw) {
    return switch (column.editorType()) {
      case TEXT, TEXTAREA, SELECT, RADIO -> raw;
      case INTEGER -> Long.parseLong(raw);
      case DECIMAL -> new BigDecimal(raw);
      case DATE -> LocalDate.parse(raw);
      case DATETIME -> LocalDateTime.parse(raw);
      case SWITCH, CHECKBOX -> parseBoolean(raw);
    };
  }

  private static Boolean parseBoolean(String raw) {
    if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException("invalid boolean value: " + raw);
  }

  private static List<String> applyValidationRule(
      ValidationRule rule, String raw, Object converted) {
    List<String> violations = new ArrayList<>();
    if (rule.has("minLength") && raw.length() < toInt(rule.get("minLength"))) {
      violations.add("minLength");
    }
    if (rule.has("maxLength") && raw.length() > toInt(rule.get("maxLength"))) {
      violations.add("maxLength");
    }
    if (rule.has("min")
        && converted instanceof Number number
        && toBigDecimal(number).compareTo(toBigDecimal(rule.get("min"))) < 0) {
      violations.add("min");
    }
    if (rule.has("max")
        && converted instanceof Number number
        && toBigDecimal(number).compareTo(toBigDecimal(rule.get("max"))) > 0) {
      violations.add("max");
    }
    if (rule.has("pattern") && !raw.matches(String.valueOf(rule.get("pattern")))) {
      violations.add("pattern");
    }
    return violations;
  }

  private static int toInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    if (value instanceof Number number) {
      return new BigDecimal(number.toString());
    }
    return new BigDecimal(String.valueOf(value));
  }

  private static String trimmedOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }
}
