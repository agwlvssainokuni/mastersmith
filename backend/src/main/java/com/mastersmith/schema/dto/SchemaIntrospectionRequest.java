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

package com.mastersmith.schema.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * C8(schema-introspector REST API)のリクエストボディ(functional-spec.md W1、entities.md
 * SchemaIntrospectionRequest)。
 *
 * @param schemaName メタデータ読み取り対象のスキーマ名(必須)
 * @param tableNames 読み取り対象のテーブル名一覧。省略時(nullまたは空)は{@code schemaName}配下の全テーブルを対象とする(BR2.2)
 */
public record SchemaIntrospectionRequest(@NotBlank String schemaName, List<String> tableNames) {

  public SchemaIntrospectionRequest {
    tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
  }

  /** BR2.2: tableNamesが指定されているか(非空)どうか。 */
  public boolean hasExplicitTableNames() {
    return !tableNames.isEmpty();
  }
}
