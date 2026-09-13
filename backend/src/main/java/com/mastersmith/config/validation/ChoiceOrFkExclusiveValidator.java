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

package com.mastersmith.config.validation;

import com.mastersmith.config.entity.ColumnConfig;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link ChoiceOrFkExclusive}(BR1.4)のカスタム検証ロジック。 */
public class ChoiceOrFkExclusiveValidator
    implements ConstraintValidator<ChoiceOrFkExclusive, ColumnConfig> {

  @Override
  public boolean isValid(ColumnConfig value, ConstraintValidatorContext context) {
    if (value == null || value.getEditorType() == null) {
      // editorType自体の欠落はBR1.3(@NotNull)が別途検知する。ここでは対象外として通す。
      return true;
    }
    if (!value.getEditorType().requiresChoiceOrFkReference()) {
      return true;
    }
    boolean hasChoiceOptions =
        value.getChoiceOptions() != null && !value.getChoiceOptions().isEmpty();
    boolean hasFkReference = value.getFkReference() != null;
    return hasChoiceOptions ^ hasFkReference;
  }
}
