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
 * CSVエクスポート処理中の予期しない失敗(業務データ用RDBMSへのアクセスエラー、出力ストリームへの書き込み エラー等)を表す実行時例外(construction phase
 * guardrails: エラーは呼び出し元へ確実に伝播させる)。
 */
public class CsvExportException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public CsvExportException(String message, Throwable cause) {
    super(message, cause);
  }
}
