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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * ColumnConfigの編集部品種別(entities.md ColumnConfig.editorType)。
 *
 * <p>{@code entities.md}が定める許容値(text/textarea/integer/decimal/date/datetime/select/
 * radio/switch/checkbox)は小文字表記のため、JSON表現との往復はJava列挙子名(大文字)との 相互変換を{@link #toWireValue()}・{@link
 * #fromWireValue(String)}で吸収する。
 */
public enum EditorType {
  TEXT,
  TEXTAREA,
  INTEGER,
  DECIMAL,
  DATE,
  DATETIME,
  SELECT,
  RADIO,
  SWITCH,
  CHECKBOX;

  /** editorTypeがselect/radioの場合にBR1.4(choiceOptions/fkReference排他)の対象となるか。 */
  public boolean requiresChoiceOrFkReference() {
    return this == SELECT || this == RADIO;
  }

  @JsonValue
  public String toWireValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static EditorType fromWireValue(String value) {
    if (value == null) {
      return null;
    }
    return EditorType.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
