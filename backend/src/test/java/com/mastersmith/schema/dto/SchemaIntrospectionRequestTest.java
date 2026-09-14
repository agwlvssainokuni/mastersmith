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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SchemaIntrospectionRequest}の単体テスト(コンパクトコンストラクタ・不変性、BR2.2)。 */
class SchemaIntrospectionRequestTest {

  @Test
  void normalizesNullTableNamesToEmptyList() {
    SchemaIntrospectionRequest request = new SchemaIntrospectionRequest("shop", null);

    assertThat(request.tableNames()).isEmpty();
    assertThat(request.hasExplicitTableNames()).isFalse();
  }

  @Test
  void reportsExplicitTableNamesWhenProvided() {
    SchemaIntrospectionRequest request =
        new SchemaIntrospectionRequest("shop", List.of("items", "orders"));

    assertThat(request.tableNames()).containsExactly("items", "orders");
    assertThat(request.hasExplicitTableNames()).isTrue();
  }

  @Test
  void tableNamesListIsImmutableCopy() {
    List<String> mutable = new ArrayList<>(List.of("items"));
    SchemaIntrospectionRequest request = new SchemaIntrospectionRequest("shop", mutable);
    mutable.add("orders");

    assertThat(request.tableNames()).containsExactly("items");
    assertThatThrownBy(() -> request.tableNames().add("orders"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
