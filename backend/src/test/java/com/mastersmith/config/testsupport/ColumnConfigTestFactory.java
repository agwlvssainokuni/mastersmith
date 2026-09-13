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

package com.mastersmith.config.testsupport;

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import java.util.UUID;

/** テストメソッドごとに独立したColumnConfigを構築するファクトリ(unit-test-instructions.md)。 */
public final class ColumnConfigTestFactory {

  private ColumnConfigTestFactory() {}

  /** editorType=text(choiceOptions/fkReference不要)の、有効なColumnConfigを生成する。 */
  public static ColumnConfig aColumnConfig(String tableConfigId) {
    String unique = UUID.randomUUID().toString().substring(0, 8);
    return new ColumnConfig(tableConfigId, "col_" + unique, EditorType.TEXT);
  }

  public static ColumnConfig aColumnConfig(
      String tableConfigId, String columnName, EditorType editorType) {
    return new ColumnConfig(tableConfigId, columnName, editorType);
  }
}
