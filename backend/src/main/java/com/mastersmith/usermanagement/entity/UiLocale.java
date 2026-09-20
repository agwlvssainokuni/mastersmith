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

package com.mastersmith.usermanagement.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 表示言語(entities.md UserPreference.locale。FR10.1)。招待メールの言語(NFR7.1)の選択にも用いる。
 *
 * <p>計画上の名称は{@code Locale}だが、{@link java.util.Locale}との混同を避けるため{@code UiLocale}とした。
 */
public enum UiLocale {
  JA("ja"),
  EN("en");

  /** 招待受諾リクエスト・招待リクエストで省略された場合の既定値(BR4.3、BR4.1追補)。 */
  public static final UiLocale DEFAULT = JA;

  private final String value;

  UiLocale(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Optional<UiLocale> fromValue(String value) {
    return Arrays.stream(values()).filter(l -> l.value.equals(value)).findFirst();
  }
}
