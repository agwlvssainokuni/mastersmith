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

import com.mastersmith.config.entity.TableConfig;
import java.util.UUID;

/** テストメソッドごとに独立したTableConfigを構築するファクトリ(unit-test-instructions.md)。 */
public final class TableConfigTestFactory {

  private TableConfigTestFactory() {}

  /** 一意なschemaName/tableNameを持つ、有効なTableConfigを生成する。 */
  public static TableConfig aTableConfig() {
    String unique = UUID.randomUUID().toString().substring(0, 8);
    return new TableConfig("public", "products_" + unique);
  }

  public static TableConfig aTableConfig(String schemaName, String tableName) {
    return new TableConfig(schemaName, tableName);
  }
}
