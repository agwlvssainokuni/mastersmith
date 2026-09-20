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

/** 表示テーマ(entities.md UserPreference.theme。FR9.1)。 */
public enum Theme {
  LIGHT("light"),
  DARK("dark");

  /** 招待受諾リクエストで省略された場合の既定値(BR4.3)。 */
  public static final Theme DEFAULT = LIGHT;

  private final String value;

  Theme(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Optional<Theme> fromValue(String value) {
    return Arrays.stream(values()).filter(t -> t.value.equals(value)).findFirst();
  }
}
