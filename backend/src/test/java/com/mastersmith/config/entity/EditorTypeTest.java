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

package com.mastersmith.config.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * EditorType列挙型の全値網羅テスト(entities.md 許容値:
 * text/textarea/integer/decimal/date/datetime/select/radio/switch/checkbox)。
 */
class EditorTypeTest {

  @ParameterizedTest
  @EnumSource(EditorType.class)
  void wireValueRoundTripsForEveryConstant(EditorType editorType) {
    String wireValue = editorType.toWireValue();
    assertThat(wireValue).isEqualTo(editorType.name().toLowerCase(java.util.Locale.ROOT));
    assertThat(EditorType.fromWireValue(wireValue)).isEqualTo(editorType);
  }

  @Test
  void definesExactlyTheTenAllowedValuesFromEntitiesMd() {
    assertThat(EditorType.values())
        .extracting(EditorType::toWireValue)
        .containsExactlyInAnyOrder(
            "text",
            "textarea",
            "integer",
            "decimal",
            "date",
            "datetime",
            "select",
            "radio",
            "switch",
            "checkbox");
  }

  @ParameterizedTest
  @EnumSource(
      value = EditorType.class,
      names = {"SELECT", "RADIO"})
  void selectAndRadioRequireChoiceOrFkReference(EditorType editorType) {
    assertThat(editorType.requiresChoiceOrFkReference()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = EditorType.class,
      names = {"SELECT", "RADIO"},
      mode = EXCLUDE)
  void otherEditorTypesDoNotRequireChoiceOrFkReference(EditorType editorType) {
    assertThat(editorType.requiresChoiceOrFkReference()).isFalse();
  }

  @Test
  void fromWireValueIsCaseInsensitiveAndTrimsWhitespace() {
    assertThat(EditorType.fromWireValue(" Select ")).isEqualTo(EditorType.SELECT);
  }

  @Test
  void fromWireValueReturnsNullForNullInput() {
    assertThat(EditorType.fromWireValue(null)).isNull();
  }

  @Test
  void everyConstantHasAUniqueWireValue() {
    var wireValues =
        EnumSet.allOf(EditorType.class).stream().map(EditorType::toWireValue).distinct().count();
    assertThat(wireValues).isEqualTo(EditorType.values().length);
  }
}
