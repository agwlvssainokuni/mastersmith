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

package com.mastersmith.usermanagement.security;

/**
 * パスワードの長さの検証を集約する部品(NFR2.3、rules.md BR4.2)。招待受諾・初期管理者の作成・{@code verifyPasswordHash}から共通に使う。
 *
 * <p>長さは、Unicodeのコードポイント数(UTF-16のchar数ではなく)で数える。下限は要件(FR2.5)、上限128文字は、長大な入力によるハッシュ計算の負荷を
 * 避けるための設計上の制約である。
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 128;

  private PasswordPolicy() {}

  /** パスワードのコードポイント数。 */
  public static int length(String password) {
    return password.codePointCount(0, password.length());
  }

  /** 長さが8以上128以下か。nullは不正。 */
  public static boolean isValidLength(String password) {
    if (password == null) {
      return false;
    }
    int length = length(password);
    return length >= MIN_LENGTH && length <= MAX_LENGTH;
  }

  /** 上限(128文字)を超えているか。上限を超える入力は、ハッシュ計算をせずに拒否する。 */
  public static boolean exceedsMaxLength(String password) {
    return password != null && length(password) > MAX_LENGTH;
  }
}
