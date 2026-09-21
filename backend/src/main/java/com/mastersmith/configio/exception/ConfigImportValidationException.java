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

package com.mastersmith.configio.exception;

import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.configio.event.ConfigImportExecutedEvent.FailureCategory;
import java.io.Serial;
import java.util.List;

/**
 * 設定の取り込みの、入力の誤り(構文・形式・構造・参照整合・各ユニットの検証・権限昇格・主権限が0件。422、BR9.7・BR9.21)。何も反映していない。集めた誤り(最大100件)と、検出した総数、失敗の分類を持つ。
 *
 * <p>メッセージには、入力値・内部の詳細を含めない。
 */
public class ConfigImportValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient FailureCategory category;
  private final transient List<ImportValidationError> errors;
  private final int totalErrorCount;
  private final boolean truncated;

  public ConfigImportValidationException(
      FailureCategory category,
      List<ImportValidationError> errors,
      int totalErrorCount,
      boolean truncated) {
    super("config import rejected: category=" + category + " errors=" + totalErrorCount);
    this.category = category;
    this.errors = List.copyOf(errors);
    this.totalErrorCount = totalErrorCount;
    this.truncated = truncated;
  }

  public FailureCategory category() {
    return category;
  }

  /** 応答に含める誤り(最大100件)。 */
  public List<ImportValidationError> errors() {
    return errors;
  }

  /** 検出した誤りの総数(打ち切り前)。 */
  public int totalErrorCount() {
    return totalErrorCount;
  }

  /** 一部の誤りを、応答に含めなかったか。 */
  public boolean truncated() {
    return truncated;
  }
}
