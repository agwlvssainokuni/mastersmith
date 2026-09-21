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

package com.mastersmith.configio.mapper;

import com.mastersmith.config.dto.ColumnConfigSnapshot;
import com.mastersmith.config.dto.ConfigExportSet;
import com.mastersmith.config.dto.TableConfigSnapshot;
import java.util.HashMap;
import java.util.Map;

/**
 * 現在の環境の、自然キー → 内部IDの対応(テーブル=(schemaName, tableName)、カラム=(schemaName, tableName,
 * columnName))。config-engineの{@code getExportableConfigSet}(内部設定DBから直接読んだ
 * 値)から、メモリ上のマップとして作る(N+1を避ける)。取り込みの検証の段階では、現在の環境にある項目の内部IDを引く(新規の項目は、見つからず、null=仮の識別)。反映の段階では、schemaの反映の後の値から作り直す。
 */
public final class NaturalKeyIndex {

  private record TableKey(String schemaName, String tableName) {}

  private record ColumnKey(String schemaName, String tableName, String columnName) {}

  private final Map<TableKey, String> tableIds = new HashMap<>();
  private final Map<ColumnKey, String> columnIds = new HashMap<>();

  private NaturalKeyIndex() {}

  public static NaturalKeyIndex from(ConfigExportSet set) {
    NaturalKeyIndex index = new NaturalKeyIndex();
    Map<String, TableConfigSnapshot> tableById = new HashMap<>();
    for (TableConfigSnapshot table : set.tableConfigs()) {
      tableById.put(table.tableConfigId(), table);
      index.tableIds.put(
          new TableKey(table.schemaName(), table.tableName()), table.tableConfigId());
    }
    for (ColumnConfigSnapshot column : set.columnConfigs()) {
      TableConfigSnapshot table = tableById.get(column.tableConfigId());
      if (table != null) {
        index.columnIds.put(
            new ColumnKey(table.schemaName(), table.tableName(), column.columnName()),
            column.columnConfigId());
      }
    }
    return index;
  }

  /** テーブルの内部ID。現在の環境にない(新規の)テーブルはnull。 */
  public String tableId(String schemaName, String tableName) {
    return tableIds.get(new TableKey(schemaName, tableName));
  }

  /** カラムの内部ID。現在の環境にない(新規の)カラムはnull。 */
  public String columnId(String schemaName, String tableName, String columnName) {
    return columnIds.get(new ColumnKey(schemaName, tableName, columnName));
  }
}
