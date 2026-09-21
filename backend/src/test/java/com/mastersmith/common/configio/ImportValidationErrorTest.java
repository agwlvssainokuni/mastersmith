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

package com.mastersmith.common.configio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link ImportValidationError}の単体テスト。 */
class ImportValidationErrorTest {

  @Test
  void keepsFieldMessageAndParams() {
    ImportValidationError error =
        ImportValidationError.of("tables[0].tableName", "k", Map.of("max", 5));

    assertThat(error.field()).isEqualTo("tables[0].tableName");
    assertThat(error.message()).isEqualTo("k");
    assertThat(error.params()).containsEntry("max", 5);
  }

  @Test
  void paramsAreDefensivelyCopiedAndDefaultToEmpty() {
    Map<String, Object> params = new HashMap<>();
    params.put("a", 1);
    ImportValidationError error = new ImportValidationError("f", "m", params);
    params.put("b", 2);

    assertThat(error.params()).containsOnlyKeys("a");
    assertThat(ImportValidationError.of("f", "m").params()).isEmpty();
    assertThat(new ImportValidationError("f", "m", null).params()).isEmpty();
  }

  @Test
  void rejectsNullFieldAndMessage() {
    assertThatThrownBy(() -> new ImportValidationError(null, "m", null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ImportValidationError("f", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void prefixIsJoinedWithADotOrDirectlyBeforeAnIndex() {
    assertThat(ImportValidationError.of("tables[0].x", "m").withFieldPrefix("schema").field())
        .isEqualTo("schema.tables[0].x");
    assertThat(ImportValidationError.of("[2]", "m").withFieldPrefix("rbac").field())
        .isEqualTo("rbac[2]");
    assertThat(ImportValidationError.of("", "m").withFieldPrefix("rbac").field()).isEqualTo("rbac");
    assertThat(ImportValidationError.of("a", "m").withFieldPrefix("").field()).isEqualTo("a");
    assertThat(ImportValidationError.of("a", "m").withFieldPrefix(null).field()).isEqualTo("a");
  }
}
