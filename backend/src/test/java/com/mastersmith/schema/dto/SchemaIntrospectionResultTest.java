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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SchemaIntrospectionResult}の単体テスト(コンパクトコンストラクタ・不変性)。 */
class SchemaIntrospectionResultTest {

  @Test
  void normalizesNullIdsToEmptyList() {
    SchemaIntrospectionResult result = new SchemaIntrospectionResult(null);

    assertThat(result.generatedTableConfigIds()).isEmpty();
  }

  @Test
  void generatedTableConfigIdsListIsImmutableCopy() {
    SchemaIntrospectionResult result = new SchemaIntrospectionResult(List.of("table-1", "table-2"));

    assertThat(result.generatedTableConfigIds()).containsExactly("table-1", "table-2");
    assertThatThrownBy(() -> result.generatedTableConfigIds().add("table-3"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
