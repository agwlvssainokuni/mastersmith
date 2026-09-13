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

package com.mastersmith.config.translation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link I18nKeyDerivation}の単体テスト(rules.md BR1.5, BR1.6)。各パターンでのキー生成を網羅する。 */
class I18nKeyDerivationTest {

  @Test
  void tableLabelKeyFollowsTheDocumentedPattern() {
    assertThat(I18nKeyDerivation.tableLabelKey("public", "products"))
        .isEqualTo("table.public.products.label");
  }

  @Test
  void columnLabelKeyFollowsTheDocumentedPattern() {
    assertThat(I18nKeyDerivation.columnLabelKey("public", "products", "unit_price"))
        .isEqualTo("table.public.products.unit_price.label");
  }

  @Test
  void columnValidationMessageKeyFollowsTheDocumentedPattern() {
    assertThat(
            I18nKeyDerivation.columnValidationMessageKey("public", "products", "sku", "required"))
        .isEqualTo("table.public.products.sku.validation.required");
  }

  @Test
  void columnValidationMessageKeyVariesByRuleType() {
    assertThat(
            I18nKeyDerivation.columnValidationMessageKey("public", "products", "sku", "maxLength"))
        .isEqualTo("table.public.products.sku.validation.maxLength");
  }

  @Test
  void differentSchemasProduceDistinctKeysForTheSameTableAndColumnName() {
    String salesKey = I18nKeyDerivation.columnLabelKey("sales", "orders", "total");
    String archiveKey = I18nKeyDerivation.columnLabelKey("archive", "orders", "total");

    assertThat(salesKey).isNotEqualTo(archiveKey);
  }
}
