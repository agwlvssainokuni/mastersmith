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

/**
 * schema-introspectorが読み取った1カラムのメタデータ(functional-spec.md W2)。
 *
 * @param columnName カラム名
 * @param rawTypeName 対象RDBMSのメタデータ型名(例: "varchar(255)", "int unsigned")。 {@code
 *     RdbmsTypeNormalizer}が論理型へ正規化する
 * @param isPrimaryKey 対象RDBMSのメタデータ読み取り時にschema-introspectorが判定した主キー列か否か (Contract
 *     Design追補C9、data-import-export/rules.md BR1.14)。{@link #ColumnDraftEntry(String,
 *     String)}経由で構築した場合はfalse
 */
public record ColumnDraftEntry(String columnName, String rawTypeName, boolean isPrimaryKey) {

  public ColumnDraftEntry(String columnName, String rawTypeName) {
    this(columnName, rawTypeName, false);
  }
}
