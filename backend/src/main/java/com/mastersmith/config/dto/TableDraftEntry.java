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

package com.mastersmith.config.dto;

import java.util.List;

/**
 * schema-introspectorが読み取った1テーブルのメタデータ(functional-spec.md W2)。
 *
 * @param schemaName スキーマ名
 * @param tableName テーブル名
 * @param columns 対象テーブルの全カラムのメタデータ
 */
public record TableDraftEntry(String schemaName, String tableName, List<ColumnDraftEntry> columns) {

  public TableDraftEntry {
    columns = columns == null ? List.of() : List.copyOf(columns);
  }
}
