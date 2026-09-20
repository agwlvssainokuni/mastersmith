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

package com.mastersmith.usermanagement.exception;

import java.util.Map;

/**
 * フィールド単位のバリデーションエラー(RFC 9457のProblemDetailsの{@code errors[]}の1要素、C5追補)。
 *
 * @param field リクエストのフィールド名(例: {@code email}・{@code roleIds})
 * @param message i18nキー(例: {@code user.validation.email.invalid})。文言への変換は、フロントエンド(U12)が行う
 * @param params メッセージのパラメータ(例: {@code {"min":8,"max":128}})。不要なら空。入力値そのもの(パスワードなど)は入れない
 */
public record UserFieldError(String field, String message, Map<String, Object> params) {

  public UserFieldError {
    params = Map.copyOf(params);
  }

  public static UserFieldError of(String field, String message) {
    return new UserFieldError(field, message, Map.of());
  }

  public static UserFieldError of(String field, String message, Map<String, Object> params) {
    return new UserFieldError(field, message, params);
  }
}
