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
 * Userの状態(entities.md User.status)。APIとイベントのスナップショットでは小文字の値({@link #value()})で表す。
 *
 * <p>遷移: 招待でinvited、招待受諾でactive、無効化または招待取消でdisabled(functional-spec.md「状態遷移」)。
 * disabledから他の状態へ戻る遷移は定めない。
 */
public enum UserStatus {
  INVITED("invited"),
  ACTIVE("active"),
  DISABLED("disabled");

  private final String value;

  UserStatus(String value) {
    this.value = value;
  }

  /** API・イベントで用いる小文字の値。 */
  public String value() {
    return value;
  }

  public static Optional<UserStatus> fromValue(String value) {
    return Arrays.stream(values()).filter(s -> s.value.equals(value)).findFirst();
  }
}
