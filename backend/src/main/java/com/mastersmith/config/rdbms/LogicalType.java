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

import com.mastersmith.config.entity.EditorType;

/**
 * PostgreSQL/MySQL/MariaDBのRDBMS方言差異を吸収した、ConfigEngine内部の論理型 (rules.md BR1.12)。
 *
 * <p>schema-introspectorが読み取った物理型名を{@link RdbmsTypeNormalizer}で本型へ正規化し、 {@link
 * #defaultEditorType()}でColumnConfig.editorTypeの初期値を推定する (functional-spec.md W2)。
 */
public enum LogicalType {
  STRING,
  LONG_TEXT,
  INTEGER,
  DECIMAL,
  BOOLEAN,
  DATE,
  DATETIME;

  /** 正規化された論理型から、schema-introspector取り込み時のeditorType初期値を推定する。 */
  public EditorType defaultEditorType() {
    return switch (this) {
      case STRING -> EditorType.TEXT;
      case LONG_TEXT -> EditorType.TEXTAREA;
      case INTEGER -> EditorType.INTEGER;
      case DECIMAL -> EditorType.DECIMAL;
      case BOOLEAN -> EditorType.CHECKBOX;
      case DATE -> EditorType.DATE;
      case DATETIME -> EditorType.DATETIME;
    };
  }
}
