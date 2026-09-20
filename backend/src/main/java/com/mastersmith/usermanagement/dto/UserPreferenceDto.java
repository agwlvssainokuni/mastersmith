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

package com.mastersmith.usermanagement.dto;

import com.mastersmith.usermanagement.entity.UserPreference;

/** ユーザー単位の表示設定(C5の{@code UserPreference}スキーマ)。値は小文字(light/dark、large/medium/small、ja/en)。 */
public record UserPreferenceDto(String theme, String fontSize, String locale) {

  public static UserPreferenceDto from(UserPreference preference) {
    return new UserPreferenceDto(
        preference.getTheme().value(),
        preference.getFontSize().value(),
        preference.getLocale().value());
  }

  /** 既定値(light/medium/ja、BR4.3)。 */
  public static UserPreferenceDto defaults() {
    return from(UserPreference.withDefaults(""));
  }
}
