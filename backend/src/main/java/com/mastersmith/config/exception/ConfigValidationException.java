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

package com.mastersmith.config.exception;

import java.io.Serial;
import java.util.List;

/**
 * 設定定義自体の誤り(必須プロパティ欠落等)をfail fastで通知する例外(rules.md BR1.1, project.md Mandated)。
 *
 * <p>起動時・設定読込時・設定インポート時に送出される。呼び出し元に公開する情報は {@link #getFieldErrors()}が返す構造化データ(フィールド名・ルール種別)のみに限定し、
 * メッセージ本文には具体的なフィールド値・SQL文・内部実装詳細を含めない (security-design.md「エラー情報の安全な返却」)。
 */
public class ConfigValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final List<FieldError> fieldErrors;

  public ConfigValidationException(List<FieldError> fieldErrors) {
    super(buildMessage(fieldErrors));
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  private static String buildMessage(List<FieldError> fieldErrors) {
    return "Configuration validation failed: %d field error(s)".formatted(fieldErrors.size());
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }
}
