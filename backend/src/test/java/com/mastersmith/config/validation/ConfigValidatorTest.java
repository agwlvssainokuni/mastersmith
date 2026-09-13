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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.TableConfig;
import com.mastersmith.config.exception.ConfigValidationException;
import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.model.FkReference;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link ConfigValidator}の単体テスト(rules.md BR1.1〜BR1.4)。安全失敗/バリデーションテスト
 * (team.md確定の必須テスト種別(a))。テーブル駆動で各違反ケースを網羅する。
 */
class ConfigValidatorTest {

  private ConfigValidator validator;

  @BeforeEach
  void setUp() {
    Validator jakartaValidator = Validation.buildDefaultValidatorFactory().getValidator();
    validator = new ConfigValidator(jakartaValidator);
  }

  @Test
  void validTableConfigPassesValidation() {
    assertThatCode(() -> validator.validate(new TableConfig("public", "products")))
        .doesNotThrowAnyException();
  }

  @Test
  void validColumnConfigPassesValidation() {
    assertThatCode(() -> validator.validate(new ColumnConfig("t1", "name", EditorType.TEXT)))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidTableConfigs")
  void rejectsInvalidTableConfigs(String caseName, Supplier<TableConfig> supplier) {
    assertThatThrownBy(() -> validator.validate(supplier.get()))
        .isInstanceOf(ConfigValidationException.class)
        .satisfies(
            ex ->
                assertThat(((ConfigValidationException) ex).getFieldErrors())
                    .isNotEmpty()
                    .allSatisfy(fe -> assertThat(fe.ruleType()).isEqualTo("required")));
  }

  static Stream<Arguments> invalidTableConfigs() {
    return Stream.of(
        Arguments.of(
            "schemaName missing (BR1.2)",
            (Supplier<TableConfig>) () -> new TableConfig(null, "products")),
        Arguments.of(
            "tableName missing (BR1.2)",
            (Supplier<TableConfig>) () -> new TableConfig("public", null)),
        Arguments.of(
            "schemaName blank (BR1.2)",
            (Supplier<TableConfig>) () -> new TableConfig(" ", "products")));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidColumnConfigs")
  void rejectsInvalidColumnConfigs(
      String caseName, Supplier<ColumnConfig> supplier, String expectedRuleType) {
    assertThatThrownBy(() -> validator.validate(supplier.get()))
        .isInstanceOf(ConfigValidationException.class)
        .satisfies(
            ex ->
                assertThat(((ConfigValidationException) ex).getFieldErrors())
                    .isNotEmpty()
                    .allSatisfy(fe -> assertThat(fe.ruleType()).isEqualTo(expectedRuleType)));
  }

  static Stream<Arguments> invalidColumnConfigs() {
    return Stream.of(
        Arguments.of(
            "tableConfigId missing (BR1.3)",
            (Supplier<ColumnConfig>) () -> new ColumnConfig(null, "name", EditorType.TEXT),
            "required"),
        Arguments.of(
            "columnName missing (BR1.3)",
            (Supplier<ColumnConfig>) () -> new ColumnConfig("t1", null, EditorType.TEXT),
            "required"),
        Arguments.of(
            "editorType missing (BR1.3)",
            (Supplier<ColumnConfig>) () -> new ColumnConfig("t1", "name", null),
            "required"),
        Arguments.of(
            "select with both choiceOptions and fkReference (BR1.4)",
            (Supplier<ColumnConfig>)
                () -> {
                  ColumnConfig cc = new ColumnConfig("t1", "status", EditorType.SELECT);
                  cc.setChoiceOptions(List.of(new ChoiceOption("a", "key.a")));
                  cc.setFkReference(new FkReference("public", "statuses", "id", "name"));
                  return cc;
                },
            "choiceOrFkExclusive"),
        Arguments.of(
            "radio with neither choiceOptions nor fkReference (BR1.4)",
            (Supplier<ColumnConfig>)
                () -> {
                  ColumnConfig cc = new ColumnConfig("t1", "status", EditorType.RADIO);
                  cc.setChoiceOptions(List.of());
                  cc.setFkReference(null);
                  return cc;
                },
            "choiceOrFkExclusive"));
  }

  @Test
  void selectWithOnlyChoiceOptionsIsValid() {
    ColumnConfig cc = new ColumnConfig("t1", "status", EditorType.SELECT);
    cc.setChoiceOptions(List.of(new ChoiceOption("a", "key.a")));

    assertThatCode(() -> validator.validate(cc)).doesNotThrowAnyException();
  }

  @Test
  void radioWithOnlyFkReferenceIsValid() {
    ColumnConfig cc = new ColumnConfig("t1", "category", EditorType.RADIO);
    cc.setFkReference(new FkReference("public", "categories", "id", "name"));

    assertThatCode(() -> validator.validate(cc)).doesNotThrowAnyException();
  }

  @Test
  void textEditorTypeIsExemptFromChoiceOrFkExclusiveRule() {
    ColumnConfig cc = new ColumnConfig("t1", "name", EditorType.TEXT);

    assertThatCode(() -> validator.validate(cc)).doesNotThrowAnyException();
  }

  @Test
  void validateAllAggregatesViolationsAcrossMultipleEntitiesIntoOneException() {
    TableConfig validTable = new TableConfig("public", "products");
    TableConfig invalidTable = new TableConfig(null, "orders");
    ColumnConfig validColumn = new ColumnConfig("t1", "name", EditorType.TEXT);
    ColumnConfig invalidColumn = new ColumnConfig("t1", null, EditorType.TEXT);

    assertThatThrownBy(
            () ->
                validator.validateAll(
                    List.of(validTable, invalidTable), List.of(validColumn, invalidColumn)))
        .isInstanceOf(ConfigValidationException.class)
        .satisfies(ex -> assertThat(((ConfigValidationException) ex).getFieldErrors()).hasSize(2));
  }

  @Test
  void validateAllDoesNotThrowWhenEverythingIsValid() {
    assertThatCode(
            () ->
                validator.validateAll(
                    List.of(new TableConfig("public", "products")),
                    List.of(new ColumnConfig("t1", "name", EditorType.TEXT))))
        .doesNotThrowAnyException();
  }
}
