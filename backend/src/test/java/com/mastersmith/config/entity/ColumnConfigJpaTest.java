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

package com.mastersmith.config.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.model.FkReference;
import com.mastersmith.config.model.ValidationRule;
import com.mastersmith.config.testsupport.ColumnConfigTestFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/** ColumnConfigのJPAマッピングテスト(保存・取得の往復、JSON型カラムのシリアライズ往復、一意性制約)。 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ColumnConfigJpaTest {

  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsScalarFields() {
    ColumnConfig columnConfig = new ColumnConfig("table-1", "unit_price", EditorType.DECIMAL);
    columnConfig.setDisplayOrder(2);
    columnConfig.setFormat("#,##0.00");
    columnConfig.setVisibility(Visibility.HIDDEN);

    entityManager.persist(columnConfig);
    entityManager.flush();
    entityManager.clear();

    ColumnConfig reloaded =
        entityManager.find(ColumnConfig.class, columnConfig.getColumnConfigId());
    assertThat(reloaded.getTableConfigId()).isEqualTo("table-1");
    assertThat(reloaded.getColumnName()).isEqualTo("unit_price");
    assertThat(reloaded.getEditorType()).isEqualTo(EditorType.DECIMAL);
    assertThat(reloaded.getDisplayOrder()).isEqualTo(2);
    assertThat(reloaded.getFormat()).isEqualTo("#,##0.00");
    assertThat(reloaded.getVisibility()).isEqualTo(Visibility.HIDDEN);
  }

  @Test
  void roundTripsValidationRuleJsonColumn() {
    ColumnConfig columnConfig = new ColumnConfig("table-1", "sku", EditorType.TEXT);
    ValidationRule rule =
        ValidationRule.builder().rule("required", true).rule("maxLength", 20).build();
    columnConfig.setValidationRule(rule);

    entityManager.persist(columnConfig);
    entityManager.flush();
    entityManager.clear();

    ColumnConfig reloaded =
        entityManager.find(ColumnConfig.class, columnConfig.getColumnConfigId());
    assertThat(reloaded.getValidationRule().has("required")).isTrue();
    assertThat(reloaded.getValidationRule().get("maxLength")).isEqualTo(20);
  }

  @Test
  void roundTripsChoiceOptionsJsonColumn() {
    ColumnConfig columnConfig = new ColumnConfig("table-1", "status", EditorType.SELECT);
    columnConfig.setChoiceOptions(
        List.of(
            new ChoiceOption("active", "table.public.items.status.active"),
            new ChoiceOption("inactive", "table.public.items.status.inactive")));

    entityManager.persist(columnConfig);
    entityManager.flush();
    entityManager.clear();

    ColumnConfig reloaded =
        entityManager.find(ColumnConfig.class, columnConfig.getColumnConfigId());
    assertThat(reloaded.getChoiceOptions())
        .containsExactly(
            new ChoiceOption("active", "table.public.items.status.active"),
            new ChoiceOption("inactive", "table.public.items.status.inactive"));
  }

  @Test
  void roundTripsFkReferenceJsonColumn() {
    ColumnConfig columnConfig = new ColumnConfig("table-1", "category_id", EditorType.SELECT);
    columnConfig.setFkReference(new FkReference("public", "categories", "id", "name"));

    entityManager.persist(columnConfig);
    entityManager.flush();
    entityManager.clear();

    ColumnConfig reloaded =
        entityManager.find(ColumnConfig.class, columnConfig.getColumnConfigId());
    assertThat(reloaded.getFkReference())
        .isEqualTo(new FkReference("public", "categories", "id", "name"));
  }

  @Test
  void savesAndReloadsIsPrimaryKeyFlag() {
    ColumnConfig primaryKeyColumn = new ColumnConfig("table-3", "id", EditorType.INTEGER, true);
    ColumnConfig nonPrimaryKeyColumn = new ColumnConfig("table-3", "name", EditorType.TEXT);

    entityManager.persist(primaryKeyColumn);
    entityManager.persist(nonPrimaryKeyColumn);
    entityManager.flush();
    entityManager.clear();

    assertThat(
            entityManager
                .find(ColumnConfig.class, primaryKeyColumn.getColumnConfigId())
                .isPrimaryKey())
        .isTrue();
    assertThat(
            entityManager
                .find(ColumnConfig.class, nonPrimaryKeyColumn.getColumnConfigId())
                .isPrimaryKey())
        .isFalse();
  }

  @Test
  void rejectsDuplicateTableConfigIdAndColumnName() {
    ColumnConfig first = new ColumnConfig("table-2", "duplicate_column", EditorType.TEXT);
    entityManager.persist(first);
    entityManager.flush();

    ColumnConfig duplicate = new ColumnConfig("table-2", "duplicate_column", EditorType.TEXT);
    entityManager.persist(duplicate);

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(PersistenceException.class);
  }

  @Test
  void validationMessageI18nKeyIsDerivedFromSchemaTableColumnAndRuleType() {
    ColumnConfig columnConfig =
        ColumnConfigTestFactory.aColumnConfig("table-1", "email", EditorType.TEXT);
    assertThat(columnConfig.validationMessageI18nKey("public", "users", "required"))
        .isEqualTo("table.public.users.email.validation.required");
  }
}
