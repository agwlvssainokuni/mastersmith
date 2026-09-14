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

/** {@link RdbmsTableMetadata}の単体テスト(コンパクトコンストラクタ・不変性、BR2.1)。 */
class RdbmsTableMetadataTest {

  @Test
  void normalizesNullColumnsToEmptyList() {
    RdbmsTableMetadata metadata = new RdbmsTableMetadata("shop", "items", null);

    assertThat(metadata.columns()).isEmpty();
  }

  @Test
  void columnsListIsImmutableCopy() {
    RdbmsColumnMetadata column = new RdbmsColumnMetadata("sku", "varchar(50)", true, false);
    RdbmsTableMetadata metadata = new RdbmsTableMetadata("shop", "items", List.of(column));

    assertThat(metadata.columns()).containsExactly(column);
    assertThatThrownBy(
            () ->
                metadata
                    .columns()
                    .add(new RdbmsColumnMetadata("name", "varchar(100)", false, true)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
