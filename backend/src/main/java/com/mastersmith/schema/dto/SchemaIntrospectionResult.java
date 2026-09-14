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

import java.util.List;

/**
 * C8(schema-introspector REST API)のレスポンスボディ(functional-spec.md W1手順11、entities.md
 * SchemaIntrospectionResult)。config-engineの{@code writeTableConfigDraft}呼び出し結果をそのまま返す。
 *
 * @param generatedTableConfigIds
 *     今回の呼び出しで新規生成されたTableConfigのID一覧。既存設定があり取り込みをスキップされたテーブルのIDは含まれない(BR2.7)
 */
public record SchemaIntrospectionResult(List<String> generatedTableConfigIds) {

  public SchemaIntrospectionResult {
    generatedTableConfigIds =
        generatedTableConfigIds == null ? List.of() : List.copyOf(generatedTableConfigIds);
  }
}
