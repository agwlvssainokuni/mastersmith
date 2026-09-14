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
 * CSVインポート時、1行の処理結果を表す値オブジェクト(entities.md CsvImportRowResult)。
 *
 * <p>永続化しない。{@code importCsv}呼び出し1回の処理内でのみ保持する中間結果。
 *
 * @param rowNumber CSVファイル上の行番号(ヘッダー行を除く、1始まり)
 * @param outcome バリデーション結果(BR8.5〜BR8.7の検証を経た結果)
 * @param operation outcomeがVALIDの場合の確定操作種別(nullable、BR8.3)
 * @param errors outcomeがINVALIDの場合のフィールド単位エラー一覧(VALIDの場合は空)
 */
public record CsvImportRowResult(
    int rowNumber, ImportOutcome outcome, ImportOperation operation, List<RowError> errors) {

  public CsvImportRowResult {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome must not be null");
    }
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public boolean isValid() {
    return outcome == ImportOutcome.VALID;
  }
}
