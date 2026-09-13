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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BR1.4: editorTypeがselect/radioのColumnConfigは、choiceOptions(静的選択肢)とfkReference
 * (FK参照)のいずれか一方を必ず設定しなければならず、両方の設定・両方の未設定は許容されない。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ChoiceOrFkExclusiveValidator.class)
public @interface ChoiceOrFkExclusive {

  String message() default
      "choiceOptions and fkReference must be set exclusively when editorType is select or radio";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
