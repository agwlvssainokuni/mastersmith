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

package com.mastersmith.auth.dto;

/**
 * ログインのリクエスト(C4)。{@code toString}は、パスワードを含めない(NFR2.7。Javaの{@code record}の既定の{@code
 * toString}は、全項目を出すため、上書きする)。
 */
public record LoginRequest(String email, String password) {

  @Override
  public String toString() {
    return "LoginRequest[]";
  }
}
