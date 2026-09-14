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

package com.mastersmith.dataio.exception;

import java.io.Serial;

/**
 * アップロードされたCSVファイルがBR8.1の形式(UTF-8 BOM付き・カンマ区切り・ヘッダー行・CRLF)で
 * パースできない場合の実行時例外(rules.md BR8.1 violation_behaviour: 行単位エラーではなくファイル形式
 * エラーとしてファイル全体を扱う)。
 */
public class CsvFormatException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public CsvFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
