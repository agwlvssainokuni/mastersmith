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

import com.mastersmith.config.entity.ColumnConfig;
import com.mastersmith.config.entity.EditorType;
import com.mastersmith.config.entity.Visibility;
import com.mastersmith.config.model.ChoiceOption;
import com.mastersmith.config.model.FkReference;
import com.mastersmith.config.model.ValidationRule;
import java.util.List;

/** カラムの設定の、他から変更できないスナップショット(C9の{@code getExportableConfigSet}の戻り値の要素)。値のコピーで、リストは変更できない。 */
public record ColumnConfigSnapshot(
    String columnConfigId,
    String tableConfigId,
    String columnName,
    int displayOrder,
    String format,
    EditorType editorType,
    ValidationRule validationRule,
    Visibility visibility,
    List<ChoiceOption> choiceOptions,
    FkReference fkReference,
    boolean primaryKey) {

  public ColumnConfigSnapshot {
    validationRule = validationRule == null ? ValidationRule.empty() : validationRule;
    choiceOptions = choiceOptions == null ? List.of() : List.copyOf(choiceOptions);
  }

  public static ColumnConfigSnapshot of(ColumnConfig entity) {
    return new ColumnConfigSnapshot(
        entity.getColumnConfigId(),
        entity.getTableConfigId(),
        entity.getColumnName(),
        entity.getDisplayOrder(),
        entity.getFormat(),
        entity.getEditorType(),
        entity.getValidationRule(),
        entity.getVisibility(),
        entity.getChoiceOptions(),
        entity.getFkReference(),
        entity.isPrimaryKey());
  }
}
