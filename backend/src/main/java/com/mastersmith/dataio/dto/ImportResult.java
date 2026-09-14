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

package com.mastersmith.dataio.dto;

import java.util.List;

/**
 * CSVインポートの実行結果(contract-summary.md C13: {@code ImportResult { successCount, errors }})。
 *
 * @param successCount インポートに成功した行数(全体ロールバック時は常に0、BR8.7)
 * @param errors BR8.6の行単位・フィールド単位エラー一覧(全体ロールバック時はINVALID行の全エラー)
 */
public record ImportResult(int successCount, List<RowError> errors) {

  public ImportResult {
    if (successCount < 0) {
      throw new IllegalArgumentException("successCount must not be negative");
    }
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public static ImportResult committed(int successCount) {
    return new ImportResult(successCount, List.of());
  }

  public static ImportResult rolledBack(List<RowError> errors) {
    return new ImportResult(0, errors);
  }
}
