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

import java.util.List;

/**
 * 利用者(業務担当者・管理者)の入力データの検証エラー(422)。開発者向けのスタックトレースではなく、フィールド単位のエラーとして返す(project.md Mandated)。
 * メッセージには、フィールド名とi18nキーだけを含め、入力値は含めない(NFR2.2)。
 */
public class UserValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<UserFieldError> errors;

  public UserValidationException(List<UserFieldError> errors) {
    super("Validation failed: " + summarize(errors));
    this.errors = List.copyOf(errors);
  }

  public UserValidationException(UserFieldError error) {
    this(List.of(error));
  }

  public List<UserFieldError> getErrors() {
    return errors;
  }

  private static String summarize(List<UserFieldError> errors) {
    return errors.stream()
        .map(e -> e.field() + ":" + e.message())
        .collect(java.util.stream.Collectors.joining(", "));
  }

  /** エラーがあれば、まとめて投げる。 */
  public static void throwIfAny(List<UserFieldError> errors) {
    if (!errors.isEmpty()) {
      throw new UserValidationException(errors);
    }
  }
}
