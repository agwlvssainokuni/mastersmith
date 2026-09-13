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

package com.mastersmith.config.model;

/**
 * editorTypeがselect/radioで、FK参照でない場合の静的選択肢1件(entities.md ColumnConfig.choiceOptions)。
 *
 * <p>表示名テキストは保持せず、TranslationEntryで解決するi18nキーのみを保持する (rules.md BR1.5, BR1.10)。
 */
public record ChoiceOption(String value, String i18nKey) {}
